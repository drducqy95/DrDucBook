package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiCredentialConfig
import io.legado.app.domain.model.AiCredentialDraft
import io.legado.app.domain.model.AiCredentialKind
import io.legado.app.domain.model.AiCredentialStatus
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiOAuthProviderId
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouteProfileDraft
import io.legado.app.domain.model.AiRouteTargetConfig
import io.legado.app.domain.model.AiRouteTargetDraft
import io.legado.app.domain.model.AiRouterSnapshot
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskRuntimeOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepairAiRouteBindingsUseCaseTest {

    @Test
    fun keepsOauthTargetCredentialPoolWhenSingleCredentialMatchesProvider() = runBlocking {
        val router = FakeRepairRouterGateway(
            snapshot = AiRouterSnapshot(
                credentials = listOf(
                    credential("credential_codex", providerId = "oauth_codex"),
                ),
                targets = listOf(
                    target("target_codex", modelProfileId = "model_codex", credentialId = null),
                ),
            )
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = router,
            aiProfileGateway = FakeRepairProfileGateway(
                models = listOf(model("model_codex", providerId = "oauth_codex")),
            ),
        )

        val result = useCase()

        assertEquals(1, result.repairedTargets)
        assertNull(router.savedTargets.firstOrNull { it.id == "target_codex" })
        assertNull(router.savedTargets.first { it.routeProfileId == "route_translate_chapter" }.credentialId)
    }

    @Test
    fun doesNotOverwriteRouteTargetThatAlreadyHasCredential() = runBlocking {
        val router = FakeRepairRouterGateway(
            snapshot = AiRouterSnapshot(
                credentials = listOf(
                    credential("credential_codex", providerId = "oauth_codex"),
                ),
                targets = listOf(
                    target("target_custom", modelProfileId = "model_codex", credentialId = "credential_custom"),
                ),
            )
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = router,
            aiProfileGateway = FakeRepairProfileGateway(
                models = listOf(model("model_codex", providerId = "oauth_codex")),
            ),
        )

        val result = useCase()

        assertEquals(2, result.repairedTargets)
        assertNull(router.savedTargets.firstOrNull { it.id == "target_custom" })
    }

    @Test
    fun createsChatAndTranslationRoutesForExistingOauthCredential() = runBlocking {
        val router = FakeRepairRouterGateway(
            snapshot = AiRouterSnapshot(
                credentials = listOf(
                    credential("credential_codex", providerId = "oauth_codex"),
                ),
            )
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = router,
            aiProfileGateway = FakeRepairProfileGateway(
                models = listOf(model("model_codex", providerId = "oauth_codex")),
            ),
        )

        val result = useCase()

        assertEquals(2, result.repairedTargets)
        assertEquals(
            listOf(AiTaskType.CHAT, AiTaskType.TRANSLATE_CHAPTER),
            router.savedRoutes.map { it.taskType },
        )
        assertEquals(
            listOf(null, null),
            router.savedTargets.map { it.credentialId },
        )
    }

    @Test
    fun convertsLegacyAccountTargetsToOneProviderPoolTargetPerModel() = runBlocking {
        val router = FakeRepairRouterGateway(
            snapshot = AiRouterSnapshot(
                credentials = listOf(
                    credential("credential_a", providerId = "oauth_codex"),
                    credential("credential_b", providerId = "oauth_codex"),
                ),
                routes = listOf(route("route_chat", AiTaskType.CHAT)),
                targets = listOf(
                    target("target_a", modelProfileId = "model_codex", credentialId = "credential_a"),
                    target("target_b", modelProfileId = "model_codex", credentialId = "credential_b"),
                ),
            )
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = router,
            aiProfileGateway = FakeRepairProfileGateway(
                models = listOf(model("model_codex", providerId = "oauth_codex")),
            ),
        )

        val result = useCase()

        assertEquals(3, result.repairedTargets)
        assertNull(router.savedTargets.first { it.id == "target_a" }.credentialId)
        assertEquals(listOf("target_b"), router.deletedTargetIds)
    }

    @Test
    fun recoversPreviouslyFailedOauthCredentialAndCreatesRoutes() = runBlocking {
        val router = FakeRepairRouterGateway(
            snapshot = AiRouterSnapshot(
                credentials = listOf(
                    credential(
                        "credential_codex",
                        providerId = "oauth_codex",
                        status = AiCredentialStatus.VERIFICATION_FAILED,
                    ),
                ),
            )
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = router,
            aiProfileGateway = FakeRepairProfileGateway(
                models = listOf(model("model_codex", providerId = "oauth_codex")),
            ),
        )

        val result = useCase()

        assertEquals(listOf("credential_codex"), router.resetCredentialIds)
        assertEquals(2, result.repairedTargets)
    }

    @Test
    fun createsChatAndTranslationPresetsForExistingOauthCredential() = runBlocking {
        val profile = FakeRepairProfileGateway(
            models = listOf(model("model_codex", providerId = "oauth_codex")),
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = FakeRepairRouterGateway(
                snapshot = AiRouterSnapshot(
                    credentials = listOf(
                        credential("credential_codex", providerId = "oauth_codex"),
                    ),
                )
            ),
            aiProfileGateway = profile,
        )

        useCase()

        assertEquals(
            listOf(AiTaskType.CHAT, AiTaskType.TRANSLATE_CHAPTER),
            profile.savedPresets.map { it.taskType },
        )
        assertEquals(
            listOf("model_codex", "model_codex"),
            profile.savedPresets.map { it.modelProfileId },
        )
        assertEquals(
            listOf("route_chat", "route_translate_chapter"),
            profile.savedPresets.map { it.runtimeOptions.routeProfileId },
        )
    }

    @Test
    fun createsFallbackTargetsForEveryOauthModel() = runBlocking {
        val router = FakeRepairRouterGateway(
            snapshot = AiRouterSnapshot(
                credentials = listOf(
                    credential("credential_codex", providerId = "oauth_codex"),
                ),
            )
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = router,
            aiProfileGateway = FakeRepairProfileGateway(
                models = listOf(
                    model("model_primary", providerId = "oauth_codex", sortNumber = 0),
                    model("model_fallback", providerId = "oauth_codex", sortNumber = 1),
                ),
            ),
        )

        val result = useCase()

        assertEquals(4, result.repairedTargets)
        assertEquals(
            listOf("model_primary", "model_fallback", "model_primary", "model_fallback"),
            router.savedTargets.map { it.modelProfileId },
        )
        assertEquals(
            listOf(0, 1, 0, 1),
            router.savedTargets.map { it.priority },
        )
    }

    @Test
    fun generatedFreeRoutesPutOauthModelsBeforeFreeFallbacks() = runBlocking {
        val router = FakeRepairRouterGateway(
            snapshot = AiRouterSnapshot(
                credentials = listOf(
                    credential("credential_codex", providerId = "oauth_codex"),
                ),
                routes = listOf(
                    AiRouteProfileConfig(
                        id = "route_chat",
                        name = "Chat AI · Free fallback",
                        taskType = AiTaskType.CHAT,
                        strategy = "priority",
                    ),
                    AiRouteProfileConfig(
                        id = "route_translation",
                        name = "Dịch AI · Free fallback",
                        taskType = AiTaskType.TRANSLATE_CHAPTER,
                        strategy = "priority",
                    ),
                ),
                targets = listOf(
                    target(
                        id = "target_free_chat",
                        modelProfileId = "model_free",
                        credentialId = null,
                        routeProfileId = "route_chat",
                    ),
                    target(
                        id = "target_free_translation",
                        modelProfileId = "model_free",
                        credentialId = null,
                        routeProfileId = "route_translation",
                    ),
                ),
            )
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = router,
            aiProfileGateway = FakeRepairProfileGateway(
                models = listOf(
                    model("model_free", providerId = "catalog_opencode_free"),
                    model("model_codex", providerId = "oauth_codex"),
                ),
            ),
        )

        useCase()

        assertEquals(
            listOf(1),
            router.savedTargets
                .filter { it.id == "target_free_chat" }
                .map { it.priority },
        )
        assertEquals(
            listOf(1),
            router.savedTargets
                .filter { it.id == "target_free_translation" }
                .map { it.priority },
        )
        assertEquals(
            listOf(0, 0),
            router.savedTargets
                .filter { it.modelProfileId == "model_codex" }
                .map { it.priority },
        )
    }

    @Test
    fun repairsExistingOauthPresetsMissingRouteBinding() = runBlocking {
        val profile = FakeRepairProfileGateway(
            models = listOf(model("model_codex", providerId = "oauth_codex")),
            presets = mapOf(
                AiTaskType.CHAT to preset(AiTaskType.CHAT, routeProfileId = ""),
                AiTaskType.TRANSLATE_CHAPTER to preset(AiTaskType.TRANSLATE_CHAPTER, routeProfileId = ""),
            ),
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = FakeRepairRouterGateway(
                snapshot = AiRouterSnapshot(
                    credentials = listOf(credential("credential_codex", providerId = "oauth_codex")),
                    routes = listOf(
                        route("route_chat", AiTaskType.CHAT),
                        route("route_translation", AiTaskType.TRANSLATE_CHAPTER),
                    ),
                )
            ),
            aiProfileGateway = profile,
        )

        val result = useCase()

        assertEquals(2, result.repairedPresets)
        assertEquals(
            listOf("route_chat", "route_translation"),
            profile.savedPresets.map { it.runtimeOptions.routeProfileId },
        )
    }

    @Test
    fun doesNotOverwritePresetThatAlreadyHasRouteBinding() = runBlocking {
        val profile = FakeRepairProfileGateway(
            models = listOf(model("model_codex", providerId = "oauth_codex")),
            presets = mapOf(
                AiTaskType.CHAT to preset(AiTaskType.CHAT, routeProfileId = "route_custom_chat"),
                AiTaskType.TRANSLATE_CHAPTER to preset(
                    AiTaskType.TRANSLATE_CHAPTER,
                    routeProfileId = "route_custom_translation",
                ),
            ),
        )
        val useCase = RepairAiRouteBindingsUseCase(
            aiRouterGateway = FakeRepairRouterGateway(
                snapshot = AiRouterSnapshot(
                    credentials = listOf(credential("credential_codex", providerId = "oauth_codex")),
                    routes = listOf(
                        route("route_chat", AiTaskType.CHAT),
                        route("route_translation", AiTaskType.TRANSLATE_CHAPTER),
                    ),
                )
            ),
            aiProfileGateway = profile,
        )

        val result = useCase()

        assertEquals(0, result.repairedPresets)
        assertEquals(emptyList<AiTaskPresetDraft>(), profile.savedPresets)
    }

    private fun credential(
        id: String,
        providerId: String,
        status: String = AiCredentialStatus.ACTIVE,
    ) = AiCredentialConfig(
        id = id,
        providerId = providerId,
        label = id,
        kind = AiCredentialKind.OAUTH_ACCESS_TOKEN,
        oauthProvider = AiOAuthProviderId.CODEX,
        status = status,
    )

    private fun target(
        id: String,
        modelProfileId: String,
        credentialId: String?,
        routeProfileId: String = "route_chat",
    ) = AiRouteTargetConfig(
        id = id,
        routeProfileId = routeProfileId,
        modelProfileId = modelProfileId,
        credentialId = credentialId,
    )

    private fun route(
        id: String,
        taskType: String,
    ) = AiRouteProfileConfig(
        id = id,
        name = id,
        taskType = taskType,
    )

    private fun model(
        id: String,
        providerId: String,
        sortNumber: Int = 0,
    ) = AiModelProfile(
        id = id,
        providerId = providerId,
        displayName = id,
        modelId = id,
        sortNumber = sortNumber,
    )

    private fun preset(
        taskType: String,
        routeProfileId: String,
        providerId: String = "oauth_codex",
        modelId: String = "model_codex",
    ) = AiTaskPresetConfig(
        id = "preset_$taskType",
        taskType = taskType,
        name = "Preset $taskType",
        model = AiModelConfig(
            id = modelId,
            provider = AiProviderConfig(
                id = providerId,
                name = providerId,
                protocol = "test",
                baseUrl = "https://example.invalid",
                apiKey = "test",
            ),
            displayName = modelId,
            modelId = modelId,
        ),
        promptTemplate = "Prompt",
        runtimeOptions = AiTaskRuntimeOptions(routeProfileId = routeProfileId),
    )
}

