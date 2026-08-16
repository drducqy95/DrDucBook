package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.domain.model.BrowserBookmark
import io.legado.app.domain.model.SourceKey
import io.legado.app.domain.model.SourceKeyType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BrowserBookmarkRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: BrowserBookmarkRepository

    @Before
    fun setUp() {
        val application: Application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BrowserBookmarkRepository(database.browserBookmarkDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun manualBookmarkCrudUsesWebBookmarkTableOnly() = runBlocking {
        val created = repository.saveBookmark(
            id = null,
            title = "Example",
            url = "https://example.com/book",
            folder = "",
        )

        val saved = repository.observeBookmarks().first().single()
        assertEquals(created.id, saved.id)
        assertEquals(BrowserBookmark.DEFAULT_FOLDER, saved.folder)
        assertEquals(0, database.bookmarkDao.all.size)

        val updated = repository.saveBookmark(
            id = null,
            title = "Example updated",
            url = "https://example.com/book",
            folder = "Sources",
        )

        assertEquals(created.id, updated.id)
        assertEquals("Sources", repository.observeBookmarks().first().single().folder)

        repository.deleteBookmark(created.id)

        assertTrue(repository.observeBookmarks().first().isEmpty())
    }

    @Test
    fun sourcePreferencePinAndHideAreMutuallySafe() = runBlocking {
        val sourceKey = SourceKey(SourceKeyType.BOOK, "https://example.com/source")

        repository.setSourcePinned(sourceKey, true)
        repository.observeSourcePreferences().first().single().let { preference ->
            assertTrue(preference.pinned)
            assertFalse(preference.hidden)
        }

        repository.setSourceHidden(sourceKey, true)
        repository.observeSourcePreferences().first().single().let { preference ->
            assertFalse(preference.pinned)
            assertTrue(preference.hidden)
        }

        repository.setSourceHidden(sourceKey, false)
        repository.observeSourcePreferences().first().single().let { preference ->
            assertFalse(preference.pinned)
            assertFalse(preference.hidden)
        }
    }
}
