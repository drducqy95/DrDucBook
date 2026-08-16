package io.legado.app.domain.model

data class AudiobookTrackCandidate(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val durationMs: Long,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val mimeType: String,
    val selected: Boolean = true,
)

data class AudiobookImportPreview(
    val title: String,
    val author: String,
    val tracks: List<AudiobookTrackCandidate>,
)

data class AudiobookCreateRequest(
    val title: String,
    val author: String,
    val tracks: List<AudiobookTrackCandidate>,
)
