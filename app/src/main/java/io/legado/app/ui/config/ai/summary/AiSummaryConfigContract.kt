package io.legado.app.ui.config.ai.summary

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.TranslationConstants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiSummaryModelOptionUi(
    val id: String,
    val providerName: String,
    val modelName: String,
    val modelId: String,
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
)

@Stable
data class AiSummaryRouteOptionUi(
    val id: String,
    val name: String,
    val targetCount: Int = 0,
    val maxAttempts: Int = 0,
    val primaryModelProfileId: String = "",
    val isDefault: Boolean = false,
)

@Stable
sealed interface AiSummaryConfigDialog {
    @Stable
    data class EditPrompt(val currentPrompt: String) : AiSummaryConfigDialog
}

@Stable
data class AiSummaryConfigUiState(
    val loading: Boolean = true,
    val presetId: String? = null,
    val modelProfileId: String = "",
    val routeProfileId: String = "",
    val promptTemplate: String = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
    val temperature: Float = TranslationConstants.DEFAULT_TEMPERATURE,
    val maxOutputTokens: Int = 0,
    val defaultPrompt: String = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
    val models: ImmutableList<AiSummaryModelOptionUi> = persistentListOf(),
    val routes: ImmutableList<AiSummaryRouteOptionUi> = persistentListOf(),
    val initialized: Boolean = false,
    val activeDialog: AiSummaryConfigDialog? = null
)

sealed interface AiSummaryConfigIntent {
    data class UpdatePrompt(val prompt: String) : AiSummaryConfigIntent
    data class OpenPromptDialog(val currentPrompt: String) : AiSummaryConfigIntent
    data class UpdateDialogPrompt(val prompt: String) : AiSummaryConfigIntent
    data object CloseDialog : AiSummaryConfigIntent
    data class UpdateModel(val modelProfileId: String) : AiSummaryConfigIntent
    data class UpdateRoute(val routeProfileId: String) : AiSummaryConfigIntent
    data class UpdateTemperature(val temperature: Float) : AiSummaryConfigIntent
    data class UpdateMaxOutputTokens(val tokens: Int) : AiSummaryConfigIntent
    data object ResetPrompt : AiSummaryConfigIntent
    data object Save : AiSummaryConfigIntent
}

sealed interface AiSummaryConfigEffect {
    data class ShowMessage(val message: String) : AiSummaryConfigEffect
    data object NavigateBack : AiSummaryConfigEffect
}
