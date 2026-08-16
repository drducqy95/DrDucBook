package io.legado.app.ui.media.download

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.MediaDownloadState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class MediaDownloadItemUi(
    val id: String,
    val taskId: String,
    val title: String,
    val state: MediaDownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val localPath: String,
    val mimeType: String,
    val errorMessage: String?,
    val sortOrder: Int,
)

@Stable
data class MediaDownloadTaskUi(
    val id: String,
    val bookTitle: String,
    val state: MediaDownloadState,
    val errorMessage: String?,
    val createdAt: Long,
    val items: ImmutableList<MediaDownloadItemUi>,
)

@Stable
data class MediaDownloadsUiState(
    val loading: Boolean = true,
    val tasks: ImmutableList<MediaDownloadTaskUi> = persistentListOf(),
    val filter: MediaDownloadFilter = MediaDownloadFilter.ALL,
    val sort: MediaDownloadSort = MediaDownloadSort.DATE,
    val storageUsedBytes: Long = 0L,
    val totalTaskCount: Int = 0,
    val activeTaskCount: Int = 0,
    val completedTaskCount: Int = 0,
    val failedTaskCount: Int = 0,
    val recoverableTaskCount: Int = 0,
)

enum class MediaDownloadFilter { ALL, ACTIVE, COMPLETED, FAILED }
enum class MediaDownloadSort { DATE, NAME, STATUS, SIZE }

sealed interface MediaDownloadsIntent {
    data object StartQueue : MediaDownloadsIntent
    data class Pause(val taskId: String) : MediaDownloadsIntent
    data class Resume(val taskId: String) : MediaDownloadsIntent
    data class Retry(val taskId: String) : MediaDownloadsIntent
    data class Cancel(val taskId: String) : MediaDownloadsIntent
    data class Delete(val taskId: String) : MediaDownloadsIntent
    data class DeleteItem(val itemId: String) : MediaDownloadsIntent
    data class Open(val itemId: String) : MediaDownloadsIntent
    data class Share(val itemId: String) : MediaDownloadsIntent
    data class Export(val itemId: String) : MediaDownloadsIntent
    data class MoveEarlier(val itemId: String) : MediaDownloadsIntent
    data class MoveLater(val itemId: String) : MediaDownloadsIntent
    data object PauseAll : MediaDownloadsIntent
    data object ResumeAll : MediaDownloadsIntent
    data object CancelAll : MediaDownloadsIntent
    data object DeleteCompleted : MediaDownloadsIntent
    data class SetFilter(val value: MediaDownloadFilter) : MediaDownloadsIntent
    data class SetSort(val value: MediaDownloadSort) : MediaDownloadsIntent
}

sealed interface MediaDownloadsEffect {
    data object StartService : MediaDownloadsEffect
    data class OpenFile(val path: String, val mimeType: String) : MediaDownloadsEffect
    data class ShareFile(val path: String, val mimeType: String) : MediaDownloadsEffect
    data class ExportFile(
        val path: String,
        val mimeType: String,
        val fileName: String,
    ) : MediaDownloadsEffect
    data class ShowMessage(val message: String) : MediaDownloadsEffect
}
