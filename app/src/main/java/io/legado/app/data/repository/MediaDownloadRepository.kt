package io.legado.app.data.repository

import io.legado.app.data.dao.MediaDownloadDao
import io.legado.app.data.entities.MediaDownloadItemEntity
import io.legado.app.data.entities.MediaDownloadTaskEntity
import io.legado.app.domain.gateway.MediaDownloadGateway
import io.legado.app.domain.model.MediaDownloadItem
import io.legado.app.domain.model.MediaDownloadRequest
import io.legado.app.domain.model.MediaDownloadState
import io.legado.app.domain.model.MediaDownloadTask
import io.legado.app.domain.model.MediaProtocol
import io.legado.app.domain.model.toPersistentMediaHeaders
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

class MediaDownloadRepository(
    private val dao: MediaDownloadDao,
) : MediaDownloadGateway {

    private val claimMutex = Mutex()

    override fun observeTasks(): Flow<List<MediaDownloadTask>> = combine(
        dao.observeTasks(),
        dao.observeItems(),
    ) { tasks, items ->
        val grouped = items.groupBy(MediaDownloadItemEntity::taskId)
        tasks.map { task ->
            MediaDownloadTask(
                id = task.id,
                bookUrl = task.bookUrl,
                bookTitle = task.bookTitle,
                coverUrl = task.coverUrl,
                state = task.status.toDownloadState(),
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
                errorMessage = task.errorMessage,
                items = grouped[task.id].orEmpty().map { it.toDomain() },
            )
        }
    }

    override suspend fun enqueue(request: MediaDownloadRequest): String {
        val now = System.currentTimeMillis()
        val taskId = stableId(request.bookUrl)
        dao.insertTask(
            MediaDownloadTaskEntity(
                id = taskId,
                bookUrl = request.bookUrl,
                bookTitle = request.bookTitle,
                coverUrl = request.coverUrl,
                status = MediaDownloadState.PENDING.name,
                createdAt = now,
                updatedAt = now,
                errorMessage = null,
            )
        )
        dao.refreshTask(
            taskId = taskId,
            title = request.bookTitle,
            coverUrl = request.coverUrl,
            status = MediaDownloadState.PENDING.name,
            now = now,
        )
        val itemId = stableId("$taskId:${request.chapterIndex}:${request.variant.id}")
        dao.insertItem(
            MediaDownloadItemEntity(
                id = itemId,
                taskId = taskId,
                bookUrl = request.bookUrl,
                chapterIndex = request.chapterIndex,
                episodeTitle = request.episodeTitle,
                variantId = request.variant.id,
                sourceUri = request.variant.uri,
                mimeType = request.variant.mimeType,
                headersJson = GSON.toJson(request.variant.headers.toPersistentMediaHeaders()),
                protocol = request.variant.protocol.name,
                expiresAt = request.variant.expiresAt,
                responseEtag = null,
                responseLastModified = null,
                responseContentLength = 0L,
                status = MediaDownloadState.PENDING.name,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                segmentIndex = 0,
                tempPath = "",
                localPath = "",
                checksum = "",
                errorMessage = null,
                retryCount = 0,
                sortOrder = dao.nextSortOrder(),
                updatedAt = now,
            )
        )
        return taskId
    }

    override suspend fun pause(taskId: String) {
        val now = System.currentTimeMillis()
        dao.updateTaskItems(taskId, listOf("PENDING", "RUNNING"), "PAUSED", now)
        dao.updateTaskState(taskId, "PAUSED", null, now)
    }

    override suspend fun resume(taskId: String) {
        val now = System.currentTimeMillis()
        dao.updateTaskItems(taskId, listOf("PAUSED"), "PENDING", now)
        dao.updateTaskState(taskId, "PENDING", null, now)
    }

    override suspend fun retry(taskId: String) {
        val now = System.currentTimeMillis()
        dao.updateTaskItems(taskId, listOf("FAILED"), "PENDING", now)
        dao.updateTaskState(taskId, "PENDING", null, now)
    }

    override suspend fun cancel(taskId: String) {
        val now = System.currentTimeMillis()
        dao.updateTaskItems(taskId, listOf("PENDING", "RUNNING", "PAUSED", "FAILED"), "CANCELED", now)
        dao.updateTaskState(taskId, "CANCELED", null, now)
        dao.getItems(taskId).forEach { item ->
            deleteScratchFiles(item)
            File(item.tempPath).takeIf(File::isFile)?.delete()
        }
    }

    override suspend fun delete(taskId: String) {
        dao.getItems(taskId).forEach { item ->
            deleteScratchFiles(item)
            File(item.tempPath).takeIf(File::isFile)?.delete()
            File(item.localPath).takeIf(File::isFile)?.delete()
        }
        dao.deleteTask(taskId)
    }

    override suspend fun deleteItem(itemId: String) {
        val item = dao.getItem(itemId) ?: return
        deleteScratchFiles(item)
        File(item.tempPath).takeIf(File::isFile)?.delete()
        File(item.localPath).takeIf(File::isFile)?.delete()
        dao.deleteItem(itemId)
        if (dao.itemCount(item.taskId) == 0) {
            dao.deleteTask(item.taskId)
        } else {
            dao.refreshParentState(item.taskId, System.currentTimeMillis())
        }
    }

    override suspend fun pauseActive() {
        val now = System.currentTimeMillis()
        val activeStates = listOf(MediaDownloadState.PENDING.name, MediaDownloadState.RUNNING.name)
        dao.updateItemsByState(activeStates, MediaDownloadState.PAUSED.name, now)
        dao.updateTasksByState(activeStates, MediaDownloadState.PAUSED.name, now)
    }

    override suspend fun resumeRecoverable() {
        val now = System.currentTimeMillis()
        val recoverableStates = listOf(MediaDownloadState.PAUSED.name, MediaDownloadState.FAILED.name)
        dao.updateItemsByState(recoverableStates, MediaDownloadState.PENDING.name, now)
        dao.updateTasksByState(recoverableStates, MediaDownloadState.PENDING.name, now)
    }

    override suspend fun cancelActive() {
        val activeStates = listOf(
            MediaDownloadState.PENDING.name,
            MediaDownloadState.RUNNING.name,
            MediaDownloadState.PAUSED.name,
            MediaDownloadState.FAILED.name,
        )
        dao.getItemsByState(activeStates).forEach { item ->
            deleteScratchFiles(item)
            File(item.tempPath).takeIf(File::isFile)?.delete()
        }
        val now = System.currentTimeMillis()
        dao.updateItemsByState(activeStates, MediaDownloadState.CANCELED.name, now)
        dao.updateTasksByState(activeStates, MediaDownloadState.CANCELED.name, now)
    }

    override suspend fun reconcileAfterProcessStart() {
        val runningItems = dao.getItemsByState(listOf(MediaDownloadState.RUNNING.name))
        runningItems.forEach(::deleteScratchFiles)
        if (runningItems.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.updateItemsByState(
            listOf(MediaDownloadState.RUNNING.name),
            MediaDownloadState.PENDING.name,
            now,
        )
        dao.updateTasksByState(
            listOf(MediaDownloadState.RUNNING.name),
            MediaDownloadState.PENDING.name,
            now,
        )
    }

    override suspend fun reorder(itemId: String, sortOrder: Int) {
        dao.reorder(itemId, sortOrder.coerceAtLeast(0), System.currentTimeMillis())
    }

    override suspend fun claimNext(): MediaDownloadItem? = claimMutex.withLock {
        val item = dao.nextPending() ?: return@withLock null
        val now = System.currentTimeMillis()
        dao.updateItemState(
            item.id,
            MediaDownloadState.RUNNING.name,
            null,
            item.retryCount,
            now,
        )
        dao.updateTaskState(item.taskId, MediaDownloadState.RUNNING.name, null, now)
        item.copy(status = MediaDownloadState.RUNNING.name).toDomain()
    }

    override suspend fun getItem(itemId: String): MediaDownloadItem? = dao.getItem(itemId)?.toDomain()

    override suspend fun updateProgress(
        itemId: String,
        downloaded: Long,
        total: Long,
        segmentIndex: Int,
        tempPath: String,
    ) {
        dao.updateProgress(itemId, downloaded, total, segmentIndex, tempPath, System.currentTimeMillis())
    }

    override suspend fun updateSource(
        itemId: String,
        sourceUri: String,
        headers: Map<String, String>,
        expiresAt: Long?,
    ) {
        dao.updateSource(
            itemId,
            sourceUri,
            GSON.toJson(headers.toPersistentMediaHeaders()),
            expiresAt,
            System.currentTimeMillis(),
        )
    }

    override suspend fun updateTransferIdentity(
        itemId: String,
        etag: String?,
        lastModified: String?,
        contentLength: Long,
    ) {
        dao.updateTransferIdentity(
            itemId,
            etag,
            lastModified,
            contentLength.coerceAtLeast(0L),
            System.currentTimeMillis(),
        )
    }

    override suspend fun complete(
        itemId: String,
        total: Long,
        localPath: String,
        checksum: String,
        mimeType: String,
    ) {
        val item = dao.getItem(itemId) ?: return
        dao.completeItem(itemId, total, localPath, checksum, mimeType, System.currentTimeMillis())
        dao.refreshParentState(item.taskId, System.currentTimeMillis())
    }

    override suspend fun fail(itemId: String, message: String) {
        val item = dao.getItem(itemId) ?: return
        dao.updateItemState(
            itemId,
            MediaDownloadState.FAILED.name,
            message.take(500),
            item.retryCount + 1,
            System.currentTimeMillis(),
        )
        dao.refreshParentState(item.taskId, System.currentTimeMillis())
    }

    override suspend fun setItemState(itemId: String, state: MediaDownloadState) {
        val item = dao.getItem(itemId) ?: return
        dao.updateItemState(itemId, state.name, null, item.retryCount, System.currentTimeMillis())
    }

    private fun MediaDownloadItemEntity.toDomain() = MediaDownloadItem(
        id = id,
        taskId = taskId,
        bookUrl = bookUrl,
        chapterIndex = chapterIndex,
        episodeTitle = episodeTitle,
        variantId = variantId,
        sourceUri = sourceUri,
        mimeType = mimeType,
        headers = GSON.fromJsonObject<HashMap<String, String>>(headersJson).getOrNull().orEmpty(),
        protocol = runCatching { MediaProtocol.valueOf(protocol) }.getOrDefault(MediaProtocol.UNKNOWN),
        expiresAt = expiresAt,
        responseEtag = responseEtag,
        responseLastModified = responseLastModified,
        responseContentLength = responseContentLength,
        state = status.toDownloadState(),
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        segmentIndex = segmentIndex,
        tempPath = tempPath,
        localPath = localPath,
        checksum = checksum,
        errorMessage = errorMessage,
        retryCount = retryCount,
        sortOrder = sortOrder,
    )

    private fun String.toDownloadState(): MediaDownloadState =
        runCatching { MediaDownloadState.valueOf(this) }.getOrDefault(MediaDownloadState.FAILED)

    private fun deleteScratchFiles(item: MediaDownloadItemEntity) {
        val temp = item.tempPath.takeIf(String::isNotBlank)?.let(::File) ?: return
        val parent = temp.parentFile ?: return
        listOf(
            File(parent, "${temp.name}.segment"),
            File(parent, "${temp.name}.dash-segment"),
        ).forEach { scratch -> scratch.takeIf(File::isFile)?.delete() }
    }

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it) }
}
