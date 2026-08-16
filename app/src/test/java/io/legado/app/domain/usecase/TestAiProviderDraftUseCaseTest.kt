package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiConnectionStatus
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderConnectionDraft
import io.legado.app.domain.model.AiProviderConnectionMode
import io.legado.app.domain.model.AiProviderFamily
import io.legado.app.domain.model.AiProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TestAiProviderDraftUseCaseTest {

    @Test
    fun freeModeDoesNotRequireApiKey() = runBlocking {
        val gateway = RecordingAiTextGateway()
        val useCase = TestAiProviderDraftUseCase(gateway, fixedClock())

        val result = useCase(
            draft(
                authType = AiProviderAuthType.NONE,
                apiKey = "",
                modelId = "big-pickle",
            )
        )

        assertEquals(AiConnectionStatus.READY, result.status)
        assertEquals(1, gateway.generateCalls)
        assertEquals("", gateway.lastRequest?.model?.provider?.apiKey)
    }

    @Test
    fun paidModeMissingKeyIsBlockedBeforeNetworkCalls() = runBlocking {
        val gateway = RecordingAiTextGateway()
        val useCase = TestAiProviderDraftUseCase(gateway, fixedClock())

        val result = useCase(
            draft(
                authType = AiProviderAuthType.BEARER,
                apiKey = "",
                modelId = "paid-model",
            )
        )

        assertEquals(AiConnectionStatus.ERROR, result.status)
        assertEquals("Cần nhập API key hoặc token", result.message)
        assertEquals(0, gateway.fetchModelsCalls)
        assertEquals(0, gateway.generateCalls)
        assertNull(gateway.lastRequest)
    }

    @Test
    fun draftTestUsesUnsavedProviderValuesAndDiscoveredModel() = runBlocking {
        val gateway = RecordingAiTextGateway(
            fetchedModels = listOf(AiAvailableModel("remote-model", "Remote Model", 128_000, 8_000))
        )
        val useCase = TestAiProviderDraftUseCase(gateway, fixedClock())

        val result = useCase(
            draft(
                baseUrl = "https://draft.example/v1",
                modelsUrl = "https://draft.example/v1/models",
                apiKey = "draft-key",
                modelId = "",
            )
        )

        assertEquals(AiConnectionStatus.READY, result.status)
        assertEquals("remote-model", result.selectedModel?.id)
        assertEquals("https://draft.example/v1", gateway.lastRequest?.model?.provider?.baseUrl)
        assertEquals("draft-key", gateway.lastRequest?.model?.provider?.apiKey)
        assertEquals("remote-model", gateway.lastRequest?.model?.modelId)
    }

    @Test
    fun gatewayProbePrefersAvailableOpenCodeFreeModelWhenCatalogModelIsCleared() = runBlocking {
        val gateway = RecordingAiTextGateway(
            fetchedModels = listOf(
                AiAvailableModel("alicode-intl/qwen3.5-plus", "Qwen 3.5 Plus", 0, 0),
                AiAvailableModel(
                    "oc/deepseek-v4-flash-free",
                    "DeepSeek V4 Flash Free",
                    0,
                    0,
                ),
                AiAvailableModel("oc/mimo-v2.5-free", "MiMo V2.5 Free", 0, 0),
            )
        )
        val useCase = TestAiProviderDraftUseCase(gateway, fixedClock())

        val result = useCase(
            draft(
                baseUrl = "http://localhost:20128/v1",
                modelsUrl = "http://localhost:20128/v1/models",
                modelId = "",
            )
        )

        assertEquals(AiConnectionStatus.READY, result.status)
        assertEquals("oc/mimo-v2.5-free", result.selectedModel?.id)
        assertEquals("oc/mimo-v2.5-free", gateway.lastRequest?.model?.modelId)
        assertEquals(1_024, gateway.lastRequest?.params?.maxOutputTokens)
    }

    @Test
    fun reasoningModelProbeHasEnoughOutputBudgetForFinalContent() = runBlocking {
        val gateway = RecordingAiTextGateway()
        val useCase = TestAiProviderDraftUseCase(gateway, fixedClock())

        val result = useCase(
            draft(
                authType = AiProviderAuthType.NONE,
                apiKey = "",
                modelId = "deepseek-v4-flash-free",
            )
        )

        assertEquals(AiConnectionStatus.READY, result.status)
        assertEquals(1_024, gateway.lastRequest?.params?.maxOutputTokens)
        assertTrue(AiCapability.REASONING in gateway.lastRequest!!.model.capabilities)
    }

    @Test
    fun openCodeBigPickleProbeUsesReasoningBudget() = runBlocking {
        val gateway = RecordingAiTextGateway()
        val useCase = TestAiProviderDraftUseCase(gateway, fixedClock())

        val result = useCase(
            draft(
                authType = AiProviderAuthType.NONE,
                apiKey = "",
                modelId = "big-pickle",
            )
        )

        assertEquals(AiConnectionStatus.READY, result.status)
        assertEquals(1_024, gateway.lastRequest?.params?.maxOutputTokens)
        assertTrue(AiCapability.REASONING in gateway.lastRequest!!.model.capabilities)
    }

    private fun draft(
        baseUrl: String = "https://provider.example/v1",
        modelsUrl: String? = null,
        authType: String = AiProviderAuthType.BEARER,
        apiKey: String = "secret",
        modelId: String = "model",
    ): AiProviderConnectionDraft =
        AiProviderConnectionDraft(
            catalogId = "test",
            providerName = "Test Provider",
            familyId = AiProviderFamily.OPENCODE,
            connectionMode = AiProviderConnectionMode.API,
            protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = baseUrl,
            modelsUrl = modelsUrl,
            apiKey = apiKey,
            authType = authType,
            modelId = modelId,
            modelName = modelId.ifBlank { "Remote Model" },
        )

    private fun fixedClock(): Clock =
        Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC)

    private class RecordingAiTextGateway(
        private val fetchedModels: List<AiAvailableModel> = emptyList(),
    ) : AiTextGateway {
        var fetchModelsCalls = 0
        var generateCalls = 0
        var lastRequest: AiGenerateRequest? = null

        override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> {
            generateCalls += 1
            lastRequest = request
            return Result.success(AiGenerateResponse("OK"))
        }

        override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = emptyFlow()

        override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> {
            fetchModelsCalls += 1
            return Result.success(fetchedModels)
        }
    }
}
