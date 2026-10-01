package io.legado.app.domain.usecase

import androidx.annotation.Keep
import com.google.gson.JsonParser
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelRegistry
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiTranslationStoryAnalysis
import io.legado.app.domain.model.AiTranslationStoryContext
import io.legado.app.domain.model.AiTranslationStoryEntity
import io.legado.app.domain.model.AiTranslationStoryMemoryPipeline
import io.legado.app.domain.model.AiTranslationStoryMemorySnapshot
import io.legado.app.domain.model.AiTranslationStoryMemoryDelta
import io.legado.app.domain.model.AiTranslationMemoryCandidate
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationStoryRelationship
import io.legado.app.domain.model.AiTranslationStoryTimeline
import io.legado.app.domain.model.AiTranslationTimelineCharacter
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import io.legado.app.domain.model.StoryWikiCharacterGraph
import io.legado.app.domain.model.StoryWikiGraphEdge
import io.legado.app.domain.model.StoryWikiGraphNode
import io.legado.app.domain.model.StoryWikiRelationshipTag
import io.legado.app.domain.model.StoryWikiSnapshot
import io.legado.app.domain.model.AiTranslationStreamAccumulator
import io.legado.app.domain.model.AiTranslationTokenBudget
import io.legado.app.domain.model.AiTranslationRefinePipeline
import io.legado.app.domain.model.AiTranslationWorldEntry
import io.legado.app.domain.model.AiTranslationRefinerResult
import io.legado.app.domain.model.CanonicalTranslationMemory
import io.legado.app.domain.model.TranslationMemoryCanonicalizer
import io.legado.app.domain.model.TranslationMemoryKind
import io.legado.app.domain.model.TranslationMemorySource
import io.legado.app.domain.model.ContentChunker
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Keep
data class AiTranslationStoryMemoryDocument(
    val format: String = STORY_MEMORY_EXPORT_FORMAT,
    val entities: List<AiTranslationStoryEntity> = emptyList(),
    val relationships: List<AiTranslationStoryRelationship> = emptyList(),
    val worldBuilding: List<AiTranslationWorldEntry> = emptyList(),
    val timelines: List<AiTranslationStoryTimeline> = emptyList(),
)

