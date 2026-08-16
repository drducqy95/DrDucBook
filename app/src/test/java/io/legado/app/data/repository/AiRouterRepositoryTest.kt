package io.legado.app.data.repository

import io.legado.app.data.dao.AiRouterDao
import io.legado.app.data.entities.AiCredentialEntity
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiRouteAttemptEntity
import io.legado.app.data.entities.AiRouteProfileEntity
import io.legado.app.data.entities.AiRouteTargetEntity
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.domain.gateway.AiOAuthGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiSecretStore
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiCredentialKind
import io.legado.app.domain.model.AiCredentialStatus
import io.legado.app.domain.model.AiFailureKind
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiOAuthAuthorization
import io.legado.app.domain.model.AiOAuthEvent
import io.legado.app.domain.model.AiOAuthProviderConfig
import io.legado.app.domain.model.AiOAuthProviderId
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiProviderException
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class AiRouterRepositoryTest {

    @Test
    fun resetHealthReactivatesInterruptedOauthVerification() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.credentials["credential_codex"] = oauthCredential().copy(
            status = AiCredentialStatus.VERIFICATION_FAILED,
        )
        dao.targets["unrelated_target"] = AiRouteTargetEntity(
            id = "unrelated_target",
            routeProfileId = "unrelated_route",
            modelProfileId = "unrelated_model",
            cooldownUntil = 123_456L,
        )
        val repository = repository(
            dao = dao,
            profileGateway = FakeAiProfileGateway(),
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = RecordingAiTextGateway(),
        )

        repository.resetHealth(credentialId = "credential_codex")

        assertEquals(
            AiCredentialStatus.ACTIVE,
            dao.credentials.getValue("credential_codex").status,
        )
        assertEquals(123_456L, dao.targets.getValue("unrelated_target").cooldownUntil)
    }

    @Test
    fun chatStreamUsesOauthCredentialToken() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_chat"] = chatRoute()
        dao.credentials["credential_codex"] = oauthCredential()
        dao.targets["target_codex"] = AiRouteTargetEntity(
            id = "target_codex",
            routeProfileId = "route_chat",
            modelProfileId = "model_codex",
            credentialId = "credential_codex",
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_codex"] = oauthModel()
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        val events = repository.generateStream(chatRequest()).toList()

        assertEquals(listOf(AiStreamEvent.Content("delegate")), events)
        assertEquals(1, delegate.streamCalls)
        assertEquals("access-token", delegate.lastStreamRequest?.model?.provider?.apiKey)
    }

    @Test
    fun chatRouteSkipsUnresolvableTargetBeforeOauth() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_chat"] = chatRoute().copy(maxAttempts = 1)
        dao.credentials["credential_codex"] = oauthCredential()
        dao.targets["target_unbound"] = AiRouteTargetEntity(
            id = "target_unbound",
            routeProfileId = "route_chat",
            modelProfileId = "model_codex",
            credentialId = null,
            sortNumber = 0,
        )
        dao.targets["target_codex"] = AiRouteTargetEntity(
            id = "target_codex",
            routeProfileId = "route_chat",
            modelProfileId = "model_codex",
            credentialId = "credential_codex",
            sortNumber = 1,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_codex"] = oauthModel()
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        val events = repository.generateStream(chatRequest()).toList()

        assertEquals(listOf(AiStreamEvent.Content("delegate")), events)
        assertEquals("access-token", delegate.lastStreamRequest?.model?.provider?.apiKey)
    }

    @Test
    fun modelOnlyTargetRotatesProviderOAuthAccountPool() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_chat"] = chatRoute().copy(maxAttempts = 1)
        dao.credentials["credential_a"] = oauthCredential().copy(
            id = "credential_a",
            label = "Codex account A",
            accountLabel = "a@example.com",
            sortNumber = 0,
        )
        dao.credentials["credential_b"] = oauthCredential().copy(
            id = "credential_b",
            label = "Codex account B",
            accountLabel = "b@example.com",
            sortNumber = 1,
        )
        dao.targets["target_codex"] = AiRouteTargetEntity(
            id = "target_codex",
            routeProfileId = "route_chat",
            modelProfileId = "model_codex",
            credentialId = null,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_codex"] = oauthModel()
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { credentialId -> "token-$credentialId" },
            delegate = delegate,
        )

        repository.generate(chatRequest()).getOrThrow()
        repository.generate(chatRequest()).getOrThrow()

        assertEquals(
            listOf("token-credential_a", "token-credential_b"),
            delegate.generateRequests.map { it.model.provider.apiKey },
        )
        assertEquals(
            listOf("Codex account A", "Codex account B"),
            dao.attempts.map { it.credentialLabel },
        )
        assertEquals(0, dao.targets.getValue("target_codex").consecutiveFailures)
    }

    @Test
    fun modelOnlyTargetRotatesProviderApiKeyPool() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_chat"] = chatRoute().copy(maxAttempts = 1)
        dao.credentials["credential_a"] = AiCredentialEntity(
            id = "credential_a",
            providerId = "provider_api",
            label = "API key A",
            kind = AiCredentialKind.API_KEY,
            secretRef = "secret_a",
            sortNumber = 0,
        )
        dao.credentials["credential_b"] = AiCredentialEntity(
            id = "credential_b",
            providerId = "provider_api",
            label = "API key B",
            kind = AiCredentialKind.API_KEY,
            secretRef = "secret_b",
            sortNumber = 1,
        )
        dao.targets["target_api"] = AiRouteTargetEntity(
            id = "target_api",
            routeProfileId = "route_chat",
            modelProfileId = "model_api",
            credentialId = null,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_api"] = apiKeyModel()
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            secretStore = FakeAiSecretStore(
                linkedMapOf(
                    "secret_a" to "api-key-a",
                    "secret_b" to "api-key-b",
                )
            ),
            oauthGateway = FakeAiOAuthGateway { "unused" },
            delegate = delegate,
        )

        repository.generate(chatRequest()).getOrThrow()
        repository.generate(chatRequest()).getOrThrow()

        assertEquals(
            listOf("api-key-a", "api-key-b"),
            delegate.generateRequests.map { it.model.provider.apiKey },
        )
        assertEquals(
            listOf("API key A", "API key B"),
            dao.attempts.map { it.credentialLabel },
        )
        assertEquals(0, dao.targets.getValue("target_api").consecutiveFailures)
    }

    @Test
    fun translationStreamUsesOauthCredentialToken() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute()
        dao.credentials["credential_codex"] = oauthCredential()
        dao.targets["target_codex"] = AiRouteTargetEntity(
            id = "target_codex",
            routeProfileId = "route_translation",
            modelProfileId = "model_codex",
            credentialId = "credential_codex",
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_codex"] = oauthModel()
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        val events = repository.generateStream(translationRequest()).toList()

        assertEquals(listOf(AiStreamEvent.Content("delegate")), events)
        assertEquals(1, delegate.streamCalls)
        assertEquals("access-token", delegate.lastStreamRequest?.model?.provider?.apiKey)
    }

    @Test
    fun routeKeepsComboStrategyOrderInsteadOfRequestModel() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute()
        dao.credentials["credential_codex"] = oauthCredential()
        dao.targets["target_primary"] = AiRouteTargetEntity(
            id = "target_primary",
            routeProfileId = "route_translation",
            modelProfileId = "model_primary",
            credentialId = "credential_codex",
            priority = 0,
        )
        dao.targets["target_selected"] = AiRouteTargetEntity(
            id = "target_selected",
            routeProfileId = "route_translation",
            modelProfileId = "model_selected",
            credentialId = "credential_codex",
            priority = 1,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_primary"] = oauthModel(
                id = "model_primary",
                modelId = "gpt-primary",
            )
            modelConfigs["model_selected"] = oauthModel(
                id = "model_selected",
                modelId = "gpt-selected",
            )
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        repository.generateStream(
            translationRequest().copy(model = oauthModel(id = "model_selected", modelId = "gpt-selected"))
        ).toList()

        assertEquals("model_primary", delegate.lastStreamRequest?.model?.id)
        assertEquals("gpt-primary", delegate.lastStreamRequest?.model?.modelId)
        assertEquals("access-token", delegate.lastStreamRequest?.model?.provider?.apiKey)
    }

    @Test
    fun targetConcurrencyOneSerializesParallelTranslationChunks() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute()
        dao.targets["target_serial"] = AiRouteTargetEntity(
            id = "target_serial",
            routeProfileId = "route_translation",
            modelProfileId = "model_serial",
            maxConcurrency = 1,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_serial"] = unauthenticatedModel("model_serial")
        }
        val delegate = ConcurrencyRecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "unused" },
            delegate = delegate,
        )

        listOf(
            async { repository.generate(translationRequest()).getOrThrow() },
            async { repository.generate(translationRequest()).getOrThrow() },
        ).awaitAll()

        assertEquals(1, delegate.maxInFlight.get())
    }

    @Test
    fun legacyGeneratedTranslationComboNormalizesConcurrencyBeforeUse() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute().copy(
            name = "D\u1ecbch AI \u00b7 Free fallback",
        )
        dao.targets["target_legacy"] = AiRouteTargetEntity(
            id = "target_legacy",
            routeProfileId = "route_translation",
            modelProfileId = "model_legacy",
            maxConcurrency = 2,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_legacy"] = unauthenticatedModel("model_legacy")
        }
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "unused" },
            delegate = RecordingAiTextGateway(),
        )

        repository.generate(translationRequest()).getOrThrow()

        assertEquals(1, dao.targets.getValue("target_legacy").maxConcurrency)
    }

    @Test
    fun blankRouteProfileBypassesActiveRoute() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_chat"] = chatRoute()
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = FakeAiProfileGateway(),
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        repository.generateStream(chatRequest().copy(routeProfileId = "")).toList()

        assertEquals(1, delegate.streamCalls)
        assertEquals(0, dao.attempts.size)
    }

    @Test
    fun explicitRouteProfileOverridesDefaultRoute() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_default"] = chatRoute().copy(id = "route_default", isDefault = true)
        dao.routes["route_prompt"] = chatRoute().copy(id = "route_prompt", isDefault = false)
        dao.credentials["credential_codex"] = oauthCredential()
        dao.targets["target_default"] = AiRouteTargetEntity(
            id = "target_default",
            routeProfileId = "route_default",
            modelProfileId = "model_default",
            credentialId = "credential_codex",
        )
        dao.targets["target_prompt"] = AiRouteTargetEntity(
            id = "target_prompt",
            routeProfileId = "route_prompt",
            modelProfileId = "model_prompt",
            credentialId = "credential_codex",
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_default"] = oauthModel(
                id = "model_default",
                modelId = "gpt-default",
            )
            modelConfigs["model_prompt"] = oauthModel(
                id = "model_prompt",
                modelId = "gpt-prompt",
            )
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        repository.generateStream(chatRequest().copy(routeProfileId = "route_prompt")).toList()

        assertEquals("target_prompt", dao.attempts.single().targetId)
        assertEquals("model_prompt", delegate.lastStreamRequest?.model?.id)
        assertEquals("gpt-prompt", delegate.lastStreamRequest?.model?.modelId)
    }

    @Test
    fun translationSemanticRetrySkipsUsedComboTarget() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute().copy(
            strategy = AiRouteStrategy.PRIORITY,
            maxAttempts = 2,
        )
        dao.credentials["credential_codex"] = oauthCredential()
        dao.targets["target_primary"] = AiRouteTargetEntity(
            id = "target_primary",
            routeProfileId = "route_translation",
            modelProfileId = "model_primary",
            credentialId = "credential_codex",
            priority = 0,
        )
        dao.targets["target_fallback"] = AiRouteTargetEntity(
            id = "target_fallback",
            routeProfileId = "route_translation",
            modelProfileId = "model_fallback",
            credentialId = "credential_codex",
            priority = 1,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_primary"] = oauthModel(
                id = "model_primary",
                modelId = "gpt-primary",
            )
            modelConfigs["model_fallback"] = oauthModel(
                id = "model_fallback",
                modelId = "deepseek-v4-flash-free",
            ).copy(maxOutputTokens = 65_536)
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        repository.generateStream(
            translationRequest().copy(
                routeProfileId = "route_translation",
                routeRetryOffset = 1,
                params = AiGenerationParams(maxOutputTokens = 1_024),
            )
        ).toList()

        assertEquals("model_fallback", delegate.lastStreamRequest?.model?.id)
        assertEquals(4_096, delegate.lastStreamRequest?.params?.maxOutputTokens)
        assertEquals("target_fallback", dao.attempts.single().targetId)
    }

    @Test
    fun rewriteRouteGivesReasoningModelEnoughOutputBudget() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_rewrite"] = translationRoute().copy(
            id = "route_rewrite",
            taskType = AiTaskType.REWRITE_TEXT,
            maxAttempts = 1,
        )
        dao.credentials["credential_codex"] = oauthCredential()
        dao.targets["target_rewrite"] = AiRouteTargetEntity(
            id = "target_rewrite",
            routeProfileId = "route_rewrite",
            modelProfileId = "model_big_pickle",
            credentialId = "credential_codex",
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_big_pickle"] = oauthModel(
                id = "model_big_pickle",
                modelId = "big-pickle",
            ).copy(maxOutputTokens = 65_536)
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        repository.generate(
            chatRequest().copy(
                taskType = AiTaskType.REWRITE_TEXT,
                routeProfileId = "route_rewrite",
                params = AiGenerationParams(maxOutputTokens = 1_024),
            )
        ).getOrThrow()

        assertEquals(4_096, delegate.lastRequest?.params?.maxOutputTokens)
    }

    @Test
    fun semanticRetryReclassifiesAttemptWithoutQuarantiningHealthyTarget() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute().copy(
            strategy = AiRouteStrategy.PRIORITY,
            maxAttempts = 2,
        )
        dao.targets["target_primary"] = AiRouteTargetEntity(
            id = "target_primary",
            routeProfileId = "route_translation",
            modelProfileId = "model_primary",
            priority = 0,
        )
        dao.targets["target_fallback"] = AiRouteTargetEntity(
            id = "target_fallback",
            routeProfileId = "route_translation",
            modelProfileId = "model_fallback",
            priority = 1,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_primary"] = unauthenticatedModel("model_primary")
            modelConfigs["model_fallback"] = unauthenticatedModel("model_fallback")
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "unused" },
            delegate = delegate,
        )

        repository.generate(translationRequest()).getOrThrow()
        repository.generate(
            translationRequest().copy(
                routeRetryOffset = 1,
                routeSemanticFailureKind = AiFailureKind.PARSE_ERROR,
            )
        ).getOrThrow()

        assertEquals(listOf("model_primary", "model_fallback"), delegate.generateRequests.map { it.model.id })
        assertFalse(dao.attempts.first().success)
        assertEquals(AiFailureKind.PARSE_ERROR.name, dao.attempts.first().failureKind)
        assertEquals(null, dao.targets.getValue("target_primary").lastFailureKind)
        assertEquals(0L, dao.targets.getValue("target_primary").cooldownUntil)
        assertEquals(null, dao.targets.getValue("target_fallback").lastFailureKind)
    }

    @Test
    fun semanticRetrySkipsWholeModelOnlyTargetInsteadOfNextAccountInSamePool() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute().copy(
            strategy = AiRouteStrategy.PRIORITY,
            maxAttempts = 2,
        )
        dao.credentials["credential_a"] = oauthCredential().copy(
            id = "credential_a",
            label = "Codex account A",
            sortNumber = 0,
        )
        dao.credentials["credential_b"] = oauthCredential().copy(
            id = "credential_b",
            label = "Codex account B",
            sortNumber = 1,
        )
        dao.targets["target_primary"] = AiRouteTargetEntity(
            id = "target_primary",
            routeProfileId = "route_translation",
            modelProfileId = "model_primary",
            credentialId = null,
            priority = 0,
        )
        dao.targets["target_fallback"] = AiRouteTargetEntity(
            id = "target_fallback",
            routeProfileId = "route_translation",
            modelProfileId = "model_fallback",
            credentialId = null,
            priority = 1,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_primary"] = oauthModel(
                id = "model_primary",
                modelId = "gpt-primary",
            )
            modelConfigs["model_fallback"] = oauthModel(
                id = "model_fallback",
                modelId = "gpt-fallback",
            )
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { credentialId -> "token-$credentialId" },
            delegate = delegate,
        )

        repository.generateStream(translationRequest()).toList()
        repository.generateStream(
            translationRequest().copy(
                routeRetryOffset = 1,
                routeSemanticFailureKind = AiFailureKind.PARSE_ERROR,
            )
        ).toList()

        assertEquals(
            listOf("model_primary", "model_fallback"),
            delegate.streamRequests.map { it.model.id },
        )
        assertEquals("target_fallback", dao.attempts.last().targetId)
        assertEquals("token-credential_a", delegate.streamRequests.last().model.provider.apiKey)
    }

    @Test
    fun chainedSemanticRetriesAdvanceAcrossEveryComboTargetWithoutCooldown() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute().copy(
            strategy = AiRouteStrategy.PRIORITY,
            maxAttempts = 4,
        )
        val profileGateway = FakeAiProfileGateway()
        repeat(4) { index ->
            val suffix = index.toString()
            dao.targets["target_$suffix"] = AiRouteTargetEntity(
                id = "target_$suffix",
                routeProfileId = "route_translation",
                modelProfileId = "model_$suffix",
                priority = index,
            )
            profileGateway.modelConfigs["model_$suffix"] = unauthenticatedModel("model_$suffix")
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "unused" },
            delegate = delegate,
        )

        repeat(4) { retryOffset ->
            repository.generate(
                translationRequest().copy(
                    routeRetryOffset = retryOffset,
                    routeSemanticFailureKind = AiFailureKind.PARSE_ERROR.takeIf { retryOffset > 0 },
                )
            ).getOrThrow()
        }

        assertEquals(
            listOf("model_0", "model_1", "model_2", "model_3"),
            delegate.generateRequests.map { it.model.id },
        )
        assertEquals(listOf(false, false, false, true), dao.attempts.map { it.success })
        assertTrue(dao.targets.values.all { it.cooldownUntil == 0L && it.lastFailureKind == null })
    }

    @Test
    fun emptyTranslationStreamFallsBackToNextTarget() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute().copy(
            strategy = AiRouteStrategy.PRIORITY,
            maxAttempts = 2,
        )
        dao.credentials["credential_codex"] = oauthCredential()
        dao.targets["target_empty"] = AiRouteTargetEntity(
            id = "target_empty",
            routeProfileId = "route_translation",
            modelProfileId = "model_empty",
            credentialId = "credential_codex",
            priority = 0,
        )
        dao.targets["target_fallback"] = AiRouteTargetEntity(
            id = "target_fallback",
            routeProfileId = "route_translation",
            modelProfileId = "model_fallback",
            credentialId = "credential_codex",
            priority = 1,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_empty"] = oauthModel(id = "model_empty", modelId = "mimo-empty")
            modelConfigs["model_fallback"] = oauthModel(
                id = "model_fallback",
                modelId = "gpt-fallback",
            )
        }
        val delegate = ScriptedAiTextGateway(
            streamScripts = listOf(
                emptyList(),
                listOf(AiStreamEvent.Content("fallback")),
            )
        )
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        val events = repository.generateStream(translationRequest()).toList()

        assertEquals(listOf(AiStreamEvent.Content("fallback")), events)
        assertEquals(listOf("model_empty", "model_fallback"), delegate.streamRequests.map { it.model.id })
        assertEquals(AiFailureKind.EMPTY_OUTPUT.name, dao.attempts.first().failureKind)
        assertEquals("target_fallback", dao.attempts.last().targetId)
        assertEquals(0, dao.credentials.getValue("credential_codex").consecutiveFailures)
    }

    @Test
    fun partialTranslationStreamIsDiscardedBeforeComboFallback() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute().copy(
            strategy = AiRouteStrategy.PRIORITY,
            maxAttempts = 2,
        )
        dao.targets["target_partial"] = AiRouteTargetEntity(
            id = "target_partial",
            routeProfileId = "route_translation",
            modelProfileId = "model_partial",
            priority = 0,
        )
        dao.targets["target_fallback"] = AiRouteTargetEntity(
            id = "target_fallback",
            routeProfileId = "route_translation",
            modelProfileId = "model_fallback",
            priority = 1,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_partial"] = unauthenticatedModel("model_partial")
            modelConfigs["model_fallback"] = unauthenticatedModel("model_fallback")
        }
        val delegate = ScriptedAiTextGateway(
            streamScripts = listOf(
                listOf(AiStreamEvent.Content("partial from failed model")),
                listOf(AiStreamEvent.Content("complete fallback")),
            ),
            failAfterScripts = setOf(0),
        )
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "unused" },
            delegate = delegate,
        )

        val events = repository.generateStream(translationRequest()).toList()

        assertEquals(listOf(AiStreamEvent.Content("complete fallback")), events)
        assertEquals(
            listOf("model_partial", "model_fallback"),
            delegate.streamRequests.map { it.model.id },
        )
        assertFalse(dao.attempts.first().success)
        assertTrue(dao.attempts.last().success)
    }

    @Test
    fun queuedFallbackSkipsTargetQuarantinedAfterRouteResolution() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute().copy(
            strategy = AiRouteStrategy.PRIORITY,
            maxAttempts = 3,
        )
        dao.targets["target_empty"] = AiRouteTargetEntity(
            id = "target_empty",
            routeProfileId = "route_translation",
            modelProfileId = "model_empty",
            priority = 0,
        )
        dao.targets["target_quarantined"] = AiRouteTargetEntity(
            id = "target_quarantined",
            routeProfileId = "route_translation",
            modelProfileId = "model_quarantined",
            priority = 1,
        )
        dao.targets["target_healthy"] = AiRouteTargetEntity(
            id = "target_healthy",
            routeProfileId = "route_translation",
            modelProfileId = "model_healthy",
            priority = 2,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_empty"] = unauthenticatedModel("model_empty")
            modelConfigs["model_quarantined"] = unauthenticatedModel("model_quarantined")
            modelConfigs["model_healthy"] = unauthenticatedModel("model_healthy")
        }
        val delegate = ScriptedAiTextGateway(
            streamScripts = listOf(
                emptyList(),
                listOf(AiStreamEvent.Content("healthy fallback")),
            ),
            onStreamRequest = { _, requestIndex ->
                if (requestIndex == 0) {
                    dao.targets["target_quarantined"] = dao.targets
                        .getValue("target_quarantined")
                        .copy(cooldownUntil = 20_000L)
                }
            },
        )
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        val events = repository.generateStream(translationRequest()).toList()

        assertEquals(listOf(AiStreamEvent.Content("healthy fallback")), events)
        assertEquals(
            listOf("model_empty", "model_healthy"),
            delegate.streamRequests.map { it.model.id },
        )
        assertEquals(
            listOf("target_empty", "target_healthy"),
            dao.attempts.map { it.targetId },
        )
    }

    @Test
    fun oauthRefreshFailureIsStructuredAndDoesNotFallbackToDirectProvider() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_chat"] = chatRoute()
        dao.credentials["credential_codex"] = oauthCredential()
        dao.targets["target_codex"] = AiRouteTargetEntity(
            id = "target_codex",
            routeProfileId = "route_chat",
            modelProfileId = "model_codex",
            credentialId = "credential_codex",
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_codex"] = oauthModel()
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway {
                throw IllegalStateException("invalid_grant refresh token expired")
            },
            delegate = delegate,
        )

        val result = repository.generate(chatRequest())

        val error = result.exceptionOrNull() as AiProviderException
        assertEquals(AiFailureKind.AUTHENTICATION, error.failure.kind)
        assertEquals(0, delegate.generateCalls)
    }

    @Test
    fun allCooldownTargetsReturnTypedRouteUnavailableFailure() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_translation"] = translationRoute().copy(
            name = "Dịch AI · Free fallback",
        )
        dao.targets["target_cooldown"] = AiRouteTargetEntity(
            id = "target_cooldown",
            routeProfileId = "route_translation",
            modelProfileId = "model_cooldown",
            cooldownUntil = 20_000L,
            lastFailureKind = AiFailureKind.EMPTY_OUTPUT.name,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_cooldown"] = unauthenticatedModel("model_cooldown")
        }
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "unused" },
            delegate = RecordingAiTextGateway(),
        )

        val error = repository.generate(translationRequest()).exceptionOrNull() as AiProviderException

        assertEquals(AiFailureKind.ROUTE_UNAVAILABLE, error.failure.kind)
        assertEquals("Dịch AI · Free fallback", error.failure.routeName)
        assertEquals(10_000L, error.failure.retryAfterMillis)
        assertTrue(error.failure.targetSummary.contains(AiFailureKind.EMPTY_OUTPUT.name))
    }

    @Test
    fun authTargetWithoutCredentialOrApiKeyDoesNotFallbackToBlankProvider() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_chat"] = chatRoute()
        dao.targets["target_unbound"] = AiRouteTargetEntity(
            id = "target_unbound",
            routeProfileId = "route_chat",
            modelProfileId = "model_codex",
            credentialId = null,
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_codex"] = oauthModel()
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "unused" },
            delegate = delegate,
        )

        val result = repository.generate(chatRequest())

        val error = result.exceptionOrNull() as AiProviderException
        assertEquals(AiFailureKind.ROUTE_UNAVAILABLE, error.failure.kind)
        assertEquals(0, delegate.generateCalls)
    }

    @Test
    fun providerDataRuntimeMetadataRedactsTokenLikeKeys() = runBlocking {
        val dao = FakeAiRouterDao()
        dao.routes["route_chat"] = chatRoute()
        dao.credentials["credential_codex"] = AiCredentialEntity(
            id = "credential_codex",
            providerId = "oauth_codex",
            label = "Codex account",
            kind = AiCredentialKind.OAUTH_ACCESS_TOKEN,
            secretRef = "access_ref",
            oauthProvider = AiOAuthProviderId.CODEX,
            accountId = "account",
            accountLabel = "account@example.com",
            providerDataJson = """
                {
                  "resourceUrl": "https://tenant.example",
                  "deviceId": "device-1",
                  "access_token": "should-not-leak",
                  "apiKey": "should-not-leak"
                }
            """.trimIndent(),
        )
        dao.targets["target_codex"] = AiRouteTargetEntity(
            id = "target_codex",
            routeProfileId = "route_chat",
            modelProfileId = "model_codex",
            credentialId = "credential_codex",
        )
        val profileGateway = FakeAiProfileGateway().apply {
            modelConfigs["model_codex"] = oauthModel()
        }
        val delegate = RecordingAiTextGateway()
        val repository = repository(
            dao = dao,
            profileGateway = profileGateway,
            oauthGateway = FakeAiOAuthGateway { "access-token" },
            delegate = delegate,
        )

        val result = repository.generate(chatRequest())

        assertEquals("delegate", result.getOrThrow().text)
        val provider = delegate.lastRequest?.model?.provider
        assertEquals("https://tenant.example/v1", provider?.baseUrl)
        assertEquals("https://tenant.example", provider?.runtimeMetadata?.get("resourceUrl"))
        assertEquals("device-1", provider?.runtimeMetadata?.get("deviceId"))
        assertFalse(provider?.runtimeMetadata.orEmpty().containsKey("access_token"))
        assertFalse(provider?.runtimeMetadata.orEmpty().containsKey("apiKey"))
    }

    private fun repository(
        dao: FakeAiRouterDao,
        profileGateway: FakeAiProfileGateway,
        secretStore: AiSecretStore = FakeAiSecretStore(),
        oauthGateway: AiOAuthGateway,
        delegate: AiTextGateway,
    ): AiRouterRepository = AiRouterRepository(
        dao = dao,
        profileGateway = profileGateway,
        secretStore = secretStore,
        oauthGateway = oauthGateway,
        delegate = delegate,
        clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC),
    )

    private fun chatRoute() = AiRouteProfileEntity(
        id = "route_chat",
        name = "Chat",
        taskType = AiTaskType.CHAT,
        strategy = AiRouteStrategy.PRIORITY,
    )

    private fun translationRoute() = AiRouteProfileEntity(
        id = "route_translation",
        name = "Translation",
        taskType = AiTaskType.TRANSLATE_CHAPTER,
        strategy = AiRouteStrategy.ROUND_ROBIN,
    )

    private fun oauthCredential() = AiCredentialEntity(
        id = "credential_codex",
        providerId = "oauth_codex",
        label = "Codex account",
        kind = AiCredentialKind.OAUTH_ACCESS_TOKEN,
        secretRef = "access_ref",
        oauthProvider = AiOAuthProviderId.CODEX,
        refreshTokenRef = "refresh_ref",
        accountId = "account",
        accountLabel = "account@example.com",
    )

    private fun oauthModel(
        id: String = "model_codex",
        modelId: String = "gpt-5.6-terra",
    ) = AiModelConfig(
        id = id,
        provider = AiProviderConfig(
            id = "oauth_codex",
            name = "ChatGPT / Codex",
            protocol = AiProtocol.CODEX_SUBSCRIPTION,
            baseUrl = "https://chatgpt.com/backend-api/codex",
            apiKey = "",
            authType = AiProviderAuthType.BEARER,
        ),
        displayName = "GPT",
        modelId = modelId,
    )

    private fun apiKeyModel() = AiModelConfig(
        id = "model_api",
        provider = AiProviderConfig(
            id = "provider_api",
            name = "API Provider",
            protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://invalid.test",
            apiKey = "",
            authType = AiProviderAuthType.BEARER,
        ),
        displayName = "API Model",
        modelId = "api-model",
    )

    private fun unauthenticatedModel(id: String) = AiModelConfig(
        id = id,
        provider = AiProviderConfig(
            id = "provider_$id",
            name = "Provider $id",
            protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://invalid.test",
            apiKey = "",
            authType = AiProviderAuthType.NONE,
        ),
        displayName = id,
        modelId = id,
    )

    private fun chatRequest() = AiGenerateRequest(
        model = AiModelConfig(
            id = "direct_model",
            provider = AiProviderConfig(
                id = "direct_provider",
                name = "Direct",
                protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://invalid.test",
                apiKey = "",
            ),
            displayName = "Direct",
            modelId = "direct",
        ),
        messages = listOf(AiMessage(AiMessageRole.USER, "Xin chào")),
        taskType = AiTaskType.CHAT,
    )

    private fun translationRequest() = chatRequest().copy(
        taskType = AiTaskType.TRANSLATE_CHAPTER,
        routeSessionKey = "book:chapter",
    )
}

