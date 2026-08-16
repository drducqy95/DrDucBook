package io.legado.app.ui.ai.router

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiCredentialKind
import io.legado.app.domain.model.AiCredentialStatus
import io.legado.app.domain.model.AiConnectionStatus
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiTaskType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiRouterUiState(
    val selectedTab: AiRouterTab = AiRouterTab.OVERVIEW,
    val registryProviderCount: Int = 0,
    val registryTextProviderCount: Int = 0,
    val registryCapabilityCount: Int = 0,
    val providers: ImmutableList<AiRouterProviderUi> = persistentListOf(),
    val oauthProviders: ImmutableList<AiRouterOAuthProviderUi> = persistentListOf(),
    val catalogProviders: ImmutableList<AiRouterCatalogProviderUi> = persistentListOf(),
    val comboTemplates: ImmutableList<AiRouterComboTemplateUi> = persistentListOf(),
    val healthSummary: AiRouterHealthSummaryUi = AiRouterHealthSummaryUi(),
    val providerSearchQuery: String = "",
    val providerFilter: String = AiRouterProviderFilter.ALL,
    val providerFilters: ImmutableList<AiRouterProviderFilterUi> = persistentListOf(),
    val providerDashboardItems: ImmutableList<AiRouterProviderDashboardItemUi> = persistentListOf(),
    val filteredProviderDashboardItems: ImmutableList<AiRouterProviderDashboardItemUi> = persistentListOf(),
    val diagnostics: ImmutableList<AiRouterDiagnosticUi> = persistentListOf(),
    val models: ImmutableList<AiRouterModelUi> = persistentListOf(),
    val credentials: ImmutableList<AiRouterCredentialUi> = persistentListOf(),
    val routes: ImmutableList<AiRouterRouteUi> = persistentListOf(),
    val attempts: ImmutableList<AiRouterAttemptUi> = persistentListOf(),
    val editor: AiRouterEditor? = null,
    val saving: Boolean = false,
)

enum class AiRouterTab {
    OVERVIEW,
    SIGN_IN,
    API_KEYS,
    COMBOS,
    MODELS,
    LOGS,
}

@Stable
data class AiRouterOAuthProviderUi(
    val id: String,
    val name: String,
    val warning: String,
    val flow: String,
    val available: Boolean,
)

@Stable
data class AiRouterCatalogProviderUi(
    val id: String,
    val name: String,
    val category: String,
    val notice: String,
    val installed: Boolean,
)

object AiRouterProviderFilter {
    const val ALL = "all"
    const val READY = "ready"
    const val ERROR = "error"
    const val FREE = "free"
    const val API = "api"
    const val OAUTH = "oauth"
    const val LOCAL = "local"

    val defaultFilters = listOf(
        ALL,
        READY,
        ERROR,
        FREE,
        API,
        OAUTH,
        LOCAL,
    )
}

object AiRouterProviderFamily {
    const val OPENCODE = "opencode"
    const val MIMO = "mimo"
    const val LOCAL_GGUF = "local_gguf"
}

@Stable
data class AiRouterProviderFilterUi(
    val id: String,
    val label: String,
    val count: Int,
)

@Stable
data class AiRouterHealthSummaryUi(
    val activeRouteCount: Int = 0,
    val readyProviderCount: Int = 0,
    val degradedProviderCount: Int = 0,
    val loginRequiredCredentialCount: Int = 0,
    val successRatePercent: Int = 0,
    val averageLatencyMs: Long = 0,
    val recentAttemptCount: Int = 0,
)

@Stable
data class AiRouterProviderDashboardItemUi(
    val id: String,
    val name: String,
    val familyId: String,
    val familyName: String,
    val connectionMode: String,
    val category: String,
    val authLabel: String,
    val status: String,
    val statusLabel: String,
    val installed: Boolean,
    val providerProfileId: String? = null,
    val modelCount: Int = 0,
    val credentialCount: Int = 0,
    val requiresKey: Boolean = false,
    val capabilityLabels: ImmutableList<String> = persistentListOf(),
    val notice: String = "",
)

