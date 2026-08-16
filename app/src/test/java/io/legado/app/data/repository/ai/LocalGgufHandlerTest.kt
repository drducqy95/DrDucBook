package io.legado.app.data.repository.ai

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.domain.gateway.LocalAiModelMetadata
import io.legado.app.domain.gateway.LocalAiEmptyOutputException
import io.legado.app.domain.gateway.LocalAiUnsupportedAbiException
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalGgufHandlerTest {

    @Test
    fun localHandlerUsesTheSameCompleteRequestContractAsOnlineProviders() = runBlocking {
        val engine = FakeLocalEngine()
        val handler = LocalGgufHandler(engine)
        val request = AiGenerateRequest(
            model = AiModelConfig(
                id = "hy-mt2",
                provider = AiProviderConfig(
                    id = "local",
                    name = "Local",
                    protocol = AiProtocol.LOCAL_GGUF,
                    baseUrl = "file:///models/hy-mt2.gguf",
                    apiKey = "",
                ),
                displayName = "Hy-MT2",
                modelId = "hy-mt2",
                contextWindow = 4_096,
            ),
            messages = listOf(
                AiMessage(AiMessageRole.SYSTEM, "preserve layout"),
                AiMessage(AiMessageRole.USER, "source"),
            ),
            params = AiGenerationParams(
                temperature = 0.7f,
                maxOutputTokens = 1_024,
                topP = 0.6f,
                topK = 20,
                repetitionPenalty = 1.05f,
            ),
            tools = listOf(
                AiToolDefinition("lookup", "Lookup a term", mapOf("type" to "object")),
            ),
        )

        val response = handler.generate(request).getOrThrow()

        assertEquals("translated", response.text)
        assertEquals("/models/hy-mt2.gguf", engine.modelPath)
        assertSame(request, engine.request)
        assertEquals(20, engine.request?.params?.topK)
        assertEquals("lookup", engine.request?.tools?.single()?.name)
    }

    @Test
    fun emptyNativeOutputReturnsTypedFailure() = runBlocking {
        val handler = LocalGgufHandler(FakeLocalEngine(output = ""))

        val failure = handler.generate(request()).exceptionOrNull()

        assertTrue(failure is LocalAiEmptyOutputException)
    }

    @Test
    fun unavailableNativeRuntimeDoesNotAdvertiseModelAsReady() = runBlocking {
        val handler = LocalGgufHandler(FakeLocalEngine(nativeAvailable = false))

        val failure = handler.fetchModels(request().model.provider).exceptionOrNull()

        assertTrue(failure is LocalAiUnsupportedAbiException)
    }

    private fun request() = AiGenerateRequest(
        model = AiModelConfig(
            id = "local",
            provider = AiProviderConfig(
                id = "local",
                name = "Local",
                protocol = AiProtocol.LOCAL_GGUF,
                baseUrl = "file:///models/test.gguf",
                apiKey = "",
            ),
            displayName = "Local",
            modelId = "local",
        ),
        messages = listOf(AiMessage(AiMessageRole.USER, "translate")),
    )

    private class FakeLocalEngine(
        private val output: String = "translated",
        nativeAvailable: Boolean = true,
    ) : LocalAiEngineGateway {
        override val nativeRuntimeAvailable: Boolean = nativeAvailable
        var modelPath: String? = null
        var request: AiGenerateRequest? = null

        override fun generateStream(
            modelPath: String,
            request: AiGenerateRequest,
        ): Flow<AiStreamEvent> {
            this.modelPath = modelPath
            this.request = request
            return flowOf(AiStreamEvent.Content(output))
        }

        override suspend fun inspectModel(modelPath: String): Result<LocalAiModelMetadata> =
            error("Not used")

        override suspend fun importModel(sourceUri: String): Result<LocalAiModelMetadata> =
            error("Not used")

        override suspend fun unload() = Unit
    }
}
