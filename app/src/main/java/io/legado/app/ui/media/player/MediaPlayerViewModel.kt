package io.legado.app.ui.media.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.FeatureFlags
import io.legado.app.domain.gateway.MediaPlaybackGateway
import io.legado.app.domain.gateway.MediaDownloadGateway
import io.legado.app.domain.model.MediaDownloadRequest
import io.legado.app.domain.model.MediaPlaybackRequest
import io.legado.app.domain.model.MediaPlaybackSnapshot
import io.legado.app.domain.model.ResolvedBookMedia
import io.legado.app.domain.usecase.ResolveBookMediaUseCase
import io.legado.app.help.config.MediaPlayerConfig
import io.legado.app.help.config.MediaPlayerConfigSnapshot
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MediaPlayerViewModel(
    private val resolveBookMediaUseCase: ResolveBookMediaUseCase,
    private val playbackGateway: MediaPlaybackGateway,
    private val mediaDownloadGateway: MediaDownloadGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaPlayerUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MediaPlayerEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var current: ResolvedBookMedia? = null
    private var initializedKey: Pair<String, Int?>? = null
    private var handledEndedKey: Pair<String, Int>? = null
    private var speedBeforeBoost = 1f
    private var resolveJob: Job? = null

    init {
        syncConfigToState()
        viewModelScope.launch {
            playbackGateway.playbackState.collect(::updatePlayerSnapshot)
        }
    }

    private fun updateConfig(block: () -> Unit) {
        block()
        syncConfigToState()
    }

    private fun syncConfigToState() {
        val config = MediaPlayerConfig.snapshot()
        _uiState.update { it.withConfig(config) }
    }

    private fun MediaPlayerUiState.withConfig(config: MediaPlayerConfigSnapshot): MediaPlayerUiState {
        return copy(
            autoPlay = config.autoPlay,
            autoNext = config.autoNext,
            resumePosition = config.resumePosition,
            seekForwardSeconds = config.seekForwardSeconds,
            seekBackwardSeconds = config.seekBackwardSeconds,
            keepScreenOn = config.keepScreenOn,
            brightnessAuto = config.brightnessAuto,
            muted = config.muted,
            volume = config.defaultVolume,
            showSubtitles = config.showSubtitles,
            subtitleTextColor = config.subtitleTextColor,
            subtitleBackgroundColor = config.subtitleBackgroundColor,
            subtitleBackgroundOpacity = config.subtitleBackgroundOpacity,
            subtitleFontScale = config.subtitleFontScale,
            subtitleBottomPaddingDp = config.subtitleBottomPaddingDp,
            subtitleFontWeight = config.subtitleFontWeight,
        )
    }

    private fun applyConfiguredVolume() {
        val state = _uiState.value
        _effects.tryEmit(MediaPlayerEffect.SetVolume(if (state.muted) 0f else state.volume))
    }

    fun onIntent(intent: MediaPlayerIntent) {
        when (intent) {
            is MediaPlayerIntent.Initialize -> initialize(intent.bookUrl, intent.chapterIndex)
            MediaPlayerIntent.Retry -> reload()
            MediaPlayerIntent.Back -> _effects.tryEmit(MediaPlayerEffect.Exit)
            MediaPlayerIntent.TogglePlayback -> togglePlayback()
            MediaPlayerIntent.Previous -> loadAdjacent(previous = true)
            MediaPlayerIntent.Next -> loadAdjacent(previous = false)
            MediaPlayerIntent.PlaybackEnded -> {
                if (MediaPlayerConfig.autoNext) {
                    loadAdjacent(previous = false, playWhenReady = MediaPlayerConfig.autoPlay)
                }
            }
            MediaPlayerIntent.EnterPictureInPicture ->
                _effects.tryEmit(MediaPlayerEffect.EnterPictureInPicture)
            MediaPlayerIntent.DownloadCurrent -> downloadCurrent()
            MediaPlayerIntent.OpenDownloads -> {
                if (FeatureFlags.mediaDownload) {
                    _effects.tryEmit(MediaPlayerEffect.OpenDownloads)
                } else {
                    featureDisabled()
                }
            }
            is MediaPlayerIntent.SelectVariant -> selectVariant(intent.id)
            is MediaPlayerIntent.SeekTo -> seekTo(intent.positionMs)
            is MediaPlayerIntent.SeekBy -> seekTo(_uiState.value.positionMs + intent.deltaMs)
            is MediaPlayerIntent.SetPlaybackSpeed -> setPlaybackSpeed(intent.speed)
            is MediaPlayerIntent.SelectSubtitle -> selectSubtitle(intent.id)
            is MediaPlayerIntent.SelectAudioTrack -> selectAudioTrack(intent.id)
            MediaPlayerIntent.ShowChapters -> _uiState.update { it.copy(showChapterSheet = true) }
            MediaPlayerIntent.HideChapters -> _uiState.update { it.copy(showChapterSheet = false) }
            MediaPlayerIntent.ShowSettings -> _uiState.update { it.copy(showSettingsSheet = true) }
            MediaPlayerIntent.HideSettings -> _uiState.update { it.copy(showSettingsSheet = false) }
            is MediaPlayerIntent.SelectChapter -> {
                _uiState.update { it.copy(showChapterSheet = false) }
                load(_uiState.value.bookUrl, intent.index, playWhenReady = MediaPlayerConfig.autoPlay)
            }
            MediaPlayerIntent.ToggleControlsLock -> _uiState.update {
                it.copy(controlsLocked = !it.controlsLocked)
            }
            MediaPlayerIntent.ToggleFullscreen -> toggleFullscreen()
            is MediaPlayerIntent.SetAutoPlay -> updateConfig {
                MediaPlayerConfig.autoPlay = intent.enabled
            }
            is MediaPlayerIntent.SetAutoNext -> updateConfig {
                MediaPlayerConfig.autoNext = intent.enabled
            }
            is MediaPlayerIntent.SetResumePosition -> updateConfig {
                MediaPlayerConfig.resumePosition = intent.enabled
            }
            is MediaPlayerIntent.SetSeekForwardSeconds -> updateConfig {
                MediaPlayerConfig.seekForwardSeconds =
                    MediaPlayerConfig.normalizeSeekSeconds(intent.seconds)
            }
            is MediaPlayerIntent.SetSeekBackwardSeconds -> updateConfig {
                MediaPlayerConfig.seekBackwardSeconds =
                    MediaPlayerConfig.normalizeSeekSeconds(intent.seconds)
            }
            is MediaPlayerIntent.SetKeepScreenOn -> updateConfig {
                MediaPlayerConfig.keepScreenOn = intent.enabled
            }
            is MediaPlayerIntent.SetBrightnessAuto -> {
                updateConfig {
                    MediaPlayerConfig.brightnessAuto = intent.enabled
                }
                if (intent.enabled) {
                    _effects.tryEmit(MediaPlayerEffect.SetBrightnessAuto)
                } else {
                    _effects.tryEmit(MediaPlayerEffect.SetBrightness(_uiState.value.brightness))
                }
            }
            is MediaPlayerIntent.SetMuted -> {
                updateConfig { MediaPlayerConfig.muted = intent.muted }
                applyConfiguredVolume()
            }
            is MediaPlayerIntent.SetDefaultVolume -> {
                updateConfig {
                    MediaPlayerConfig.defaultVolume = intent.volume.coerceIn(0f, 1f)
                    MediaPlayerConfig.muted = false
                }
                applyConfiguredVolume()
            }
            is MediaPlayerIntent.SetShowSubtitles -> updateConfig {
                MediaPlayerConfig.showSubtitles = intent.enabled
            }
            is MediaPlayerIntent.SetSubtitleTextColor -> updateConfig {
                MediaPlayerConfig.subtitleTextColor = intent.color
            }
            is MediaPlayerIntent.SetSubtitleBackgroundColor -> updateConfig {
                MediaPlayerConfig.subtitleBackgroundColor = intent.color
            }
            is MediaPlayerIntent.SetSubtitleBackgroundOpacity -> updateConfig {
                MediaPlayerConfig.subtitleBackgroundOpacity = intent.opacity.coerceIn(0f, 1f)
            }
            is MediaPlayerIntent.SetSubtitleFontScale -> updateConfig {
                MediaPlayerConfig.subtitleFontScale = intent.scale.coerceIn(0.7f, 1.6f)
            }
            is MediaPlayerIntent.SetSubtitleBottomPadding -> updateConfig {
                MediaPlayerConfig.subtitleBottomPaddingDp = intent.paddingDp.coerceIn(0, 120)
            }
            is MediaPlayerIntent.SetSubtitleFontWeight -> updateConfig {
                MediaPlayerConfig.subtitleFontWeight = intent.weight
            }
            is MediaPlayerIntent.AdjustBrightness -> adjustBrightness(intent.delta)
            is MediaPlayerIntent.AdjustVolume -> adjustVolume(intent.delta)
            MediaPlayerIntent.BeginSpeedBoost -> beginSpeedBoost()
            MediaPlayerIntent.EndSpeedBoost -> endSpeedBoost()
            MediaPlayerIntent.ClearGestureIndicator -> _uiState.update {
                it.copy(gestureIndicator = null)
            }
            is MediaPlayerIntent.PlayerSnapshot -> updatePlayerSnapshot(intent)
            is MediaPlayerIntent.PlayerFailed -> {
                _uiState.update {
                    it.copy(
                        errorMessage = intent.message,
                        isBuffering = false,
                        isPlaying = false,
                    )
                }
            }
        }
    }

    private fun initialize(bookUrl: String, chapterIndex: Int?) {
        val key = bookUrl to chapterIndex
        if (initializedKey == key) return
        initializedKey = key
        load(bookUrl, chapterIndex, playWhenReady = MediaPlayerConfig.autoPlay)
    }

    private fun reload() {
        val state = _uiState.value
        if (state.bookUrl.isBlank()) return
        load(state.bookUrl, current?.chapterIndex, playWhenReady = state.isPlaying)
    }

    private fun load(
        bookUrl: String,
        chapterIndex: Int?,
        playWhenReady: Boolean,
    ) {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    bookUrl = bookUrl,
                    isLoading = true,
                    errorMessage = null,
                    isBuffering = false,
                    positionMs = 0L,
                    durationMs = 0L,
                )
            }
            try {
                val resolved = resolveBookMediaUseCase.execute(bookUrl, chapterIndex).getOrThrow()
                current = resolved
                val selected = preferredVariant(resolved)
                _uiState.update {
                    it.copy(
                        bookUrl = resolved.bookUrl,
                        bookTitle = resolved.bookTitle,
                        episodeTitle = resolved.media.title,
                        coverUrl = resolved.coverUrl,
                        isVideo = resolved.isVideo,
                        isLoading = false,
                        variants = resolved.media.variants.map { variant ->
                            MediaVariantUi(
                                id = variant.id,
                                title = variant.title,
                                contentKind = variant.contentKind,
                                protocol = variant.protocol,
                                mimeType = variant.mimeType,
                                downloadSupported = variant.downloadSupported,
                                externalPlayerRequired = variant.externalPlayerRequired,
                            )
                        }.toImmutableList(),
                        selectedVariantId = selected.id,
                        chapterIndex = resolved.chapterIndex,
                        chapterCount = resolved.chapterCount,
                        hasPrevious = resolved.previousChapterIndex != null,
                        hasNext = resolved.nextChapterIndex != null,
                        chapters = resolved.chapters.map { chapter ->
                            MediaChapterUi(chapter.index, chapter.title, chapter.isOffline)
                        }.toImmutableList(),
                        subtitleTracks = resolved.media.subtitles.map { track ->
                            MediaTrackUi(track.id, track.label, track.language)
                        }.toImmutableList(),
                        audioTracks = resolved.media.audioTracks.map { track ->
                            MediaTrackUi(track.id, track.label, track.language)
                        }.toImmutableList(),
                        errorMessage = null,
                    )
                }
                if (!selected.externalPlayerRequired) {
                    playbackGateway.prepare(
                        resolved.toPlaybackRequest(
                            variant = selected,
                            playWhenReady = playWhenReady,
                        )
                    )
                    applyConfiguredVolume()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isBuffering = false,
                        isPlaying = false,
                        errorMessage = error.localizedMessage.orEmpty(),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        resolveJob?.cancel()
        super.onCleared()
    }

    private fun preferredVariant(resolved: ResolvedBookMedia): io.legado.app.domain.model.ResolvedMediaVariant {
        return resolved.media.variants.firstOrNull { !it.externalPlayerRequired }
            ?: resolved.media.variants.first()
    }

    private fun togglePlayback() {
        val resolved = current ?: return
        val selected = resolved.media.variants
            .firstOrNull { it.id == _uiState.value.selectedVariantId }
            ?: return
        if (selected.externalPlayerRequired) {
            _effects.tryEmit(
                MediaPlayerEffect.ShowMessage(
                    "Nguồn này cần trình phát ngoài; DrDucBook không tự mở trình duyệt."
                )
            )
        } else {
            viewModelScope.launch {
                if (_uiState.value.isPlaying) playbackGateway.pause() else playbackGateway.play()
            }
        }
    }

    private fun selectVariant(id: String) {
        val resolved = current ?: return
        val variant = resolved.media.variants.firstOrNull { it.id == id } ?: return
        val shouldResume = _uiState.value.isPlaying
        val currentPosition = _uiState.value.positionMs
        _uiState.update {
            it.copy(
                selectedVariantId = id,
                isBuffering = !variant.externalPlayerRequired,
                errorMessage = null,
            )
        }
        if (variant.externalPlayerRequired) return
        viewModelScope.launch {
            playbackGateway.prepare(
                resolved.toPlaybackRequest(
                    variant = variant,
                    playWhenReady = shouldResume,
                    startPositionMs = currentPosition,
                )
            )
        }
    }

    private fun loadAdjacent(previous: Boolean, playWhenReady: Boolean = true) {
        val resolved = current ?: return
        val target = if (previous) {
            resolved.previousChapterIndex
        } else {
            resolved.nextChapterIndex
        } ?: return
        load(
            bookUrl = resolved.bookUrl,
            chapterIndex = target,
            playWhenReady = playWhenReady,
        )
    }

    private fun seekTo(positionMs: Long) {
        val bounded = positionMs.coerceIn(0L, _uiState.value.durationMs.coerceAtLeast(0L))
        _uiState.update { it.copy(positionMs = bounded) }
        viewModelScope.launch { playbackGateway.seekTo(bounded) }
    }

    private fun setPlaybackSpeed(speed: Float) {
        val bounded = speed.coerceIn(0.5f, 3f)
        _uiState.update { it.copy(playbackSpeed = bounded) }
        viewModelScope.launch { playbackGateway.setPlaybackSpeed(bounded) }
    }

    private fun selectSubtitle(id: String?) {
        _uiState.update { it.copy(selectedSubtitleId = id) }
        viewModelScope.launch { playbackGateway.selectSubtitle(id) }
    }

    private fun selectAudioTrack(id: String?) {
        _uiState.update { it.copy(selectedAudioTrackId = id) }
        viewModelScope.launch { playbackGateway.selectAudioTrack(id) }
    }

    private fun toggleFullscreen() {
        val enabled = !_uiState.value.isFullscreen
        _uiState.update { it.copy(isFullscreen = enabled) }
        _effects.tryEmit(MediaPlayerEffect.SetFullscreen(enabled))
    }

    private fun adjustBrightness(delta: Float) {
        if (MediaPlayerConfig.brightnessAuto) {
            MediaPlayerConfig.brightnessAuto = false
            syncConfigToState()
        }
        val value = (_uiState.value.brightness + delta).coerceIn(0.02f, 1f)
        _uiState.update {
            it.copy(brightness = value, gestureIndicator = MediaGestureIndicator.BRIGHTNESS)
        }
        _effects.tryEmit(MediaPlayerEffect.SetBrightness(value))
    }

    private fun adjustVolume(delta: Float) {
        val value = (_uiState.value.volume + delta).coerceIn(0f, 1f)
        MediaPlayerConfig.defaultVolume = value
        MediaPlayerConfig.muted = value <= 0.01f
        _uiState.update {
            it.copy(
                volume = value,
                muted = MediaPlayerConfig.muted,
                gestureIndicator = MediaGestureIndicator.VOLUME,
            )
        }
        _effects.tryEmit(MediaPlayerEffect.SetVolume(value))
    }

    private fun beginSpeedBoost() {
        if (_uiState.value.gestureIndicator == MediaGestureIndicator.SPEED) return
        speedBeforeBoost = _uiState.value.playbackSpeed
        _uiState.update { it.copy(gestureIndicator = MediaGestureIndicator.SPEED) }
        viewModelScope.launch { playbackGateway.setPlaybackSpeed(2f) }
    }

    private fun endSpeedBoost() {
        if (_uiState.value.gestureIndicator != MediaGestureIndicator.SPEED) return
        _uiState.update { it.copy(gestureIndicator = null) }
        viewModelScope.launch { playbackGateway.setPlaybackSpeed(speedBeforeBoost) }
    }

    private fun downloadCurrent() {
        if (!FeatureFlags.mediaDownload) {
            featureDisabled()
            return
        }
        val resolved = current ?: return
        val variant = resolved.media.variants
            .firstOrNull { it.id == _uiState.value.selectedVariantId }
            ?: return
        if (!variant.downloadSupported || variant.externalPlayerRequired) {
            _effects.tryEmit(MediaPlayerEffect.ShowMessage("Biến thể này không hỗ trợ tải ngoại tuyến"))
            return
        }
        viewModelScope.launch {
            runCatching {
                mediaDownloadGateway.enqueue(
                    MediaDownloadRequest(
                        bookUrl = resolved.bookUrl,
                        bookTitle = resolved.bookTitle,
                        coverUrl = resolved.coverUrl,
                        chapterIndex = resolved.chapterIndex,
                        episodeTitle = resolved.media.title,
                        variant = variant,
                    )
                )
            }.onSuccess {
                _effects.tryEmit(MediaPlayerEffect.StartDownloadService)
                _effects.tryEmit(MediaPlayerEffect.ShowMessage("Đã thêm tập vào hàng đợi tải"))
            }.onFailure { error ->
                _effects.tryEmit(MediaPlayerEffect.ShowMessage(error.localizedMessage.orEmpty()))
            }
        }
    }

    private fun featureDisabled() {
        _effects.tryEmit(MediaPlayerEffect.ShowMessage("Offline media download is disabled in Lab settings"))
    }

    private fun updatePlayerSnapshot(snapshot: MediaPlayerIntent.PlayerSnapshot) {
        _uiState.update {
            it.copy(
                isPlaying = snapshot.isPlaying,
                isBuffering = snapshot.isBuffering,
                positionMs = snapshot.positionMs.coerceAtLeast(0L),
                durationMs = snapshot.durationMs.coerceAtLeast(0L),
            )
        }
    }

    private fun updatePlayerSnapshot(snapshot: MediaPlaybackSnapshot) {
        val state = _uiState.value
        if (snapshot.bookUrl.isNotBlank() &&
            (snapshot.bookUrl != state.bookUrl || snapshot.chapterIndex != state.chapterIndex)
        ) return
        _uiState.update {
            it.copy(
                isPlaying = snapshot.isPlaying,
                isBuffering = snapshot.isBuffering,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                playbackSpeed = snapshot.playbackSpeed,
                subtitleTracks = snapshot.subtitleTracks.map {
                    MediaTrackUi(it.id, it.label, it.language)
                }.toImmutableList(),
                audioTracks = snapshot.audioTracks.map {
                    MediaTrackUi(it.id, it.label, it.language)
                }.toImmutableList(),
                selectedSubtitleId = snapshot.selectedSubtitleId,
                selectedAudioTrackId = snapshot.selectedAudioTrackId,
                errorMessage = snapshot.errorMessage ?: it.errorMessage,
            )
        }
        if (snapshot.ended && MediaPlayerConfig.autoNext) {
            val endedKey = snapshot.bookUrl to snapshot.chapterIndex
            if (handledEndedKey != endedKey) {
                handledEndedKey = endedKey
                loadAdjacent(previous = false, playWhenReady = MediaPlayerConfig.autoPlay)
            }
        } else if (snapshot.bookUrl.isNotBlank()) {
            handledEndedKey = null
        }
    }

    private fun ResolvedBookMedia.toPlaybackRequest(
        variant: io.legado.app.domain.model.ResolvedMediaVariant,
        playWhenReady: Boolean,
        startPositionMs: Long = 0L,
    ) = MediaPlaybackRequest(
        bookUrl = bookUrl,
        bookTitle = bookTitle,
        chapterIndex = chapterIndex,
        episodeTitle = media.title,
        coverUrl = coverUrl,
        variant = variant,
        subtitles = media.subtitles,
        audioTracks = media.audioTracks,
        playWhenReady = playWhenReady,
        startPositionMs = startPositionMs,
        resumeStoredPosition = MediaPlayerConfig.resumePosition && startPositionMs <= 0L,
        clipStartMs = clipStartMs,
        clipEndMs = clipEndMs,
    )
}