@Stable
data class AiRouterDiagnosticUi(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
)

@Stable
data class AiRouterComboTemplateUi(
    val id: String,
    val name: String,
    val description: String,
)

@Stable
data class AiRouterProviderUi(
    val id: String,
    val name: String,
)

@Stable
data class AiRouterModelUi(
    val id: String,
    val providerId: String,
    val label: String,
)

@Stable
data class AiRouterCredentialUi(
    val id: String,
    val providerId: String,
    val providerName: String,
    val label: String,
    val kind: String,
    val enabled: Boolean,
    val hasSecret: Boolean,
    val cooldownUntil: Long,
    val consecutiveFailures: Int,
    val lastFailureKind: String?,
    val oauthProvider: String? = null,
    val accountLabel: String? = null,
    val expiresAt: Long? = null,
    val status: String = AiCredentialStatus.ACTIVE,
    val hasRefreshToken: Boolean = false,
)

@Stable
data class AiRouterRouteUi(
    val id: String,
    val name: String,
    val taskType: String,
    val strategy: String,
    val maxAttempts: Int,
    val stickySession: Boolean,
    val enabled: Boolean,
    val healthStatus: String = AiConnectionStatus.UNVERIFIED,
    val successRatePercent: Int = 0,
    val averageLatencyMs: Long = 0,
    val recentAttemptCount: Int = 0,
    val targets: ImmutableList<AiRouterTargetUi> = persistentListOf(),
)

@Stable
data class AiRouterTargetUi(
    val id: String,
    val routeProfileId: String,
    val modelProfileId: String,
    val modelLabel: String,
    val credentialId: String?,
    val credentialLabel: String?,
    val priority: Int,
    val weight: Int,
    val maxConcurrency: Int,
    val enabled: Boolean,
    val cooldownUntil: Long,
    val consecutiveFailures: Int,
    val lastFailureKind: String?,
)

@Stable
data class AiRouterAttemptUi(
    val id: Long,
    val targetLabel: String,
    val credentialLabel: String?,
    val success: Boolean,
    val failureKind: String?,
    val latencyMs: Long,
    val firstEventMs: Long?,
)

sealed interface AiRouterEditor {
    @Stable
    data class Credential(
        val id: String? = null,
        val providerId: String = "",
        val label: String = "",
        val kind: String = AiCredentialKind.API_KEY,
        val secret: String = "",
        val enabled: Boolean = true,
        val hasStoredSecret: Boolean = false,
    ) : AiRouterEditor

    @Stable
    data class ProviderConfig(
        val catalogId: String,
        val providerProfileId: String? = null,
        val name: String = "",
        val familyId: String = "",
        val familyName: String = "",
        val connectionMode: String = "",
        val category: String = "",
        val protocol: String = "",
        val baseUrl: String = "",
        val modelsUrl: String = "",
        val authType: String = AiProviderAuthType.BEARER,
        val apiKey: String = "",
        val modelId: String = "",
        val modelName: String = "",
        val contextWindow: String = "",
        val maxOutputTokens: String = "",
        val chatPath: String = "",
        val responsesPath: String = "",
        val messagesPath: String = "",
        val modelsPath: String = "",
        val notice: String = "",
        val hasStoredSecret: Boolean = false,
        val testStatus: String = AiConnectionStatus.UNVERIFIED,
        val testMessage: String = "",
        val testLatencyMs: Long? = null,
        val discoveredModels: ImmutableList<AiRouterModelOptionUi> = persistentListOf(),
        val localModelSizeBytes: Long = 0,
        val localModelSha256: String = "",
        val localRuntimeProfile: String = "",
        val localPrimaryAbi: String = "",
        val localTotalMemoryMb: Long = 0,
        val localRuntimeAvailable: Boolean? = null,
    ) : AiRouterEditor

    @Stable
    data class ProviderCredentials(
        val providerId: String,
        val providerProfileId: String? = null,
        val name: String,
        val connectionMode: String = "",
        val authLabel: String = "",
        val statusLabel: String = "",
        val notice: String = "",
        val oauthProviderId: String? = null,
        val oauthAvailable: Boolean = false,
        val supportsApiKey: Boolean = false,
    ) : AiRouterEditor

