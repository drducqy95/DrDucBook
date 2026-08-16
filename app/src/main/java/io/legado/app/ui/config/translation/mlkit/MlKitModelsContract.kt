package io.legado.app.ui.config.translation.mlkit

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class MlKitLanguageModelUi(
    val languageTag: String,
    val displayName: String,
    val downloaded: Boolean,
    val busy: Boolean = false,
)

@Stable
data class MlKitModelsUiState(
    val loading: Boolean = false,
    val batchRunning: Boolean = false,
    val models: ImmutableList<MlKitLanguageModelUi> = persistentListOf(),
    val dialog: MlKitModelsDialog? = null,
)

sealed interface MlKitModelsDialog {
    data object DownloadAll : MlKitModelsDialog
    data object DeleteAll : MlKitModelsDialog
}

sealed interface MlKitModelsIntent {
    data object Refresh : MlKitModelsIntent
    data class Download(val languageTag: String) : MlKitModelsIntent
    data class Delete(val languageTag: String) : MlKitModelsIntent
    data object RequestDownloadAll : MlKitModelsIntent
    data object RequestDeleteAll : MlKitModelsIntent
    data object DismissDialog : MlKitModelsIntent
    data object ConfirmDialog : MlKitModelsIntent
}

sealed interface MlKitModelsEffect {
    data class ShowMessage(val message: String) : MlKitModelsEffect
}
