package io.legado.app.service

import android.content.BroadcastReceiver
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioFocusRequestCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.drducbook.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.NotificationId
import io.legado.app.domain.model.MediaPlaybackRequest
import io.legado.app.domain.model.MediaPlaybackSnapshot
import io.legado.app.domain.model.MediaPlaybackTrack
import io.legado.app.domain.usecase.ResolveBookMediaUseCase
import io.legado.app.help.MediaHelp
import io.legado.app.help.media.MediaPlaybackPositionPolicy
import io.legado.app.help.media.ResolvedMediaPlayer
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import splitties.systemservices.audioManager
import splitties.systemservices.notificationManager

@OptIn(UnstableApi::class)
class MediaPlaybackService : BaseService(), Player.Listener,
    AudioManager.OnAudioFocusChangeListener {

    inner class LocalBinder : Binder() {
        val service: MediaPlaybackService
            get() = this@MediaPlaybackService
    }

    private val binder = LocalBinder()
    private val resolvedPlayer by lazy { ResolvedMediaPlayer.create(this) }
    private val resolveBookMedia: ResolveBookMediaUseCase by inject()
    val player: Player
        get() = resolvedPlayer.player

    private val _playbackState = MutableStateFlow(MediaPlaybackSnapshot())
    val playbackState = _playbackState.asStateFlow()

    private val preferences by lazy {
        getSharedPreferences("media_playback_state", Context.MODE_PRIVATE)
    }
    private val focusRequest: AudioFocusRequestCompat by lazy {
        MediaHelp.buildAudioFocusRequestCompat(this)
    }
    private val mediaSession by lazy {
        MediaSessionCompat(this, "MediaPlaybackService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onStop() = stopPlayback()
                override fun onSeekTo(pos: Long) = seekTo(pos)
                override fun onSkipToPrevious() = prepareAdjacent(previous = true)
                override fun onSkipToNext() = prepareAdjacent(previous = false)
            })
            isActive = true
        }
    }
    private var currentRequest: MediaPlaybackRequest? = null
    private var clipEnded = false
    private var snapshotJob: Job? = null
    private var resumeAfterFocusGain = false
    private var routeReceiverRegistered = false
    private var resumeAfterRouteReconnect = false
    private var desiredSubtitleId: String? = null
    private var desiredAudioTrackId: String? = null
    private var lastInteractionAt = System.currentTimeMillis()
    private val routeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    resumeAfterRouteReconnect = resolvedPlayer.player.isPlaying
                    pause()
                }
                Intent.ACTION_HEADSET_PLUG -> handleRouteConnection(
                    intent.getIntExtra("state", 0) == 1
                )
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> handleRouteConnection(
                    intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED) ==
                        BluetoothProfile.STATE_CONNECTED
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        resolvedPlayer.player.addListener(this)
        registerReceiver(
            routeReceiver,
            IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            },
        )
        routeReceiverRegistered = true
        snapshotJob = lifecycleScope.launch {
            var lastPersistedAt = 0L
            while (isActive) {
                enforceClipEnd()
                publishSnapshot()
                val now = System.currentTimeMillis()
                if (now - lastPersistedAt >= PROGRESS_PERSIST_INTERVAL_MS) {
                    persistProgress()
                    lastPersistedAt = now
                }
                if (currentRequest != null && !resolvedPlayer.player.isPlaying &&
                    now - lastInteractionAt >= IDLE_STOP_MS
                ) {
                    stopPlayback()
                    return@launch
                }
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopPlayback()
            ACTION_PREVIOUS -> prepareAdjacent(previous = true)
            ACTION_NEXT -> prepareAdjacent(previous = false)
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    fun prepare(request: MediaPlaybackRequest) {
        currentRequest = request
        lastInteractionAt = System.currentTimeMillis()
        clipEnded = false
        val persistedPosition = if (request.resumeStoredPosition) {
            preferences.getLong(positionKey(request), 0L)
        } else {
            0L
        }
        val startPosition = MediaPlaybackPositionPolicy.prepareStartPosition(
            persistedAbsoluteMs = persistedPosition,
            clipStartMs = request.clipStartMs,
            clipEndMs = request.clipEndMs,
            requestedRelativeMs = request.startPositionMs,
        )
        val speed = preferences.getFloat(KEY_SPEED, 1f).coerceIn(0.5f, 3f)
        desiredSubtitleId = preferences.getString(trackKey(request, "subtitle"), null)
            ?: request.subtitles.firstOrNull { it.isDefault }?.id
        desiredAudioTrackId = preferences.getString(trackKey(request, "audio"), null)
            ?: request.audioTracks.firstOrNull { it.isDefault }?.id
        resolvedPlayer.prepare(request.variant, request.subtitles, request.playWhenReady)
        resolvedPlayer.player.setPlaybackSpeed(speed)
        if (startPosition > 0L) resolvedPlayer.player.seekTo(startPosition)
        updateMetadata(request)
        publishSnapshot()
        updateNotification()
    }

    fun play() {
        lastInteractionAt = System.currentTimeMillis()
        if (!MediaHelp.requestFocus(focusRequest)) return
        if (clipEnded) {
            clipEnded = false
            resolvedPlayer.player.seekTo(currentRequest?.clipStartMs ?: 0L)
        }
        resolvedPlayer.player.play()
        publishSnapshot()
        updateNotification()
    }

    fun pause() {
        lastInteractionAt = System.currentTimeMillis()
        resolvedPlayer.player.pause()
        persistProgress()
        publishSnapshot()
        updateNotification()
    }

    fun seekTo(positionMs: Long) {
        lastInteractionAt = System.currentTimeMillis()
        val request = currentRequest
        val bounded = MediaPlaybackPositionPolicy.seekAbsolute(
            relativeMs = positionMs,
            clipStartMs = request?.clipStartMs,
            clipEndMs = request?.clipEndMs,
        )
        clipEnded = false
        resolvedPlayer.player.seekTo(bounded)
        publishSnapshot()
    }

    fun setPlaybackSpeed(speed: Float) {
        lastInteractionAt = System.currentTimeMillis()
        val bounded = speed.coerceIn(0.5f, 3f)
        resolvedPlayer.player.setPlaybackSpeed(bounded)
        preferences.edit().putFloat(KEY_SPEED, bounded).apply()
        publishSnapshot()
    }

    fun selectSubtitle(trackId: String?) {
        desiredSubtitleId = trackId
        currentRequest?.let { request ->
            preferences.edit().putString(trackKey(request, "subtitle"), trackId).apply()
        }
        applyTrackSelection(C.TRACK_TYPE_TEXT, trackId)
        publishSnapshot()
    }

    fun selectAudioTrack(trackId: String?) {
        desiredAudioTrackId = trackId
        currentRequest?.let { request ->
            preferences.edit().putString(trackKey(request, "audio"), trackId).apply()
        }
        applyTrackSelection(C.TRACK_TYPE_AUDIO, trackId)
        publishSnapshot()
    }

    fun stopPlayback() {
        persistProgress()
        resolvedPlayer.player.stop()
        stopSelf()
    }

    override fun onPlayerError(error: PlaybackException) {
        publishSnapshot(error.localizedMessage ?: error.errorCodeName)
        updateNotification()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) clipEnded = true
        publishSnapshot()
        updateNotification()
    }

    override fun onTracksChanged(tracks: Tracks) {
        desiredSubtitleId?.let { applyTrackSelection(C.TRACK_TYPE_TEXT, it) }
        desiredAudioTrackId?.let { applyTrackSelection(C.TRACK_TYPE_AUDIO, it) }
        publishSnapshot()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        publishSnapshot()
        updateMediaSessionState()
        updateNotification()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeAfterFocusGain) {
                    resumeAfterFocusGain = false
                    play()
                }
                resolvedPlayer.player.volume = 1f
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> resolvedPlayer.player.volume = 0.2f
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterFocusGain = resolvedPlayer.player.isPlaying
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocusGain = false
                pause()
            }
        }
    }

    private fun publishSnapshot(errorMessage: String? = null) {
        val request = currentRequest
        val exoPlayer = resolvedPlayer.player
        val clipStart = request?.clipStartMs ?: 0L
        val rawPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
        val rawDuration = exoPlayer.duration.takeIf { it > 0L } ?: 0L
        _playbackState.value = MediaPlaybackSnapshot(
            bookUrl = request?.bookUrl.orEmpty(),
            chapterIndex = request?.chapterIndex ?: -1,
            variantId = request?.variant?.id.orEmpty(),
            isPlaying = exoPlayer.isPlaying,
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
            positionMs = MediaPlaybackPositionPolicy.relativePosition(rawPosition, clipStart),
            durationMs = MediaPlaybackPositionPolicy.relativeDuration(
                rawDurationMs = rawDuration,
                clipStartMs = request?.clipStartMs,
                clipEndMs = request?.clipEndMs,
            ),
            playbackSpeed = exoPlayer.playbackParameters.speed,
            subtitleTracks = availableTracks(C.TRACK_TYPE_TEXT),
            audioTracks = availableTracks(C.TRACK_TYPE_AUDIO),
            selectedSubtitleId = desiredSubtitleId,
            selectedAudioTrackId = desiredAudioTrackId,
            errorMessage = errorMessage,
            ended = clipEnded,
        )
    }

    private fun enforceClipEnd() {
        val end = currentRequest?.clipEndMs ?: return
        if (!clipEnded && resolvedPlayer.player.currentPosition >= end) {
            clipEnded = true
            resolvedPlayer.player.pause()
            resolvedPlayer.player.seekTo(end)
        }
    }

    private fun persistProgress() {
        val request = currentRequest ?: return
        val position = resolvedPlayer.player.currentPosition.coerceAtLeast(0L)
        preferences.edit().putLong(positionKey(request), position).apply()
    }

    private fun positionKey(request: MediaPlaybackRequest): String =
        "position:${request.bookUrl.hashCode()}:${request.chapterIndex}:${request.variant.id}"

    private fun trackKey(request: MediaPlaybackRequest, type: String): String =
        "$type:${request.bookUrl.hashCode()}:${request.chapterIndex}:${request.variant.id}"

    private fun availableTracks(trackType: Int): List<MediaPlaybackTrack> =
        resolvedPlayer.player.currentTracks.groups
            .filter { it.type == trackType }
            .flatMap { group ->
                (0 until group.length).mapNotNull { index ->
                    if (!group.isTrackSupported(index)) return@mapNotNull null
                    val format = group.getTrackFormat(index)
                    MediaPlaybackTrack(
                        id = trackId(group, index),
                        label = format.label
                            ?: format.language
                            ?: if (trackType == C.TRACK_TYPE_TEXT) "Subtitle ${index + 1}" else "Audio ${index + 1}",
                        language = format.language.orEmpty(),
                    )
                }
            }
            .distinctBy(MediaPlaybackTrack::id)

    private fun applyTrackSelection(trackType: Int, trackId: String?) {
        val builder = resolvedPlayer.player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(trackType)
            .setTrackTypeDisabled(trackType, trackId == null)
        if (trackId != null) {
            resolvedPlayer.player.currentTracks.groups
                .asSequence()
                .filter { it.type == trackType }
                .forEach { group ->
                    val index = (0 until group.length).firstOrNull { trackId(group, it) == trackId }
                    if (index != null) {
                        builder.setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, listOf(index))
                        )
                        resolvedPlayer.player.trackSelectionParameters = builder.build()
                        return
                    }
                }
        }
        resolvedPlayer.player.trackSelectionParameters = builder.build()
    }

    private fun trackId(group: Tracks.Group, index: Int): String {
        val format = group.getTrackFormat(index)
        return format.id ?: "${group.mediaTrackGroup.id}:$index"
    }

    private fun prepareAdjacent(previous: Boolean) {
        val request = currentRequest ?: return
        lifecycleScope.launch {
            val resolved = resolveBookMedia.execute(request.bookUrl, request.chapterIndex).getOrNull()
                ?: return@launch
            val target = (if (previous) resolved.previousChapterIndex else resolved.nextChapterIndex)
                ?: return@launch
            val adjacent = resolveBookMedia.execute(request.bookUrl, target).getOrNull()
                ?: return@launch
            val variant = adjacent.media.variants.firstOrNull { it.id == request.variant.id }
                ?: adjacent.media.variants.firstOrNull { !it.externalPlayerRequired }
                ?: return@launch
            prepare(
                MediaPlaybackRequest(
                    bookUrl = adjacent.bookUrl,
                    bookTitle = adjacent.bookTitle,
                    chapterIndex = adjacent.chapterIndex,
                    episodeTitle = adjacent.media.title,
                    coverUrl = adjacent.coverUrl,
                    variant = variant,
                    subtitles = adjacent.media.subtitles,
                    audioTracks = adjacent.media.audioTracks,
                    playWhenReady = true,
                    clipStartMs = adjacent.clipStartMs,
                    clipEndMs = adjacent.clipEndMs,
                )
            )
        }
    }

    private fun handleRouteConnection(connected: Boolean) {
        if (connected && resumeAfterRouteReconnect) {
            resumeAfterRouteReconnect = false
            play()
        }
    }

    private fun updateMetadata(request: MediaPlaybackRequest) {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, request.episodeTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, request.bookTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, request.coverUrl)
                .build()
        )
        updateMediaSessionState()
    }

    private fun updateMediaSessionState() {
        val exoPlayer = resolvedPlayer.player
        val state = if (exoPlayer.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, exoPlayer.currentPosition, exoPlayer.playbackParameters.speed)
                .build()
        )
    }

    private fun createNotification(): android.app.Notification {
        val playing = resolvedPlayer.player.isPlaying
        val request = currentRequest
        val builder = NotificationCompat.Builder(this, AppConst.channelIdReadAloud)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(request?.episodeTitle ?: getString(R.string.media_player_title))
            .setContentText(request?.bookTitle ?: getString(R.string.media_player_title))
            .setContentIntent(activityPendingIntent<MainActivity>("media-playback"))
            .addAction(
                R.drawable.ic_skip_previous,
                getString(R.string.previous_chapter),
                servicePendingIntent<MediaPlaybackService>(ACTION_PREVIOUS),
            )
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                getString(if (playing) R.string.pause else R.string.resume),
                servicePendingIntent<MediaPlaybackService>(if (playing) ACTION_PAUSE else ACTION_PLAY),
            )
            .addAction(
                R.drawable.ic_skip_next,
                getString(R.string.next_chapter),
                servicePendingIntent<MediaPlaybackService>(ACTION_NEXT),
            )
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.stop),
                servicePendingIntent<MediaPlaybackService>(ACTION_STOP),
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        return builder.build()
    }

    private fun updateNotification() {
        notificationManager.notify(NotificationId.MediaPlaybackService, createNotification())
    }

    override fun startForegroundNotification() {
        startForeground(NotificationId.MediaPlaybackService, createNotification())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistProgress()
        if (!resolvedPlayer.player.isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        snapshotJob?.cancel()
        persistProgress()
        if (routeReceiverRegistered) unregisterReceiver(routeReceiver)
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(this)
        resolvedPlayer.player.removeListener(this)
        resolvedPlayer.release()
        mediaSession.release()
        notificationManager.cancel(NotificationId.MediaPlaybackService)
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "io.legado.app.media.PLAY"
        const val ACTION_PAUSE = "io.legado.app.media.PAUSE"
        const val ACTION_STOP = "io.legado.app.media.STOP"
        const val ACTION_PREVIOUS = "io.legado.app.media.PREVIOUS"
        const val ACTION_NEXT = "io.legado.app.media.NEXT"
        private const val KEY_SPEED = "playback_speed"
        private const val SNAPSHOT_INTERVAL_MS = 500L
        private const val PROGRESS_PERSIST_INTERVAL_MS = 5_000L
        private const val IDLE_STOP_MS = 30_000L
    }
}
