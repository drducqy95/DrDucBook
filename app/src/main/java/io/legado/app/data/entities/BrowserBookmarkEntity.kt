package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.legado.app.domain.model.BrowserBookmark

@Entity(
    tableName = "browser_bookmarks",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["folder", "sortOrder"]),
        Index(value = ["updatedAt"]),
    ],
)
data class BrowserBookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val folder: String = BrowserBookmark.DEFAULT_FOLDER,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun toDomain(): BrowserBookmark = BrowserBookmark(
        id = id,
        title = title,
        url = url,
        folder = folder,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun BrowserBookmark.toEntity(): BrowserBookmarkEntity = BrowserBookmarkEntity(
    id = id,
    title = title,
    url = url,
    folder = folder,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
