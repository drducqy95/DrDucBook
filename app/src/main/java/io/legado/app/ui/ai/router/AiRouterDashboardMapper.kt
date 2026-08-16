package io.legado.app.ui.ai.router

import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.domain.model.AiConnectionStatus
import io.legado.app.domain.model.AiCredentialConfig
import io.legado.app.domain.model.AiCredentialStatus
import io.legado.app.domain.model.AiOAuthProviderConfig
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderCatalogEntry
import io.legado.app.domain.model.AiProviderCategory
import io.legado.app.domain.model.AiRouteAttemptConfig
import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouteTargetConfig
import kotlinx.collections.immutable.toImmutableList
import java.text.Normalizer
import java.util.Locale

internal fun buildProviderDashboardItems(
    catalogEntries: List<AiProviderCatalogEntry>,
    oauthProviders: List<AiOAuthProviderConfig>,
    providers: List<AiProviderProfile>,
    models: List<AiModelProfile>,
    credentials: List<AiCredentialConfig>,
): List<AiRouterProviderDashboardItemUi> {
    val providersById = providers.associateBy(AiProviderProfile::id)
    val modelCountByProvider = models.groupingBy(AiModelProfile::providerId).eachCount()
    val credentialsByProvider = credentials.groupBy(AiCredentialConfig::providerId)
    val catalogItems = catalogEntries.map { entry ->
        val providerProfileId = "catalog_${entry.id}"
        val provider = providersById[providerProfileId]
        val providerCredentials = credentialsByProvider[providerProfileId].orEmpty()
        val requiresKey = entry.authType != AiProviderAuthType.NONE
        val status = providerStatus(
            installed = provider != null,
            requiresKey = requiresKey,
            credentials = providerCredentials,
            modelCount = modelCountByProvider[providerProfileId] ?: 0,
        )
        AiRouterProviderDashboardItemUi(
            id = entry.id,
            name = entry.name,
            familyId = providerFamilyId(entry.id, entry.name),
            familyName = providerFamilyName(entry.id, entry.name),
            connectionMode = providerConnectionMode(entry.id, entry.category),
            category = entry.category,
            authLabel = authLabel(entry.authType),
            status = status,
            statusLabel = statusLabel(status),
            installed = provider != null,
            providerProfileId = provider?.id,
            modelCount = modelCountByProvider[providerProfileId] ?: entry.models.size,
            credentialCount = providerCredentials.size,
            requiresKey = requiresKey,
            capabilityLabels = entry.serviceKinds.sorted().toImmutableList(),
            notice = entry.notice,
        )
    }
    val oauthItems = oauthProviders.map { provider ->
        val providerProfileId = "oauth_${provider.id}"
        val providerCredentials = credentials.filter { it.oauthProvider == provider.id }
        val status = when {
            providerCredentials.any { it.status == AiCredentialStatus.ACTIVE && it.hasSecret } ->
                AiConnectionStatus.READY
            providerCredentials.any { it.status == AiCredentialStatus.RELOGIN_REQUIRED } ->
                AiConnectionStatus.LOGIN_REQUIRED
            providerCredentials.isNotEmpty() -> AiConnectionStatus.DEGRADED
            provider.available -> AiConnectionStatus.UNCONFIGURED
            else -> AiConnectionStatus.UNVERIFIED
        }
        AiRouterProviderDashboardItemUi(
            id = provider.id,
            name = provider.name,
            familyId = provider.id,
            familyName = provider.name,
            connectionMode = "OAuth",
            category = AiRouterProviderFilter.OAUTH,
            authLabel = "OAuth",
            status = status,
            statusLabel = statusLabel(status),
            installed = providerCredentials.isNotEmpty(),
            providerProfileId = providerProfileId.takeIf { providerCredentials.isNotEmpty() },
            modelCount = modelCountByProvider[providerProfileId] ?: 0,
            credentialCount = providerCredentials.size,
            requiresKey = true,
            capabilityLabels = listOf("llm").toImmutableList(),
            notice = provider.warning,
        )
    }
    return (catalogItems + oauthItems).sortedWith(
        compareBy<AiRouterProviderDashboardItemUi> { it.familyName.lowercase(Locale.ROOT) }
            .thenBy { it.connectionMode.lowercase(Locale.ROOT) }
            .thenBy { it.name.lowercase(Locale.ROOT) }
    )
}

