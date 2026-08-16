package io.legado.app.ui.config.ai.prompt

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiPromptCatalogTemplate
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.TranslationConstants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiPromptModelOptionUi(
    val id: String,
    val providerName: String,
    val modelName: String,
    val modelId: String,
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
)

@Stable
data class AiPromptRouteOptionUi(
    val id: String,
    val taskType: String,
    val name: String,
    val targetCount: Int = 0,
    val maxAttempts: Int = 0,
    val primaryModelProfileId: String = "",
    val isDefault: Boolean = false,
)

@Stable
data class AiPromptPresetItemUi(
    val id: String,
    val taskType: String,
    val name: String,
    val description: String,
    val modelProfileId: String,
    val modelLabel: String,
    val routeProfileId: String,
    val routeLabel: String,
    val promptTemplate: String,
    val paramsJson: String?,
    val runtimeOptionsJson: String?,
    val enabled: Boolean,
    val isDefault: Boolean,
    val sortNumber: Int,
)

@Stable
data class AiPromptCatalogItemUi(
    val id: String,
    val taskType: String,
    val name: String,
    val description: String,
    val prompt: String,
)

@Stable
data class AiPromptPresetEditorUi(
    val presetId: String? = null,
    val taskType: String,
    val name: String = "",
    val description: String = "",
    val modelProfileId: String = "",
    val routeProfileId: String = "",
    val promptTemplate: String = "",
    val temperature: String = TranslationConstants.DEFAULT_TEMPERATURE.toString(),
    val topP: String = "",
    val topK: String = "",
    val repetitionPenalty: String = "",
    val maxOutputTokens: String = "",
    val reasoningLevel: AiReasoningLevel = AiReasoningLevel.AUTO,
    val targetLanguage: String = "vi",
    val maxInputChars: String = "1000",
    val concurrentRequests: String = "1",
    val retryCount: String = "2",
    val enabled: Boolean = true,
    val makeDefault: Boolean = false,
    val sortNumber: Int = 0,
    val errorMessage: String? = null,
)

@Stable
sealed interface AiPromptEditorDialog {
    @Stable
    data class Delete(val item: AiPromptPresetItemUi) : AiPromptEditorDialog

    @Stable
    data class Preview(
        val title: String,
        val content: String,
    ) : AiPromptEditorDialog

    data object DiscardEditor : AiPromptEditorDialog
}

@Stable
data class AiPromptEditorUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val transferring: Boolean = false,
    val selectedTaskType: String,
    val supportedTaskTypes: ImmutableList<String> = persistentListOf(),
    val models: ImmutableList<AiPromptModelOptionUi> = persistentListOf(),
    val routes: ImmutableList<AiPromptRouteOptionUi> = persistentListOf(),
    val presets: ImmutableList<AiPromptPresetItemUi> = persistentListOf(),
    val catalog: ImmutableList<AiPromptCatalogItemUi> = persistentListOf(),
    val editor: AiPromptPresetEditorUi? = null,
    val hasUnsavedEditorChanges: Boolean = false,
    val showCatalog: Boolean = false,
    val activeDialog: AiPromptEditorDialog? = null,
)

