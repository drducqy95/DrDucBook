package io.legado.app.domain.model

enum class LocalTtsImportStage {
    PREPARING,
    EXTRACTING,
    VALIDATING,
    PROBING,
    INSTALLING,
}

data class LocalTtsImportProgress(
    val stage: LocalTtsImportStage,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
) {
    val fraction: Float?
        get() = totalBytes.takeIf { it > 0L }
            ?.let { (processedBytes.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
}
