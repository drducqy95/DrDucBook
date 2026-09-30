package io.legado.app.ui.media.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.Icon
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.help.media.MediaPlaybackConnection
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.service.MediaDownloadService
import io.legado.app.service.MediaPlaybackService
import com.drducbook.app.R
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

    val lifecycleOwner = LocalLifecycleOwner.current
    var isInPipMode by remember {
        mutableStateOf(activity?.isInPictureInPictureMode == true)
    }
    var pipRequestPending by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(pipListener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(pipListener)
        }
    }

    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        val listener = Runnable {
            val current = viewModel.uiState.value
            if (current.isVideo && current.isPlaying && current.autoEnterPipOnExit) {
                pipRequestPending = true
                runCatching {
                    componentActivity?.enterPictureInPictureMode(
                        buildPipParams(context, current.isPlaying, current.isVideo, false)
                    )
                }
            }
        }
        componentActivity?.addOnUserLeaveHintListener(listener)
        onDispose { componentActivity?.removeOnUserLeaveHintListener(listener) }
    }

    LaunchedEffect(activity, state.isVideo, state.isPlaying) {
        if (activity != null && state.isVideo) {
            runCatching {
                activity.setPictureInPictureParams(
                    buildPipParams(
                        context,
                        state.isPlaying,
                        state.isVideo,
                        state.autoEnterPipOnExit,
                    )
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner, activity, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val pipActive = activity?.isInPictureInPictureMode == true
                val current = viewModel.uiState.value
                if (current.isPlaying && !pipActive && !pipRequestPending) {
                    viewModel.onIntent(MediaPlayerIntent.PictureInPictureClosed)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(bookUrl, chapterIndex, viewModel) {
        viewModel.onIntent(MediaPlayerIntent.Initialize(bookUrl, chapterIndex))
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                MediaPlayerEffect.Exit -> onBack()
                MediaPlayerEffect.ExitAfterStop -> onBack()
                MediaPlayerEffect.EnterPictureInPicture -> {
                    pipRequestPending = true
                    val entered = runCatching {
                        activity?.enterPictureInPictureMode(
                            buildPipParams(context, state.isPlaying, state.isVideo, false)
                        ) == true
                    }.getOrDefault(false)
                    if (entered) {
                        pipRequestPending = false
                        viewModel.onIntent(MediaPlayerIntent.PictureInPictureEntered)
                    } else {
                        pipRequestPending = false
                        viewModel.onIntent(MediaPlayerIntent.PictureInPictureRequestFailed)
                    }
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
        isInPipMode = isInPipMode,
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
                        isClickable = false
                        isFocusable = false
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

    DisposableEffect(viewModel, activity, playbackConnection) {
        playbackConnection.connect()
        onDispose {
            if (activity?.isInPictureInPictureMode != true) {
                viewModel.onIntent(MediaPlayerIntent.PictureInPictureClosed)
            }
            playbackConnection.disconnect()
        }
    }
}

private fun buildPipParams(
    context: Context,
    isPlaying: Boolean,
    isVideo: Boolean,
    autoEnter: Boolean,
): PictureInPictureParams {
    val playPauseIntent = Intent(context, MediaPlaybackService::class.java).apply {
        action = if (isPlaying) MediaPlaybackService.ACTION_PAUSE else MediaPlaybackService.ACTION_PLAY
    }
    val playPausePendingIntent = PendingIntent.getService(
        context,
        if (isPlaying) 101 else 102,
        playPauseIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val playPauseIcon = Icon.createWithResource(
        context,
        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
    )
    val playPauseTitle = context.getString(if (isPlaying) R.string.pause else R.string.resume)
    val playPauseAction = RemoteAction(
        playPauseIcon,
        playPauseTitle,
        playPauseTitle,
        playPausePendingIntent,
    )

    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(listOf(playPauseAction))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter && isVideo && isPlaying)
    }

    return builder.build()
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
