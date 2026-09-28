package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_book_metadata")
data class RemoteBookMetadata(
    @PrimaryKey
    val path: String,
    val title: String,
    val author: String? = null,
    val intro: String? = null,
    val format: String = "",
    val coverUrl: String? = null,
    val fileSize: Long = 0L,
    val lastModify: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis()
)
