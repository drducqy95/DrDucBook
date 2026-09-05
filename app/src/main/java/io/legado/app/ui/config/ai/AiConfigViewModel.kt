package io.legado.app.ui.config.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiTaskType
import io.legado.app.help.config.AiChatBubbleConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.worker.ModelDiscoveryWorker
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class AiConfigViewModel(
    private val aiProfileGateway: AiProfileGateway,
    private val rawAiTextGateway: AiTextGateway,
    private val aiRouterGateway: AiRouterGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiConfigUiState(
            discoveryFrequencyHours = AppConfig.modelDiscoveryIntervalHours,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observeActiveModels(),
                aiProfileGateway.observePresets(),
                aiRouterGateway.observeSnapshot(),
            ) { providers, models, presets, snapshot ->
                val providerMap = providers.associateBy { it.id }
                val defaultTranslatePreset = presets.firstOrNull {
                    it.taskType == AiTaskType.TRANSLATE_CHAPTER && it.isDefault
                }
                val currentModelProfileId = defaultTranslatePreset?.modelProfileId
                    ?: models.firstOrNull()?.id
                val modelItems = models.mapNotNull { model ->
                    val provider = providerMap[model.providerId] ?: return@mapNotNull null
                    AiModelListItemUi(
                        providerId = provider.id,
                        modelProfileId = model.id,
                        providerName = provider.name,
                        protocol = provider.protocol,
                        baseUrl = provider.baseUrl,
                        modelName = model.displayName,
                        modelId = model.modelId,
                        contextWindow = model.contextWindow,
                        maxOutputTokens = model.maxOutputTokens,
                        enabled = provider.enabled && model.enabled,
                        isCurrent = model.id == currentModelProfileId
                    )
                }
                val modelItemsByProvider = modelItems.groupBy { it.providerId }
                val providerItems = providers.map { provider ->
                    val providerModels = modelItemsByProvider[provider.id].orEmpty().toImmutableList()
                    AiProviderListItemUi(
                        providerId = provider.id,
                        providerName = provider.name,
                        protocol = provider.protocol,
                        baseUrl = provider.baseUrl,
                        modelCount = providerModels.size,
                        enabled = provider.enabled,
                        models = providerModels
                    )
                }.toImmutableList()
                val modelNameById = models.associate { it.id to it.displayName }
                val currentModelName = currentModelProfileId
                    ?.let { modelNameById[it] }
                    .orEmpty()

                val now = System.currentTimeMillis()
                val healthList = providers.map { provider ->
                    val providerCredentials = snapshot.credentials.filter {
                        it.providerId == provider.id ||
                            it.providerId == "oauth_${provider.id}" ||
                            it.providerId == "catalog_${provider.id}" ||
                            it.oauthProvider == provider.id
                    }
                    val totalCredentials = providerCredentials.size
                    val activeCredentials = providerCredentials.count { it.enabled && it.status == "active" }
                    val providerModelIds = models.filter { it.providerId == provider.id }.map { it.id }.toSet()
                    val providerTargets = snapshot.targets.filter {
                        it.modelProfileId in providerModelIds || (it.credentialId != null && it.credentialId in providerCredentials.map { c -> c.id })
                    }
                    val consecutiveFailures = maxOf(
                        providerCredentials.maxOfOrNull { it.consecutiveFailures } ?: 0,
                        providerTargets.maxOfOrNull { it.consecutiveFailures } ?: 0
                    )
                    val cooldownUntil = maxOf(
                        providerCredentials.maxOfOrNull { it.cooldownUntil } ?: 0L,
                        providerTargets.maxOfOrNull { it.cooldownUntil } ?: 0L
                    )
                    val isCooldownActive = cooldownUntil > now
                    val lastFailureKind = providerCredentials.firstOrNull { it.lastFailureKind != null }?.lastFailureKind
                        ?: providerTargets.firstOrNull { it.lastFailureKind != null }?.lastFailureKind
                    val lastUsedAt = maxOf(
                        providerCredentials.mapNotNull { it.lastUsedAt }.maxOrNull() ?: 0L,
                        providerTargets.mapNotNull { it.lastUsedAt }.maxOrNull() ?: 0L
                    ).takeIf { it > 0L }

                    val healthStatus = when {
                        !provider.enabled -> ProviderHealthStatus.DISABLED
                        isCooldownActive -> ProviderHealthStatus.COOLDOWN
                        consecutiveFailures > 0 -> ProviderHealthStatus.DEGRADED
                        else -> ProviderHealthStatus.HEALTHY
                    }

                    ProviderHealthUi(
                        providerId = provider.id,
                        providerName = provider.name,
                        protocol = provider.protocol,
                        totalCredentials = totalCredentials,
                        activeCredentials = activeCredentials,
                        consecutiveFailures = consecutiveFailures,
                        cooldownUntil = cooldownUntil,
                        isCooldownActive = isCooldownActive,
                        lastFailureKind = lastFailureKind,
                        lastUsedAt = lastUsedAt,
                        status = healthStatus,
                    )
                }.toImmutableList()

                _uiState.update {
                    it.copy(
                        providers = providerItems,
                        models = modelItems.toImmutableList(),
                        providerHealthList = healthList,
                        currentModelProfileId = currentModelProfileId,
                        currentModelName = currentModelName,
                        providerCount = providers.size,
                        modelCount = models.size,
                        presetCount = presets.size,
                        chatBubbleEnabled = AiChatBubbleConfig.enabled,
                        discoveryFrequencyHours = AppConfig.modelDiscoveryIntervalHours,
                    )
                }
            }.collect {}
        }
    }

    fun onIntent(intent: AiConfigIntent) {
        when (intent) {
            is AiConfigIntent.SetDefaultModel -> setDefaultModel(intent.modelProfileId)
            is AiConfigIntent.SetChatBubbleEnabled -> setChatBubbleEnabled(intent.enabled)
            is AiConfigIntent.SetDiscoveryFrequency -> setDiscoveryFrequency(intent.hours)
            is AiConfigIntent.RefreshAllModels -> refreshAllModels()
            is AiConfigIntent.ResetProviderHealth -> resetProviderHealth(intent.providerId)
        }
    }

    private fun resetProviderHealth(providerId: String) {
        viewModelScope.launch {
            runCatching {
                val snapshot = aiRouterGateway.observeSnapshot().firstOrNull()
                val credentials = snapshot?.credentials.orEmpty().filter {
                    it.providerId == providerId ||
                        it.providerId == "oauth_$providerId" ||
                        it.providerId == "catalog_$providerId" ||
                        it.oauthProvider == providerId
                }
                for (cred in credentials) {
                    aiRouterGateway.resetHealth(credentialId = cred.id)
                }
                val providerModels = aiProfileGateway.observeActiveModels().firstOrNull().orEmpty()
                    .filter { it.providerId == providerId }
                    .map { it.id }.toSet()
                val targets = snapshot?.targets.orEmpty().filter {
                    it.modelProfileId in providerModels || (it.credentialId != null && it.credentialId in credentials.map { c -> c.id })
                }
                for (target in targets) {
                    aiRouterGateway.resetHealth(targetId = target.id)
                }
                if (credentials.isEmpty() && targets.isEmpty()) {
                    aiRouterGateway.resetHealth()
                }
            }.onSuccess {
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_health_reset_success))
                )
            }.onFailure { error ->
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_health_reset)
                    )
                )
            }
        }
    }

    private fun setDiscoveryFrequency(hours: Long) {
        AppConfig.modelDiscoveryIntervalHours = hours
        ModelDiscoveryWorker.schedule(appCtx, hours)
        _uiState.update { it.copy(discoveryFrequencyHours = hours) }
    }

    private fun refreshAllModels() {
        if (_uiState.value.isRefreshingModels) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingModels = true) }
            runCatching {
                val providers = aiProfileGateway.getEnabledProviders()
                var count = 0
                for (provider in providers) {
                    try {
                        val config = aiProfileGateway.toProviderConfig(provider)
                        val models = rawAiTextGateway.fetchModels(config).getOrNull()
                        if (models != null && models.isNotEmpty()) {
                            aiProfileGateway.syncDiscoveredModels(provider.id, models)
                            count += models.size
                        }
                    } catch (e: Exception) {
                        timber.log.Timber.w(e, "Refresh models failed for provider ${provider.name}")
                    }
                }
                aiProfileGateway.deprecateStaleModels()
                count
            }.onSuccess {
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_models_refreshed_success))
                )
            }.onFailure { error ->
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_models_refreshed_failed)
                    )
                )
            }
            _uiState.update { it.copy(isRefreshingModels = false) }
        }
    }

    private fun setChatBubbleEnabled(enabled: Boolean) {
        AiChatBubbleConfig.enabled = enabled
        _uiState.update { it.copy(chatBubbleEnabled = enabled) }
    }

    private fun setDefaultModel(modelProfileId: String) {
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.setDefaultModel(modelProfileId)
            }.onSuccess {
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_default_model_saved))
                )
            }.onFailure { error ->
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_default_model_save_failed)
                    )
                )
            }
        }
    }
}
