package io.legado.app.help.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import io.legado.app.domain.gateway.MediaPlaybackGateway
import io.legado.app.domain.model.MediaPlaybackRequest
import io.legado.app.domain.model.MediaPlaybackSnapshot
import io.legado.app.service.MediaPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

class MediaPlaybackConnection(
    context: Context,
) : MediaPlaybackGateway, ServiceConnection {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serviceState = MutableStateFlow<MediaPlaybackService?>(null)
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()
    private val _playbackState = MutableStateFlow(MediaPlaybackSnapshot())
    override val playbackState: StateFlow<MediaPlaybackSnapshot> = _playbackState.asStateFlow()
    private var bound = false
    private var serviceCollectionJob: Job? = null

    fun connect() {
        // Binding used to start the foreground Media3 service as soon as the route was opened,
        // before the chapter resolver had proved that a playable URL existed.  A malformed video
        // source could therefore crash the service while merely opening the screen.  The service
        // is now admitted lazily by prepare(), after resolution and validation.
    }

    fun disconnect() {
        if (!bound) return
        runCatching { appContext.unbindService(this) }
        bound = false
        serviceState.value = null
        _player.value = null
        serviceCollectionJob?.cancel()
        serviceCollectionJob = null
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val service = (binder as? MediaPlaybackService.LocalBinder)?.service ?: run {
            bound = false
            return
        }
        serviceState.value = service
        _player.value = service.player
        serviceCollectionJob?.cancel()
        serviceCollectionJob = scope.launch {
            service.playbackState.collect { snapshot ->
                if (serviceState.value === service) _playbackState.value = snapshot
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        serviceState.value = null
        _player.value = null
        serviceCollectionJob?.cancel()
        serviceCollectionJob = null
        bound = false
    }

    override suspend fun prepare(request: MediaPlaybackRequest) = onService { it.prepare(request) }

    override suspend fun play() = onService(MediaPlaybackService::play)

    override suspend fun pause() = onService(MediaPlaybackService::pause)

    override suspend fun seekTo(positionMs: Long) = onService { it.seekTo(positionMs) }

    override suspend fun setPlaybackSpeed(speed: Float) = onService { it.setPlaybackSpeed(speed) }

    override suspend fun selectSubtitle(trackId: String?) = onService { it.selectSubtitle(trackId) }

    override suspend fun selectAudioTrack(trackId: String?) = onService { it.selectAudioTrack(trackId) }

    override suspend fun stop() = onService(MediaPlaybackService::stopPlayback)

    private suspend fun onService(block: (MediaPlaybackService) -> Unit) {
        val service = withContext(Dispatchers.Main.immediate) {
            ensureBound()
            withTimeout(SERVICE_CONNECT_TIMEOUT_MS) {
                serviceState.filterNotNull().first()
            }
        }
        withContext(Dispatchers.Main.immediate) { block(service) }
    }

    private fun ensureBound() {
        if (bound) return
        val intent = Intent(appContext, MediaPlaybackService::class.java)
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
            .getOrElse { throw IllegalStateException("Không thể khởi động trình phát video", it) }
        bound = appContext.bindService(intent, this, Context.BIND_AUTO_CREATE)
        if (!bound) {
            appContext.stopService(intent)
            throw IllegalStateException("Không thể kết nối trình phát video")
        }
    }

    private companion object {
        const val SERVICE_CONNECT_TIMEOUT_MS = 8_000L
    }
}
