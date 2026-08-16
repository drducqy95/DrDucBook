package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_download_tasks",
    indices = [
        Index("bookUrl"),
        Index("status"),
        Index("createdAt"),
    ],
)
data class MediaDownloadTaskEntity(
    @PrimaryKey val id: String,
    val bookUrl: String,
    val bookTitle: String,
    val coverUrl: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String?,
)
