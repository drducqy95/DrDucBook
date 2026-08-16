package io.legado.app.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.QuickDictionaryCatalog
import io.legado.app.domain.model.QuickDictionaryCatalogEntry
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
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
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class QuickDictionaryRepositoryImportDedupTest {

    private lateinit var database: AppDatabase
    private lateinit var root: File
    private lateinit var packStore: QuickDictionaryPackStore
    private lateinit var repository: QuickDictionaryRepository

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = Files.createTempDirectory("quick-dictionary-import-dedup-test").toFile()
        packStore = QuickDictionaryPackStore(File(root, "packs"))
        val preferences = application.getSharedPreferences(
            "quick_dictionary_import_dedup_test",
            Context.MODE_PRIVATE,
        ).also { it.edit().clear().commit() }
        repository = QuickDictionaryRepository(
            dao = database.quickDictionaryDao,
            packStore = packStore,
            translationGateway = BuiltInDuplicateGateway,
            revisionPreferences = preferences,
        )
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun importKeepsOnlyKeysMissingFromBuiltInRoomExistingPacksAndCurrentFile() = runBlocking {
        repository.save(
            QuickDictionaryEntry(
                raw = "room",
                target = "Room value",
                scope = QuickDictionaryScope.GLOBAL,
                scopeKey = "",
            )
        )
        val existingPackInput = File(root, "existing.txt").apply {
            writeText("packed=Original packed value")
        }
        repository.importPack(
            localPath = existingPackInput.absolutePath,
            displayName = "Existing",
            type = QuickDictionaryType.VIETPHRASE,
            scope = QuickDictionaryScope.GLOBAL,
            scopeKey = "",
        )
        val input = File(root, "new.txt").apply {
            writeText(
                """
                bundled=Must not override built-in
                room=Must not override Room
                packed=Must not override pack
                fresh=New value
                FRESH=Duplicate in current file
                """.trimIndent()
            )
        }

        val result = repository.importPack(
            localPath = input.absolutePath,
            displayName = "New",
            type = QuickDictionaryType.NAME,
            scope = QuickDictionaryScope.GLOBAL,
            scopeKey = "",
        )

        assertEquals(1, result.importedEntries)
        assertEquals(4, result.duplicateLines)
        assertEquals(0, result.rejectedLines)
        val importedPack = requireNotNull(result.pack)
        assertEquals(
            listOf("fresh=New value"),
            File(root, "packs/${importedPack.id}.source.txt").readLines(),
        )
        val matches = packStore.matchEntries("packed fresh", "", "")
        assertTrue(matches.any { it.raw == "packed" && it.target == "Original packed value" })
        assertTrue(matches.any { it.raw == "fresh" && it.target == "New value" })
    }

    private object BuiltInDuplicateGateway : QuickTranslationGateway {
        override val packVersion: String = "test"

        override fun translate(
            text: String,
            projectTerms: List<DictPair>,
            customPhonetics: List<DictPair>,
        ): String = text

        override fun hanViet(text: String, customPhonetics: List<DictPair>): String = text

        override fun getBuiltInCatalogs(): List<QuickDictionaryCatalog> = emptyList()

        override fun searchBuiltInEntries(
            type: QuickDictionaryType,
            query: String,
            limit: Int,
            catalogId: String?,
        ): List<QuickDictionaryCatalogEntry> = emptyList()

        override fun containsBuiltInEntry(type: QuickDictionaryType, raw: String): Boolean {
            return raw.equals("bundled", ignoreCase = true)
        }
    }
}
