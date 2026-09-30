package io.legado.app.ui.config.media

import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import io.legado.app.help.config.MediaPlayerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import splitties.init.appCtx

class MediaPlayerConfigViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        MediaPlayerConfigUiState(
            autoEnterPipOnExit = MediaPlayerConfig.autoEnterPipOnExit,
            supportsPictureInPicture = appCtx.packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE,
            ),
        )
    )
    val uiState = _uiState.asStateFlow()

    fun onIntent(intent: MediaPlayerConfigIntent) {
        when (intent) {
            is MediaPlayerConfigIntent.SetAutoEnterPipOnExit -> {
                val enabled = intent.enabled && _uiState.value.supportsPictureInPicture
                MediaPlayerConfig.autoEnterPipOnExit = enabled
                _uiState.update { it.copy(autoEnterPipOnExit = enabled) }
            }
        }
    }
}
