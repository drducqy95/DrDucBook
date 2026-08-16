package io.legado.app.domain.gateway

import io.legado.app.domain.model.MediaPlaybackRequest
import io.legado.app.domain.model.MediaPlaybackSnapshot
import kotlinx.coroutines.flow.StateFlow

interface MediaPlaybackGateway {
    val playbackState: StateFlow<MediaPlaybackSnapshot>

    suspend fun prepare(request: MediaPlaybackRequest)

    suspend fun play()

    suspend fun pause()

    suspend fun seekTo(positionMs: Long)

    suspend fun setPlaybackSpeed(speed: Float)

    suspend fun selectSubtitle(trackId: String?)

    suspend fun selectAudioTrack(trackId: String?)

    suspend fun stop()
}