private class FakeAiRouterDao : AiRouterDao {
    val credentials = linkedMapOf<String, AiCredentialEntity>()
    val routes = linkedMapOf<String, AiRouteProfileEntity>()
    val targets = linkedMapOf<String, AiRouteTargetEntity>()
    val attempts = mutableListOf<AiRouteAttemptEntity>()

    override fun observeCredentials(): Flow<List<AiCredentialEntity>> =
        flowOf(credentials.values.toList())

    override fun observeRoutes(): Flow<List<AiRouteProfileEntity>> =
        flowOf(routes.values.toList())

    override fun observeTargets(): Flow<List<AiRouteTargetEntity>> =
        flowOf(targets.values.toList())

    override fun observeRecentAttempts(limit: Int): Flow<List<AiRouteAttemptEntity>> =
        flowOf(attempts.take(limit))

    override suspend fun getCredential(id: String): AiCredentialEntity? = credentials[id]

    override suspend fun getCredentialsForProvider(providerId: String): List<AiCredentialEntity> =
        credentials.values
            .filter { it.providerId == providerId }
            .sortedWith(compareBy<AiCredentialEntity> { it.sortNumber }.thenBy { it.createdAt })

    override suspend fun getRoute(id: String): AiRouteProfileEntity? = routes[id]

