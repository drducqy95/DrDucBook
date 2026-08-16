package io.legado.app.ui.media.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.domain.gateway.MediaDownloadGateway
import io.legado.app.domain.model.MediaDownloadState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.File

class MediaDownloadsViewModel(
    private val gateway: MediaDownloadGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaDownloadsUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MediaDownloadsEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private var allTasks: List<MediaDownloadTaskUi> = emptyList()

    init {
        viewModelScope.launch {
            gateway.observeTasks().collect { tasks ->
                allTasks = tasks.map { task ->
                    MediaDownloadTaskUi(
                        id = task.id,
                        bookTitle = task.bookTitle,
                        state = task.state,
                        errorMessage = task.errorMessage,
                        createdAt = task.createdAt,
                        items = task.items.map { item ->
                            MediaDownloadItemUi(
                                id = item.id,
                                taskId = item.taskId,
                                title = item.episodeTitle,
                                state = item.state,
                                bytesDownloaded = item.bytesDownloaded,
                                totalBytes = item.totalBytes,
                                localPath = item.localPath,
                                mimeType = item.mimeType,
                                errorMessage = item.errorMessage,
                                sortOrder = item.sortOrder,
                            )
                        }.toImmutableList(),
                    )
                }
                refreshVisibleTasks(loading = false)
            }
        }
    }

    fun onIntent(intent: MediaDownloadsIntent) {
        when (intent) {
            MediaDownloadsIntent.StartQueue -> _effects.tryEmit(MediaDownloadsEffect.StartService)
            is MediaDownloadsIntent.Pause -> runAction { gateway.pause(intent.taskId) }
            is MediaDownloadsIntent.Resume -> runAction(startService = true) { gateway.resume(intent.taskId) }
            is MediaDownloadsIntent.Retry -> runAction(startService = true) { gateway.retry(intent.taskId) }
            is MediaDownloadsIntent.Cancel -> runAction { gateway.cancel(intent.taskId) }
            is MediaDownloadsIntent.Delete -> runAction { gateway.delete(intent.taskId) }
            is MediaDownloadsIntent.DeleteItem -> runAction { gateway.deleteItem(intent.itemId) }
            is MediaDownloadsIntent.Open -> open(intent.itemId)
            is MediaDownloadsIntent.Share -> fileAction(intent.itemId, share = true)
            is MediaDownloadsIntent.Export -> fileAction(intent.itemId, share = false)
            is MediaDownloadsIntent.MoveEarlier -> move(intent.itemId, earlier = true)
            is MediaDownloadsIntent.MoveLater -> move(intent.itemId, earlier = false)
            MediaDownloadsIntent.PauseAll -> runAction { gateway.pauseActive() }
            MediaDownloadsIntent.ResumeAll -> runAction(startService = true) { gateway.resumeRecoverable() }
            MediaDownloadsIntent.CancelAll -> runAction { gateway.cancelActive() }
            MediaDownloadsIntent.DeleteCompleted -> batchAction { task ->
                if (task.state == MediaDownloadState.COMPLETED) gateway.delete(task.id)
            }
            is MediaDownloadsIntent.SetFilter -> {
                _uiState.update { it.copy(filter = intent.value) }
                refreshVisibleTasks()
            }
            is MediaDownloadsIntent.SetSort -> {
                _uiState.update { it.copy(sort = intent.value) }
                refreshVisibleTasks()
            }
        }
    }

    private fun refreshVisibleTasks(loading: Boolean = _uiState.value.loading) {
        _uiState.update { buildMediaDownloadsUiState(allTasks, it, loading) }
    }

    private fun batchAction(
        startService: Boolean = false,
        action: suspend (MediaDownloadTaskUi) -> Unit,
    ) = runAction(startService) { allTasks.forEach { action(it) } }

    private fun runAction(startService: Boolean = false, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { if (startService) _effects.tryEmit(MediaDownloadsEffect.StartService) }
                .onFailure { error ->
                    _effects.tryEmit(MediaDownloadsEffect.ShowMessage(error.localizedMessage.orEmpty()))
                }
        }
    }

    private fun open(itemId: String) {
        val item = allTasks.flatMap(MediaDownloadTaskUi::items)
            .firstOrNull { it.id == itemId }
            ?: return
        if (item.state != MediaDownloadState.COMPLETED || item.localPath.isBlank()) {
            _effects.tryEmit(MediaDownloadsEffect.ShowMessage(appCtx.getString(R.string.media_download_file_not_ready)))
            return
        }
        _effects.tryEmit(MediaDownloadsEffect.OpenFile(item.localPath, item.mimeType))
    }

    private fun fileAction(itemId: String, share: Boolean) {
        val item = allTasks.flatMap(MediaDownloadTaskUi::items).firstOrNull { it.id == itemId }
            ?: return
        if (item.state != MediaDownloadState.COMPLETED || item.localPath.isBlank()) {
            _effects.tryEmit(MediaDownloadsEffect.ShowMessage(appCtx.getString(R.string.media_download_file_not_ready)))
            return
        }
        if (share) {
            _effects.tryEmit(MediaDownloadsEffect.ShareFile(item.localPath, item.mimeType))
        } else {
            _effects.tryEmit(
                MediaDownloadsEffect.ExportFile(
                    item.localPath,
                    item.mimeType,
                    File(item.localPath).name,
                )
            )
        }
    }

    private fun move(itemId: String, earlier: Boolean) {
        val items = allTasks.flatMap(MediaDownloadTaskUi::items).sortedBy(MediaDownloadItemUi::sortOrder)
        val index = items.indexOfFirst { it.id == itemId }
        val targetIndex = if (earlier) index - 1 else index + 1
        if (index < 0 || targetIndex !in items.indices) return
        val current = items[index]
        val target = items[targetIndex]
        viewModelScope.launch {
            runCatching {
                gateway.reorder(current.id, target.sortOrder)
                gateway.reorder(target.id, current.sortOrder)
            }.onFailure { error ->
                _effects.tryEmit(MediaDownloadsEffect.ShowMessage(error.localizedMessage.orEmpty()))
            }
        }
    }
}