private class FakeRepairRouterGateway(
    private val snapshot: AiRouterSnapshot,
) : AiRouterGateway {
    val savedRoutes = mutableListOf<AiRouteProfileDraft>()
    val savedTargets = mutableListOf<AiRouteTargetDraft>()
    val deletedTargetIds = mutableListOf<String>()
    val resetCredentialIds = mutableListOf<String>()

    override fun observeSnapshot(): Flow<AiRouterSnapshot> = flowOf(snapshot)
    override suspend fun saveCredential(draft: AiCredentialDraft): AiCredentialConfig = error("unused")
    override suspend fun resolveCredentialSecret(id: String): String = error("unused")
    override suspend fun deleteCredential(id: String) = Unit
    override suspend fun saveRoute(draft: AiRouteProfileDraft): AiRouteProfileConfig {
        savedRoutes += draft
        return AiRouteProfileConfig(
            id = draft.id ?: "route_${draft.taskType}",
            name = draft.name,
            taskType = draft.taskType,
            strategy = draft.strategy,
            maxAttempts = draft.maxAttempts,
            stickySession = draft.stickySession,
            enabled = draft.enabled,
            isDefault = draft.makeDefault,
            sortNumber = draft.sortNumber,
        )
    }
    override suspend fun deleteRoute(id: String) = Unit

    override suspend fun saveTarget(draft: AiRouteTargetDraft): AiRouteTargetConfig {
        savedTargets += draft
        return AiRouteTargetConfig(
            id = draft.id.orEmpty(),
            routeProfileId = draft.routeProfileId,
            modelProfileId = draft.modelProfileId,
            credentialId = draft.credentialId,
            priority = draft.priority,
            weight = draft.weight,
            maxConcurrency = draft.maxConcurrency,
            enabled = draft.enabled,
            sortNumber = draft.sortNumber,
        )
    }

    override suspend fun deleteTarget(id: String) {
        deletedTargetIds += id
    }
    override suspend fun resetHealth(targetId: String?, credentialId: String?) {
        credentialId?.let(resetCredentialIds::add)
    }
}