    override suspend fun getActiveRoute(taskType: String): AiRouteProfileEntity? =
        routes.values.firstOrNull { it.taskType == taskType && it.enabled }

    override suspend fun getTarget(id: String): AiRouteTargetEntity? = targets[id]

    override suspend fun getEnabledTargets(routeProfileId: String): List<AiRouteTargetEntity> =
        targets.values
            .filter { it.routeProfileId == routeProfileId && it.enabled }
            .sortedWith(compareBy<AiRouteTargetEntity> { it.priority }.thenBy { it.sortNumber })

    override suspend fun upsertCredential(entity: AiCredentialEntity) {
        credentials[entity.id] = entity
    }

    override suspend fun upsertRoute(entity: AiRouteProfileEntity) {
        routes[entity.id] = entity
    }

    override suspend fun upsertTarget(entity: AiRouteTargetEntity) {
        targets[entity.id] = entity
    }

    override suspend fun insertAttempt(entity: AiRouteAttemptEntity) {
        attempts += entity
    }

    override suspend fun markLatestAttemptSemanticFailure(
        routeProfileId: String,
        targetId: String,
        failureKind: String,
    ) {
        val index = attempts.indexOfLast { attempt ->
            attempt.routeProfileId == routeProfileId &&
                attempt.targetId == targetId &&
                attempt.success
        }
        if (index >= 0) {
            attempts[index] = attempts[index].copy(
                success = false,
                failureKind = failureKind,
            )
        }
    }

