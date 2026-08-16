package io.legado.app.ui.media.player

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.MediaProtocol
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class MediaPlayerUiState(
    val bookUrl: String = "",
    val bookTitle: String = "",
    val episodeTitle: String = "",
    val coverUrl: String? = null,
    val isVideo: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val variants: ImmutableList<MediaVariantUi> = persistentListOf(),
    val selectedVariantId: String? = null,
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val subtitleTracks: ImmutableList<MediaTrackUi> = persistentListOf(),
    val audioTracks: ImmutableList<MediaTrackUi> = persistentListOf(),
    val selectedSubtitleId: String? = null,
    val selectedAudioTrackId: String? = null,
    val chapters: ImmutableList<MediaChapterUi> = persistentListOf(),
    val showChapterSheet: Boolean = false,
    val controlsLocked: Boolean = false,
    val isFullscreen: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val autoPlay: Boolean = true,
    val autoNext: Boolean = true,
    val resumePosition: Boolean = true,
    val seekForwardSeconds: Int = 10,
    val seekBackwardSeconds: Int = 10,
    val keepScreenOn: Boolean = true,
    val brightnessAuto: Boolean = true,
    val muted: Boolean = false,
    val brightness: Float = 0.5f,
    val volume: Float = 0.5f,
    val showSubtitles: Boolean = true,
    val subtitleTextColor: Int = android.graphics.Color.WHITE,
    val subtitleBackgroundColor: Int = android.graphics.Color.BLACK,
    val subtitleBackgroundOpacity: Float = 0.35f,
    val subtitleFontScale: Float = 1f,
    val subtitleBottomPaddingDp: Int = 0,
    val subtitleFontWeight: Int = 1,
    val gestureIndicator: MediaGestureIndicator? = null,
)

@Stable
data class MediaTrackUi(val id: String, val label: String, val language: String)

@Stable
data class MediaChapterUi(val index: Int, val title: String, val isOffline: Boolean)

enum class MediaGestureIndicator { BRIGHTNESS, VOLUME, SPEED }

@Stable
data class MediaVariantUi(
    val id: String,
    val title: String,
    val contentKind: MediaContentKind,
    val protocol: MediaProtocol,
    val mimeType: String,
    val downloadSupported: Boolean,
    val externalPlayerRequired: Boolean,
)

sealed interface MediaPlayerIntent {
    data class Initialize(
        val bookUrl: String,
        val chapterIndex: Int?,
    ) : MediaPlayerIntent

    data object Retry : MediaPlayerIntent
    data object Back : MediaPlayerIntent
    data object TogglePlayback : MediaPlayerIntent
    data object Previous : MediaPlayerIntent
    data object Next : MediaPlayerIntent
    data object PlaybackEnded : MediaPlayerIntent
    data object EnterPictureInPicture : MediaPlayerIntent
    data object DownloadCurrent : MediaPlayerIntent
    data object OpenDownloads : MediaPlayerIntent
    data class SelectVariant(val id: String) : MediaPlayerIntent
    data class SeekTo(val positionMs: Long) : MediaPlayerIntent
    data class SeekBy(val deltaMs: Long) : MediaPlayerIntent
    data class SetPlaybackSpeed(val speed: Float) : MediaPlayerIntent
    data class SelectSubtitle(val id: String?) : MediaPlayerIntent
    data class SelectAudioTrack(val id: String?) : MediaPlayerIntent
    data object ShowChapters : MediaPlayerIntent
    data object HideChapters : MediaPlayerIntent
    data object ShowSettings : MediaPlayerIntent
    data object HideSettings : MediaPlayerIntent
    data class SelectChapter(val index: Int) : MediaPlayerIntent
    data object ToggleControlsLock : MediaPlayerIntent
    data object ToggleFullscreen : MediaPlayerIntent
    data class SetAutoPlay(val enabled: Boolean) : MediaPlayerIntent
    data class SetAutoNext(val enabled: Boolean) : MediaPlayerIntent
    data class SetResumePosition(val enabled: Boolean) : MediaPlayerIntent
    data class SetSeekForwardSeconds(val seconds: Int) : MediaPlayerIntent
    data class SetSeekBackwardSeconds(val seconds: Int) : MediaPlayerIntent
    data class SetKeepScreenOn(val enabled: Boolean) : MediaPlayerIntent
    data class SetBrightnessAuto(val enabled: Boolean) : MediaPlayerIntent
    data class SetMuted(val muted: Boolean) : MediaPlayerIntent
    data class SetDefaultVolume(val volume: Float) : MediaPlayerIntent
    data class SetShowSubtitles(val enabled: Boolean) : MediaPlayerIntent
    data class SetSubtitleTextColor(val color: Int) : MediaPlayerIntent
    data class SetSubtitleBackgroundColor(val color: Int) : MediaPlayerIntent
    data class SetSubtitleBackgroundOpacity(val opacity: Float) : MediaPlayerIntent
    data class SetSubtitleFontScale(val scale: Float) : MediaPlayerIntent
    data class SetSubtitleBottomPadding(val paddingDp: Int) : MediaPlayerIntent
    data class SetSubtitleFontWeight(val weight: Int) : MediaPlayerIntent
    data class AdjustBrightness(val delta: Float) : MediaPlayerIntent
    data class AdjustVolume(val delta: Float) : MediaPlayerIntent
    data object BeginSpeedBoost : MediaPlayerIntent
    data object EndSpeedBoost : MediaPlayerIntent
    data object ClearGestureIndicator : MediaPlayerIntent
    data class PlayerSnapshot(
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val positionMs: Long,
        val durationMs: Long,
    ) : MediaPlayerIntent

    data class PlayerFailed(val message: String) : MediaPlayerIntent
}

sealed interface MediaPlayerEffect {
    data object Exit : MediaPlayerEffect
    data object EnterPictureInPicture : MediaPlayerEffect
    data object StartDownloadService : MediaPlayerEffect
    data object OpenDownloads : MediaPlayerEffect
    data class ShowMessage(val message: String) : MediaPlayerEffect
    data class OpenExternal(val url: String) : MediaPlayerEffect
    data class SetFullscreen(val enabled: Boolean) : MediaPlayerEffect
    data object SetBrightnessAuto : MediaPlayerEffect
    data class SetBrightness(val value: Float) : MediaPlayerEffect
    data class SetVolume(val value: Float) : MediaPlayerEffect
}
