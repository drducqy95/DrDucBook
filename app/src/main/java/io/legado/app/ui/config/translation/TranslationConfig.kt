package io.legado.app.ui.config.translation

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.QuickTranslationPronounMode
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.ui.config.prefDelegate

object TranslationConfig {

    var llmTranslateEnabled by prefDelegate(
        PreferKey.llmTranslateEnabled,
        false
    )

    private var storedLlmProvider by prefDelegate(
        PreferKey.llmProvider,
        "google"
    )

    var llmProvider: String
        get() = when (storedLlmProvider) {
            TranslationConstants.PROVIDER_OPENAI -> TranslationConstants.PROVIDER_APP_AI
            else -> storedLlmProvider
        }
        set(value) {
            storedLlmProvider = value
        }

    var llmBaseUrl by prefDelegate(
        PreferKey.llmBaseUrl,
        ""
    )

    var llmApiKey by prefDelegate(
        PreferKey.llmApiKey,
        ""
    )

    var llmModel by prefDelegate(
        PreferKey.llmModel,
        ""
    )

    var llmTargetLanguage by prefDelegate(
        PreferKey.llmTargetLanguage,
        "zh"
    )

    private var storedLlmMaxCharsPerChunk by prefDelegate(
        PreferKey.llmMaxCharsPerChunk,
        1000
    )
    private var llmChunkTuningVersion by prefDelegate(
        PreferKey.llmChunkTuningVersion,
        0,
    )
    var llmMaxCharsPerChunk: Int
        get() {
            if (llmChunkTuningVersion < CURRENT_LLM_CHUNK_TUNING_VERSION) {
                if (storedLlmMaxCharsPerChunk == 10000) storedLlmMaxCharsPerChunk = 1000
                llmChunkTuningVersion = CURRENT_LLM_CHUNK_TUNING_VERSION
            }
            return storedLlmMaxCharsPerChunk.coerceIn(MIN_CHUNK_CHARS, MAX_CHUNK_CHARS)
        }
        set(value) {
            storedLlmMaxCharsPerChunk = value.coerceIn(MIN_CHUNK_CHARS, MAX_CHUNK_CHARS)
            llmChunkTuningVersion = CURRENT_LLM_CHUNK_TUNING_VERSION
        }

    var llmConcurrentChunks by prefDelegate(
        PreferKey.llmConcurrentChunks,
        2
    )

    private var storedAiMaxCharsPerChunk by prefDelegate(
        PreferKey.aiTranslationMaxCharsPerChunk,
        OPTIMIZED_AI_CHUNK_CHARS,
    )
    private var aiChunkTuningVersion by prefDelegate(
        PreferKey.aiTranslationChunkTuningVersion,
        0,
    )
    var aiMaxCharsPerChunk: Int
        get() {
            if (aiChunkTuningVersion < CURRENT_AI_CHUNK_TUNING_VERSION) {
                if (storedAiMaxCharsPerChunk == LEGACY_AI_CHUNK_CHARS) {
                    storedAiMaxCharsPerChunk = OPTIMIZED_AI_CHUNK_CHARS
                }
                aiChunkTuningVersion = CURRENT_AI_CHUNK_TUNING_VERSION
            }
            return storedAiMaxCharsPerChunk.coerceIn(MIN_CHUNK_CHARS, MAX_CHUNK_CHARS)
        }
        set(value) {
            storedAiMaxCharsPerChunk = value.coerceIn(MIN_CHUNK_CHARS, MAX_CHUNK_CHARS)
            aiChunkTuningVersion = CURRENT_AI_CHUNK_TUNING_VERSION
        }

    private var storedAiConcurrentChunks by prefDelegate(
        PreferKey.aiTranslationConcurrentChunks,
        1,
    )
    var aiConcurrentChunks: Int
        get() {
            if (aiChunkTuningVersion < CURRENT_AI_CHUNK_TUNING_VERSION) {
                aiChunkTuningVersion = CURRENT_AI_CHUNK_TUNING_VERSION
            }
            return storedAiConcurrentChunks.coerceIn(1, 4)
        }
        set(value) {
            storedAiConcurrentChunks = value.coerceIn(1, 4)
        }

    var llmRetryCount by prefDelegate(
        PreferKey.llmRetryCount,
        2
    )

    private var storedLlmTemperature by prefDelegate(
        PreferKey.llmTemperature,
        TranslationConstants.DEFAULT_TEMPERATURE
    )

