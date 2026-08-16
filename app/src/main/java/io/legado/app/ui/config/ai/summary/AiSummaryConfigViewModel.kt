package io.legado.app.ui.config.ai.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouterSnapshot
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class AiSummaryConfigViewModel(
    private val aiProfileGateway: AiProfileGateway,
    private val aiRouterGateway: AiRouterGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSummaryConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiSummaryConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var currentPresetName = "Default Chapter Summary"
    private var currentPresetDescription = ""
    private var currentRuntimeOptions = AiTaskRuntimeOptions()
    private var currentSortNumber = 0

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observeModels(),
                aiRouterGateway.observeSnapshot(),
            ) { providers, models, snapshot ->
                val providerNames = providers
                    .filter(AiProviderProfile::enabled)
                    .associate { it.id to it.name }
                val modelOptions = models
                    .asSequence()
                    .filter { model -> model.enabled && model.providerId in providerNames }
                    .map { model ->
                        AiSummaryModelOptionUi(
                            id = model.id,
                            providerName = providerNames[model.providerId].orEmpty(),
                            modelName = model.displayName,
                            modelId = model.modelId,
                            contextWindow = model.contextWindow,
                            maxOutputTokens = model.maxOutputTokens,
                        )
                    }
                    .toList()
                SummaryOptionSources(
                    models = modelOptions,
                    routes = snapshot.summaryRouteOptions(),
                )
            }.collect { sources ->
                _uiState.update { state ->
                    if (!state.initialized) return@update state.copy(
                        models = sources.models.toImmutableList(),
                        routes = sources.routes.toImmutableList(),
                    )
                    val selectedRoute = sources.routes.firstOrNull {
                        it.id == state.routeProfileId
                    }
                    val selectedModel = selectedRoute?.primaryModelProfileId
                        ?.takeIf { modelId -> sources.models.any { it.id == modelId } }
                        ?: state.modelProfileId.takeIf { modelId ->
                            sources.models.any { it.id == modelId }
                        }
                        ?: sources.models.firstOrNull()?.id.orEmpty()
                    state.copy(
                        models = sources.models.toImmutableList(),
                        routes = sources.routes.toImmutableList(),
                        modelProfileId = selectedModel,
                        routeProfileId = selectedRoute?.id.orEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                val config = aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
                applyLoadedPreset(config)
                _uiState.update { current ->
                    val selectedRoute = if (config == null) {
                        current.routes.firstOrNull(AiSummaryRouteOptionUi::isDefault)
                            ?: current.routes.firstOrNull()
                    } else {
                        current.routes.firstOrNull {
                            it.id == config.runtimeOptions.routeProfileId
                        }
                    }
                    current.copy(
                        loading = false,
                        presetId = config?.id,
                        modelProfileId = selectedRoute?.primaryModelProfileId
                            ?.takeIf(String::isNotBlank)
                            ?: config?.model?.id
                            ?: current.modelProfileId,
                        routeProfileId = selectedRoute?.id
                            ?: config?.runtimeOptions?.routeProfileId.orEmpty(),
                        promptTemplate = config?.promptTemplate ?: AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
                        temperature = config?.params?.temperature ?: TranslationConstants.DEFAULT_TEMPERATURE,
                        maxOutputTokens = config?.params?.maxOutputTokens ?: 0,
                        defaultPrompt = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
                        initialized = true
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        promptTemplate = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
                        temperature = TranslationConstants.DEFAULT_TEMPERATURE,
                        maxOutputTokens = 0,
                        defaultPrompt = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
                        initialized = true
                    )
                }
                appCtx.toastOnUi(
                    appCtx.getString(
                        R.string.summary_config_load_failed,
                        error.localizedMessage
                            ?: appCtx.getString(R.string.using_default_parameters)
                    )
                )
            }
        }
    }

    fun onIntent(intent: AiSummaryConfigIntent) {
        when (intent) {
            is AiSummaryConfigIntent.UpdatePrompt -> {
                _uiState.update { it.copy(promptTemplate = intent.prompt, activeDialog = null) }
            }
            is AiSummaryConfigIntent.OpenPromptDialog -> {
                _uiState.update { it.copy(activeDialog = AiSummaryConfigDialog.EditPrompt(intent.currentPrompt)) }
            }
            is AiSummaryConfigIntent.UpdateDialogPrompt -> {
                val currentDialog = _uiState.value.activeDialog as? AiSummaryConfigDialog.EditPrompt
                if (currentDialog != null) {
                    _uiState.update { it.copy(activeDialog = currentDialog.copy(currentPrompt = intent.prompt)) }
                }
            }
            is AiSummaryConfigIntent.CloseDialog -> {
                _uiState.update { it.copy(activeDialog = null) }
            }
            is AiSummaryConfigIntent.UpdateModel -> {
                _uiState.update {
                    it.copy(modelProfileId = intent.modelProfileId, routeProfileId = "")
                }
            }
            is AiSummaryConfigIntent.UpdateRoute -> {
                _uiState.update { state ->
                    val route = state.routes.firstOrNull { it.id == intent.routeProfileId }
                    state.copy(
                        routeProfileId = route?.id.orEmpty(),
                        modelProfileId = route?.primaryModelProfileId
                            ?.takeIf(String::isNotBlank)
                            ?: state.modelProfileId,
                    )
                }
            }
            is AiSummaryConfigIntent.UpdateTemperature -> {
                _uiState.update { it.copy(temperature = intent.temperature) }
            }
            is AiSummaryConfigIntent.UpdateMaxOutputTokens -> {
                _uiState.update { it.copy(maxOutputTokens = intent.tokens) }
            }
            is AiSummaryConfigIntent.ResetPrompt -> {
                _uiState.update { it.copy(promptTemplate = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY) }
                _effects.tryEmit(AiSummaryConfigEffect.ShowMessage(appCtx.getString(R.string.ai_prompt_reset_success)))
            }
            is AiSummaryConfigIntent.Save -> save()
        }
    }

    private fun save() {
        viewModelScope.launch {
            runCatching {
                val state = _uiState.value
                require(state.modelProfileId.isNotBlank()) {
                    appCtx.getString(R.string.ai_prompt_editor_model_required)
                }
                val route = state.routes.firstOrNull { it.id == state.routeProfileId }
                if (state.routeProfileId.isNotBlank()) {
                    require(route != null && route.targetCount > 0) {
                        appCtx.getString(R.string.ai_prompt_editor_combo_empty)
                    }
                }
                val savedConfig = aiProfileGateway.saveTaskPreset(
                    AiTaskPresetDraft(
                        presetId = state.presetId,
                        taskType = AiTaskType.SUMMARIZE_CHAPTER,
                        name = currentPresetName,
                        description = currentPresetDescription,
                        modelProfileId = state.modelProfileId,
                        promptTemplate = state.promptTemplate,
                        params = AiGenerationParams(
                            temperature = state.temperature,
                            maxOutputTokens = state.maxOutputTokens.takeIf { it > 0 },
                        ),
                        runtimeOptions = currentRuntimeOptions.copy(
                            routeProfileId = state.routeProfileId,
                        ),
                        enabled = true,
                        makeDefault = true,
                        sortNumber = currentSortNumber,
                    )
                )
                applyLoadedPreset(savedConfig)
                _uiState.update { current ->
                    current.copy(
                        presetId = savedConfig.id,
                        modelProfileId = savedConfig.model.id,
                        routeProfileId = savedConfig.runtimeOptions.routeProfileId,
                        promptTemplate = savedConfig.promptTemplate,
                        temperature = savedConfig.params.temperature ?: TranslationConstants.DEFAULT_TEMPERATURE,
                        maxOutputTokens = savedConfig.params.maxOutputTokens ?: 0
                    )
                }
            }.onSuccess {
                appCtx.toastOnUi(R.string.ai_config_saved_success)
                _effects.tryEmit(AiSummaryConfigEffect.NavigateBack)
            }.onFailure { error ->
                _effects.tryEmit(
                    AiSummaryConfigEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_config_save_failed)
                    )
                )
            }
        }
    }

    private fun applyLoadedPreset(config: AiTaskPresetConfig?) {
        currentPresetName = config?.name?.takeIf(String::isNotBlank)
            ?: "Default Chapter Summary"
        currentPresetDescription = config?.description.orEmpty()
        currentRuntimeOptions = config?.runtimeOptions ?: AiTaskRuntimeOptions()
    }

    private fun AiRouterSnapshot.summaryRouteOptions(): List<AiSummaryRouteOptionUi> {
        val enabledTargets = targets.filter { it.enabled }
        val targetsByRoute = enabledTargets.groupBy { it.routeProfileId }
        return routes
            .asSequence()
            .filter { route -> route.enabled && route.taskType == AiTaskType.SUMMARIZE_CHAPTER }
            .sortedWith(
                compareByDescending<AiRouteProfileConfig> { it.isDefault }
                    .thenBy { it.sortNumber }
                    .thenBy { it.name }
            )
            .map { route ->
                AiSummaryRouteOptionUi(
                    id = route.id,
                    name = route.name,
                    targetCount = targetsByRoute[route.id]?.size ?: 0,
                    maxAttempts = route.maxAttempts,
                    primaryModelProfileId = targetsByRoute[route.id]
                        ?.sortedWith(
                            compareBy<io.legado.app.domain.model.AiRouteTargetConfig> { it.priority }
                                .thenBy { it.sortNumber }
                                .thenBy { it.id }
                        )
                        ?.firstOrNull()
                        ?.modelProfileId
                        .orEmpty(),
                    isDefault = route.isDefault,
                )
            }
            .toList()
    }
}

private data class SummaryOptionSources(
    val models: List<AiSummaryModelOptionUi>,
    val routes: List<AiSummaryRouteOptionUi>,
)