    override suspend fun deleteCredential(id: String) {
        credentials.remove(id)
    }

    override suspend fun clearCredentialFromTargets(credentialId: String) {
        targets.replaceAll { _, target ->
            if (target.credentialId == credentialId) target.copy(credentialId = null) else target
        }
    }

    override suspend fun deleteRouteRow(id: String) {
        routes.remove(id)
    }

    override suspend fun deleteTargetsForRoute(routeProfileId: String) {
        targets.entries.removeIf { it.value.routeProfileId == routeProfileId }
    }

    override suspend fun deleteTarget(id: String) {
        targets.remove(id)
    }

    override suspend fun clearDefaultRoutes(taskType: String) {
        routes.replaceAll { _, route ->
            if (route.taskType == taskType) route.copy(isDefault = false) else route
        }
    }

    override suspend fun markTargetFailure(
        targetId: String,
        failureKind: String,
        cooldownUntil: Long,
        now: Long,
    ) {
        targets[targetId]?.let { target ->
            targets[targetId] = target.copy(
                cooldownUntil = cooldownUntil,
                consecutiveFailures = target.consecutiveFailures + 1,
                lastFailureKind = failureKind,
                lastFailureAt = now,
                lastUsedAt = now,
                updatedAt = now,
            )
        }
    }

