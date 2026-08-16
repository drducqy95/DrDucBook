package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.CachedChapterSnapshot
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.EntityAnalysisCandidate
import io.legado.app.domain.model.QuickDictionaryCatalog
import io.legado.app.domain.model.QuickDictionaryCatalogEntry
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryImportProgress
import io.legado.app.domain.model.QuickDictionaryImportResult
import io.legado.app.domain.model.QuickDictionaryPack
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryUniverse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeDownloadedEntitiesUseCaseTest {

    @Test
    fun accumulatorCountsOccurrencesAndDistinctChapters() {
        val accumulator = EntityCandidateAccumulator()
        accumulator.addChapter(
            chapterIndex = 0,
            chapterTitle = "Chương 1",
            content = "叶长生来到学院。叶长生开始修炼。",
        )
        accumulator.addChapter(
            chapterIndex = 1,
            chapterTitle = "Chương 2",
            content = "众人看见叶长生归来。",
        )

        val candidate = accumulator.ranked(
            minimumOccurrences = 2,
            limit = 500,
        ).firstOrNull { it.raw == "叶长生" }

        assertNotNull(candidate)
        assertEquals(3, candidate?.occurrences)
        assertEquals(2, candidate?.chapterCount)
        assertEquals("Chương 1", candidate?.firstChapterTitle)
    }

    @Test
    fun accumulatorNeverRetainsMoreThanConfiguredCandidateLimit() {
        val accumulator = EntityCandidateAccumulator(maxTrackedCandidates = 20)

        accumulator.addChapter(
            chapterIndex = 0,
            chapterTitle = "Chương dài",
            content = "天地玄黄宇宙洪荒日月盈昃辰宿列张寒来暑往秋收冬藏闰余成岁律吕调阳云腾致雨露结为霜",
        )

        assertTrue(accumulator.size <= 20)
    }

    @Test
    fun analysisUsesOnlyCachedContentAndExcludesExistingDictionaryRows() = runBlocking {
        val book = Book(bookUrl = "book://one", name = "Test book")
        val chapterGateway = FakeCachedChapterGateway(
            book = book,
            chapters = listOf(
                CachedChapterSnapshot(0, "Chương 1", "叶长生来到学院。"),
                CachedChapterSnapshot(1, "Chương 2", null),
                CachedChapterSnapshot(2, "Chương 3", "叶长生离开学院。"),
            ),
        )
        val dictionary = FakeQuickDictionaryGateway(
            effectiveEntries = listOf(
                QuickDictionaryEntry(
                    raw = "学院",
                    target = "học viện",
                    scope = QuickDictionaryScope.PROJECT,
                    scopeKey = book.bookUrl,
                )
            )
        )
        val progress = mutableListOf<Int>()

        val result = AnalyzeDownloadedEntitiesUseCase(
            cachedChapterGateway = chapterGateway,
            dictionaryGateway = dictionary,
            translationGateway = FakeQuickTranslationGateway(),
        )(
            bookUrl = book.bookUrl,
            onProgress = { progress += it.scannedChapters },
        )

        assertEquals(3, result.totalChapters)
        assertEquals(2, result.downloadedChapters)
        assertEquals(listOf(1, 2, 3), progress)
        assertNotNull(result.candidates.firstOrNull { it.raw == "叶长生" })
        assertFalse(result.candidates.any { it.raw == "学院" })
    }

    @Test
    fun importWritesOnlyApprovedCandidatesToProjectScope() = runBlocking {
        val dictionary = FakeQuickDictionaryGateway()
        val selected = listOf(
            EntityAnalysisCandidate(
                raw = "叶长生",
                hanViet = "Diệp Trường Sinh",
                target = "Diệp Trường Sinh",
                type = QuickDictionaryType.NAME,
                occurrences = 3,
                chapterCount = 2,
                firstChapterTitle = "Chương 1",
                context = "叶长生来到学院",
            )
        )

        val imported = ImportEntityCandidatesUseCase(dictionary)(
            bookUrl = "book://one",
            candidates = selected,
        )

        assertEquals(1, imported)
        assertEquals(1, dictionary.savedEntries.size)
        assertEquals(QuickDictionaryScope.PROJECT, dictionary.savedEntries.single().scope)
        assertEquals("book://one", dictionary.savedEntries.single().scopeKey)
    }
}

private class FakeCachedChapterGateway(
    private val book: Book,
    private val chapters: List<CachedChapterSnapshot>,
) : CachedChapterGateway {
    override suspend fun getBook(bookUrl: String): Book? = book.takeIf { it.bookUrl == bookUrl }

    override suspend fun getChapterCount(bookUrl: String): Int = chapters.size

    override fun streamChapterCache(book: Book): Flow<CachedChapterSnapshot> = flowOf(*chapters.toTypedArray())
}

private class FakeQuickTranslationGateway : QuickTranslationGateway {
    override val packVersion: String = "test"

    override fun translate(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
    ): String = "bản dịch $text"

    override fun hanViet(text: String, customPhonetics: List<DictPair>): String = "hán việt $text"

    override fun getBuiltInCatalogs(): List<QuickDictionaryCatalog> = emptyList()

    override fun searchBuiltInEntries(
        type: QuickDictionaryType,
        query: String,
        limit: Int,
        catalogId: String?,
    ): List<QuickDictionaryCatalogEntry> = emptyList()
}

private class FakeQuickDictionaryGateway(
    private val effectiveEntries: List<QuickDictionaryEntry> = emptyList(),
) : QuickDictionaryGateway {
    val savedEntries = mutableListOf<QuickDictionaryEntry>()

    override val currentRevision: Long = 0

    override fun observeEntries(): Flow<List<QuickDictionaryEntry>> = emptyFlow()

    override fun observePacks(): Flow<List<QuickDictionaryPack>> = emptyFlow()

    override suspend fun getEffectiveEntries(
        book: Book,
        context: String,
    ): List<QuickDictionaryEntry> = effectiveEntries

    override suspend fun getUniverses(): List<QuickDictionaryUniverse> = emptyList()

    override suspend fun saveUniverse(universe: QuickDictionaryUniverse) = Unit

    override suspend fun save(entry: QuickDictionaryEntry) {
        savedEntries += entry
    }

    override suspend fun saveAll(entries: List<QuickDictionaryEntry>): Int {
        savedEntries += entries
        return entries.size
    }

    override suspend fun importPack(
        localPath: String,
        displayName: String,
        type: QuickDictionaryType,
        scope: QuickDictionaryScope,
        scopeKey: String,
        onProgress: (QuickDictionaryImportProgress) -> Unit,
    ): QuickDictionaryImportResult = error("Not used")

    override suspend fun deletePack(id: String) = Unit

    override suspend fun deleteEntry(id: Long) = Unit

    override suspend fun deleteUniverse(key: String) = Unit
}
