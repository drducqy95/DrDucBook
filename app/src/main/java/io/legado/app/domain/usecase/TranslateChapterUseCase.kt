package io.legado.app.domain.usecase

import androidx.annotation.Keep
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.NmtTranslationGateway
import io.legado.app.domain.gateway.MlKitTranslationGateway
import io.legado.app.domain.gateway.NmtDecodeConfig
import io.legado.app.domain.gateway.AiPromptPresetGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiFailureKind
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelRegistry
import io.legado.app.domain.model.AiProviderException
import io.legado.app.domain.model.AiProviderFailureClassifier
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTranslationChunkContext
import io.legado.app.domain.model.AiTranslationChunkPlanner
import io.legado.app.domain.model.AiTranslationEntity
import io.legado.app.domain.model.AiTranslationStoryContext
import io.legado.app.domain.model.AiTranslationProtectionProtocol
import io.legado.app.domain.model.AiTranslationRefinePipeline
import io.legado.app.domain.model.AiTranslationRefinerResult
import io.legado.app.domain.model.AiTranslationTokenBudget
import io.legado.app.domain.model.AiTranslationLayoutProtocol
import io.legado.app.domain.model.AiTranslationStreamAccumulator
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.ContentChunker
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.PartialTranslationAssembler
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryRevision
import io.legado.app.domain.model.QuickTranslationPronounMode
import io.legado.app.domain.model.RetryReason
import io.legado.app.domain.model.TextChunk
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.TranslationPromptStage
import io.legado.app.domain.model.LocalAiTranslationBudgetPlanner
import io.legado.app.domain.model.VietnameseTranslationPostProcessor
import io.legado.app.domain.model.QUICK_DICTIONARY_IGNORE_TARGET
import io.legado.app.domain.model.toQuickPhoneticPair
import io.legado.app.domain.model.toQuickTranslationPair
import io.legado.app.domain.model.dictionaryAwareContentHash
import io.legado.app.domain.model.normalizedForRuntime
import io.legado.app.domain.model.protectsMachineTranslation
import io.legado.app.domain.model.usesQuickDictionaryForTranslation
import io.legado.app.help.book.BookHelp
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postForm
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranslateChapterUseCase(
    private val aiTextGateway: AiTextGateway,
    private val translationCacheGateway: TranslationCacheGateway,
    private val dictionaryGateway: DictionaryGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val quickTranslationGateway: QuickTranslationGateway,
    private val quickDictionaryGateway: QuickDictionaryGateway,
    private val nmtTranslationGateway: NmtTranslationGateway,
    private val mlKitTranslationGateway: MlKitTranslationGateway,
    private val aiPromptPresetGateway: AiPromptPresetGateway,
    private val translateDynamicUiTextUseCase: TranslateDynamicUiTextUseCase,
    private val translationStoryMemoryUseCase: TranslationStoryMemoryUseCase? = null,
) {

    data class TranslationProgress(
        val currentChunk: Int,
        val totalChunks: Int,
        val mixedContent: String? = null,
        val translatedChunkIndices: Set<Int> = emptySet(),
        val stage: String? = null,
    )

    private data class PreferredCachedTranslation(
        val content: String,
        val provider: String,
        val targetLanguage: String,
        val revision: io.legado.app.domain.model.TranslationRevision? = null,
    )

    companion object {
        private const val MAX_DICTIONARY_PAIRS = 80
        private const val STREAM_PREVIEW_MIN_CHARS = 20
        private const val STREAM_PREVIEW_INTERVAL_NANOS = 75_000_000L
        private const val LOCAL_AI_PREFERRED_CHUNK_CHARS = 256
        private const val QUICK_TRANSLATOR_CHUNK_CHARS = 2_000
        private const val ML_KIT_CHUNK_CHARS = 4_000
        private const val MAX_ML_KIT_RESIDUAL_REPAIR_PASSES = 2
        private const val LOCAL_AI_ADJACENT_CONTEXT_CHARS = 128
        private const val DEFAULT_AI_ADJACENT_CONTEXT_CHARS = 400
        private val STRUCTURAL_PARAGRAPH_BREAK = Regex("[\\t ]*(?:\\r?\\n[\\t ]*)+")
    }

    private val dictionaryLock = Any()

    private sealed interface ChunkTranslationEvent {
        data class Partial(val chunkIndex: Int, val text: String) : ChunkTranslationEvent
        data class Completed(
            val chunk: TextChunk,
            val result: Result<String>,
        ) : ChunkTranslationEvent
    }

    /**
     * Translate source-owned labels and book metadata for display only.
     *
     * This path shares the selected provider, target language and provider-specific policy with
     * chapter translation, while keeping a separate permanent cache and never writing translated
     * values back to [Book]. NMT still uses its own character and tokenizer limits directly; it
     * never goes through the AI prompt pipeline or AI chunk planner.
     */
    suspend fun executeDynamicUiText(
        scopeKey: String,
        originalText: String,
        book: Book? = null,
        contextText: String = originalText,
        forceRetranslate: Boolean = false,
    ): Result<String> = translateDynamicUiTextUseCase.execute(
        scopeKey = scopeKey,
        originalText = originalText,
        book = book,
        contextText = contextText,
        forceRetranslate = forceRetranslate,
    )

    suspend fun clearDynamicUiTranslationCache() {
        translateDynamicUiTextUseCase.clearCache()
    }

    /** Produces one non-persistent dictionary suggestion with the provider chosen by the user. */
    suspend fun executeSuggestion(
        text: String,
        provider: String,
        book: Book? = null,
        previousContext: String = "",
        nextContext: String = "",
        targetLanguage: String = TranslationConstants.TARGET_VIETNAMESE,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty source text"))
        if (!TranslationConstants.supportsTargetLanguage(provider, targetLanguage)) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported target language"))
        }
        runCatching {
            val quickEntries = book
                ?.let { quickDictionaryGateway.getEffectiveEntries(it, previousContext + text + nextContext) }
                .orEmpty()
            val quickTerms = quickEntries.mapNotNull { it.toQuickTranslationPair() }
            val ignoredTerms = quickTerms
                .filter { it.translation == QUICK_DICTIONARY_IGNORE_TARGET }
                .map { it.original }
            val source = removeQuickIgnoredTerms(text, ignoredTerms)
            val bookTerms = book?.let(dictionaryGateway::getBookDictionaries)?.pairs.orEmpty()
            val quickPronounMode = book?.getQuickTranslationPronounModeOverride()
            val dictionaries = mergeDictionaryTerms(
                primaryTerms = bookTerms,
                fallbackTerms = quickTerms.filterNot {
                    it.translation == QUICK_DICTIONARY_IGNORE_TARGET
                },
            )
            val translated = when (provider) {
                TranslationConstants.PROVIDER_QUICK_TRANSLATOR -> quickTranslationGateway.translate(
                    text = source,
                    projectTerms = dictionaries,
                    customPhonetics = quickEntries.mapNotNull { it.toQuickPhoneticPair() },
                    pronounMode = quickPronounMode,
                )
                TranslationConstants.PROVIDER_NMT -> try {
                    nmtTranslationGateway.translate(
                        text = source,
                        dictionary = dictionaries,
                        config = currentNmtDecodeConfig(),
                    ).text
                } finally {
                    nmtTranslationGateway.close()
                }
                TranslationConstants.PROVIDER_GOOGLE -> translateWithGoogle(source, targetLanguage)
                    .getOrThrow()
                TranslationConstants.PROVIDER_ML_KIT -> mlKitTranslationGateway.translate(
                    text = source,
                    targetLanguage = targetLanguage,
                )
                TranslationConstants.PROVIDER_APP_AI -> {
                    val preset = resolveTranslationPreset()
                        ?: error("No AI translation preset configured")
                    val promptStages = TranslationPromptStage.entries.associateWith { stage ->
                        aiPromptPresetGateway.getEnabledByTaskType(stage.taskType)
                            .map { it.instruction }
                            .filter(String::isNotBlank)
                    }
                    translateWithAiGateway(
                        text = source,
                        targetLanguage = targetLanguage,
                        preset = preset,
                        dictionaries = dictionaries,
                        onUpdate = null,
                        retryReason = null,
                        promptStages = promptStages,
                        isExplicitRetranslation = false,
                        context = AiTranslationChunkContext(
                            previous = previousContext.takeLast(400),
                            next = nextContext.take(400),
                        ),
                        routeSessionKey = book?.bookUrl,
                        onPartial = {},
                    ).getOrThrow()
                }
                else -> error("Unknown translation provider: $provider")
            }
            val repairedTranslation = if (provider == TranslationConstants.PROVIDER_ML_KIT) {
                repairMlKitResidualCjk(
                    text = translated,
                    targetLanguage = targetLanguage,
                    sourceLanguage = inferMlKitSourceLanguageHint(source, targetLanguage),
                    dictionaries = dictionaries,
                    quickPhonetics = quickEntries.mapNotNull { it.toQuickPhoneticPair() },
                )
            } else {
                translated
            }
            translationQualityError(
                source = source,
                translated = repairedTranslation,
                targetLanguage = targetLanguage,
            )?.let { throw it }
            postProcessTranslation(repairedTranslation, targetLanguage)
        }
    }

    suspend fun computeSuggestionDependencyHash(
        text: String,
        provider: String,
        book: Book? = null,
    ): String = withContext(Dispatchers.IO) {
        val quickEntries = if (book != null && provider.supportsQuickDictionaryPipeline()) {
            quickDictionaryGateway.getEffectiveEntries(book, text)
        } else {
            emptyList()
        }
        val dictionaryTerms = mergeDictionaryTerms(
            primaryTerms = book?.let(dictionaryGateway::getBookDictionaries)?.pairs.orEmpty(),
            fallbackTerms = quickEntries.mapNotNull { it.toQuickTranslationPair() } +
                quickEntries.mapNotNull { it.toQuickPhoneticPair() },
        )
        chunkTranslationDependencyHash(
            sourceContent = text,
            provider = provider,
            dictionaryTerms = dictionaryTerms,
            quickTranslationPackVersion = quickTranslationGateway.packVersionFor(
                book?.getQuickTranslationPronounModeOverride(),
            ),
            providerConfigurationRevision = providerConfigurationRevision(provider),
            computeHash = translationCacheGateway::computeContentHash,
        )
    }

    suspend fun execute(
        book: Book,
        bookChapter: BookChapter,
        forceRetranslate: Boolean = false,
        provider: String = TranslationConfig.llmProvider,
        targetLanguage: String = TranslationConfig.llmTargetLanguage,
        onProgress: (TranslationProgress) -> Unit,
        onTranslateStarted: () -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!TranslationConstants.supportsTargetLanguage(provider, targetLanguage)) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "The selected provider does not support target language: $targetLanguage"
                    )
                )
            }
            val preset = if (provider == TranslationConstants.PROVIDER_APP_AI) {
                resolveTranslationPreset()
                    ?: return@withContext Result.failure(Exception("No AI translation preset configured"))
            } else {
                null
            }
            val promptStages = if (provider == TranslationConstants.PROVIDER_APP_AI) {
                TranslationPromptStage.entries.associateWith { stage ->
                    aiPromptPresetGateway.getEnabledByTaskType(stage.taskType)
                        .map { it.instruction }
                        .filter(String::isNotBlank)
                }
            } else {
                emptyMap()
            }
            val providerConfigRevision = providerConfigurationRevision(provider)

            val originalContent = BookHelp.getContent(book, bookChapter)
                ?: return@withContext Result.failure(Exception("Failed to read original content"))
            onProgress(TranslationProgress(0, 0, stage = "SOURCE_READ chars=${originalContent.length}"))
            val quickPronounMode = book.getQuickTranslationPronounModeOverride()
            val quickTranslationPackVersion = quickTranslationGateway.packVersionFor(quickPronounMode)
            val dictionaryRevision = quickDictionaryGateway.getEffectiveRevision(
                book = book,
                context = originalContent,
            )

            val rawContentHash = translationCacheGateway.computeContentHash(originalContent)
            val dictionaryContentHash = dictionaryAwareContentHash(
                originalContentHash = rawContentHash,
                provider = provider,
                dictionaryRevision = dictionaryRevision,
                quickTranslationPackVersion = quickTranslationPackVersion,
            )
            val contentHash = applyProviderConfigurationRevision(
                contentHash = dictionaryContentHash,
                providerConfigurationRevision = providerConfigRevision,
                computeHash = translationCacheGateway::computeContentHash,
            )

            val protectedRevision = findPreferredProtectedRevision(
                book = book,
                bookChapter = bookChapter,
                targetLanguage = targetLanguage,
                rawContentHash = rawContentHash,
            )
            if (protectedRevision != null) {
                if (forceRetranslate) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "This translation was edited or finalized. Unlock it before translating again."
                        )
                    )
                }
                val displayTranslation = postProcessTranslation(
                    protectedRevision.content,
                    targetLanguage,
                )
                onProgress(TranslationProgress(1, 1, displayTranslation, setOf(0)))
                return@withContext Result.success(displayTranslation)
            }

            if (!forceRetranslate) {
                val cachedTranslation = findPreferredMachineCache(
                    book = book,
                    bookChapter = bookChapter,
                    originalContent = originalContent,
                    targetLanguage = targetLanguage,
                    rawContentHash = rawContentHash,
                    dictionaryRevision = dictionaryRevision,
                    quickTranslationPackVersion = quickTranslationPackVersion,
                    requestedProvider = provider,
                    requestedContentHash = contentHash,
                )
                if (cachedTranslation != null) {
                    val displayTranslation = postProcessTranslation(
                        cachedTranslation.content,
                        targetLanguage,
                    )
                    if (isUsableCachedTranslation(
                            source = originalContent,
                            translated = displayTranslation,
                            targetLanguage = targetLanguage,
                            provider = cachedTranslation.provider,
                        )
                    ) {
                        onProgress(
                            TranslationProgress(
                                1,
                                1,
                                displayTranslation,
                                emptySet(),
                                stage = "CACHE_SELECTED provider=${cachedTranslation.provider}",
                            )
                        )
                        return@withContext Result.success(displayTranslation)
                    }
                }
            }

            val cachedTranslation = if (forceRetranslate) null else translationCacheGateway.readCurrentTranslation(
                book = book,
                bookChapter = bookChapter,
                targetLanguage = targetLanguage,
                originalContentHash = contentHash,
                provider = provider,
            )
            if (cachedTranslation != null) {
                val displayTranslation = postProcessTranslation(
                    cachedTranslation,
                    targetLanguage,
                )
                if (isUsableCachedTranslation(
                        source = originalContent,
                        translated = displayTranslation,
                        targetLanguage = targetLanguage,
                        provider = provider,
                    )
                ) {
                    onProgress(TranslationProgress(1, 1, displayTranslation, emptySet()))
                    return@withContext Result.success(displayTranslation)
                }
            }

            // Load book dictionary for consistent terminology
            val bookDictionary = dictionaryGateway.getBookDictionaries(book)
            val storyContext = try {
                translationStoryMemoryUseCase?.prepareForTranslation(
                    book = book,
                    currentChapter = bookChapter,
                    currentContent = originalContent,
                    preset = preset,
                    baseDictionary = bookDictionary.pairs,
                ) ?: AiTranslationStoryContext()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Story memory enriches translation but must never block a chapter when it is
                // absent, incomplete, or temporarily cannot be analyzed.
                AiTranslationStoryContext()
            }
            val storyMemoryRevision = GSON.toJson(storyContext)
            onProgress(
                TranslationProgress(
                    0,
                    0,
                    stage = "STORY_CONTEXT_LOADED entities=${storyContext.currentEntities.size} " +
                        "relationships=${storyContext.currentRelationships.size} " +
                        "world=${storyContext.currentWorldBuilding.size} " +
                        "timelines=${storyContext.recentTimelines.size}",
                )
            )
            var activeStoryContext = storyContext
            val storyContextLock = Any()
            val storyContextProvider: () -> AiTranslationStoryContext = {
                synchronized(storyContextLock) { activeStoryContext }
            }
            val quickEntries = if (provider.supportsQuickDictionaryPipeline()) {
                quickDictionaryGateway.getEffectiveEntries(book, originalContent)
            } else {
                emptyList()
            }
            val scopedQuickTranslatorTerms = quickEntries.mapNotNull { it.toQuickTranslationPair() }
            val scopedQuickTerms = scopedQuickTranslatorTerms
                .filterNot { it.translation == QUICK_DICTIONARY_IGNORE_TARGET }
            val scopedQuickIgnoredTerms = scopedQuickTranslatorTerms
                .filter { it.translation == QUICK_DICTIONARY_IGNORE_TARGET }
                .map { it.original }
            val scopedQuickPhonetics = quickEntries.mapNotNull { it.toQuickPhoneticPair() }
            val dictionaries = mergeDictionaryTerms(
                primaryTerms = bookDictionary.pairs + storyContext.entityDictionary,
                fallbackTerms = scopedQuickTerms,
            )
                .toMutableList()

            // Callback to update dictionary pairs immediately (persist as soon as discovered)
            val onDictionaryUpdate: (List<DictPair>) -> Unit = { newPairs ->
                val merged = synchronized(dictionaryLock) {
                    mergeDictionaryPairs(dictionaries, newPairs)
                }
                if (merged) {
                    synchronized(dictionaryLock) {
                        dictionaryGateway.updateBookDic(book, dictionaries.toList())
                    }
                }
            }

            val onStoryMemoryUpdate: suspend (AiTranslationRefinerResult, String) -> Unit = { result, source ->
                val memoryResult = translationStoryMemoryUseCase?.persistRefinerResult(
                    book = book,
                    chapter = bookChapter,
                    source = source,
                    result = result,
                )
                val stage = memoryResult?.fold(
                    onSuccess = { count ->
                        translationStoryMemoryUseCase.let { memoryUseCase ->
                            runCatching {
                                val snapshot = memoryUseCase.loadSnapshot(book.bookUrl)
                                val refreshed = io.legado.app.domain.model.AiTranslationStoryMemoryPipeline
                                    .selectContext(snapshot, bookChapter.index, originalContent)
                                synchronized(storyContextLock) { activeStoryContext = refreshed }
                            }
                        }
                        "MEMORY_COMMITTED records=$count"
                    },
                    onFailure = { error ->
                        "MEMORY_PENDING warning=${error.message ?: error::class.java.simpleName}"
                    },
                ) ?: "MEMORY_DISABLED"
                onProgress(
                    TranslationProgress(
                        currentChunk = 0,
                        totalChunks = 0,
                        stage = stage,
                    )
                )
            }

            if (provider == TranslationConstants.PROVIDER_NMT && targetLanguage != "vi") {
                return@withContext Result.failure(
                    IllegalArgumentException("Offline NMT currently supports Vietnamese output only")
                )
            }

            val localAiBudget = preset
                ?.takeIf { it.model.provider.protocol == AiProtocol.LOCAL_GGUF }
                ?.let {
                    LocalAiTranslationBudgetPlanner.plan(
                        contextWindow = it.model.contextWindow,
                        providerMaxOutputTokens = it.model.maxOutputTokens,
                        configuredMaxOutputTokens = it.params.maxOutputTokens,
                        configuredMaxSourceChars = minOf(
                            it.runtimeOptions.maxInputChars.coerceAtLeast(10),
                            TranslationConfig.MAX_CHUNK_CHARS,
                        ),
                        preferredChunkChars = LOCAL_AI_PREFERRED_CHUNK_CHARS,
                        adjacentContextChars = 0,
                        fixedPromptChars = estimateFixedTranslationPromptChars(
                            preset = it,
                            dictionaries = dictionaries,
                            promptStages = promptStages,
                        ),
                    )
                }
            val maxCharsPerChunk = when {
                localAiBudget != null -> localAiBudget.maxSourceChars
                provider == TranslationConstants.PROVIDER_NMT ->
                    TranslationConfig.nmtMaxCharsPerChunk
                provider == TranslationConstants.PROVIDER_QUICK_TRANSLATOR ->
                    QUICK_TRANSLATOR_CHUNK_CHARS
                provider == TranslationConstants.PROVIDER_ML_KIT -> ML_KIT_CHUNK_CHARS
                provider == TranslationConstants.PROVIDER_APP_AI -> preset
                    .let {
                        resolveAiRuntimeMaxInputChars(
                            runtimeOptions = it?.runtimeOptions,
                            globalFallback = TranslationConfig.aiMaxCharsPerChunk,
                        )
                    }
                else -> TranslationConfig.llmMaxCharsPerChunk
            }
            val chunks = ContentChunker.chunk(originalContent, maxCharsPerChunk)
            if (chunks.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to chunk content"))
            }
            onProgress(
                TranslationProgress(
                    0,
                    chunks.size,
                    stage = "CHUNKS_PLANNED count=${chunks.size} maxChars=$maxCharsPerChunk",
                )
            )
            fun currentChunkContentHash(chunk: TextChunk): String {
                val dependencyTerms = synchronized(dictionaryLock) {
                    mergeDictionaryTerms(
                        primaryTerms = dictionaries.toList(),
                        fallbackTerms = scopedQuickTranslatorTerms + scopedQuickPhonetics,
                    )
                }
                return chunkTranslationDependencyHash(
                    sourceContent = chunk.content,
                    provider = provider,
                    dictionaryTerms = dependencyTerms,
                    quickTranslationPackVersion = quickTranslationPackVersion,
                    providerConfigurationRevision = providerConfigRevision,
                    storyMemoryRevision = storyMemoryRevision,
                    computeHash = translationCacheGateway::computeContentHash,
                )
            }
            val chunkContentHashes = chunks.associate { chunk ->
                chunk.index to currentChunkContentHash(chunk)
            }

            val translatedChunks = mutableMapOf<Int, String>()
            val displayFallbackChunks = mutableMapOf<Int, String>()
            val pendingChunks = mutableListOf<TextChunk>()
            suspend fun checkpointTranslatedChunks() {
                chunks.forEach { chunk ->
                    translatedChunks[chunk.index]?.let { translatedContent ->
                        val refreshedContentHash = currentChunkContentHash(chunk)
                        if (refreshedContentHash == chunkContentHashes.getValue(chunk.index)) {
                            return@let
                        }
                        translationCacheGateway.saveChunk(
                            book = book,
                            bookChapter = bookChapter,
                            targetLanguage = targetLanguage,
                            chunkIndex = chunk.index,
                            originalChunkContent = chunk.content,
                            originalContentHash = refreshedContentHash,
                            provider = provider,
                            status = TranslationCache.STATUS_SUCCESS,
                            translatedContent = translatedContent,
                            errorMessage = null,
                        )
                    }
                }
            }
            fun stableDisplayChunks(): Map<Int, String> {
                if (displayFallbackChunks.isEmpty()) return translatedChunks
                return buildMap(displayFallbackChunks.size + translatedChunks.size) {
                    putAll(displayFallbackChunks)
                    putAll(translatedChunks)
                }
            }

            // Load already cached chunks
            for (chunk in chunks) {
                val cached = translationCacheGateway.getCachedChunk(
                    book,
                    bookChapter,
                    targetLanguage,
                    chunk.index,
                    provider,
                )
                val cachedContent = cached?.translatedChunkContent
                val hasUsableCachedContent = cached != null &&
                    cachedContent != null &&
                    cached.isSuccess &&
                    cached.originalChunkContent == chunk.content &&
                    isUsableCachedTranslation(
                        source = removeQuickIgnoredTerms(chunk.content, scopedQuickIgnoredTerms),
                        translated = cachedContent,
                        targetLanguage = targetLanguage,
                        provider = provider,
                    )
                if (
                    hasUsableCachedContent &&
                    !forceRetranslate
                ) {
                    translatedChunks[chunk.index] = cachedContent
                } else {
                    if (hasUsableCachedContent) {
                        displayFallbackChunks[chunk.index] = requireNotNull(cachedContent)
                    }
                    pendingChunks.add(chunk)
                }
            }

            // If we have partial cached chunks, report initial mixed content
            if (translatedChunks.isNotEmpty() || displayFallbackChunks.isNotEmpty()) {
                val displayChunks = stableDisplayChunks()
                val mixedContent = postProcessTranslation(
                    PartialTranslationAssembler.assemble(chunks, displayChunks),
                    targetLanguage,
                )
                onProgress(TranslationProgress(
                    translatedChunks.size,
                    chunks.size,
                    mixedContent,
                    displayChunks.keys
                ))
            }

            if (pendingChunks.isEmpty()) {
                val sortedChunks = chunks.sortedBy { it.index }.mapNotNull { translatedChunks[it.index]?.let { content -> TextChunk(it.index, content, it.paragraphIndices) } }
                val mergedContent = postProcessTranslation(
                    ContentChunker.merge(sortedChunks),
                    targetLanguage,
                )
                translationCacheGateway.writeTranslation(
                    book = book,
                    bookChapter = bookChapter,
                    targetLanguage = targetLanguage,
                    content = mergedContent,
                    originalContentHash = contentHash,
                    provider = provider,
                    rawContentHash = rawContentHash,
                    dictionaryRevision = dictionaryRevision.cacheToken,
                )
                if (provider == TranslationConstants.PROVIDER_APP_AI) {
                    translationStoryMemoryUseCase?.markChapterAnalyzed(book.bookUrl, bookChapter)
                }
                onProgress(TranslationProgress(chunks.size, chunks.size, mergedContent, chunks.map { it.index }.toSet()))
                return@withContext Result.success(mergedContent)
            }

            onTranslateStarted()
            onProgress(TranslationProgress(0, chunks.size, stage = "TRANSLATION_STARTED provider=$provider"))
            var translationError: Throwable? = null
            val streamingChunks = mutableMapOf<Int, String>()
            coroutineScope translationScope@{
                val concurrentChunks = resolveTranslationChunkConcurrency(
                    provider = provider,
                    hasLocalAiBudget = localAiBudget != null,
                    storyMemoryEnabled = translationStoryMemoryUseCase != null,
                    aiConcurrentRequests = resolveAiRuntimeConcurrentRequests(
                        runtimeOptions = preset?.runtimeOptions,
                        globalFallback = TranslationConfig.aiConcurrentChunks,
                    ),
                    standardConcurrentRequests = TranslationConfig.llmConcurrentChunks,
                )
                val chunkGroups = pendingChunks.chunked(concurrentChunks)

                for (group in chunkGroups) {
                    val events = Channel<ChunkTranslationEvent>(Channel.UNLIMITED)
                    group.forEach { chunk ->
                        launch {
                            val result = try {
                                translateAndCacheChunk(
                                    chunk = chunk,
                                    book = book,
                                    bookChapter = bookChapter,
                                    targetLanguage = targetLanguage,
                                    contentHash = chunkContentHashes.getValue(chunk.index),
                                    cacheContentHash = { currentChunkContentHash(chunk) },
                                    provider = provider,
                                    preset = preset,
                                    dictionaries = dictionaries,
                                    onDictionaryUpdate = onDictionaryUpdate,
                                    promptStages = promptStages,
                                    allowCachedChunk = !forceRetranslate,
                                    quickPhonetics = scopedQuickPhonetics,
                                    quickTranslatorTerms = scopedQuickTranslatorTerms,
                                    quickIgnoredTerms = scopedQuickIgnoredTerms,
                                    quickPronounMode = quickPronounMode,
                                    isExplicitRetranslation = forceRetranslate,
                                    aiContext = if (provider == TranslationConstants.PROVIDER_APP_AI) {
                                        AiTranslationChunkPlanner.contextFor(
                                            chunks = chunks,
                                            chunkIndex = chunk.index,
                                            maxCharsPerChunk = maxCharsPerChunk,
                                            maxContextChars = if (localAiBudget != null) {
                                                LOCAL_AI_ADJACENT_CONTEXT_CHARS
                                            } else {
                                                DEFAULT_AI_ADJACENT_CONTEXT_CHARS
                                            },
                                        )
                                    } else {
                                        AiTranslationChunkContext()
                                    },
                                    storyContextProvider = storyContextProvider,
                                    onStoryMemoryUpdate = onStoryMemoryUpdate,
                                    onStage = { stage ->
                                        onProgress(
                                            TranslationProgress(
                                                currentChunk = translatedChunks.size,
                                                totalChunks = chunks.size,
                                                mixedContent = stableDisplayChunks().let { displayChunks ->
                                                    PartialTranslationAssembler.assemble(chunks, displayChunks)
                                                },
                                                translatedChunkIndices = stableDisplayChunks().keys.toSet(),
                                                stage = stage,
                                            )
                                        )
                                    },
                                    routeSessionKey = book.bookUrl,
                                    onPartialTranslation = { partial ->
                                        events.trySend(
                                            ChunkTranslationEvent.Partial(chunk.index, partial)
                                        )
                                    },
                                )
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                            events.send(ChunkTranslationEvent.Completed(chunk, result))
                        }
                    }
                    var completedInGroup = 0
                    while (completedInGroup < group.size) {
                        when (val event = events.receive()) {
                            is ChunkTranslationEvent.Partial -> {
                                streamingChunks[event.chunkIndex] = event.text
                                val mixedContent = postProcessTranslation(
                                    PartialTranslationAssembler.assembleStreaming(
                                        originalChunks = chunks,
                                        translatedMap = stableDisplayChunks(),
                                        partialMap = streamingChunks,
                                    ),
                                    targetLanguage,
                                )
                                onProgress(
                                    TranslationProgress(
                                        currentChunk = translatedChunks.size,
                                        totalChunks = chunks.size,
                                        mixedContent = mixedContent,
                                        translatedChunkIndices = stableDisplayChunks().keys.toSet(),
                                    )
                                )
                            }

                            is ChunkTranslationEvent.Completed -> {
                                completedInGroup += 1
                                streamingChunks.remove(event.chunk.index)
                                if (event.result.isSuccess) {
                                    translatedChunks[event.chunk.index] = event.result.getOrThrow()
                                    val displayChunks = stableDisplayChunks()
                                    val mixedContent = postProcessTranslation(
                                        PartialTranslationAssembler.assemble(
                                            chunks,
                                            displayChunks,
                                        ),
                                        targetLanguage,
                                    )
                                    onProgress(
                                        TranslationProgress(
                                            translatedChunks.size,
                                            chunks.size,
                                            mixedContent,
                                            displayChunks.keys.toSet(),
                                        )
                                    )
                                } else {
                                    translationError = translationError
                                        ?: event.result.exceptionOrNull()
                                        ?: IllegalStateException(
                                            "Provider $provider failed chunk ${event.chunk.index} without an error"
                                        )
                                    val stableMixedContent = postProcessTranslation(
                                        PartialTranslationAssembler.assemble(
                                            chunks,
                                            stableDisplayChunks(),
                                        ),
                                        targetLanguage,
                                    )
                                    onProgress(
                                        TranslationProgress(
                                            translatedChunks.size,
                                            chunks.size,
                                            stableMixedContent,
                                            stableDisplayChunks().keys.toSet(),
                                        )
                                    )
                                }
                            }
                        }
                    }
                    events.close()
                    if (translationError != null) return@translationScope
                }
            }

            checkpointTranslatedChunks()
            translationError?.let {
                return@withContext Result.failure(it)
            }

            if (translatedChunks.size != chunks.size) {
                return@withContext Result.failure(Exception("Translation incomplete"))
            }

            val allTranslatedChunks = chunks.sortedBy { it.index }.mapNotNull { chunk ->
                translatedChunks[chunk.index]?.let { content -> TextChunk(chunk.index, content, chunk.paragraphIndices) }
            }
            val mergedContent = postProcessTranslation(
                ContentChunker.merge(allTranslatedChunks),
                targetLanguage,
            )
            translationCacheGateway.writeTranslation(
                book = book,
                bookChapter = bookChapter,
                targetLanguage = targetLanguage,
                content = mergedContent,
                originalContentHash = contentHash,
                provider = provider,
                rawContentHash = rawContentHash,
                dictionaryRevision = dictionaryRevision.cacheToken,
            )
            if (provider == TranslationConstants.PROVIDER_APP_AI) {
                translationStoryMemoryUseCase?.markChapterAnalyzed(book.bookUrl, bookChapter)
            }

            onProgress(TranslationProgress(chunks.size, chunks.size, stage = "CACHE_COMMITTED"))

            onProgress(TranslationProgress(chunks.size, chunks.size, mergedContent, chunks.map { it.index }.toSet()))
            Result.success(mergedContent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (provider == TranslationConstants.PROVIDER_NMT) {
                runCatching { nmtTranslationGateway.close() }
            }
        }
    }

    /**
     * Merge new pairs into existing list:
     * - If original exists, replace the translation
     * - If new, add to list
     * - Keep at most MAX_DICTIONARY_PAIRS
     * @return true if any changes were made
     */
    private fun mergeDictionaryPairs(existing: MutableList<DictPair>, newPairs: List<DictPair>): Boolean {
        var changed = false
        for (newPair in newPairs) {
            val existingIndex = existing.indexOfFirst { it.original == newPair.original }
            if (existingIndex >= 0) {
                if (existing[existingIndex].translation != newPair.translation) {
                    existing[existingIndex] = newPair
                    changed = true
                }
            } else {
                existing.add(newPair)
                changed = true
            }
        }

        // Keep early important names and the most recently discovered terms.
        if (existing.size > MAX_DICTIONARY_PAIRS) {
            val headCount = MAX_DICTIONARY_PAIRS / 2
            val tailCount = MAX_DICTIONARY_PAIRS - headCount
            val trimmed = existing.take(headCount) + existing.takeLast(tailCount)
            existing.clear()
            existing.addAll(trimmed)
            changed = true
        }
        return changed
    }

    private suspend fun translateAndCacheChunk(
        chunk: TextChunk,
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String,
        cacheContentHash: () -> String,
        provider: String,
        preset: AiTaskPresetConfig?,
        dictionaries: MutableList<DictPair>,
        onDictionaryUpdate: (List<DictPair>) -> Unit,
        promptStages: Map<TranslationPromptStage, List<String>>,
        allowCachedChunk: Boolean,
        quickPhonetics: List<DictPair>,
        quickTranslatorTerms: List<DictPair>,
        quickIgnoredTerms: List<String>,
        quickPronounMode: QuickTranslationPronounMode?,
        isExplicitRetranslation: Boolean,
        aiContext: AiTranslationChunkContext,
        storyContextProvider: () -> AiTranslationStoryContext,
        onStoryMemoryUpdate: suspend (AiTranslationRefinerResult, String) -> Unit,
        onStage: (String) -> Unit,
        routeSessionKey: String,
        onPartialTranslation: (String) -> Unit,
    ): Result<String> {
        val sourceContent = removeQuickIgnoredTerms(chunk.content, quickIgnoredTerms)
        val existingCache =
            translationCacheGateway.getCachedChunk(
                book,
                bookChapter,
                targetLanguage,
                chunk.index,
                provider,
            )
        if (allowCachedChunk && existingCache?.isSuccess == true &&
            existingCache.originalContentHash == contentHash &&
            existingCache.provider == provider &&
            existingCache.originalChunkContent == chunk.content &&
            existingCache.translatedChunkContent != null &&
            isUsableCachedTranslation(
                source = sourceContent,
                translated = existingCache.translatedChunkContent,
                targetLanguage = targetLanguage,
                provider = provider,
            )
        ) {
            return Result.success(existingCache.translatedChunkContent)
        }

        val result = translateChunkWithRetry(
            chunk,
            targetLanguage,
            provider,
            preset,
            dictionaries,
            onDictionaryUpdate,
            promptStages,
            quickPhonetics,
            quickTranslatorTerms,
            quickIgnoredTerms,
            quickPronounMode,
            isExplicitRetranslation,
            aiContext,
            storyContextProvider,
            onStoryMemoryUpdate,
            onStage,
            routeSessionKey,
            onPartialTranslation,
        )
        if (result.isSuccess) {
            translationCacheGateway.saveChunk(
                book, bookChapter, targetLanguage,
                chunk.index, chunk.content, cacheContentHash(),
                provider,
                TranslationCache.STATUS_SUCCESS, result.getOrThrow(), null
            )
        } else if (existingCache?.isSuccess != true) {
            val errorMessage = result.exceptionOrNull()?.message
                ?: "Provider $provider did not report a failure reason"
            translationCacheGateway.saveChunk(
                book, bookChapter, targetLanguage,
                chunk.index, chunk.content, cacheContentHash(),
                provider,
                TranslationCache.STATUS_FAILED, null, errorMessage
            )
        }
        return result
    }

    private suspend fun translateChunkWithRetry(
        chunk: TextChunk,
        targetLanguage: String,
        provider: String,
        preset: AiTaskPresetConfig?,
        dictionaries: MutableList<DictPair>,
        onDictionaryUpdate: (List<DictPair>) -> Unit,
        promptStages: Map<TranslationPromptStage, List<String>>,
        quickPhonetics: List<DictPair>,
        quickTranslatorTerms: List<DictPair>,
        quickIgnoredTerms: List<String>,
        quickPronounMode: QuickTranslationPronounMode?,
        isExplicitRetranslation: Boolean,
        aiContext: AiTranslationChunkContext,
        storyContextProvider: () -> AiTranslationStoryContext,
        onStoryMemoryUpdate: suspend (AiTranslationRefinerResult, String) -> Unit,
        onStage: (String) -> Unit,
        routeSessionKey: String,
        onPartialTranslation: (String) -> Unit,
        splitDepth: Int = 0,
    ): Result<String> {
        var lastError: Exception? = null
        var lastRetryReason: RetryReason? = null
        var completedAiAttempts = 0
        val configuredRetryCount = if (provider == TranslationConstants.PROVIDER_APP_AI) {
            resolveAiRuntimeRetryCount(
                runtimeOptions = preset?.runtimeOptions,
                globalFallback = TranslationConfig.llmRetryCount,
            )
        } else {
            TranslationConfig.llmRetryCount
        }
        val pipelineAttempts = configuredRetryCount.coerceIn(0, 5) + 1
        for (pipelineAttempt in 1..pipelineAttempts) {
            val dictSnapshot = synchronized(dictionaryLock) { dictionaries.toList() }
            val sourceContent = removeQuickIgnoredTerms(chunk.content, quickIgnoredTerms)
            val result = when (provider) {
                TranslationConstants.PROVIDER_GOOGLE -> translateWithGoogle(sourceContent, targetLanguage)
                TranslationConstants.PROVIDER_ML_KIT -> runCatching {
                    translateWithMlKitPreservingLayout(
                        chunk = chunk,
                        sourceContent = sourceContent,
                        targetLanguage = targetLanguage,
                        dictionaries = mergeDictionaryTerms(
                            primaryTerms = dictSnapshot,
                            fallbackTerms = quickTranslatorTerms,
                        ),
                        quickPhonetics = quickPhonetics,
                    )
                }
                TranslationConstants.PROVIDER_QUICK_TRANSLATOR -> {
                    if (targetLanguage != "vi") {
                        Result.failure(
                            IllegalArgumentException("Quick Translator currently supports Vietnamese output only")
                        )
                    } else {
                        Result.success(
                            quickTranslationGateway.translate(
                                text = sourceContent,
                                projectTerms = mergeDictionaryTerms(
                                    primaryTerms = dictSnapshot,
                                    fallbackTerms = quickTranslatorTerms,
                                ),
                                customPhonetics = quickPhonetics,
                                pronounMode = quickPronounMode,
                            )
                        )
                    }
                }
                TranslationConstants.PROVIDER_NMT -> runCatching {
                    nmtTranslationGateway.translate(
                        text = sourceContent,
                        dictionary = dictSnapshot,
                        config = currentNmtDecodeConfig(),
                    ).text
                }
                TranslationConstants.PROVIDER_APP_AI -> preset?.let { configuredPreset ->
                    translateWithAiGateway(
                        text = sourceContent,
                        targetLanguage = targetLanguage,
                        preset = configuredPreset,
                        dictionaries = dictSnapshot,
                        onUpdate = onDictionaryUpdate,
                        retryReason = lastRetryReason,
                        promptStages = promptStages,
                        isExplicitRetranslation = isExplicitRetranslation,
                        context = aiContext,
                        storyContext = storyContextProvider(),
                        onStoryMemoryUpdate = onStoryMemoryUpdate,
                        onStage = onStage,
                        layoutChunk = chunk,
                        routeSessionKey = routeSessionKey,
                        routeRetryOffset = pipelineAttempt - 1,
                        onPartial = onPartialTranslation,
                    )
                } ?: Result.failure(Exception("No AI translation preset configured"))
                else -> Result.failure(IllegalArgumentException("Unknown translation provider: $provider"))
            }
            if (result.isSuccess) {
                val translated = result.getOrThrow()
                val qualityError = translationQualityError(
                    source = sourceContent,
                    translated = translated,
                    targetLanguage = targetLanguage,
                )
                if (qualityError != null) {
                    val failure = if (provider == TranslationConstants.PROVIDER_APP_AI) {
                        classifyAiTranslationFailure(
                            error = qualityError,
                            preset = preset,
                            attemptOffset = completedAiAttempts,
                        )
                    } else {
                        null
                    }
                    completedAiAttempts = failure?.failure?.attempt ?: completedAiAttempts
                    lastError = failure ?: qualityError
                    lastRetryReason = RetryReason.PARSE_ERROR
                    continue
                }
                val restored = ContentChunker.restoreLayout(chunk, translated)
                if (restored != null) {
                    return Result.success(restored)
                }
                val layoutError = TranslationLayoutException(
                    "Translation parse error: changed paragraph count for chunk ${chunk.index}"
                )
                if (provider == TranslationConstants.PROVIDER_APP_AI) {
                    val failure = classifyAiTranslationFailure(
                        error = layoutError,
                        preset = preset,
                        attemptOffset = completedAiAttempts,
                    )
                    completedAiAttempts = failure.failure.attempt
                    lastError = failure
                    lastRetryReason = retryReasonFor(failure.failure.kind)
                } else {
                    lastError = layoutError
                    lastRetryReason = RetryReason.PARSE_ERROR
                }
                continue
            }
            val rawError = result.exceptionOrNull()?.let { error ->
                error as? Exception ?: Exception(error.message, error)
            } ?: Exception("Translation provider returned a failed result without an error")
            if (provider == TranslationConstants.PROVIDER_APP_AI) {
                val failure = classifyAiTranslationFailure(
                    error = rawError,
                    preset = preset,
                    attemptOffset = completedAiAttempts,
                )
                completedAiAttempts = failure.failure.attempt
                lastError = failure
                lastRetryReason = retryReasonFor(failure.failure.kind)
                if (!failure.failure.retryable) {
                    return Result.failure(failure)
                }
                if (rawError.message.orEmpty().contains("json=truncated") ||
                    pipelineAttempt >= pipelineAttempts
                ) break
            } else {
                lastError = rawError
                lastRetryReason = parseRetryReason(rawError)
            }
        }
        val terminalError = lastError ?: Exception("Translation pipeline ended without a result")
        val splitMaxChars = if (
            provider == TranslationConstants.PROVIDER_APP_AI &&
            lastRetryReason == RetryReason.PARSE_ERROR
        ) {
            aiTranslationFallbackSplitMaxChars(chunk.content.length, splitDepth)
        } else {
            null
        }
        if (splitMaxChars != null) {
            val splitChunks = ContentChunker.chunk(chunk.content, splitMaxChars)
            if (splitChunks.size > 1) {
                onStage(
                    "AI_STAGE=chunk_split depth=${splitDepth + 1} " +
                        "count=${splitChunks.size} maxChars=$splitMaxChars"
                )
                val translatedSplitChunks = mutableListOf<TextChunk>()
                for (splitChunk in splitChunks) {
                    val internalContext = AiTranslationChunkPlanner.contextFor(
                        chunks = splitChunks,
                        chunkIndex = splitChunk.index,
                        maxCharsPerChunk = splitMaxChars,
                    )
                    val splitContext = internalContext.copy(
                        previous = if (splitChunk.index == 0) {
                            aiContext.previous
                        } else {
                            internalContext.previous
                        },
                        next = if (splitChunk.index == splitChunks.lastIndex) {
                            aiContext.next
                        } else {
                            internalContext.next
                        },
                    )
                    val splitResult = translateChunkWithRetry(
                        chunk = splitChunk,
                        targetLanguage = targetLanguage,
                        provider = provider,
                        preset = preset,
                        dictionaries = dictionaries,
                        onDictionaryUpdate = onDictionaryUpdate,
                        promptStages = promptStages,
                        quickPhonetics = quickPhonetics,
                        quickTranslatorTerms = quickTranslatorTerms,
                        quickIgnoredTerms = quickIgnoredTerms,
                        quickPronounMode = quickPronounMode,
                        isExplicitRetranslation = isExplicitRetranslation,
                        aiContext = splitContext,
                        storyContextProvider = storyContextProvider,
                        onStoryMemoryUpdate = onStoryMemoryUpdate,
                        onStage = onStage,
                        routeSessionKey = "$routeSessionKey:split:${splitDepth + 1}:${splitChunk.index}",
                        onPartialTranslation = {},
                        splitDepth = splitDepth + 1,
                    )
                    if (splitResult.isFailure) return splitResult
                    translatedSplitChunks += splitChunk.copy(
                        content = splitResult.getOrThrow(),
                    )
                }
                val merged = ContentChunker.merge(translatedSplitChunks)
                ContentChunker.restoreLayout(chunk, merged)?.let {
                    return Result.success(it)
                }
            }
        }
        return Result.failure(terminalError)
    }

    private fun classifyAiTranslationFailure(
        error: Throwable,
        preset: AiTaskPresetConfig?,
        attemptOffset: Int,
    ): AiProviderException {
        if (error is AiProviderException) {
            if (attemptOffset <= 0) return error
            return AiProviderException(
                failure = error.failure.copy(
                    attempt = error.failure.attempt + attemptOffset,
                ),
                cause = error,
            )
        }
        return AiProviderFailureClassifier.classify(
            error = error,
            provider = preset?.model?.provider?.name.orEmpty().ifBlank { "AI Provider" },
            model = preset?.model?.modelId.orEmpty(),
            attemptOffset = attemptOffset,
        )
    }

    private fun retryReasonFor(kind: AiFailureKind): RetryReason = when (kind) {
        AiFailureKind.ROUTE_UNAVAILABLE -> RetryReason.ROUTE_UNAVAILABLE
        AiFailureKind.CONFIGURATION -> RetryReason.CONFIG_ERROR
        AiFailureKind.AUTHENTICATION -> RetryReason.AUTH_ERROR
        AiFailureKind.RATE_LIMIT -> RetryReason.RATE_LIMIT
        AiFailureKind.QUOTA -> RetryReason.QUOTA_ERROR
        AiFailureKind.TIMEOUT -> RetryReason.TIMEOUT
        AiFailureKind.NETWORK -> RetryReason.NETWORK_ERROR
        AiFailureKind.PROTOCOL -> RetryReason.PROTOCOL_ERROR
        AiFailureKind.EMPTY_OUTPUT -> RetryReason.EMPTY_RESPONSE
        AiFailureKind.PARSE_ERROR -> RetryReason.PARSE_ERROR
        AiFailureKind.CANCELLED -> RetryReason.CANCELLED
        AiFailureKind.SERVER -> RetryReason.SERVER_ERROR
        AiFailureKind.UNKNOWN -> RetryReason.UNKNOWN
    }

    private suspend fun resolveTranslationPreset(): AiTaskPresetConfig? {
        aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)?.let { return it }
        return aiProfileGateway.getTaskPreset(AiTaskType.CHAT)?.copy(
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            name = "Translation fallback",
            promptTemplate = TranslationConstants.DEFAULT_PROMPT,
        )
    }

    suspend fun currentProviderConfigurationRevision(provider: String): String {
        return providerConfigurationRevision(provider)
    }

    private suspend fun findPreferredProtectedRevision(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        rawContentHash: String,
    ): io.legado.app.domain.model.TranslationRevision? {
        return TranslationConstants.preferredContentProviders(targetLanguage)
            .mapIndexedNotNull { priority, identity ->
                translationCacheGateway.getCurrentRevision(
                    book = book,
                    bookChapter = bookChapter,
                    targetLanguage = identity.targetLanguage,
                    provider = identity.provider,
                    currentRawContentHash = rawContentHash,
                )?.takeIf { it.protectsMachineTranslation }
                    ?.let { revision -> priority to revision }
            }
            .sortedWith(
                compareBy<Pair<Int, io.legado.app.domain.model.TranslationRevision>> {
                    when (it.second.sourceStatus) {
                        io.legado.app.domain.model.RevisionStatus.FINAL -> 0
                        io.legado.app.domain.model.RevisionStatus.USER_EDITED -> 1
                        else -> 2
                    }
                }.thenBy { it.first }
            )
            .firstOrNull()
            ?.second
    }

    private suspend fun findPreferredMachineCache(
        book: Book,
        bookChapter: BookChapter,
        originalContent: String,
        targetLanguage: String,
        rawContentHash: String,
        dictionaryRevision: QuickDictionaryRevision,
        quickTranslationPackVersion: String,
        requestedProvider: String,
        requestedContentHash: String,
    ): PreferredCachedTranslation? {
        val candidateIdentities = if (requestedProvider.isNotBlank()) {
            listOf(TranslationConstants.TranslationProviderIdentity(requestedProvider, targetLanguage))
                .filter { TranslationConstants.supportsTargetLanguage(it.provider, it.targetLanguage) }
        } else {
            TranslationConstants.preferredContentProviders(targetLanguage)
        }
        for (identity in candidateIdentities) {
            val identityContentHash = if (identity.provider == requestedProvider) {
                requestedContentHash
            } else {
                val dictionaryContentHash = dictionaryAwareContentHash(
                    originalContentHash = rawContentHash,
                    provider = identity.provider,
                    dictionaryRevision = dictionaryRevision,
                    quickTranslationPackVersion = quickTranslationPackVersion,
                )
                applyProviderConfigurationRevision(
                    contentHash = dictionaryContentHash,
                    providerConfigurationRevision = providerConfigurationRevision(identity.provider),
                    computeHash = translationCacheGateway::computeContentHash,
                )
            }
            val content = translationCacheGateway.readCurrentTranslation(
                book = book,
                bookChapter = bookChapter,
                targetLanguage = identity.targetLanguage,
                originalContentHash = identityContentHash,
                provider = identity.provider,
            ) ?: continue
            val display = postProcessTranslation(content, targetLanguage)
            if (isUsableCachedTranslation(
                    source = originalContent,
                    translated = display,
                    targetLanguage = targetLanguage,
                    provider = identity.provider,
                )
            ) {
                return PreferredCachedTranslation(
                    content = content,
                    provider = identity.provider,
                    targetLanguage = identity.targetLanguage,
                )
            }
        }
        return null
    }

    private fun currentNmtDecodeConfig() = NmtDecodeConfig(
        maxSourceTokens = TranslationConfig.nmtSourceTokenBudget,
        maxSourceChars = TranslationConfig.nmtMaxCharsPerChunk,
        sourcePrompt = TranslationConfig.nmtSourcePrompt,
        maxNewTokens = TranslationConfig.nmtMaxNewTokens,
        repetitionPenalty = TranslationConfig.nmtRepetitionPenalty,
        noRepeatNgramSize = if (TranslationConfig.nmtNoRepeatBigram) 2 else 0,
        retryMissingRequiredTerms = TranslationConfig.nmtRetryMissingTerms,
    )

    private suspend fun providerConfigurationRevision(provider: String): String = when (provider) {
        TranslationConstants.PROVIDER_NMT -> currentNmtDecodeConfig().toString()
        TranslationConstants.PROVIDER_APP_AI -> {
            val preset = resolveTranslationPreset()
            val promptStages = TranslationPromptStage.entries.associate { stage ->
                stage.storageKey to aiPromptPresetGateway.getEnabledByTaskType(stage.taskType)
                    .map { it.instruction }
                    .filter(String::isNotBlank)
            }
            GSON.toJson(
                linkedMapOf(
                    "pipeline" to AI_TRANSLATION_PIPELINE_REVISION,
                    "preset_id" to preset?.id.orEmpty(),
                    "model_id" to preset?.model?.modelId.orEmpty(),
                    "provider_id" to preset?.model?.provider?.id.orEmpty(),
                    "prompt" to preset?.promptTemplate.orEmpty(),
                    "params" to preset?.params,
                    "route_profile_id" to preset?.runtimeOptions?.routeProfileId,
                    "prompt_stages" to promptStages,
                )
            )
        }
        else -> ""
    }

    private suspend fun translateWithGoogle(text: String, targetLanguage: String): Result<String> {
        val url = "https://translate.googleapis.com/translate_a/single"
        val response = okHttpClient.newCallStrResponse {
            url(url)
            postForm(
                mapOf(
                    "client" to "gtx",
                    "sl" to "auto",
                    "tl" to targetLanguage,
                    "dj" to "1",
                    "dt" to "t",
                    "ie" to "UTF-8",
                    "q" to text,
                )
            )
        }
        return if (response.isSuccessful()) {
            runCatching {
                val json = GSON.fromJson(response.body, GoogleTranslateResponse::class.java)
                json?.sentences?.mapNotNull { it.trans }?.joinToString("").orEmpty()
            }.fold(
                onSuccess = { translatedText ->
                    if (translatedText.isNotEmpty()) {
                        Result.success(translatedText)
                    } else {
                        Result.failure(Exception("Empty translation result"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } else {
            Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
        }
    }

    private suspend fun translateWithMlKitPreservingLayout(
        chunk: TextChunk,
        sourceContent: String,
        targetLanguage: String,
        dictionaries: List<DictPair>,
        quickPhonetics: List<DictPair>,
    ): String {
        val paragraphCount = chunk.paragraphSeparators.size + 1
        val sourceLanguage = inferMlKitSourceLanguageHint(sourceContent, targetLanguage)
        val paragraphParts = splitForExpectedParagraphCount(sourceContent, paragraphCount)
            ?: return repairMlKitResidualCjk(
                text = mlKitTranslationGateway.translate(
                    text = sourceContent,
                    targetLanguage = targetLanguage,
                    sourceLanguage = sourceLanguage,
                ),
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage,
                dictionaries = dictionaries,
                quickPhonetics = quickPhonetics,
            )
        return buildList(paragraphParts.size) {
            for (paragraph in paragraphParts) {
                val translated = if (paragraph.isBlank()) {
                    paragraph
                } else {
                    mlKitTranslationGateway.translate(
                        text = paragraph,
                        targetLanguage = targetLanguage,
                        sourceLanguage = sourceLanguage,
                    ).trim()
                }
                add(
                    repairMlKitResidualCjk(
                        text = translated,
                        targetLanguage = targetLanguage,
                        sourceLanguage = sourceLanguage,
                        dictionaries = dictionaries,
                        quickPhonetics = quickPhonetics,
                    )
                )
            }
        }.joinToString("\n\n")
    }

    private suspend fun repairMlKitResidualCjk(
        text: String,
        targetLanguage: String,
        sourceLanguage: String?,
        dictionaries: List<DictPair>,
        quickPhonetics: List<DictPair>,
    ): String {
        if (targetLanguage != TranslationConstants.TARGET_VIETNAMESE) {
            return normalizeCjkPunctuation(text)
        }
        var current = repairResidualCjkForVietnamese(
            text = text,
            targetLanguage = targetLanguage,
            translateResidual = { residual ->
                quickTranslationGateway.translate(residual, dictionaries, quickPhonetics)
            },
            phoneticResidual = { residual ->
                quickTranslationGateway.hanViet(residual, quickPhonetics)
            },
        )
        repeat(MAX_ML_KIT_RESIDUAL_REPAIR_PASSES) {
            if (!current.hasCjkSourceCodePoints()) return current
            val retranslated = replaceCjkSourceRuns(current) { run ->
                val translated = try {
                    mlKitTranslationGateway.translate(
                        text = run.value,
                        targetLanguage = targetLanguage,
                        sourceLanguage = sourceLanguage,
                    ).trim().ifBlank { run.value }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    run.value
                }
                translated
            }
            val repaired = repairResidualCjkForVietnamese(
                text = retranslated,
                targetLanguage = targetLanguage,
                translateResidual = { residual ->
                    quickTranslationGateway.translate(residual, dictionaries, quickPhonetics)
                },
                phoneticResidual = { residual ->
                    quickTranslationGateway.hanViet(residual, quickPhonetics)
                },
            )
            if (repaired == current) return current
            current = repaired
        }
        return current
    }

    private fun splitForExpectedParagraphCount(
        text: String,
        expectedCount: Int,
    ): List<String>? {
        val clean = text.trim()
        if (expectedCount <= 1) return listOf(clean)
        val parts = clean.split(STRUCTURAL_PARAGRAPH_BREAK)
        return parts.takeIf { it.size == expectedCount }
    }

    private suspend fun translateWithAiGateway(
        text: String,
        targetLanguage: String,
        preset: AiTaskPresetConfig,
        dictionaries: List<DictPair>,
        onUpdate: ((List<DictPair>) -> Unit)?,
        retryReason: RetryReason?,
        promptStages: Map<TranslationPromptStage, List<String>>,
        isExplicitRetranslation: Boolean,
        context: AiTranslationChunkContext,
        storyContext: AiTranslationStoryContext = AiTranslationStoryContext(),
        onStoryMemoryUpdate: suspend (AiTranslationRefinerResult, String) -> Unit = { _, _ -> },
        onStage: (String) -> Unit = {},
        layoutChunk: TextChunk? = null,
        onPartial: (String) -> Unit,
        routeSessionKey: String? = null,
        routeRetryOffset: Int = 0,
    ): Result<String> {
        if (targetLanguage == "en" && isMostlyEnglish(text)) {
            return Result.success(text)
        }
        if (targetLanguage == "zh" && isMostlyChinese(text)) {
            return Result.success(text)
        }
        val isLocalAi = preset.model.provider.protocol == AiProtocol.LOCAL_GGUF
        val isReasoningModel = AiCapability.REASONING in preset.model.capabilities ||
            AiCapability.REASONING in AiModelRegistry.inferCapabilities(preset.model.modelId)

        val promptDictionaries = selectRelevantDictionaries(
            dictionaries = dictionaries,
            sourceAndContext = context.previous + text + context.next,
        )
        val retryInstruction = buildRetryInstruction(retryReason)
        val protectedText = AiTranslationProtectionProtocol.protect(text)
        val protectedInstruction = buildProtectedTokenInstruction(protectedText)
        val protectedSource = protectedText.value
        val targetLanguageName = getLanguageDisplayName(targetLanguage)
        val includeRetranslateStage = shouldIncludeRetranslatePrompt(
            isExplicitRetranslation = isExplicitRetranslation,
            hasRetryReason = retryReason != null,
        )
        val contextPack = AiTranslationRefinePipeline.buildContextPack(
            text = protectedSource,
            targetLanguage = targetLanguage,
            targetLanguageName = targetLanguageName,
            context = context,
            storyContext = storyContext,
            dictionaries = promptDictionaries,
            promptStages = promptStages,
            includeRetranslateStage = includeRetranslateStage,
            quickDraft = { segment ->
                quickTranslationGateway.translate(
                    text = segment,
                    projectTerms = promptDictionaries,
                    customPhonetics = emptyList(),
                )
            },
        )
        onStage("AI_STAGE=context_pack_ready segments=${contextPack.raw_segments.size}")
        val expectedIds = AiTranslationRefinePipeline.expectedIds(contextPack)
        val systemPrompt = AiTranslationRefinePipeline.buildSystemPrompt(
            configuredPrompt = preset.promptTemplate,
            targetLanguageName = targetLanguageName,
            retryInstruction = retryInstruction,
            protectedInstruction = protectedInstruction,
        )
        val userPrompt = AiTranslationRefinePipeline.buildUserPrompt(contextPack)
        val fixedPromptChars = systemPrompt.length + userPrompt.length - protectedSource.length + 64
        val outputTokenBudget = if (isLocalAi) {
            LocalAiTranslationBudgetPlanner.plan(
                contextWindow = preset.model.contextWindow,
                providerMaxOutputTokens = preset.model.maxOutputTokens,
                configuredMaxOutputTokens = preset.params.maxOutputTokens,
                configuredMaxSourceChars = text.length.coerceAtLeast(10),
                preferredChunkChars = text.length.coerceAtLeast(10),
                adjacentContextChars = 0,
                fixedPromptChars = fixedPromptChars,
                sourceChars = text.length,
            ).maxOutputTokens
        } else {
            AiTranslationTokenBudget.forSourceChars(
                sourceChars = text.length,
                configuredLimit = preset.params.maxOutputTokens,
                providerLimit = preset.model.maxOutputTokens,
                reasoningModel = isReasoningModel,
                structuredJson = true,
            )
        }
        val params = preset.params.copy(
            temperature = preset.params.temperature
                ?: preset.model.defaultParams.temperature
                ?: if (isLocalAi) 0.7f else TranslationConstants.DEFAULT_TEMPERATURE,
            topP = preset.params.topP
                ?: preset.model.defaultParams.topP
                ?: if (isLocalAi) 0.6f else null,
            topK = preset.params.topK
                ?: preset.model.defaultParams.topK
                ?: if (isLocalAi) 20 else null,
            repetitionPenalty = preset.params.repetitionPenalty
                ?: preset.model.defaultParams.repetitionPenalty
                ?: if (isLocalAi) 1.05f else null,
            reasoningLevel = if (preset.params.reasoningLevel == AiReasoningLevel.AUTO) {
                AiReasoningLevel.OFF
            } else {
                preset.params.reasoningLevel
            },
            maxOutputTokens = outputTokenBudget,
        )
        val request = AiGenerateRequest(
            model = preset.model,
            messages = if (isLocalAi) {
                listOf(AiMessage(AiMessageRole.USER, systemPrompt + "\n\n" + userPrompt))
            } else {
                listOf(
                    AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                    AiMessage(AiMessageRole.USER, userPrompt),
                )
            },
            params = params,
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            routeProfileId = preset.runtimeOptions.routeProfileId,
            routeSessionKey = routeSessionKey,
            routeRetryOffset = routeRetryOffset,
            routeSemanticFailureKind = if (retryReason == RetryReason.PARSE_ERROR) {
                AiFailureKind.PARSE_ERROR
            } else {
                null
            },
        )
        val rawContent = AiTranslationStreamAccumulator()
        var lastPreviewLength = 0
        var lastPreviewAt = 0L
        try {
            onStage("AI_STAGE=provider_stream_started")
            aiTextGateway.generateStream(request).collect { event ->
                if (event is AiStreamEvent.Content) {
                    rawContent.append(event.text)
                    val preview = AiTranslationRefinePipeline.preview(
                        rawOutput = rawContent.toString(),
                        expectedIds = expectedIds,
                        targetLanguage = targetLanguage,
                    )
                        ?: return@collect
                    val restoredPreview = protectedText.restore(preview)
                    val now = System.nanoTime()
                    if (lastPreviewLength == 0 ||
                        restoredPreview.length - lastPreviewLength >= STREAM_PREVIEW_MIN_CHARS ||
                        now - lastPreviewAt >= STREAM_PREVIEW_INTERVAL_NANOS
                    ) {
                        onPartial(
                            layoutChunk?.let { ContentChunker.previewWithLayout(it, restoredPreview) }
                                ?: restoredPreview
                        )
                        lastPreviewLength = restoredPreview.length
                        lastPreviewAt = now
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onStage("AI_STAGE=provider_error ${error.message ?: error::class.java.simpleName}")
            return Result.failure(error)
        }
        val completedContent = rawContent.toString()
        if (completedContent.isBlank()) {
            return Result.failure(Exception("Empty translation result"))
        }
        val refinerResult = runCatching {
            AiTranslationRefinePipeline.parseRefinerOutput(
                rawOutput = completedContent,
                expectedIds = expectedIds,
                targetLanguage = targetLanguage,
            )
        }.getOrElse { error ->
            val outputDescription = AiTranslationRefinePipeline.describeJsonOutput(completedContent)
            onStage(
                "AI_STAGE=parse_error $outputDescription " +
                    (error.message ?: error::class.java.simpleName)
            )
            return Result.failure(
                TranslationLayoutException(
                    "Translation parse error: $outputDescription; " +
                        (error.message ?: error::class.java.simpleName)
                )
            )
        }
        onStage(
            "AI_STAGE=json_parsed segments=${refinerResult.refined_segments.size} " +
                "entities=${refinerResult.story_memory?.entities?.size ?: refinerResult.new_entities.size} " +
                "relationships=${refinerResult.story_memory?.relationships?.size ?: refinerResult.relationships.size} " +
                "world=${refinerResult.story_memory?.worldBuilding?.size ?: 0} " +
                "timeline=${refinerResult.story_memory?.timeline != null}",
        )
        val assembledText = AiTranslationRefinePipeline.assemble(refinerResult)
        val finalText = when (targetLanguage) {
            TranslationConstants.TARGET_VIETNAMESE -> normalizeCjkPunctuation(assembledText)
            "zh" -> filterHighEnglishAiParagraphs(assembledText)
            else -> assembledText
        }
        val protectedTokenViolations = protectedText.integrityViolations(finalText)
        if (protectedTokenViolations.isNotEmpty()) {
            return Result.failure(
                TranslationLayoutException(
                    "Translation parse error: changed protected token layout: " +
                        protectedTokenViolations.first()
                )
            )
        }
        val restoredText = protectedText.restore(finalText)
        if (layoutChunk != null && ContentChunker.restoreLayout(layoutChunk, restoredText) == null) {
            return Result.failure(
                TranslationLayoutException(
                    "Translation parse error: changed paragraph count for chunk ${layoutChunk.index}"
                )
            )
        }
        val extractedPairs = refinerResult.new_entities
            .mapNotNull { entity -> entity.toDictionaryPair(promptDictionaries) }
            .take(10)
        if (extractedPairs.isNotEmpty()) {
            onUpdate?.invoke(extractedPairs)
        }
        onStoryMemoryUpdate(refinerResult, text)
        return Result.success(restoredText)
    }

    private fun AiTranslationEntity.toDictionaryPair(
        existingDictionaries: List<DictPair>,
    ): DictPair? {
        val original = raw.trim()
        val translation = target.trim()
        if (original.isEmpty() || translation.isEmpty()) return null
        if (existingDictionaries.any { it.original == original }) return null
        val normalizedType = type.lowercase()
        val dictionaryType = when {
            normalizedType.contains("character") ||
                normalizedType.contains("person") ||
                normalizedType.contains("name") -> QuickDictionaryType.NAME
            normalizedType.contains("pronoun") -> QuickDictionaryType.PRONOUN
            normalizedType.contains("vietphrase") -> QuickDictionaryType.VIETPHRASE
            normalizedType.contains("luat") -> QuickDictionaryType.LUAT_NHAN
            else -> QuickDictionaryType.TERM
        }
        return DictPair(original, translation, dictionaryType)
    }

    private fun estimateFixedTranslationPromptChars(
        preset: AiTaskPresetConfig,
        dictionaries: List<DictPair>,
        promptStages: Map<TranslationPromptStage, List<String>>,
    ): Int {
        return AiTranslationRefinePipeline.estimatePromptChars(
            presetPromptChars = preset.promptTemplate.length,
            dictionaries = dictionaries,
            promptStages = promptStages,
        )
    }

    private fun selectRelevantDictionaries(
        dictionaries: List<DictPair>,
        sourceAndContext: String,
    ): List<DictPair> {
        if (dictionaries.isEmpty() || sourceAndContext.isBlank()) return emptyList()
        return dictionaries.asSequence()
            .filter { pair ->
                pair.original.isNotBlank() && sourceAndContext.contains(pair.original)
            }
            .distinctBy(DictPair::original)
            .sortedByDescending { it.original.length }
            .take(MAX_DICTIONARY_PAIRS)
            .toList()
    }

    private fun buildRetryInstruction(retryReason: RetryReason?): String {
        return when (retryReason) {
            RetryReason.EMPTY_RESPONSE -> "\nPrevious attempt returned empty content. Return the required JSON object with every refined_segments id."
            RetryReason.PARSE_ERROR -> "\nPrevious attempt failed JSON, segment-id, layout, or no-CJK validation. Return JSON only and include every expected id exactly once."
            RetryReason.RATE_LIMIT,
            RetryReason.ROUTE_UNAVAILABLE,
            RetryReason.SERVER_ERROR,
            RetryReason.AUTH_ERROR,
            RetryReason.CONFIG_ERROR,
            RetryReason.QUOTA_ERROR,
            RetryReason.PROTOCOL_ERROR,
            RetryReason.TIMEOUT,
            RetryReason.NETWORK_ERROR,
            RetryReason.UNKNOWN,
            RetryReason.PERMANENT_FAILURE,
            RetryReason.CANCELLED,
            null -> ""
        }
    }

    private fun buildProtectedTokenInstruction(
        protectedText: AiTranslationProtectionProtocol.ProtectedText,
    ): String {
        if (!protectedText.hasProtectedTokens) return ""
        val examples = protectedText.replacements
            .asSequence()
            .map { it.placeholder }
            .take(5)
            .joinToString(", ")
        return "\nProtected tokens must be copied exactly once and kept in their original order: " +
            "$examples. Do not translate, remove, split, duplicate, or reorder any listed token."
    }

    private fun getLanguageDisplayName(code: String): String {
        return when (code) {
            "vi" -> "Vietnamese"
            "zh" -> "Simplified Chinese"
            "en" -> "English"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "fr" -> "French"
            "de" -> "German"
            "es" -> "Spanish"
            "ru" -> "Russian"
            "ar" -> "Arabic"
            else -> TranslationConstants.targetLanguages.find { it.first == code }?.second ?: code
        }
    }

    private fun postProcessTranslation(text: String, targetLanguage: String): String {
        return if (targetLanguage == TranslationConstants.TARGET_VIETNAMESE) {
            VietnameseTranslationPostProcessor.capitalizeSentences(
                normalizeCjkPunctuation(text)
            )
        } else {
            text
        }
    }

    /** Reject a structurally valid but untranslated AI chunk before it reaches the cache. */
    private fun translationQualityError(
        source: String,
        translated: String,
        targetLanguage: String,
    ): TranslationQualityException? {
        return if (hasUntranslatedCjkForVietnamese(source, translated, targetLanguage)) {
            val translatedCjk = translated.countCjkSourceCodePoints()
            TranslationQualityException(
                "Translation changed source language: ${translatedCjk} CJK chars remain"
            )
        } else {
            null
        }
    }

    private fun isUsableCachedTranslation(
        source: String,
        translated: String,
        targetLanguage: String,
        provider: String,
    ): Boolean {
        if (translationQualityError(source, translated, targetLanguage) != null) return false
        if (provider == TranslationConstants.PROVIDER_APP_AI &&
            (AiTranslationLayoutProtocol.containsMarker(translated) ||
                containsLegacyAiTranslationContract(translated))
        ) {
            return false
        }
        return true
    }

    private fun containsLegacyAiTranslationContract(text: String): Boolean =
        LEGACY_AI_TRANSLATION_SECTION_PATTERN.containsMatchIn(text)

    private fun isMostlyEnglish(text: String): Boolean {
        if (text.isEmpty()) return false
        val englishChars =
            text.count { it in 'A'..'Z' || it in 'a'..'z' || it in ".,!?;:'\"-()[]{}-" }
        return englishChars.toDouble() / text.length > 0.8
    }

    private fun isMostlyChinese(text: String): Boolean {
        if (text.isEmpty()) return false
        val chinesePunctuation = "。，！？；：“”‘’（）【】《》"
        val chineseChars = text.count {
            it in '一'..'鿿' || it in chinesePunctuation
        }
        return chineseChars.toDouble() / text.length > 0.8
    }

    private fun parseRetryReason(error: Exception?): RetryReason? {
        val message = error?.message ?: return null
        return when {
            message.contains("429") -> RetryReason.RATE_LIMIT
            message.contains("500") || message.contains("502") || message.contains("503") || message.contains("504") -> RetryReason.SERVER_ERROR
            message.contains("401") || message.contains("403") -> RetryReason.AUTH_ERROR
            message.contains("timeout", ignoreCase = true) -> RetryReason.TIMEOUT
            message.contains("HTTP") -> RetryReason.UNKNOWN
            else -> null
        }
    }

    private fun String.supportsQuickDictionaryPipeline(): Boolean =
        usesQuickDictionaryForTranslation() &&
            this != TranslationConstants.PROVIDER_HAN_VIET

    private fun removeQuickIgnoredTerms(text: String, terms: List<String>): String {
        if (text.isBlank() || terms.isEmpty()) return text
        return terms.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sortedByDescending { it.length }
            .fold(text) { output, term -> output.replace(term, "") }
    }

    private class TranslationLayoutException(message: String) : Exception(message)

    private class TranslationQualityException(message: String) : Exception(message)
}

internal fun shouldIncludeRetranslatePrompt(
    isExplicitRetranslation: Boolean,
    hasRetryReason: Boolean,
): Boolean = isExplicitRetranslation || hasRetryReason

internal fun resolveAiRuntimeMaxInputChars(
    runtimeOptions: AiTaskRuntimeOptions?,
    globalFallback: Int,
): Int = (runtimeOptions?.maxInputChars ?: globalFallback)
    .coerceIn(TranslationConfig.MIN_CHUNK_CHARS, TranslationConfig.MAX_CHUNK_CHARS)

internal fun resolveAiRuntimeConcurrentRequests(
    runtimeOptions: AiTaskRuntimeOptions?,
    globalFallback: Int,
): Int = (runtimeOptions?.concurrentRequests ?: globalFallback).coerceIn(1, 4)

internal fun resolveAiRuntimeRetryCount(
    runtimeOptions: AiTaskRuntimeOptions?,
    globalFallback: Int,
): Int = (runtimeOptions?.retryCount ?: globalFallback).coerceIn(0, 5)

internal fun resolveTranslationChunkConcurrency(
    provider: String,
    hasLocalAiBudget: Boolean,
    storyMemoryEnabled: Boolean,
    aiConcurrentRequests: Int,
    standardConcurrentRequests: Int,
): Int = when {
    hasLocalAiBudget || provider == TranslationConstants.PROVIDER_NMT -> 1
    provider == TranslationConstants.PROVIDER_APP_AI && storyMemoryEnabled -> {
        // Story memory is causal: chunk N+1 must see the accepted delta from chunk N.
        1
    }
    provider == TranslationConstants.PROVIDER_APP_AI -> aiConcurrentRequests.coerceIn(1, 4)
    else -> standardConcurrentRequests.coerceIn(1, 4)
}

internal fun mergeDictionaryTerms(
    primaryTerms: List<DictPair>,
    fallbackTerms: List<DictPair>,
): List<DictPair> {
    val seen = hashSetOf<String>()
    return (primaryTerms.asSequence() + fallbackTerms.asSequence())
        .map(DictPair::normalizedForRuntime)
        .map { term -> term.copy(original = term.original.trim()) }
        .filter { term -> term.original.isNotEmpty() }
        .filter { term -> seen.add(term.original.lowercase()) }
        .toList()
}

private const val AI_TRANSLATION_PIPELINE_REVISION =
    "translator-engine-android-v5-structured-split-fallback"

internal fun aiTranslationFallbackSplitMaxChars(
    contentLength: Int,
    splitDepth: Int,
): Int? {
    if (splitDepth >= AI_TRANSLATION_MAX_SPLIT_DEPTH ||
        contentLength <= AI_TRANSLATION_MIN_SPLIT_CHARS
    ) return null
    return (contentLength / 2)
        .coerceAtLeast(AI_TRANSLATION_MIN_SPLIT_CHARS)
        .coerceAtMost(contentLength - 1)
}

private const val AI_TRANSLATION_MIN_SPLIT_CHARS = 160
private const val AI_TRANSLATION_MAX_SPLIT_DEPTH = 3

internal fun chunkTranslationDependencyHash(
    sourceContent: String,
    provider: String,
    dictionaryTerms: List<DictPair>,
    quickTranslationPackVersion: String,
    providerConfigurationRevision: String = "",
    storyMemoryRevision: String = "",
    computeHash: (String) -> String,
): String {
    val sourceHash = computeHash(sourceContent)
    val configuredSourceHash = if (providerConfigurationRevision.isBlank()) {
        sourceHash
    } else {
        "$sourceHash|provider-config:${computeHash(providerConfigurationRevision)}"
    }
    if (!provider.usesQuickDictionaryForTranslation()) return configuredSourceHash
    val relevantDictionarySignature = dictionaryTerms.asSequence()
        .map(DictPair::normalizedForRuntime)
        .map { term ->
            term.copy(
                original = term.original.trim(),
                translation = term.translation.trim(),
            )
        }
        .filter { term ->
            term.original.isNotEmpty() && sourceContent.contains(term.original, ignoreCase = true)
        }
        .distinctBy { term -> "${term.type}\u0000${term.original.lowercase()}" }
        .sortedWith(compareBy<DictPair> { it.original.lowercase() }.thenBy { it.type.name })
        .joinToString("\u0001") { term ->
            "${term.type}\u0000${term.original}\u0000${term.translation}"
        }
    return buildString {
        append(configuredSourceHash)
        append("|qt-chunk:").append(computeHash(relevantDictionarySignature))
        append(":").append(quickTranslationPackVersion)
        if (provider == TranslationConstants.PROVIDER_APP_AI) {
            append("|ai-pipeline:").append(AI_TRANSLATION_PIPELINE_REVISION)
            if (storyMemoryRevision.isNotBlank()) {
                append("|story-memory:").append(computeHash(storyMemoryRevision))
            }
        }
    }
}

internal fun applyProviderConfigurationRevision(
    contentHash: String,
    providerConfigurationRevision: String,
    computeHash: (String) -> String,
): String = if (providerConfigurationRevision.isBlank()) {
    contentHash
} else {
    "$contentHash|provider-config:${computeHash(providerConfigurationRevision)}"
}

internal fun hasUntranslatedCjkForVietnamese(
    source: String,
    translated: String,
    targetLanguage: String,
): Boolean {
    if (targetLanguage != TranslationConstants.TARGET_VIETNAMESE || source.isBlank()) return false
    val sourceCjk = source.countCjkSourceCodePoints()
    if (sourceCjk <= 0) return false
    return translated.hasCjkSourceCodePoints()
}

internal fun repairResidualCjkForVietnamese(
    text: String,
    targetLanguage: String,
    translateResidual: (String) -> String,
    phoneticResidual: (String) -> String,
): String {
    if (targetLanguage != TranslationConstants.TARGET_VIETNAMESE ||
        !text.hasCjkSourceCodePoints()
    ) {
        return normalizeCjkPunctuation(text)
    }
    val repaired = replaceCjkSourceRuns(text) { run ->
        fun repairCandidate(value: String, depth: Int): String {
            val normalized = value.trim().let(::normalizeCjkPunctuation)
            if (!normalized.hasCjkSourceCodePoints()) return normalized
            if (depth >= 2) {
                return replaceCjkSourceRuns(normalized) { unresolved ->
                    unresolved.value.codePoints()
                        .toArray()
                        .map { codePoint -> String(Character.toChars(codePoint)) }
                        .joinToString(" ") { source ->
                            runCatching { phoneticResidual(source) }
                                .getOrNull()
                                ?.trim()
                                ?.takeIf { it.isNotBlank() && !it.hasCjkSourceCodePoints() }
                                ?: "U+${source.codePointAt(0).toString(16).uppercase()}"
                        }
                }
            }
            return replaceCjkSourceRuns(normalized) { nested ->
                val nestedTranslated = runCatching { translateResidual(nested.value) }
                    .getOrNull()
                    .orEmpty()
                val nestedPhonetic = runCatching { phoneticResidual(nested.value) }
                    .getOrNull()
                    .orEmpty()
                listOf(nestedTranslated, nestedPhonetic)
                    .filter(String::isNotBlank)
                    .map { repairCandidate(it, depth + 1) }
                    .minWithOrNull(
                        compareBy<String> { candidate -> candidate.countCjkSourceCodePoints() }
                            .thenByDescending(String::length)
                    )
                    ?: nested.value
            }
        }

        val translated = runCatching { translateResidual(run.value) }
            .getOrNull()
            .orEmpty()
        val phonetic = runCatching { phoneticResidual(run.value) }
            .getOrNull()
            .orEmpty()
        val replacement = listOf(translated, phonetic)
            .map { repairCandidate(it, 0) }
            .filter(String::isNotBlank)
            .minWithOrNull(
                compareBy<String> { value -> value.countCjkSourceCodePoints() }
                    .thenByDescending(String::length)
            )
            ?: run.value
        replacement.withWordBoundariesFor(text, run.range)
    }
    return normalizeCjkPunctuation(repaired)
}

private fun normalizeCjkPunctuation(text: String): String = buildString(text.length) {
    text.forEach { char ->
        append(
            when (char) {
                '\u3000' -> ' '
                '\u3001', '\uff0c' -> ','
                '\u3002' -> '.'
                '\uff01' -> '!'
                '\uff1f' -> '?'
                '\uff1b' -> ';'
                '\uff1a' -> ':'
                '\u201c', '\u201d', '\u300c', '\u300d', '\u300e', '\u300f' -> '"'
                '\u2018', '\u2019' -> '\''
                '\u300a', '\u300b', '\u3010', '\u3011' -> '"'
                else -> if (char.code in 0xFF01..0xFF5E) {
                    (char.code - 0xFEE0).toChar()
                } else if (
                    (char.code in 0x3000..0x303F || char.code in 0xFF5F..0xFF65) &&
                    !char.code.isCjkSourceCodePoint()
                ) {
                    ' '
                } else {
                    char
                }
            }
        )
    }
}

private fun String.withWordBoundariesFor(source: String, range: IntRange): String {
    if (isEmpty()) return this
    val needsLeadingSpace = range.first > 0 &&
        source[range.first - 1].isLetterOrDigit() &&
        first().isLetterOrDigit()
    val needsTrailingSpace = range.last < source.lastIndex &&
        source[range.last + 1].isLetterOrDigit() &&
        last().isLetterOrDigit()
    return buildString(length + 2) {
        if (needsLeadingSpace) append(' ')
        append(this@withWordBoundariesFor)
        if (needsTrailingSpace) append(' ')
    }
}

internal fun inferMlKitSourceLanguageHint(
    text: String,
    targetLanguage: String,
): String? {
    val inferred = inferCjkScriptLanguage(text) ?: return null
    return inferred.takeUnless { it == targetLanguage }
}

private fun inferCjkScriptLanguage(text: String): String? {
    var hanCount = 0
    var kanaCount = 0
    var hangulCount = 0
    var letterCount = 0
    var offset = 0
    val sample = text.take(4_000)
    while (offset < sample.length) {
        val codePoint = sample.codePointAt(offset)
        if (Character.isLetter(codePoint)) letterCount += 1
        when {
            codePoint.isKanaCodePoint() -> kanaCount += 1
            codePoint.isHangulCodePoint() -> hangulCount += 1
            codePoint.isHanCodePoint() -> hanCount += 1
        }
        offset += Character.charCount(codePoint)
    }
    val letters = letterCount.coerceAtLeast(1)
    return when {
        kanaCount > 0 && (kanaCount + hanCount) * 2 >= letters -> "ja"
        hangulCount > 0 && hangulCount * 2 >= letters -> "ko"
        hanCount > 0 && hanCount * 3 >= letters -> "zh"
        else -> null
    }
}

internal fun finalizeAiTranslationOutput(
    translatedText: String,
    targetLanguage: String,
    encodedParagraphCount: Int?,
): String {
    val normalizedText = extractAiTranslationPayload(translatedText)
    val decodedText = if (encodedParagraphCount != null) {
        AiTranslationLayoutProtocol.decodeCompleteOrPlain(
            normalizedText,
            encodedParagraphCount,
        ) ?: AiTranslationLayoutProtocol.stripMarkers(normalizedText)
    } else {
        normalizedText
    }
    return if (targetLanguage == "zh") {
        filterHighEnglishAiParagraphs(decodedText)
    } else {
        decodedText
    }
}

private fun extractAiTranslationPayload(rawText: String): String {
    var text = rawText.trim()
    repeat(3) {
        val unwrapped = unwrapMarkdownFence(text).trim()
        val jsonPayload = extractJsonTranslationPayload(unwrapped)?.trim()
        val next = when {
            !jsonPayload.isNullOrBlank() -> jsonPayload
            else -> unwrapped
        }
        if (next == text) return@repeat
        text = next
    }
    return text.trim()
}

private fun unwrapMarkdownFence(rawText: String): String {
    val text = rawText.trim()
    val match = MARKDOWN_FENCE_PATTERN.matchEntire(text) ?: return text
    return match.groupValues[1]
}

private fun extractJsonTranslationPayload(text: String): String? {
    if (!(text.startsWith("{") && text.endsWith("}")) &&
        !(text.startsWith("[") && text.endsWith("]"))
    ) {
        return null
    }
    val root = runCatching {
        GSON.fromJson(text, com.google.gson.JsonElement::class.java)
    }.getOrNull() ?: return null
    return translationPayloadFromJsonElement(root)
}

private fun translationPayloadFromJsonElement(
    element: com.google.gson.JsonElement,
    arrayMode: JsonArrayMode = JsonArrayMode.PARAGRAPHS,
): String? {
    if (element.isJsonNull) return null
    if (element.isJsonPrimitive) {
        return element.asJsonPrimitive
            .takeIf { it.isString }
            ?.asString
            ?.takeIf(String::isNotBlank)
    }
    if (element.isJsonArray) {
        val separator = when (arrayMode) {
            JsonArrayMode.PARAGRAPHS -> "\n\n"
            JsonArrayMode.FRAGMENTS -> ""
        }
        return element.asJsonArray
            .mapNotNull { item -> translationPayloadFromJsonElement(item, arrayMode) }
            .filter(String::isNotBlank)
            .joinToString(separator)
            .takeIf(String::isNotBlank)
    }
    if (!element.isJsonObject) return null
    val root = element.asJsonObject
    JSON_TRANSLATION_KEYS.forEach { key ->
        val child = root.get(key)
        child
            ?.let {
                translationPayloadFromJsonElement(
                    element = it,
                    arrayMode = jsonArrayModeForKey(key, it),
                )
            }
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
    }
    JSON_TRANSLATION_CONTAINER_KEYS.forEach { key ->
        val child = root.get(key)
        child
            ?.let {
                translationPayloadFromJsonElement(
                    element = it,
                    arrayMode = jsonArrayModeForKey(key, it),
                )
            }
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
    }
    return null
}

private enum class JsonArrayMode {
    PARAGRAPHS,
    FRAGMENTS,
}

private fun jsonArrayModeForKey(
    key: String,
    element: com.google.gson.JsonElement,
): JsonArrayMode {
    if (!element.isJsonArray) return JsonArrayMode.PARAGRAPHS
    return if (key in JSON_FRAGMENT_ARRAY_KEYS || element.asJsonArray.isTextPartArray()) {
        JsonArrayMode.FRAGMENTS
    } else {
        JsonArrayMode.PARAGRAPHS
    }
}

private fun com.google.gson.JsonArray.isTextPartArray(): Boolean {
    if (size() == 0) return false
    return all { item ->
        item.isJsonObject && item.asJsonObject.run {
            has("text") && (has("type") || has("mimeType"))
        }
    }
}

private fun filterHighEnglishAiParagraphs(text: String): String {
    return text.split("\n")
        .filter { paragraph -> !isMostlyEnglishAiParagraph(paragraph) }
        .joinToString("\n")
        .trim()
}

private fun isMostlyEnglishAiParagraph(text: String): Boolean {
    if (text.isEmpty()) return false
    val englishChars =
        text.count { it in 'A'..'Z' || it in 'a'..'z' || it in ".,!?;:'\"-()[]{}-" }
    return englishChars.toDouble() / text.length > 0.8
}

private fun Int.isHanCodePoint(): Boolean =
    this in 0x3400..0x4DBF ||
        this in 0x4E00..0x9FFF ||
        this in 0xF900..0xFAFF ||
        this in 0x20000..0x2A6DF ||
        this in 0x2A700..0x2B73F ||
        this in 0x2B740..0x2B81F ||
        this in 0x2B820..0x2CEAF ||
        this in 0x2CEB0..0x2EBEF ||
        this in 0x2EBF0..0x2EE5F ||
        this in 0x2F800..0x2FA1F ||
        this in 0x30000..0x3134F ||
        this in 0x31350..0x323AF

private fun Int.isKanaCodePoint(): Boolean =
    this in 0x3040..0x30FF ||
        this in 0x31F0..0x31FF ||
        this in 0xFF66..0xFF9F ||
        this in 0x1AFF0..0x1AFFF ||
        this in 0x1B000..0x1B16F

private fun Int.isHangulCodePoint(): Boolean =
    this in 0x1100..0x11FF ||
        this in 0x3130..0x318F ||
        this in 0xA960..0xA97F ||
        this in 0xAC00..0xD7AF ||
        this in 0xD7B0..0xD7FF

private fun Int.isCjkSourceCodePoint(): Boolean =
    isHanCodePoint() ||
        isKanaCodePoint() ||
        isHangulCodePoint() ||
        this in 0x2E80..0x2EFF ||
        this in 0x2F00..0x2FDF ||
        this in 0x3005..0x3007 ||
        this in 0x3031..0x3035 ||
        this == 0x303B ||
        this in 0x3100..0x312F ||
        this in 0x31A0..0x31BF ||
        this in 0x31C0..0x31EF

private fun String.hasCjkSourceCodePoints(): Boolean {
    var offset = 0
    while (offset < length) {
        val codePoint = codePointAt(offset)
        if (codePoint.isCjkSourceCodePoint()) return true
        offset += Character.charCount(codePoint)
    }
    return false
}

private fun String.countCjkSourceCodePoints(): Int {
    var count = 0
    var offset = 0
    while (offset < length) {
        val codePoint = codePointAt(offset)
        if (codePoint.isCjkSourceCodePoint()) count += 1
        offset += Character.charCount(codePoint)
    }
    return count
}

private data class CjkSourceRun(
    val value: String,
    val start: Int,
    val endExclusive: Int,
) {
    val range: IntRange
        get() = start until endExclusive
}

private fun findCjkSourceRuns(text: String): List<CjkSourceRun> {
    val runs = mutableListOf<CjkSourceRun>()
    var offset = 0
    var runStart = -1
    while (offset < text.length) {
        val codePoint = text.codePointAt(offset)
        val nextOffset = offset + Character.charCount(codePoint)
        if (codePoint.isCjkSourceCodePoint()) {
            if (runStart < 0) runStart = offset
        } else if (runStart >= 0) {
            runs += CjkSourceRun(text.substring(runStart, offset), runStart, offset)
            runStart = -1
        }
        offset = nextOffset
    }
    if (runStart >= 0) {
        runs += CjkSourceRun(text.substring(runStart), runStart, text.length)
    }
    return runs
}

private inline fun replaceCjkSourceRuns(
    text: String,
    transform: (CjkSourceRun) -> String,
): String {
    val runs = findCjkSourceRuns(text)
    if (runs.isEmpty()) return text
    return buildString(text.length) {
        var cursor = 0
        runs.forEach { run ->
            append(text, cursor, run.start)
            append(transform(run))
            cursor = run.endExclusive
        }
        append(text, cursor, text.length)
    }
}

private val MARKDOWN_FENCE_PATTERN = Regex(
    pattern = "^```[A-Za-z0-9_-]*\\s*\\n?([\\s\\S]*?)\\n?```$",
)
private val LEGACY_AI_TRANSLATION_SECTION_PATTERN = Regex(
    pattern = """(?im)(?:^|\R)\s*\[(?:/?result|dictionary)](?:\s|$)""",
)
private val JSON_TRANSLATION_KEYS = listOf(
    "result",
    "translation",
    "translatedText",
    "translated_text",
    "output",
    "text",
    "content",
)
private val JSON_TRANSLATION_CONTAINER_KEYS = listOf(
    "data",
    "payload",
    "message",
    "response",
    "choices",
    "choice",
    "candidates",
    "candidate",
    "parts",
)
private val JSON_FRAGMENT_ARRAY_KEYS = setOf(
    "content",
    "parts",
)

@Keep
private data class GoogleTranslateResponse(
    val sentences: List<GoogleSentence>?
)

@Keep
private data class GoogleSentence(
    val trans: String?
)