internal fun buildHealthSummary(
    providerItems: List<AiRouterProviderDashboardItemUi>,
    routes: List<AiRouteProfileConfig>,
    credentials: List<AiCredentialConfig>,
    attempts: List<AiRouteAttemptConfig>,
): AiRouterHealthSummaryUi {
    val recentAttempts = attempts.take(20)
    val successCount = recentAttempts.count(AiRouteAttemptConfig::success)
    return AiRouterHealthSummaryUi(
        activeRouteCount = routes.count { it.enabled },
        readyProviderCount = providerItems.count { it.status == AiConnectionStatus.READY },
        degradedProviderCount = providerItems.count {
            it.status == AiConnectionStatus.DEGRADED ||
                it.status == AiConnectionStatus.ERROR ||
                it.status == AiConnectionStatus.LOGIN_REQUIRED
        },
        loginRequiredCredentialCount = credentials.count {
            it.status == AiCredentialStatus.RELOGIN_REQUIRED
        },
        successRatePercent = if (recentAttempts.isEmpty()) {
            0
        } else {
            successCount * 100 / recentAttempts.size
        },
        averageLatencyMs = recentAttempts
            .takeIf { it.isNotEmpty() }
            ?.map(AiRouteAttemptConfig::latencyMs)
            ?.average()
            ?.toLong()
            ?: 0,
        recentAttemptCount = recentAttempts.size,
    )
}

internal fun routeHealth(
    route: AiRouteProfileConfig,
    targets: List<AiRouteTargetConfig>,
    attempts: List<AiRouteAttemptConfig>,
): AiRouterRouteHealthUi {
    val routeAttempts = attempts.filter { it.routeProfileId == route.id }.take(20)
    val successCount = routeAttempts.count(AiRouteAttemptConfig::success)
    val hasTargetFailure = targets.any { it.consecutiveFailures > 0 || it.lastFailureKind != null }
    val status = when {
        !route.enabled -> AiConnectionStatus.UNCONFIGURED
        targets.none { it.enabled } -> AiConnectionStatus.ERROR
        hasTargetFailure -> AiConnectionStatus.DEGRADED
        routeAttempts.isNotEmpty() && successCount == 0 -> AiConnectionStatus.ERROR
        routeAttempts.isNotEmpty() -> AiConnectionStatus.READY
        else -> AiConnectionStatus.UNVERIFIED
    }
    return AiRouterRouteHealthUi(
        status = status,
        successRatePercent = if (routeAttempts.isEmpty()) 0 else successCount * 100 / routeAttempts.size,
        averageLatencyMs = routeAttempts
            .takeIf { it.isNotEmpty() }
            ?.map(AiRouteAttemptConfig::latencyMs)
            ?.average()
            ?.toLong()
            ?: 0,
        recentAttemptCount = routeAttempts.size,
    )
}

internal fun buildProviderFilters(
    items: List<AiRouterProviderDashboardItemUi>,
): List<AiRouterProviderFilterUi> =
    AiRouterProviderFilter.defaultFilters.map { filter ->
        AiRouterProviderFilterUi(
            id = filter,
            label = providerFilterLabel(filter),
            count = if (filter == AiRouterProviderFilter.ALL) {
                items.size
            } else {
                items.count { item -> item.matchesProviderFilter(filter) }
            },
        )
    }

internal fun AiRouterUiState.withProviderDashboardFilters(): AiRouterUiState {
    val filter = providerFilter.takeIf { selected ->
        AiRouterProviderFilter.defaultFilters.contains(selected)
    } ?: AiRouterProviderFilter.ALL
    return copy(
        providerFilter = filter,
        providerFilters = buildProviderFilters(providerDashboardItems).toImmutableList(),
        filteredProviderDashboardItems = filterProviderDashboardItems(
            items = providerDashboardItems,
            query = providerSearchQuery,
            filter = filter,
        ).toImmutableList(),
    )
}

