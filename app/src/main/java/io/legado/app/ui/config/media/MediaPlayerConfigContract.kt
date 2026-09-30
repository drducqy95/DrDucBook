package io.legado.app.ui.config.media

import androidx.compose.runtime.Stable

@Stable
data class MediaPlayerConfigUiState(
    val autoEnterPipOnExit: Boolean = false,
    val supportsPictureInPicture: Boolean = false,
)

sealed interface MediaPlayerConfigIntent {
    data class SetAutoEnterPipOnExit(val enabled: Boolean) : MediaPlayerConfigIntent
}
