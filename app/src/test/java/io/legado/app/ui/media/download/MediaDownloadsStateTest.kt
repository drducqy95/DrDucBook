package io.legado.app.ui.media.download

import io.legado.app.domain.model.MediaDownloadState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaDownloadsStateTest {

    @Test
    fun reducerFiltersActiveTasksAndKeepsSummaryCountsFromFullList() {
        val tasks = listOf(
            task("running", "Zeta", MediaDownloadState.RUNNING, downloaded = 100L, createdAt = 4L),
            task("paused", "Alpha", MediaDownloadState.PAUSED, downloaded = 50L, createdAt = 3L),
            task("failed", "Beta", MediaDownloadState.FAILED, downloaded = 25L, createdAt = 2L),
            task("done", "Gamma", MediaDownloadState.COMPLETED, downloaded = 200L, createdAt = 1L),
        )

        val state = buildMediaDownloadsUiState(
            allTasks = tasks,
            currentState = MediaDownloadsUiState(
                loading = true,
                filter = MediaDownloadFilter.ACTIVE,
                sort = MediaDownloadSort.NAME,
            ),
            loading = false,
        )

        assertEquals(false, state.loading)
        assertEquals(listOf("paused", "running"), state.tasks.map { it.id })
        assertEquals(4, state.totalTaskCount)
        assertEquals(2, state.activeTaskCount)
        assertEquals(1, state.completedTaskCount)
        assertEquals(1, state.failedTaskCount)
        assertEquals(2, state.recoverableTaskCount)
        assertEquals(200L, state.storageUsedBytes)
    }

    @Test
    fun reducerSortsByDownloadedSizeDescending() {
        val tasks = listOf(
            task("small", "Small", MediaDownloadState.PENDING, downloaded = 5L, createdAt = 1L),
            task("large", "Large", MediaDownloadState.PENDING, downloaded = 500L, createdAt = 2L),
            task("medium", "Medium", MediaDownloadState.PENDING, downloaded = 50L, createdAt = 3L),
        )

        val state = buildMediaDownloadsUiState(
            allTasks = tasks,
            currentState = MediaDownloadsUiState(sort = MediaDownloadSort.SIZE),
            loading = false,
        )

        assertEquals(listOf("large", "medium", "small"), state.tasks.map { it.id })
    }

    private fun task(
        id: String,
        title: String,
        state: MediaDownloadState,
        downloaded: Long,
        createdAt: Long,
    ) = MediaDownloadTaskUi(
        id = id,
        bookTitle = title,
        state = state,
        errorMessage = if (state == MediaDownloadState.FAILED) "network" else null,
        createdAt = createdAt,
        items = persistentListOf(
            MediaDownloadItemUi(
                id = "${id}_item",
                taskId = id,
                title = "$title episode",
                state = state,
                bytesDownloaded = downloaded,
                totalBytes = downloaded * 2,
                localPath = if (state == MediaDownloadState.COMPLETED) "/tmp/$id.mp4" else "",
                mimeType = "video/mp4",
                errorMessage = if (state == MediaDownloadState.FAILED) "network" else null,
                sortOrder = downloaded.toInt(),
            )
        ),
    )
}
