package io.legado.app.domain.usecase

import androidx.annotation.Keep
import com.google.gson.JsonParser
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
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
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationStoryRelationship
import io.legado.app.domain.model.AiTranslationStoryTimeline
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import io.legado.app.domain.model.AiTranslationStreamAccumulator
import io.legado.app.domain.model.AiTranslationTokenBudget
import io.legado.app.domain.model.AiTranslationWorldEntry
import io.legado.app.domain.model.AiTranslationRefinerResult
import io.legado.app.domain.model.ContentChunker
import io.legado.app.domain.model.DictPair
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
) {

    private val bookLocks = ConcurrentHashMap<String, Mutex>()

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
                snapshot = loadSnapshot(book.bookUrl),
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
        val delta = result.story_memory ?: AiTranslationStoryMemoryDelta(
            entities = result.new_entities.map { entity ->
                AiTranslationStoryEntity(
                    raw = entity.raw,
                    target = entity.target,
                    type = entity.type.ifBlank { "term" },
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
                val normalizedEntities = normalizeEntities(delta.entities, source, chapter.index)
                val knownNames = (snapshot.entities + normalizedEntities).flatMap { entity ->
                    listOf(entity.raw, entity.target) + entity.aliases
                }.filter(String::isNotBlank).toSet()
                val normalizedRelationships = delta.relationships
                    .mapNotNull { relationship ->
                        relationship.takeIf { value ->
                            (value.source in knownNames || source.contains(value.source)) &&
                                (value.target in knownNames || source.contains(value.target)) &&
                                value.relationship.isNotBlank()
                        }?.copy(chapterIndex = chapter.index)
                    }
                val placeholders = normalizedRelationships
                    .flatMap { relationship -> listOf(relationship.source, relationship.target) }
                    .filter { name -> name !in knownNames && source.contains(name) }
                    .distinctBy(String::lowercase)
                    .map { raw ->
                        AiTranslationStoryEntity(
                            raw = raw,
                            target = raw,
                            type = "unknown",
                            description = "Placeholder created from a relationship endpoint",
                            firstChapterIndex = chapter.index,
                        )
                    }
                val incomingTimeline = delta.timeline ?: AiTranslationStoryTimeline()
                val timeline = incomingTimeline.copy(
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    characters = incomingTimeline.characters.filter { character ->
                        source.contains(character.raw) || knownNames.contains(character.raw)
                    },
                )
                val analysis = AiTranslationStoryAnalysis(
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    entities = (normalizedEntities + placeholders)
                        .distinctBy { it.raw.lowercase() },
                    relationships = normalizedRelationships
                        .distinctBy { "${it.source}\u0000${it.target}\u0000${it.relationship}".lowercase() },
                    worldBuilding = delta.worldBuilding
                        .filter { source.contains(it.raw) }
                        .map { it.copy(chapterIndex = chapter.index) }
                        .distinctBy { "${it.category}\u0000${it.raw}".lowercase() },
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
        return memories.toStorySnapshot()
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

    private fun List<AiMemory>.toStorySnapshot(): AiTranslationStoryMemorySnapshot =
        AiTranslationStoryMemorySnapshot(
            entities = decodeValues(ENTITY_PREFIX),
            relationships = decodeValues(RELATIONSHIP_PREFIX),
            worldBuilding = decodeValues(WORLD_PREFIX),
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
        )

    suspend fun upsertEntity(bookUrl: String, entity: AiTranslationStoryEntity) {
        require(entity.raw.isNotBlank() && entity.target.isNotBlank())
        upsertBookMemory(bookUrl, entityKey(entity.raw), AiMemory.TYPE_GLOSSARY, entity)
    }

    suspend fun upsertRelationship(bookUrl: String, relationship: AiTranslationStoryRelationship) {
        require(
            relationship.source.isNotBlank() && relationship.target.isNotBlank() &&
                relationship.relationship.isNotBlank()
        )
        upsertBookMemory(
            bookUrl,
            relationshipKey(relationship),
            AiMemory.TYPE_RELATIONSHIP,
            relationship,
        )
    }

    suspend fun upsertWorldEntry(bookUrl: String, entry: AiTranslationWorldEntry) {
        require(entry.raw.isNotBlank())
        upsertBookMemory(bookUrl, worldKey(entry), AiMemory.TYPE_FACT, entry)
    }

    suspend fun upsertTimeline(bookUrl: String, timeline: AiTranslationStoryTimeline) {
        require(timeline.chapterIndex >= 0 && timeline.summary.isNotBlank())
        upsertBookMemory(
            bookUrl,
            timelineKey(timeline.chapterIndex),
            AiMemory.TYPE_SUMMARY,
            timeline,
        )
    }

    suspend fun deleteEntity(bookUrl: String, raw: String) =
        deleteBookMemory(bookUrl, entityKey(raw))

    suspend fun deleteRelationship(bookUrl: String, relationship: AiTranslationStoryRelationship) =
        deleteBookMemory(bookUrl, relationshipKey(relationship))

    suspend fun deleteWorldEntry(bookUrl: String, entry: AiTranslationWorldEntry) =
        deleteBookMemory(bookUrl, worldKey(entry))

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
                taskType = AiTaskType.SUMMARIZE_CHAPTER,
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
        val existingEntities = previous.entities.associateBy { it.raw.lowercase() }
        val normalizedEntities = analysis.entities.map { discovered ->
            val existing = existingEntities[discovered.raw.lowercase()]
            if (existing == null) {
                discovered
            } else {
                existing.copy(
                    target = existing.target.ifBlank { discovered.target },
                    type = existing.type.ifBlank { discovered.type },
                    description = discovered.description.ifBlank { existing.description },
                    aliases = (existing.aliases + discovered.aliases).distinct(),
                    gender = existing.gender.ifBlank { discovered.gender },
                    rank = discovered.rank.ifBlank { existing.rank },
                    firstChapterIndex = listOf(existing.firstChapterIndex, discovered.firstChapterIndex)
                        .filter { it >= 0 }
                        .minOrNull() ?: analysis.chapterIndex,
                )
            }
        }
        normalizedEntities.forEach { upsertEntity(bookUrl, it) }
        analysis.relationships.forEach { upsertRelationship(bookUrl, it) }
        val existingWorldEntries = previous.worldBuilding.associateBy(::worldKey)
        analysis.worldBuilding.forEach { discovered ->
            val existing = existingWorldEntries[worldKey(discovered)]
            upsertWorldEntry(
                bookUrl,
                if (existing == null) {
                    discovered
                } else {
                    discovered.copy(
                        imagePath = existing.imagePath,
                        imagePrompt = existing.imagePrompt,
                        imageUpdatedAt = existing.imageUpdatedAt,
                    )
                }
            )
        }
        val existingRaw = existingEntities.keys
        val incomingTimeline = analysis.timeline.copy(
            characters = analysis.timeline.characters.map { character ->
                character.copy(
                    status = if (character.raw.lowercase() in existingRaw) "existing" else "new"
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
            upsertTimeline(bookUrl, normalizedTimeline)
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
        .filter { it.raw.isNotBlank() && it.target.isNotBlank() && source.contains(it.raw) }
        .map { entity ->
            entity.copy(firstChapterIndex = entity.firstChapterIndex.takeIf { it >= 0 } ?: chapterIndex)
        }
        .distinctBy { it.raw.lowercase() }

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
        AiTranslationStoryMemoryPipeline.selectContext(this, Int.MAX_VALUE, "")
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

        fun entityKey(raw: String): String = ENTITY_PREFIX + stableId(raw.trim().lowercase())

        fun relationshipKey(value: AiTranslationStoryRelationship): String =
            RELATIONSHIP_PREFIX + value.chapterIndex + ":" + stableId(
                "${value.source}\u0000${value.target}\u0000${value.relationship}"
            )

        fun worldKey(value: AiTranslationWorldEntry): String =
            WORLD_PREFIX + value.chapterIndex + ":" + stableId("${value.category}\u0000${value.raw}")

        fun timelineKey(chapterIndex: Int): String =
            TIMELINE_PREFIX + chapterIndex.toString().padStart(8, '0')

        private fun bookConversationId(bookUrl: String): String = "${AiMemory.SCOPE_BOOK}:$bookUrl"

        private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

const val STORY_MEMORY_EXPORT_FORMAT = "legado-translation-story-memory-v1"
