package io.legado.app.domain.model

enum class MediaContentKind {
    VIDEO,
    AUDIO,
    UNKNOWN,
}

enum class MediaProtocol {
    DIRECT,
    HLS,
    DASH,
    IFRAME,
    UNKNOWN,
}

data class ResolvedMediaVariant(
    val id: String,
    val title: String,
    val uri: String,
    val contentKind: MediaContentKind,
    val protocol: MediaProtocol,
    val mimeType: String,
    val headers: Map<String, String>,
    val referer: String,
    val expiresAt: Long?,
    val downloadSupported: Boolean,
    val externalPlayerRequired: Boolean,
    val durationMs: Long? = null,
    val drmUnsupported: Boolean = false,
    val downloadFileName: String? = null,
)

data class ResolvedSubtitleTrack(
    val id: String,
    val label: String,
    val language: String,
    val uri: String,
    val mimeType: String,
    val headers: Map<String, String>,
    val isDefault: Boolean,
)

data class ResolvedAudioTrack(
    val id: String,
    val label: String,
    val language: String,
    val uri: String,
    val mimeType: String,
    val headers: Map<String, String>,
    val isDefault: Boolean,
)

data class ResolvedMedia(
    val sourceId: String,
    val contentId: String,
    val title: String,
    val variants: List<ResolvedMediaVariant>,
    val subtitles: List<ResolvedSubtitleTrack>,
    val audioTracks: List<ResolvedAudioTrack>,
    val resolvedAt: Long,
) {
    init {
        require(variants.isNotEmpty()) { "Resolved media requires at least one variant" }
    }
}

data class ResolvedBookMedia(
    val bookUrl: String,
    val bookTitle: String,
    val coverUrl: String?,
    val chapterIndex: Int,
    val chapterCount: Int,
    val previousChapterIndex: Int?,
    val nextChapterIndex: Int?,
    val isVideo: Boolean,
    val clipStartMs: Long? = null,
    val clipEndMs: Long? = null,
    val chapters: List<ResolvedMediaChapter> = emptyList(),
    val media: ResolvedMedia,
)

data class ResolvedMediaChapter(
    val index: Int,
    val title: String,
    val isOffline: Boolean,
)

data class MediaPlaybackRequest(
    val bookUrl: String,
    val bookTitle: String,
    val chapterIndex: Int,
    val episodeTitle: String,
    val coverUrl: String?,
    val variant: ResolvedMediaVariant,
    val subtitles: List<ResolvedSubtitleTrack>,
    val audioTracks: List<ResolvedAudioTrack> = emptyList(),
    val playWhenReady: Boolean,
    val startPositionMs: Long = 0L,
    val resumeStoredPosition: Boolean = true,
    val clipStartMs: Long? = null,
    val clipEndMs: Long? = null,
)

data class MediaPlaybackTrack(
    val id: String,
    val label: String,
    val language: String,
)

data class MediaPlaybackSnapshot(
    val bookUrl: String = "",
    val chapterIndex: Int = -1,
    val variantId: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val subtitleTracks: List<MediaPlaybackTrack> = emptyList(),
    val audioTracks: List<MediaPlaybackTrack> = emptyList(),
    val selectedSubtitleId: String? = null,
    val selectedAudioTrackId: String? = null,
    val errorMessage: String? = null,
    val ended: Boolean = false,
)