    override suspend fun markCredentialFailure(
        credentialId: String,
        failureKind: String,
        cooldownUntil: Long,
        now: Long,
    ) {
        credentials[credentialId]?.let { credential ->
            credentials[credentialId] = credential.copy(
                cooldownUntil = cooldownUntil,
                consecutiveFailures = credential.consecutiveFailures + 1,
                lastFailureKind = failureKind,
                lastFailureAt = now,
                lastUsedAt = now,
                updatedAt = now,
            )
        }
    }

    override suspend fun markTargetSuccess(targetId: String, now: Long) {
        targets[targetId]?.let { target ->
            targets[targetId] = target.copy(
                cooldownUntil = 0,
                consecutiveFailures = 0,
                lastFailureKind = null,
                lastSuccessAt = now,
                lastUsedAt = now,
                updatedAt = now,
            )
        }
    }

    override suspend fun markCredentialSuccess(credentialId: String, now: Long) {
        credentials[credentialId]?.let { credential ->
            credentials[credentialId] = credential.copy(
                cooldownUntil = 0,
                consecutiveFailures = 0,
                lastFailureKind = null,
                lastSuccessAt = now,
                lastUsedAt = now,
                updatedAt = now,
            )
        }
    }

    override suspend fun updateOAuthTokens(
        credentialId: String,
        accessTokenRef: String,
        refreshTokenRef: String?,
        idTokenRef: String?,
        expiresAt: Long?,
        scopes: String?,
        status: String,
        providerDataJson: String?,
        now: Long,
    ) {
        credentials[credentialId]?.let { credential ->
            credentials[credentialId] = credential.copy(
                secretRef = accessTokenRef,
                refreshTokenRef = refreshTokenRef,
                idTokenRef = idTokenRef,
                expiresAt = expiresAt,
                scopes = scopes,
                status = status,
                providerDataJson = providerDataJson,
                cooldownUntil = 0,
                consecutiveFailures = 0,
                lastFailureKind = null,
                updatedAt = now,
            )
        }
    }

