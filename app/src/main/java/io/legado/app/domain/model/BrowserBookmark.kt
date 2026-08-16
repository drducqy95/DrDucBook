package io.legado.app.domain.model

data class BrowserBookmark(
    val id: String,
    val title: String,
    val url: String,
    val folder: String = DEFAULT_FOLDER,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_FOLDER = "General"
    }
}

data class SourceBookmarkPreference(
    val sourceKey: SourceKey,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
)
