package io.legado.app.ui.config.ai.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptPresetGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiPromptCatalog
import io.legado.app.domain.model.AiPromptPresetTransfer
import io.legado.app.domain.model.AiPromptPresetTransferFile
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiRouterSnapshot
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.TranslationPromptStage
import io.legado.app.domain.model.activeTranslationPromptStages
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class AiPromptEditorViewModel(
    private val aiProfileGateway: AiProfileGateway,
    private val aiPromptPresetGateway: AiPromptPresetGateway,
    private val aiRouterGateway: AiRouterGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiPromptEditorUiState(
            selectedTaskType = AiTaskType.TRANSLATE_CHAPTER,
            supportedTaskTypes = AiPromptCatalog.supportedTaskTypes.toImmutableList(),
            catalog = AiPromptCatalog.templates
                .map { it.toPromptCatalogItemUi() }
                .toImmutableList(),
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiPromptEditorEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var latestProviders: List<AiProviderProfile> = emptyList()
    private var latestModels: List<AiModelProfile> = emptyList()
    private var latestPresets: List<AiTaskPreset> = emptyList()
    private var latestRouterSnapshot: AiRouterSnapshot = AiRouterSnapshot()
    private var editorBaseline: AiPromptPresetEditorUi? = null

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observeModels(),
                aiProfileGateway.observePresets(),
                aiRouterGateway.observeSnapshot(),
            ) { providers, models, presets, routerSnapshot ->
                PromptEditorSources(providers, models, presets, routerSnapshot)
            }
                .collect { sources ->
                    val (providers, models, presets, routerSnapshot) = sources
                    latestProviders = providers
                    latestModels = models
                    latestPresets = presets
                    latestRouterSnapshot = routerSnapshot
                    publishLists()
                }
        }
    }

    fun onIntent(intent: AiPromptEditorIntent) {
        when (intent) {
            is AiPromptEditorIntent.SelectTask -> {
                _uiState.update { it.copy(selectedTaskType = intent.taskType) }
                publishLists()
            }
            AiPromptEditorIntent.AddPreset -> openNewEditor()
            is AiPromptEditorIntent.EditPreset -> openEditor(intent.item)
            AiPromptEditorIntent.DuplicateCurrentEditor -> duplicateCurrentEditor()
            is AiPromptEditorIntent.SetDefault -> setDefault(intent.presetId)
            is AiPromptEditorIntent.RequestDelete -> {
                _uiState.update {
                    it.copy(activeDialog = AiPromptEditorDialog.Delete(intent.item))
                }
            }
            AiPromptEditorIntent.ConfirmDelete -> deleteSelected()
            AiPromptEditorIntent.ConfirmDiscardEditor -> discardEditor()
            AiPromptEditorIntent.CloseDialog -> {
                _uiState.update { it.copy(activeDialog = null) }
            }
            AiPromptEditorIntent.OpenCatalog -> {
                _uiState.update { it.copy(showCatalog = true) }
            }
            AiPromptEditorIntent.CloseCatalog -> {
                _uiState.update { it.copy(showCatalog = false) }
            }
            is AiPromptEditorIntent.ApplyCatalog -> applyCatalog(intent.template)
            AiPromptEditorIntent.CloseEditor -> requestCloseEditor()
            is AiPromptEditorIntent.UpdateTask -> updateEditor {
                val route = defaultRouteForTask(intent.value)
                copy(
                    taskType = intent.value,
                    routeProfileId = route?.id.orEmpty(),
                    modelProfileId = route?.primaryModelProfileId
                        ?.takeIf(String::isNotBlank)
                        ?: modelProfileId,
                )
            }
            is AiPromptEditorIntent.UpdateName -> updateEditor { copy(name = intent.value) }
            is AiPromptEditorIntent.UpdateDescription -> updateEditor {
                copy(description = intent.value)
            }
            is AiPromptEditorIntent.UpdateModel -> updateEditor {
                copy(modelProfileId = intent.value)
            }
            is AiPromptEditorIntent.UpdateRoute -> updateEditor {
                copy(routeProfileId = intent.value)
            }
            is AiPromptEditorIntent.SelectModelOrRoute -> selectModelOrRoute(intent.value)
            is AiPromptEditorIntent.UpdatePrompt -> updateEditor {
                copy(promptTemplate = intent.value)
            }
            is AiPromptEditorIntent.UpdateTemperature -> updateEditor {
                copy(temperature = intent.value)
            }
            is AiPromptEditorIntent.UpdateTopP -> updateEditor { copy(topP = intent.value) }
            is AiPromptEditorIntent.UpdateTopK -> updateEditor { copy(topK = intent.value) }
            is AiPromptEditorIntent.UpdateRepetitionPenalty -> updateEditor {
                copy(repetitionPenalty = intent.value)
            }
            is AiPromptEditorIntent.UpdateMaxOutputTokens -> updateEditor {
                copy(maxOutputTokens = intent.value)
            }
            is AiPromptEditorIntent.UpdateReasoning -> updateEditor {
                copy(reasoningLevel = intent.value)
            }
            is AiPromptEditorIntent.UpdateTargetLanguage -> updateEditor {
                copy(targetLanguage = intent.value)
            }
            is AiPromptEditorIntent.UpdateMaxInputChars -> updateEditor {
                copy(maxInputChars = intent.value)
            }
            is AiPromptEditorIntent.UpdateConcurrentRequests -> updateEditor {
                copy(concurrentRequests = intent.value)
            }
            is AiPromptEditorIntent.UpdateRetryCount -> updateEditor {
                copy(retryCount = intent.value)
            }
            is AiPromptEditorIntent.UpdateEnabled -> updateEditor {
                copy(enabled = intent.value)
            }
            is AiPromptEditorIntent.UpdateMakeDefault -> updateEditor {
                copy(makeDefault = intent.value, enabled = enabled || intent.value)
            }
            AiPromptEditorIntent.ResetEditor -> resetEditor()
            AiPromptEditorIntent.PreviewEffectivePrompt -> previewEffectivePrompt()
            AiPromptEditorIntent.SavePreset -> savePreset()
            AiPromptEditorIntent.RequestImport -> {
                if (!_uiState.value.transferring) {
                    _effects.tryEmit(AiPromptEditorEffect.OpenImportFile)
                }
            }
            is AiPromptEditorIntent.ImportJson -> importJson(intent.content)
            AiPromptEditorIntent.RequestExport -> exportPresets()
            is AiPromptEditorIntent.ExportFinished -> {
                _uiState.update { it.copy(transferring = false) }
                _effects.tryEmit(
                    AiPromptEditorEffect.ShowMessage(
                        appCtx.getString(
                            if (intent.succeeded) {
                                R.string.ai_prompt_editor_exported
                            } else {
                                R.string.ai_prompt_editor_transfer_failed
                            }
                        )
                    )
                )
            }
            AiPromptEditorIntent.TransferCancelled -> {
                _uiState.update { it.copy(transferring = false) }
            }
        }
    }

    private fun publishLists() {
        val providerNames = latestProviders
            .asSequence()
            .filter(AiProviderProfile::enabled)
            .associate { it.id to it.name }
        val models = latestModels.asSequence()
            .filter { model -> model.enabled && model.providerId in providerNames }
            .map { model ->
            AiPromptModelOptionUi(
                id = model.id,
                providerName = providerNames[model.providerId].orEmpty(),
                modelName = model.displayName,
                modelId = model.modelId,
                contextWindow = model.contextWindow,
                maxOutputTokens = model.maxOutputTokens,
            )
        }.toList()
        val modelLabels = models.associate { it.id to it.displayLabel }
        val targetCounts = latestRouterSnapshot.targets
            .filter { it.enabled }
            .groupingBy { it.routeProfileId }
            .eachCount()
        val primaryModels = latestRouterSnapshot.targets
            .asSequence()
            .filter { it.enabled }
            .groupBy { it.routeProfileId }
            .mapValues { (_, targets) ->
                targets.sortedWith(
                    compareBy<io.legado.app.domain.model.AiRouteTargetConfig> { it.priority }
                        .thenBy { it.sortNumber }
                        .thenBy { it.id }
                ).firstOrNull()?.modelProfileId.orEmpty()
            }
        val routes = latestRouterSnapshot.routes
            .asSequence()
            .filter { it.enabled }
            .map { route ->
                AiPromptRouteOptionUi(
                    id = route.id,
                    taskType = route.taskType,
                    name = route.name,
                    targetCount = targetCounts[route.id] ?: 0,
                    maxAttempts = route.maxAttempts,
                    primaryModelProfileId = primaryModels[route.id].orEmpty(),
                    isDefault = route.isDefault,
                )
            }
            .sortedWith(
                compareBy<AiPromptRouteOptionUi> { route ->
                    AiPromptCatalog.supportedTaskTypes.indexOf(route.taskType).takeIf { it >= 0 }
                        ?: Int.MAX_VALUE
                }.thenByDescending { it.isDefault }
                    .thenBy { it.name }
            )
            .toList()
        val routeLabels = routes.associate { it.id to it.displayLabel }
        val selectedTask = _uiState.value.selectedTaskType
        val presets = latestPresets
            .asSequence()
            .filter { it.taskType == selectedTask }
            .sortedWith(compareByDescending<AiTaskPreset> { it.isDefault }
                .thenBy { it.sortNumber }
                .thenBy { it.name })
            .map { preset ->
                val runtimeOptions = parseRuntimeOptions(preset.chunkPolicyJson)
                AiPromptPresetItemUi(
                    id = preset.id,
                    taskType = preset.taskType,
                    name = preset.name,
                    description = preset.description,
                    modelProfileId = preset.modelProfileId,
                    modelLabel = if (runtimeOptions.routeProfileId.isNotBlank()) {
                        ""
                    } else {
                        modelLabels[preset.modelProfileId]
                            ?: appCtx.getString(R.string.ai_prompt_editor_missing_model)
                    },
                    routeProfileId = runtimeOptions.routeProfileId,
                    routeLabel = routeLabels[runtimeOptions.routeProfileId].orEmpty(),
                    promptTemplate = preset.promptTemplate,
                    paramsJson = preset.paramsJson,
                    runtimeOptionsJson = preset.chunkPolicyJson,
                    enabled = preset.enabled,
                    isDefault = preset.isDefault,
                    sortNumber = preset.sortNumber,
                )
            }
            .toList()
        _uiState.update {
            it.copy(
                loading = false,
                models = models.toImmutableList(),
                routes = routes.toImmutableList(),
                presets = presets.toImmutableList(),
            )
        }
    }

    private fun openNewEditor() {
        val state = _uiState.value
        val taskType = state.selectedTaskType
        val route = defaultRouteForTask(taskType)
        showEditor(
            AiPromptPresetEditorUi(
                taskType = taskType,
                name = defaultPresetName(taskType),
                modelProfileId = route?.primaryModelProfileId
                    ?.takeIf(String::isNotBlank)
                    ?: state.models.firstOrNull()?.id.orEmpty(),
                routeProfileId = route?.id.orEmpty(),
                promptTemplate = AiPromptCatalog.defaultPrompt(taskType),
                temperature = TranslationConstants.DEFAULT_TEMPERATURE.toString(),
                targetLanguage = TranslationConfig.llmTargetLanguage,
                maxInputChars = TranslationConfig.aiMaxCharsPerChunk.toString(),
                concurrentRequests = TranslationConfig.aiConcurrentChunks.toString(),
                retryCount = TranslationConfig.llmRetryCount.toString(),
                makeDefault = state.presets.none { preset -> preset.isDefault },
                sortNumber = state.presets.size,
            )
        )
    }

    private fun openEditor(item: AiPromptPresetItemUi) {
        val params = parseParams(item.paramsJson)
        val runtime = parseRuntimeOptions(item.runtimeOptionsJson)
        val route = runtime.routeProfileId
            .takeIf { it.isNotBlank() && routeMatchesTask(it, item.taskType) }
            ?.let { routeId -> _uiState.value.routes.firstOrNull { it.id == routeId } }
        showEditor(
            AiPromptPresetEditorUi(
                presetId = item.id,
                taskType = item.taskType,
                name = item.name,
                description = item.description,
                modelProfileId = route?.primaryModelProfileId
                    ?.takeIf(String::isNotBlank)
                    ?: item.modelProfileId,
                routeProfileId = route?.id.orEmpty(),
                promptTemplate = item.promptTemplate,
                temperature = params.temperature?.toString().orEmpty(),
                topP = params.topP?.toString().orEmpty(),
                topK = params.topK?.toString().orEmpty(),
                repetitionPenalty = params.repetitionPenalty?.toString().orEmpty(),
                maxOutputTokens = params.maxOutputTokens?.toString().orEmpty(),
                reasoningLevel = params.reasoningLevel,
                targetLanguage = runtime.targetLanguage,
                maxInputChars = runtime.maxInputChars.toString(),
                concurrentRequests = runtime.concurrentRequests.toString(),
                retryCount = runtime.retryCount.toString(),
                enabled = item.enabled,
                makeDefault = item.isDefault,
                sortNumber = item.sortNumber,
            )
        )
    }

    private fun showEditor(
        editor: AiPromptPresetEditorUi,
        markUnsaved: Boolean = false,
    ) {
        editorBaseline = editor.persistedContent().takeUnless { markUnsaved }
        _uiState.update { state ->
            state.copy(
                editor = editor,
                hasUnsavedEditorChanges = markUnsaved,
            )
        }
    }

    private fun duplicateCurrentEditor() {
        val editor = _uiState.value.editor ?: return
        showEditor(
            editor = editor.copy(
                presetId = null,
                name = appCtx.getString(R.string.ai_prompt_editor_copy_name, editor.name),
                makeDefault = false,
                sortNumber = _uiState.value.presets.size,
                errorMessage = null,
            ),
            markUnsaved = true,
        )
    }

    private fun applyCatalog(template: AiPromptCatalogItemUi) {
        val existingForTask = latestPresets.filter { it.taskType == template.taskType }
        val route = defaultRouteForTask(template.taskType)
        val editor = AiPromptPresetEditorUi(
            taskType = template.taskType,
            name = template.name,
            description = template.description,
            modelProfileId = route?.primaryModelProfileId
                ?.takeIf(String::isNotBlank)
                ?: _uiState.value.models.firstOrNull()?.id.orEmpty(),
            routeProfileId = route?.id.orEmpty(),
            promptTemplate = template.prompt,
            temperature = TranslationConstants.DEFAULT_TEMPERATURE.toString(),
            targetLanguage = TranslationConfig.llmTargetLanguage,
            maxInputChars = TranslationConfig.aiMaxCharsPerChunk.toString(),
            concurrentRequests = TranslationConfig.aiConcurrentChunks.toString(),
            retryCount = TranslationConfig.llmRetryCount.toString(),
            makeDefault = existingForTask.none(AiTaskPreset::isDefault),
            sortNumber = existingForTask.size,
        )
        editorBaseline = editor.persistedContent()
        _uiState.update {
            it.copy(
                selectedTaskType = template.taskType,
                showCatalog = false,
                editor = editor,
                hasUnsavedEditorChanges = false,
            )
        }
        publishLists()
    }

    private fun updateEditor(
        update: AiPromptPresetEditorUi.() -> AiPromptPresetEditorUi
    ) {
        _uiState.update { state ->
            val editor = state.editor?.update()?.copy(errorMessage = null)
            state.copy(
                editor = editor,
                hasUnsavedEditorChanges = editor?.persistedContent() != editorBaseline,
            )
        }
    }

    private fun selectModelOrRoute(value: String) {
        when (val selection = decodeAiPromptTargetSelection(value)) {
            AiPromptTargetSelection.Default -> {
                val route = defaultRouteForTask(_uiState.value.editor?.taskType.orEmpty())
                updateEditor {
                    copy(
                        routeProfileId = route?.id.orEmpty(),
                        modelProfileId = route?.primaryModelProfileId
                            ?.takeIf(String::isNotBlank)
                            ?: modelProfileId,
                    )
                }
            }
            is AiPromptTargetSelection.Route -> {
                val routeId = selection.routeProfileId
                val route = _uiState.value.routes.firstOrNull { it.id == routeId }
                    ?: return
                val modelId = route.primaryModelProfileId
                    .takeIf(String::isNotBlank)
                    ?: _uiState.value.editor?.modelProfileId.orEmpty()
                updateEditor {
                    copy(
                        routeProfileId = route.id,
                        modelProfileId = modelId,
                    )
                }
            }
            is AiPromptTargetSelection.Model -> {
                updateEditor {
                    copy(
                        routeProfileId = "",
                        modelProfileId = selection.modelProfileId,
                    )
                }
            }
            null -> Unit
        }
    }

    private fun requestCloseEditor() {
        if (_uiState.value.hasUnsavedEditorChanges) {
            _uiState.update { it.copy(activeDialog = AiPromptEditorDialog.DiscardEditor) }
        } else {
            discardEditor()
        }
    }

    private fun discardEditor() {
        editorBaseline = null
        _uiState.update {
            it.copy(
                editor = null,
                hasUnsavedEditorChanges = false,
                activeDialog = null,
            )
        }
    }

    private fun resetEditor() {
        updateEditor {
            val route = defaultRouteForTask(taskType)
            copy(
                promptTemplate = AiPromptCatalog.defaultPrompt(taskType),
                temperature = TranslationConstants.DEFAULT_TEMPERATURE.toString(),
                topP = "",
                topK = "",
                repetitionPenalty = "",
                maxOutputTokens = "",
                reasoningLevel = AiReasoningLevel.AUTO,
                targetLanguage = TranslationConfig.llmTargetLanguage,
                maxInputChars = TranslationConfig.aiMaxCharsPerChunk.toString(),
                concurrentRequests = TranslationConfig.aiConcurrentChunks.toString(),
                retryCount = TranslationConfig.llmRetryCount.toString(),
                routeProfileId = route?.id.orEmpty(),
                modelProfileId = route?.primaryModelProfileId
                    ?.takeIf(String::isNotBlank)
                    ?: modelProfileId,
            )
        }
    }

    private fun exportPresets() {
        if (_uiState.value.transferring) return
        if (latestPresets.isEmpty()) {
            _effects.tryEmit(
                AiPromptEditorEffect.ShowMessage(
                    appCtx.getString(R.string.ai_prompt_editor_nothing_to_export)
                )
            )
            return
        }
        val providers = latestProviders.associateBy(AiProviderProfile::id)
        val models = latestModels.associateBy(AiModelProfile::id)
        val transfers = latestPresets.mapNotNull { preset ->
            val model = models[preset.modelProfileId] ?: return@mapNotNull null
            AiPromptPresetTransfer(
                taskType = preset.taskType,
                name = preset.name,
                description = preset.description,
                providerName = providers[model.providerId]?.name,
                modelId = model.modelId,
                modelDisplayName = model.displayName,
                promptTemplate = preset.promptTemplate,
                params = parseParams(preset.paramsJson),
                runtimeOptions = parseRuntimeOptions(preset.chunkPolicyJson),
                enabled = preset.enabled,
                makeDefault = preset.isDefault,
                sortNumber = preset.sortNumber,
            )
        }
        if (transfers.isEmpty()) {
            _effects.tryEmit(
                AiPromptEditorEffect.ShowMessage(
                    appCtx.getString(R.string.ai_prompt_editor_nothing_to_export)
                )
            )
            return
        }
        _uiState.update { it.copy(transferring = true) }
        _effects.tryEmit(
            AiPromptEditorEffect.CreateExportFile(
                fileName = "legado-ai-prompts.json",
                content = GSON.toJson(AiPromptPresetTransferFile(presets = transfers)),
            )
        )
    }

    private fun importJson(content: String) {
        if (_uiState.value.transferring || content.length > MAX_TRANSFER_CHARS) {
            _effects.tryEmit(
                AiPromptEditorEffect.ShowMessage(
                    appCtx.getString(R.string.ai_prompt_editor_transfer_failed)
                )
            )
            return
        }
        _uiState.update { it.copy(transferring = true) }
        viewModelScope.launch {
            val transferFile = withContext(Dispatchers.Default) {
                runCatching {
                    GSON.fromJson(content, AiPromptPresetTransferFile::class.java).also { file ->
                        require(file.schemaVersion == AiPromptPresetTransferFile.CURRENT_SCHEMA_VERSION)
                        require(file.presets.orEmpty().size <= MAX_TRANSFER_PRESETS)
                    }
                }.getOrNull()
            }
            if (transferFile == null) {
                _uiState.update { it.copy(transferring = false) }
                _effects.tryEmit(
                    AiPromptEditorEffect.ShowMessage(
                        appCtx.getString(R.string.ai_prompt_editor_transfer_failed)
                    )
                )
                return@launch
            }

            var imported = 0
            var skipped = 0
            transferFile.presets.orEmpty().forEach { transfer ->
                val draft = transfer.toDraftOrNull()
                if (draft == null) {
                    skipped += 1
                } else {
                    runCatching { aiProfileGateway.saveTaskPreset(draft) }
                        .onSuccess { imported += 1 }
                        .onFailure { skipped += 1 }
                }
            }
            _uiState.update { it.copy(transferring = false) }
            _effects.tryEmit(
                AiPromptEditorEffect.ShowMessage(
                    appCtx.getString(
                        R.string.ai_prompt_editor_imported,
                        imported,
                        skipped,
                    )
                )
            )
        }
    }

    private fun AiPromptPresetTransfer.toDraftOrNull(): AiTaskPresetDraft? {
        val validTask = taskType?.takeIf(AiPromptCatalog.supportedTaskTypes::contains) ?: return null
        val validName = name?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val validPrompt = promptTemplate?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val requestedModelId = modelId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val candidates = latestModels.filter { it.modelId == requestedModelId }
        val providerIds = latestProviders.asSequence()
            .filter { it.name.equals(providerName, ignoreCase = true) }
            .map(AiProviderProfile::id)
            .toSet()
        val model = candidates.firstOrNull { it.providerId in providerIds }
            ?: candidates.singleOrNull()
            ?: return null
        val importedParams = params ?: AiGenerationParams()
        val sanitizedParams = AiGenerationParams(
            temperature = importedParams.temperature?.takeIf { it in 0f..2f },
            maxOutputTokens = importedParams.maxOutputTokens?.takeIf { it > 0 },
            topP = importedParams.topP?.takeIf { it in 0f..1f },
            topK = importedParams.topK?.takeIf { it in 1..MAX_TOP_K },
            repetitionPenalty = importedParams.repetitionPenalty
                ?.takeIf { it in MIN_REPETITION_PENALTY..MAX_REPETITION_PENALTY },
            reasoningLevel = runCatching { importedParams.reasoningLevel }.getOrNull()
                ?: AiReasoningLevel.AUTO,
        )
        val importedRuntime = runtimeOptions ?: AiTaskRuntimeOptions()
        val sanitizedRuntime = AiTaskRuntimeOptions(
            targetLanguage = runCatching { importedRuntime.targetLanguage }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: TranslationConfig.llmTargetLanguage,
            maxInputChars = importedRuntime.maxInputChars.coerceIn(
                TranslationConfig.MIN_CHUNK_CHARS,
                TranslationConfig.MAX_CHUNK_CHARS,
            ),
            concurrentRequests = importedRuntime.concurrentRequests.coerceIn(1, 8),
            retryCount = importedRuntime.retryCount.coerceIn(0, 10),
            routeProfileId = importedRuntime.routeProfileId
                .takeIf { routeMatchesTask(it, validTask) }
                .orEmpty(),
        )
        return AiTaskPresetDraft(
            taskType = validTask,
            name = validName,
            description = description?.trim().orEmpty(),
            modelProfileId = model.id,
            promptTemplate = validPrompt,
            params = sanitizedParams,
            runtimeOptions = sanitizedRuntime,
            enabled = enabled ?: true,
            makeDefault = makeDefault ?: false,
            sortNumber = sortNumber?.coerceAtLeast(0) ?: latestPresets.size,
        )
    }

    private fun savePreset() {
        val editor = _uiState.value.editor ?: return
        val validationError = validate(editor)
        if (validationError != null) {
            updateEditor { copy(errorMessage = validationError) }
            return
        }
        val params = AiGenerationParams(
            temperature = editor.temperature.toFloatOrNull(),
            maxOutputTokens = editor.maxOutputTokens.toIntOrNull()?.takeIf { it > 0 },
            topP = editor.topP.toFloatOrNull(),
            topK = editor.topK.toIntOrNull(),
            repetitionPenalty = editor.repetitionPenalty.toFloatOrNull(),
            reasoningLevel = editor.reasoningLevel,
        )
        val runtimeOptions = AiTaskRuntimeOptions(
            targetLanguage = editor.targetLanguage,
            maxInputChars = editor.maxInputChars.toInt(),
            concurrentRequests = editor.concurrentRequests.toInt(),
            retryCount = editor.retryCount.toInt(),
            routeProfileId = editor.routeProfileId,
        )
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.saveTaskPreset(
                    AiTaskPresetDraft(
                        presetId = editor.presetId,
                        taskType = editor.taskType,
                        name = editor.name,
                        description = editor.description,
                        modelProfileId = editor.routeProfileId
                            .takeIf(String::isNotBlank)
                            ?.let { routeId ->
                                _uiState.value.routes.firstOrNull { it.id == routeId }
                                    ?.primaryModelProfileId
                            }
                            ?.takeIf(String::isNotBlank)
                            ?: editor.modelProfileId,
                        promptTemplate = editor.promptTemplate,
                        params = params,
                        runtimeOptions = runtimeOptions,
                        enabled = editor.enabled,
                        makeDefault = editor.makeDefault,
                        sortNumber = editor.sortNumber,
                    )
                )
            }.onSuccess {
                editorBaseline = null
                _uiState.update {
                    it.copy(
                        saving = false,
                        editor = null,
                        hasUnsavedEditorChanges = false,
                        selectedTaskType = editor.taskType,
                    )
                }
                _effects.tryEmit(
                    AiPromptEditorEffect.ShowMessage(
                        appCtx.getString(R.string.ai_prompt_editor_saved)
                    )
                )
            }.onFailure { error ->
                _uiState.update { it.copy(saving = false) }
                updateEditor {
                    copy(
                        errorMessage = error.message
                            ?: appCtx.getString(R.string.ai_config_save_failed)
                    )
                }
            }
        }
    }

    private fun setDefault(presetId: String) {
        viewModelScope.launch {
            runCatching { aiProfileGateway.setDefaultTaskPreset(presetId) }
                .onSuccess {
                    _effects.tryEmit(
                        AiPromptEditorEffect.ShowMessage(
                            appCtx.getString(R.string.ai_prompt_editor_activated)
                        )
                    )
                }
                .onFailure { error ->
                    _effects.tryEmit(
                        AiPromptEditorEffect.ShowMessage(
                            error.message ?: appCtx.getString(R.string.ai_config_save_failed)
                        )
                    )
                }
        }
    }

    private fun deleteSelected() {
        val dialog = _uiState.value.activeDialog as? AiPromptEditorDialog.Delete ?: return
        viewModelScope.launch {
            runCatching { aiProfileGateway.deleteTaskPreset(dialog.item.id) }
                .onSuccess {
                    val deletedEditor = _uiState.value.editor?.presetId == dialog.item.id
                    if (deletedEditor) editorBaseline = null
                    _uiState.update {
                        it.copy(
                            activeDialog = null,
                            editor = if (deletedEditor) null else it.editor,
                            hasUnsavedEditorChanges = if (deletedEditor) {
                                false
                            } else {
                                it.hasUnsavedEditorChanges
                            },
                        )
                    }
                    _effects.tryEmit(
                        AiPromptEditorEffect.ShowMessage(
                            appCtx.getString(R.string.ai_prompt_editor_deleted)
                        )
                    )
                }
                .onFailure { error ->
                    _effects.tryEmit(
                        AiPromptEditorEffect.ShowMessage(
                            error.message ?: appCtx.getString(R.string.ai_config_save_failed)
                        )
                    )
                }
        }
    }

    private fun previewEffectivePrompt() {
        val editor = _uiState.value.editor ?: return
        if (editor.promptTemplate.isBlank()) {
            updateEditor {
                copy(errorMessage = appCtx.getString(R.string.ai_prompt_editor_prompt_required))
            }
            return
        }
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                buildString {
                    append(editor.promptTemplate.trim())
                    if (editor.taskType == AiTaskType.TRANSLATE_CHAPTER) {
                        activeTranslationPromptStages(
                            includeRetranslateStage = false,
                        ).forEach { stage ->
                            aiPromptPresetGateway.getEnabledByTaskType(stage.taskType)
                                .map { it.instruction.trim() }
                                .filter(String::isNotBlank)
                                .forEach { instruction ->
                                    append("\n\n[").append(stage.storageKey).append("]\n")
                                    append(instruction)
                                }
                        }
                        append("\n\nTarget language: ")
                        append(
                            TranslationConstants.targetLanguages
                                .firstOrNull { it.first == editor.targetLanguage }
                                ?.second
                                ?: editor.targetLanguage
                        )
                        append("\n\nTerminology Dictionary:\n")
                        append("<runtime dictionary entries>")
                        append("\n\n")
                        append(TranslationConstants.OUTPUT_FORMAT)
                    }
                }
            }
            _uiState.update {
                it.copy(
                    activeDialog = AiPromptEditorDialog.Preview(
                        title = editor.name.ifBlank {
                            appCtx.getString(R.string.ai_prompt_editor_preview)
                        },
                        content = preview,
                    )
                )
            }
        }
    }

    private fun validate(editor: AiPromptPresetEditorUi): String? {
        if (editor.name.isBlank()) {
            return appCtx.getString(R.string.ai_prompt_editor_name_required)
        }
        when (validateAiPromptTargetSelection(editor, latestModels.map { model ->
            AiPromptModelOptionUi(
                id = model.id,
                providerName = "",
                modelName = model.displayName,
                modelId = model.modelId,
            )
        }, _uiState.value.routes.toList())) {
            AiPromptTargetValidationError.MODEL_REQUIRED ->
                return appCtx.getString(R.string.ai_prompt_editor_model_required)
            AiPromptTargetValidationError.ROUTE_INVALID ->
                return appCtx.getString(R.string.ai_prompt_editor_route_invalid)
            AiPromptTargetValidationError.ROUTE_EMPTY ->
                return appCtx.getString(R.string.ai_prompt_editor_combo_empty)
            null -> Unit
        }
        if (editor.promptTemplate.isBlank()) {
            return appCtx.getString(R.string.ai_prompt_editor_prompt_required)
        }
        if (editor.routeProfileId.isNotBlank() &&
            !routeMatchesTask(editor.routeProfileId, editor.taskType)
        ) {
            return appCtx.getString(R.string.ai_prompt_editor_route_invalid)
        }
        val temperature = editor.temperature.toFloatOrNull()
        if (editor.temperature.isNotBlank() &&
            (temperature == null || temperature !in 0f..2f)
        ) {
            return appCtx.getString(R.string.ai_prompt_editor_temperature_invalid)
        }
        val topP = editor.topP.toFloatOrNull()
        if (editor.topP.isNotBlank() && (topP == null || topP !in 0f..1f)) {
            return appCtx.getString(R.string.ai_prompt_editor_top_p_invalid)
        }
        val topK = editor.topK.toIntOrNull()
        if (editor.topK.isNotBlank() && (topK == null || topK !in 1..MAX_TOP_K)) {
            return appCtx.getString(R.string.ai_prompt_editor_top_k_invalid, MAX_TOP_K)
        }
        val repetitionPenalty = editor.repetitionPenalty.toFloatOrNull()
        if (editor.repetitionPenalty.isNotBlank() &&
            (repetitionPenalty == null ||
                repetitionPenalty !in MIN_REPETITION_PENALTY..MAX_REPETITION_PENALTY)
        ) {
            return appCtx.getString(R.string.ai_prompt_editor_repetition_penalty_invalid)
        }
        if (editor.maxOutputTokens.isNotBlank() &&
            (editor.maxOutputTokens.toIntOrNull() ?: 0) <= 0
        ) {
            return appCtx.getString(R.string.ai_prompt_editor_token_invalid)
        }
        val selectedModel = _uiState.value.models.firstOrNull {
            it.id == editor.modelProfileId
        }
        val requestedOutputTokens = editor.maxOutputTokens.toIntOrNull()
        if (requestedOutputTokens != null &&
            selectedModel != null &&
            selectedModel.maxOutputTokens > 0 &&
            requestedOutputTokens > selectedModel.maxOutputTokens
        ) {
            return appCtx.getString(
                R.string.ai_prompt_editor_output_exceeds_model,
                selectedModel.maxOutputTokens,
            )
        }
        val maxInput = editor.maxInputChars.toIntOrNull()
        val concurrency = editor.concurrentRequests.toIntOrNull()
        val retries = editor.retryCount.toIntOrNull()
        if (maxInput == null ||
            maxInput !in TranslationConfig.MIN_CHUNK_CHARS..TranslationConfig.MAX_CHUNK_CHARS ||
            concurrency == null || concurrency !in 1..8 ||
            retries == null || retries !in 0..10
        ) {
            return appCtx.getString(R.string.ai_prompt_editor_runtime_invalid)
        }
        return null
    }

    private fun defaultRouteForTask(taskType: String): AiPromptRouteOptionUi? =
        _uiState.value.routes.firstOrNull {
            it.taskType == taskType && it.isDefault
        } ?: _uiState.value.routes.firstOrNull { it.taskType == taskType }

    private fun parseParams(json: String?): AiGenerationParams {
        if (json.isNullOrBlank()) return AiGenerationParams()
        return runCatching {
            GSON.fromJson(json, AiGenerationParams::class.java)
        }.getOrDefault(AiGenerationParams())
    }

    private fun parseRuntimeOptions(json: String?): AiTaskRuntimeOptions {
        val fallback = AiTaskRuntimeOptions(
            targetLanguage = TranslationConfig.llmTargetLanguage,
            maxInputChars = TranslationConfig.aiMaxCharsPerChunk,
            concurrentRequests = TranslationConfig.aiConcurrentChunks,
            retryCount = TranslationConfig.llmRetryCount,
        )
        if (json.isNullOrBlank()) {
            return fallback
        }
        return runCatching {
            GSON.fromJson(json, AiTaskRuntimeOptions::class.java)
        }.getOrNull()?.withDefaults(fallback) ?: fallback
    }

    private fun AiTaskRuntimeOptions.withDefaults(
        fallback: AiTaskRuntimeOptions,
    ): AiTaskRuntimeOptions {
        return AiTaskRuntimeOptions(
            targetLanguage = runCatching { targetLanguage }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: fallback.targetLanguage,
            maxInputChars = runCatching { maxInputChars }.getOrDefault(0)
                .takeIf { it > 0 }
                ?: fallback.maxInputChars,
            concurrentRequests = runCatching { concurrentRequests }.getOrDefault(0)
                .takeIf { it > 0 }
                ?: fallback.concurrentRequests,
            retryCount = runCatching { retryCount }.getOrDefault(-1)
                .takeIf { it >= 0 }
                ?: fallback.retryCount,
            routeProfileId = runCatching { routeProfileId }.getOrNull().orEmpty(),
        )
    }

    private fun routeMatchesTask(routeProfileId: String, taskType: String): Boolean {
        if (routeProfileId.isBlank()) return true
        return latestRouterSnapshot.routes.any { route ->
            route.id == routeProfileId && route.enabled && route.taskType == taskType
        }
    }

    private fun defaultPresetName(taskType: String): String {
        val taskName = appCtx.getString(
            when (taskType) {
                AiTaskType.TRANSLATE_CHAPTER -> R.string.ai_prompt_task_translate
                AiTaskType.CHAT -> R.string.ai_prompt_task_chat
                AiTaskType.SUMMARIZE_CHAPTER -> R.string.ai_prompt_task_summary_chapter
                AiTaskType.SUMMARIZE_BOOK -> R.string.ai_prompt_task_summary_book
                AiTaskType.EXPLAIN_SELECTION -> R.string.ai_prompt_task_explain
                AiTaskType.CLEAN_SELECTION -> R.string.ai_prompt_task_clean
                AiTaskType.TEXT_FACTORY -> R.string.ai_prompt_task_text_factory
                AiTaskType.REWRITE_TEXT -> R.string.ai_prompt_task_rewrite
                AiTaskType.AUTHORING_DIRECTOR -> R.string.ai_prompt_task_authoring_director
                AiTaskType.AUTHORING_WRITER -> R.string.ai_prompt_task_authoring_writer
                AiTaskType.GENERATE_STORY_IMAGE -> R.string.ai_prompt_task_story_image
                else -> R.string.ai_prompt_task_other
            }
        )
        return appCtx.getString(
            R.string.ai_prompt_editor_new_name,
            taskName,
        )
    }

    private val AiPromptModelOptionUi.displayLabel: String
        get() = "$providerName · $modelName ($modelId)"

    private val AiPromptRouteOptionUi.displayLabel: String
        get() = if (targetCount > 0) "$name ($targetCount)" else name

    private fun AiPromptPresetEditorUi.persistedContent(): AiPromptPresetEditorUi =
        copy(errorMessage = null)

    private data class PromptEditorSources(
        val providers: List<AiProviderProfile>,
        val models: List<AiModelProfile>,
        val presets: List<AiTaskPreset>,
        val routerSnapshot: AiRouterSnapshot,
    )

    companion object {
        private const val MAX_TRANSFER_CHARS = 2_000_000
        private const val MAX_TRANSFER_PRESETS = 500
        private const val MAX_TOP_K = 10_000
        private const val MIN_REPETITION_PENALTY = 0.1f
        private const val MAX_REPETITION_PENALTY = 2f
    }
}