internal fun filterProviderDashboardItems(
    items: List<AiRouterProviderDashboardItemUi>,
    query: String,
    filter: String,
): List<AiRouterProviderDashboardItemUi> {
    val normalizedQuery = normalizeAiRouterSearch(query)
    return items.filter { item ->
        item.matchesProviderFilter(filter) &&
            (normalizedQuery.isBlank() || item.searchText.contains(normalizedQuery))
    }
}

internal fun buildDiagnostics(
    providerItems: List<AiRouterProviderDashboardItemUi>,
    routes: List<AiRouteProfileConfig>,
    targets: List<AiRouteTargetConfig>,
    credentials: List<AiCredentialConfig>,
    modelLabelById: Map<String, String> = emptyMap(),
): List<AiRouterDiagnosticUi> {
    val routeById = routes.associateBy(AiRouteProfileConfig::id)
    val missingTargets = routes.filter { route ->
        route.enabled && targets.none { it.routeProfileId == route.id && it.enabled }
    }.map { route ->
        AiRouterDiagnosticUi(
            id = "route:${route.id}:empty",
            title = "${route.name}: chưa có target",
            description = "Route đang bật nhưng chưa có model hoặc pool provider khả dụng.",
            status = AiConnectionStatus.ERROR,
        )
    }
    val reloginCredentials = credentials.filter {
        it.status == AiCredentialStatus.RELOGIN_REQUIRED
    }.map { credential ->
        AiRouterDiagnosticUi(
            id = "credential:${credential.id}:login",
            title = "${credential.label}: cần đăng nhập lại",
            description = credential.lastFailureKind ?: "OAuth credential không thể refresh.",
            status = AiConnectionStatus.LOGIN_REQUIRED,
        )
    }
    val failingTargets = targets.filter {
        it.consecutiveFailures > 0 || it.lastFailureKind != null
    }.map { target ->
        AiRouterDiagnosticUi(
            id = "target:${target.id}:failure",
            title = listOfNotNull(
                routeById[target.routeProfileId]?.name ?: "Target lỗi",
                modelLabelById[target.modelProfileId]?.takeIf(String::isNotBlank),
            ).joinToString(" · "),
            description = "${target.lastFailureKind ?: "failure"} · ${target.consecutiveFailures} lỗi liên tiếp",
            status = AiConnectionStatus.DEGRADED,
        )
    }
    val missingKeys = providerItems.filter {
        it.installed &&
            it.requiresKey &&
            it.category != AiRouterProviderFilter.OAUTH &&
            it.credentialCount == 0
    }.map { provider ->
        AiRouterDiagnosticUi(
            id = "provider:${provider.id}:credential",
            title = "${provider.name}: thiếu credential",
            description = "Provider cần API key/token trước khi đưa vào route.",
            status = AiConnectionStatus.UNVERIFIED,
        )
    }
    return (reloginCredentials + failingTargets + missingTargets + missingKeys).take(12)
}