    @Stable
    data class Route(
        val id: String? = null,
        val name: String = "",
        val taskType: String = AiTaskType.CHAT,
        val strategy: String = AiRouteStrategy.PRIORITY,
        val maxAttempts: String = "3",
        val stickySession: Boolean = true,
        val enabled: Boolean = true,
    ) : AiRouterEditor

    @Stable
    data class Target(
        val id: String? = null,
        val routeProfileId: String,
        val modelProfileId: String = "",
        val selectedModelProfileIds: ImmutableList<String> = persistentListOf(),
        val credentialId: String = "",
        val priority: String = "0",
        val weight: String = "1",
        val maxConcurrency: String = "0",
        val enabled: Boolean = true,
    ) : AiRouterEditor
}

@Stable
data class AiRouterModelOptionUi(
    val id: String,
    val name: String,
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
)

sealed interface AiRouterIntent {
    data class SelectTab(val tab: AiRouterTab) : AiRouterIntent
    data class OpenCredential(val id: String? = null) : AiRouterIntent
    data class OpenCredentialForProvider(
        val providerId: String,
        val providerName: String,
    ) : AiRouterIntent
    data class UpdateCredential(val value: AiRouterEditor.Credential) : AiRouterIntent
    data object SaveCredential : AiRouterIntent
    data class DeleteCredential(val id: String) : AiRouterIntent

    data class OpenProviderConfig(val providerId: String) : AiRouterIntent
    data class OpenProviderCredentials(val providerId: String) : AiRouterIntent
    data class UpdateProviderConfig(val value: AiRouterEditor.ProviderConfig) : AiRouterIntent
    data object TestProviderConfig : AiRouterIntent
    data object SaveProviderConfig : AiRouterIntent

    data class OpenRoute(val id: String? = null) : AiRouterIntent
    data class UpdateRoute(val value: AiRouterEditor.Route) : AiRouterIntent
    data object SaveRoute : AiRouterIntent
    data class DeleteRoute(val id: String) : AiRouterIntent

    data class OpenTarget(val routeId: String, val id: String? = null) : AiRouterIntent
    data class UpdateTarget(val value: AiRouterEditor.Target) : AiRouterIntent
    data object SaveTarget : AiRouterIntent
    data class DeleteTarget(val id: String) : AiRouterIntent

    data class ResetTargetHealth(val id: String) : AiRouterIntent
    data class ResetCredentialHealth(val id: String) : AiRouterIntent
    data class StartOAuth(val providerId: String) : AiRouterIntent
    data class SyncOAuthModels(val credentialId: String) : AiRouterIntent
    data class UpdateProviderSearch(val query: String) : AiRouterIntent
    data class SelectProviderFilter(val filter: String) : AiRouterIntent
    data class CreateComboTemplate(val templateId: String) : AiRouterIntent
    data object OpenLocalGgufCatalog : AiRouterIntent
    data object ChooseLocalGguf : AiRouterIntent
    data class LocalGgufSelected(val uri: String) : AiRouterIntent
    data object DismissEditor : AiRouterIntent
}

sealed interface AiRouterEffect {
    data class ShowMessage(val message: String) : AiRouterEffect
    data class OpenUrl(val url: String) : AiRouterEffect
    data object OpenLocalGgufPicker : AiRouterEffect
}

val aiRouterTaskTypes = listOf(
    AiTaskType.CHAT,
    AiTaskType.TRANSLATE_CHAPTER,
    AiTaskType.SUMMARIZE_CHAPTER,
    AiTaskType.SUMMARIZE_BOOK,
    AiTaskType.EXPLAIN_SELECTION,
    AiTaskType.CLEAN_SELECTION,
    AiTaskType.TEXT_FACTORY,
    AiTaskType.REWRITE_TEXT,
    AiTaskType.AUTHORING_DIRECTOR,
    AiTaskType.AUTHORING_WRITER,
)
