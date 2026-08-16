package io.legado.app.ui.media.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drducbook.app.R
import io.legado.app.constant.FeatureFlags
import io.legado.app.domain.model.MediaProtocol
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.button.AppIconButton
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MediaPlayerScreen(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
    mediaSurface: @Composable () -> Unit,
) {
    var controlsVisible by remember(state.bookUrl, state.chapterIndex) { mutableStateOf(true) }
    val showTopBar = !state.isVideo || (controlsVisible && !state.controlsLocked)

    LaunchedEffect(
        state.isVideo,
        state.isPlaying,
        state.controlsLocked,
        state.showChapterSheet,
        state.showSettingsSheet,
        controlsVisible,
    ) {
        if (
            state.isVideo &&
            state.isPlaying &&
            controlsVisible &&
            !state.controlsLocked &&
            !state.showChapterSheet &&
            !state.showSettingsSheet
        ) {
            delay(VideoControlsAutoHideDelayMs)
            controlsVisible = false
        }
    }

    LaunchedEffect(state.showChapterSheet, state.showSettingsSheet) {
        if (state.showChapterSheet || state.showSettingsSheet) {
            controlsVisible = true
        }
    }

    AppScaffold(
        alwaysDrawBehindBars = state.isVideo,
        disableHazeSource = state.isVideo,
        topBar = {
            if (showTopBar) GlassMediumFlexibleTopAppBar(
                title = state.bookTitle.ifBlank { stringResource(R.string.media_player_title) },
                subtitle = state.episodeTitle,
                navigationIcon = {
                    TopBarNavigationButton(
                        onClick = { onIntent(MediaPlayerIntent.Back) }
                    )
                },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = stringResource(R.string.media_chapter_list),
                        onClick = { onIntent(MediaPlayerIntent.ShowChapters) },
                    )
                    TopBarActionButton(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.media_player_settings),
                        onClick = { onIntent(MediaPlayerIntent.ShowSettings) },
                    )
                    if (FeatureFlags.mediaDownload) {
                        TopBarActionButton(
                            imageVector = Icons.Default.DownloadForOffline,
                            contentDescription = stringResource(R.string.media_downloads_title),
                            onClick = { onIntent(MediaPlayerIntent.OpenDownloads) },
                        )
                        if (state.variants.firstOrNull { it.id == state.selectedVariantId }?.downloadSupported == true) {
                            TopBarActionButton(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(R.string.media_download_current),
                                onClick = { onIntent(MediaPlayerIntent.DownloadCurrent) },
                            )
                        }
                    }
                    if (state.isVideo) {
                        TopBarActionButton(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = stringResource(R.string.media_picture_in_picture),
                            onClick = { onIntent(MediaPlayerIntent.EnterPictureInPicture) },
                        )
                    }
                },
            )
        },
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AppCircularProgressIndicator()
                }
            }

            state.errorMessage != null -> {
                MediaPlayerError(
                    message = state.errorMessage,
                    onRetry = { onIntent(MediaPlayerIntent.Retry) },
                )
            }

            else -> {
                MediaPlayerContent(
                    state = state,
                    onIntent = onIntent,
                    mediaSurface = mediaSurface,
                    controlsVisible = controlsVisible,
                    onToggleControls = {
                        controlsVisible = !controlsVisible
                    },
                    onShowControls = {
                        controlsVisible = true
                    },
                )
            }
        }
    }
    MediaChapterSheet(state, onIntent)
    MediaPlayerSettingsSheet(state, onIntent)
}

