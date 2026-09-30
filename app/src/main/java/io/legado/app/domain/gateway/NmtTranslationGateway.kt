package io.legado.app.domain.gateway

import io.legado.app.domain.model.DictPair

enum class NmtQualityStatus {
    PASS,
    DEGRADED,
}

data class NmtQualityReport(
    val status: NmtQualityStatus = NmtQualityStatus.PASS,
    val missingRequiredTerms: List<String> = emptyList(),
    val repeatedOutput: Boolean = false,
    val truncatedOutput: Boolean = false,
    val layoutValid: Boolean = true,
)

data class NmtPerformanceMetrics(
    val serviceBindMs: Long = 0L,
    val modelLoadMs: Long = 0L,
    val dictionaryProjectionMs: Long = 0L,
    val ipcSerializeMs: Long = 0L,
    val ipcDeserializeMs: Long = 0L,
    val sourceTokenizationMs: Long = 0L,
    val constraintEncodingMs: Long = 0L,
    val encoderMs: Long = 0L,
    val decoderMs: Long = 0L,
    val strictRetryCount: Int = 0,
    val strictRetryMs: Long = 0L,
    val detokenizerMs: Long = 0L,
    val totalMs: Long = 0L,
    val timeToFirstSegmentMs: Long = 0L,
    val generatedTokens: Int = 0,
    val sourceSegments: Int = 0,
    val tokensPerSecond: Float = 0f,
    val ipcBytes: Long = 0L,
    val candidateConstraintCount: Int = 0,
    val sentConstraintCount: Int = 0,
)

data class NmtTranslationResult(
    val text: String,
    val sourceSegments: Int,
    val generatedTokens: Int,
    val missingRequiredTerms: List<String>,
    val attribution: String,
    val qualityReport: NmtQualityReport = NmtQualityReport(
        missingRequiredTerms = missingRequiredTerms,
        status = if (missingRequiredTerms.isEmpty()) {
            NmtQualityStatus.PASS
        } else {
            NmtQualityStatus.DEGRADED
        },
    ),
    val dictionaryRevision: String = "",
    val constraintFingerprint: String = "",
    val metrics: NmtPerformanceMetrics = NmtPerformanceMetrics(
        generatedTokens = generatedTokens,
        sourceSegments = sourceSegments,
    ),
)

data class NmtDecodeConfig(
    val maxSourceTokens: Int = 64,
    val maxSourceChars: Int = 512,
    /** Optional source-side control prefix for prompt-aware NMT models. Never shared with AI. */
    val sourcePrompt: String = "",
    val maxNewTokens: Int = 192,
    val repetitionPenalty: Float = 1.2f,
    val noRepeatNgramSize: Int = 2,
    val retryMissingRequiredTerms: Boolean = true,
    val modelId: String = "hachimi_onnx",
    val dictionaryRevision: String = "",
    val constraintFingerprint: String = "",
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