internal fun normalizeAiRouterSearch(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace('đ', 'd')
        .replace('Đ', 'd')

private val AiRouterProviderDashboardItemUi.searchText: String
    get() = normalizeAiRouterSearch(
        listOf(
            name,
            familyName,
            connectionMode,
            category,
            authLabel,
            statusLabel,
            notice,
            capabilityLabels.joinToString(" "),
        ).joinToString(" ")
    )

private fun AiRouterProviderDashboardItemUi.matchesProviderFilter(filter: String): Boolean =
    when (filter) {
        AiRouterProviderFilter.READY -> status == AiConnectionStatus.READY
        AiRouterProviderFilter.ERROR ->
            status == AiConnectionStatus.ERROR ||
                status == AiConnectionStatus.DEGRADED ||
                status == AiConnectionStatus.LOGIN_REQUIRED
        AiRouterProviderFilter.FREE -> category == AiProviderCategory.FREE
        AiRouterProviderFilter.API ->
            category == AiProviderCategory.API_KEY ||
                category == AiProviderCategory.FREE_TIER ||
                category == AiProviderCategory.SUBSCRIPTION_KEY
        AiRouterProviderFilter.OAUTH -> category == AiRouterProviderFilter.OAUTH
        AiRouterProviderFilter.LOCAL -> category == AiProviderCategory.LOCAL
        else -> true
    }

private fun providerStatus(
    installed: Boolean,
    requiresKey: Boolean,
    credentials: List<AiCredentialConfig>,
    modelCount: Int,
): String = when {
    !installed -> AiConnectionStatus.UNCONFIGURED
    requiresKey && credentials.any { it.status == AiCredentialStatus.RELOGIN_REQUIRED } ->
        AiConnectionStatus.LOGIN_REQUIRED
    requiresKey && credentials.none { it.enabled && it.hasSecret } -> AiConnectionStatus.UNVERIFIED
    credentials.any { it.consecutiveFailures > 0 || it.lastFailureKind != null } ->
        AiConnectionStatus.DEGRADED
    modelCount <= 0 -> AiConnectionStatus.UNVERIFIED
    else -> AiConnectionStatus.READY
}

private fun providerFamilyId(id: String, name: String): String {
    val text = "$id $name".lowercase(Locale.ROOT)
    return when {
        "opencode" in text -> AiRouterProviderFamily.OPENCODE
        "mimo" in text || "xiaomi" in text -> AiRouterProviderFamily.MIMO
        "local" in text || "gguf" in text -> AiRouterProviderFamily.LOCAL_GGUF
        else -> id
    }
}

private fun providerFamilyName(id: String, name: String): String =
    when (providerFamilyId(id, name)) {
        AiRouterProviderFamily.OPENCODE -> "OpenCode"
        AiRouterProviderFamily.MIMO -> "MiMo"
        AiRouterProviderFamily.LOCAL_GGUF -> "Local GGUF"
        else -> name
    }

private fun providerConnectionMode(id: String, category: String): String =
    when {
        id == "opencode_free" -> "Free Console"
        id == "opencode_go" -> "Go/API"
        id == "mimo_free" -> "Free"
        "token_plan" in id -> "Token Plan"
        id == "xiaomi_mimo" -> "API"
        category == AiProviderCategory.LOCAL -> "Local file"
        category == AiProviderCategory.FREE -> "Free"
        category == AiProviderCategory.FREE_TIER -> "Free tier"
        category == AiProviderCategory.SUBSCRIPTION_KEY -> "Subscription key"
        else -> "API key"
    }

private fun providerFilterLabel(filter: String): String =
    when (filter) {
        AiRouterProviderFilter.READY -> "Ready"
        AiRouterProviderFilter.ERROR -> "Error"
        AiRouterProviderFilter.FREE -> "Free"
        AiRouterProviderFilter.API -> "API"
        AiRouterProviderFilter.OAUTH -> "OAuth"
        AiRouterProviderFilter.LOCAL -> "Local"
        else -> "All"
    }

internal fun statusLabel(status: String): String =
    when (status) {
        AiConnectionStatus.READY -> "Sẵn sàng"
        AiConnectionStatus.DEGRADED -> "Có lỗi"
        AiConnectionStatus.LOGIN_REQUIRED -> "Cần đăng nhập"
        AiConnectionStatus.ERROR -> "Lỗi"
        AiConnectionStatus.UNCONFIGURED -> "Chưa cấu hình"
        else -> "Chưa test"
    }

internal fun authLabel(authType: String): String =
    when (authType) {
        AiProviderAuthType.NONE -> "No key"
        AiProviderAuthType.HEADER -> "Header key"
        else -> "Bearer"
    }

internal data class AiRouterRouteHealthUi(
    val status: String,
    val successRatePercent: Int,
    val averageLatencyMs: Long,
    val recentAttemptCount: Int,
)
