package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.repository.TranslationCacheRepositoryImpl
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.CachedChapterSnapshot
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.MappedTranslation
import io.legado.app.domain.model.QuickDictionaryCatalog
import io.legado.app.domain.model.QuickDictionaryCatalogEntry
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryImportProgress
import io.legado.app.domain.model.QuickDictionaryImportResult
import io.legado.app.domain.model.QuickDictionaryPack
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryUniverse
import io.legado.app.domain.model.QuickTranslationPronounMode
import io.legado.app.domain.model.RevisionStatus
import io.legado.app.domain.model.protectsMachineTranslation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ManageTranslationRevisionUseCaseTest {

    private lateinit var root: File
    private lateinit var repository: TranslationCacheRepositoryImpl
    private lateinit var useCase: ManageTranslationRevisionUseCase

    private val book = Book(bookUrl = "https://example.test/book", name = "Test book")
    private val chapter = BookChapter(
        url = "chapter-1",
        title = "Chapter 1",
        bookUrl = book.bookUrl,
        index = 0,
    )
    private val rawContent = "Original chapter text"

    @Before
    fun setUp() {
        root = Files.createTempDirectory("translation-revision-usecase-test").toFile()
        repository = TranslationCacheRepositoryImpl(
            cacheDir = File(root, "chapters"),
            dynamicUiDir = File(root, "dynamic-ui"),
        )
        useCase = ManageTranslationRevisionUseCase(
            cachedChapterGateway = MemoryCachedChapterGateway(book, chapter, rawContent),
            translationCacheGateway = repository,
            quickDictionaryGateway = EmptyQuickDictionaryGateway(),
            quickTranslationGateway = NoopQuickTranslationGateway(),
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun legacyPayloadOnlyTranslation_canBeLoadedAndFinalized() = runBlocking {
        val payload = repository.getCacheFile(book, chapter, "vi", "app_ai")
        payload.parentFile?.mkdirs()
        payload.writeText("Legacy translated chapter")

        val snapshot = useCase.load(book.bookUrl, chapter.index, "vi", "app_ai")
        assertEquals("Legacy translated chapter", snapshot.current?.content)
        assertEquals(RevisionStatus.MACHINE_DRAFT, snapshot.current?.status)

        val final = useCase.finalize(snapshot, "vi", "app_ai")
        assertEquals(RevisionStatus.FINAL, final.status)

        val current = repository.getCurrentRevision(
            book,
            chapter,
            "vi",
            "app_ai",
            repository.computeContentHash(rawContent),
        )
        assertEquals("Legacy translated chapter", current?.content)
        assertTrue(current?.protectsMachineTranslation == true)
    }

    private class MemoryCachedChapterGateway(
        private val book: Book,
        private val chapter: BookChapter,
        private val content: String,
    ) : CachedChapterGateway {
        override suspend fun getBook(bookUrl: String): Book? = book.takeIf { it.bookUrl == bookUrl }
        override suspend fun getChapter(bookUrl: String, chapterIndex: Int): BookChapter? =
            chapter.takeIf { it.bookUrl == bookUrl && it.index == chapterIndex }
        override suspend fun getChapterContent(book: Book, chapter: BookChapter): String? = content
        override suspend fun getChapterCount(bookUrl: String): Int = 1
        override fun streamChapterCache(book: Book): Flow<CachedChapterSnapshot> = emptyFlow()
    }

    private class EmptyQuickDictionaryGateway : QuickDictionaryGateway {
        override val currentRevision: Long = 0
        override fun observeEntries(): Flow<List<QuickDictionaryEntry>> = emptyFlow()
        override fun observePacks(): Flow<List<QuickDictionaryPack>> = emptyFlow()
        override suspend fun getEffectiveEntries(book: Book, context: String): List<QuickDictionaryEntry> =
            emptyList()
        override suspend fun getUniverses(): List<QuickDictionaryUniverse> = emptyList()
        override suspend fun saveUniverse(universe: QuickDictionaryUniverse) = Unit
        override suspend fun save(entry: QuickDictionaryEntry) = Unit
        override suspend fun saveAll(entries: List<QuickDictionaryEntry>): Int = entries.size
        override suspend fun importPack(
            localPath: String,
            displayName: String,
            type: QuickDictionaryType,
            scope: io.legado.app.domain.model.QuickDictionaryScope,
            scopeKey: String,
            onProgress: (QuickDictionaryImportProgress) -> Unit,
        ): QuickDictionaryImportResult = error("Not used")
        override suspend fun deletePack(id: String) = Unit
        override suspend fun deleteEntry(id: Long) = Unit
        override suspend fun deleteUniverse(key: String) = Unit
    }

    private class NoopQuickTranslationGateway : QuickTranslationGateway {
        override val packVersion: String = "test-pack"
        override fun translate(
            text: String,
            projectTerms: List<DictPair>,
            customPhonetics: List<DictPair>,
        ): String = text
        override fun translate(
            text: String,
            projectTerms: List<DictPair>,
            customPhonetics: List<DictPair>,
            pronounMode: QuickTranslationPronounMode?,
        ): String = text
        override fun translateMapped(
            text: String,
            projectTerms: List<DictPair>,
            customPhonetics: List<DictPair>,
        ): MappedTranslation = error("Not used")
        override fun translateMapped(
            text: String,
            projectTerms: List<DictPair>,
            customPhonetics: List<DictPair>,
            pronounMode: QuickTranslationPronounMode?,
        ): MappedTranslation = error("Not used")
        override fun hanViet(text: String, customPhonetics: List<DictPair>): String = text
        override fun getBuiltInCatalogs(): List<QuickDictionaryCatalog> = emptyList()
        override fun searchBuiltInEntries(
            type: QuickDictionaryType,
            query: String,
            limit: Int,
            catalogId: String?,
        ): List<QuickDictionaryCatalogEntry> = emptyList()
    }
}
