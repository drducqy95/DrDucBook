package io.legado.app.ui.media.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.media.AudioManager
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.help.media.MediaPlaybackConnection
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.service.MediaDownloadService
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerRouteScreen(
    bookUrl: String,
    chapterIndex: Int?,
    viewModel: MediaPlayerViewModel,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    playbackConnection: MediaPlaybackConnection = koinInject(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by playbackConnection.player.collectAsStateWithLifecycle()
    val activity = context.findActivity()

    DisposableEffect(playbackConnection) {
        playbackConnection.connect()
        onDispose { playbackConnection.disconnect() }
    }

    DisposableEffect(activity, state.isVideo, state.isPlaying) {
        val componentActivity = activity as? ComponentActivity
        val listener = Runnable {
            if (state.isVideo && state.isPlaying) {
                componentActivity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
        componentActivity?.addOnUserLeaveHintListener(listener)
        onDispose { componentActivity?.removeOnUserLeaveHintListener(listener) }
    }

    LaunchedEffect(bookUrl, chapterIndex, viewModel) {
        viewModel.onIntent(MediaPlayerIntent.Initialize(bookUrl, chapterIndex))
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                MediaPlayerEffect.Exit -> onBack()
                MediaPlayerEffect.EnterPictureInPicture -> {
                    context.findActivity()?.enterPictureInPictureMode(
                        PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build()
                    )
                }
                MediaPlayerEffect.StartDownloadService -> MediaDownloadService.start(context)
                MediaPlayerEffect.OpenDownloads -> onOpenDownloads()
                is MediaPlayerEffect.ShowMessage -> context.toastOnUi(effect.message)
                is MediaPlayerEffect.OpenExternal -> onOpenExternal(effect.url)
                is MediaPlayerEffect.SetFullscreen -> activity?.let { host ->
                    WindowCompat.setDecorFitsSystemWindows(host.window, !effect.enabled)
                    WindowCompat.getInsetsController(host.window, host.window.decorView).run {
                        if (effect.enabled) {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior =
                                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                    host.requestedOrientation = if (effect.enabled) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
                is MediaPlayerEffect.SetBrightness -> activity?.window?.let { window ->
                    window.attributes = window.attributes.apply {
                        screenBrightness = effect.value.coerceIn(0.02f, 1f)
                    }
                }
                MediaPlayerEffect.SetBrightnessAuto -> activity?.window?.let { window ->
                    window.attributes = window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
                is MediaPlayerEffect.SetVolume -> {
                    val manager = context.getSystemService(AudioManager::class.java)
                    val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    manager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        (maximum * effect.value).toInt().coerceIn(0, maximum),
                        0,
                    )
                }
            }
        }
    }

    LaunchedEffect(activity, state.brightnessAuto, state.brightness) {
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply {
                screenBrightness = if (state.brightnessAuto) {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                } else {
                    state.brightness.coerceIn(0.02f, 1f)
                }
            }
        }
    }

    MediaPlayerScreen(
        state = state,
        onIntent = viewModel::onIntent,
        mediaSurface = {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        this.player = player
                    }
                },
                update = { playerView ->
                    playerView.player = player
                    playerView.keepScreenOn = state.isVideo && (state.isPlaying || state.keepScreenOn)
                    playerView.applySubtitleStyle(state)
                },
            )
        },
    )
}

@OptIn(UnstableApi::class)
private fun PlayerView.applySubtitleStyle(state: MediaPlayerUiState) {
    val subtitleView = this.subtitleView ?: return
    subtitleView.visibility = if (state.showSubtitles) View.VISIBLE else View.GONE
    val style = CaptionStyleCompat(
        state.subtitleTextColor,
        colorWithOpacity(state.subtitleBackgroundColor, state.subtitleBackgroundOpacity),
        android.graphics.Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        android.graphics.Color.BLACK,
        when (state.subtitleFontWeight) {
            2 -> subtitleTypeface().let { Typeface.create(it, Typeface.BOLD) }
            else -> subtitleTypeface()
        },
    )
    subtitleView.setStyle(style)
    subtitleView.setFractionalTextSize((0.0533f * state.subtitleFontScale).coerceIn(0.035f, 0.09f))
    val bottomPadding = if (height > 0) {
        (state.subtitleBottomPaddingDp * resources.displayMetrics.density / height).coerceIn(0f, 0.45f)
    } else {
        0.08f
    }
    subtitleView.setBottomPaddingFraction(bottomPadding)
}

private fun PlayerView.subtitleTypeface(): Typeface = runCatching {
    ThemeConfig.appFontPath
        ?.takeIf { it.isNotBlank() }
        ?.let(Typeface::createFromFile)
        ?: Typeface.createFromAsset(context.assets, "font/vietnamese/BeVietnamPro-Regular.ttf")
}.getOrDefault(Typeface.DEFAULT)

private fun colorWithOpacity(color: Int, opacity: Float): Int {
    val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
    return (color and 0x00FFFFFF) or (alpha shl 24)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