    override suspend fun updateCredentialStatus(credentialId: String, status: String, now: Long) {
        credentials[credentialId]?.let { credential ->
            credentials[credentialId] = credential.copy(status = status, updatedAt = now)
        }
    }

    override suspend fun resetTargetHealth(targetId: String?, now: Long) {
        targets.replaceAll { id, target ->
            if (targetId == null || id == targetId) {
                target.copy(
                    cooldownUntil = 0,
                    consecutiveFailures = 0,
                    lastFailureKind = null,
                    updatedAt = now,
                )
            } else {
                target
            }
        }
    }

    override suspend fun resetCredentialHealth(credentialId: String?, now: Long) {
        credentials.replaceAll { id, credential ->
            if (credentialId == null || id == credentialId) {
                credential.copy(
                    cooldownUntil = 0,
                    consecutiveFailures = 0,
                    lastFailureKind = null,
                    updatedAt = now,
                )
            } else {
                credential
            }
        }
    }

    override suspend fun trimAttempts(keep: Int) {
        if (attempts.size > keep) attempts.subList(keep, attempts.size).clear()
    }
}

private class FakeAiProfileGateway : AiProfileGateway {
    val modelConfigs = linkedMapOf<String, AiModelConfig>()

    override fun observeProviders(): Flow<List<AiProviderProfile>> = flowOf(emptyList())
    override fun observeModels(): Flow<List<AiModelProfile>> = flowOf(emptyList())
    override fun observePresets(): Flow<List<AiTaskPreset>> = flowOf(emptyList())
    override suspend fun getProvider(id: String): AiProviderProfile? = null
    override suspend fun getModel(id: String): AiModelProfile? = null
    override suspend fun getModelConfig(id: String): AiModelConfig? = modelConfigs[id]
    override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? = null
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

