package io.legado.app.ui.ai.router

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.gateway.AiOAuthGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.domain.model.AiConnectionStatus
import io.legado.app.domain.model.AiCredentialKind
import io.legado.app.domain.model.AiOAuthEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiPromptCatalog
import io.legado.app.domain.model.AiProviderCatalog
import io.legado.app.domain.model.AiProviderRegistry
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderConnectionDraft
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiCredentialDraft
import io.legado.app.domain.model.AiRouteProfileDraft
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiRouteTargetDraft
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.ExternalAssetCatalog
import io.legado.app.domain.usecase.TestAiProviderDraftUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiRouterViewModel(
    private val routerGateway: AiRouterGateway,
    private val profileGateway: AiProfileGateway,
    private val oauthGateway: AiOAuthGateway,
    private val aiTextGateway: AiTextGateway,
    private val testProviderDraft: TestAiProviderDraftUseCase,
    private val localAiEngineGateway: LocalAiEngineGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiRouterUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiRouterEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            oauthGateway.events.collect { event ->
                when (event) {
                    is AiOAuthEvent.Connected -> {
                        _uiState.update { it.copy(saving = false) }
                        _effects.emit(
                            AiRouterEffect.ShowMessage("Đã kết nối ${event.accountLabel}")
                        )
                    }
                    is AiOAuthEvent.Failed -> {
                        _uiState.update { it.copy(saving = false) }
                        _effects.emit(AiRouterEffect.ShowMessage(event.message))
                    }
                }
            }
        }
        // Catalog bootstrap can fetch models and write several Room rows. Keep it off the main
        // dispatcher and make it idempotent so reopening the dashboard stays cheap.
        viewModelScope.launch(Dispatchers.IO) {
            AiProviderCatalog.autoInstallIds.forEach { providerId ->
                runCatching { installCatalogEntry(providerId) }
            }
        }
        viewModelScope.launch {
            combine(
                routerGateway.observeSnapshot(),
                profileGateway.observeProviders(),
                profileGateway.observeModels(),
            ) { snapshot, providers, models ->
                val providerNameById = providers.associate { it.id to it.name }
                val modelLabelById = models.associate { model ->
                    model.id to listOfNotNull(
                        providerNameById[model.providerId],
                        model.displayName,
                    ).joinToString(" · ")
                }
                val credentialById = snapshot.credentials.associateBy { it.id }
                val targetByRoute = snapshot.targets.groupBy { it.routeProfileId }
                val targetLabelById = snapshot.targets.associate { target ->
                    target.id to modelLabelById[target.modelProfileId].orEmpty()
                }
                val oauthProviderConfigs = oauthGateway.providers()
                val providerDashboardItems = buildProviderDashboardItems(
                    catalogEntries = AiProviderCatalog.entries,
                    oauthProviders = oauthProviderConfigs,
                    providers = providers,
                    models = models,
                    credentials = snapshot.credentials,
                )
                AiRouterUiState(
                    registryProviderCount = AiProviderRegistry.entries.size,
                    registryTextProviderCount = AiProviderRegistry.textProviders.size,
                    registryCapabilityCount = AiProviderRegistry.entries
                        .flatMap { it.serviceKinds }
                        .distinct()
                        .size,
                    oauthProviders = oauthProviderConfigs.map { provider ->
                        AiRouterOAuthProviderUi(
                            id = provider.id,
                            name = provider.name,
                            warning = provider.warning,
                            flow = provider.flow,
                            available = provider.available,
                        )
                    }.toImmutableList(),
                    catalogProviders = AiProviderCatalog.entries.map { entry ->
                        AiRouterCatalogProviderUi(
                            id = entry.id,
                            name = entry.name,
                            category = entry.category,
                            notice = entry.notice,
                            installed = providers.any { it.id == "catalog_${entry.id}" },
                        )
                    }.toImmutableList(),
                    healthSummary = buildHealthSummary(
                        providerItems = providerDashboardItems,
                        routes = snapshot.routes,
                        credentials = snapshot.credentials,
                        attempts = snapshot.attempts,
                    ),
                    providerFilters = buildProviderFilters(providerDashboardItems).toImmutableList(),
                    providerDashboardItems = providerDashboardItems.toImmutableList(),
                    filteredProviderDashboardItems = providerDashboardItems.toImmutableList(),
                    diagnostics = buildDiagnostics(
                        providerItems = providerDashboardItems,
                        routes = snapshot.routes,
                        targets = snapshot.targets,
                        credentials = snapshot.credentials,
                        modelLabelById = modelLabelById,
                    ).toImmutableList(),
                    comboTemplates = comboTemplates.map { template ->
                        AiRouterComboTemplateUi(
                            id = template.id,
                            name = template.name,
                            description = template.description,
                        )
                    }.toImmutableList(),
                    providers = providers.map {
                        AiRouterProviderUi(it.id, it.name)
                    }.toImmutableList(),
                    models = models.map { model ->
                        AiRouterModelUi(
                            id = model.id,
                            providerId = model.providerId,
                            label = modelLabelById[model.id].orEmpty(),
                        )
                    }.toImmutableList(),
                    credentials = snapshot.credentials.map { credential ->
                        AiRouterCredentialUi(
                            id = credential.id,
                            providerId = credential.providerId,
                            providerName = providerNameById[credential.providerId].orEmpty(),
                            label = credential.label,
                            kind = credential.kind,
                            enabled = credential.enabled,
                            hasSecret = credential.hasSecret,
                            cooldownUntil = credential.cooldownUntil,
                            consecutiveFailures = credential.consecutiveFailures,
                            lastFailureKind = credential.lastFailureKind,
                            oauthProvider = credential.oauthProvider,
                            accountLabel = credential.accountLabel,
                            expiresAt = credential.expiresAt,
                            status = credential.status,
                            hasRefreshToken = credential.hasRefreshToken,
                        )
                    }.toImmutableList(),
                    routes = snapshot.routes.map { route ->
                        val routeTargets = targetByRoute[route.id].orEmpty()
                        val health = routeHealth(route, routeTargets, snapshot.attempts)
                        AiRouterRouteUi(
                            id = route.id,
                            name = route.name,
                            taskType = route.taskType,
                            strategy = route.strategy,
                            maxAttempts = route.maxAttempts,
                            stickySession = route.stickySession,
                            enabled = route.enabled,
                            healthStatus = health.status,
                            successRatePercent = health.successRatePercent,
                            averageLatencyMs = health.averageLatencyMs,
                            recentAttemptCount = health.recentAttemptCount,
                            targets = routeTargets.map { target ->
                                val credential = target.credentialId?.let(credentialById::get)
                                AiRouterTargetUi(
                                    id = target.id,
                                    routeProfileId = target.routeProfileId,
                                    modelProfileId = target.modelProfileId,
                                    modelLabel = modelLabelById[target.modelProfileId].orEmpty(),
                                    credentialId = target.credentialId,
                                    credentialLabel = credential?.label,
                                    priority = target.priority,
                                    weight = target.weight,
                                    maxConcurrency = target.maxConcurrency,
                                    enabled = target.enabled,
                                    cooldownUntil = target.cooldownUntil,
                                    consecutiveFailures = target.consecutiveFailures,
                                    lastFailureKind = target.lastFailureKind,
                                )
                            }.toImmutableList(),
                        )
                    }.toImmutableList(),
                    attempts = snapshot.attempts.map { attempt ->
                        AiRouterAttemptUi(
                            id = attempt.id,
                            targetLabel = targetLabelById[attempt.targetId]
                                ?.takeIf(String::isNotBlank)
                                ?: "${attempt.providerName} · ${attempt.modelName}",
                            credentialLabel = attempt.credentialLabel,
                            success = attempt.success,
                            failureKind = attempt.failureKind,
                            latencyMs = attempt.latencyMs,
                            firstEventMs = attempt.firstEventMs,
                        )
                    }.toImmutableList(),
                )
            }.collect { loaded ->
                _uiState.update { current ->
                    loaded.copy(
                        selectedTab = current.selectedTab,
                        editor = current.editor,
                        saving = current.saving,
                        providerSearchQuery = current.providerSearchQuery,
                        providerFilter = current.providerFilter,
                    ).withProviderDashboardFilters()
                }
            }
        }
    }

    fun onIntent(intent: AiRouterIntent) {
        when (intent) {
            is AiRouterIntent.SelectTab -> _uiState.update { it.copy(selectedTab = intent.tab) }
            is AiRouterIntent.OpenCredential -> openCredential(intent.id)
            is AiRouterIntent.OpenCredentialForProvider -> openCredentialForProvider(
                providerId = intent.providerId,
                providerName = intent.providerName,
            )
            is AiRouterIntent.UpdateCredential -> updateEditor(intent.value)
            AiRouterIntent.SaveCredential -> saveCredential()
            is AiRouterIntent.DeleteCredential -> deleteCredential(intent.id)
            is AiRouterIntent.OpenProviderConfig -> openProviderConfig(intent.providerId)
            is AiRouterIntent.OpenProviderCredentials -> openProviderCredentials(intent.providerId)
            is AiRouterIntent.UpdateProviderConfig -> updateEditor(intent.value)
            AiRouterIntent.TestProviderConfig -> testProviderConfig()
            AiRouterIntent.SaveProviderConfig -> saveProviderConfig()
            is AiRouterIntent.OpenRoute -> openRoute(intent.id)
            is AiRouterIntent.UpdateRoute -> updateEditor(intent.value)
            AiRouterIntent.SaveRoute -> saveRoute()
            is AiRouterIntent.DeleteRoute -> deleteRoute(intent.id)
            is AiRouterIntent.OpenTarget -> openTarget(intent.routeId, intent.id)
            is AiRouterIntent.UpdateTarget -> updateEditor(intent.value)
            AiRouterIntent.SaveTarget -> saveTarget()
            is AiRouterIntent.DeleteTarget -> deleteTarget(intent.id)
            is AiRouterIntent.ResetTargetHealth -> resetHealth(targetId = intent.id)
            is AiRouterIntent.ResetCredentialHealth -> resetHealth(credentialId = intent.id)
            is AiRouterIntent.StartOAuth -> startOAuth(intent.providerId)
            is AiRouterIntent.SyncOAuthModels -> syncOAuthModels(intent.credentialId)
            is AiRouterIntent.UpdateProviderSearch -> updateProviderSearch(intent.query)
            is AiRouterIntent.SelectProviderFilter -> selectProviderFilter(intent.filter)
            is AiRouterIntent.CreateComboTemplate -> createComboTemplate(intent.templateId)
            AiRouterIntent.OpenLocalGgufCatalog -> {
                _effects.tryEmit(AiRouterEffect.OpenUrl(ExternalAssetCatalog.ggufFolderUrl))
            }
            AiRouterIntent.ChooseLocalGguf -> {
                _effects.tryEmit(AiRouterEffect.OpenLocalGgufPicker)
            }
            is AiRouterIntent.LocalGgufSelected -> importLocalGguf(intent.uri)
            AiRouterIntent.DismissEditor -> updateEditor(null)
        }
    }

    private fun importLocalGguf(uri: String) {
        val editor = _uiState.value.editor as? AiRouterEditor.ProviderConfig
            ?: return
        if (editor.protocol != io.legado.app.domain.model.AiProtocol.LOCAL_GGUF) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(saving = true) }
            val result = if (!localAiEngineGateway.nativeRuntimeAvailable) {
                Result.failure(IllegalStateException("Native local AI runtime is unavailable for this device"))
            } else {
                localAiEngineGateway.importModel(uri)
            }
            result.onSuccess { metadata ->
                val option = AiRouterModelOptionUi(
                    id = metadata.path,
                    name = metadata.name,
                    contextWindow = metadata.contextWindow,
                    maxOutputTokens = metadata.contextWindow,
                )
                _uiState.update { state ->
                    val current = state.editor as? AiRouterEditor.ProviderConfig ?: return@update state
                    state.copy(
                        saving = false,
                        editor = current.copy(
                            baseUrl = metadata.path,
                            modelId = metadata.path,
                            modelName = metadata.name,
                            contextWindow = metadata.contextWindow.toString(),
                            maxOutputTokens = metadata.contextWindow.toString(),
                            discoveredModels = listOf(option).toImmutableList(),
                            localModelSizeBytes = metadata.sizeBytes,
                            localModelSha256 = metadata.sha256,
                            localRuntimeProfile = with(metadata.runtimeProfile) {
                                "${threads} threads · context ${contextWindow} · batch ${batchSize}/${microBatchSize}"
                            },
                            localPrimaryAbi = metadata.primaryAbi,
                            localTotalMemoryMb = metadata.totalMemoryMb,
                            localRuntimeAvailable = true,
                            testStatus = AiConnectionStatus.UNVERIFIED,
                            testMessage = "GGUF đã được kiểm tra; hãy Test rồi Lưu",
                            testLatencyMs = null,
                        ),
                    )
                }
                _effects.tryEmit(AiRouterEffect.ShowMessage("Đã nạp model GGUF: ${metadata.name}"))
            }.onFailure { error ->
                _uiState.update { it.copy(saving = false) }
                _effects.tryEmit(
                    AiRouterEffect.ShowMessage(error.message ?: "Không thể nạp model GGUF")
                )
            }
        }
    }

    private fun updateProviderSearch(query: String) {
        _uiState.update {
            it.copy(providerSearchQuery = query).withProviderDashboardFilters()
        }
    }

    private fun selectProviderFilter(filter: String) {
        _uiState.update {
            it.copy(providerFilter = filter).withProviderDashboardFilters()
        }
    }

    private fun openProviderConfig(providerId: String) {
        val entry = AiProviderCatalog.byId(providerId)
        if (entry == null) {
            _effects.tryEmit(AiRouterEffect.ShowMessage("Provider catalog không tồn tại"))
            return
        }
        val providerProfileId = "catalog_${entry.id}"
        viewModelScope.launch {
            val savedProvider = profileGateway.getProvider(providerProfileId)
            val savedModels = if (savedProvider != null) {
                profileGateway.observeModels().first()
                    .filter { model -> model.providerId == providerProfileId && model.enabled }
            } else {
                emptyList()
            }
            val modelOptions = if (savedModels.isNotEmpty()) {
                savedModels.map { model ->
                    AiRouterModelOptionUi(
                        id = model.modelId,
                        name = model.displayName,
                        contextWindow = model.contextWindow,
                        maxOutputTokens = model.maxOutputTokens,
                    )
                }
            } else {
                entry.models.map { model ->
                    AiRouterModelOptionUi(
                        id = model.id,
                        name = model.name,
                        contextWindow = model.contextWindow,
                        maxOutputTokens = model.maxOutputTokens,
                    )
                }
            }
            val primaryModel = preferredProviderEditorModel(modelOptions)
            val providerName = savedProvider?.name ?: entry.name
            val hasStoredSecret = _uiState.value.credentials.any { credential ->
                credential.providerId == providerProfileId &&
                    credential.oauthProvider == null &&
                    credential.hasSecret
            }
            updateEditor(
                AiRouterEditor.ProviderConfig(
                    catalogId = entry.id,
                    providerProfileId = providerProfileId,
                    name = providerName,
                    familyId = providerFamilyIdForEditor(entry.id, providerName),
                    familyName = providerFamilyNameForEditor(entry.id, providerName),
                    connectionMode = providerConnectionModeForEditor(entry.id, entry.category),
                    category = entry.category,
                    protocol = savedProvider?.protocol ?: entry.protocol,
                    baseUrl = savedProvider?.baseUrl ?: entry.baseUrl,
                    modelsUrl = savedProvider?.modelsUrl ?: entry.modelsUrl.orEmpty(),
                    authType = savedProvider?.authType ?: entry.authType,
                    modelId = primaryModel?.id.orEmpty(),
                    modelName = primaryModel?.name.orEmpty(),
                    contextWindow = primaryModel?.contextWindow
                        ?.takeIf { it > 0 }
                        ?.toString()
                        .orEmpty(),
                    maxOutputTokens = primaryModel?.maxOutputTokens
                        ?.takeIf { it > 0 }
                        ?.toString()
                        .orEmpty(),
                    chatPath = savedProvider?.chatPath ?: entry.chatPath,
                    responsesPath = savedProvider?.responsesPath ?: entry.responsesPath,
                    messagesPath = savedProvider?.messagesPath ?: entry.messagesPath,
                    modelsPath = savedProvider?.modelsPath ?: entry.modelsPath.orEmpty(),
                    notice = entry.notice,
                    hasStoredSecret = hasStoredSecret,
                    localRuntimeAvailable = if (
                        (savedProvider?.protocol ?: entry.protocol) ==
                        io.legado.app.domain.model.AiProtocol.LOCAL_GGUF
                    ) {
                        localAiEngineGateway.nativeRuntimeAvailable
                    } else {
                        null
                    },
                    discoveredModels = modelOptions.toImmutableList(),
                )
            )
        }
    }

    private fun openProviderCredentials(providerId: String) {
        val provider = _uiState.value.providerDashboardItems.firstOrNull { it.id == providerId }
        if (provider == null) {
            _effects.tryEmit(AiRouterEffect.ShowMessage("Provider không tồn tại"))
            return
        }
        val oauthProvider = _uiState.value.oauthProviders.firstOrNull { it.id == provider.id }
        updateEditor(
            AiRouterEditor.ProviderCredentials(
                providerId = provider.id,
                providerProfileId = provider.providerProfileId,
                name = provider.name,
                connectionMode = provider.connectionMode,
                authLabel = provider.authLabel,
                statusLabel = provider.statusLabel,
                notice = provider.notice,
                oauthProviderId = oauthProvider?.id,
                oauthAvailable = oauthProvider?.available == true,
                supportsApiKey = provider.category != AiRouterProviderFilter.OAUTH && provider.requiresKey,
            )
        )
    }

    private fun testProviderConfig() {
        val restoredEditor = _uiState.value.editor as? AiRouterEditor.ProviderConfig
            ?: return
        if (_uiState.value.saving) return
        val editor = restoredEditor.normalizedEndpointConfig()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(saving = true, editor = editor) }
            val storedCredential = _uiState.value.credentials.firstOrNull { credential ->
                credential.providerId == editor.providerProfileId &&
                    credential.oauthProvider == null &&
                    credential.enabled &&
                    credential.hasSecret
            }
            val testApiKey = editor.apiKey.ifBlank {
                storedCredential?.let { routerGateway.resolveCredentialSecret(it.id) }.orEmpty()
            }
            val result = runCatching {
                testProviderDraft(
                    editor.copy(
                        apiKey = testApiKey,
                        hasStoredSecret = testApiKey.isNotBlank() || editor.hasStoredSecret,
                    ).toConnectionDraft()
                )
            }
            result.onSuccess { testResult ->
                val modelOptions = testResult.discoveredModels
                    .takeIf { it.isNotEmpty() }
                    ?.map { model ->
                        AiRouterModelOptionUi(
                            id = model.id,
                            name = model.name,
                            contextWindow = model.contextWindow,
                            maxOutputTokens = model.maxOutputTokens,
                        )
                    }
                    ?: editor.discoveredModels
                val selected = testResult.selectedModel
                _uiState.update {
                    it.copy(
                        saving = false,
                        editor = editor.copy(
                            testStatus = testResult.status,
                            testMessage = testResult.message,
                            testLatencyMs = testResult.latencyMs,
                            discoveredModels = modelOptions.toImmutableList(),
                            modelId = selected?.id ?: editor.modelId,
                            modelName = selected?.name ?: editor.modelName,
                            contextWindow = selected?.contextWindow
                                ?.takeIf { value -> value > 0 }
                                ?.toString()
                                ?: editor.contextWindow,
                            maxOutputTokens = selected?.maxOutputTokens
                                ?.takeIf { value -> value > 0 }
                                ?.toString()
                                ?: editor.maxOutputTokens,
                        ),
                    )
                }
                _effects.tryEmit(AiRouterEffect.ShowMessage(testResult.message))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        saving = false,
                        editor = editor.copy(
                            testStatus = AiConnectionStatus.ERROR,
                            testMessage = error.message ?: "Không thể test provider",
                        ),
                    )
                }
                _effects.tryEmit(
                    AiRouterEffect.ShowMessage(error.message ?: "Không thể test provider")
                )
            }
            if (editor.protocol == io.legado.app.domain.model.AiProtocol.LOCAL_GGUF) {
                runCatching { localAiEngineGateway.unload() }
            }
        }
    }

    private fun saveProviderConfig() = launchMutation(
        successMessage = "Đã lưu cấu hình provider",
    ) {
        val restoredEditor = _uiState.value.editor as? AiRouterEditor.ProviderConfig
            ?: error("Không có provider để lưu")
        val editor = restoredEditor.normalizedEndpointConfig()
        val draft = editor.toConnectionDraft()
        require(draft.providerName.isNotBlank()) { "Cần nhập tên provider" }
        require(draft.baseUrl.isNotBlank()) { "Cần nhập Base URL" }
        val hasPoolCredential = _uiState.value.credentials.any { credential ->
            credential.providerId == draft.providerProfileId &&
                credential.oauthProvider == null &&
                credential.hasSecret
        }
        if (draft.authType != AiProviderAuthType.NONE) {
            require(draft.apiKey.isNotBlank() || draft.hasStoredSecret || hasPoolCredential) {
                "Cần nhập API key hoặc token"
            }
        }
        val provider = profileGateway.saveProvider(
            AiProviderDraft(
                providerId = draft.providerProfileId,
                providerName = draft.providerName,
                protocol = draft.protocol,
                baseUrl = draft.baseUrl,
                modelsUrl = draft.modelsUrl,
                apiKey = "",
                authType = draft.authType,
                headers = draft.headers,
                chatPath = draft.chatPath,
                responsesPath = draft.responsesPath,
                messagesPath = draft.messagesPath,
                modelsPath = draft.modelsPath,
                customHeaders = draft.customHeaders,
            )
        )
        val selectedModel = AiRouterModelOptionUi(
            id = draft.modelId,
            name = draft.modelName.ifBlank { draft.modelId },
            contextWindow = draft.contextWindow,
            maxOutputTokens = draft.maxOutputTokens,
        )
        val models = (editor.discoveredModels + selectedModel)
            .filter { it.id.isNotBlank() }
            .distinctBy(AiRouterModelOptionUi::id)
        require(models.any { it.id.isNotBlank() }) { "Cần chọn model" }
        prioritizeSelectedModel(models, draft.modelId).forEachIndexed { index, model ->
            profileGateway.saveModel(
                AiModelDraft(
                    providerId = provider.id,
                    modelName = model.name.ifBlank { model.id },
                    modelId = model.id,
                    contextWindow = model.contextWindow,
                    maxOutputTokens = model.maxOutputTokens,
                    sortNumber = index,
                )
            )
        }
        if (draft.authType != AiProviderAuthType.NONE && draft.apiKey.isNotBlank()) {
            routerGateway.saveCredential(
                AiCredentialDraft(
                    providerId = provider.id,
                    label = "${draft.providerName} API key",
                    kind = AiCredentialKind.API_KEY,
                    secret = draft.apiKey,
                    enabled = true,
                )
            )
        }
    }

    private fun AiRouterEditor.ProviderConfig.toConnectionDraft(): AiProviderConnectionDraft {
        val entry = AiProviderCatalog.byId(catalogId)
        return AiProviderConnectionDraft(
            catalogId = catalogId,
            providerProfileId = providerProfileId,
            providerName = name,
            familyId = familyId,
            connectionMode = connectionMode,
            protocol = protocol,
            baseUrl = baseUrl,
            modelsUrl = modelsUrl.takeIf(String::isNotBlank),
            apiKey = apiKey,
            hasStoredSecret = hasStoredSecret,
            authType = authType,
            headers = entry?.headers.orEmpty(),
            customHeaders = entry?.customHeaders.orEmpty(),
            chatPath = chatPath.ifBlank { entry?.chatPath ?: "/chat/completions" },
            responsesPath = responsesPath.ifBlank { entry?.responsesPath ?: "/responses" },
            messagesPath = messagesPath.ifBlank { entry?.messagesPath ?: "/v1/messages" },
            modelsPath = modelsPath.takeIf(String::isNotBlank) ?: entry?.modelsPath,
            modelId = modelId,
            modelName = modelName.ifBlank { modelId },
            contextWindow = contextWindow.toIntOrNull() ?: 0,
            maxOutputTokens = maxOutputTokens.toIntOrNull() ?: 0,
        )
    }

    private fun AiRouterEditor.ProviderConfig.normalizedEndpointConfig(): AiRouterEditor.ProviderConfig {
        val entry = AiProviderCatalog.byId(catalogId) ?: return this
        val normalizedProtocol = protocolForRebasedEndpoint(
            currentProtocol = protocol,
            previousBaseUrl = entry.baseUrl,
            newBaseUrl = baseUrl,
        )
        val staleCatalogModel = shouldClearCatalogModelForEndpoint(
            catalogProtocol = entry.protocol,
            catalogBaseUrl = entry.baseUrl,
            newBaseUrl = baseUrl,
            selectedModelId = modelId,
            catalogModelIds = entry.models.mapTo(mutableSetOf()) { it.id },
        )
        return copy(
            protocol = normalizedProtocol,
            modelsUrl = rebaseDerivedModelsUrl(
                previousBaseUrl = entry.baseUrl,
                newBaseUrl = baseUrl,
                currentModelsUrl = modelsUrl.ifBlank { entry.modelsUrl.orEmpty() },
            ),
            modelId = modelId.takeUnless { staleCatalogModel }.orEmpty(),
            modelName = modelName.takeUnless { staleCatalogModel }.orEmpty(),
        )
    }

    private fun providerFamilyIdForEditor(id: String, name: String): String {
        val text = "$id $name".lowercase()
        return when {
            "opencode" in text -> AiRouterProviderFamily.OPENCODE
            "mimo" in text || "xiaomi" in text -> AiRouterProviderFamily.MIMO
            "local" in text || "gguf" in text -> AiRouterProviderFamily.LOCAL_GGUF
            else -> id
        }
    }

    private fun providerFamilyNameForEditor(id: String, name: String): String =
        when (providerFamilyIdForEditor(id, name)) {
            AiRouterProviderFamily.OPENCODE -> "OpenCode"
            AiRouterProviderFamily.MIMO -> "MiMo"
            AiRouterProviderFamily.LOCAL_GGUF -> "Local GGUF"
            else -> name
        }

    private fun providerConnectionModeForEditor(id: String, category: String): String =
        when {
            id == "opencode_free" -> "Free Console"
            id == "opencode_go" -> "Go/API"
            id == "mimo_free" -> "Free"
            "token_plan" in id -> "Token Plan"
            id == "xiaomi_mimo" -> "API"
            category == "local" -> "Local file"
            category == "free" -> "Free"
            category == "free_tier" -> "Free tier"
            category == "subscription_key" -> "Subscription key"
            else -> "API key"
        }

    private fun isOpenCodeFreeModel(model: AiAvailableModel): Boolean {
        val id = model.id.trim()
        return id !in OPENCODE_RETIRED_FREE_MODELS &&
            id in OPENCODE_KNOWN_FREE_MODELS
    }

    private fun createComboTemplate(templateId: String) = launchMutation(
        successMessage = "Đã tạo combo fallback; hãy mở combo để chỉnh tác vụ và thứ tự",
        closeEditor = false,
    ) {
        val template = comboTemplates.firstOrNull { it.id == templateId }
            ?: error("Combo mẫu không tồn tại")
        val state = _uiState.value
        val selectedModels = template.providerOrder.flatMap { providerId ->
            state.models.filter { it.providerId == providerId }.take(MAX_COMBO_MODELS_PER_PROVIDER)
        }.distinctBy(AiRouterModelUi::id)
        require(selectedModels.isNotEmpty()) {
            "Chưa có provider phù hợp; hãy nạp provider hoặc đăng nhập OAuth trước"
        }
        aiRouterTaskTypes.forEach { taskType ->
            val routeName = comboRouteName(template.name, taskType)
            val existing = state.routes.firstOrNull {
                it.name == routeName && it.taskType == taskType
            }
            existing?.targets?.forEach { target -> routerGateway.deleteTarget(target.id) }
            val route = routerGateway.saveRoute(
                AiRouteProfileDraft(
                    id = existing?.id,
                    name = routeName,
                    taskType = taskType,
                    strategy = template.strategy,
                    maxAttempts = selectedModels.size.coerceAtLeast(1),
                    stickySession = true,
                    enabled = true,
                )
            )
            selectedModels.forEachIndexed { index, model ->
                val modelPriority = selectedModels.indexOfFirst { it.id == model.id }
                routerGateway.saveTarget(
                    AiRouteTargetDraft(
                        routeProfileId = route.id,
                        modelProfileId = model.id,
                        credentialId = null,
                        priority = if (template.strategy == AiRouteStrategy.PRIORITY) {
                            modelPriority.coerceAtLeast(0)
                        } else {
                            0
                        },
                        weight = 1,
                        maxConcurrency = 1,
                        enabled = true,
                        sortNumber = index,
                    )
                )
            }
        }
    }

    private suspend fun installCatalogEntry(providerId: String) {
        val providerProfileId = "catalog_${providerId}"
        val existingModels = if (profileGateway.getProvider(providerProfileId) != null) {
            profileGateway.observeModels()
                .first()
                .filter { it.providerId == providerProfileId && it.enabled && it.modelId.isNotBlank() }
        } else {
            emptyList()
        }
        val entry = AiProviderCatalog.byId(providerId)
            ?: error("Provider catalog không tồn tại")
        val provider = profileGateway.saveProvider(
            AiProviderDraft(
                providerId = providerProfileId,
                providerName = entry.name,
                protocol = entry.protocol,
                baseUrl = entry.baseUrl,
                modelsUrl = entry.modelsUrl,
                apiKey = "",
                authType = entry.authType,
                headers = entry.headers,
                chatPath = entry.chatPath,
                responsesPath = entry.responsesPath,
                messagesPath = entry.messagesPath,
                modelsPath = entry.modelsPath,
                customHeaders = entry.customHeaders,
            )
        )
        val catalogModels = entry.models.map {
            AiAvailableModel(it.id, it.name, it.contextWindow, it.maxOutputTokens)
        }
        val discoveryResult = if (entry.modelsUrl != null) {
            aiTextGateway.fetchModels(
                AiProviderConfig(
                    id = provider.id,
                    name = entry.name,
                    protocol = entry.protocol,
                    baseUrl = entry.baseUrl,
                    apiKey = "",
                    authType = entry.authType,
                    modelsUrl = entry.modelsUrl,
                    headers = entry.headers,
                    chatPath = entry.chatPath,
                    responsesPath = entry.responsesPath,
                    messagesPath = entry.messagesPath,
                    modelsPath = entry.modelsPath,
                    customHeaders = entry.customHeaders,
                )
            )
        } else {
            Result.success(emptyList())
        }
        val fetchedModels = discoveryResult.getOrDefault(emptyList())
        val selectedModels = when (entry.id) {
            "opencode_free" -> selectDiscoveredCatalogModels(
                catalogModels = catalogModels,
                discoveredModels = fetchedModels,
                discoverySucceeded = discoveryResult.isSuccess,
                accept = ::isOpenCodeFreeModel,
            )
            "mimo_free" -> catalogModels
            else -> catalogModels + fetchedModels
        }.distinctBy(AiAvailableModel::id)
        val savedModels = profileGateway.importProviderModels(provider.id, selectedModels)
        if (entry.id == "opencode_free" && discoveryResult.isSuccess && fetchedModels.isNotEmpty()) {
            val activeModelIds = selectedModels.mapTo(hashSetOf(), AiAvailableModel::id)
            val staleModels = existingModels.filter { it.modelId !in activeModelIds }
            if (staleModels.isNotEmpty()) {
                val staleProfileIds = staleModels.mapTo(hashSetOf()) { it.id }
                routerGateway.observeSnapshot().first().targets
                    .filter { it.modelProfileId in staleProfileIds }
                    .forEach { routerGateway.deleteTarget(it.id) }
                staleModels.forEach { profileGateway.deleteModel(it.id) }
            }
        }
        if (profileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER) == null) {
            savedModels.minWithOrNull(
                compareBy<io.legado.app.data.entities.AiModelProfile> {
                    catalogModelRank(it.modelId)
                }.thenBy { it.displayName.lowercase() }
            )?.let { profileGateway.setDefaultModel(it.id) }
        }
        ensureGeneratedFreeRoutes()
    }

    private suspend fun ensureGeneratedFreeRoutes() {
        val models = profileGateway.observeModels().first()
        val modelIds = selectGeneratedFreeRouteModelIds(
            models = models,
            providerOrder = AUTO_INSTALL_PROVIDER_PROFILE_IDS,
            maxModelsPerProvider = MAX_AUTO_MODELS_PER_PROVIDER,
            eligibleModelIdsByProvider = mapOf(
                "catalog_opencode_free" to OPENCODE_KNOWN_FREE_MODELS,
            ),
        )
        if (modelIds.isEmpty()) return
        val retiredModelIds = models
            .filter { model ->
                model.providerId in RETIRED_AUTO_PROVIDER_PROFILE_IDS ||
                    (model.providerId == "catalog_opencode_free" &&
                        model.modelId in OPENCODE_RETIRED_FREE_MODELS)
            }
            .mapTo(hashSetOf()) { model -> model.id }
        val snapshot = routerGateway.observeSnapshot().first()
        generatedFreeRouteSpecs.forEach { spec ->
            val taskRoutes = snapshot.routes.filter { it.taskType == spec.taskType }
            val namedRoute = taskRoutes.firstOrNull { it.name == spec.name }
            if (namedRoute == null && taskRoutes.isNotEmpty()) {
                return@forEach
            }
            val rawExistingTargets = namedRoute?.let { route ->
                snapshot.targets.filter { it.routeProfileId == route.id }
            }.orEmpty()
            rawExistingTargets
                .filter { target -> target.isRetiredGeneratedRouteTarget(retiredModelIds) }
                .forEach { target -> routerGateway.deleteTarget(target.id) }
            val existingTargets = activeGeneratedRouteTargets(rawExistingTargets, retiredModelIds)
            val hasExternalTargets = hasExternalGeneratedRouteTargets(
                targets = existingTargets,
                generatedModelIds = modelIds,
                retiredGeneratedModelIds = retiredModelIds,
            )
            val maxConcurrency = 1
            val route = if (hasExternalTargets && namedRoute != null) {
                val desiredMaxAttempts = existingTargets.size.coerceIn(1, MAX_GENERATED_ROUTE_ATTEMPTS)
                if (
                    namedRoute.strategy != AiRouteStrategy.PRIORITY ||
                    !namedRoute.stickySession ||
                    !namedRoute.enabled ||
                    namedRoute.maxAttempts != desiredMaxAttempts
                ) {
                    // OAuth/custom targets may be added to the generated free route at startup.
                    // Preserve them and only normalize the composite route contract here.
                    routerGateway.saveRoute(
                        AiRouteProfileDraft(
                            id = namedRoute.id,
                            name = namedRoute.name,
                            taskType = namedRoute.taskType,
                            strategy = AiRouteStrategy.PRIORITY,
                            maxAttempts = desiredMaxAttempts,
                            stickySession = true,
                            enabled = true,
                            makeDefault = namedRoute.isDefault,
                            sortNumber = namedRoute.sortNumber,
                        )
                    )
                } else {
                    namedRoute
                }
            } else if (shouldRebuildGeneratedRoute(
                    route = namedRoute,
                    targets = existingTargets,
                    modelIds = modelIds,
                    maxConcurrency = maxConcurrency,
                    strategy = spec.strategy,
                )
            ) {
                // Rebuild only generated routes. A user-created route for the task always wins.
                existingTargets.forEach { routerGateway.deleteTarget(it.id) }
                routerGateway.saveRoute(
                    AiRouteProfileDraft(
                        id = namedRoute?.id,
                        name = spec.name,
                        taskType = spec.taskType,
                        strategy = spec.strategy,
                        maxAttempts = modelIds.size.coerceAtLeast(1),
                        stickySession = true,
                        enabled = true,
                        makeDefault = true,
                    )
                ).also { savedRoute ->
                    modelIds.forEachIndexed { index, modelId ->
                        routerGateway.saveTarget(
                            AiRouteTargetDraft(
                                routeProfileId = savedRoute.id,
                                modelProfileId = modelId,
                                credentialId = null,
                                priority = if (spec.strategy == AiRouteStrategy.PRIORITY) index else 0,
                                weight = 1,
                                maxConcurrency = maxConcurrency,
                                enabled = true,
                                sortNumber = index,
                            )
                        )
                    }
                }
            } else {
                requireNotNull(namedRoute)
            }
            if (spec.taskType == AiTaskType.TRANSLATE_CHAPTER && route.name == FREE_TRANSLATION_ROUTE) {
                routerGateway.observeSnapshot()
                    .first()
                    .targets
                    .filter { target ->
                        target.routeProfileId == route.id && target.maxConcurrency != 1
                    }
                    .forEach { target ->
                        routerGateway.saveTarget(
                            AiRouteTargetDraft(
                                id = target.id,
                                routeProfileId = target.routeProfileId,
                                modelProfileId = target.modelProfileId,
                                credentialId = target.credentialId,
                                priority = target.priority,
                                weight = target.weight,
                                maxConcurrency = 1,
                                enabled = target.enabled,
                                sortNumber = target.sortNumber,
                            )
                        )
                    }
            }
            ensureGeneratedPresetRouteBinding(
                taskType = spec.taskType,
                routeId = route.id,
                modelIds = modelIds,
            )
        }
    }

    private suspend fun ensureGeneratedPresetRouteBinding(
        taskType: String,
        routeId: String,
        modelIds: List<String>,
    ) {
        val existing = profileGateway.getTaskPreset(taskType)
        if (existing?.runtimeOptions?.routeProfileId == routeId) return
        val modelProfileId = existing?.model?.id
            ?.takeIf(modelIds::contains)
            ?: modelIds.first()
        profileGateway.saveTaskPreset(
            AiTaskPresetDraft(
                presetId = existing?.id,
                taskType = taskType,
                name = existing?.name ?: "Free fallback · ${comboTaskLabel(taskType)}",
                description = existing?.description.orEmpty(),
                modelProfileId = modelProfileId,
                promptTemplate = existing?.promptTemplate
                    ?.takeIf(String::isNotBlank)
                    ?: AiPromptCatalog.defaultPrompt(taskType),
                params = existing?.params ?: io.legado.app.domain.model.AiGenerationParams(),
                runtimeOptions = existing?.runtimeOptions
                    ?.copy(routeProfileId = routeId)
                    ?: io.legado.app.domain.model.AiTaskRuntimeOptions(routeProfileId = routeId),
                enabled = true,
                makeDefault = true,
            )
        )
    }

    private fun catalogModelRank(modelId: String): Int {
        val normalized = modelId.lowercase()
        val geminiRank = PREFERRED_GEMINI_MODELS.indexOf(normalized)
        return when {
            geminiRank >= 0 -> geminiRank
            "gemini" in normalized && "flash-lite" in normalized -> 20
            "gemini" in normalized && "flash" in normalized -> 30
            "gemini" in normalized && normalized.endsWith("-preview") -> 50
            normalized == "mimo-v2.5-free" -> 60
            normalized.endsWith("-free") -> 70
            normalized == "big-pickle" -> 80
            "gemini" in normalized -> 100
            else -> 90
        }
    }

    private fun startOAuth(providerId: String) {
        if (_uiState.value.saving) return
        if (_uiState.value.oauthProviders.firstOrNull { it.id == providerId }?.available != true) {
            _effects.tryEmit(AiRouterEffect.ShowMessage("Adapter OAuth này chưa sẵn sàng để đăng nhập"))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            runCatching { oauthGateway.begin(providerId) }
                .onSuccess { authorization ->
                    authorization.userCode?.takeIf(String::isNotBlank)?.let { code ->
                        _effects.emit(AiRouterEffect.ShowMessage("Mã xác nhận: $code"))
                    }
                    _effects.emit(AiRouterEffect.OpenUrl(authorization.authorizationUrl))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(saving = false) }
                    _effects.emit(
                        AiRouterEffect.ShowMessage(error.message ?: "Không thể bắt đầu OAuth")
                    )
                }
        }
    }

    private fun syncOAuthModels(credentialId: String) {
        if (_uiState.value.saving) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(saving = true) }
            oauthGateway.syncModels(credentialId)
                .onSuccess { models ->
                    _effects.emit(
                        AiRouterEffect.ShowMessage(
                            "Đã xác thực tài khoản và đồng bộ ${models.size} model khả dụng"
                        )
                    )
                }
                .onFailure { error ->
                    _effects.emit(
                        AiRouterEffect.ShowMessage(
                            error.message ?: "Không thể kiểm tra tài khoản OAuth"
                        )
                    )
                }
            _uiState.update { it.copy(saving = false) }
        }
    }

    private fun openCredential(id: String?) {
        val credential = _uiState.value.credentials.firstOrNull { it.id == id }
        updateEditor(
            AiRouterEditor.Credential(
                id = credential?.id,
                providerId = credential?.providerId ?: _uiState.value.providers.firstOrNull()?.id.orEmpty(),
                label = credential?.label.orEmpty(),
                kind = credential?.kind ?: io.legado.app.domain.model.AiCredentialKind.API_KEY,
                enabled = credential?.enabled ?: true,
                hasStoredSecret = credential?.hasSecret == true,
            )
        )
    }

    private fun openCredentialForProvider(providerId: String, providerName: String) {
        updateEditor(
            AiRouterEditor.Credential(
                providerId = providerId,
                label = "$providerName API key",
                kind = AiCredentialKind.API_KEY,
                enabled = true,
            )
        )
    }

    private fun openRoute(id: String?) {
        val route = _uiState.value.routes.firstOrNull { it.id == id }
        updateEditor(
            AiRouterEditor.Route(
                id = route?.id,
                name = route?.name.orEmpty(),
                taskType = route?.taskType ?: io.legado.app.domain.model.AiTaskType.CHAT,
                strategy = route?.strategy ?: io.legado.app.domain.model.AiRouteStrategy.PRIORITY,
                maxAttempts = route?.maxAttempts?.toString() ?: "3",
                stickySession = route?.stickySession ?: true,
                enabled = route?.enabled ?: true,
            )
        )
    }

    private fun openTarget(routeId: String, id: String?) {
        val target = _uiState.value.routes
            .flatMap(AiRouterRouteUi::targets)
            .firstOrNull { it.id == id }
        updateEditor(
            AiRouterEditor.Target(
                id = target?.id,
                routeProfileId = target?.routeProfileId ?: routeId,
                modelProfileId = target?.modelProfileId ?: _uiState.value.models.firstOrNull()?.id.orEmpty(),
                selectedModelProfileIds = listOfNotNull(
                    target?.modelProfileId ?: _uiState.value.models.firstOrNull()?.id,
                ).toImmutableList(),
                credentialId = target?.credentialId.orEmpty(),
                priority = target?.priority?.toString() ?: "0",
                weight = target?.weight?.toString() ?: "1",
                maxConcurrency = target?.maxConcurrency?.toString() ?: "0",
                enabled = target?.enabled ?: true,
            )
        )
    }

    private fun saveCredential() = launchMutation("Đã lưu credential") {
        val editor = _uiState.value.editor as? AiRouterEditor.Credential
            ?: error("Không có credential để lưu")
        routerGateway.saveCredential(
            AiCredentialDraft(
                id = editor.id,
                providerId = editor.providerId,
                label = editor.label,
                kind = editor.kind,
                secret = editor.secret,
                enabled = editor.enabled,
            )
        )
    }

    private fun saveRoute() = launchMutation("Đã lưu tuyến AI") {
        val editor = _uiState.value.editor as? AiRouterEditor.Route
            ?: error("Không có tuyến để lưu")
        routerGateway.saveRoute(
            AiRouteProfileDraft(
                id = editor.id,
                name = editor.name,
                taskType = editor.taskType,
                strategy = editor.strategy,
                maxAttempts = editor.maxAttempts.toIntOrNull() ?: 3,
                stickySession = editor.stickySession,
                enabled = editor.enabled,
            )
        )
    }

    private fun saveTarget() = launchMutation("Đã lưu đích tuyến") {
        val editor = _uiState.value.editor as? AiRouterEditor.Target
            ?: error("Không có đích tuyến để lưu")
        val state = _uiState.value
        val route = state.routes.firstOrNull { it.id == editor.routeProfileId }
        val modelIds = if (editor.id == null) {
            editor.selectedModelProfileIds
                .takeIf { it.isNotEmpty() }
                ?: listOf(editor.modelProfileId)
        } else {
            listOf(editor.modelProfileId)
        }.filter(String::isNotBlank).distinct()
        require(modelIds.isNotEmpty()) { "Cần chọn model" }
        if (editor.id == null && route != null && route.maxAttempts < modelIds.size) {
            routerGateway.saveRoute(
                AiRouteProfileDraft(
                    id = route.id,
                    name = route.name,
                    taskType = route.taskType,
                    strategy = route.strategy,
                    maxAttempts = modelIds.size,
                    stickySession = route.stickySession,
                    enabled = route.enabled,
                    makeDefault = false,
                )
            )
        }
        val basePriority = editor.priority.toIntOrNull() ?: 0
        val existingTargetCount = route?.targets?.size ?: 0
        modelIds.forEachIndexed { index, modelId ->
            val modelIndex = modelIds.indexOf(modelId).coerceAtLeast(0)
            routerGateway.saveTarget(
                AiRouteTargetDraft(
                    id = editor.id?.takeIf { modelIds.size == 1 },
                    routeProfileId = editor.routeProfileId,
                    modelProfileId = modelId,
                    credentialId = null,
                    priority = basePriority + modelIndex,
                    weight = editor.weight.toIntOrNull() ?: 1,
                    maxConcurrency = editor.maxConcurrency.toIntOrNull() ?: 0,
                    enabled = editor.enabled,
                    sortNumber = existingTargetCount + index,
                )
            )
        }
    }

    private fun deleteCredential(id: String) = launchMutation("Đã xoá credential") {
        routerGateway.deleteCredential(id)
    }

    private fun deleteRoute(id: String) = launchMutation("Đã xoá tuyến AI") {
        routerGateway.deleteRoute(id)
    }

    private fun deleteTarget(id: String) = launchMutation("Đã xoá đích tuyến") {
        routerGateway.deleteTarget(id)
    }

    private fun resetHealth(targetId: String? = null, credentialId: String? = null) =
        launchMutation("Đã đặt lại trạng thái sức khoẻ") {
            routerGateway.resetHealth(targetId, credentialId)
        }

    private fun launchMutation(
        successMessage: String,
        closeEditor: Boolean = true,
        block: suspend () -> Unit,
    ) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            runCatching { block() }
                .onSuccess {
                    _uiState.update {
                        it.copy(editor = if (closeEditor) null else it.editor, saving = false)
                    }
                    _effects.tryEmit(AiRouterEffect.ShowMessage(successMessage))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(saving = false) }
                    _effects.tryEmit(
                        AiRouterEffect.ShowMessage(error.message ?: "Không thể cập nhật AI Router")
                    )
                }
        }
    }

    private fun updateEditor(editor: AiRouterEditor?) {
        _uiState.update { it.copy(editor = editor) }
    }

    private data class ComboTemplate(
        val id: String,
        val name: String,
        val description: String,
        val strategy: String,
        val providerOrder: List<String>,
    )

    private data class GeneratedFreeRouteSpec(
        val taskType: String,
        val name: String,
        val strategy: String,
    )

    private val generatedFreeRouteSpecs: List<GeneratedFreeRouteSpec>
        get() = aiRouterTaskTypes.map { taskType ->
            GeneratedFreeRouteSpec(
                taskType = taskType,
                name = when (taskType) {
                    AiTaskType.TRANSLATE_CHAPTER -> FREE_TRANSLATION_ROUTE
                    AiTaskType.CHAT -> FREE_CHAT_ROUTE
                    else -> "${comboTaskLabel(taskType)} · Free fallback"
                },
                strategy = AiRouteStrategy.PRIORITY,
            )
        }

    private fun comboRouteName(templateName: String, taskType: String): String =
        "$templateName · ${comboTaskLabel(taskType)}"

    private fun comboTaskLabel(taskType: String): String = when (taskType) {
        AiTaskType.TRANSLATE_CHAPTER -> "Dịch"
        AiTaskType.CHAT -> "Chat"
        AiTaskType.SUMMARIZE_CHAPTER -> "Tóm tắt chương"
        AiTaskType.SUMMARIZE_BOOK -> "Tóm tắt sách"
        AiTaskType.EXPLAIN_SELECTION -> "Giải thích"
        AiTaskType.CLEAN_SELECTION -> "Làm sạch"
        AiTaskType.TEXT_FACTORY -> "Sáng tác"
        AiTaskType.REWRITE_TEXT -> "Biên tập"
        AiTaskType.AUTHORING_DIRECTOR -> "Kiến trúc sư truyện"
        AiTaskType.AUTHORING_WRITER -> "Nhà văn"
        else -> taskType
    }

    private companion object {
        const val MAX_COMBO_MODELS_PER_PROVIDER = 3
        const val MAX_AUTO_MODELS_PER_PROVIDER = 3
        const val MAX_GENERATED_ROUTE_ATTEMPTS = 20
        const val FREE_CHAT_ROUTE = "Chat AI · Free fallback"
        val AUTO_INSTALL_PROVIDER_PROFILE_IDS = listOf(
            "catalog_opencode_free",
        )
        val RETIRED_AUTO_PROVIDER_PROFILE_IDS = setOf(
            "catalog_mimo_free",
        )
        val PREFERRED_GEMINI_MODELS = listOf(
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite",
            "gemini-3.1-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
        )
        const val FREE_TRANSLATION_ROUTE = "Dịch AI · Free fallback"
        val OPENCODE_RETIRED_FREE_MODELS = setOf(
            "hy3-free",
            "laguna-s-2.1-free",
            "ling-3.0-flash-free",
            "deepseek-v4-flash-free",
            "nemotron-3-ultra-free",
            "north-mini-code-free",
            "nemotron-3-super-free",
        )
        val OPENCODE_KNOWN_FREE_MODELS = setOf(
            "big-pickle",
            "mimo-v2.5-free",
        )
        val comboTemplates = listOf(
            ComboTemplate(
                id = "subscription_free",
                name = "Subscription → Free",
                description = "Claude/Codex/Antigravity trước, rồi MiMo và OpenCode Free.",
                strategy = io.legado.app.domain.model.AiRouteStrategy.PRIORITY,
                providerOrder = listOf(
                    "oauth_claude",
                    "oauth_codex",
                    "oauth_antigravity",
                    "catalog_xiaomi_mimo",
                    "catalog_opencode_free",
                ),
            ),
            ComboTemplate(
                id = "free_first",
                name = "Free First",
                description = "OpenCode Free → Groq → OpenRouter; không dùng provider trả phí trước.",
                strategy = io.legado.app.domain.model.AiRouteStrategy.PRIORITY,
                providerOrder = listOf(
                    "catalog_opencode_free",
                    "catalog_groq",
                    "catalog_openrouter",
                ),
            ),
            ComboTemplate(
                id = "balanced_round_robin",
                name = "Cân bằng nhiều provider",
                description = "Luân phiên có trọng số giữa account subscription và provider API.",
                strategy = io.legado.app.domain.model.AiRouteStrategy.WEIGHTED_ROUND_ROBIN,
                providerOrder = listOf(
                    "oauth_antigravity",
                    "oauth_codex",
                    "catalog_gemini",
                    "catalog_xiaomi_mimo",
                    "catalog_opencode_free",
                ),
            ),
        )
    }
}