private class FakeRepairProfileGateway(
    private val models: List<AiModelProfile>,
    presets: Map<String, AiTaskPresetConfig> = emptyMap(),
) : AiProfileGateway {
    val savedPresets = mutableListOf<AiTaskPresetDraft>()
    private val presetsByTask = presets.toMutableMap()

    override fun observeProviders(): Flow<List<AiProviderProfile>> = flowOf(emptyList())
    override fun observeModels(): Flow<List<AiModelProfile>> = flowOf(models)
    override fun observePresets(): Flow<List<AiTaskPreset>> = flowOf(emptyList())
    override suspend fun getProvider(id: String): AiProviderProfile? = null
    override suspend fun getModel(id: String): AiModelProfile? = models.firstOrNull { it.id == id }
    override suspend fun getModelConfig(id: String): AiModelConfig? = null
    override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? = presetsByTask[taskType]
    override suspend fun getProviderApiKey(providerId: String): String = ""
    override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile = error("unused")
    override suspend fun saveModel(draft: AiModelDraft): AiModelProfile = error("unused")
    override suspend fun importProviderModels(
        providerId: String,
        models: List<AiAvailableModel>,
    ): List<AiModelProfile> = error("unused")

    override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig = error("unused")
    override suspend fun saveDefaultChatProfile(draft: AiProfileDraft): AiTaskPresetConfig = error("unused")
    override suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int,
    ): AiTaskPresetConfig = error("unused")

    override suspend fun saveTaskPreset(draft: AiTaskPresetDraft): AiTaskPresetConfig {
        savedPresets += draft
        return AiTaskPresetConfig(
            id = draft.presetId ?: "preset_${draft.taskType}",
            taskType = draft.taskType,
            name = draft.name,
            model = AiModelConfig(
                id = draft.modelProfileId,
                provider = AiProviderConfig(
                    id = "provider",
                    name = "Provider",
                    protocol = "test",
                    baseUrl = "https://example.invalid",
                    apiKey = "test",
                ),
                displayName = draft.modelProfileId,
                modelId = draft.modelProfileId,
            ),
            promptTemplate = draft.promptTemplate,
            params = draft.params,
            runtimeOptions = draft.runtimeOptions,
        ).also { presetsByTask[draft.taskType] = it }
    }
    override suspend fun setDefaultTaskPreset(presetId: String): AiTaskPresetConfig = error("unused")
    override suspend fun deleteTaskPreset(presetId: String) = Unit
    override suspend fun deleteProvider(providerId: String) = Unit
    override suspend fun deleteModel(modelId: String) = Unit
}
