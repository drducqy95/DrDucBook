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
import io.legado.app.model.translation.HachimiOnnxModelRegistry
import io.legado.app.domain.gateway.AiPromptPresetGateway
import io.legado.app.domain.gateway.LocalAiTranslationGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiOutputContract
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
import io.legado.app.domain.model.AiTranslationStoryMemoryPipeline
import io.legado.app.domain.model.AiTranslationProtectionProtocol
import io.legado.app.domain.model.AiTranslationRefinePipeline
import io.legado.app.domain.model.AiTranslationRefinerResult
import io.legado.app.domain.model.AiTranslationTokenBudget
import io.legado.app.domain.model.AiTranslationLayoutProtocol
import io.legado.app.domain.model.AiTranslationStreamAccumulator
import io.legado.app.domain.model.AiPromptCatalog
import io.legado.app.domain.model.AiPromptTemplate
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
import io.legado.app.domain.model.TranslationContentSanitizer
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap
import java.util.LinkedHashMap

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
    private val localAiTranslationGateway: LocalAiTranslationGateway? = null,
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
    private val nmtProjectionCache = object : LinkedHashMap<String, List<DictPair>>(128, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<DictPair>>?,
        ): Boolean = size > 128
    }
    private val nmtDictionaryIndexCache = IdentityHashMap<List<DictPair>, Map<Char, List<DictPair>>>()

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

    suspend fun executeDynamicChapterTitle(
        scopeKey: String,
        originalText: String,
        book: Book? = null,
        contextText: String = originalText,
        forceRetranslate: Boolean = false,
    ): Result<String> = translateDynamicUiTextUseCase.executeChapterTitle(
        scopeKey = scopeKey,
        originalText = originalText,
        book = book,
        contextText = contextText,
        forceRetranslate = forceRetranslate,
    )

    suspend fun executeDynamicBookName(
        scopeKey: String,
        originalText: String,
        book: Book? = null,
        contextText: String = originalText,
        forceRetranslate: Boolean = false,
    ): Result<String> = translateDynamicUiTextUseCase.executeBookName(
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
            val memoryTerms = book?.let { currentBook ->
                runCatching {
                    val snapshot = translationStoryMemoryUseCase?.loadSnapshot(currentBook.bookUrl)
                    snapshot?.let {
                        AiTranslationStoryMemoryPipeline.selectContext(it, Int.MAX_VALUE, text).memoryDictionary
                    }.orEmpty()
                }.getOrDefault(emptyList())
            }.orEmpty()
            val quickPronounMode = book?.getQuickTranslationPronounModeOverride()
            val dictionaries = mergeDictionaryTerms(
                primaryTerms = memoryTerms + bookTerms,
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
                TranslationConstants.PROVIDER_NMT -> {
                    nmtTranslationGateway.translate(
                        text = source,
                        dictionary = dictionaries,
                        config = currentNmtDecodeConfig(),
                    ).text
                }
                TranslationConstants.PROVIDER_GOOGLE -> translateWithGoogle(source, targetLanguage)
                    .getOrThrow()
                TranslationConstants.PROVIDER_ML_KIT -> mlKitTranslationGateway.translate(
                    text = source,
                    targetLanguage = targetLanguage,
                )
                TranslationConstants.PROVIDER_LOCAL_AI -> {
                    val gateway = localAiTranslationGateway
                        ?: error("Local AI translation gateway is not available")
                    val rawText = gateway.translate(
                        text = source,
                        targetLanguage = targetLanguage,
                        context = AiTranslationChunkContext(
                            previous = previousContext.takeLast(160),
                            next = nextContext.take(160),
                        ),
                        dictionary = selectRelevantDictionaries(dictionaries, source, maxPairs = 20),
                        configuredPrompt = TranslationConfig.localAiPrompt,
                    ).text
                    if (targetLanguage == TranslationConstants.TARGET_VIETNAMESE && rawText.hasCjkSourceCodePoints()) {
                        val quickPhonetics = quickEntries.mapNotNull { it.toQuickPhoneticPair() }
                        repairResidualCjkForVietnamese(
                            text = rawText,
                            targetLanguage = targetLanguage,
                            translateResidual = { residual ->
                                quickTranslationGateway.translate(
                                    text = residual,
                                    projectTerms = dictionaries,
                                    customPhonetics = quickPhonetics,
                                    pronounMode = quickPronounMode,
                                )
                            },
                            phoneticResidual = { residual ->
                                quickTranslationGateway.hanViet(residual, quickPhonetics)
                            },
                        )
                    } else {
                        rawText
                    }
                }
                TranslationConstants.PROVIDER_APP_AI,
                TranslationConstants.PROVIDER_REWRITE -> {
                    val preset = if (provider == TranslationConstants.PROVIDER_REWRITE) {
                        resolveRewritePreset(book)
                            ?: error("Chưa cấu hình preset hoặc model cho AI viết lại. Vui lòng vào Cài đặt AI để chọn model.")
                    } else {
                        resolveTranslationPreset(book)
                            ?: error("Chưa cấu hình preset hoặc model cho AI dịch. Vui lòng vào Cài đặt AI để chọn model.")
                    }
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
            postProcessTranslation(
                repairedTranslation,
                targetLanguage,
                isRewrite = provider == TranslationConstants.PROVIDER_REWRITE,
            )
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
        retrofitWithExistingDraft: Boolean = false,
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
            val isRewrite = provider == TranslationConstants.PROVIDER_REWRITE
            val preset = if (provider == TranslationConstants.PROVIDER_APP_AI || provider == TranslationConstants.PROVIDER_REWRITE) {
                if (provider == TranslationConstants.PROVIDER_REWRITE) {
                    resolveRewritePreset(book)
                        ?: return@withContext Result.failure(Exception("No AI rewrite preset configured"))
                } else {
                    resolveTranslationPreset(book)
                        ?: return@withContext Result.failure(Exception("No AI translation preset configured"))
                }
            } else {
                null
            }
            val promptStages = if (provider == TranslationConstants.PROVIDER_APP_AI || provider == TranslationConstants.PROVIDER_REWRITE) {
                TranslationPromptStage.entries.associateWith { stage ->
                    aiPromptPresetGateway.getEnabledByTaskType(stage.taskType)
                        .map { it.instruction }
                        .filter(String::isNotBlank)
                }
            } else {
                emptyMap()
            }
            val providerConfigRevision = providerConfigurationRevision(provider, book)

            val originalContent = BookHelp.getContent(book, bookChapter)
                ?.let(TranslationContentSanitizer::sanitize)
                ?.takeIf(String::isNotBlank)
                ?: return@withContext Result.failure(Exception("Failed to read original content"))
            onProgress(TranslationProgress(0, 1, stage = "SOURCE_READ chars=${originalContent.length}"))
            val quickPronounMode = book.getQuickTranslationPronounModeOverride()
            val quickTranslationPackVersion = quickTranslationGateway.packVersionFor(quickPronounMode)
            val dictionaryRevision = quickDictionaryGateway.getEffectiveRevision(
                book = book,
                context = originalContent,
            )

            // Story memory is a cache dependency, not merely optional prompt context. Load it
            // before any cache lookup so a user-edited target immediately invalidates related
            // chapter/chunk translations.
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
                AiTranslationStoryContext()
            }
            var storyMemoryRevision = GSON.toJson(storyContext)
            onProgress(
                TranslationProgress(
                    0,
                    1,
                    stage = "STORY_CONTEXT_LOADED entities=${storyContext.currentEntities.size} " +
                        "relationships=${storyContext.currentRelationships.size} " +
                        "world=${storyContext.currentWorldBuilding.size} " +
                        "timelines=${storyContext.recentTimelines.size}",
                )
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
            ).let { baseHash ->
                if (storyMemoryRevision.isBlank()) baseHash
                else "$baseHash|story-memory:${translationCacheGateway.computeContentHash(storyMemoryRevision)}"
            }

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
                    isRewrite = isRewrite,
                )
                onProgress(TranslationProgress(1, 1, displayTranslation, setOf(0)))
                return@withContext Result.success(displayTranslation)
            }

            val isAiTranslation = provider == TranslationConstants.PROVIDER_APP_AI ||
                provider == TranslationConstants.PROVIDER_REWRITE
            if (retrofitWithExistingDraft && isAiTranslation) {
                val existingRevision = translationCacheGateway.getCurrentRevision(
                    book = book,
                    bookChapter = bookChapter,
                    targetLanguage = targetLanguage,
                    provider = provider,
                    currentRawContentHash = rawContentHash,
                )
                val existingAiTranslation = existingRevision?.takeIf { it.content.isNotBlank() }?.content
                    ?: translationCacheGateway.readCurrentTranslation(
                        book = book,
                        bookChapter = bookChapter,
                        targetLanguage = targetLanguage,
                        originalContentHash = rawContentHash,
                        provider = provider,
                    )
                if (existingAiTranslation != null) {
                    var updatedText: String = existingAiTranslation
                    for (mem in storyContext.canonicalMemory) {
                        if (mem.target.isNotBlank()) {
                            for (alias in mem.aliases) {
                                if (alias.isNotBlank() && alias != mem.target && updatedText.contains(alias)) {
                                    updatedText = updatedText.replace(alias, mem.target)
                                }
                            }
                        }
                    }
                    val displayTranslation = postProcessTranslation(
                        text = updatedText,
                        targetLanguage = targetLanguage,
                        isRewrite = isRewrite,
                    )
                    translationCacheGateway.writeTranslation(
                        book = book,
                        bookChapter = bookChapter,
                        targetLanguage = targetLanguage,
                        content = displayTranslation,
                        originalContentHash = contentHash,
                        provider = provider,
                        rawContentHash = rawContentHash,
                        dictionaryRevision = dictionaryRevision.cacheToken,
                    )
                    onProgress(
                        TranslationProgress(
                            currentChunk = 1,
                            totalChunks = 1,
                            mixedContent = displayTranslation,
                            translatedChunkIndices = emptySet(),
                            stage = "AI_CACHE_PRESERVED provider=$provider",
                        )
                    )
                    return@withContext Result.success(displayTranslation)
                }
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
                        isRewrite = isRewrite,
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
                    isRewrite = isRewrite,
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

            var activeStoryContext = storyContext
            val storyContextLock = Any()
            var plannedChunkCount = 1
            var completedChunkCount = 0
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
                primaryTerms = storyContext.memoryDictionary + bookDictionary.pairs,
                fallbackTerms = scopedQuickTerms,
            )
                .toMutableList()

            // A successful chunk commits its staged dictionary/story-memory deltas before the
            // next chunk starts. Failed attempts never mutate persistent state.
            val commitStoryMemoryUpdate: suspend (AiTranslationRefinerResult, String) -> Unit = { result, source ->
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
                                storyMemoryRevision = GSON.toJson(refreshed)
                            }
                        }
                        "MEMORY_COMMITTED records=$count"
                    },
                    onFailure = { error ->
                        "MEMORY_PENDING warning=${error.message ?: error::class.java.simpleName}"
                    },
                ) ?: "MEMORY_DISABLED"
                result.story_memory?.timeline?.chapterTitle?.takeIf { it.isNotBlank() && !it.containsCjk() }?.let { aiTitle ->
                    translateDynamicUiTextUseCase.saveAiChapterTitle(
                        scopeKey = "chapter-title:${book.bookUrl}:${bookChapter.index}",
                        originalText = bookChapter.title,
                        aiTitle = aiTitle,
                    )
                }
                onProgress(
                    TranslationProgress(
                        currentChunk = completedChunkCount,
                        totalChunks = plannedChunkCount,
                        stage = stage,
                    )
                )
            }

            if (provider == TranslationConstants.PROVIDER_NMT && targetLanguage != "vi") {
                return@withContext Result.failure(
                    IllegalArgumentException("Offline NMT currently supports Vietnamese output only")
                )
            }

            val maxCharsPerChunk = when (provider) {
                TranslationConstants.PROVIDER_LOCAL_AI -> {
                    val localContextWindow = localAiTranslationGateway?.contextWindow() ?: 4_096
                    val budget = LocalAiTranslationBudgetPlanner.plan(
                        contextWindow = localContextWindow,
                        providerMaxOutputTokens = 4_096,
                        configuredMaxOutputTokens = null,
                        configuredMaxSourceChars = TranslationConfig.localAiMaxCharsPerChunk,
                        preferredChunkChars = TranslationConfig.localAiMaxCharsPerChunk,
                        adjacentContextChars = LOCAL_AI_ADJACENT_CONTEXT_CHARS,
                        fixedPromptChars = 400,
                    )
                    budget.maxSourceChars
                }
                TranslationConstants.PROVIDER_NMT ->
                    TranslationConfig.nmtMaxCharsPerChunk
                TranslationConstants.PROVIDER_QUICK_TRANSLATOR ->
                    QUICK_TRANSLATOR_CHUNK_CHARS
                TranslationConstants.PROVIDER_ML_KIT -> ML_KIT_CHUNK_CHARS
                TranslationConstants.PROVIDER_APP_AI -> preset
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
            plannedChunkCount = chunks.size
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
                    // An explicit retranslation may display the previous chunk as a
                    // temporary fallback while the replacement is running. It is never
                    // selected as the new result because allowCachedChunk remains false.
                    expectedContentHash = if (forceRetranslate) null else chunkContentHashes.getValue(chunk.index),
                )
                val cachedContent = cached?.translatedChunkContent
                val hasUsableCachedContent = cached != null &&
                    cachedContent != null &&
                    cached.isReadable &&
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
                    isRewrite = isRewrite,
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
                    isRewrite = isRewrite,
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
                            val stagedDictionary = mutableListOf<DictPair>()
                            val stagedStoryMemory = mutableListOf<Pair<AiTranslationRefinerResult, String>>()
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
                                    onDictionaryUpdate = { pairs -> stagedDictionary += pairs },
                                    promptStages = promptStages,
                                    allowCachedChunk = !forceRetranslate,
                                    quickPhonetics = scopedQuickPhonetics,
                                    quickTranslatorTerms = scopedQuickTranslatorTerms,
                                    quickIgnoredTerms = scopedQuickIgnoredTerms,
                                    quickPronounMode = quickPronounMode,
                                    isExplicitRetranslation = forceRetranslate,
                                    aiContext = when (provider) {
                                        TranslationConstants.PROVIDER_APP_AI -> {
                                            AiTranslationChunkPlanner.contextFor(
                                                chunks = chunks,
                                                chunkIndex = chunk.index,
                                                maxCharsPerChunk = maxCharsPerChunk,
                                                maxContextChars = DEFAULT_AI_ADJACENT_CONTEXT_CHARS,
                                            )
                                        }
                                        TranslationConstants.PROVIDER_LOCAL_AI -> {
                                            AiTranslationChunkPlanner.contextFor(
                                                chunks = chunks,
                                                chunkIndex = chunk.index,
                                                maxCharsPerChunk = maxCharsPerChunk,
                                                maxContextChars = LOCAL_AI_ADJACENT_CONTEXT_CHARS,
                                            )
                                        }
                                        else -> AiTranslationChunkContext()
                                    },
                                    storyContextProvider = storyContextProvider,
                                    onStoryMemoryUpdate = { memory, source ->
                                        stagedStoryMemory += memory to source
                                    },
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
                                    onNmtQuality = { report ->
                                        if (provider == TranslationConstants.PROVIDER_NMT &&
                                            report.status == io.legado.app.domain.gateway.NmtQualityStatus.DEGRADED
                                        ) {
                                            onProgress(
                                                TranslationProgress(
                                                    currentChunk = translatedChunks.size,
                                                    totalChunks = chunks.size,
                                                    mixedContent = stableDisplayChunks().let { displayChunks ->
                                                        PartialTranslationAssembler.assemble(chunks, displayChunks)
                                                    },
                                                    translatedChunkIndices = stableDisplayChunks().keys.toSet(),
                                                    stage = "NMT_QUALITY_DEGRADED missingTerms=${report.missingRequiredTerms.size}",
                                                )
                                            )
                                        }
                                    },
                                )
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                            if (result.isSuccess) {
                                if (stagedDictionary.isNotEmpty()) {
                                    val shouldPersist = synchronized(dictionaryLock) {
                                        mergeDictionaryPairs(dictionaries, stagedDictionary)
                                    }
                                    if (shouldPersist) {
                                        synchronized(nmtProjectionCache) {
                                            nmtProjectionCache.clear()
                                            nmtDictionaryIndexCache.clear()
                                        }
                                    }
                                    if (shouldPersist && provider == TranslationConstants.PROVIDER_APP_AI) {
                                        dictionaryGateway.updateBookDic(
                                            book,
                                            synchronized(dictionaryLock) { dictionaries.toList() },
                                        )
                                    }
                                }
                                stagedStoryMemory.forEach { (memory, source) ->
                                    commitStoryMemoryUpdate(memory, source)
                                }
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
                                    isRewrite = isRewrite,
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
                                    completedChunkCount = translatedChunks.size
                                    val displayChunks = stableDisplayChunks()
                                    val mixedContent = postProcessTranslation(
                                        PartialTranslationAssembler.assemble(
                                            chunks,
                                            displayChunks,
                                        ),
                                        targetLanguage,
                                        isRewrite = isRewrite,
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
                                        isRewrite = isRewrite,
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
                isRewrite = isRewrite,
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
     * - Keep every persisted term; NMT applies its limit only to the per-chunk projection
     * @return true if any changes were made
     */
    private fun mergeDictionaryPairs(existing: MutableList<DictPair>, newPairs: List<DictPair>): Boolean {
        var changed = false
        val positions = existing.withIndex().associate { indexed ->
            dictionaryPairKey(indexed.value) to indexed.index
        }.toMutableMap()
        for (newPair in newPairs) {
            val key = dictionaryPairKey(newPair)
            val existingIndex = positions[key]
            if (existingIndex != null) {
                if (existing[existingIndex].translation != newPair.translation) {
                    existing[existingIndex] = newPair
                    changed = true
                }
            } else {
                existing.add(newPair)
                positions[key] = existing.lastIndex
                changed = true
            }
        }

        return changed
    }

    private fun dictionaryPairKey(pair: DictPair): String =
        "${pair.type.name}\u0000${pair.original.trim().lowercase()}"

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
        onNmtQuality: (io.legado.app.domain.gateway.NmtQualityReport) -> Unit = {},
    ): Result<String> {
        val sourceContent = removeQuickIgnoredTerms(chunk.content, quickIgnoredTerms)
        val existingCache =
            translationCacheGateway.getCachedChunk(
                book,
                bookChapter,
                targetLanguage,
                chunk.index,
                provider,
                // Keep a readable previous chunk available to the explicit-retranslation
                // fallback path. The cache is still not selected because allowCachedChunk is
                // false; a successful replacement is saved with the new content hash.
                expectedContentHash = if (isExplicitRetranslation) null else contentHash,
            )
        if (allowCachedChunk && existingCache?.isReadable == true &&
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

        var nmtQuality = io.legado.app.domain.gateway.NmtQualityReport()
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
            onNmtQuality = { report ->
                nmtQuality = report
                onNmtQuality(report)
            },
        )
        if (result.isSuccess) {
            translationCacheGateway.saveChunk(
                book, bookChapter, targetLanguage,
                chunk.index, chunk.content, cacheContentHash(),
                provider,
                if (nmtQuality.status == io.legado.app.domain.gateway.NmtQualityStatus.DEGRADED) {
                    TranslationCache.STATUS_DEGRADED
                } else {
                    TranslationCache.STATUS_SUCCESS
                },
                result.getOrThrow(),
                nmtQuality.missingRequiredTerms.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "NMT_QUALITY_DEGRADED missingTerms=", separator = ", "),
            )
        } else if (existingCache?.isReadable != true) {
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
        onNmtQuality: (io.legado.app.domain.gateway.NmtQualityReport) -> Unit = {},
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
            val dictSnapshot = synchronized(dictionaryLock) {
                mergeDictionaryTerms(
                    primaryTerms = storyContextProvider().memoryDictionary,
                    fallbackTerms = dictionaries.toList(),
                )
            }
        for (pipelineAttempt in 1..pipelineAttempts) {
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
                    val selectedDictionary = selectNmtDictionaries(dictSnapshot, sourceContent)
                    val dictionaryFingerprint = nmtDictionaryFingerprint(
                        dictionary = selectedDictionary,
                        memoryRevision = storyContextProvider().memoryRevision,
                    )
                    val nmtResult = nmtTranslationGateway.translate(
                        text = sourceContent,
                        dictionary = selectedDictionary,
                        config = currentNmtDecodeConfig().copy(
                            dictionaryRevision = dictionaryFingerprint,
                            constraintFingerprint = dictionaryFingerprint,
                        ),
                    )
                    if (nmtResult.qualityReport.status == io.legado.app.domain.gateway.NmtQualityStatus.DEGRADED) {
                        onNmtQuality(nmtResult.qualityReport)
                        onStage(
                            "NMT_QUALITY_DEGRADED missingTerms=" +
                                nmtResult.qualityReport.missingRequiredTerms.size,
                        )
                    }
                    nmtResult.text
                }
                TranslationConstants.PROVIDER_LOCAL_AI -> runCatching {
                    val gateway = localAiTranslationGateway
                        ?: error("Local AI translation gateway is not available")
                    val rawText = gateway.translate(
                        text = sourceContent,
                        targetLanguage = targetLanguage,
                        context = aiContext,
                        dictionary = selectRelevantDictionaries(dictSnapshot, sourceContent, maxPairs = 20),
                        configuredPrompt = TranslationConfig.localAiPrompt,
                        retryInstruction = if (lastRetryReason == RetryReason.PARSE_ERROR) {
                            "Ensure the output matches the paragraph structure of the source text exactly."
                        } else "",
                        onToken = onPartialTranslation,
                    ).text
                    if (targetLanguage == TranslationConstants.TARGET_VIETNAMESE && rawText.hasCjkSourceCodePoints()) {
                        repairResidualCjkForVietnamese(
                            text = rawText,
                            targetLanguage = targetLanguage,
                            translateResidual = { residual ->
                                quickTranslationGateway.translate(
                                    text = residual,
                                    projectTerms = mergeDictionaryTerms(
                                        primaryTerms = dictSnapshot,
                                        fallbackTerms = quickTranslatorTerms,
                                    ),
                                    customPhonetics = quickPhonetics,
                                    pronounMode = quickPronounMode,
                                )
                            },
                            phoneticResidual = { residual ->
                                quickTranslationGateway.hanViet(residual, quickPhonetics)
                            },
                        )
                    } else {
                        rawText
                    }
                }
                TranslationConstants.PROVIDER_APP_AI,
                TranslationConstants.PROVIDER_REWRITE -> preset?.let { configuredPreset ->
                    translateWithAiGateway(
                        text = sourceContent,
                        targetLanguage = targetLanguage,
                        preset = configuredPreset,
                        dictionaries = dictSnapshot,
                        onUpdate = onDictionaryUpdate,
                        retryReason = lastRetryReason,
                        lastErrorMessage = lastError?.message,
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
                } ?: Result.failure(Exception("No AI translation/rewrite preset configured"))
                else -> Result.failure(IllegalArgumentException("Unknown translation provider: $provider"))
            }
            if (result.isSuccess) {
                val translated = result.getOrThrow()
                if (provider == TranslationConstants.PROVIDER_NMT) {
                    nmtOutputQualityError(sourceContent, translated)?.let { qualityError ->
                        lastError = qualityError
                        lastRetryReason = RetryReason.PARSE_ERROR
                        continue
                    }
                }
                val qualityError = translationQualityError(
                    source = sourceContent,
                    translated = translated,
                    targetLanguage = targetLanguage,
                )
                if (qualityError != null) {
                    val failure = if (provider == TranslationConstants.PROVIDER_APP_AI ||
                        provider == TranslationConstants.PROVIDER_REWRITE
                    ) {
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
                    ?: if (provider == TranslationConstants.PROVIDER_LOCAL_AI ||
                        provider == TranslationConstants.PROVIDER_NMT ||
                        provider == TranslationConstants.PROVIDER_QUICK_TRANSLATOR ||
                        provider == TranslationConstants.PROVIDER_REWRITE
                    ) {
                        ContentChunker.restoreLayoutRelaxed(chunk, translated)
                    } else {
                        null
                    }
                if (restored != null) {
                    return Result.success(restored)
                }
                val layoutError = TranslationLayoutException(
                    "Translation parse error: changed paragraph count for chunk ${chunk.index}"
                )
                if (provider == TranslationConstants.PROVIDER_APP_AI ||
                    provider == TranslationConstants.PROVIDER_REWRITE
                ) {
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
            if (provider == TranslationConstants.PROVIDER_APP_AI ||
                provider == TranslationConstants.PROVIDER_REWRITE
            ) {
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
            (provider == TranslationConstants.PROVIDER_APP_AI || provider == TranslationConstants.PROVIDER_REWRITE) &&
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
                        onNmtQuality = onNmtQuality,
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

    private fun resolveEffectiveTranslationPrompt(book: Book?, basePromptTemplate: String): String {
        book?.getCustomTranslationPrompt()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        return basePromptTemplate
    }

    private fun resolveEffectiveRewritePrompt(book: Book?, basePromptTemplate: String): String {
        book?.getCustomRewritePrompt()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        val globalCustom = TranslationConfig.rewriteCustomPrompt.trim()
        if (globalCustom.isNotBlank()) return globalCustom

        AiPromptCatalog.findById(TranslationConfig.rewritePresetId)?.prompt
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        return basePromptTemplate.ifBlank { AiPromptTemplate.DEFAULT_REWRITE }
    }

    private suspend fun resolveTranslationPreset(book: Book? = null): AiTaskPresetConfig? {
        val base = aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)?.copy(
                taskType = AiTaskType.TRANSLATE_CHAPTER,
                name = "Translation fallback",
                promptTemplate = TranslationConstants.DEFAULT_PROMPT,
            )
            ?: run {
                val model = aiProfileGateway.observeModels().firstOrNull()?.firstOrNull { it.enabled }
                if (model != null) {
                    aiProfileGateway.getModelConfig(model.id)?.let { modelConfig ->
                        AiTaskPresetConfig(
                            id = "default_translation",
                            name = "Default Translation",
                            taskType = AiTaskType.TRANSLATE_CHAPTER,
                            promptTemplate = TranslationConstants.DEFAULT_PROMPT,
                            model = modelConfig,
                        )
                    }
                } else null
            }
        if (base == null) return null
        val effectivePrompt = resolveEffectiveTranslationPrompt(book, base.promptTemplate)
        return base.copy(promptTemplate = effectivePrompt)
    }

    private suspend fun resolveRewritePreset(book: Book? = null): AiTaskPresetConfig? {
        val basePreset = aiProfileGateway.getTaskPreset(AiTaskType.REWRITE_TEXT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)?.copy(
                taskType = AiTaskType.REWRITE_TEXT,
                name = "Rewrite fallback",
                promptTemplate = AiPromptTemplate.DEFAULT_REWRITE,
            )
            ?: run {
                val model = aiProfileGateway.observeModels().firstOrNull()?.firstOrNull { it.enabled }
                if (model != null) {
                    aiProfileGateway.getModelConfig(model.id)?.let { modelConfig ->
                        AiTaskPresetConfig(
                            id = "default_rewrite",
                            name = "Default Rewrite",
                            taskType = AiTaskType.REWRITE_TEXT,
                            promptTemplate = AiPromptTemplate.DEFAULT_REWRITE,
                            model = modelConfig,
                        )
                    }
                } else null
            }

        if (basePreset == null) return null

        val effectivePrompt = resolveEffectiveRewritePrompt(book, basePreset.promptTemplate)
        return basePreset.copy(promptTemplate = effectivePrompt)
    }

    suspend fun currentProviderConfigurationRevision(provider: String, book: Book? = null): String {
        return providerConfigurationRevision(provider, book)
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
            val display = postProcessTranslation(
                content,
                targetLanguage,
                isRewrite = identity.provider == TranslationConstants.PROVIDER_REWRITE,
            )
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

    private fun currentNmtDecodeConfig(): NmtDecodeConfig {
        val activeModel = TranslationConfig.nmtActiveModelId
        return NmtDecodeConfig(
            maxSourceTokens = TranslationConfig.nmtSourceTokenBudget,
            maxSourceChars = TranslationConfig.nmtMaxCharsPerChunk,
            sourcePrompt = TranslationConfig.nmtSourcePrompt,
            maxNewTokens = TranslationConfig.nmtMaxNewTokens,
            repetitionPenalty = TranslationConfig.nmtRepetitionPenalty,
            noRepeatNgramSize = if (activeModel == HachimiOnnxModelRegistry.HACHIMI_QT_MODEL_ID) {
                0
            } else if (TranslationConfig.nmtNoRepeatBigram) {
                2
            } else {
                0
            },
            retryMissingRequiredTerms = TranslationConfig.nmtRetryMissingTerms,
            modelId = activeModel,
        )
    }

    private suspend fun providerConfigurationRevision(provider: String, book: Book? = null): String = when (provider) {
        TranslationConstants.PROVIDER_NMT -> currentNmtDecodeConfig().toString()
        TranslationConstants.PROVIDER_LOCAL_AI -> {
            GSON.toJson(
                linkedMapOf(
                    "model" to TranslationConfig.localAiModelPath,
                    "prompt" to TranslationConfig.localAiPrompt,
                    "temperature" to TranslationConfig.localAiTemperature,
                    "topP" to TranslationConfig.localAiTopP,
                    "topK" to TranslationConfig.localAiTopK,
                    "repetitionPenalty" to TranslationConfig.localAiRepetitionPenalty,
                )
            )
        }
        TranslationConstants.PROVIDER_APP_AI -> {
            val preset = resolveTranslationPreset(book)
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
        TranslationConstants.PROVIDER_REWRITE -> {
            val preset = resolveRewritePreset(book)
            val promptStages = TranslationPromptStage.entries.associate { stage ->
                stage.storageKey to aiPromptPresetGateway.getEnabledByTaskType(stage.taskType)
                    .map { it.instruction }
                    .filter(String::isNotBlank)
            }
            GSON.toJson(
                linkedMapOf(
                    "pipeline" to AI_REWRITE_PIPELINE_REVISION,
                    "contract" to AiOutputContract.REWRITE_TEXT,
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
                quickPhonetics = emptyList(),
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

    private suspend fun rewriteWithAiGateway(
        text: String,
        targetLanguage: String,
        preset: AiTaskPresetConfig,
        context: AiTranslationChunkContext,
        retryReason: RetryReason?,
        layoutChunk: TextChunk?,
        routeSessionKey: String?,
        routeRetryOffset: Int,
        onStage: (String) -> Unit,
        onPartial: (String) -> Unit,
    ): Result<String> {
        val protectedText = AiTranslationProtectionProtocol.protect(text)
        val languageName = getLanguageDisplayName(targetLanguage)
        val retryInstruction = buildRetryInstruction(
            retryReason = retryReason,
            lastErrorMessage = null,
            targetLanguageName = languageName,
        )
        val systemPrompt = buildString {
            append(preset.promptTemplate.ifBlank { AiPromptTemplate.DEFAULT_REWRITE })
            append("\nTarget language: ").append(languageName).append('.')
            append("\nPreserve the exact paragraph count and protected tokens.")
            append(buildProtectedTokenInstruction(protectedText))
            if (retryInstruction.isNotBlank()) append("\n").append(retryInstruction)
        }
        val userPrompt = buildString {
            if (context.previous.isNotBlank()) {
                append("Previous context (reference only):\n")
                    .append(context.previous.takeLast(400))
                    .append("\n\n")
            }
            append("Rewrite this text in ").append(languageName).append(". Return plain text only:\n")
            append(protectedText.value)
            if (context.next.isNotBlank()) {
                append("\n\nFollowing context (reference only):\n")
                    .append(context.next.take(400))
            }
        }
        val reasoningModel = AiCapability.REASONING in preset.model.capabilities ||
            AiCapability.REASONING in AiModelRegistry.inferCapabilities(preset.model.modelId)
        val params = preset.params.copy(
            temperature = preset.params.temperature
                ?: preset.model.defaultParams.temperature
                ?: TranslationConstants.DEFAULT_TEMPERATURE,
            maxOutputTokens = AiTranslationTokenBudget.forSourceChars(
                sourceChars = text.length,
                configuredLimit = preset.params.maxOutputTokens,
                providerLimit = preset.model.maxOutputTokens,
                reasoningModel = reasoningModel,
                structuredJson = false,
            ),
        )
        val request = AiGenerateRequest(
            model = preset.model,
            messages = listOf(
                AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                AiMessage(AiMessageRole.USER, userPrompt),
            ),
            params = params,
            taskType = AiTaskType.REWRITE_TEXT,
            outputContract = AiOutputContract.REWRITE_TEXT,
            routeProfileId = preset.runtimeOptions.routeProfileId,
            routeSessionKey = routeSessionKey,
            routeRetryOffset = routeRetryOffset,
            routeSemanticFailureKind = if (retryReason == RetryReason.PARSE_ERROR) {
                AiFailureKind.PARSE_ERROR
            } else null,
        )
        val output = AiTranslationStreamAccumulator()
        var lastPreviewLength = 0
        try {
            onStage("AI_STAGE=rewrite_stream_started")
            aiTextGateway.generateStream(request).collect { event ->
                if (event is AiStreamEvent.Content) {
                    output.append(event.text)
                    val restored = protectedText.restore(output.toString())
                    if (restored.length - lastPreviewLength >= STREAM_PREVIEW_MIN_CHARS) {
                        onPartial(
                            layoutChunk?.let { ContentChunker.previewWithLayout(it, restored) } ?: restored
                        )
                        lastPreviewLength = restored.length
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onStage("AI_STAGE=rewrite_provider_error ${error.message ?: error::class.java.simpleName}")
            return Result.failure(error)
        }
        val raw = output.toString().trim()
        if (raw.isBlank()) return Result.failure(Exception("Empty rewrite result"))
        if (raw.trimStart().startsWith("{") || raw.contains("```")) {
            return Result.failure(TranslationLayoutException("Rewrite output must be plain text"))
        }
        val restored = protectedText.restore(raw)
        val tokenViolations = protectedText.integrityViolations(restored)
        if (tokenViolations.isNotEmpty()) {
            return Result.failure(
                TranslationLayoutException("Rewrite changed protected token layout: ${tokenViolations.first()}")
            )
        }
        if (layoutChunk != null && ContentChunker.restoreLayout(layoutChunk, restored) == null) {
            return Result.failure(TranslationLayoutException("Rewrite changed paragraph count"))
        }
        return Result.success(restored)
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
        lastErrorMessage: String? = null,
    ): Result<String> {
        if (preset.taskType == AiTaskType.REWRITE_TEXT) {
            return rewriteWithAiGateway(
                text = text,
                targetLanguage = targetLanguage,
                preset = preset,
                context = context,
                retryReason = retryReason,
                layoutChunk = layoutChunk,
                routeSessionKey = routeSessionKey,
                routeRetryOffset = routeRetryOffset,
                onStage = onStage,
                onPartial = onPartial,
            )
        }
        if (targetLanguage == "en" && isMostlyEnglish(text)) {
            return Result.success(text)
        }
        if (targetLanguage == "zh" && isMostlyChinese(text)) {
            return Result.success(text)
        }
        val isReasoningModel = AiCapability.REASONING in preset.model.capabilities ||
            AiCapability.REASONING in AiModelRegistry.inferCapabilities(preset.model.modelId)

        val promptDictionaries = selectRelevantDictionaries(
            dictionaries = dictionaries,
            sourceAndContext = context.previous + text + context.next,
        )
        val targetLanguageName = getLanguageDisplayName(targetLanguage)
        val retryInstruction = buildRetryInstruction(retryReason, lastErrorMessage, targetLanguageName)
        val protectedText = AiTranslationProtectionProtocol.protect(text)
        val protectedInstruction = buildProtectedTokenInstruction(protectedText)
        val protectedSource = protectedText.value
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
            configuredPrompt = preset.promptTemplate,
        )
        onStage("AI_STAGE=context_pack_ready segments=${contextPack.raw_segments.size}")
        val expectedIds = AiTranslationRefinePipeline.expectedIds(contextPack)
        val systemPrompt = AiTranslationRefinePipeline.buildSystemPrompt(
            configuredPrompt = preset.promptTemplate,
            targetLanguageName = targetLanguageName,
            retryInstruction = retryInstruction,
            protectedInstruction = protectedInstruction,
            promptStages = promptStages,
            includeRetranslateStage = includeRetranslateStage,
        )
        val userPrompt = AiTranslationRefinePipeline.buildUserPrompt(contextPack)
        val outputTokenBudget = AiTranslationTokenBudget.forSourceChars(
            sourceChars = text.length,
            configuredLimit = preset.params.maxOutputTokens,
            providerLimit = preset.model.maxOutputTokens,
            reasoningModel = isReasoningModel,
            structuredJson = true,
        )
        val params = preset.params.copy(
            temperature = preset.params.temperature
                ?: preset.model.defaultParams.temperature
                ?: TranslationConstants.DEFAULT_TEMPERATURE,
            topP = preset.params.topP
                ?: preset.model.defaultParams.topP,
            topK = preset.params.topK
                ?: preset.model.defaultParams.topK,
            repetitionPenalty = preset.params.repetitionPenalty
                ?: preset.model.defaultParams.repetitionPenalty,
            reasoningLevel = if (preset.params.reasoningLevel == AiReasoningLevel.AUTO) {
                AiReasoningLevel.OFF
            } else {
                preset.params.reasoningLevel
            },
            maxOutputTokens = outputTokenBudget,
        )
        val request = AiGenerateRequest(
            model = preset.model,
            messages = listOf(
                AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                AiMessage(AiMessageRole.USER, userPrompt),
            ),
            params = params,
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            outputContract = AiOutputContract.TRANSLATION_JSON,
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
            AiTranslationRefinePipeline.parseRefinerStructureOutput(
                rawOutput = completedContent,
                expectedIds = expectedIds,
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
        val repairedResult = runCatching {
            repairAiResidualSegments(
                result = refinerResult,
                targetLanguage = targetLanguage,
                preset = preset,
                dictionaries = promptDictionaries,
                quickPhonetics = emptyList(),
                routeSessionKey = routeSessionKey,
                routeRetryOffset = routeRetryOffset,
                onStage = onStage,
            ).also { repaired ->
                AiTranslationRefinePipeline.validateQuality(repaired, targetLanguage)
            }
        }.getOrElse { error ->
            onStage(
                "AI_STAGE=quality_error kind=RESIDUAL_CJK " +
                    (error.message ?: error::class.java.simpleName),
            )
            return Result.failure(
                TranslationLayoutException(
                    "Translation quality error: ${error.message ?: "residual CJK text"}",
                )
            )
        }
        val assembledText = AiTranslationRefinePipeline.assemble(repairedResult)
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
        val extractedPairs = repairedResult.new_entities
            .mapNotNull { entity -> entity.toDictionaryPair(promptDictionaries) }
            .take(10)
        if (extractedPairs.isNotEmpty()) {
            onUpdate?.invoke(extractedPairs)
        }
        onStoryMemoryUpdate(repairedResult, text)
        return Result.success(restoredText)
    }

    private suspend fun repairAiResidualSegments(
        result: AiTranslationRefinerResult,
        targetLanguage: String,
        preset: AiTaskPresetConfig,
        dictionaries: List<DictPair>,
        quickPhonetics: List<DictPair>,
        routeSessionKey: String?,
        routeRetryOffset: Int,
        onStage: (String) -> Unit,
    ): AiTranslationRefinerResult {
        if (targetLanguage != TranslationConstants.TARGET_VIETNAMESE) return result
        val repairedSegments = result.refined_segments.map { segment ->
            val current = segment.refined_translation
            if (!current.hasCjkSourceCodePoints() &&
                !AiTranslationRefinePipeline.containsUnicodeCodePointEscape(current)
            ) {
                return@map segment
            }
            val locallyRepaired = repairResidualCjkForVietnamese(
                text = current,
                targetLanguage = targetLanguage,
                translateResidual = { residual ->
                    quickTranslationGateway.translate(
                        text = residual,
                        projectTerms = dictionaries,
                        customPhonetics = quickPhonetics,
                    )
                },
                phoneticResidual = { residual ->
                    quickTranslationGateway.hanViet(residual, quickPhonetics)
                },
            )
            if (!locallyRepaired.hasCjkSourceCodePoints() &&
                !AiTranslationRefinePipeline.containsUnicodeCodePointEscape(locallyRepaired)
            ) {
                onStage("AI_STAGE=segment_cjk_repaired id=${segment.id} method=local")
                return@map segment.copy(refined_translation = locallyRepaired)
            }

            val targeted = requestAiResidualSegment(
                segment = segment,
                targetLanguage = targetLanguage,
                preset = preset,
                routeSessionKey = routeSessionKey,
                routeRetryOffset = routeRetryOffset,
            )
            val targetedText = targeted?.refined_segments
                ?.singleOrNull { it.id == segment.id }
                ?.refined_translation
                ?.trim()
            if (!targetedText.isNullOrBlank()) {
                onStage("AI_STAGE=segment_cjk_repaired id=${segment.id} method=targeted_ai")
                segment.copy(refined_translation = targetedText)
            } else {
                segment.copy(refined_translation = locallyRepaired)
            }
        }
        return result.copy(refined_segments = repairedSegments)
    }

    private suspend fun requestAiResidualSegment(
        segment: io.legado.app.domain.model.AiTranslationRefinedSegment,
        targetLanguage: String,
        preset: AiTaskPresetConfig,
        routeSessionKey: String?,
        routeRetryOffset: Int,
    ): AiTranslationRefinerResult? {
        val languageName = getLanguageDisplayName(targetLanguage)
        val request = AiGenerateRequest(
            model = preset.model,
            messages = listOf(
                AiMessage(
                    AiMessageRole.SYSTEM,
                    "Translate the supplied segment into $languageName. Return exactly one JSON object " +
                        "with refined_segments containing id ${segment.id}. Do not output CJK characters, " +
                        "Unicode code-point escapes, Markdown, or explanations.",
                ),
                AiMessage(
                    AiMessageRole.USER,
                    "{\"refined_segments\":[{\"id\":${segment.id},\"refined_translation\":${GSON.toJson(segment.refined_translation)}," +
                        "\"instruction\":\"Translate every remaining CJK character naturally.\"}]}",
                ),
            ),
            params = preset.params.copy(
                maxOutputTokens = AiTranslationTokenBudget.forSourceChars(
                    sourceChars = segment.refined_translation.length,
                    configuredLimit = preset.params.maxOutputTokens,
                    providerLimit = preset.model.maxOutputTokens,
                    reasoningModel = false,
                    structuredJson = true,
                ),
            ),
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            outputContract = AiOutputContract.TRANSLATION_JSON,
            routeProfileId = preset.runtimeOptions.routeProfileId,
            routeSessionKey = routeSessionKey,
            routeRetryOffset = routeRetryOffset + 1,
            routeSemanticFailureKind = AiFailureKind.PARSE_ERROR,
        )
        val output = AiTranslationStreamAccumulator()
        return runCatching {
            aiTextGateway.generateStream(request).collect { event ->
                if (event is AiStreamEvent.Content) output.append(event.text)
            }
            AiTranslationRefinePipeline.parseRefinerStructureOutput(
                rawOutput = output.toString(),
                expectedIds = listOf(segment.id),
            )
        }.getOrNull()
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
        maxPairs: Int = MAX_DICTIONARY_PAIRS,
    ): List<DictPair> {
        if (dictionaries.isEmpty() || sourceAndContext.isBlank()) return emptyList()
        return dictionaries.asSequence()
            .filter { pair ->
                pair.original.isNotBlank() && sourceAndContext.contains(pair.original)
            }
            .distinctBy(DictPair::original)
            .sortedByDescending { it.original.length }
            .take(maxPairs)
            .toList()
    }

    /** Keeps the Messenger payload and Hachimi lexical search bounded per source chunk. */
    private fun selectNmtDictionaries(
        dictionaries: List<DictPair>,
        source: String,
    ): List<DictPair> {
        if (dictionaries.isEmpty() || source.isBlank()) return emptyList()
        val cacheKey = "${System.identityHashCode(dictionaries)}:${source.hashCode()}"
        synchronized(nmtProjectionCache) {
            nmtProjectionCache[cacheKey]?.let { return it }
        }
        val indexedCandidates = synchronized(nmtProjectionCache) {
            val index = nmtDictionaryIndexCache.getOrPut(dictionaries) {
                dictionaries.asSequence()
                    .filter { it.original.isNotBlank() }
                    .groupBy { it.original.trim().lowercase().first() }
            }
            source.lowercase()
                .asSequence()
                .filterNot(Char::isWhitespace)
                .distinct()
                .flatMap { index[it].orEmpty().asSequence() }
                .distinct()
                .toList()
        }
        val candidates = indexedCandidates.asSequence()
            .filter { pair ->
                pair.original.isNotBlank() &&
                    pair.translation.isNotBlank() &&
                    pair.translation != pair.original &&
                    pair.type != QuickDictionaryType.PHONETIC &&
                    pair.type != QuickDictionaryType.IGNORE &&
                    source.contains(pair.original, ignoreCase = true) &&
                    !(targetLanguageRejectsCjkForNmt(pair.translation))
            }
            // Memory is merged before QT, so the first raw occurrence is canonical. Do not let
            // a lower-priority QT row with another semantic type re-enter the NMT payload.
            .distinctBy { it.original.trim().lowercase() }
            .sortedWith(
                compareByDescending<DictPair> {
                    when (it.type) {
                        QuickDictionaryType.NAME,
                        QuickDictionaryType.PRONOUN -> 2
                        QuickDictionaryType.TERM -> 1
                        else -> 0
                    }
                }.thenByDescending { it.original.length }
                    .thenBy { it.original.lowercase() }
                    .thenBy { it.type.name },
            )
            .toList()
        val hard = candidates.filter {
            it.type == QuickDictionaryType.NAME ||
                it.type == QuickDictionaryType.PRONOUN
        }.take(16)
        val hardKeys = hard.map { it.original.trim().lowercase() }.toSet()
        val soft = candidates
            .filterNot { it.original.trim().lowercase() in hardKeys }
            .take(48)
        return (hard + soft).also { selected ->
            synchronized(nmtProjectionCache) {
                nmtProjectionCache[cacheKey] = selected
            }
        }
    }

    private fun nmtDictionaryFingerprint(
        dictionary: List<DictPair>,
        memoryRevision: String = "",
    ): String =
        dictionary.asSequence()
            .map { "${it.type.name}\u0000${it.original.trim().lowercase()}\u0000${it.translation.trim()}" }
            .sorted()
            .joinToString("\u0001") + "|memory:" + memoryRevision
            .hashCode()
            .toUInt()
            .toString(16)

    private fun targetLanguageRejectsCjkForNmt(target: String): Boolean =
        AiTranslationRefinePipeline.hasCjkTextCodePoints(target) ||
            AiTranslationRefinePipeline.containsUnicodeCodePointEscape(target)

    private fun buildRetryInstruction(
        retryReason: RetryReason?,
        lastErrorMessage: String? = null,
        targetLanguageName: String = "Vietnamese",
    ): String {
        return when (retryReason) {
            RetryReason.EMPTY_RESPONSE -> "\nPrevious attempt returned empty content. Return the required JSON object with every refined_segments id."
            RetryReason.PARSE_ERROR -> {
                val specificError = lastErrorMessage?.trim().orEmpty()
                if (specificError.contains("still contains CJK text", ignoreCase = true) ||
                    specificError.contains("contains un-translated CJK", ignoreCase = true)
                ) {
                    val detail = specificError
                        .substringAfter("Translation parse error:", specificError)
                        .substringAfter("AI_STAGE=parse_error", specificError)
                        .trim()
                    "\nCRITICAL: Previous attempt failed validation because segments contain un-translated Chinese (CJK) text: $detail. You MUST translate EVERY Chinese word, term, and proper noun into $targetLanguageName. Absolutely NO Chinese Hanzi characters may appear in refined_segments."
                } else {
                    "\nPrevious attempt failed JSON, segment-id, layout, or no-CJK validation. Return JSON only and include every expected id exactly once."
                }
            }
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

    private fun postProcessTranslation(
        text: String,
        targetLanguage: String,
        isRewrite: Boolean = false,
    ): String {
        val sanitizedText = TranslationContentSanitizer.sanitize(text)
        return if (targetLanguage == TranslationConstants.TARGET_VIETNAMESE) {
            val cleanedNames = VietnameseTranslationPostProcessor.cleanRogueNameQuestionMarks(
                normalizeCjkPunctuation(sanitizedText)
            )
            val fixedDialogue = VietnameseTranslationPostProcessor.fixContradictoryDialoguePronouns(cleanedNames)
            val fixedForeign = VietnameseTranslationPostProcessor.fixRogueForeignHanVietNames(fixedDialogue)
            val capitalized = VietnameseTranslationPostProcessor.capitalizeSentences(
                VietnameseTranslationPostProcessor.cleanRogueBooleanLiterals(
                    fixedForeign
                )
            )
            if (isRewrite) {
                VietnameseTranslationPostProcessor.indentNarrativeParagraphs(capitalized)
            } else {
                capitalized
            }
        } else {
            sanitizedText
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
                "Translation changed source language: ${translatedCjk} CJK chars or Unicode escapes remain"
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
        if (provider == TranslationConstants.PROVIDER_REWRITE &&
            (translated.trimStart().startsWith("{") || translated.contains("```"))
        ) {
            // Rewrite has a plain-text contract. Do not resurrect old translation JSON or
            // fenced model output from a cache created before the split pipeline.
            return false
        }
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

    private fun nmtOutputQualityError(source: String, translated: String): Exception? {
        val normalizedSource = source.trim()
        val normalizedOutput = translated.trim()
        if (normalizedOutput.isEmpty()) return Exception("NMT returned empty output")
        if (normalizedSource.length > 24 && normalizedSource == normalizedOutput) {
            return Exception("NMT returned source text unchanged")
        }
        val sentences = normalizedOutput
            .split(Regex("(?<=[.!?。！？])\\s+"))
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (sentences.size >= 3 && sentences.zipWithNext().count { (left, right) -> left == right } >= 2) {
            return Exception("NMT returned repeated output")
        }
        return null
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
    storyMemoryEnabled: Boolean,
    aiConcurrentRequests: Int,
    standardConcurrentRequests: Int,
): Int = when {
    provider == TranslationConstants.PROVIDER_LOCAL_AI || provider == TranslationConstants.PROVIDER_NMT -> 1
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
private const val AI_REWRITE_PIPELINE_REVISION =
    "rewrite-engine-android-v1-plain-text-contract"

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
        if (storyMemoryRevision.isNotBlank()) {
            append("|story-memory:").append(computeHash(storyMemoryRevision))
        }
        if (provider == TranslationConstants.PROVIDER_APP_AI) {
            append("|ai-pipeline:").append(AI_TRANSLATION_PIPELINE_REVISION)
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
    if (targetLanguage != TranslationConstants.TARGET_VIETNAMESE) return false
    if (containsCjkUnicodeEscape(translated)) return true
    if (source.isBlank()) return false
    val sourceCjk = source.countCjkSourceCodePoints()
    if (sourceCjk <= 0) return false
    return translated.hasCjkSourceCodePoints() || containsCjkUnicodeEscape(translated)
}

internal fun containsCjkUnicodeEscape(text: String): Boolean =
    Regex("\\bU\\+[0-9A-Fa-f]{4,6}\\b").containsMatchIn(text)

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
                                ?: unresolved.value
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
