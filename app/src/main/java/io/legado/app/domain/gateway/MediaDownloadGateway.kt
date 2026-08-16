package io.legado.app.domain.gateway

import io.legado.app.domain.model.MediaDownloadItem
import io.legado.app.domain.model.MediaDownloadRequest
import io.legado.app.domain.model.MediaDownloadState
import io.legado.app.domain.model.MediaDownloadTask
import kotlinx.coroutines.flow.Flow

interface MediaDownloadGateway {
    fun observeTasks(): Flow<List<MediaDownloadTask>>

    suspend fun enqueue(request: MediaDownloadRequest): String

    suspend fun pause(taskId: String)

    suspend fun resume(taskId: String)

    suspend fun retry(taskId: String)

    suspend fun cancel(taskId: String)

    suspend fun delete(taskId: String)

    suspend fun deleteItem(itemId: String)

    suspend fun pauseActive()

    suspend fun resumeRecoverable()

    suspend fun cancelActive()

    suspend fun reconcileAfterProcessStart()

    suspend fun reorder(itemId: String, sortOrder: Int)

    suspend fun claimNext(): MediaDownloadItem?

    suspend fun getItem(itemId: String): MediaDownloadItem?

    suspend fun updateProgress(
        itemId: String,
        downloaded: Long,
        total: Long,
        segmentIndex: Int,
        tempPath: String,
    )

    suspend fun updateSource(itemId: String, sourceUri: String, headers: Map<String, String>, expiresAt: Long?)

    suspend fun updateTransferIdentity(
        itemId: String,
        etag: String?,
        lastModified: String?,
        contentLength: Long,
    )

    suspend fun complete(itemId: String, total: Long, localPath: String, checksum: String, mimeType: String)

    suspend fun fail(itemId: String, message: String)

    suspend fun setItemState(itemId: String, state: MediaDownloadState)
}