    override suspend fun saveTaskPreset(draft: AiTaskPresetDraft): AiTaskPresetConfig = error("unused")
    override suspend fun setDefaultTaskPreset(presetId: String): AiTaskPresetConfig = error("unused")
    override suspend fun deleteTaskPreset(presetId: String) = Unit
    override suspend fun deleteProvider(providerId: String) = Unit
    override suspend fun deleteModel(modelId: String) = Unit
}

private class FakeAiSecretStore(
    private val values: MutableMap<String, String> = linkedMapOf(),
) : AiSecretStore {

    override fun put(secret: String, secretRef: String?): String {
        val ref = secretRef ?: "secret_${values.size}"
        values[ref] = secret
        return ref
    }

    override fun get(secretRef: String): String? = values[secretRef]

    override fun delete(secretRef: String) {
        values.remove(secretRef)
    }
}

private class FakeAiOAuthGateway(
    private val resolve: suspend (String) -> String,
) : AiOAuthGateway {
    override val events: Flow<AiOAuthEvent> = emptyFlow()
    override fun providers(): List<AiOAuthProviderConfig> = emptyList()
    override suspend fun begin(providerId: String): AiOAuthAuthorization = error("unused")
    override suspend fun resolveAccessToken(credentialId: String): String = resolve(credentialId)
    override suspend fun refresh(credentialId: String): Result<Unit> = Result.success(Unit)
    override suspend fun syncModels(credentialId: String): Result<List<AiAvailableModel>> =
        Result.success(emptyList())
}

