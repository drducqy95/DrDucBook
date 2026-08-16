package io.legado.app.domain.model

enum class MediaDownloadState {
    PENDING,
    RUNNING,
    PAUSED,
    FAILED,
    COMPLETED,
    CANCELED,
}

data class MediaDownloadRequest(
    val bookUrl: String,
    val bookTitle: String,
    val coverUrl: String?,
    val chapterIndex: Int,
    val episodeTitle: String,
    val variant: ResolvedMediaVariant,
)

data class MediaDownloadItem(
    val id: String,
    val taskId: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val episodeTitle: String,
    val variantId: String,
    val sourceUri: String,
    val mimeType: String,
    val headers: Map<String, String>,
    val protocol: MediaProtocol,
    val expiresAt: Long?,
    val responseEtag: String?,
    val responseLastModified: String?,
    val responseContentLength: Long,
    val state: MediaDownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val segmentIndex: Int,
    val tempPath: String,
    val localPath: String,
    val checksum: String,
    val errorMessage: String?,
    val retryCount: Int,
    val sortOrder: Int,
)

data class MediaDownloadTask(
    val id: String,
    val bookUrl: String,
    val bookTitle: String,
    val coverUrl: String?,
    val state: MediaDownloadState,
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String?,
    val items: List<MediaDownloadItem>,
)
