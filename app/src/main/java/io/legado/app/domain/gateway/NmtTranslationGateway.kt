package io.legado.app.domain.gateway

import io.legado.app.domain.model.DictPair

data class NmtTranslationResult(
    val text: String,
    val sourceSegments: Int,
    val generatedTokens: Int,
    val missingRequiredTerms: List<String>,
    val attribution: String,
)

data class NmtDecodeConfig(
    val maxSourceTokens: Int = 96,
    val maxSourceChars: Int = 1000,
    /** Optional source-side control prefix for prompt-aware NMT models. Never shared with AI. */
    val sourcePrompt: String = "",
    val maxNewTokens: Int = 240,
    val repetitionPenalty: Float = 1.2f,
    val noRepeatNgramSize: Int = 2,
    val retryMissingRequiredTerms: Boolean = true,
)

interface NmtTranslationGateway {
    suspend fun translate(
        text: String,
        dictionary: List<DictPair> = emptyList(),
        config: NmtDecodeConfig = NmtDecodeConfig(),
        onProgress: suspend (
            completedSegments: Int,
            totalSegments: Int,
            mixedText: String,
        ) -> Unit = { _, _, _ -> },
    ): NmtTranslationResult

    suspend fun close()
}
