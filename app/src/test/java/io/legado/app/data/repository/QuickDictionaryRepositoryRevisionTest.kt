package io.legado.app.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.QuickDictionaryEntryEntity
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class QuickDictionaryRepositoryRevisionTest {

    private lateinit var database: AppDatabase
    private lateinit var root: File
    private lateinit var repository: QuickDictionaryRepository

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = Files.createTempDirectory("quick-dictionary-revision-test").toFile()
        val preferences = application.getSharedPreferences(
            "quick_dictionary_revision_test",
            Context.MODE_PRIVATE,
        )
        preferences.edit().clear().commit()
        repository = QuickDictionaryRepository(
            dao = database.quickDictionaryDao,
            packStore = QuickDictionaryPackStore(File(root, "packs")),
            revisionPreferences = preferences,
        )
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun everyUserDictionaryMutationAdvancesPersistentRevision() = runBlocking {
        assertEquals(0L, repository.currentRevision)

        repository.save(
            QuickDictionaryEntry(
                raw = "天道",
                target = "thiên đạo",
                type = QuickDictionaryType.VIETPHRASE,
                scope = QuickDictionaryScope.GLOBAL,
                scopeKey = "",
            )
        )
        assertEquals(1L, repository.currentRevision)

        val entryId = repository.observeEntries().first().single().id
        repository.deleteEntry(entryId)
        assertEquals(2L, repository.currentRevision)

        val input = File(root, "Names.txt").apply {
            writeText("叶长生=Diệp Trường Sinh")
        }
        val pack = requireNotNull(repository.importPack(
            localPath = input.absolutePath,
            displayName = "Names",
            type = QuickDictionaryType.NAME,
            scope = QuickDictionaryScope.GLOBAL,
            scopeKey = "",
        ).pack)
        assertEquals(3L, repository.currentRevision)

        repository.deletePack(pack.id)
        assertEquals(4L, repository.currentRevision)
    }

    @Test
    fun effectiveEntriesNormalizeLegacyStoredAlternativeTargets() = runBlocking {
        database.quickDictionaryDao.upsert(
            QuickDictionaryEntryEntity(
                raw = "\u4EBA",
                target = "nh\u00E2n=t\u00ECnh nh\u00E2n",
                type = QuickDictionaryType.VIETPHRASE.name,
                scope = QuickDictionaryScope.GLOBAL.name,
                scopeKey = "",
            )
        )

        val entries = repository.getEffectiveEntries(
            book = Book(bookUrl = "book-a"),
            context = "\u4EBA",
        )

        assertEquals("t\u00ECnh nh\u00E2n", entries.single().target)
    }

    @Test
    fun projectAndUniverseMutationsOnlyAdvanceTheirOwnEffectiveRevision() = runBlocking {
        val bookA = Book(bookUrl = "book-a")
        val bookB = Book(bookUrl = "book-b")
        repository.saveUniverse(
            io.legado.app.domain.model.QuickDictionaryUniverse(
                key = "xianxia",
                name = "Tiên hiệp",
                contextMarkers = listOf("仙门"),
            )
        )
        val initialA = repository.getEffectiveRevision(bookA, "仙门")
        val initialB = repository.getEffectiveRevision(bookB, "modern")

        repository.save(
            QuickDictionaryEntry(
                raw = "叶长青",
                target = "Diệp Trường Thanh",
                type = QuickDictionaryType.NAME,
                scope = QuickDictionaryScope.PROJECT,
                scopeKey = bookA.bookUrl,
            )
        )
        val projectARevision = repository.getEffectiveRevision(bookA, "仙门")
        val projectBAfterAEdit = repository.getEffectiveRevision(bookB, "modern")

        assertEquals(initialA.global, projectARevision.global)
        assertEquals(initialA.universe, projectARevision.universe)
        assertEquals(initialA.project + 1, projectARevision.project)
        assertEquals(initialB.cacheToken, projectBAfterAEdit.cacheToken)

        repository.save(
            QuickDictionaryEntry(
                raw = "仙门",
                target = "tiên môn",
                type = QuickDictionaryType.TERM,
                scope = QuickDictionaryScope.UNIVERSE,
                scopeKey = "xianxia",
            )
        )
        val universeARevision = repository.getEffectiveRevision(bookA, "仙门")
        val universeBRevision = repository.getEffectiveRevision(bookB, "modern")

        assertEquals(projectARevision.universe + 1, universeARevision.universe)
        assertEquals(projectBAfterAEdit.cacheToken, universeBRevision.cacheToken)
        assertEquals(1L, repository.revisionFor(QuickDictionaryScope.PROJECT, bookA.bookUrl))
        assertEquals(0L, repository.revisionFor(QuickDictionaryScope.PROJECT, bookB.bookUrl))
        assertEquals(1L, repository.revisionFor(QuickDictionaryScope.UNIVERSE, "xianxia"))
    }

    @Test
    fun movingAnEntryInvalidatesBothOldAndNewScopes() = runBlocking {
        repository.save(
            QuickDictionaryEntry(
                raw = "天道",
                target = "thiên đạo",
                scope = QuickDictionaryScope.PROJECT,
                scopeKey = "book-a",
            )
        )
        val saved = repository.observeEntries().first().single()

        repository.save(saved.copy(scopeKey = "book-b"))

        assertEquals(2L, repository.revisionFor(QuickDictionaryScope.PROJECT, "book-a"))
        assertEquals(1L, repository.revisionFor(QuickDictionaryScope.PROJECT, "book-b"))
    }
}
