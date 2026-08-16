package io.legado.app.model.translation

import io.legado.app.domain.model.AiProviderFailure

/**
 * Key for per-chapter translation display state.
 * Used as task key for looking up ongoing translation tasks in TranslationManager.
 */
data class TranslationChapterKey(
    val bookUrl: String,
    val chapterIndex: Int,
    val provider: String,
    val targetLanguage: String,
)

/**
 * Per-chapter translation status.
 */
enum class TranslationChapterStatus {
    Idle,
    Translating,
    Translated,
    Failed,
    Cancelled,
}

enum class TranslationLogType {
    TASK_CREATED,
    CONFIGURATION,
    PIPELINE_STAGE,
    MEMORY_COMMITTED,
    MEMORY_WARNING,
    CHUNK_COMPLETED,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class TranslationLogEntry(
    val type: TranslationLogType,
    val provider: String = "",
    val targetLanguage: String = "",
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val detail: String = "",
)

/**
 * Per-chapter translation state stored in TranslationManager.
 * Runtime-only, derived from translation cache on app restart.
 */
data class TranslationChapterState(
    val key: TranslationChapterKey,
    val status: TranslationChapterStatus = TranslationChapterStatus.Idle,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val mixedContent: String? = null,
    val translatedContent: String? = null,
    val errorMessage: String? = null,
    val failure: AiProviderFailure? = null,
    val logs: List<TranslationLogEntry> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

internal fun TranslationChapterState.failPreservingProgress(
    errorMessage: String,
    failure: AiProviderFailure?,
    logs: List<TranslationLogEntry>,
    updatedAt: Long = System.currentTimeMillis(),
): TranslationChapterState = copy(
    status = TranslationChapterStatus.Failed,
    errorMessage = errorMessage,
    failure = failure,
    logs = logs,
    updatedAt = updatedAt,
)
