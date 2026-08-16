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
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouteProfileDraft
import io.legado.app.domain.model.AiRouteTargetConfig
import io.legado.app.domain.model.AiRouteTargetDraft
import io.legado.app.domain.model.AiRouterSnapshot
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskPresetDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrateAiProviderApiKeysUseCaseTest {

    @Test
    fun migratesPlaintextProviderApiKeyToCredentialAndClearsProviderDraft() = runBlocking {
        val profileGateway = FakeMigrationProfileGateway(
            providers = mutableListOf(
                provider(apiKey = "plain-secret")
            )
        )
        val routerGateway = FakeMigrationRouterGateway()

        val result = MigrateAiProviderApiKeysUseCase(profileGateway, routerGateway)()

        assertEquals(1, result.migratedProviderCount)
        val credential = routerGateway.savedCredentials.single()
        assertTrue(credential.id?.startsWith("credential_") == true)
        assertEquals("provider_1", credential.providerId)
        assertEquals(AiCredentialKind.API_KEY, credential.kind)
        assertEquals("plain-secret", credential.secret)

        val redacted = profileGateway.savedProviders.single()
        assertEquals("provider_1", redacted.providerId)
        assertEquals("", redacted.apiKey)
        assertEquals(AiProviderAuthType.BEARER, redacted.authType)
    }

    @Test
    fun skipsProvidersWithoutPlaintextApiKey() = runBlocking {
        val profileGateway = FakeMigrationProfileGateway(
            providers = mutableListOf(provider(apiKey = ""))
        )
        val routerGateway = FakeMigrationRouterGateway()

        val result = MigrateAiProviderApiKeysUseCase(profileGateway, routerGateway)()

        assertEquals(0, result.migratedProviderCount)
        assertEquals(emptyList<AiCredentialDraft>(), routerGateway.savedCredentials)
        assertEquals(emptyList<AiProviderDraft>(), profileGateway.savedProviders)
    }

    private fun provider(apiKey: String): AiProviderProfile =
        AiProviderProfile(
            id = "provider_1",
            name = "Provider One",
            protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://provider.example/v1",
            modelsUrl = "https://provider.example/v1/models",
            apiKey = apiKey,
            authType = AiProviderAuthType.BEARER,
        )
}

private class FakeMigrationProfileGateway(
    private val providers: MutableList<AiProviderProfile>,
) : AiProfileGateway {
    val savedProviders = mutableListOf<AiProviderDraft>()

    override fun observeProviders(): Flow<List<AiProviderProfile>> = flowOf(providers)
    override fun observeModels(): Flow<List<AiModelProfile>> = flowOf(emptyList())
    override fun observePresets(): Flow<List<AiTaskPreset>> = flowOf(emptyList())
    override suspend fun getProvider(id: String): AiProviderProfile? = providers.firstOrNull { it.id == id }
    override suspend fun getModel(id: String): AiModelProfile? = null
    override suspend fun getModelConfig(id: String): AiModelConfig? = null
    override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? = null
    override suspend fun getProviderApiKey(providerId: String): String = ""

    override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile {
        savedProviders += draft
        val existing = providers.first { it.id == draft.providerId }
        val updated = existing.copy(
            name = draft.providerName,
            protocol = draft.protocol,
            baseUrl = draft.baseUrl,
            modelsUrl = draft.modelsUrl,
            apiKey = draft.apiKey,
            authType = draft.authType ?: existing.authType,
        )
        providers[providers.indexOf(existing)] = updated
        return updated
    }

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

    override suspend fun saveTaskPreset(draft: AiTaskPresetDraft): AiTaskPresetConfig = error("unused")
    override suspend fun setDefaultTaskPreset(presetId: String): AiTaskPresetConfig = error("unused")
    override suspend fun deleteTaskPreset(presetId: String) = Unit
    override suspend fun deleteProvider(providerId: String) = Unit
    override suspend fun deleteModel(modelId: String) = Unit
}

private class FakeMigrationRouterGateway : AiRouterGateway {
    val savedCredentials = mutableListOf<AiCredentialDraft>()

    override fun observeSnapshot(): Flow<AiRouterSnapshot> = flowOf(AiRouterSnapshot())

    override suspend fun saveCredential(draft: AiCredentialDraft): AiCredentialConfig {
        savedCredentials += draft
        return AiCredentialConfig(
            id = draft.id.orEmpty(),
            providerId = draft.providerId,
            label = draft.label,
            kind = draft.kind,
            enabled = draft.enabled,
            hasSecret = draft.secret.isNotBlank(),
        )
    }

    override suspend fun resolveCredentialSecret(id: String): String = error("unused")
    override suspend fun deleteCredential(id: String) = Unit
    override suspend fun saveRoute(draft: AiRouteProfileDraft): AiRouteProfileConfig = error("unused")
    override suspend fun deleteRoute(id: String) = Unit
    override suspend fun saveTarget(draft: AiRouteTargetDraft): AiRouteTargetConfig = error("unused")
    override suspend fun deleteTarget(id: String) = Unit
    override suspend fun resetHealth(targetId: String?, credentialId: String?) = Unit
}
