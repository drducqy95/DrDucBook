package io.legado.app.data.repository

import io.legado.app.data.dao.BookDao
import io.legado.app.domain.gateway.MediaDownloadGateway
import io.legado.app.domain.gateway.DownloadCenterGateway
import io.legado.app.domain.model.DownloadAttention
import io.legado.app.domain.model.DownloadCenterAction
import io.legado.app.domain.model.DownloadCenterItem
import io.legado.app.domain.model.DownloadCenterSnapshot
import io.legado.app.domain.model.DownloadDisplayState
import io.legado.app.domain.model.DownloadItemKind
import io.legado.app.domain.model.MediaDownloadState
import io.legado.app.model.CacheBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DownloadCenterRepository(
    private val bookDao: BookDao,
    private val mediaDownloadGateway: MediaDownloadGateway,
) : DownloadCenterGateway {

    private val snapshot: Flow<DownloadCenterSnapshot> = combine(
        CacheBook.downloadStateFlow,
        CacheBook.pendingAdmissionFlow,
        mediaDownloadGateway.observeTasks(),
        bookDao.flowAll(),
    ) { bookState, pending, mediaTasks, books ->
        val bookNames = books.associateBy { it.bookUrl }
        val bookItems = bookState.books.values.mapNotNull { state ->
            val waiting = state.waitingCount + pending[state.bookUrl].orZero()
            val running = state.runningIndices.size
            val paused = state.pausedIndices.size
            val failed = state.failedIndices.size + if (state.failureMessage.isNullOrBlank()) 0 else 1
            val displayState = when {
                running > 0 -> DownloadDisplayState.RUNNING
                waiting > 0 -> DownloadDisplayState.WAITING
                paused > 0 -> DownloadDisplayState.PAUSED
                failed > 0 -> DownloadDisplayState.FAILED
                state.successCount > 0 -> DownloadDisplayState.COMPLETED
                else -> return@mapNotNull null
            }
            DownloadCenterItem(
                id = "book:${state.bookUrl}",
                kind = DownloadItemKind.BOOK,
                title = bookNames[state.bookUrl]?.name?.ifBlank { state.bookUrl } ?: state.bookUrl,
                subtitle = "${running + waiting + paused} đang xử lý · $failed lỗi",
                coverUrl = bookNames[state.bookUrl]?.getDisplayCover(),
                state = displayState,
                progress = null,
                downloadedBytes = null,
                totalBytes = null,
                activeCount = running + waiting,
                completedCount = state.successCount,
                failedCount = failed,
                errorMessage = state.failureMessage
                    ?: state.failureMessages.values.firstOrNull(),
                updatedAt = System.currentTimeMillis(),
            )
        }
        val mediaItems = mediaTasks.map { task ->
            val items = task.items
            val active = items.count {
                it.state == MediaDownloadState.PENDING || it.state == MediaDownloadState.RUNNING
            }
            val recoverable = items.count {
                it.state == MediaDownloadState.PAUSED || it.state == MediaDownloadState.FAILED
            }
            val downloaded = items.sumOf { it.bytesDownloaded }
            val total = items.sumOf { it.totalBytes }.takeIf { it > 0L }
            DownloadCenterItem(
                id = "media:${task.id}",
                kind = DownloadItemKind.MEDIA,
                title = task.bookTitle,
                subtitle = "${items.size} tập media",
                coverUrl = task.coverUrl,
                state = task.state.toDisplayState(),
                progress = total?.let { (downloaded.toFloat() / it).coerceIn(0f, 1f) },
                downloadedBytes = downloaded,
                totalBytes = total,
                activeCount = active,
                completedCount = items.count { it.state == MediaDownloadState.COMPLETED },
                failedCount = recoverable,
                errorMessage = task.errorMessage ?: items.firstNotNullOfOrNull { it.errorMessage },
                updatedAt = task.updatedAt,
            )
        }
        val items = (bookItems + mediaItems).sortedByDescending { it.updatedAt }
        DownloadCenterSnapshot(
            items = items,
            attention = DownloadAttention(
                activeCount = items.count {
                    it.state == DownloadDisplayState.WAITING ||
                        it.state == DownloadDisplayState.RUNNING
                },
                recoverableCount = items.count {
                    it.state == DownloadDisplayState.PAUSED ||
                        it.state == DownloadDisplayState.FAILED
                },
            ),
        )
    }.distinctUntilChanged()

    override fun observeSnapshot(): Flow<DownloadCenterSnapshot> = snapshot

    override fun observeAttention(): Flow<DownloadAttention> = snapshot.map { it.attention }

    override suspend fun perform(action: DownloadCenterAction) {
        when (action) {
            is DownloadCenterAction.PauseMedia -> mediaDownloadGateway.pause(action.taskId)
            is DownloadCenterAction.ResumeMedia -> mediaDownloadGateway.resume(action.taskId)
            is DownloadCenterAction.RetryMedia -> mediaDownloadGateway.retry(action.taskId)
            is DownloadCenterAction.CancelMedia -> mediaDownloadGateway.cancel(action.taskId)
            is DownloadCenterAction.DeleteMedia -> mediaDownloadGateway.delete(action.taskId)
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun MediaDownloadState.toDisplayState(): DownloadDisplayState = when (this) {
        MediaDownloadState.PENDING -> DownloadDisplayState.WAITING
        MediaDownloadState.RUNNING -> DownloadDisplayState.RUNNING
        MediaDownloadState.PAUSED -> DownloadDisplayState.PAUSED
        MediaDownloadState.FAILED -> DownloadDisplayState.FAILED
        MediaDownloadState.COMPLETED -> DownloadDisplayState.COMPLETED
        MediaDownloadState.CANCELED -> DownloadDisplayState.CANCELED
    }
}