    var llmTemperature: Float
        get() = storedLlmTemperature.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)
        set(value) {
            storedLlmTemperature = value.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)
        }

    var llmPrompt by prefDelegate(
        PreferKey.llmPrompt,
        TranslationConstants.DEFAULT_PROMPT
    )

    var autoTranslateEnabled by prefDelegate(
        PreferKey.translationAutoEnabled,
        false,
    )

    var autoTranslateWifiOnly by prefDelegate(
        PreferKey.translationAutoWifiOnly,
        true,
    )

    var dynamicUiTranslationEnabled by prefDelegate(
        PreferKey.translationDynamicUiEnabled,
        true,
    )

    var quickTranslationPronounMode by prefDelegate(
        PreferKey.quickTranslationPronounMode,
        QuickTranslationPronounMode.default.value,
    )

    var promptPipelineInitialized by prefDelegate(
        PreferKey.translationPromptPipelineInitialized,
        false,
    )

    private var storedNmtMaxCharsPerChunk by prefDelegate(
        PreferKey.nmtMaxCharsPerChunk,
        512,
    )
    private var nmtChunkTuningVersion by prefDelegate(
        PreferKey.nmtChunkTuningVersion,
        0,
    )
    var nmtMaxCharsPerChunk: Int
        get() {
            migrateNmtTuning()
            return storedNmtMaxCharsPerChunk.coerceIn(MIN_CHUNK_CHARS, MAX_CHUNK_CHARS)
        }
        set(value) {
            storedNmtMaxCharsPerChunk = value.coerceIn(MIN_CHUNK_CHARS, MAX_CHUNK_CHARS)
            nmtChunkTuningVersion = CURRENT_NMT_CHUNK_TUNING_VERSION
        }

    private var storedNmtSourceTokenBudget by prefDelegate(
        PreferKey.nmtSourceTokenBudget,
        64,
    )
    var nmtSourceTokenBudget: Int
        get() {
            migrateNmtTuning()
            return storedNmtSourceTokenBudget.coerceIn(32, 480)
        }
        set(value) {
            storedNmtSourceTokenBudget = value.coerceIn(32, 480)
            nmtChunkTuningVersion = CURRENT_NMT_CHUNK_TUNING_VERSION
        }

    var nmtSourcePrompt by prefDelegate(
        PreferKey.nmtSourcePrompt,
        "",
    )

    private var storedNmtMaxNewTokens by prefDelegate(
        PreferKey.nmtMaxNewTokens,
        192,
    )
    var nmtMaxNewTokens: Int
        get() {
            migrateNmtTuning()
            return storedNmtMaxNewTokens.coerceIn(32, 384)
        }
        set(value) {
            storedNmtMaxNewTokens = value.coerceIn(32, 384)
            nmtChunkTuningVersion = CURRENT_NMT_CHUNK_TUNING_VERSION
        }

    private var storedNmtRepetitionPenalty by prefDelegate(
        PreferKey.nmtRepetitionPenalty,
        1.2f,
    )
    var nmtRepetitionPenalty: Float
        get() = storedNmtRepetitionPenalty.coerceIn(1f, 2f)
        set(value) { storedNmtRepetitionPenalty = value.coerceIn(1f, 2f) }

    var nmtNoRepeatBigram by prefDelegate(
        PreferKey.nmtNoRepeatBigram,
        true,
    )

    var nmtRetryMissingTerms by prefDelegate(
        PreferKey.nmtRetryMissingTerms,
        true,
    )

    private var storedAutoTranslateNextChapters by prefDelegate(
        PreferKey.translationAutoNextChapters,
        3,
    )

    var autoTranslateNextChapters: Int
        get() = storedAutoTranslateNextChapters.coerceIn(0, 20)
        set(value) {
            storedAutoTranslateNextChapters = value.coerceIn(0, 20)
        }

    // Delegate constants to domain layer
    const val PROVIDER_OPENAI = TranslationConstants.PROVIDER_OPENAI
    const val PROVIDER_APP_AI = TranslationConstants.PROVIDER_APP_AI
    const val PROVIDER_GOOGLE = TranslationConstants.PROVIDER_GOOGLE
    const val PROVIDER_QUICK_TRANSLATOR = TranslationConstants.PROVIDER_QUICK_TRANSLATOR
    const val PROVIDER_NMT = TranslationConstants.PROVIDER_NMT
    const val PROVIDER_ML_KIT = TranslationConstants.PROVIDER_ML_KIT
    val providerDisplayNames get() = TranslationConstants.providerDisplayNames
    val providerValues get() = TranslationConstants.providerValues
    val targetLanguages get() = TranslationConstants.targetLanguages
    const val MIN_TEMPERATURE = TranslationConstants.MIN_TEMPERATURE
    const val MAX_TEMPERATURE = TranslationConstants.MAX_TEMPERATURE
    const val DEFAULT_TEMPERATURE = TranslationConstants.DEFAULT_TEMPERATURE
    const val DEFAULT_PROMPT = TranslationConstants.DEFAULT_PROMPT
    const val OUTPUT_FORMAT = TranslationConstants.OUTPUT_FORMAT
    const val MIN_CHUNK_CHARS = 10
    const val MAX_CHUNK_CHARS = 10000
    private const val LEGACY_AI_CHUNK_CHARS = 6000
    private const val OPTIMIZED_AI_CHUNK_CHARS = 1000
    private const val CURRENT_AI_CHUNK_TUNING_VERSION = 2
    private const val CURRENT_LLM_CHUNK_TUNING_VERSION = 1
    private const val CURRENT_NMT_CHUNK_TUNING_VERSION = 1
    private fun migrateNmtTuning() {
        if (nmtChunkTuningVersion >= CURRENT_NMT_CHUNK_TUNING_VERSION) return
        if (storedNmtMaxCharsPerChunk == 1000) storedNmtMaxCharsPerChunk = 512
        if (storedNmtSourceTokenBudget == 96) storedNmtSourceTokenBudget = 64
        if (storedNmtMaxNewTokens == 240) storedNmtMaxNewTokens = 192
        nmtChunkTuningVersion = CURRENT_NMT_CHUNK_TUNING_VERSION
    }
}
