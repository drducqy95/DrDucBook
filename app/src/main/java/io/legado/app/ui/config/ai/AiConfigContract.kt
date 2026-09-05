package io.legado.app.ui.config.ai

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class ProviderHealthStatus {
    HEALTHY,
    DEGRADED,
    COOLDOWN,
    DISABLED,
}

@Stable
data class ProviderHealthUi(
    val providerId: String,
    val providerName: String,
    val protocol: String,
    val totalCredentials: Int,
    val activeCredentials: Int,
    val consecutiveFailures: Int,
    val cooldownUntil: Long,
    val isCooldownActive: Boolean,
    val lastFailureKind: String? = null,
    val lastUsedAt: Long? = null,
    val status: ProviderHealthStatus,
)

@Stable
data class AiConfigUiState(
    val providers: ImmutableList<AiProviderListItemUi> = persistentListOf(),
    val models: ImmutableList<AiModelListItemUi> = persistentListOf(),
    val providerHealthList: ImmutableList<ProviderHealthUi> = persistentListOf(),
    val currentModelProfileId: String? = null,
    val currentModelName: String = "",
    val providerCount: Int = 0,
    val modelCount: Int = 0,
    val presetCount: Int = 0,
    val chatBubbleEnabled: Boolean = false,
    val discoveryFrequencyHours: Long = 12L,
    val isRefreshingModels: Boolean = false,
)

@Stable
data class AiProviderListItemUi(
    val providerId: String,
    val providerName: String,
    val protocol: String,
    val baseUrl: String,
    val modelCount: Int,
    val enabled: Boolean,
    val models: ImmutableList<AiModelListItemUi> = persistentListOf()
)

@Stable
data class AiModelListItemUi(
    val providerId: String,
    val modelProfileId: String,
    val providerName: String,
    val protocol: String,
    val baseUrl: String,
    val modelName: String,
    val modelId: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val enabled: Boolean,
    val isCurrent: Boolean
)

sealed interface AiConfigIntent
{
    data class SetDefaultModel(val modelProfileId: String) : AiConfigIntent
    data class SetChatBubbleEnabled(val enabled: Boolean) : AiConfigIntent
    data class SetDiscoveryFrequency(val hours: Long) : AiConfigIntent
    data object RefreshAllModels : AiConfigIntent
    data class ResetProviderHealth(val providerId: String) : AiConfigIntent
}

sealed interface AiConfigEffect {
    data class ShowMessage(val message: String) : AiConfigEffect
}