class TranslationStoryMemoryUseCase(
    private val aiTextGateway: AiTextGateway,
    private val aiMemoryGateway: AiMemoryGateway,
    private val cachedChapterGateway: CachedChapterGateway,
    private val quickTranslationGateway: QuickTranslationGateway,
    private val quickDictionaryGateway: QuickDictionaryGateway? = null,
) {

    private val bookLocks = ConcurrentHashMap<String, Mutex>()

    private suspend fun syncToQuickDict(
        bookUrl: String,
        raw: String,
        target: String,
        type: QuickDictionaryType,
    ) {
        if (raw.isBlank() || target.isBlank()) return
        runCatching {
            quickDictionaryGateway?.save(
                QuickDictionaryEntry(
                    raw = raw,
                    hanViet = "",
                    target = target,
                    type = type,
                    scope = QuickDictionaryScope.PROJECT,
                    scopeKey = bookUrl,
                )
            )
        }
    }

    suspend fun prepareForTranslation(
        book: Book,
        currentChapter: BookChapter,
        currentContent: String,
        preset: AiTaskPresetConfig?,
        baseDictionary: List<DictPair>,
    ): AiTranslationStoryContext {
        val lock = bookLocks.getOrPut(book.bookUrl) { Mutex() }
        return lock.withLock {
            AiTranslationStoryMemoryPipeline.selectContext(
                snapshot = if (book.getInheritSeriesMemory() && book.group != 0L) {
                    loadSnapshotWithSeriesInheritance(book)
                } else {
                    loadSnapshot(book.bookUrl)
                },
                chapterIndex = currentChapter.index,
                source = currentContent,
            )
        }
    }

    /**
     * Commits the structured memory emitted by the Stage 3 refiner. This is intentionally
     * independent from the old bootstrap analyser: translating a chapter must not spend fifteen
     * extra AI calls before the first token, while every valid delta is still persisted and
     * immediately available to the next chunk/chapter.
     */
    suspend fun persistRefinerResult(
        book: Book,
        chapter: BookChapter,
        source: String,
        result: AiTranslationRefinerResult,
    ): Result<Int> {
        val extractedMemory = result.translation_memory.toStoryMemoryDelta(source)
        val delta = result.story_memory?.let { existing ->
            existing.copy(
                    entities = (existing.entities + extractedMemory.entities)
                        .distinctBy { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) },
                    relationships = (existing.relationships + extractedMemory.relationships + result.relationships.mapNotNull { map ->
                        AiTranslationStoryRelationship(
                            source = map["source"].orEmpty(),
                            target = map["target"].orEmpty(),
                            relationship = map["relationship"].orEmpty(),
                            description = map["description"].orEmpty(),
                        ).takeIf { it.source.isNotBlank() && it.target.isNotBlank() && it.relationship.isNotBlank() }
                    }).distinctBy { "${it.source}\u0000${it.target}\u0000${it.relationship}".lowercase() },
                    worldBuilding = (existing.worldBuilding + extractedMemory.worldBuilding)
                        .distinctBy { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) },
            )
        } ?: if (extractedMemory.entities.isNotEmpty() || extractedMemory.worldBuilding.isNotEmpty()) {
            extractedMemory.copy(
                relationships = result.relationships.mapNotNull { map ->
                    AiTranslationStoryRelationship(
                        source = map["source"].orEmpty(),
                        target = map["target"].orEmpty(),
                        relationship = map["relationship"].orEmpty(),
                        description = map["description"].orEmpty(),
                    ).takeIf {
                        it.source.isNotBlank() && it.target.isNotBlank() && it.relationship.isNotBlank()
                    }
                },
            )
        } else AiTranslationStoryMemoryDelta(
            entities = result.new_entities.map { entity ->
                AiTranslationStoryEntity(
                    raw = entity.raw,
                    target = entity.target,
                    senseKey = entity.sense_key,
                    type = entity.type.ifBlank { "term" },
                    origin = entity.origin.ifBlank { "unknown" },
                )
            },
            relationships = result.relationships.mapNotNull { map ->
                AiTranslationStoryRelationship(
                    source = map["source"].orEmpty(),
                    target = map["target"].orEmpty(),
                    relationship = map["relationship"].orEmpty(),
                    description = map["description"].orEmpty(),
                ).takeIf {
                    it.source.isNotBlank() && it.target.isNotBlank() && it.relationship.isNotBlank()
                }
            },
        )
        if (delta.entities.isEmpty() && delta.relationships.isEmpty() &&
            delta.worldBuilding.isEmpty() && delta.timeline == null
        ) {
            return Result.success(0)
        }
        val lock = bookLocks.getOrPut(book.bookUrl) { Mutex() }
        return lock.withLock {
            val pendingKey = pendingKey(chapter.index, source, delta)
            try {
                val snapshot = loadSnapshot(book.bookUrl)
                val incomingTimeline = delta.timeline ?: AiTranslationStoryTimeline()
                val timelineCharactersAsEntities = incomingTimeline.characters
                    .filter { character ->
                        character.raw.isNotBlank() &&
                            source.contains(character.raw) &&
                            (character.status.equals("new", ignoreCase = true) || snapshot.entities.none { it.raw == character.raw })
                    }
                    .map { character ->
                        AiTranslationStoryEntity(
                            raw = character.raw,
                            target = character.target.ifBlank { character.raw },
                            type = "character",
                            description = character.role.ifBlank { "Character from chapter timeline" },
                            firstChapterIndex = chapter.index,
                            lastChapterIndex = chapter.index,
                        )
                    }
                val normalizedEntities = normalizeEntities(delta.entities + timelineCharactersAsEntities, source, chapter.index)
                val knownNames = ((snapshot.entities + normalizedEntities).flatMap { entity ->
                    listOf(entity.raw, entity.target) + entity.aliases
                } + (snapshot.worldBuilding + delta.worldBuilding).flatMap { entry ->
                    listOf(entry.raw, entry.target) + entry.entityRefs
                }).filter(String::isNotBlank).toSet()
                val timelineRelationships = extractRelationshipsFromTimeline(
                    characters = incomingTimeline.characters,
                    knownNames = knownNames,
                    source = source,
                    chapterIndex = chapter.index,
                )
                val allCandidateRelationships = delta.relationships + timelineRelationships
                val normalizedRelationships = allCandidateRelationships
                    .mapNotNull { relationship ->
                        relationship.takeIf { value ->
                            (value.source in knownNames || source.contains(value.source)) &&
                                (value.target in knownNames || source.contains(value.target)) &&
                                value.relationship.isNotBlank()
                        }?.copy(chapterIndex = chapter.index)
                    }
                    .distinctBy { "${it.source}\u0000${it.target}\u0000${it.relationship}".lowercase() }
                val placeholders = normalizedRelationships
                    .flatMap { relationship -> listOf(relationship.source, relationship.target) }
                    .filter { name -> name !in knownNames && source.contains(name) }
                    .distinctBy(String::lowercase)
                    .map { raw ->
                        AiTranslationStoryEntity(
                            raw = raw,
                            target = "",
                            type = "unknown",
                            description = "Placeholder created from a relationship endpoint",
                            firstChapterIndex = chapter.index,
                            lastChapterIndex = chapter.index,
                        )
                    }
                val timeline = incomingTimeline.copy(
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    characters = incomingTimeline.characters.filter { character ->
                        character.raw.isNotBlank() && (
                            source.contains(character.raw) ||
                                knownNames.contains(character.raw) ||
                                (character.target.isNotBlank() && knownNames.contains(character.target))
                        )
                    },
                    discoveries = incomingTimeline.discoveries.filter { discovery ->
                        discovery.raw.isNotBlank() && (
                            source.contains(discovery.raw) ||
                                knownNames.contains(discovery.raw) ||
                                (discovery.target.isNotBlank() && knownNames.contains(discovery.target))
                        )
                    },
                )
                val analysis = AiTranslationStoryAnalysis(
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    entities = (normalizedEntities + placeholders)
                        .distinctBy { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) },
                    relationships = normalizedRelationships
                        .distinctBy { "${it.source}\u0000${it.target}\u0000${it.relationship}".lowercase() },
                    worldBuilding = delta.worldBuilding
                    .filter { entry -> source.contains(entry.raw) }
                    .map { it.copy(chapterIndex = chapter.index, lastChapterIndex = chapter.index) }
                        .distinctBy { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) },
                    timeline = timeline,
                )
                persistAnalysis(book.bookUrl, analysis, snapshot)
                aiMemoryGateway.delete(bookConversationId(book.bookUrl), pendingKey)
                Result.success(analysis.entities.size + analysis.relationships.size + analysis.worldBuilding.size + 1)
            } catch (error: Throwable) {
                // Translation remains successful; a durable pending record lets the UI/retry job
                // surface the warning instead of silently losing the story graph.
                runCatching {
                    upsertBookMemory(
                        bookUrl = book.bookUrl,
                        key = pendingKey,
                        type = AiMemory.TYPE_WORKFLOW_RESULT,
                        value = mapOf(
                            "chapterIndex" to chapter.index,
                            "chapterTitle" to chapter.title,
                            "source" to source,
                            "delta" to delta,
                            "error" to (error.message ?: error::class.java.simpleName),
                        ),
                    )
                }
                Result.failure(error)
            }
        }
    }

    suspend fun loadSnapshot(bookUrl: String): AiTranslationStoryMemorySnapshot {
        val memories = aiMemoryGateway.getByScope(AiMemory.SCOPE_BOOK, bookUrl)
        val snapshot = memories.toStorySnapshot()
        healCanonicalGlossary(bookUrl, memories, snapshot)
        return snapshot
    }

    private suspend fun healCanonicalGlossary(
        bookUrl: String,
        memories: List<AiMemory>,
        snapshot: AiTranslationStoryMemorySnapshot,
    ) {
        val canonical = snapshot.canonicalMemory.associateBy { it.identity }
        memories.forEach { memory ->
            when {
                memory.key.startsWith(ENTITY_PREFIX) -> memory.decodeValue<AiTranslationStoryEntity>()?.let { entity ->
                    val entry = canonical[TranslationMemoryCanonicalizer.identity(entity.raw, entity.senseKey)]
                    if (entry != null && entry.target.isNotBlank() && entity.target != entry.target) {
                        runCatching { upsertBookMemory(bookUrl, memory.key, memory.type, entity.copy(target = entry.target)) }
                    }
                }
                memory.key.startsWith(WORLD_PREFIX) -> memory.decodeValue<AiTranslationWorldEntry>()?.let { world ->
                    val entry = canonical[TranslationMemoryCanonicalizer.identity(world.raw, world.senseKey)]
                    if (entry != null && entry.target.isNotBlank() && world.target != entry.target) {
                        runCatching { upsertBookMemory(bookUrl, memory.key, memory.type, world.copy(target = entry.target)) }
                    }
                }
            }
        }
    }

    private fun extractRelationshipsFromTimeline(
        characters: List<AiTranslationTimelineCharacter>,
        knownNames: Set<String>,
        source: String,
        chapterIndex: Int,
    ): List<AiTranslationStoryRelationship> {
        if (characters.isEmpty()) return emptyList()
        val extracted = mutableListOf<AiTranslationStoryRelationship>()
        characters.forEach { char ->
            val charName = char.raw.ifBlank { char.target }.trim()
            if (charName.isBlank()) return@forEach
            char.relationships.forEach { relDesc ->
                val trimmed = relDesc.trim()
                if (trimmed.isBlank()) return@forEach
                val matchedTarget = knownNames
                    .filter { it.length >= 2 && !it.equals(charName, ignoreCase = true) && !it.equals(char.target, ignoreCase = true) }
                    .sortedByDescending { it.length }
                    .firstOrNull { name -> trimmed.contains(name, ignoreCase = true) }
                if (matchedTarget != null) {
                    val cleanRelation = trimmed
                        .replace(matchedTarget, "", ignoreCase = true)
                        .replace(Regex("^(?:của|với|là|và|về|thuộc|ở|trong|tại|of|to|with|and|is)\\s+", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("\\s+(?:của|với|là|và|về|thuộc|ở|trong|tại|of|to|with|and|is)$", RegexOption.IGNORE_CASE), "")
                        .trim(' ', ':', '-', ',', '·', '—')
                        .ifBlank { trimmed }
                    extracted += AiTranslationStoryRelationship(
                        source = charName,
                        target = matchedTarget,
                        relationship = cleanRelation,
                        description = trimmed,
                        chapterIndex = chapterIndex,
                    )
                }
            }
        }
        return extracted
    }

    suspend fun loadSnapshotWithSeriesInheritance(
        book: Book,
    ): AiTranslationStoryMemorySnapshot {
        val ownSnapshot = loadSnapshot(book.bookUrl)
        if (!book.getInheritSeriesMemory() || book.group == 0L) {
            return ownSnapshot
        }
        val siblingBooks = appDb.bookDao.getBooksByGroup(book.group)
            .filter { it.bookUrl != book.bookUrl }
        if (siblingBooks.isEmpty()) return ownSnapshot

        val siblingUrls = siblingBooks.map { it.bookUrl }
        val siblingMemories = aiMemoryGateway.getByScopeIds(AiMemory.SCOPE_BOOK, siblingUrls)
        val siblingSnapshot = siblingMemories.toStorySnapshot()

        return mergeSnapshots(primary = ownSnapshot, inherited = siblingSnapshot)
    }

    suspend fun loadSnapshotForGroup(groupId: Long): AiTranslationStoryMemorySnapshot {
        val books = if (groupId <= 0L) appDb.bookDao.all else appDb.bookDao.getBooksByGroup(groupId)
        val bookUrls = books.map { it.bookUrl }
        val memories = aiMemoryGateway.getByScopeIds(AiMemory.SCOPE_BOOK, bookUrls)
        return memories.toStorySnapshot()
    }

    private fun mergeSnapshots(
        primary: AiTranslationStoryMemorySnapshot,
        inherited: AiTranslationStoryMemorySnapshot,
    ): AiTranslationStoryMemorySnapshot {
        val ownEntityKeys = primary.entities.map { it.raw.lowercase() }.toSet()
        val inheritedEntities = inherited.entities.filter {
            it.raw.lowercase() !in ownEntityKeys
        }
        val ownRelKeys = primary.relationships.map {
            "${it.source.lowercase()}|${it.target.lowercase()}|${it.relationship.lowercase()}"
        }.toSet()
        val inheritedRels = inherited.relationships.filter {
            "${it.source.lowercase()}|${it.target.lowercase()}|${it.relationship.lowercase()}" !in ownRelKeys
        }
        val ownWorldKeys = primary.worldBuilding.map {
            "${it.category.lowercase()}|${it.raw.lowercase()}"
        }.toSet()
        val inheritedWorld = inherited.worldBuilding.filter {
            "${it.category.lowercase()}|${it.raw.lowercase()}" !in ownWorldKeys
        }

        return primary.copy(
            entities = primary.entities + inheritedEntities,
            relationships = primary.relationships + inheritedRels,
            worldBuilding = primary.worldBuilding + inheritedWorld,
        )
    }

    /** Marks a chapter complete only after its full translated payload has been committed. */
    suspend fun markChapterAnalyzed(bookUrl: String, chapter: BookChapter) {
        upsertBookMemory(
            bookUrl = bookUrl,
            key = "$ANALYSIS_PREFIX${chapter.index}",
            type = AiMemory.TYPE_WORKFLOW_RESULT,
            value = mapOf(
                "chapterIndex" to chapter.index,
                "chapterTitle" to chapter.title,
                "pipeline" to STORY_MEMORY_EXPORT_FORMAT,
            ),
        )
    }

    /** Retries durable memory commits without re-running the translation provider. */
    suspend fun retryPending(bookUrl: String): Int {
        val book = cachedChapterGateway.getBook(bookUrl) ?: return 0
        val pending = aiMemoryGateway.getByScope(AiMemory.SCOPE_BOOK, bookUrl)
            .filter { it.key.startsWith(PENDING_PREFIX) }
        var committed = 0
        pending.forEach { memory ->
            val payload = runCatching { JsonParser.parseString(memory.value).asJsonObject }.getOrNull()
                ?: return@forEach
            val chapterIndex = payload.get("chapterIndex")?.asInt ?: return@forEach
            val chapterTitle = payload.get("chapterTitle")?.asString.orEmpty()
            val source = payload.get("source")?.asString.orEmpty()
            val delta = payload.get("delta")?.let { element ->
                runCatching { GSON.fromJson(element, AiTranslationStoryMemoryDelta::class.java) }.getOrNull()
            } ?: return@forEach
            val result = persistRefinerResult(
                book = book,
                chapter = BookChapter(
                    url = "pending:$chapterIndex",
                    title = chapterTitle,
                    bookUrl = bookUrl,
                    index = chapterIndex,
                ),
                source = source,
                result = AiTranslationRefinerResult(
                    refined_segments = emptyList(),
                    story_memory = delta,
                ),
            )
            if (result.isSuccess) committed++
        }
        return committed
    }

    /** Explicit, user-triggered backfill for existing books; never called from normal translation. */
    suspend fun backfill(
        book: Book,
        preset: AiTaskPresetConfig,
        chapterRange: IntRange,
    ): Int {
        val lock = bookLocks.getOrPut(book.bookUrl) { Mutex() }
        return lock.withLock {
            var snapshot = loadSnapshot(book.bookUrl)
            var completed = 0
            chapterRange.filter { it >= 0 }.forEach { chapterIndex ->
                if (chapterIndex in snapshot.analyzedChapterIndices) return@forEach
                val chapter = cachedChapterGateway.getChapter(book.bookUrl, chapterIndex) ?: return@forEach
                val content = cachedChapterGateway.getChapterContent(book, chapter)
                    ?.takeIf(String::isNotBlank) ?: return@forEach
                val analysis = analyzeChapter(
                    book = book,
                    chapter = chapter,
                    content = content,
                    preset = preset,
                    dictionary = snapshot.toDictionaryPairs(),
                ) ?: return@forEach
                persistAnalysis(book.bookUrl, analysis, snapshot)
                markChapterAnalyzed(book.bookUrl, chapter)
                snapshot = loadSnapshot(book.bookUrl)
                completed++
            }
            completed
        }
    }

    suspend fun analyzeTextContent(
        book: Book,
        rawText: String,
        preset: AiTaskPresetConfig,
    ): Result<AiTranslationStoryAnalysis> {
        val cleanText = rawText.trim()
        if (cleanText.isBlank()) {
            return Result.failure(IllegalArgumentException("Text to analyze is blank"))
        }
        val lock = bookLocks.getOrPut(book.bookUrl) { Mutex() }
        return lock.withLock {
            val snapshot = loadSnapshot(book.bookUrl)
            val fakeChapter = BookChapter(
                url = "custom_text:${System.currentTimeMillis()}",
                title = "Văn bản bách khoa",
                bookUrl = book.bookUrl,
                index = -1,
            )
            val dictionary = snapshot.toDictionaryPairs()
            val relevantDictionary = dictionary.asSequence()
                .filter { it.original.isNotBlank() && cleanText.contains(it.original) }
                .distinctBy { it.original.lowercase() }
                .take(MAX_ANALYSIS_DICTIONARY_PAIRS)
                .toList()
            val qtDraft = runCatching {
                quickTranslationGateway.translate(cleanText, relevantDictionary)
            }.getOrDefault("")
            val analysis = generateAnalysis(
                preset = preset,
                book = book,
                chapter = fakeChapter,
                source = cleanText,
                qtDraft = qtDraft,
                dictionary = relevantDictionary,
                partIndex = 0,
            ) ?: return@withLock Result.failure(Exception("AI did not return a valid story analysis"))
            persistAnalysis(book.bookUrl, analysis, snapshot)
            Result.success(analysis)
        }
    }

    suspend fun batchAnalyzeChapters(
        book: Book,
        chapterRange: IntRange,
        preset: AiTaskPresetConfig,
        force: Boolean = false,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): Result<Int> {
        val lock = bookLocks.getOrPut(book.bookUrl) { Mutex() }
        return lock.withLock {
            var snapshot = loadSnapshot(book.bookUrl)
            val indices = chapterRange.filter { it >= 0 }
            val total = indices.size
            if (total == 0) return@withLock Result.success(0)
            var completed = 0
            indices.forEachIndexed { i, chapterIndex ->
                onProgress?.invoke(i, total)
                if (!force && chapterIndex in snapshot.analyzedChapterIndices) {
                    completed++
                    return@forEachIndexed
                }
                val chapter = cachedChapterGateway.getChapter(book.bookUrl, chapterIndex)
                    ?: return@forEachIndexed
                val content = cachedChapterGateway.getChapterContent(book, chapter)
                    ?.takeIf(String::isNotBlank) ?: return@forEachIndexed
                val analysis = analyzeChapter(
                    book = book,
                    chapter = chapter,
                    content = content,
                    preset = preset,
                    dictionary = snapshot.toDictionaryPairs(),
                ) ?: return@forEachIndexed
                persistAnalysis(book.bookUrl, analysis, snapshot)
                markChapterAnalyzed(book.bookUrl, chapter)
                snapshot = loadSnapshot(book.bookUrl)
                completed++
                onProgress?.invoke(completed, total)
            }
            onProgress?.invoke(total, total)
            Result.success(completed)
        }
    }

    suspend fun retrofitChapterTranslations(
        book: Book,
        chapterIndices: List<Int>,
        translateChapterUseCase: TranslateChapterUseCase,
        provider: String = TranslationConfig.llmProvider,
        targetLanguage: String = TranslationConfig.llmTargetLanguage,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): Result<Int> {
        val validIndices = chapterIndices.filter { it >= 0 }.distinct()
        val total = validIndices.size
        if (total == 0) return Result.success(0)
        var retrofitted = 0
        validIndices.forEachIndexed { idx, chapterIndex ->
            onProgress?.invoke(idx, total)
            val chapter = cachedChapterGateway.getChapter(book.bookUrl, chapterIndex)
                ?: return@forEachIndexed
            val translationResult = translateChapterUseCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = true,
                provider = provider,
                targetLanguage = targetLanguage,
                onProgress = {},
                onTranslateStarted = {},
            )
            if (translationResult.isSuccess) {
                retrofitted++
            }
            onProgress?.invoke(idx + 1, total)
        }
        onProgress?.invoke(total, total)
        return Result.success(retrofitted)
    }

    fun observeBookSnapshot(bookUrl: String): Flow<AiTranslationStoryMemorySnapshot> =
        aiMemoryGateway.observeByScope(AiMemory.SCOPE_BOOK, bookUrl)
            .map { memories -> memories.toStorySnapshot() }

    fun observeLibraryRecords(): Flow<List<AiTranslationStoryWikiRecord>> =
        aiMemoryGateway.observeAllByScope(AiMemory.SCOPE_BOOK).map { memories ->
            val storyMemories = memories.filter { it.key.startsWith(STORY_MEMORY_PREFIX) }
            val bookNames = storyMemories.asSequence()
                .map(AiMemory::scopeId)
                .filter(String::isNotBlank)
                .distinct()
                .associateWith { bookUrl ->
                    cachedChapterGateway.getBook(bookUrl)?.name?.takeIf(String::isNotBlank)
                        ?: bookUrl
                }
            storyMemories.mapNotNull { memory -> memory.toWikiRecord(bookNames) }
        }

    /**
     * Builds the Wiki from canonical book snapshots. Relationship rows are deliberately not
     * returned as glossary records: they are projected into tags and graph edges instead.
     */
    fun observeLibraryWikiSnapshots(): Flow<List<StoryWikiSnapshot>> =
        aiMemoryGateway.observeAllByScope(AiMemory.SCOPE_BOOK).map { memories ->
            memories.asSequence()
                .filter { it.key.startsWith(STORY_MEMORY_PREFIX) }
                .groupBy(AiMemory::scopeId)
                .filterKeys(String::isNotBlank)
                .map { (bookUrl, bookMemories) ->
                    val snapshot = bookMemories.toStorySnapshot()
                    val book = cachedChapterGateway.getBook(bookUrl)
                    val bookName = book?.name?.takeIf(String::isNotBlank) ?: bookUrl
                    val bookAuthor = book?.author.orEmpty()
                    val bookCoverUrl = book?.coverUrl.orEmpty()
                    buildWikiSnapshot(bookUrl, bookName, snapshot, bookAuthor, bookCoverUrl)
                }
                .sortedBy { it.bookName.lowercase() }
        }

    private fun buildWikiSnapshot(
        bookUrl: String,
        bookName: String,
        snapshot: AiTranslationStoryMemorySnapshot,
        bookAuthor: String = "",
        bookCoverUrl: String = "",
    ): StoryWikiSnapshot {
        val glossary = snapshot.canonicalMemory.map { entry ->
            AiTranslationStoryWikiRecord(
                id = "$bookUrl|glossary|${stableId(entry.identity)}",
                bookUrl = bookUrl,
                bookName = bookName,
                kind = if (entry.kind == TranslationMemoryKind.ENTITY) {
                    AiTranslationStoryMemoryKind.ENTITY
                } else {
                    AiTranslationStoryMemoryKind.WORLD_BUILDING
                },
                title = entry.target.ifBlank { entry.raw },
                subtitle = listOfNotNull(
                    entry.raw,
                    entry.senseKey.takeIf(String::isNotBlank)?.let { "sense=$it" },
                    entry.category.takeIf(String::isNotBlank),
                    entry.description.takeIf(String::isNotBlank),
                    chapterRange(entry.firstChapterIndex, entry.lastChapterIndex),
                ).joinToString(" · "),
                chapterIndex = entry.firstChapterIndex.takeIf { it >= 0 },
                imagePath = entry.imagePath.takeIf(String::isNotBlank),
                raw = entry.raw,
                senseKey = entry.senseKey,
                category = entry.category,
                description = entry.description,
                metadata = entry.metadata,
            )
        }
        val timelines = snapshot.timelines
            .groupBy { it.chapterIndex }
            .map { (_, values) ->
                val timeline = values.reduce { existing, incoming -> mergeTimeline(existing, incoming) }
                AiTranslationStoryWikiRecord(
                    id = "$bookUrl|timeline|${timeline.chapterIndex}",
                    bookUrl = bookUrl,
                    bookName = bookName,
                    kind = AiTranslationStoryMemoryKind.TIMELINE,
                    title = timeline.chapterTitle.ifBlank { "Chương ${timeline.chapterIndex + 1}" },
                    subtitle = timeline.summary,
                    chapterIndex = timeline.chapterIndex.takeIf { it >= 0 },
                    raw = timeline.characters.joinToString(", ") { it.raw },
                    description = timeline.events.joinToString(" · "),
                )
            }
            .sortedBy { it.chapterIndex }
        val canonicalByRaw = snapshot.canonicalMemory
            .groupBy { TranslationMemoryCanonicalizer.normalizeRaw(it.raw) }
            .mapValues { (_, values) -> values.firstOrNull { it.target.isNotBlank() } ?: values.first() }
        val relationshipTags = snapshot.relationships
            .filter { it.source.isNotBlank() && it.target.isNotBlank() && it.relationship.isNotBlank() }
            .groupBy {
                "${TranslationMemoryCanonicalizer.normalizeRaw(it.source)}\u0000" +
                    "${TranslationMemoryCanonicalizer.normalizeRaw(it.target)}\u0000" +
                    it.relationship.trim().lowercase()
            }
            .map { (id, values) ->
                val relationship = values.maxByOrNull { it.chapterIndex } ?: values.first()
                val sourceMemory = canonicalByRaw[TranslationMemoryCanonicalizer.normalizeRaw(relationship.source)]
                val targetMemory = canonicalByRaw[TranslationMemoryCanonicalizer.normalizeRaw(relationship.target)]
                StoryWikiRelationshipTag(
                    id = "$bookUrl|relation|${stableId(id)}",
                    bookUrl = bookUrl,
                    sourceRaw = relationship.source,
                    sourceTarget = sourceMemory?.target.orEmpty(),
                    targetRaw = relationship.target,
                    targetTarget = targetMemory?.target.orEmpty(),
                    relation = relationship.relationship,
                    chapterIndex = relationship.chapterIndex.takeIf { it >= 0 },
                )
            }
        val graphNodeMap = linkedMapOf<String, StoryWikiGraphNode>()
        snapshot.canonicalMemory
            .filter { it.kind == TranslationMemoryKind.ENTITY }
            .forEach { entry ->
                graphNodeMap[TranslationMemoryCanonicalizer.normalizeRaw(entry.raw)] = StoryWikiGraphNode(
                    id = "${bookUrl}|node|${stableId(entry.identity)}",
                    raw = entry.raw,
                    target = entry.target,
                    category = entry.category,
                    imagePath = entry.imagePath.takeIf(String::isNotBlank),
                )
            }
        snapshot.timelines.flatMap { it.characters }.forEach { character ->
            val key = TranslationMemoryCanonicalizer.normalizeRaw(character.raw)
            if (key.isNotBlank() && key !in graphNodeMap) {
                graphNodeMap[key] = StoryWikiGraphNode(
                    id = "$bookUrl|temp-node|${stableId(key)}",
                    raw = character.raw,
                    target = character.target,
                    category = character.role.ifBlank { "character" },
                )
            }
        }
        relationshipTags.forEach { tag ->
            listOf(tag.sourceRaw to tag.sourceTarget, tag.targetRaw to tag.targetTarget).forEach { (raw, target) ->
                val key = TranslationMemoryCanonicalizer.normalizeRaw(raw)
                if (key.isNotBlank() && key !in graphNodeMap) {
                    graphNodeMap[key] = StoryWikiGraphNode(
                        id = "$bookUrl|temp-node|${stableId(key)}",
                        raw = raw,
                        target = target,
                        category = "character",
                    )
                }
            }
        }
        val graphEdges = relationshipTags.map { tag ->
            val sourceId = graphNodeMap.getValue(TranslationMemoryCanonicalizer.normalizeRaw(tag.sourceRaw)).id
            val targetId = graphNodeMap.getValue(TranslationMemoryCanonicalizer.normalizeRaw(tag.targetRaw)).id
            StoryWikiGraphEdge(
                id = tag.id,
                sourceId = sourceId,
                targetId = targetId,
                relation = tag.relation,
                chapterIndex = tag.chapterIndex,
            )
        }
        return StoryWikiSnapshot(
            bookUrl = bookUrl,
            bookName = bookName,
            bookAuthor = bookAuthor,
            bookCoverUrl = bookCoverUrl,
            glossaryRecords = glossary,
            timelineRecords = timelines,
            relationshipTags = relationshipTags,
            characterGraph = StoryWikiCharacterGraph(graphNodeMap.values.toList(), graphEdges),
        )
    }

    private fun chapterRange(first: Int, last: Int): String? = when {
        first < 0 && last < 0 -> null
        first >= 0 && last > first -> "ch.${first + 1}–${last + 1}"
        first >= 0 -> "ch.${first + 1}"
        else -> "ch.${last + 1}"
    }

    private fun List<AiMemory>.toStorySnapshot(): AiTranslationStoryMemorySnapshot {
        val entityMemories = filter { it.key.startsWith(ENTITY_PREFIX) }
        val rawEntities: List<AiTranslationStoryEntity> = entityMemories.mapNotNull { it.decodeValue() }
        val sanitizedEntities = rawEntities.map { entity ->
            val cleanRaw = entity.raw.replace("?", "·").trim()
            val rawTarget = entity.target.trim()
            val sanitizedTarget = if (entity.userEdited) {
                rawTarget
            } else if (rawTarget.isBlank() ||
                rawTarget.equals(cleanRaw, ignoreCase = true) ||
                AiTranslationRefinePipeline.hasCjkTextCodePoints(rawTarget)
            ) {
                val candidate = io.legado.app.domain.model.SinoForeignNameDetector.classifyName(cleanRaw)
                if (candidate != null && candidate.origin != "chinese" && candidate.suggested.isNotBlank() && candidate.confidence >= 0.85f) {
                    candidate.suggested
                } else {
                    ""
                }
            } else {
                sanitizeExtractedEntityTarget(cleanRaw, rawTarget)
            }
            val finalTarget = if (entity.userEdited) {
                sanitizedTarget
            } else if (sanitizedTarget.isNotBlank() &&
                (sanitizedTarget.equals(cleanRaw, ignoreCase = true) ||
                    AiTranslationRefinePipeline.hasCjkTextCodePoints(sanitizedTarget))
            ) {
                ""
            } else {
                sanitizedTarget
            }
            entity.copy(raw = cleanRaw, target = finalTarget)
        }
        val worldMemories = filter { it.key.startsWith(WORLD_PREFIX) }
        val rawWorld: List<AiTranslationWorldEntry> = worldMemories.mapNotNull { it.decodeValue() }
        val sanitizedWorld = rawWorld.map { entry ->
            if (!entry.userEdited && entry.target.isNotBlank() &&
                (entry.target.equals(entry.raw, ignoreCase = true) ||
                    AiTranslationRefinePipeline.hasCjkTextCodePoints(entry.target))
            ) {
                entry.copy(target = "")
            } else {
                entry
            }
        }
        val canonicalInput = entityMemories.mapNotNull { memory ->
            memory.decodeValue<AiTranslationStoryEntity>()?.let { entity ->
                CanonicalTranslationMemory(
                    identity = TranslationMemoryCanonicalizer.identity(entity.raw, entity.senseKey),
                    raw = entity.raw,
                    target = sanitizedEntities.firstOrNull { it.raw == entity.raw }?.target ?: entity.target,
                    senseKey = entity.senseKey,
                    kind = TranslationMemoryKind.ENTITY,
                    category = entity.category.ifBlank { entity.type },
                    description = entity.description,
                    aliases = entity.aliases,
                    origin = entity.origin,
                    namingStyle = entity.namingStyle,
                    firstChapterIndex = entity.firstChapterIndex,
                    lastChapterIndex = entity.lastChapterIndex,
                    imagePath = entity.imagePath,
                    userEdited = entity.userEdited,
                    source = entity.source.toMemorySourceValue(),
                    updatedAt = memory.updatedAt,
                    metadata = entity.metadata,
                )
            }
        } + worldMemories.mapNotNull { memory ->
            memory.decodeValue<AiTranslationWorldEntry>()?.let { entry ->
                CanonicalTranslationMemory(
                    identity = TranslationMemoryCanonicalizer.identity(entry.raw, entry.senseKey),
                    raw = entry.raw,
                    target = sanitizedWorld.firstOrNull { it.raw == entry.raw && it.senseKey == entry.senseKey }?.target
                        ?: entry.target,
                    senseKey = entry.senseKey,
                    kind = TranslationMemoryKind.WORLD,
                    category = entry.category,
                    description = entry.description,
                    aliases = entry.entityRefs,
                    origin = entry.origin,
                    namingStyle = entry.namingStyle,
                    firstChapterIndex = entry.chapterIndex,
                    lastChapterIndex = entry.lastChapterIndex,
                    imagePath = entry.imagePath,
                    userEdited = entry.userEdited,
                    source = entry.source.toMemorySourceValue(),
                    updatedAt = memory.updatedAt,
                )
            }
        }
        val canonicalMemory = TranslationMemoryCanonicalizer.canonicalize(canonicalInput)
        val canonicalByIdentity = canonicalMemory.associateBy { it.identity }
        val healedEntities = sanitizedEntities.map { entity ->
            entity.copy(
                target = canonicalByIdentity[
                    TranslationMemoryCanonicalizer.identity(entity.raw, entity.senseKey)
                ]?.target?.takeIf(String::isNotBlank) ?: entity.target,
            )
        }
        val healedWorld = sanitizedWorld.map { entry ->
            entry.copy(
                target = canonicalByIdentity[
                    TranslationMemoryCanonicalizer.identity(entry.raw, entry.senseKey)
                ]?.target?.takeIf(String::isNotBlank) ?: entry.target,
            )
        }
        return AiTranslationStoryMemorySnapshot(
            entities = healedEntities,
            relationships = decodeValues(RELATIONSHIP_PREFIX),
            worldBuilding = healedWorld,
            timelines = decodeValues<AiTranslationStoryTimeline>(TIMELINE_PREFIX)
                .sortedBy(AiTranslationStoryTimeline::chapterIndex),
            analyzedChapterIndices = asSequence()
                .map(AiMemory::key)
                .filter { it.startsWith(ANALYSIS_PREFIX) }
                .mapNotNull { it.removePrefix(ANALYSIS_PREFIX).toIntOrNull() }
                .toSet(),
            pendingChapterIndices = asSequence()
                .map(AiMemory::key)
                .filter { it.startsWith(PENDING_PREFIX) }
                .mapNotNull { it.removePrefix(PENDING_PREFIX).substringBefore(':').toIntOrNull() }
                .toSet(),
            canonicalMemory = canonicalMemory,
        )
    }

    suspend fun upsertEntity(bookUrl: String, entity: AiTranslationStoryEntity) {
        require(entity.raw.isNotBlank())
        withBookLock(bookUrl) { upsertEntityUnlocked(bookUrl, entity) }
    }

    /** Explicit QT opt-in path. A user-edited memory conflict is never overwritten silently. */
    suspend fun addQuickDictionaryEntry(
        book: Book,
        raw: String,
        target: String,
        type: QuickDictionaryType,
        memoryCategory: String = if (type == QuickDictionaryType.NAME) "CHARACTER" else "TERM",
        description: String = "",
    ) {
        if (type == QuickDictionaryType.PHONETIC || type == QuickDictionaryType.IGNORE) return
        val cleanRaw = raw.trim()
        val cleanTarget = target.trim()
        require(cleanRaw.isNotBlank() && cleanTarget.isNotBlank())
        val snapshot = loadSnapshot(book.bookUrl)
        val existingEntity = snapshot.entities.firstOrNull { it.raw.equals(cleanRaw, ignoreCase = true) }
        val existingWorld = snapshot.worldBuilding.firstOrNull { it.raw.equals(cleanRaw, ignoreCase = true) }
        val existingTarget = existingEntity?.target ?: existingWorld?.target
        if (existingEntity?.userEdited == true || existingWorld?.userEdited == true) {
            if (existingTarget.orEmpty().isNotBlank() && !existingTarget.equals(cleanTarget, ignoreCase = true)) {
                throw IllegalStateException("Translation memory already contains a user-edited target for $cleanRaw")
            }
            return
        }
        val isCharacter = memoryCategory.equals("CHARACTER", ignoreCase = true) ||
            (memoryCategory.isBlank() && type == QuickDictionaryType.NAME)
        val worldCat = when (memoryCategory.uppercase()) {
            "FACTION" -> "faction"
            "LOCATION" -> "location"
            "ARTIFACT" -> "weapon"
            "TECHNIQUE" -> "technique"
            "REALM" -> "rank"
            else -> "term"
        }
        val cleanDesc = description.trim()
        if (isCharacter) {
            upsertEntity(
                book.bookUrl,
                AiTranslationStoryEntity(
                    raw = cleanRaw,
                    target = cleanTarget,
                    type = "character",
                    category = "character",
                    description = cleanDesc,
                    userEdited = true,
                    source = "QT",
                ),
            )
        } else {
            upsertWorldEntry(
                book.bookUrl,
                AiTranslationWorldEntry(
                    raw = cleanRaw,
                    target = cleanTarget,
                    category = worldCat,
                    description = cleanDesc,
                    userEdited = true,
                    source = "QT",
                ),
            )
        }
    }

    private suspend fun upsertEntityUnlocked(
        bookUrl: String,
        incoming: AiTranslationStoryEntity,
    ) {
        val entity = incoming.copy(
            raw = incoming.raw.trim(),
            senseKey = incoming.senseKey.trim(),
            target = incoming.target.trim(),
        )
        val memories = aiMemoryGateway.getByScope(AiMemory.SCOPE_BOOK, bookUrl)
        val identity = TranslationMemoryCanonicalizer.identity(entity.raw, entity.senseKey)
        val matching = memories.filter { memory ->
            when {
                memory.key.startsWith(ENTITY_PREFIX) -> memory.decodeValue<AiTranslationStoryEntity>()
                    ?.let { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) == identity } == true
                memory.key.startsWith(WORLD_PREFIX) -> memory.decodeValue<AiTranslationWorldEntry>()
                    ?.let { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) == identity } == true
                else -> false
            }
        }
        val lockedTarget = matching.asSequence()
            .mapNotNull { memory ->
                memory.decodeValue<AiTranslationStoryEntity>()?.takeIf { it.userEdited }?.target
                    ?: memory.decodeValue<AiTranslationWorldEntry>()?.takeIf { it.userEdited }?.target
            }
            .firstOrNull(String::isNotBlank)
        val target = when {
            entity.userEdited -> entity.target
            lockedTarget != null -> lockedTarget
            entity.target.isNotBlank() -> entity.target
            else -> matching.firstNotNullOfOrNull { memory ->
                memory.decodeValue<AiTranslationStoryEntity>()?.target
                    ?: memory.decodeValue<AiTranslationWorldEntry>()?.target
            }.orEmpty()
        }
        val entityToWrite = entity.copy(target = target)
        val existingEntity = matching.firstOrNull { it.key.startsWith(ENTITY_PREFIX) }
        upsertBookMemory(
            bookUrl,
            existingEntity?.key ?: entityKey(entity.raw, entity.senseKey),
            AiMemory.TYPE_GLOSSARY,
            entityToWrite,
        )
        matching.filter { it.key.startsWith(WORLD_PREFIX) }.forEach { memory ->
            memory.decodeValue<AiTranslationWorldEntry>()?.let { world ->
                upsertBookMemory(
                    bookUrl,
                    memory.key,
                    memory.type,
                    world.copy(
                        target = target,
                        userEdited = world.userEdited || entity.userEdited,
                        source = if (entity.userEdited) "USER" else world.source,
                    ),
                )
            }
        }
        syncToQuickDict(bookUrl, entityToWrite.raw, entityToWrite.target, QuickDictionaryType.NAME)
    }

    private suspend fun upsertWorldEntryUnlocked(
        bookUrl: String,
        incoming: AiTranslationWorldEntry,
    ) {
        val entry = incoming.copy(
            raw = incoming.raw.trim(),
            senseKey = incoming.senseKey.trim(),
            target = incoming.target.trim(),
        )
        val memories = aiMemoryGateway.getByScope(AiMemory.SCOPE_BOOK, bookUrl)
        val identity = TranslationMemoryCanonicalizer.identity(entry.raw, entry.senseKey)
        val matching = memories.filter { memory ->
            when {
                memory.key.startsWith(ENTITY_PREFIX) -> memory.decodeValue<AiTranslationStoryEntity>()
                    ?.let { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) == identity } == true
                memory.key.startsWith(WORLD_PREFIX) -> memory.decodeValue<AiTranslationWorldEntry>()
                    ?.let { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) == identity } == true
                else -> false
            }
        }
        val lockedTarget = matching.asSequence()
            .mapNotNull { memory ->
                memory.decodeValue<AiTranslationStoryEntity>()?.takeIf { it.userEdited }?.target
                    ?: memory.decodeValue<AiTranslationWorldEntry>()?.takeIf { it.userEdited }?.target
            }
            .firstOrNull(String::isNotBlank)
        val target = when {
            entry.userEdited -> entry.target
            lockedTarget != null -> lockedTarget
            entry.target.isNotBlank() -> entry.target
            else -> matching.firstNotNullOfOrNull { memory ->
                memory.decodeValue<AiTranslationWorldEntry>()?.target
                    ?: memory.decodeValue<AiTranslationStoryEntity>()?.target
            }.orEmpty()
        }
        val worldToWrite = entry.copy(target = target)
        val existingWorld = matching.firstOrNull { it.key.startsWith(WORLD_PREFIX) }
        upsertBookMemory(
            bookUrl,
            existingWorld?.key ?: worldKey(entry),
            AiMemory.TYPE_FACT,
            worldToWrite,
        )
        matching.filter { it.key.startsWith(ENTITY_PREFIX) }.forEach { memory ->
            memory.decodeValue<AiTranslationStoryEntity>()?.let { entity ->
                upsertBookMemory(
                    bookUrl,
                    memory.key,
                    memory.type,
                    entity.copy(
                        target = target,
                        userEdited = entity.userEdited || entry.userEdited,
                        source = if (entry.userEdited) "USER" else entity.source,
                    ),
                )
            }
        }
        syncToQuickDict(bookUrl, worldToWrite.raw, worldToWrite.target, QuickDictionaryType.VIETPHRASE)
    }

    private suspend fun deleteGlossaryIdentity(bookUrl: String, raw: String, senseKey: String) {
        val identity = TranslationMemoryCanonicalizer.identity(raw, senseKey)
        aiMemoryGateway.getByScope(AiMemory.SCOPE_BOOK, bookUrl)
            .filter { memory ->
                when {
                    memory.key.startsWith(ENTITY_PREFIX) -> memory.decodeValue<AiTranslationStoryEntity>()
                        ?.let { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) == identity } == true
                    memory.key.startsWith(WORLD_PREFIX) -> memory.decodeValue<AiTranslationWorldEntry>()
                        ?.let { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) == identity } == true
                    else -> false
                }
            }
            .forEach { memory -> deleteBookMemory(bookUrl, memory.key) }
    }

    private suspend fun <T> withBookLock(bookUrl: String, block: suspend () -> T): T {
        val lock = bookLocks.getOrPut(bookUrl) { Mutex() }
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    suspend fun upsertRelationship(bookUrl: String, relationship: AiTranslationStoryRelationship) {
        require(
            relationship.source.isNotBlank() && relationship.target.isNotBlank() &&
                relationship.relationship.isNotBlank()
        )
        withBookLock(bookUrl) {
            upsertBookMemory(
                bookUrl,
                relationshipKey(relationship),
                AiMemory.TYPE_RELATIONSHIP,
                relationship,
            )
        }
    }

    suspend fun upsertWorldEntry(bookUrl: String, entry: AiTranslationWorldEntry) {
        require(entry.raw.isNotBlank())
        withBookLock(bookUrl) { upsertWorldEntryUnlocked(bookUrl, entry) }
    }

    suspend fun upsertTimeline(bookUrl: String, timeline: AiTranslationStoryTimeline) {
        require(timeline.chapterIndex >= 0 && timeline.summary.isNotBlank())
        withBookLock(bookUrl) {
            upsertBookMemory(
                bookUrl,
                timelineKey(timeline.chapterIndex),
                AiMemory.TYPE_SUMMARY,
                timeline,
            )
        }
    }

    suspend fun deleteEntity(bookUrl: String, raw: String, senseKey: String = "") = withBookLock(bookUrl) {
        deleteGlossaryIdentity(bookUrl, raw, senseKey)
    }

    suspend fun deleteRelationship(bookUrl: String, relationship: AiTranslationStoryRelationship) =
        deleteBookMemory(bookUrl, relationshipKey(relationship))

    suspend fun deleteWorldEntry(bookUrl: String, entry: AiTranslationWorldEntry) = withBookLock(bookUrl) {
        deleteGlossaryIdentity(bookUrl, entry.raw, entry.senseKey)
    }

    suspend fun deleteTimeline(bookUrl: String, chapterIndex: Int) =
        deleteBookMemory(bookUrl, timelineKey(chapterIndex))

    suspend fun clear(bookUrl: String) {
        aiMemoryGateway.getByScope(AiMemory.SCOPE_BOOK, bookUrl)
            .asSequence()
            .filter { it.key.startsWith(STORY_MEMORY_PREFIX) }
            .forEach { memory -> deleteBookMemory(bookUrl, memory.key) }
    }

    suspend fun exportJson(bookUrl: String): String {
        val snapshot = loadSnapshot(bookUrl)
        return GSON.toJson(
            AiTranslationStoryMemoryDocument(
                entities = snapshot.entities,
                relationships = snapshot.relationships,
                worldBuilding = snapshot.worldBuilding,
                timelines = snapshot.timelines,
            )
        )
    }

    suspend fun importJson(bookUrl: String, json: String) {
        val document = runCatching {
            GSON.fromJson(json, AiTranslationStoryMemoryDocument::class.java)
                ?: throw IllegalArgumentException("Story-memory JSON is empty")
        }.getOrElse { throw IllegalArgumentException("Invalid story-memory JSON", it) }
        require(document.format == STORY_MEMORY_EXPORT_FORMAT) {
            "Unsupported story-memory format: ${document.format}"
        }
        document.entities.forEach { upsertEntity(bookUrl, it) }
        document.relationships.forEach { upsertRelationship(bookUrl, it) }
        document.worldBuilding.forEach { upsertWorldEntry(bookUrl, it) }
        document.timelines.forEach { upsertTimeline(bookUrl, it) }
    }

    private suspend fun analyzeChapter(
        book: Book,
        chapter: BookChapter,
        content: String,
        preset: AiTaskPresetConfig,
        dictionary: List<DictPair>,
    ): AiTranslationStoryAnalysis? {
        val maxSourceChars = resolveAiRuntimeMaxInputChars(
            runtimeOptions = preset.runtimeOptions,
            globalFallback = TranslationConfig.aiMaxCharsPerChunk,
        )
        val chunks = ContentChunker.chunk(content, maxSourceChars)
        if (chunks.isEmpty()) return null
        val analyses = mutableListOf<AiTranslationStoryAnalysis>()
        chunks.forEachIndexed { partIndex, chunk ->
            val source = chunk.content
            val relevantDictionary = dictionary.asSequence()
                .filter { it.original.isNotBlank() && source.contains(it.original) }
                .distinctBy { it.original.lowercase() }
                .take(MAX_ANALYSIS_DICTIONARY_PAIRS)
                .toList()
            val qtDraft = runCatching {
                quickTranslationGateway.translate(source, relevantDictionary)
            }.getOrDefault("")
            val analysis = generateAnalysis(
                preset = preset,
                book = book,
                chapter = chapter,
                source = source,
                qtDraft = qtDraft,
                dictionary = relevantDictionary,
                partIndex = partIndex,
            ) ?: return@forEachIndexed
            analyses += analysis
        }
        return analyses.takeIf { it.size == chunks.size }?.let {
            AiTranslationStoryMemoryPipeline.mergeAnalyses(
                chapterIndex = chapter.index,
                chapterTitle = chapter.title,
                analyses = it,
            )
        }
    }

    private suspend fun generateAnalysis(
        preset: AiTaskPresetConfig,
        book: Book,
        chapter: BookChapter,
        source: String,
        qtDraft: String,
        dictionary: List<DictPair>,
        partIndex: Int,
    ): AiTranslationStoryAnalysis? {
        val systemPrompt = AiTranslationStoryMemoryPipeline.buildAnalysisSystemPrompt()
        val userPrompt = AiTranslationStoryMemoryPipeline.buildAnalysisUserPrompt(
            chapterIndex = chapter.index,
            chapterTitle = chapter.title,
            raw = source,
            qtDraft = qtDraft,
            lockedEntities = dictionary,
        )
        val isReasoningModel = AiCapability.REASONING in preset.model.capabilities ||
            AiCapability.REASONING in AiModelRegistry.inferCapabilities(preset.model.modelId)
        val outputBudget = AiTranslationTokenBudget.forSourceChars(
            sourceChars = (source.length / 2).coerceAtLeast(256),
            configuredLimit = preset.params.maxOutputTokens,
            providerLimit = preset.model.maxOutputTokens,
            reasoningModel = isReasoningModel,
        ).coerceAtMost(MAX_ANALYSIS_OUTPUT_TOKENS)
        val params = preset.params.copy(
            temperature = ANALYSIS_TEMPERATURE,
            reasoningLevel = AiReasoningLevel.OFF,
            maxOutputTokens = outputBudget,
        )
        repeat(ANALYSIS_ATTEMPTS) { attempt ->
            val request = AiGenerateRequest(
                model = preset.model,
                messages = if (preset.model.provider.protocol == AiProtocol.LOCAL_GGUF) {
                    listOf(AiMessage(AiMessageRole.USER, "$systemPrompt\n\n$userPrompt"))
                } else {
                    listOf(
                        AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                        AiMessage(AiMessageRole.USER, userPrompt),
                    )
                },
                params = params,
                taskType = preset.taskType.ifBlank { AiTaskType.EXTRACT_STORY_MEMORY },
                routeProfileId = preset.runtimeOptions.routeProfileId,
                routeSessionKey = "story-memory:${book.bookUrl}:${chapter.index}:$partIndex",
                routeRetryOffset = attempt,
            )
            val accumulator = AiTranslationStreamAccumulator()
            try {
                aiTextGateway.generateStream(request).collect { event ->
                    if (event is AiStreamEvent.Content) accumulator.append(event.text)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                return@repeat
            }
            val analysis = runCatching {
                AiTranslationStoryMemoryPipeline.parseAnalysis(
                    rawOutput = accumulator.toString(),
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    source = source,
                )
            }.getOrNull()
            if (analysis != null) return analysis
        }
        return null
    }

    private suspend fun persistAnalysis(
        bookUrl: String,
        analysis: AiTranslationStoryAnalysis,
        previous: AiTranslationStoryMemorySnapshot,
    ) {
        val existingEntities = previous.entities.associateBy {
            TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey)
        }
        val normalizedEntities = analysis.entities.map { discovered ->
            val identity = TranslationMemoryCanonicalizer.identity(discovered.raw, discovered.senseKey)
            val existing = existingEntities[identity]
                ?: previous.worldBuilding.firstOrNull {
                    TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) == identity
                }?.let { world ->
                    AiTranslationStoryEntity(
                        raw = world.raw,
                        target = world.target,
                        senseKey = world.senseKey,
                        category = world.category,
                        description = world.description,
                        aliases = world.entityRefs,
                        userEdited = world.userEdited,
                        source = world.source,
                    )
                }
            if (existing == null) {
                discovered
            } else {
                existing.copy(
                    // A user-edited record is a lock. AI may enrich only missing descriptive
                    // fields and must never replace its translation or classification.
                    target = if (existing.userEdited) existing.target else existing.target.ifBlank { discovered.target },
                    type = if (existing.userEdited) existing.type else existing.type.ifBlank { discovered.type },
                    category = if (existing.userEdited) existing.category else existing.category.ifBlank { discovered.category },
                    origin = if (existing.userEdited) existing.origin else existing.origin.ifBlank { discovered.origin },
                    namingStyle = if (existing.userEdited) existing.namingStyle else existing.namingStyle.ifBlank { discovered.namingStyle },
                    description = discovered.description.ifBlank { existing.description },
                    aliases = (existing.aliases + discovered.aliases).distinct(),
                    gender = existing.gender.ifBlank { discovered.gender },
                    rank = discovered.rank.ifBlank { existing.rank },
                    firstChapterIndex = listOf(existing.firstChapterIndex, discovered.firstChapterIndex)
                        .filter { it >= 0 }
                        .minOrNull() ?: analysis.chapterIndex,
                    lastChapterIndex = listOf(existing.lastChapterIndex, discovered.lastChapterIndex, analysis.chapterIndex)
                        .filter { it >= 0 }
                        .maxOrNull() ?: analysis.chapterIndex,
                )
            }
        }
        normalizedEntities.forEach { upsertEntityUnlocked(bookUrl, it) }
        analysis.relationships.forEach {
            upsertBookMemory(bookUrl, relationshipKey(it), AiMemory.TYPE_RELATIONSHIP, it)
        }
        val existingWorldEntries = previous.worldBuilding.associateBy {
            TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey)
        }
        analysis.worldBuilding.forEach { discovered ->
            val existing = existingWorldEntries[
                TranslationMemoryCanonicalizer.identity(discovered.raw, discovered.senseKey)
            ]
            upsertWorldEntryUnlocked(
                bookUrl,
                if (existing == null) discovered else discovered.copy(
                    target = if (existing.userEdited) existing.target else existing.target.ifBlank { discovered.target },
                    category = if (existing.userEdited) existing.category else existing.category.ifBlank { discovered.category },
                    origin = if (existing.userEdited) existing.origin else existing.origin.ifBlank { discovered.origin },
                    namingStyle = if (existing.userEdited) existing.namingStyle else existing.namingStyle.ifBlank { discovered.namingStyle },
                    userEdited = existing.userEdited,
                    source = existing.source,
                    lastChapterIndex = listOf(existing.lastChapterIndex, discovered.lastChapterIndex, analysis.chapterIndex)
                        .filter { it >= 0 }
                        .maxOrNull() ?: analysis.chapterIndex,
                    imagePath = existing.imagePath,
                    imagePrompt = existing.imagePrompt,
                    imageUpdatedAt = existing.imageUpdatedAt,
                ),
            )
        }
        val existingRaw = existingEntities.keys
        val incomingTimeline = analysis.timeline.copy(
            characters = analysis.timeline.characters.map { character ->
                character.copy(
                    status = if (existingRaw.any {
                            it.startsWith(TranslationMemoryCanonicalizer.normalizeRaw(character.raw) + "\u0000")
                        }) "existing" else "new"
                )
            }
        )
        val normalizedTimeline = mergeTimeline(
            existing = previous.timelines.firstOrNull {
                it.chapterIndex == analysis.chapterIndex
            },
            incoming = incomingTimeline,
        )
        if (normalizedTimeline.summary.isNotBlank() || normalizedTimeline.events.isNotEmpty()) {
            upsertBookMemory(
                bookUrl,
                timelineKey(normalizedTimeline.chapterIndex),
                AiMemory.TYPE_SUMMARY,
                normalizedTimeline,
            )
        }
    }

    private fun mergeTimeline(
        existing: AiTranslationStoryTimeline?,
        incoming: AiTranslationStoryTimeline,
    ): AiTranslationStoryTimeline {
        if (existing == null) return incoming
        val summaries = listOf(existing.summary, incoming.summary)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        return incoming.copy(
            chapterTitle = incoming.chapterTitle.ifBlank { existing.chapterTitle },
            summary = summaries.joinToString(" "),
            events = (existing.events + incoming.events).distinct(),
            characters = (existing.characters + incoming.characters)
                .associateBy { it.raw.lowercase() }
                .values
                .toList(),
            discoveries = (existing.discoveries + incoming.discoveries)
                .associateBy { "${it.category}\u0000${it.raw}".lowercase() }
                .values
                .toList(),
        )
    }

    private suspend fun upsertBookMemory(
        bookUrl: String,
        key: String,
        type: String,
        value: Any,
    ) {
        aiMemoryGateway.upsert(
            AiMemory(
                conversationId = "",
                key = key,
                value = GSON.toJson(value),
                scope = AiMemory.SCOPE_BOOK,
                scopeId = bookUrl,
                type = type,
                sourceConversationId = null,
                confidence = 1.0,
            )
        )
    }

    private fun normalizeEntities(
        entities: List<AiTranslationStoryEntity>,
        source: String,
        chapterIndex: Int,
    ): List<AiTranslationStoryEntity> = entities
        .filter { it.raw.isNotBlank() && (source.contains(it.raw) || source.contains(it.raw.replace("?", "·"))) }
        .map { entity ->
            val cleanRaw = entity.raw.replace("?", "·")
            val rawTarget = entity.target.trim()
            val sanitizedTarget = if (rawTarget.isBlank() ||
                rawTarget.equals(cleanRaw, ignoreCase = true) ||
                AiTranslationRefinePipeline.hasCjkTextCodePoints(rawTarget)
            ) {
                val candidate = io.legado.app.domain.model.SinoForeignNameDetector.classifyName(cleanRaw)
                if (candidate != null && candidate.origin != "chinese" && candidate.suggested.isNotBlank() && candidate.confidence >= 0.85f) {
                    candidate.suggested
                } else {
                    ""
                }
            } else {
                sanitizeExtractedEntityTarget(cleanRaw, rawTarget)
            }
            val finalTarget = if (sanitizedTarget.isNotBlank() &&
                (sanitizedTarget.equals(cleanRaw, ignoreCase = true) ||
                    AiTranslationRefinePipeline.hasCjkTextCodePoints(sanitizedTarget))
            ) {
                ""
            } else {
                sanitizedTarget
            }
            entity.copy(
                raw = cleanRaw,
                target = finalTarget,
                firstChapterIndex = entity.firstChapterIndex.takeIf { it >= 0 } ?: chapterIndex,
                lastChapterIndex = entity.lastChapterIndex.takeIf { it >= 0 } ?: chapterIndex,
            )
        }
        .distinctBy { TranslationMemoryCanonicalizer.identity(it.raw, it.senseKey) }

    fun sanitizeExtractedEntityTarget(raw: String, target: String): String {
        val cleanTarget = target.replace("?", "").replace(Regex(" {2,}"), " ").trim()
        val cleanRaw = raw.replace("?", "·").trim()
        val candidate = io.legado.app.domain.model.SinoForeignNameDetector.classifyName(cleanRaw)
        if (candidate != null && candidate.origin != "chinese" && candidate.suggested.isNotBlank() && candidate.confidence >= 0.85f) {
            val targetLower = cleanTarget.lowercase()
            val targetUnaccented = stripVietnameseDiacritics(targetLower)
            if (targetUnaccented.contains("qiao en") ||
                targetUnaccented.contains("kieu an") ||
                targetUnaccented.contains("mai kiet") ||
                targetUnaccented.contains("kiet phu") ||
                targetUnaccented.contains("lac khac") ||
                targetUnaccented.contains("la khac") ||
                (targetUnaccented == "lok" && candidate.suggested == "Locke") ||
                target.contains("?")
            ) {
                return candidate.suggested
            }
            if (candidate.detectionTier in setOf("exact", "canonical_alias", "title_compound", "compound_separated")) {
                return candidate.suggested
            }
        }
        return cleanTarget
    }

    private fun List<AiTranslationMemoryCandidate>.toStoryMemoryDelta(source: String): AiTranslationStoryMemoryDelta {
        val valid = filter {
            it.raw.isNotBlank() &&
                it.target.isNotBlank() &&
                source.contains(it.raw)
        }
        return AiTranslationStoryMemoryDelta(
            entities = valid.filter { it.kind == "entity" }.map { candidate ->
                AiTranslationStoryEntity(
                    raw = candidate.raw,
                    target = candidate.target,
                    senseKey = candidate.sense_key,
                    type = candidate.category.ifBlank { candidate.name_type.ifBlank { "character" } },
                    category = candidate.category.ifBlank { candidate.name_type.ifBlank { "character" } },
                    aliases = candidate.aliases,
                    origin = candidate.origin,
                    namingStyle = candidate.naming_style,
                    source = "AI",
                )
            },
            worldBuilding = valid.filter { it.kind == "world" || it.kind == "term" }.map { candidate ->
                AiTranslationWorldEntry(
                    raw = candidate.raw,
                    target = candidate.target,
                    senseKey = candidate.sense_key,
                    category = candidate.category.ifBlank { "other" },
                    entityRefs = candidate.aliases,
                    origin = candidate.origin,
                    namingStyle = candidate.naming_style,
                    source = "AI",
                )
            },
        )
    }

    private fun pendingKey(
        chapterIndex: Int,
        source: String,
        delta: AiTranslationStoryMemoryDelta,
    ): String = "$PENDING_PREFIX$chapterIndex:${stableId(source + GSON.toJson(delta))}"

    private suspend fun deleteBookMemory(bookUrl: String, key: String) {
        aiMemoryGateway.delete(bookConversationId(bookUrl), key)
    }

    private inline fun <reified T> List<AiMemory>.decodeValues(prefix: String): List<T> =
        asSequence()
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { memory -> runCatching { GSON.fromJson(memory.value, T::class.java) }.getOrNull() }
            .toList()

    private fun AiMemory.toWikiRecord(
        bookNames: Map<String, String>,
    ): AiTranslationStoryWikiRecord? {
        val bookUrl = scopeId.takeIf(String::isNotBlank) ?: return null
        val bookName = bookNames[bookUrl] ?: bookUrl
        return when {
            key.startsWith(ENTITY_PREFIX) -> decodeValue<AiTranslationStoryEntity>()?.let { entity ->
                AiTranslationStoryWikiRecord(
                    id = "$bookUrl|$key",
                    bookUrl = bookUrl,
                    bookName = bookName,
                    kind = AiTranslationStoryMemoryKind.ENTITY,
                    title = entity.target.ifBlank { entity.raw },
                    subtitle = listOf(entity.raw, entity.type, entity.description)
                        .filter(String::isNotBlank).joinToString(" · "),
                    chapterIndex = entity.firstChapterIndex.takeIf { it >= 0 },
                    imagePath = entity.imagePath.takeIf(String::isNotBlank),
                )
            }
            key.startsWith(RELATIONSHIP_PREFIX) ->
                decodeValue<AiTranslationStoryRelationship>()?.let { relationship ->
                    AiTranslationStoryWikiRecord(
                        id = "$bookUrl|$key",
                        bookUrl = bookUrl,
                        bookName = bookName,
                        kind = AiTranslationStoryMemoryKind.RELATIONSHIP,
                        title = "${relationship.source} → ${relationship.target}",
                        subtitle = listOf(relationship.relationship, relationship.description)
                            .filter(String::isNotBlank).joinToString(" · "),
                        chapterIndex = relationship.chapterIndex.takeIf { it >= 0 },
                    )
                }
            key.startsWith(WORLD_PREFIX) -> decodeValue<AiTranslationWorldEntry>()?.let { entry ->
                AiTranslationStoryWikiRecord(
                    id = "$bookUrl|$key",
                    bookUrl = bookUrl,
                    bookName = bookName,
                    kind = AiTranslationStoryMemoryKind.WORLD_BUILDING,
                    title = entry.target.ifBlank { entry.raw },
                    subtitle = listOf(entry.raw, entry.category, entry.description)
                        .filter(String::isNotBlank).joinToString(" · "),
                    chapterIndex = entry.chapterIndex.takeIf { it >= 0 },
                    imagePath = entry.imagePath.takeIf(String::isNotBlank),
                )
            }
            key.startsWith(TIMELINE_PREFIX) ->
                decodeValue<AiTranslationStoryTimeline>()?.let { timeline ->
                    AiTranslationStoryWikiRecord(
                        id = "$bookUrl|$key",
                        bookUrl = bookUrl,
                        bookName = bookName,
                        kind = AiTranslationStoryMemoryKind.TIMELINE,
                        title = timeline.chapterTitle.ifBlank { "Chương ${timeline.chapterIndex + 1}" },
                        subtitle = timeline.summary,
                        chapterIndex = timeline.chapterIndex.takeIf { it >= 0 },
                    )
                }
            else -> null
        }
    }

    private inline fun <reified T> AiMemory.decodeValue(): T? =
        runCatching { GSON.fromJson(value, T::class.java) }.getOrNull()

    private fun AiTranslationStoryMemorySnapshot.toDictionaryPairs(): List<DictPair> =
        AiTranslationStoryMemoryPipeline.selectContext(
            snapshot = this,
            chapterIndex = Int.MAX_VALUE,
            source = buildString {
                entities.forEach { append(it.raw).append('\n') }
                worldBuilding.forEach { append(it.raw).append('\n') }
            },
        )
            .entityDictionary

    private fun MutableList<DictPair>.replaceWith(values: List<DictPair>) {
        clear()
        addAll(values.distinctBy { it.original.lowercase() })
    }

    companion object {
        const val BOOTSTRAP_CHAPTER_COUNT = 15
        private const val MAX_ANALYSIS_DICTIONARY_PAIRS = 120
        private const val MAX_ANALYSIS_OUTPUT_TOKENS = 4_096
        private const val ANALYSIS_ATTEMPTS = 2
        private const val ANALYSIS_TEMPERATURE = 0.2f

        private const val STORY_MEMORY_PREFIX = "translation-story:"
        private const val ENTITY_PREFIX = "${STORY_MEMORY_PREFIX}entity:"
        private const val RELATIONSHIP_PREFIX = "translation-story:relationship:"
        private const val WORLD_PREFIX = "translation-story:world:"
        private const val TIMELINE_PREFIX = "translation-story:timeline:"
        private const val ANALYSIS_PREFIX = "translation-story:analysis:"
        private const val PENDING_PREFIX = "translation-story:pending:"

        fun entityKey(raw: String, senseKey: String = ""): String =
            ENTITY_PREFIX + stableId(TranslationMemoryCanonicalizer.identity(raw, senseKey))

        fun relationshipKey(value: AiTranslationStoryRelationship): String =
            RELATIONSHIP_PREFIX + value.chapterIndex + ":" + stableId(
                "${value.source}\u0000${value.target}\u0000${value.relationship}"
            )

        fun worldKey(value: AiTranslationWorldEntry): String =
            WORLD_PREFIX + stableId(
                TranslationMemoryCanonicalizer.identity(value.raw, value.senseKey)
            )

        fun timelineKey(chapterIndex: Int): String =
            TIMELINE_PREFIX + chapterIndex.toString().padStart(8, '0')

        fun stripVietnameseDiacritics(text: String): String {
            if (text.isBlank()) return ""
            val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            return normalized.replace(Regex("""\p{M}"""), "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
        }

        private fun bookConversationId(bookUrl: String): String = "${AiMemory.SCOPE_BOOK}:$bookUrl"

        private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

const val STORY_MEMORY_EXPORT_FORMAT = "legado-translation-story-memory-v1"

private fun String.toMemorySourceValue(): TranslationMemorySource = when (trim().uppercase()) {
    "USER" -> TranslationMemorySource.USER
    "QT" -> TranslationMemorySource.QT
    else -> TranslationMemorySource.AI
}