sealed interface AiPromptEditorIntent {
    data class SelectTask(val taskType: String) : AiPromptEditorIntent
    data object AddPreset : AiPromptEditorIntent
    data class EditPreset(val item: AiPromptPresetItemUi) : AiPromptEditorIntent
    data object DuplicateCurrentEditor : AiPromptEditorIntent
    data class SetDefault(val presetId: String) : AiPromptEditorIntent
    data class RequestDelete(val item: AiPromptPresetItemUi) : AiPromptEditorIntent
    data object ConfirmDelete : AiPromptEditorIntent
    data object ConfirmDiscardEditor : AiPromptEditorIntent
    data object CloseDialog : AiPromptEditorIntent
    data object OpenCatalog : AiPromptEditorIntent
    data object CloseCatalog : AiPromptEditorIntent
    data class ApplyCatalog(val template: AiPromptCatalogItemUi) : AiPromptEditorIntent
    data object CloseEditor : AiPromptEditorIntent
    data class UpdateTask(val value: String) : AiPromptEditorIntent
    data class UpdateName(val value: String) : AiPromptEditorIntent
    data class UpdateDescription(val value: String) : AiPromptEditorIntent
    data class UpdateModel(val value: String) : AiPromptEditorIntent
    data class UpdateRoute(val value: String) : AiPromptEditorIntent
    data class SelectModelOrRoute(val value: String) : AiPromptEditorIntent
    data class UpdatePrompt(val value: String) : AiPromptEditorIntent
    data class UpdateTemperature(val value: String) : AiPromptEditorIntent
    data class UpdateTopP(val value: String) : AiPromptEditorIntent
    data class UpdateTopK(val value: String) : AiPromptEditorIntent
    data class UpdateRepetitionPenalty(val value: String) : AiPromptEditorIntent
    data class UpdateMaxOutputTokens(val value: String) : AiPromptEditorIntent
    data class UpdateReasoning(val value: AiReasoningLevel) : AiPromptEditorIntent
    data class UpdateTargetLanguage(val value: String) : AiPromptEditorIntent
    data class UpdateMaxInputChars(val value: String) : AiPromptEditorIntent
    data class UpdateConcurrentRequests(val value: String) : AiPromptEditorIntent
    data class UpdateRetryCount(val value: String) : AiPromptEditorIntent
    data class UpdateEnabled(val value: Boolean) : AiPromptEditorIntent
    data class UpdateMakeDefault(val value: Boolean) : AiPromptEditorIntent
    data object ResetEditor : AiPromptEditorIntent
    data object PreviewEffectivePrompt : AiPromptEditorIntent
    data object SavePreset : AiPromptEditorIntent
    data object RequestImport : AiPromptEditorIntent
    data class ImportJson(val content: String) : AiPromptEditorIntent
    data object RequestExport : AiPromptEditorIntent
    data class ExportFinished(val succeeded: Boolean) : AiPromptEditorIntent
    data object TransferCancelled : AiPromptEditorIntent
}

internal const val AI_PROMPT_SELECTION_DEFAULT = "router:default"
internal const val AI_PROMPT_SELECTION_ROUTE_PREFIX = "router:route:"
internal const val AI_PROMPT_SELECTION_MODEL_PREFIX = "router:model:"

@Stable
internal sealed interface AiPromptTargetSelection {
    data object Default : AiPromptTargetSelection
    data class Route(val routeProfileId: String) : AiPromptTargetSelection
    data class Model(val modelProfileId: String) : AiPromptTargetSelection
}

internal enum class AiPromptTargetValidationError {
    MODEL_REQUIRED,
    ROUTE_INVALID,
    ROUTE_EMPTY,
}

internal fun decodeAiPromptTargetSelection(value: String): AiPromptTargetSelection? = when {
    value == AI_PROMPT_SELECTION_DEFAULT -> AiPromptTargetSelection.Default
    value.startsWith(AI_PROMPT_SELECTION_ROUTE_PREFIX) -> value
        .removePrefix(AI_PROMPT_SELECTION_ROUTE_PREFIX)
        .takeIf(String::isNotBlank)
        ?.let(AiPromptTargetSelection::Route)
    value.startsWith(AI_PROMPT_SELECTION_MODEL_PREFIX) -> value
        .removePrefix(AI_PROMPT_SELECTION_MODEL_PREFIX)
        .takeIf(String::isNotBlank)
        ?.let(AiPromptTargetSelection::Model)
    else -> null
}

internal fun validateAiPromptTargetSelection(
    editor: AiPromptPresetEditorUi,
    models: List<AiPromptModelOptionUi>,
    routes: List<AiPromptRouteOptionUi>,
): AiPromptTargetValidationError? {
    if (editor.routeProfileId.isNotBlank()) {
        val route = routes.firstOrNull {
            it.id == editor.routeProfileId && it.taskType == editor.taskType
        } ?: return AiPromptTargetValidationError.ROUTE_INVALID
        if (route.targetCount <= 0 || route.primaryModelProfileId.isBlank()) {
            return AiPromptTargetValidationError.ROUTE_EMPTY
        }
        return null
    }
    if (editor.modelProfileId.isBlank() || models.none { it.id == editor.modelProfileId }) {
        return AiPromptTargetValidationError.MODEL_REQUIRED
    }
    return null
}

sealed interface AiPromptEditorEffect {
    data class ShowMessage(val message: String) : AiPromptEditorEffect
    data object OpenImportFile : AiPromptEditorEffect
    data class CreateExportFile(
        val fileName: String,
        val content: String,
    ) : AiPromptEditorEffect
}

internal fun AiPromptCatalogTemplate.toPromptCatalogItemUi() = AiPromptCatalogItemUi(
    id = id,
    taskType = taskType,
    name = name,
    description = description,
    prompt = prompt,
)
