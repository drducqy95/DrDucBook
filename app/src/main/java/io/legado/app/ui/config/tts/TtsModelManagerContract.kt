package io.legado.app.ui.config.tts

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.domain.model.LocalTtsImportProgress
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class TtsVoiceItemUi(
    val id: Int,
    val name: String,
)

@Stable
data class TtsModelItemUi(
    val id: String,
    val name: String,
    val engine: String,
    val language: String,
    val sampleRate: Int,
    val voices: ImmutableList<TtsVoiceItemUi>,
    val selectedVoiceId: Int,
    val isDefault: Boolean,
    val attribution: String,
    val license: String,
    val checksum: String,
    val sizeBytes: Long,
    val runtimeReady: Boolean,
)

@Stable
data class TtsModelManagerUiState(
    val loading: Boolean = false,
    val importing: Boolean = false,
    val importProgress: LocalTtsImportProgress? = null,
    val testingModelId: String? = null,
    val deletingModelId: String? = null,
    val models: ImmutableList<TtsModelItemUi> = persistentListOf(),
)

sealed interface TtsModelManagerIntent {
    data object Refresh : TtsModelManagerIntent
    data object PickImportFile : TtsModelManagerIntent
    data class ImportFile(val uri: Uri) : TtsModelManagerIntent
    data object CancelImport : TtsModelManagerIntent
    data object OpenCatalog : TtsModelManagerIntent
    data class SelectVoice(val modelId: String, val voiceId: Int) : TtsModelManagerIntent
    data class TestModel(val modelId: String) : TtsModelManagerIntent
    data class SetDefault(val modelId: String) : TtsModelManagerIntent
    data class RequestDelete(val modelId: String) : TtsModelManagerIntent
    data object ConfirmDelete : TtsModelManagerIntent
    data object DismissDelete : TtsModelManagerIntent
}

sealed interface TtsModelManagerEffect {
    data object PickImportFile : TtsModelManagerEffect
    data class OpenUrl(val url: String) : TtsModelManagerEffect
    data class ShowMessage(val message: String) : TtsModelManagerEffect
}
