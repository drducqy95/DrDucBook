package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.AiMemory
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTranslationRefinePipeline
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.CachedChapterSnapshot
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.QuickDictionaryCatalog
import io.legado.app.domain.model.QuickDictionaryCatalogEntry
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.TranslationConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationStoryMemoryUseCaseTest {

    @Test
    fun canonicalRefinerMemoryPersistsAndFeedsStoryWikiAfterChapterCommit() = runBlocking {
        val book = Book(
            bookUrl = "test://story-memory",
            name = "Kiem Dao",
            author = "test",
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        val source = "\u53f6\u957f\u751f\u52a0\u5165\u5927\u68a6\u5b66\u5bab\uff0c\u62d4\u51fa\u9752\u950b\u5251\u3002"
        val memoryGateway = InMemoryAiMemoryGateway()
        val useCase = TranslationStoryMemoryUseCase(
            aiTextGateway = UnusedAiTextGateway(),
            aiMemoryGateway = memoryGateway,
            cachedChapterGateway = SingleBookCachedChapterGateway(book),
            quickTranslationGateway = IdentityQuickTranslationGateway(),
        )
        val refinerResult = AiTranslationRefinePipeline.parseRefinerOutput(
            rawOutput = """
                {
                  "refined_segments":[{"id":1,"refined_translation":"Diep Truong Sinh gia nhap hoc cung va rut kiem."}],
                  "new_entities":[
                    {"raw":"\u53f6\u957f\u751f","target":"Diep Truong Sinh","type":"character"},
                    {"raw":"\u5927\u68a6\u5b66\u5bab","target":"Dai Mong Hoc Cung","type":"faction"}
                  ],
                  "relationships":[{"source":"\u53f6\u957f\u751f","target":"\u5927\u68a6\u5b66\u5bab","relationship":"member_of"}],
                  "world_building":[{"raw":"\u9752\u950b\u5251","target":"Thanh Phong Kiem","category":"weapon"}],
                  "story_timeline":{"summary":"Diep Truong Sinh gia nhap hoc cung.","events":["Rut kiem"]},
                  "grammar_notes":[]
                }
            """.trimIndent(),
            expectedIds = listOf(1),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )

        useCase.persistRefinerResult(book, chapter, source, refinerResult).getOrThrow()

        val beforeCommit = useCase.loadSnapshot(book.bookUrl)
        assertEquals(2, beforeCommit.entities.size)
        assertEquals(1, beforeCommit.relationships.size)
        assertEquals(1, beforeCommit.worldBuilding.size)
        assertEquals("Diep Truong Sinh gia nhap hoc cung.", beforeCommit.timelines.single().summary)
        assertTrue(beforeCommit.analyzedChapterIndices.isEmpty())

        useCase.markChapterAnalyzed(book.bookUrl, chapter)

        assertEquals(setOf(0), useCase.loadSnapshot(book.bookUrl).analyzedChapterIndices)
        val wikiRecords = useCase.observeLibraryRecords().first()
        assertEquals(5, wikiRecords.size)
        assertEquals(setOf("Kiem Dao"), wikiRecords.map { it.bookName }.toSet())
        assertEquals(
            AiTranslationStoryMemoryKind.entries.toSet(),
            wikiRecords.map { it.kind }.toSet(),
        )
    }
}

private class InMemoryAiMemoryGateway : AiMemoryGateway {
    private val memories = mutableListOf<AiMemory>()

    override fun observeByConversation(conversationId: String): Flow<List<AiMemory>> =
        flowOf(memories.filter { it.conversationId == conversationId })

    override fun observeGlobal(): Flow<List<AiMemory>> =
        flowOf(memories.filter { it.scope == AiMemory.SCOPE_GLOBAL })

    override fun observeRecent(limit: Int): Flow<List<AiMemory>> = flowOf(memories.take(limit))

    override fun observeByScope(scope: String, scopeId: String): Flow<List<AiMemory>> =
        flowOf(memories.filter { it.scope == scope && it.scopeId == scopeId })

    override fun observeAllByScope(scope: String): Flow<List<AiMemory>> =
        flowOf(memories.filter { it.scope == scope })

    override suspend fun getByConversation(conversationId: String): List<AiMemory> =
        memories.filter { it.conversationId == conversationId }

    override suspend fun getGlobal(): List<AiMemory> =
        memories.filter { it.scope == AiMemory.SCOPE_GLOBAL }

    override suspend fun getByScope(scope: String, scopeId: String): List<AiMemory> =
        memories.filter { it.scope == scope && it.scopeId == scopeId }

    override suspend fun getForPrompt(conversationId: String): List<AiMemory> =
        memories.filter { it.conversationId.isBlank() || it.conversationId == conversationId }

    override suspend fun search(
        query: String,
        scope: String?,
        scopeId: String?,
        limit: Int,
    ): List<AiMemory> = emptyList()

    override suspend fun searchForPrompt(
        query: String,
        conversationId: String,
        limit: Int,
    ): List<AiMemory> = emptyList()

    override suspend fun upsert(memory: AiMemory) {
        val normalizedConversationId = when (memory.scope) {
            AiMemory.SCOPE_GLOBAL -> ""
            AiMemory.SCOPE_CONVERSATION -> memory.scopeId.ifBlank { memory.conversationId }
            else -> "${memory.scope}:${memory.scopeId}"
        }
        val normalized = memory.copy(conversationId = normalizedConversationId)
        memories.removeAll {
            it.conversationId == normalized.conversationId && it.key == normalized.key
        }
        memories += normalized
    }

    override suspend fun updatePinned(
        conversationId: String,
        key: String,
        pinned: Boolean,
    ) = Unit

    override suspend fun delete(conversationId: String, key: String) {
        memories.removeAll { it.conversationId == conversationId && it.key == key }
    }

    override suspend fun deleteAllForConversation(conversationId: String) {
        memories.removeAll { it.conversationId == conversationId }
    }
}

private class SingleBookCachedChapterGateway(
    private val book: Book,
) : CachedChapterGateway {
    override suspend fun getBook(bookUrl: String): Book? = book.takeIf { it.bookUrl == bookUrl }
    override suspend fun getChapterCount(bookUrl: String): Int = 0
    override fun streamChapterCache(book: Book): Flow<CachedChapterSnapshot> = emptyFlow()
}

private class UnusedAiTextGateway : AiTextGateway {
    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        error("AI gateway should not be called")

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = emptyFlow()

    override suspend fun fetchModels(
        provider: AiProviderConfig,
    ): Result<List<AiAvailableModel>> = Result.success(emptyList())
}

private class IdentityQuickTranslationGateway : QuickTranslationGateway {
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
}