internal fun buildMediaDownloadsUiState(
    allTasks: List<MediaDownloadTaskUi>,
    currentState: MediaDownloadsUiState,
    loading: Boolean = currentState.loading,
): MediaDownloadsUiState {
    val filtered = allTasks.filter { task ->
        when (currentState.filter) {
            MediaDownloadFilter.ALL -> true
            MediaDownloadFilter.ACTIVE -> task.state in setOf(
                MediaDownloadState.PENDING,
                MediaDownloadState.RUNNING,
                MediaDownloadState.PAUSED,
            )
            MediaDownloadFilter.COMPLETED -> task.state == MediaDownloadState.COMPLETED
            MediaDownloadFilter.FAILED -> task.state == MediaDownloadState.FAILED
        }
    }
    val sorted = when (currentState.sort) {
        MediaDownloadSort.DATE -> filtered.sortedByDescending(MediaDownloadTaskUi::createdAt)
        MediaDownloadSort.NAME -> filtered.sortedBy { it.bookTitle.lowercase() }
        MediaDownloadSort.STATUS -> filtered.sortedBy { it.state.name }
        MediaDownloadSort.SIZE -> filtered.sortedByDescending { task ->
            task.items.sumOf(MediaDownloadItemUi::bytesDownloaded)
        }
    }
    return currentState.copy(
        loading = loading,
        tasks = sorted.toImmutableList(),
        storageUsedBytes = allTasks.flatMap(MediaDownloadTaskUi::items)
            .filter { item -> item.state == MediaDownloadState.COMPLETED }
            .sumOf(MediaDownloadItemUi::bytesDownloaded),
        totalTaskCount = allTasks.size,
        activeTaskCount = allTasks.count { task ->
            task.state in setOf(
                MediaDownloadState.PENDING,
                MediaDownloadState.RUNNING,
                MediaDownloadState.PAUSED,
            )
        },
        completedTaskCount = allTasks.count { task -> task.state == MediaDownloadState.COMPLETED },
        failedTaskCount = allTasks.count { task -> task.state == MediaDownloadState.FAILED },
        recoverableTaskCount = allTasks.count { task ->
            task.state in setOf(MediaDownloadState.PAUSED, MediaDownloadState.FAILED)
        },
    )
}
