package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.MediaDownloadItemEntity
import io.legado.app.data.entities.MediaDownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDownloadDao {
    @Query("SELECT * FROM media_download_tasks ORDER BY updatedAt DESC")
    fun observeTasks(): Flow<List<MediaDownloadTaskEntity>>

    @Query("SELECT * FROM media_download_items ORDER BY sortOrder, chapterIndex")
    fun observeItems(): Flow<List<MediaDownloadItemEntity>>

    @Query("SELECT * FROM media_download_tasks ORDER BY updatedAt DESC")
    suspend fun getAllTasks(): List<MediaDownloadTaskEntity>

    @Query("SELECT * FROM media_download_items ORDER BY sortOrder, chapterIndex")
    suspend fun getAllItems(): List<MediaDownloadItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTask(task: MediaDownloadTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MediaDownloadItemEntity)

    @Query("UPDATE media_download_tasks SET bookTitle = :title, coverUrl = :coverUrl, status = :status, updatedAt = :now, errorMessage = NULL WHERE id = :taskId")
    suspend fun refreshTask(taskId: String, title: String, coverUrl: String?, status: String, now: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM media_download_items")
    suspend fun nextSortOrder(): Int

    @Query("SELECT * FROM media_download_items WHERE status = 'PENDING' ORDER BY sortOrder, updatedAt LIMIT 1")
    suspend fun nextPending(): MediaDownloadItemEntity?

    @Query("SELECT * FROM media_download_items WHERE id = :itemId LIMIT 1")
    suspend fun getItem(itemId: String): MediaDownloadItemEntity?

    @Query("SELECT * FROM media_download_items WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex AND status = 'COMPLETED' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getCompleted(bookUrl: String, chapterIndex: Int): MediaDownloadItemEntity?

    @Query("SELECT * FROM media_download_items WHERE bookUrl = :bookUrl AND status = 'COMPLETED'")
    suspend fun getCompletedForBook(bookUrl: String): List<MediaDownloadItemEntity>

    @Query("SELECT * FROM media_download_items WHERE taskId = :taskId")
    suspend fun getItems(taskId: String): List<MediaDownloadItemEntity>

    @Query("SELECT * FROM media_download_items WHERE status IN (:states)")
    suspend fun getItemsByState(states: List<String>): List<MediaDownloadItemEntity>

    @Query("UPDATE media_download_items SET status = :status, errorMessage = :error, retryCount = :retryCount, updatedAt = :now WHERE id = :itemId")
    suspend fun updateItemState(itemId: String, status: String, error: String?, retryCount: Int, now: Long)

    @Query("UPDATE media_download_items SET bytesDownloaded = :downloaded, totalBytes = :total, segmentIndex = :segmentIndex, tempPath = :tempPath, updatedAt = :now WHERE id = :itemId")
    suspend fun updateProgress(itemId: String, downloaded: Long, total: Long, segmentIndex: Int, tempPath: String, now: Long)

    @Query("UPDATE media_download_items SET status = 'COMPLETED', bytesDownloaded = :total, totalBytes = :total, localPath = :localPath, tempPath = '', checksum = :checksum, mimeType = :mimeType, errorMessage = NULL, updatedAt = :now WHERE id = :itemId")
    suspend fun completeItem(itemId: String, total: Long, localPath: String, checksum: String, mimeType: String, now: Long)

    @Query("UPDATE media_download_items SET sourceUri = :sourceUri, headersJson = :headersJson, expiresAt = :expiresAt, updatedAt = :now WHERE id = :itemId")
    suspend fun updateSource(itemId: String, sourceUri: String, headersJson: String, expiresAt: Long?, now: Long)

    @Query("UPDATE media_download_items SET responseEtag = :etag, responseLastModified = :lastModified, responseContentLength = :contentLength, updatedAt = :now WHERE id = :itemId")
    suspend fun updateTransferIdentity(
        itemId: String,
        etag: String?,
        lastModified: String?,
        contentLength: Long,
        now: Long,
    )

    @Query("UPDATE media_download_items SET status = :target, errorMessage = NULL, updatedAt = :now WHERE taskId = :taskId AND status IN (:fromStates)")
    suspend fun updateTaskItems(taskId: String, fromStates: List<String>, target: String, now: Long)

    @Query("UPDATE media_download_items SET status = :target, errorMessage = NULL, updatedAt = :now WHERE status IN (:fromStates)")
    suspend fun updateItemsByState(fromStates: List<String>, target: String, now: Long)

    @Query("UPDATE media_download_tasks SET status = :status, errorMessage = :error, updatedAt = :now WHERE id = :taskId")
    suspend fun updateTaskState(taskId: String, status: String, error: String?, now: Long)

    @Query("UPDATE media_download_tasks SET status = :target, errorMessage = NULL, updatedAt = :now WHERE status IN (:fromStates)")
    suspend fun updateTasksByState(fromStates: List<String>, target: String, now: Long)

    @Query("UPDATE media_download_items SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :itemId")
    suspend fun reorder(itemId: String, sortOrder: Int, now: Long)

    @Query("DELETE FROM media_download_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM media_download_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("SELECT COUNT(*) FROM media_download_items WHERE taskId = :taskId")
    suspend fun itemCount(taskId: String): Int

    @Query("SELECT COUNT(*) FROM media_download_items WHERE taskId = :taskId AND status NOT IN ('COMPLETED', 'CANCELED')")
    suspend fun remainingCount(taskId: String): Int

    @Query("SELECT COUNT(*) FROM media_download_items WHERE taskId = :taskId AND status = 'FAILED'")
    suspend fun failedCount(taskId: String): Int

    @Transaction
    suspend fun refreshParentState(taskId: String, now: Long) {
        val failed = failedCount(taskId)
        val remaining = remainingCount(taskId)
        when {
            failed > 0 -> updateTaskState(taskId, "FAILED", null, now)
            remaining == 0 -> updateTaskState(taskId, "COMPLETED", null, now)
            else -> updateTaskState(taskId, "RUNNING", null, now)
        }
    }
}