internal fun prioritizeSelectedModel(
    models: List<AiRouterModelOptionUi>,
    selectedModelId: String,
): List<AiRouterModelOptionUi> = models
    .asSequence()
    .filter { it.id.isNotBlank() }
    .distinctBy(AiRouterModelOptionUi::id)
    .sortedBy { model -> if (model.id == selectedModelId) 0 else 1 }
    .toList()

internal fun preferredProviderEditorModel(
    models: List<AiRouterModelOptionUi>,
): AiRouterModelOptionUi? = models.firstOrNull { model ->
    model.id.equals("oc/mimo-v2.5-free", ignoreCase = true)
} ?: models.firstOrNull()

internal fun selectGeneratedFreeRouteModelIds(
    models: List<AiModelProfile>,
    providerOrder: List<String>,
    maxModelsPerProvider: Int,
    eligibleModelIdsByProvider: Map<String, Set<String>> = emptyMap(),
): List<String> = providerOrder.flatMap { providerId ->
    val eligibleModelIds = eligibleModelIdsByProvider[providerId]
    models.asSequence()
        .filter {
            it.providerId == providerId &&
                it.enabled &&
                it.modelId.isNotBlank() &&
                (eligibleModelIds == null || it.modelId in eligibleModelIds)
        }
        .sortedWith(
            compareBy<AiModelProfile> { it.sortNumber }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
        .take(maxModelsPerProvider.coerceAtLeast(1))
        .map(AiModelProfile::id)
        .toList()
}.distinct()

internal fun selectDiscoveredCatalogModels(
    catalogModels: List<AiAvailableModel>,
    discoveredModels: List<AiAvailableModel>,
    discoverySucceeded: Boolean,
    accept: (AiAvailableModel) -> Boolean,
): List<AiAvailableModel> {
    val discovered = discoveredModels.filter(accept)
    return if (discoverySucceeded && discovered.isNotEmpty()) {
        discovered
    } else {
        catalogModels.filter(accept)
    }
}

internal fun hasExternalGeneratedRouteTargets(
    targets: List<io.legado.app.domain.model.AiRouteTargetConfig>,
    generatedModelIds: List<String>,
    retiredGeneratedModelIds: Set<String> = emptySet(),
): Boolean {
    val generatedModels = generatedModelIds.toSet() + retiredGeneratedModelIds
    return targets.any { target ->
        target.credentialId != null || target.modelProfileId !in generatedModels
    }
}

internal fun activeGeneratedRouteTargets(
    targets: List<io.legado.app.domain.model.AiRouteTargetConfig>,
    retiredGeneratedModelIds: Set<String>,
): List<io.legado.app.domain.model.AiRouteTargetConfig> =
    targets.filterNot { target -> target.isRetiredGeneratedRouteTarget(retiredGeneratedModelIds) }

private fun io.legado.app.domain.model.AiRouteTargetConfig.isRetiredGeneratedRouteTarget(
    retiredGeneratedModelIds: Set<String>,
): Boolean = credentialId == null && modelProfileId in retiredGeneratedModelIds

internal fun shouldRebuildGeneratedRoute(
    route: io.legado.app.domain.model.AiRouteProfileConfig?,
    targets: List<io.legado.app.domain.model.AiRouteTargetConfig>,
    modelIds: List<String>,
    maxConcurrency: Int,
    strategy: String,
): Boolean {
    val expectedModelIds = modelIds.distinct()
    if (route == null) return true
    if (!route.enabled ||
        route.strategy != strategy ||
        !route.stickySession ||
        route.maxAttempts != expectedModelIds.size.coerceAtLeast(1)
    ) {
        return true
    }
    val orderedTargets = targets.sortedWith(
        compareBy<io.legado.app.domain.model.AiRouteTargetConfig> {
            it.sortNumber
        }.thenBy { it.priority }.thenBy { it.id }
    )
    if (orderedTargets.size != expectedModelIds.size) return true
    return orderedTargets.withIndex().any { (index, target) ->
        val expectedPriority = if (strategy == AiRouteStrategy.PRIORITY) index else 0
        target.modelProfileId != expectedModelIds[index] ||
            !target.enabled ||
            target.priority != expectedPriority ||
            target.weight != 1 ||
            target.maxConcurrency != maxConcurrency
    }
}
