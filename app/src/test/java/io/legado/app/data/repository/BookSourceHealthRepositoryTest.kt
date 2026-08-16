package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSourceHealth
import io.legado.app.domain.gateway.SourceDomainIndexGateway
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.SourceDomainEntry
import io.legado.app.domain.model.SourceDomainIndex
import io.legado.app.domain.model.SourceKey
import io.legado.app.domain.model.SourceKeyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class BookSourceHealthRepositoryTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeRowsIncludesBookRssAndVbookSourcesFromDomainIndex() = runBlocking {
        val rssUrl = "https://rss.example/feed"
        val vbookUrl = "vbook://plugin/drduc"
        database.bookSourceHealthDao.upsert(
            BookSourceHealth(
                sourceUrl = rssUrl,
                status = BookSourceHealthStatus.AUTH_REQUIRED.name,
                lastChecked = 2_000L,
                latencyMs = 300L,
            )
        )
        val gateway = FakeSourceDomainIndexGateway(
            SourceDomainIndex(
                listOf(
                    entry(
                        type = SourceKeyType.BOOK,
                        sourceUrl = "https://book.example/source",
                        name = "Book",
                    ),
                    entry(
                        type = SourceKeyType.RSS,
                        sourceUrl = rssUrl,
                        name = "RSS",
                        loginUrl = "https://rss.example/login",
                    ),
                    entry(
                        type = SourceKeyType.BOOK,
                        sourceUrl = vbookUrl,
                        name = "VBook",
                        homeUrl = null,
                        isVbook = true,
                    ),
                )
            )
        )
        val repository = BookSourceHealthRepository(gateway, database.bookSourceHealthDao)

        val rows = repository.observeRows().first()

        assertEquals(3, rows.size)
        val rss = rows.single { it.sourceUrl == rssUrl }
        assertEquals(SourceKeyType.RSS, rss.sourceType)
        assertTrue(rss.hasLoginUrl)
        assertEquals(BookSourceHealthStatus.AUTH_REQUIRED, rss.health?.statusValue)
        val vbook = rows.single { it.sourceUrl == vbookUrl }
        assertTrue(vbook.isVbook)
        assertEquals(null, vbook.homeUrl)
    }

    private fun entry(
        type: SourceKeyType,
        sourceUrl: String,
        name: String,
        homeUrl: String? = sourceUrl,
        loginUrl: String? = null,
        isVbook: Boolean = false,
    ): SourceDomainEntry = SourceDomainEntry(
        key = SourceKey(type, sourceUrl),
        name = name,
        sourceUrl = sourceUrl,
        homeUrl = homeUrl,
        loginUrl = loginUrl,
        enabled = true,
        isVbook = isVbook,
    )

    private class FakeSourceDomainIndexGateway(
        initialIndex: SourceDomainIndex,
    ) : SourceDomainIndexGateway {
        private val state = MutableStateFlow(initialIndex)
        override val index: Flow<SourceDomainIndex> = state
    }
}
