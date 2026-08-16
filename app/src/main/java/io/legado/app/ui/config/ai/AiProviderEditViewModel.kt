package io.legado.app.ui.config.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiCredentialDraft
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiProviderPresets
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiRouteProfileDraft
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiRouteTargetDraft
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.ExternalAssetCatalog
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first as flowFirst
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class AiProviderEditViewModel(
    private val initialProviderId: String?,
    private val aiProfileGateway: AiProfileGateway,
    private val aiRouterGateway: AiRouterGateway,
    private val aiTextGateway: AiTextGateway,
    private val localAiEngineGateway: LocalAiEngineGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiProviderEditUiState(
            providerPresets = AiProviderPresets.items.map {
                AiProviderPresetUi(
                    id = it.id,
                    name = it.name,
                    protocol = it.protocol,
                    baseUrl = it.baseUrl,
                    modelsUrl = it.modelsUrl,
                    modelName = it.modelName,
                    modelId = it.modelId
                )
            }.toImmutableList(),
            providerId = initialProviderId
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiProviderEditEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observeModels(),
                aiProfileGateway.observePresets(),
            ) { providers, models, presets ->
                Triple(providers, models, presets)
            }.collect { (providers, models, presets) ->
                val providerId = _uiState.value.providerId ?: initialProviderId
                val resolvedApiKeys = providerId
                    ?.let { aiProfileGateway.getProviderApiKey(it) }
                    .orEmpty()
                _uiState.update { current ->
                    val provider = providerId?.let { id -> providers.firstOrNull { it.id == id } }
                    val providerModels = models
                        .filter { it.providerId == providerId }
                        .map { model ->
                            val params = parseParams(model.defaultParamsJson)
                            AiProviderModelUi(
                                modelProfileId = model.id,
                                providerId = model.providerId,
                                modelName = model.displayName,
                                modelId = model.modelId,
                                contextWindow = model.contextWindow,
                                maxOutputTokens = model.maxOutputTokens,
                                temperature = params.temperature ?: TranslationConstants.DEFAULT_TEMPERATURE
                            )
                        }
                        .toImmutableList()
                    val fallbackModels = selectFallbackModels(providerModels).toImmutableList()
                    val providerNames = providers.associate { it.id to it.name }
                    val modelsById = models.associateBy { it.id }
                    val translationPresets = presets
                        .filter { it.taskType == AiTaskType.TRANSLATE_CHAPTER && it.enabled }
                    val translationPrompts = translationPresets.map { preset ->
                        val model = modelsById[preset.modelProfileId]
                        val providerName = model?.providerId?.let(providerNames::get).orEmpty()
                        val modelLabel = listOf(providerName, model?.displayName.orEmpty())
                            .filter(String::isNotBlank)
                            .joinToString(" · ")
                            .ifBlank { preset.modelProfileId }
                        AiProviderPromptUi(
                            id = preset.id,
                            name = preset.name,
                            modelLabel = modelLabel,
                        )
                    }.toImmutableList()
                    val selectedTranslationPromptId = translationPresets
                        .firstOrNull { it.isDefault }
                        ?.id
                        ?: translationPresets.firstOrNull()?.id.orEmpty()
                    if (current.initialized) {
                        current.copy(
                            providerModels = providerModels,
                            fallbackModels = fallbackModels,
                            translationPrompts = translationPrompts,
                            selectedTranslationPromptId = selectedTranslationPromptId,
                        )
                    } else {
                        current.copy(
                            providerId = provider?.id ?: current.providerId,
                            providerName = provider?.name ?: current.providerName,
                            protocol = provider?.protocol ?: current.protocol,
                            baseUrl = provider?.baseUrl.orEmpty(),
                            modelsUrl = provider?.modelsUrl.orEmpty(),
                            apiKey = resolvedApiKeys.normalizeKeyInput(),
                            authType = provider?.authType ?: current.authType,
                            headers = parseStringMap(provider?.headersJson),
                            chatPath = provider?.chatPath ?: current.chatPath,
                            responsesPath = provider?.responsesPath ?: current.responsesPath,
                            messagesPath = provider?.messagesPath ?: current.messagesPath,
                            modelsPath = provider?.modelsPath,
                            customHeaders = parseStringMap(provider?.customHeadersJson),
                            providerModels = providerModels,
                            fallbackModels = fallbackModels,
                            translationPrompts = translationPrompts,
                            selectedTranslationPromptId = selectedTranslationPromptId,
                            initialized = true
                        )
                    }
                }
            }
        }
    }

    fun onIntent(intent: AiProviderEditIntent) {
        when (intent) {
            is AiProviderEditIntent.ApplyProviderPreset -> applyProviderPreset(intent.id)
            is AiProviderEditIntent.UpdateProviderName -> _uiState.update { it.copy(providerName = intent.value) }
            is AiProviderEditIntent.UpdateProtocol -> _uiState.update {
                it.copy(protocol = intent.value, selectedProviderPresetId = "", fetchedModels = emptyList<AiFetchedModelUi>().toImmutableList())
            }
            is AiProviderEditIntent.UpdateBaseUrl -> _uiState.update { it.copy(baseUrl = intent.value, selectedProviderPresetId = "") }
            is AiProviderEditIntent.UpdateModelsUrl -> _uiState.update { it.copy(modelsUrl = intent.value, selectedProviderPresetId = "") }
            is AiProviderEditIntent.UpdateApiKey -> _uiState.update { it.copy(apiKey = intent.value) }
            is AiProviderEditIntent.SelectTranslationPrompt -> {
                selectTranslationPrompt(intent.presetId)
            }
            AiProviderEditIntent.AddModel -> _uiState.update {
                it.copy(editingModel = AiProviderModelEditorUi(temperature = TranslationConstants.DEFAULT_TEMPERATURE.toString()))
            }
            is AiProviderEditIntent.EditModel -> editModel(intent.modelProfileId)
            AiProviderEditIntent.DismissModelEditor -> _uiState.update { it.copy(editingModel = null) }
            is AiProviderEditIntent.UpdateEditingModelName -> updateEditingModel { copy(modelName = intent.value) }
            is AiProviderEditIntent.UpdateEditingModelId -> updateEditingModel { copy(modelId = intent.value) }
            is AiProviderEditIntent.UpdateEditingContextWindow -> updateEditingModel { copy(contextWindow = intent.value) }
            is AiProviderEditIntent.UpdateEditingMaxOutputTokens -> updateEditingModel { copy(maxOutputTokens = intent.value) }
            is AiProviderEditIntent.UpdateEditingTemperature -> updateEditingModel { copy(temperature = intent.value) }
            AiProviderEditIntent.SaveEditingModel -> saveEditingModel()
            AiProviderEditIntent.TestConnection -> testConnection()
            AiProviderEditIntent.SaveProvider -> saveProvider()
            AiProviderEditIntent.SyncModels -> syncModels()
            AiProviderEditIntent.ChooseLocalModel -> {
                _effects.tryEmit(AiProviderEditEffect.OpenLocalModelPicker)
            }
            AiProviderEditIntent.OpenLocalModelCatalog -> {
                _effects.tryEmit(AiProviderEditEffect.OpenUrl(ExternalAssetCatalog.ggufFolderUrl))
            }
            is AiProviderEditIntent.LocalModelSelected -> importLocalModel(intent.uri)
            AiProviderEditIntent.DeleteProvider -> deleteProvider()
            is AiProviderEditIntent.DeleteModel -> deleteModel(intent.modelProfileId)
        }
    }

    private fun applyProviderPreset(id: String) {
        if (id.isBlank()) {
            _uiState.update { it.copy(selectedProviderPresetId = "") }
            return
        }
        val preset = AiProviderPresets.items.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                selectedProviderPresetId = preset.id,
                providerName = preset.name,
                protocol = preset.protocol,
                baseUrl = preset.baseUrl,
                modelsUrl = preset.modelsUrl,
                fetchedModels = emptyList<AiFetchedModelUi>().toImmutableList()
            )
        }
    }

    private fun selectTranslationPrompt(presetId: String) {
        if (presetId.isBlank() || presetId == _uiState.value.selectedTranslationPromptId) return
        viewModelScope.launch {
            runCatching { aiProfileGateway.setDefaultTaskPreset(presetId) }
                .onSuccess {
                    _effects.tryEmit(
                        AiProviderEditEffect.ShowMessage(
                            appCtx.getString(R.string.ai_prompt_editor_activated)
                        )
                    )
                }
                .onFailure { error ->
                    _effects.tryEmit(
                        AiProviderEditEffect.ShowMessage(
                            error.message ?: appCtx.getString(R.string.ai_config_save_failed)
                        )
                    )
                }
        }
    }

    private fun editModel(modelProfileId: String) {
        val model = _uiState.value.providerModels.firstOrNull { it.modelProfileId == modelProfileId } ?: return
        _uiState.update {
            it.copy(
                editingModel = AiProviderModelEditorUi(
                    modelProfileId = model.modelProfileId,
                    modelName = model.modelName,
                    modelId = model.modelId,
                    contextWindow = model.contextWindow.takeIf { value -> value > 0 }?.toString().orEmpty(),
                    maxOutputTokens = model.maxOutputTokens.takeIf { value -> value > 0 }?.toString().orEmpty(),
                    temperature = model.temperature.toString()
                )
            )
        }
    }

    private fun updateEditingModel(update: AiProviderModelEditorUi.() -> AiProviderModelEditorUi) {
        _uiState.update {
            it.copy(editingModel = it.editingModel?.update())
        }
    }

    private fun saveEditingModel() {
        viewModelScope.launch {
            val editor = _uiState.value.editingModel ?: return@launch
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val providerId = _uiState.value.providerId
                    ?: aiProfileGateway.saveProvider(_uiState.value.toDraft()).id
                val model = aiProfileGateway.saveModel(
                    AiModelDraft(
                        modelProfileId = editor.modelProfileId,
                        providerId = providerId,
                        modelName = editor.modelName.trim(),
                        modelId = editor.modelId.trim(),
                        contextWindow = editor.contextWindow.toIntOrNull() ?: 0,
                        maxOutputTokens = editor.maxOutputTokens.toIntOrNull() ?: 0,
                        temperature = editor.temperature.toFloatOrNull()
                            ?: TranslationConstants.DEFAULT_TEMPERATURE
                    )
                )
                aiProfileGateway.setDefaultModel(model.id)
                model
            }.onSuccess { model ->
                _uiState.update { it.copy(editingModel = null, providerId = model.providerId) }
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(appCtx.getString(R.string.ai_model_saved))
                )
            }.onFailure { error ->
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_model_save_failed)
                    )
                )
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun saveProvider() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val state = _uiState.value
                val provider = aiProfileGateway.saveProvider(state.toDraft())
                provisionTranslationFallback(
                    providerId = provider.id,
                    providerName = provider.name,
                    models = state.providerModels,
                    rawKeys = state.apiKey,
                )
                provider
            }.onSuccess { provider ->
                _uiState.update { it.copy(providerId = provider.id) }
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(appCtx.getString(R.string.ai_provider_saved))
                )
                _effects.tryEmit(AiProviderEditEffect.NavigateBack)
            }.onFailure { error ->
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_provider_save_failed)
                    )
                )
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun testConnection() {
        if (_uiState.value.isTesting || _uiState.value.isSaving || _uiState.value.isFetchingModels) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            try {
                val state = _uiState.value
                val provider = state.toProviderConfig()
                val fetchedModels = aiTextGateway.fetchModels(provider).getOrElse { error ->
                    if (state.providerModels.isEmpty()) throw error
                    emptyList()
                }
                val selectedModel = selectPreferredModel(
                    fetchedModels.ifEmpty {
                        state.providerModels.map {
                            AiAvailableModel(
                                id = it.modelId,
                                name = it.modelName,
                                contextWindow = it.contextWindow,
                                maxOutputTokens = it.maxOutputTokens,
                            )
                        }
                    }
                ) ?: error("Chưa có model để kiểm tra sinh nội dung")
                val response = aiTextGateway.generate(
                    AiGenerateRequest(
                        model = AiModelConfig(
                            id = "connection_test_model",
                            provider = provider,
                            displayName = selectedModel.name,
                            modelId = selectedModel.id,
                            contextWindow = selectedModel.contextWindow,
                            maxOutputTokens = selectedModel.maxOutputTokens,
                        ),
                        messages = listOf(
                            AiMessage(
                                AiMessageRole.SYSTEM,
                                "Trả lời chính xác một từ OK, không giải thích.",
                            ),
                            AiMessage(AiMessageRole.USER, "Kiểm tra kết nối."),
                        ),
                        params = AiGenerationParams(
                            temperature = 0f,
                            maxOutputTokens = 256,
                            reasoningLevel = AiReasoningLevel.OFF,
                        ),
                    )
                ).getOrThrow()
                require(response.text.isNotBlank()) { "Provider không sinh được nội dung" }
                val count = fetchedModels.size
                val message = if (count == 0) {
                    "Kết nối và sinh nội dung thành công · ${selectedModel.name}"
                } else {
                    "Kết nối và sinh nội dung thành công · ${selectedModel.name} · $count model"
                }
                _effects.tryEmit(AiProviderEditEffect.ShowMessage(message))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val errorMsg = error.message
                val failMsg = appCtx.getString(R.string.ai_test_failed)
                val message = if (errorMsg.isNullOrBlank()) failMsg else "$failMsg: $errorMsg"
                _effects.tryEmit(AiProviderEditEffect.ShowMessage(message))
            } finally {
                _uiState.update { it.copy(isTesting = false) }
            }
        }
    }

    private fun syncModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isFetchingModels = true) }
            runCatching {
                aiProfileGateway.saveProvider(_uiState.value.toDraft())
            }.onSuccess { provider ->
                _uiState.update { it.copy(providerId = provider.id) }
                val state = _uiState.value
                runCatching {
                    aiTextGateway.fetchModels(state.toProviderConfig(provider.id)).getOrThrow()
                }.onSuccess { models ->
                    val importedModels = aiProfileGateway.importProviderModels(provider.id, models)
                    selectPreferredModel(models)?.let { preferred ->
                        importedModels.firstOrNull { it.modelId == preferred.id }?.let {
                            aiProfileGateway.setDefaultModel(it.id)
                        }
                    }
                    provisionTranslationFallback(
                        providerId = provider.id,
                        providerName = provider.name,
                        models = importedModels.map { model ->
                            AiProviderModelUi(
                                modelProfileId = model.id,
                                providerId = model.providerId,
                                modelName = model.displayName,
                                modelId = model.modelId,
                                contextWindow = model.contextWindow,
                                maxOutputTokens = model.maxOutputTokens,
                            )
                        },
                        rawKeys = state.apiKey,
                    )
                    applyFetchedModels(provider.id, models)
                }.onFailure { error ->
                    _effects.tryEmit(
                        AiProviderEditEffect.ShowMessage(
                            error.message ?: appCtx.getString(R.string.ai_provider_saved_models_failed)
                        )
                    )
                }
            }.onFailure { error ->
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_provider_save_failed)
                    )
                )
            }
            _uiState.update { it.copy(isSaving = false, isFetchingModels = false) }
        }
    }

    private var importJob: Job? = null

    private fun importLocalModel(uri: String) {
        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val metadata = localAiEngineGateway.importModel(uri).getOrThrow()
                val provider = aiProfileGateway.saveProvider(
                    AiProviderDraft(
                        providerId = _uiState.value.providerId,
                        providerName = "Local AI · ${metadata.name}",
                        protocol = AiProtocol.LOCAL_GGUF,
                        baseUrl = metadata.path,
                        modelsUrl = null,
                        apiKey = "",
                    )
                )
                val model = aiProfileGateway.saveModel(
                    AiModelDraft(
                        providerId = provider.id,
                        modelName = metadata.name,
                        modelId = metadata.path,
                        contextWindow = metadata.contextWindow,
                        maxOutputTokens = metadata.contextWindow,
                        temperature = 0.7f,
                    )
                )
                aiProfileGateway.saveTaskPreset(
                    AiTaskPresetDraft(
                        taskType = AiTaskType.TRANSLATE_CHAPTER,
                        name = "Dịch local · ${metadata.name}",
                        modelProfileId = model.id,
                        promptTemplate = TranslationConstants.DEFAULT_PROMPT,
                        params = AiGenerationParams(
                            temperature = 0.7f,
                            maxOutputTokens = metadata.contextWindow,
                            topP = 0.6f,
                            topK = 20,
                            repetitionPenalty = 1.05f,
                        ),
                        runtimeOptions = AiTaskRuntimeOptions(
                            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                            maxInputChars = metadata.runtimeProfile.preferredChunkChars,
                            concurrentRequests = 1,
                            retryCount = 2,
                        ),
                        enabled = true,
                        makeDefault = true,
                    )
                )
                metadata to provider
            }.onSuccess { (metadata, provider) ->
                _uiState.update {
                    it.copy(
                        providerId = provider.id,
                        providerName = provider.name,
                        protocol = AiProtocol.LOCAL_GGUF,
                        baseUrl = metadata.path,
                        modelsUrl = "",
                        apiKey = "",
                        selectedProviderPresetId = "local_hy_mt2",
                    )
                }
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(appCtx.getString(R.string.ai_local_model_imported))
                )
            }.onFailure { error ->
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(
                        appCtx.getString(
                            R.string.ai_local_model_import_failed,
                            error.message ?: "Unknown error",
                        )
                    )
                )
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun deleteProvider() {
        val providerId = _uiState.value.providerId ?: return
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.deleteProvider(providerId)
            }.onSuccess {
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(appCtx.getString(R.string.ai_provider_deleted))
                )
                _effects.tryEmit(AiProviderEditEffect.NavigateBackAfterDelete)
            }.onFailure { error ->
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_provider_delete_failed)
                    )
                )
            }
        }
    }

    private fun deleteModel(modelProfileId: String) {
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.deleteModel(modelProfileId)
            }.onSuccess {
                _uiState.update { it.copy(editingModel = null) }
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(appCtx.getString(R.string.ai_model_deleted))
                )
            }.onFailure { error ->
                _effects.tryEmit(
                    AiProviderEditEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_model_delete_failed)
                    )
                )
            }
        }
    }

    private fun applyFetchedModels(providerId: String, models: List<AiAvailableModel>) {
        val options = models.map {
            AiFetchedModelUi(
                id = it.id,
                name = it.name,
                contextWindow = it.contextWindow,
                maxOutputTokens = it.maxOutputTokens
            )
        }.toImmutableList()
        _uiState.update {
            it.copy(
                providerId = providerId,
                fetchedModels = options
            )
        }
        val message = if (options.isEmpty()) {
            appCtx.getString(R.string.ai_models_none_found)
        } else {
            appCtx.getString(R.string.ai_models_fetched_saved, options.size)
        }
        _effects.tryEmit(AiProviderEditEffect.ShowMessage(message))
    }

    private fun AiProviderEditUiState.toDraft(): AiProviderDraft {
        return AiProviderDraft(
            providerId = providerId,
            providerName = providerName,
            protocol = protocol,
            baseUrl = baseUrl,
            modelsUrl = modelsUrl,
            apiKey = apiKey.toStoredKeyList(),
            authType = authType,
            headers = headers,
            chatPath = chatPath,
            responsesPath = responsesPath,
            messagesPath = messagesPath,
            modelsPath = modelsPath,
            customHeaders = customHeaders,
        )
    }

    private fun AiProviderEditUiState.toProviderConfig(id: String = providerId ?: "test_connection_id"): AiProviderConfig {
        return AiProviderConfig(
            id = id,
            name = providerName,
            protocol = protocol,
            baseUrl = baseUrl,
            apiKey = apiKey.toStoredKeyList(),
            authType = authType,
            modelsUrl = modelsUrl.ifBlank { null },
            headers = headers,
            chatPath = chatPath,
            responsesPath = responsesPath,
            messagesPath = messagesPath,
            modelsPath = modelsPath,
            customHeaders = customHeaders,
        )
    }

    private suspend fun provisionTranslationFallback(
        providerId: String,
        providerName: String,
        models: List<AiProviderModelUi>,
        rawKeys: String,
    ) {
        if (models.isEmpty()) return
        val before = aiRouterGateway.observeSnapshot().flowFirst()
        val existingCredentials = before.credentials.filter { it.providerId == providerId }
        val submittedKeys = rawKeys.parseKeyList()
        val savedCredentials = if (submittedKeys.isNotEmpty()) {
            submittedKeys.mapIndexed { index, key ->
                val label = "API key ${index + 1}"
                aiRouterGateway.saveCredential(
                    AiCredentialDraft(
                        id = existingCredentials.firstOrNull { it.label == label }?.id,
                        providerId = providerId,
                        label = label,
                        secret = key,
                        enabled = true,
                        sortNumber = index,
                    )
                )
            }
        } else {
            existingCredentials.filter { it.enabled && it.hasSecret }
        }
        val routeName = "$providerName · Dịch"
        val existingRoute = before.routes.firstOrNull {
            it.taskType == AiTaskType.TRANSLATE_CHAPTER && it.name == routeName
        }
        existingRoute?.let { route ->
            before.targets.filter { it.routeProfileId == route.id }.forEach {
                aiRouterGateway.deleteTarget(it.id)
            }
        }
        val fallbackModels = selectFallbackModels(models)
        val targetCount = fallbackModels.size * savedCredentials.size.coerceAtLeast(1)
        val route = aiRouterGateway.saveRoute(
            AiRouteProfileDraft(
                id = existingRoute?.id,
                name = routeName,
                taskType = AiTaskType.TRANSLATE_CHAPTER,
                strategy = AiRouteStrategy.ROUND_ROBIN,
                maxAttempts = targetCount.coerceAtLeast(1),
                stickySession = true,
                enabled = true,
                makeDefault = true,
            )
        )
        var order = 0
        fallbackModels.forEachIndexed { modelIndex, model ->
            val credentials = savedCredentials.takeIf(List<*>::isNotEmpty) ?: listOf(null)
            credentials.forEach { credential ->
                aiRouterGateway.saveTarget(
                    AiRouteTargetDraft(
                        routeProfileId = route.id,
                        modelProfileId = model.modelProfileId,
                        credentialId = credential?.id,
                        // Same model = same fallback tier; credentials rotate inside that tier.
                        priority = modelIndex,
                        weight = 1,
                        maxConcurrency = TranslationConfig.aiConcurrentChunks.coerceAtLeast(1),
                        enabled = true,
                        sortNumber = order++,
                    )
                )
            }
        }
    }

    private fun selectFallbackModels(models: List<AiProviderModelUi>): List<AiProviderModelUi> =
        models.sortedWith(
            compareBy<AiProviderModelUi> { preferredModelRank(it.modelId) }
                .thenBy { it.modelName.lowercase() }
        ).take(MAX_AUTOMATIC_FALLBACK_MODELS)

    private fun selectPreferredModel(models: List<AiAvailableModel>): AiAvailableModel? =
        models.minWithOrNull(
            compareBy<AiAvailableModel> { preferredModelRank(it.id) }
                .thenBy { it.name.lowercase() }
        )

    private fun preferredModelRank(modelId: String): Int {
        val normalized = modelId.lowercase()
        return when {
            normalized in PREFERRED_GEMINI_MODELS -> PREFERRED_GEMINI_MODELS.indexOf(normalized)
            "gemini" in normalized && normalized.endsWith("-preview") -> 50
            "gemini" in normalized && "flash-lite" in normalized -> 20
            "gemini" in normalized && "flash" in normalized -> 30
            normalized.endsWith("-free") -> 60
            "flash" in normalized -> 70
            "gemini" in normalized -> 100
            else -> 10
        }
    }

    private fun parseParams(json: String?): AiGenerationParams {
        if (json.isNullOrBlank()) return AiGenerationParams()
        return runCatching {
            GSON.fromJson(json, AiGenerationParams::class.java)
        }.getOrDefault(AiGenerationParams())
    }

    private fun parseStringMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            GSON.fromJson(json, Map::class.java)
                .mapKeys { it.key.toString() }
                .mapValues { it.value.toString() }
        }.getOrDefault(emptyMap())
    }

    private fun String.parseKeyList(): List<String> =
        split(Regex("[,;\\r\\n]+"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun String.toStoredKeyList(): String = parseKeyList().joinToString(",")

    private fun String.normalizeKeyInput(): String = parseKeyList().joinToString("\n")

    private companion object {
        val PREFERRED_GEMINI_MODELS = listOf(
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite",
            "gemini-3.1-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
        )
        const val MAX_AUTOMATIC_FALLBACK_MODELS = 6
    }
}
