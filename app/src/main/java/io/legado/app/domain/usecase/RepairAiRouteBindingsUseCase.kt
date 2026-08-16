package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiModelProfile
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.model.AiCredentialStatus
import io.legado.app.domain.model.AiOAuthProviderId
import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouteProfileDraft
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiRouteTargetDraft
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.TranslationConstants
import kotlinx.coroutines.flow.first

class RepairAiRouteBindingsUseCase(
    private val aiRouterGateway: AiRouterGateway,
    private val aiProfileGateway: AiProfileGateway,
) {

    suspend operator fun invoke(): RepairAiRouteBindingsResult {
        val snapshot = aiRouterGateway.observeSnapshot().first()
        val models = aiProfileGateway.observeModels()
            .first()
            .filter { it.enabled }
        val modelsByProvider = models
            .groupBy { it.providerId }
            .mapValues { (_, providerModels) ->
                providerModels.sortedWith(
                    compareBy<AiModelProfile> { it.sortNumber }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                )
            }
        val recoverableOAuthCredentials = snapshot.credentials
            .filter { credential ->
                credential.enabled &&
                    credential.status in setOf(
                        AiCredentialStatus.ACTIVE,
                        AiCredentialStatus.VERIFYING,
                        AiCredentialStatus.VERIFICATION_FAILED,
                    ) &&
                    credential.oauthProvider?.let(repairableOAuthProviders::contains) == true
            }
        recoverableOAuthCredentials
            .filter { it.status != AiCredentialStatus.ACTIVE }
            .forEach { credential ->
                aiRouterGateway.resetHealth(credentialId = credential.id)
            }
        val oauthCredentialsByProvider = recoverableOAuthCredentials
            .groupBy { it.providerId }
        val routesByTask = snapshot.routes
            .filter { it.enabled }
            .groupBy { it.taskType }
            .mapValues { (_, routes) ->
                routes.sortedWith(
                    compareByDescending<AiRouteProfileConfig> { it.isDefault }
                        .thenBy { it.sortNumber }
                        .thenBy { it.id }
                )
            }
            .toMutableMap()
        val targetsByRoute = snapshot.targets
            .filter { it.enabled }
            .groupBy { it.routeProfileId }
            .mapValues { (_, targets) -> targets.toMutableList() }
            .toMutableMap()
        var repaired = 0
        var repairedPresets = 0
        var skippedMissingCredential = 0
        var skippedAmbiguousCredential = 0
        val oauthModelIds = models
            .filter { model -> model.providerId in oauthCredentialsByProvider.keys }
            .mapTo(hashSetOf()) { model -> model.id }

        oauthCredentialsByProvider.forEach { (providerId, credentials) ->
            val providerModels = modelsByProvider[providerId].orEmpty()
            if (providerModels.isEmpty()) {
                return@forEach
            }
            oauthTaskBindings.forEach { binding ->
                val route = routesByTask[binding.taskType]?.firstOrNull()
                    ?: aiRouterGateway.saveRoute(
                        AiRouteProfileDraft(
                            name = binding.routeName,
                            taskType = binding.taskType,
                            strategy = binding.strategy,
                            maxAttempts = providerModels.size.coerceAtLeast(1),
                            stickySession = true,
                            enabled = true,
                            makeDefault = true,
                        )
                    ).also { saved ->
                        routesByTask[binding.taskType] = listOf(saved)
                    }
                if (ensureTaskPreset(binding, providerId, providerModels.first().id, route.id)) {
                    repairedPresets++
                }
                val routeTargets = targetsByRoute.getOrPut(route.id) { mutableListOf() }
                providerModels.forEachIndexed { modelIndex, model ->
                    val poolTarget = routeTargets.firstOrNull {
                        it.modelProfileId == model.id && it.credentialId == null
                    }
                    val legacyBoundTargets = routeTargets.filter { target ->
                        target.modelProfileId == model.id &&
                            credentials.any { credential -> credential.id == target.credentialId }
                    }
                    val sourceTarget = poolTarget ?: legacyBoundTargets.firstOrNull()
                    if (sourceTarget == null || legacyBoundTargets.isNotEmpty()) {
                        val savedTarget = aiRouterGateway.saveTarget(
                            AiRouteTargetDraft(
                                id = sourceTarget?.id,
                                routeProfileId = route.id,
                                modelProfileId = model.id,
                                credentialId = null,
                                priority = sourceTarget?.priority ?: modelIndex,
                                weight = sourceTarget?.weight ?: 1,
                                maxConcurrency = sourceTarget?.maxConcurrency
                                    ?: if (binding.taskType == AiTaskType.TRANSLATE_CHAPTER) 2 else 0,
                                enabled = true,
                                sortNumber = sourceTarget?.sortNumber ?: modelIndex,
                            )
                        )
                        if (sourceTarget == null) {
                            routeTargets += savedTarget
                        } else {
                            routeTargets.replaceAll {
                                if (it.id == sourceTarget.id) savedTarget else it
                            }
                        }
                        legacyBoundTargets
                            .filterNot { it.id == savedTarget.id }
                            .forEach { staleTarget ->
                                aiRouterGateway.deleteTarget(staleTarget.id)
                                routeTargets.removeAll { it.id == staleTarget.id }
                                repaired++
                            }
                        repaired++
                    }
                }
                repaired += normalizeGeneratedFallbackPriorities(
                    route = route,
                    targets = routeTargets,
                    oauthModelIds = oauthModelIds,
                )
                val desiredMaxAttempts = routeTargets.size.coerceAtLeast(1).coerceAtMost(20)
                if (route.maxAttempts < desiredMaxAttempts) {
                    val updatedRoute = aiRouterGateway.saveRoute(
                        AiRouteProfileDraft(
                            id = route.id,
                            name = route.name,
                            taskType = route.taskType,
                            strategy = route.strategy,
                            maxAttempts = desiredMaxAttempts,
                            stickySession = route.stickySession,
                            enabled = route.enabled,
                            makeDefault = route.isDefault,
                            sortNumber = route.sortNumber,
                        )
                    )
                    routesByTask[binding.taskType] = listOf(updatedRoute)
                }
            }
        }

        return RepairAiRouteBindingsResult(
            repairedTargets = repaired,
            skippedMissingCredential = skippedMissingCredential,
            skippedAmbiguousCredential = skippedAmbiguousCredential,
            repairedPresets = repairedPresets,
        )
    }

    private suspend fun normalizeGeneratedFallbackPriorities(
        route: AiRouteProfileConfig,
        targets: MutableList<io.legado.app.domain.model.AiRouteTargetConfig>,
        oauthModelIds: Set<String>,
    ): Int {
        if (!route.name.endsWith(GENERATED_FREE_ROUTE_SUFFIX)) return 0
        val oauthTargets = targets.filter { target ->
            target.modelProfileId in oauthModelIds
        }
        val fallbackTargets = targets.filterNot { target ->
            target.modelProfileId in oauthModelIds
        }
        if (oauthTargets.isEmpty() || fallbackTargets.isEmpty()) return 0

        val targetOrder = compareBy<io.legado.app.domain.model.AiRouteTargetConfig> { it.priority }
            .thenBy { it.sortNumber }
            .thenBy { it.id }
        val orderedModelIds = (
            oauthTargets.sortedWith(targetOrder) + fallbackTargets.sortedWith(targetOrder)
            )
            .map { it.modelProfileId }
            .distinct()
        val priorityByModel = orderedModelIds.withIndex().associate { it.value to it.index }
        var repaired = 0
        targets.toList().forEach { target ->
            val desiredPriority = priorityByModel.getValue(target.modelProfileId)
            if (target.priority == desiredPriority) return@forEach
            val saved = aiRouterGateway.saveTarget(
                AiRouteTargetDraft(
                    id = target.id,
                    routeProfileId = target.routeProfileId,
                    modelProfileId = target.modelProfileId,
                    credentialId = target.credentialId,
                    priority = desiredPriority,
                    weight = target.weight,
                    maxConcurrency = target.maxConcurrency,
                    enabled = target.enabled,
                    sortNumber = target.sortNumber,
                )
            )
            targets.replaceAll { if (it.id == target.id) saved else it }
            repaired++
        }
        return repaired
    }

    private suspend fun ensureTaskPreset(
        binding: OAuthTaskBinding,
        providerId: String,
        modelProfileId: String,
        routeProfileId: String,
    ): Boolean {
        val existing = aiProfileGateway.getTaskPreset(binding.taskType)
        if (existing == null) {
            aiProfileGateway.saveTaskPreset(
                AiTaskPresetDraft(
                    taskType = binding.taskType,
                    name = binding.routeName,
                    modelProfileId = modelProfileId,
                    promptTemplate = binding.defaultPrompt,
                    runtimeOptions = AiTaskRuntimeOptions(routeProfileId = routeProfileId),
                    makeDefault = true,
                )
            )
            return true
        }
        if (
            existing.runtimeOptions.routeProfileId.isNotBlank() ||
            existing.model.provider.id != providerId
        ) {
            return false
        }
        aiProfileGateway.saveTaskPreset(
            AiTaskPresetDraft(
                presetId = existing.id,
                taskType = existing.taskType,
                name = existing.name,
                description = existing.description,
                modelProfileId = existing.model.id,
                promptTemplate = existing.promptTemplate,
                params = existing.params,
                runtimeOptions = existing.runtimeOptions.copy(routeProfileId = routeProfileId),
                makeDefault = true,
            )
        )
        return true
    }

    private companion object {
        const val GENERATED_FREE_ROUTE_SUFFIX = "· Free fallback"

        val oauthTaskBindings = listOf(
            OAuthTaskBinding(
                taskType = AiTaskType.CHAT,
                routeName = "Default Chat",
                strategy = AiRouteStrategy.PRIORITY,
                defaultPrompt = "You are a helpful AI assistant.",
            ),
            OAuthTaskBinding(
                taskType = AiTaskType.TRANSLATE_CHAPTER,
                routeName = "Default Translation",
                strategy = AiRouteStrategy.ROUND_ROBIN,
                defaultPrompt = TranslationConstants.DEFAULT_PROMPT,
            ),
        )

        val repairableOAuthProviders = setOf(
            AiOAuthProviderId.CODEX,
            AiOAuthProviderId.CLAUDE,
            AiOAuthProviderId.ANTIGRAVITY,
            AiOAuthProviderId.XAI,
            AiOAuthProviderId.KIMI,
            AiOAuthProviderId.QWEN,
            AiOAuthProviderId.GROK_CLI,
            AiOAuthProviderId.GITHUB,
            AiOAuthProviderId.CLINE,
            AiOAuthProviderId.CLINEPASS,
        )
    }
}

private data class OAuthTaskBinding(
    val taskType: String,
    val routeName: String,
    val strategy: String,
    val defaultPrompt: String,
)

data class RepairAiRouteBindingsResult(
    val repairedTargets: Int,
    val skippedMissingCredential: Int,
    val skippedAmbiguousCredential: Int,
    val repairedPresets: Int = 0,
)
