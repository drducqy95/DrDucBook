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
import io.legado.app.domain.model.AiTranslationStoryRelationship
import io.legado.app.domain.model.AiTranslationStoryTimeline
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationWorldEntry
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
import org.junit.Assert.assertFalse
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

    @Test
    fun sanitizesCorruptOrPinyinEntityTargetsOnPersist() = runBlocking {
        val book = Book(bookUrl = "test://sanitize", name = "Test Book", author = "test")
        val chapter = BookChapter(url = "ch-1", title = "Ch 1", bookUrl = book.bookUrl, index = 0)
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
                  "refined_segments":[{"id":1,"refined_translation":"Jon gap Medvedev."}],
                  "new_entities":[
                    {"raw":"乔恩","target":"Qiao En","type":"character"},
                    {"raw":"梅杰夫","target":"Mai Kiệt Phu?","type":"character"}
                  ]
                }
            """.trimIndent(),
            expectedIds = listOf(1),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )

        useCase.persistRefinerResult(book, chapter, "乔恩见梅杰夫", refinerResult).getOrThrow()
        val snapshot = useCase.loadSnapshot(book.bookUrl)
        val jon = snapshot.entities.find { it.raw == "乔恩" }
        assertEquals("Jon", jon?.target)
        val medvedev = snapshot.entities.find { it.raw == "梅杰夫" }
        assertEquals("Medvedev", medvedev?.target)
    }

    @Test
    fun placeholderEntityFromRelationshipHasEmptyTargetAndIsNotInEntityDictionary() = runBlocking {
        val book = Book(bookUrl = "test://placeholder", name = "Test Book", author = "test")
        val chapter = BookChapter(url = "ch-1", title = "Ch 1", bookUrl = book.bookUrl, index = 0)
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
                  "refined_segments":[{"id":1,"refined_translation":"Captain America gap Howling Commandos."}],
                  "new_entities":[
                    {"raw":"美队","target":"Captain America","type":"character"}
                  ],
                  "relationships":[
                    {"source":"美队","target":"咆哮突击队","relationship":"leader_of"}
                  ]
                }
            """.trimIndent(),
            expectedIds = listOf(1),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )

        useCase.persistRefinerResult(book, chapter, "美队带领咆哮突击队出击", refinerResult).getOrThrow()
        val snapshot = useCase.loadSnapshot(book.bookUrl)
        val placeholder = snapshot.entities.find { it.raw == "咆哮突击队" }
        assertEquals("", placeholder?.target)

        val storyContext = io.legado.app.domain.model.AiTranslationStoryMemoryPipeline.selectContext(
            snapshot = snapshot,
            chapterIndex = 1,
            source = "美队带领咆哮突击队出击",
        )
        val dictionaryRaws = storyContext.entityDictionary.map { it.original }
        assertTrue(dictionaryRaws.contains("美队"))
        assertFalse(dictionaryRaws.contains("咆哮突击队"))
    }

    @Test
    fun loadSnapshotSanitizesLegacyPoisonedCjkTargets() = runBlocking {
        val book = Book(bookUrl = "test://legacy-sanitize", name = "Test Book", author = "test")
        val memoryGateway = InMemoryAiMemoryGateway()
        memoryGateway.upsert(
            AiMemory(
                conversationId = "",
                key = TranslationStoryMemoryUseCase.entityKey("咆哮突击队"),
                value = """{"raw":"咆哮突击队","target":"咆哮突击队","type":"unknown","description":"Placeholder","firstChapterIndex":0,"aliases":[]}""",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = book.bookUrl,
                type = AiMemory.TYPE_GLOSSARY,
            )
        )
        memoryGateway.upsert(
            AiMemory(
                conversationId = "",
                key = TranslationStoryMemoryUseCase.worldKey(
                    AiTranslationWorldEntry(raw = "帕特庄园", category = "location", chapterIndex = 0)
                ),
                value = """{"raw":"帕特庄园","target":"帕特庄园","category":"location","description":"Old manor","firstChapterIndex":0,"entityRefs":[]}""",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = book.bookUrl,
                type = AiMemory.TYPE_FACT,
            )
        )

        val useCase = TranslationStoryMemoryUseCase(
            aiTextGateway = UnusedAiTextGateway(),
            aiMemoryGateway = memoryGateway,
            cachedChapterGateway = SingleBookCachedChapterGateway(book),
            quickTranslationGateway = IdentityQuickTranslationGateway(),
        )

        val snapshot = useCase.loadSnapshot(book.bookUrl)
        val entity = snapshot.entities.find { it.raw == "咆哮突击队" }
        assertEquals("", entity?.target)
        val world = snapshot.worldBuilding.find { it.raw == "帕特庄园" }
        assertEquals("", world?.target)
    }

    @Test
    fun healsLegacyHanVietForeignEntitiesOnLoadSnapshot() = runBlocking {
        val book = Book(bookUrl = "https://example.com/test-western-fantasy", name = "Test Fantasy")
        val memoryGateway = InMemoryAiMemoryGateway()

        // Poisoned entity 1: 梅杰夫 -> Mai Kiệt Phu
        memoryGateway.upsert(
            AiMemory(
                conversationId = "",
                key = TranslationStoryMemoryUseCase.entityKey("梅杰夫"),
                value = """{"raw":"梅杰夫","target":"Mai Kiệt Phu","type":"character","firstChapterIndex":1,"aliases":[]}""",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = book.bookUrl,
                type = AiMemory.TYPE_GLOSSARY,
            )
        )
        // Poisoned entity 2: 梅杰夫大师 -> đại sư Mai Kiệt Phu
        memoryGateway.upsert(
            AiMemory(
                conversationId = "",
                key = TranslationStoryMemoryUseCase.entityKey("梅杰夫大师"),
                value = """{"raw":"梅杰夫大师","target":"đại sư Mai Kiệt Phu","type":"character","firstChapterIndex":1,"aliases":[]}""",
                scope = AiMemory.SCOPE_BOOK,
                scopeId = book.bookUrl,
                type = AiMemory.TYPE_GLOSSARY,
            )
        )

        val useCase = TranslationStoryMemoryUseCase(
            aiTextGateway = UnusedAiTextGateway(),
            aiMemoryGateway = memoryGateway,
            cachedChapterGateway = SingleBookCachedChapterGateway(book),
            quickTranslationGateway = IdentityQuickTranslationGateway(),
        )

        val snapshot = useCase.loadSnapshot(book.bookUrl)
        val medvedev = snapshot.entities.find { it.raw == "梅杰夫" }
        assertEquals("Medvedev", medvedev?.target)

        val masterMedvedev = snapshot.entities.find { it.raw == "梅杰夫大师" }
        assertEquals("đại sư Medvedev", masterMedvedev?.target)

        // Verify that memory in gateway was healed
        val updatedMemories = memoryGateway.getByScope(AiMemory.SCOPE_BOOK, book.bookUrl)
        val medvedevMemory = updatedMemories.find { it.key == TranslationStoryMemoryUseCase.entityKey("梅杰夫") }
        assertTrue(medvedevMemory?.value?.contains("Medvedev") == true)
        assertTrue(medvedevMemory?.value?.contains("Mai Kiệt Phu") == false)
    }

    @Test
    fun userEditedMemoryIsNotOverwrittenByLaterAiExtraction() = runBlocking {
        val book = Book(bookUrl = "test://user-lock", name = "Locked", author = "test")
        val chapter = BookChapter(url = "ch-1", title = "Ch 1", bookUrl = book.bookUrl, index = 0)
        val gateway = InMemoryAiMemoryGateway()
        val useCase = TranslationStoryMemoryUseCase(
            aiTextGateway = UnusedAiTextGateway(),
            aiMemoryGateway = gateway,
            cachedChapterGateway = SingleBookCachedChapterGateway(book),
            quickTranslationGateway = IdentityQuickTranslationGateway(),
        )
        useCase.upsertEntity(
            book.bookUrl,
            io.legado.app.domain.model.AiTranslationStoryEntity(
                raw = "叶长生",
                target = "Tên người dùng",
                type = "character",
                userEdited = true,
                source = "USER",
            ),
        )
        val result = AiTranslationRefinePipeline.parseRefinerOutput(
            rawOutput = """
                {"refined_segments":[{"id":1,"refined_translation":"Ban dich."}],"new_entities":[{"raw":"叶长生","target":"Ten AI","type":"character"}],"story_timeline":{"summary":"Tiep tuc."}}
            """.trimIndent(),
            expectedIds = listOf(1),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )
        useCase.persistRefinerResult(book, chapter, "叶长生出现", result).getOrThrow()

        assertEquals("Tên người dùng", useCase.loadSnapshot(book.bookUrl).entities.single().target)
    }

    @Test
    fun translationMemoryRequiresExactSourceRawAndKeepsOneWorldEntryAcrossChapters() = runBlocking {
        val book = Book(bookUrl = "test://translation-memory-schema", name = "Memory", author = "test")
        val gateway = InMemoryAiMemoryGateway()
        val useCase = TranslationStoryMemoryUseCase(
            aiTextGateway = UnusedAiTextGateway(),
            aiMemoryGateway = gateway,
            cachedChapterGateway = SingleBookCachedChapterGateway(book),
            quickTranslationGateway = IdentityQuickTranslationGateway(),
        )

        fun resultFor(target: String) = AiTranslationRefinePipeline.parseRefinerOutput(
            rawOutput = """
                {
                  "refined_segments":[{"id":1,"refined_translation":"$target"}],
                  "translation_memory":[
                    {"raw":"叶长生","target":"Diep Truong Sinh","kind":"entity","origin":"chinese","name_type":"person","naming_style":"ancient_sino_vietnamese","category":"character"},
                    {"raw":"青锋剑","target":"Thanh Phong Kiem","kind":"world","origin":"chinese","name_type":"item","naming_style":"ancient_sino_vietnamese","category":"weapon"},
                    {"raw":"不存在","target":"Khong Ton Tai","kind":"term","origin":"unknown","name_type":"term","naming_style":"literal_term","category":"other"},
                    {"raw":"叶长生","target":"错误","kind":"entity","origin":"chinese","name_type":"person","naming_style":"ancient_sino_vietnamese","category":"character"}
                  ]
                }
            """.trimIndent(),
            expectedIds = listOf(1),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )

        useCase.persistRefinerResult(
            book = book,
            chapter = BookChapter(url = "ch-1", title = "Ch 1", bookUrl = book.bookUrl, index = 0),
            source = "叶长生拔出青锋剑",
            result = resultFor("Diep Truong Sinh rut kiem."),
        ).getOrThrow()
        useCase.persistRefinerResult(
            book = book,
            chapter = BookChapter(url = "ch-2", title = "Ch 2", bookUrl = book.bookUrl, index = 1),
            source = "叶长生再次握住青锋剑",
            result = resultFor("Diep Truong Sinh nam chat kiem."),
        ).getOrThrow()

        val snapshot = useCase.loadSnapshot(book.bookUrl)
        assertEquals(listOf("叶长生"), snapshot.entities.map { it.raw })
        assertEquals(0, snapshot.entities.single().firstChapterIndex)
        assertEquals(1, snapshot.entities.single().lastChapterIndex)
        assertEquals(listOf("青锋剑"), snapshot.worldBuilding.map { it.raw })
        assertEquals(1, snapshot.worldBuilding.single().lastChapterIndex)
        assertFalse(snapshot.entities.any { it.raw == "不存在" })
    }

    @Test
    fun entityAndWorldWithSameRawShareUserEditedTarget() = runBlocking {
        val book = Book(bookUrl = "test://canonical-shared", name = "Shared", author = "test")
        val gateway = InMemoryAiMemoryGateway()
        val useCase = TranslationStoryMemoryUseCase(
            aiTextGateway = UnusedAiTextGateway(),
            aiMemoryGateway = gateway,
            cachedChapterGateway = SingleBookCachedChapterGateway(book),
            quickTranslationGateway = IdentityQuickTranslationGateway(),
        )
        useCase.upsertWorldEntry(
            book.bookUrl,
            AiTranslationWorldEntry(raw = "青锋剑", target = "Thanh Phong Kiếm", category = "weapon"),
        )
        useCase.upsertEntity(
            book.bookUrl,
            io.legado.app.domain.model.AiTranslationStoryEntity(
                raw = "青锋剑",
                target = "Thanh Phong Kiếm chuẩn",
                type = "item",
                userEdited = true,
                source = "USER",
            ),
        )
        val snapshot = useCase.loadSnapshot(book.bookUrl)
        assertEquals("Thanh Phong Kiếm chuẩn", snapshot.entities.single().target)
        assertEquals("Thanh Phong Kiếm chuẩn", snapshot.worldBuilding.single().target)
        assertEquals(1, snapshot.canonicalMemory.count { it.raw == "青锋剑" })
    }

    @Test
    fun wikiSeparatesBooksAndProjectsTimelineRelationshipsWithoutGlossaryDuplicates() = runBlocking {
        val firstBook = Book(bookUrl = "test://wiki-first", name = "First", author = "test")
        val secondBook = Book(bookUrl = "test://wiki-second", name = "Second", author = "test")
        val gateway = InMemoryAiMemoryGateway()
        val useCase = TranslationStoryMemoryUseCase(
            aiTextGateway = UnusedAiTextGateway(),
            aiMemoryGateway = gateway,
            cachedChapterGateway = SingleBookCachedChapterGateway(firstBook),
            quickTranslationGateway = IdentityQuickTranslationGateway(),
        )
        useCase.upsertEntity(
            firstBook.bookUrl,
            io.legado.app.domain.model.AiTranslationStoryEntity(
                raw = "叶长生",
                target = "Diệp Trường Sinh",
                category = "character",
                userEdited = true,
                source = "USER",
            ),
        )
        useCase.upsertEntity(
            firstBook.bookUrl,
            io.legado.app.domain.model.AiTranslationStoryEntity(
                raw = "林月",
                target = "Lâm Nguyệt",
                category = "character",
            ),
        )
        useCase.upsertWorldEntry(
            firstBook.bookUrl,
            AiTranslationWorldEntry(raw = "叶长生", target = "Bản khác", category = "weapon"),
        )
        useCase.upsertWorldEntry(
            firstBook.bookUrl,
            AiTranslationWorldEntry(raw = "青锋剑", target = "Thanh Phong Kiếm", category = "weapon"),
        )
        useCase.upsertRelationship(
            firstBook.bookUrl,
            AiTranslationStoryRelationship("叶长生", "林月", "đồng minh", chapterIndex = 0),
        )
        useCase.upsertRelationship(
            firstBook.bookUrl,
            AiTranslationStoryRelationship("林月", "叶长生", "đồng minh", chapterIndex = 0),
        )
        useCase.upsertTimeline(
            firstBook.bookUrl,
            AiTranslationStoryTimeline(
                chapterIndex = 0,
                chapterTitle = "Chương 1",
                summary = "Hai nhân vật gặp nhau.",
                events = listOf("Gặp nhau"),
                discoveries = listOf(
                    AiTranslationWorldEntry(raw = "青锋剑", target = "Thanh Phong Kiếm", category = "weapon"),
                ),
            ),
        )
        useCase.upsertEntity(
            secondBook.bookUrl,
            io.legado.app.domain.model.AiTranslationStoryEntity(
                raw = "叶长生",
                target = "Diệp Trường Sinh khác",
                category = "character",
            ),
        )

        val wiki = useCase.observeLibraryWikiSnapshots().first()
        assertEquals(setOf(firstBook.bookUrl, secondBook.bookUrl), wiki.map { it.bookUrl }.toSet())
        val first = wiki.single { it.bookUrl == firstBook.bookUrl }
        assertEquals(1, first.glossaryRecords.count { it.raw == "叶长生" })
        assertEquals(1, first.glossaryRecords.count { it.raw == "青锋剑" })
        assertTrue(first.glossaryRecords.single { it.raw == "叶长生" }.kind == AiTranslationStoryMemoryKind.ENTITY)
        assertEquals(1, first.timelineRecords.size)
        assertEquals(2, first.relationshipTags.size)
        assertEquals(2, first.characterGraph.nodes.size)
        assertEquals(2, first.characterGraph.edges.size)
        assertEquals("Diệp Trường Sinh", first.glossaryRecords.single { it.raw == "叶长生" }.title)
        assertEquals("Diệp Trường Sinh khác", wiki.single { it.bookUrl == secondBook.bookUrl }
            .glossaryRecords.single { it.raw == "叶长生" }.title)
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

    override fun observeByScopeIds(scope: String, scopeIds: List<String>): Flow<List<AiMemory>> =
        flowOf(memories.filter { it.scope == scope && it.scopeId in scopeIds })

    override fun observeAllByScope(scope: String): Flow<List<AiMemory>> =
        flowOf(memories.filter { it.scope == scope })

    override suspend fun getByConversation(conversationId: String): List<AiMemory> =
        memories.filter { it.conversationId == conversationId }

    override suspend fun getGlobal(): List<AiMemory> =
        memories.filter { it.scope == AiMemory.SCOPE_GLOBAL }

    override suspend fun getByScope(scope: String, scopeId: String): List<AiMemory> =
        memories.filter { it.scope == scope && it.scopeId == scopeId }

    override suspend fun getByScopeIds(scope: String, scopeIds: List<String>): List<AiMemory> =
        memories.filter { it.scope == scope && it.scopeId in scopeIds }

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
