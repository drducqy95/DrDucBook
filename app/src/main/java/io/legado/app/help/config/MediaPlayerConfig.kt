package io.legado.app.help.config

import android.graphics.Color
import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

data class MediaPlayerConfigSnapshot(
    val autoPlay: Boolean,
    val autoNext: Boolean,
    val resumePosition: Boolean,
    val seekForwardSeconds: Int,
    val seekBackwardSeconds: Int,
    val keepScreenOn: Boolean,
    val brightnessAuto: Boolean,
    val muted: Boolean,
    val defaultVolume: Float,
    val showSubtitles: Boolean,
    val subtitleTextColor: Int,
    val subtitleBackgroundColor: Int,
    val subtitleBackgroundOpacity: Float,
    val subtitleFontScale: Float,
    val subtitleBottomPaddingDp: Int,
    val subtitleFontWeight: Int,
)

object MediaPlayerConfig {
    private val seekOptions = setOf(5, 10, 15, 30)

    var autoPlay by prefDelegate(PreferKey.mediaPlayerAutoPlay, true)
    var autoNext by prefDelegate(PreferKey.mediaPlayerAutoNext, true)
    var resumePosition by prefDelegate(PreferKey.mediaPlayerResumePosition, true)
    var seekForwardSeconds by prefDelegate(PreferKey.mediaPlayerSeekForwardSeconds, 10)
    var seekBackwardSeconds by prefDelegate(PreferKey.mediaPlayerSeekBackwardSeconds, 10)
    var keepScreenOn by prefDelegate(PreferKey.mediaPlayerKeepScreenOn, true)
    var brightnessAuto by prefDelegate(PreferKey.mediaPlayerBrightnessAuto, true)
    var muted by prefDelegate(PreferKey.mediaPlayerMuted, false)
    var defaultVolume by prefDelegate(PreferKey.mediaPlayerDefaultVolume, 0.33f)
    var showSubtitles by prefDelegate(PreferKey.mediaPlayerShowSubtitles, true)
    var subtitleTextColor by prefDelegate(PreferKey.mediaPlayerSubtitleTextColor, Color.WHITE)
    var subtitleBackgroundColor by prefDelegate(PreferKey.mediaPlayerSubtitleBackgroundColor, Color.BLACK)
    var subtitleBackgroundOpacity by prefDelegate(PreferKey.mediaPlayerSubtitleBackgroundOpacity, 0.35f)
    var subtitleFontScale by prefDelegate(PreferKey.mediaPlayerSubtitleFontScale, 1f)
    var subtitleBottomPaddingDp by prefDelegate(PreferKey.mediaPlayerSubtitleBottomPaddingDp, 0)
    var subtitleFontWeight by prefDelegate(PreferKey.mediaPlayerSubtitleFontWeight, SubtitleFontWeightMedium)

    fun normalizeSeekSeconds(value: Int): Int {
        return seekOptions.minBy { kotlin.math.abs(it - value) }
    }

    fun snapshot() = MediaPlayerConfigSnapshot(
        autoPlay = autoPlay,
        autoNext = autoNext,
        resumePosition = resumePosition,
        seekForwardSeconds = normalizeSeekSeconds(seekForwardSeconds),
        seekBackwardSeconds = normalizeSeekSeconds(seekBackwardSeconds),
        keepScreenOn = keepScreenOn,
        brightnessAuto = brightnessAuto,
        muted = muted,
        defaultVolume = defaultVolume.coerceIn(0f, 1f),
        showSubtitles = showSubtitles,
        subtitleTextColor = subtitleTextColor,
        subtitleBackgroundColor = subtitleBackgroundColor,
        subtitleBackgroundOpacity = subtitleBackgroundOpacity.coerceIn(0f, 1f),
        subtitleFontScale = subtitleFontScale.coerceIn(0.7f, 1.6f),
        subtitleBottomPaddingDp = subtitleBottomPaddingDp.coerceIn(0, 120),
        subtitleFontWeight = subtitleFontWeight.coerceIn(
            SubtitleFontWeightNormal,
            SubtitleFontWeightBold,
        ),
    )

    const val SubtitleFontWeightNormal = 0
    const val SubtitleFontWeightMedium = 1
    const val SubtitleFontWeightBold = 2
}