private class RecordingAiTextGateway : AiTextGateway {
    val generateRequests = mutableListOf<AiGenerateRequest>()
    val streamRequests = mutableListOf<AiGenerateRequest>()
    var generateCalls = 0
        private set
    var streamCalls = 0
        private set
    var lastRequest: AiGenerateRequest? = null
        private set
    var lastStreamRequest: AiGenerateRequest? = null
        private set

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> {
        generateCalls++
        generateRequests += request
        lastRequest = request
        return Result.success(AiGenerateResponse("delegate"))
    }

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        streamCalls++
        streamRequests += request
        lastStreamRequest = request
        emit(AiStreamEvent.Content("delegate"))
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        Result.success(emptyList())
}

private class ConcurrencyRecordingAiTextGateway : AiTextGateway {
    private val inFlight = AtomicInteger()
    val maxInFlight = AtomicInteger()

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> {
        val active = inFlight.incrementAndGet()
        maxInFlight.updateAndGet { previous -> maxOf(previous, active) }
        return try {
            delay(40)
            Result.success(AiGenerateResponse("delegate"))
        } finally {
            inFlight.decrementAndGet()
        }
    }

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> =
        flowOf(AiStreamEvent.Content("delegate"))

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        Result.success(emptyList())
}

private class ScriptedAiTextGateway(
    private val streamScripts: List<List<AiStreamEvent>>,
    private val onStreamRequest: suspend (AiGenerateRequest, Int) -> Unit = { _, _ -> },
    private val failAfterScripts: Set<Int> = emptySet(),
) : AiTextGateway {
    val streamRequests = mutableListOf<AiGenerateRequest>()

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        Result.success(AiGenerateResponse(""))

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        streamRequests += request
        onStreamRequest(request, streamRequests.lastIndex)
        streamScripts.getOrElse(streamRequests.lastIndex) { emptyList() }
            .forEach { emit(it) }
        if (streamRequests.lastIndex in failAfterScripts) {
            error("scripted stream failure")
        }
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        Result.success(emptyList())
}
