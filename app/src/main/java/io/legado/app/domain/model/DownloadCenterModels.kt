package io.legado.app.domain.model

enum class DownloadItemKind {
    BOOK,
    MEDIA,
}

enum class DownloadDisplayState {
    WAITING,
    RUNNING,
    PAUSED,
    FAILED,
    COMPLETED,
    CANCELED,
}

data class DownloadCenterItem(
    val id: String,
    val kind: DownloadItemKind,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
    val state: DownloadDisplayState,
    val progress: Float?,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val activeCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val errorMessage: String?,
    val updatedAt: Long,
)

data class DownloadAttention(
    val activeCount: Int = 0,
    val recoverableCount: Int = 0,
) {
    val hasAttention: Boolean
        get() = activeCount > 0 || recoverableCount > 0
}

data class DownloadCenterSnapshot(
    val items: List<DownloadCenterItem> = emptyList(),
    val attention: DownloadAttention = DownloadAttention(),
)

sealed interface DownloadCenterAction {
    data class PauseMedia(val taskId: String) : DownloadCenterAction
    data class ResumeMedia(val taskId: String) : DownloadCenterAction
    data class RetryMedia(val taskId: String) : DownloadCenterAction
    data class CancelMedia(val taskId: String) : DownloadCenterAction
    data class DeleteMedia(val taskId: String) : DownloadCenterAction
}