@Composable
private fun MediaPlayerContent(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
    mediaSurface: @Composable () -> Unit,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onShowControls: () -> Unit,
) {
    if (state.isVideo) {
        VideoPlayerContent(
            state = state,
            onIntent = onIntent,
            mediaSurface = mediaSurface,
            controlsVisible = controlsVisible,
            onToggleControls = onToggleControls,
            onShowControls = onShowControls,
        )
    } else {
        AudioPlayerContent(
            state = state,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun VideoPlayerContent(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
    mediaSurface: @Composable () -> Unit,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onShowControls: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(state.seekBackwardSeconds, state.seekForwardSeconds, state.controlsLocked) {
                detectTapGestures(
                    onTap = {
                        if (state.controlsLocked) {
                            onShowControls()
                        } else {
                            onToggleControls()
                        }
                    },
                    onDoubleTap = { offset ->
                        onShowControls()
                        if (!state.controlsLocked) {
                            onIntent(
                                MediaPlayerIntent.SeekBy(
                                    if (offset.x < size.width / 2f) {
                                        -state.seekBackwardSeconds * 1_000L
                                    } else {
                                        state.seekForwardSeconds * 1_000L
                                    }
                                )
                            )
                        }
                    },
                    onPress = {
                        if (!state.controlsLocked) {
                            coroutineScope {
                                var boosted = false
                                val boostJob = launch {
                                    delay(450L)
                                    boosted = true
                                    onIntent(MediaPlayerIntent.BeginSpeedBoost)
                                }
                                tryAwaitRelease()
                                boostJob.cancel()
                                if (boosted) onIntent(MediaPlayerIntent.EndSpeedBoost)
                            }
                        }
                    },
                )
            }
            .pointerInput(state.controlsLocked) {
                if (state.controlsLocked) return@pointerInput
                var adjustBrightness = false
                detectVerticalDragGestures(
                    onDragStart = {
                        onShowControls()
                        adjustBrightness = it.x < size.width / 2f
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        val delta = -amount / size.height.coerceAtLeast(1).toFloat()
                        onIntent(
                            if (adjustBrightness) MediaPlayerIntent.AdjustBrightness(delta)
                            else MediaPlayerIntent.AdjustVolume(delta)
                        )
                    },
                    onDragEnd = { onIntent(MediaPlayerIntent.ClearGestureIndicator) },
                    onDragCancel = { onIntent(MediaPlayerIntent.ClearGestureIndicator) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        mediaSurface()
        if (state.isBuffering) {
            AppCircularProgressIndicator()
        }
        if (controlsVisible || state.controlsLocked) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppIconButton(
                    onClick = {
                        onShowControls()
                        onIntent(MediaPlayerIntent.ToggleControlsLock)
                    },
                ) {
                    Icon(
                        if (state.controlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        stringResource(R.string.media_lock_controls),
                        tint = Color.White,
                    )
                }
                if (!state.controlsLocked) {
                    AppIconButton(
                        onClick = {
                            onShowControls()
                            onIntent(MediaPlayerIntent.ToggleFullscreen)
                        },
                    ) {
                        Icon(
                            if (state.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            stringResource(R.string.media_fullscreen),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
        if (controlsVisible && !state.controlsLocked) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MediaControlPanel(state = state, onIntent = onIntent)
            }
        }
        MediaGestureOverlay(state, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun AudioPlayerContent(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .height(320.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = state.coverUrl,
                contentDescription = state.bookTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (state.isBuffering) {
                AppCircularProgressIndicator()
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediaControlPanel(state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun MediaControlPanel(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
) {
    Text(
        text = state.episodeTitle,
        style = LegadoTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = if (state.isVideo) Color.White else Color.Unspecified,
    )
    Text(
        text = stringResource(
            R.string.media_episode_progress,
            state.chapterIndex + 1,
            state.chapterCount,
        ),
        style = LegadoTheme.typography.bodySmall,
        color = if (state.isVideo) Color.White.copy(alpha = 0.78f)
        else LegadoTheme.colorScheme.onSurfaceVariant,
    )
    PlaybackSeekBar(state = state, onIntent = onIntent)
    PlaybackControls(state = state, onIntent = onIntent)
    DownloadCurrentButton(state = state, onIntent = onIntent)
    VariantSelector(state = state, onIntent = onIntent)
    SubtitleSelector(state = state, onIntent = onIntent)
    AudioTrackSelector(state = state, onIntent = onIntent)
    PlaybackSpeedSelector(state = state, onIntent = onIntent)
}

@Composable
private fun PlaybackSeekBar(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.positionMs, duration, dragging) {
        if (!dragging) {
            sliderValue = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        }
    }
    AppSlider(
        value = sliderValue,
        onValueChange = {
            dragging = true
            sliderValue = it
        },
        onValueChangeFinished = {
            dragging = false
            onIntent(MediaPlayerIntent.SeekTo((duration * sliderValue).toLong()))
        },
        enabled = state.durationMs > 0,
        accessibilityLabel = stringResource(R.string.media_seek),
        accessibilityValue = formatDuration((duration * sliderValue).toLong()),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDuration(if (dragging) (duration * sliderValue).toLong() else state.positionMs),
            style = LegadoTheme.typography.labelMedium,
        )
        Text(
            text = formatDuration(state.durationMs),
            style = LegadoTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PlaybackControls(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
) {
    val selected = state.variants.firstOrNull { it.id == state.selectedVariantId }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerControlButton(
            enabled = state.hasPrevious,
            icon = { Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous_chapter)) },
            onClick = { onIntent(MediaPlayerIntent.Previous) },
        )
        PlayerControlButton(
            icon = {
                Icon(
                    Icons.Default.Replay10,
                    stringResource(R.string.media_rewind_seconds, state.seekBackwardSeconds),
                )
            },
            onClick = { onIntent(MediaPlayerIntent.SeekBy(-state.seekBackwardSeconds * 1_000L)) },
        )
        PlayerControlButton(
            modifier = Modifier.size(64.dp),
            icon = {
                Icon(
                    imageVector = when {
                        selected?.externalPlayerRequired == true -> Icons.Default.OpenInBrowser
                        state.isPlaying -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        selected?.externalPlayerRequired == true ->
                            stringResource(R.string.media_open_external)
                        state.isPlaying -> stringResource(R.string.pause)
                        else -> stringResource(R.string.audio_play)
                    },
                    modifier = Modifier.size(36.dp),
                )
            },
            onClick = { onIntent(MediaPlayerIntent.TogglePlayback) },
        )
        PlayerControlButton(
            icon = {
                Icon(
                    Icons.Default.Forward10,
                    stringResource(R.string.media_forward_seconds, state.seekForwardSeconds),
                )
            },
            onClick = { onIntent(MediaPlayerIntent.SeekBy(state.seekForwardSeconds * 1_000L)) },
        )
        PlayerControlButton(
            enabled = state.hasNext,
            icon = { Icon(Icons.Default.SkipNext, stringResource(R.string.next_chapter)) },
            onClick = { onIntent(MediaPlayerIntent.Next) },
        )
    }
}

@Composable
private fun DownloadCurrentButton(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
) {
    if (!FeatureFlags.mediaDownload) return
    val selected = state.variants.firstOrNull { it.id == state.selectedVariantId }
    val canDownload = selected?.downloadSupported == true && !selected.externalPlayerRequired
    Button(
        onClick = { onIntent(MediaPlayerIntent.DownloadCurrent) },
        enabled = canDownload,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Download, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (canDownload) R.string.media_download_current
                else R.string.media_download_not_supported
            )
        )
    }
}

@Composable
private fun PlayerControlButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        icon()
    }
}

@Composable
private fun VariantSelector(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
) {
    if (state.variants.size <= 1) return
    Text(
        text = stringResource(R.string.media_quality),
        style = LegadoTheme.typography.titleSmall,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.variants, key = { it.id }) { variant ->
            FilterChip(
                selected = variant.id == state.selectedVariantId,
                onClick = { onIntent(MediaPlayerIntent.SelectVariant(variant.id)) },
                label = {
                    Text(
                        text = buildString {
                            append(variant.title)
                            append(" · ")
                            append(protocolLabel(variant.protocol))
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun PlaybackSpeedSelector(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
) {
    Text(
        text = stringResource(R.string.media_playback_speed),
        style = LegadoTheme.typography.titleSmall,
    )
    AppSlider(
        value = state.playbackSpeed,
        onValueChange = { value ->
            onIntent(MediaPlayerIntent.SetPlaybackSpeed((value * 10f).roundToInt() / 10f))
        },
        valueRange = 0.5f..3f,
        steps = 24,
        accessibilityLabel = stringResource(R.string.media_playback_speed),
        accessibilityValue = "${state.playbackSpeed}x",
        modifier = Modifier.fillMaxWidth(),
    )
    Text("${state.playbackSpeed}x", style = LegadoTheme.typography.labelMedium)
}

@Composable
private fun SubtitleSelector(state: MediaPlayerUiState, onIntent: (MediaPlayerIntent) -> Unit) {
    if (state.subtitleTracks.isEmpty()) return
    Text(stringResource(R.string.media_subtitles), style = LegadoTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = state.selectedSubtitleId == null,
                onClick = { onIntent(MediaPlayerIntent.SelectSubtitle(null)) },
                label = { Text(stringResource(R.string.disable)) },
            )
        }
        items(state.subtitleTracks, key = MediaTrackUi::id) { track ->
            FilterChip(
                selected = state.selectedSubtitleId == track.id,
                onClick = { onIntent(MediaPlayerIntent.SelectSubtitle(track.id)) },
                leadingIcon = { Icon(Icons.Default.Subtitles, contentDescription = null) },
                label = { Text(track.label) },
            )
        }
    }
}

@Composable
private fun AudioTrackSelector(state: MediaPlayerUiState, onIntent: (MediaPlayerIntent) -> Unit) {
    if (state.audioTracks.size <= 1) return
    Text(stringResource(R.string.media_audio_tracks), style = LegadoTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.audioTracks, key = MediaTrackUi::id) { track ->
            FilterChip(
                selected = state.selectedAudioTrackId == track.id,
                onClick = { onIntent(MediaPlayerIntent.SelectAudioTrack(track.id)) },
                label = { Text(track.label) },
            )
        }
    }
}

@Composable
private fun MediaGestureOverlay(state: MediaPlayerUiState, modifier: Modifier = Modifier) {
    val indicator = state.gestureIndicator ?: return
    val value = when (indicator) {
        MediaGestureIndicator.BRIGHTNESS -> state.brightness
        MediaGestureIndicator.VOLUME -> state.volume
        MediaGestureIndicator.SPEED -> 2f / 3f
    }
    Column(
        modifier = modifier.fillMaxWidth(0.46f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = when (indicator) {
                MediaGestureIndicator.BRIGHTNESS -> Icons.Default.BrightnessMedium
                MediaGestureIndicator.VOLUME -> Icons.Default.VolumeUp
                MediaGestureIndicator.SPEED -> Icons.Default.Forward10
            },
            contentDescription = null,
            tint = Color.White,
        )
        Text(
            if (indicator == MediaGestureIndicator.SPEED) "2.0x" else "${(value * 100).roundToInt()}%",
            color = Color.White,
        )
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MediaChapterSheet(state: MediaPlayerUiState, onIntent: (MediaPlayerIntent) -> Unit) {
    AppModalBottomSheet(
        show = state.showChapterSheet,
        onDismissRequest = { onIntent(MediaPlayerIntent.HideChapters) },
        title = stringResource(R.string.media_chapter_list),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.chapters, key = MediaChapterUi::index) { chapter ->
                FilterChip(
                    selected = chapter.index == state.chapterIndex,
                    onClick = { onIntent(MediaPlayerIntent.SelectChapter(chapter.index)) },
                    label = {
                        Text(
                            if (chapter.isOffline) "${chapter.title} · Ngoại tuyến" else chapter.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaPlayerSettingsSheet(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = state.showSettingsSheet,
        onDismissRequest = { onIntent(MediaPlayerIntent.HideSettings) },
        title = stringResource(R.string.media_player_settings),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(title = stringResource(R.string.media_player_playback_settings)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.media_player_auto_play),
                    checked = state.autoPlay,
                    onCheckedChange = { onIntent(MediaPlayerIntent.SetAutoPlay(it)) },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.media_player_auto_next),
                    checked = state.autoNext,
                    onCheckedChange = { onIntent(MediaPlayerIntent.SetAutoNext(it)) },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.media_player_resume_position),
                    checked = state.resumePosition,
                    onCheckedChange = { onIntent(MediaPlayerIntent.SetResumePosition(it)) },
                )
                SeekSecondsSelector(
                    title = stringResource(R.string.media_player_seek_forward),
                    selectedSeconds = state.seekForwardSeconds,
                    onSelect = { onIntent(MediaPlayerIntent.SetSeekForwardSeconds(it)) },
                )
                SeekSecondsSelector(
                    title = stringResource(R.string.media_player_seek_backward),
                    selectedSeconds = state.seekBackwardSeconds,
                    onSelect = { onIntent(MediaPlayerIntent.SetSeekBackwardSeconds(it)) },
                )
            }

            SettingsSection(title = stringResource(R.string.media_player_display_settings)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.media_player_keep_screen_on),
                    checked = state.keepScreenOn,
                    onCheckedChange = { onIntent(MediaPlayerIntent.SetKeepScreenOn(it)) },
                )
            }

            SettingsSection(title = stringResource(R.string.media_player_brightness_volume)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.media_player_auto_brightness),
                    checked = state.brightnessAuto,
                    onCheckedChange = { onIntent(MediaPlayerIntent.SetBrightnessAuto(it)) },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.media_player_muted),
                    checked = state.muted,
                    onCheckedChange = { onIntent(MediaPlayerIntent.SetMuted(it)) },
                )
                SliderSettingRow(
                    title = stringResource(R.string.media_player_default_volume),
                    valueText = "${(state.volume * 100).roundToInt()}%",
                ) {
                    AppSlider(
                        value = state.volume,
                        onValueChange = { onIntent(MediaPlayerIntent.SetDefaultVolume(it)) },
                        valueRange = 0f..1f,
                        accessibilityLabel = stringResource(R.string.media_player_default_volume),
                        accessibilityValue = "${(state.volume * 100).roundToInt()}%",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.media_player_subtitle_settings)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.media_player_show_subtitles),
                    checked = state.showSubtitles,
                    onCheckedChange = { onIntent(MediaPlayerIntent.SetShowSubtitles(it)) },
                )
                if (state.showSubtitles) {
                    ColorSwatchRow(
                        title = stringResource(R.string.media_player_subtitle_text_color),
                        selectedColor = state.subtitleTextColor,
                        colors = SubtitleTextColors,
                        onSelect = { onIntent(MediaPlayerIntent.SetSubtitleTextColor(it)) },
                    )
                    ColorSwatchRow(
                        title = stringResource(R.string.media_player_subtitle_background_color),
                        selectedColor = state.subtitleBackgroundColor,
                        colors = SubtitleBackgroundColors,
                        onSelect = { onIntent(MediaPlayerIntent.SetSubtitleBackgroundColor(it)) },
                    )
                    SubtitleFontWeightSelector(state, onIntent)
                    SliderSettingRow(
                        title = stringResource(R.string.media_player_subtitle_font_scale),
                        valueText = "${(state.subtitleFontScale * 100).roundToInt()}%",
                    ) {
                        AppSlider(
                            value = state.subtitleFontScale,
                            onValueChange = { onIntent(MediaPlayerIntent.SetSubtitleFontScale(it)) },
                            valueRange = 0.7f..1.6f,
                            steps = 8,
                            accessibilityLabel = stringResource(R.string.media_player_subtitle_font_scale),
                            accessibilityValue = "${(state.subtitleFontScale * 100).roundToInt()}%",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SliderSettingRow(
                        title = stringResource(R.string.media_player_subtitle_background_opacity),
                        valueText = "${(state.subtitleBackgroundOpacity * 100).roundToInt()}%",
                    ) {
                        AppSlider(
                            value = state.subtitleBackgroundOpacity,
                            onValueChange = {
                                onIntent(MediaPlayerIntent.SetSubtitleBackgroundOpacity(it))
                            },
                            valueRange = 0f..1f,
                            accessibilityLabel = stringResource(
                                R.string.media_player_subtitle_background_opacity
                            ),
                            accessibilityValue = "${(state.subtitleBackgroundOpacity * 100).roundToInt()}%",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SliderSettingRow(
                        title = stringResource(R.string.media_player_subtitle_bottom_padding),
                        valueText = "${state.subtitleBottomPaddingDp}dp",
                    ) {
                        AppSlider(
                            value = state.subtitleBottomPaddingDp.toFloat(),
                            onValueChange = {
                                onIntent(MediaPlayerIntent.SetSubtitleBottomPadding(it.roundToInt()))
                            },
                            valueRange = 0f..120f,
                            steps = 11,
                            accessibilityLabel = stringResource(
                                R.string.media_player_subtitle_bottom_padding
                            ),
                            accessibilityValue = "${state.subtitleBottomPaddingDp}dp",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = LegadoTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = LegadoTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderSettingRow(
    title: String,
    valueText: String,
    slider: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = LegadoTheme.typography.bodyLarge)
            Text(
                valueText,
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
        slider()
    }
}

@Composable
private fun SeekSecondsSelector(
    title: String,
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = LegadoTheme.typography.bodyLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SeekSecondOptions, key = { it }) { seconds ->
                FilterChip(
                    selected = seconds == selectedSeconds,
                    onClick = { onSelect(seconds) },
                    label = { Text(stringResource(R.string.media_player_seconds, seconds)) },
                )
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    title: String,
    selectedColor: Int,
    colors: List<Int>,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = LegadoTheme.typography.bodyLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(colors, key = { it }) { color ->
                val selected = color == selectedColor
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                LegadoTheme.colorScheme.primary
                            } else {
                                LegadoTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        )
                        .clickable { onSelect(color) },
                )
            }
        }
    }
}

@Composable
private fun SubtitleFontWeightSelector(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.media_player_subtitle_font_weight),
            style = LegadoTheme.typography.bodyLarge,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SubtitleFontWeights, key = { it }) { weight ->
                FilterChip(
                    selected = state.subtitleFontWeight == weight,
                    onClick = { onIntent(MediaPlayerIntent.SetSubtitleFontWeight(weight)) },
                    label = {
                        Text(
                            when (weight) {
                                0 -> stringResource(R.string.media_player_font_weight_normal)
                                2 -> stringResource(R.string.media_player_font_weight_bold)
                                else -> stringResource(R.string.media_player_font_weight_medium)
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaPlayerError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message.ifBlank { stringResource(R.string.media_resolve_failed) },
            color = LegadoTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun protocolLabel(protocol: MediaProtocol): String = when (protocol) {
    MediaProtocol.DIRECT -> stringResource(R.string.media_protocol_direct)
    MediaProtocol.HLS -> "HLS"
    MediaProtocol.DASH -> "DASH"
    MediaProtocol.IFRAME -> stringResource(R.string.media_protocol_iframe)
    MediaProtocol.UNKNOWN -> stringResource(R.string.unknown_state)
}

private val SeekSecondOptions = listOf(5, 10, 15, 30)

private val SubtitleTextColors = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFFEB3B.toInt(),
    0xFF00BCD4.toInt(),
    0xFFF06292.toInt(),
    0xFF8BC34A.toInt(),
    0xFFFFA726.toInt(),
)

private val SubtitleBackgroundColors = listOf(
    0xFF000000.toInt(),
    0xFF121018.toInt(),
    0xFF424242.toInt(),
    0xFFFFFFFF.toInt(),
)

private val SubtitleFontWeights = listOf(0, 1, 2)

private const val VideoControlsAutoHideDelayMs = 3_500L

private fun formatDuration(valueMs: Long): String {
    val totalSeconds = (valueMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
