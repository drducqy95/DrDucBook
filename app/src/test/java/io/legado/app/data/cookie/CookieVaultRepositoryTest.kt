package io.legado.app.data.cookie

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Cookie
import okhttp3.Cookie.Builder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CookieVaultRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CookieVaultRepository

    @Before
    fun setUp() {
        val application: Application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CookieVaultRepository(database, database.cookieDao, FakeCookieVaultCodec())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replaceCookieMergesScopedValues() {
        repository.setCookie("https://example.com", "a=1; b=2")
        repository.replaceCookie("https://example.com", "b=3; c=4")

        assertEquals("a=1; b=3; c=4", repository.getCookie("https://example.com"))
        assertTrue(database.cookieDao.getAllLegacyCookies().isEmpty())
    }

    @Test
    fun responseCookieHonorsPathAndExpiryCleanup() {
        val now = System.currentTimeMillis()
        repository.saveResponse(
            "https://example.com/secure/login",
            listOf(
                Builder()
                    .name("sid")
                    .value("abc")
                    .domain("example.com")
                    .path("/secure")
                    .expiresAt(now + 60_000)
                    .build(),
                Builder()
                    .name("expired")
                    .value("gone")
                    .domain("example.com")
                    .path("/")
                    .expiresAt(now - 60_000)
                    .build(),
            )
        )

        assertEquals("sid=abc", repository.getCookie("https://example.com/secure/profile"))
        assertEquals("", repository.getCookie("https://example.com/public"))
        assertEquals(1, database.cookieDao.getVaultCookiesByScopeKey("example.com").size)
    }

    @Test
    fun responseCookieHonorsSecureAndHostOnlyScopes() {
        val now = System.currentTimeMillis()
        repository.saveResponse(
            "https://example.com/login",
            listOf(
                Builder()
                    .name("secureOnly")
                    .value("1")
                    .domain("example.com")
                    .path("/")
                    .expiresAt(now + 60_000)
                    .secure()
                    .build(),
                Builder()
                    .name("hostOnly")
                    .value("2")
                    .hostOnlyDomain("example.com")
                    .path("/")
                    .expiresAt(now + 60_000)
                    .build(),
            )
        )

        assertEquals(
            mapOf("secureOnly" to "1", "hostOnly" to "2"),
            repository.cookieToMap(repository.getCookie("https://example.com/profile")),
        )
        assertEquals("hostOnly=2", repository.getCookie("http://example.com/profile"))
        assertEquals("secureOnly=1", repository.getCookie("https://sub.example.com/profile"))
    }

    @Test
    fun legacyPlaintextCookiesMigrateIntoVault() {
        database.cookieDao.insert(
            Cookie(
                url = "example.com",
                cookie = "session=legacy; theme=dark",
            )
        )

        assertEquals(1, repository.migrateLegacyCookies())
        assertTrue(database.cookieDao.getAllLegacyCookies().isEmpty())
        assertEquals("session=legacy; theme=dark", repository.getCookie("example.com"))
    }

    @Test
    fun unreadableEncryptedCookieFailsClosedAndDeletesRecord() {
        val failingRepository = CookieVaultRepository(
            database,
            database.cookieDao,
            object : CookieVaultCodec {
                override fun encrypt(value: String): String = "cipher:$value"
                override fun decrypt(value: String): String? = null
            },
        )

        failingRepository.setCookie("https://example.com", "sid=secret")

        assertEquals("", failingRepository.getCookie("https://example.com"))
        assertTrue(database.cookieDao.getVaultCookiesByScopeKey("example.com").isEmpty())
    }

    @Test
    fun removeCookieClearsVaultScope() {
        repository.setCookie("https://example.com", "sid=secret; theme=dark")

        repository.removeCookie("https://example.com")

        assertEquals("", repository.getCookie("https://example.com"))
        assertTrue(database.cookieDao.getVaultCookiesByScopeKey("example.com").isEmpty())
    }
}

private class FakeCookieVaultCodec : CookieVaultCodec {

    override fun encrypt(value: String): String {
        return value.reversed()
    }

    override fun decrypt(value: String): String? {
        return value.reversed()
    }
}
