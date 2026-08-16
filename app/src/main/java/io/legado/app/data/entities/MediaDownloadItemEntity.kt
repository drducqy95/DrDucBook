package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_download_items",
    foreignKeys = [
        ForeignKey(
            entity = MediaDownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("taskId"),
        Index("status"),
        Index(value = ["taskId", "chapterIndex", "variantId"], unique = true),
        Index("sortOrder"),
    ],
)
data class MediaDownloadItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val episodeTitle: String,
    val variantId: String,
    val sourceUri: String,
    val mimeType: String,
    val headersJson: String,
    val protocol: String,
    val expiresAt: Long?,
    val responseEtag: String?,
    val responseLastModified: String?,
    val responseContentLength: Long,
    val status: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val segmentIndex: Int,
    val tempPath: String,
    val localPath: String,
    val checksum: String,
    val errorMessage: String?,
    val retryCount: Int,
    val sortOrder: Int,
    val updatedAt: Long,
)
