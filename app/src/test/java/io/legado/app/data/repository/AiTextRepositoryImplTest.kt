package io.legado.app.data.repository

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.domain.gateway.LocalAiModelMetadata
import io.legado.app.domain.model.AiFailureKind
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiTextRepositoryImplTest {

    private val repository = AiTextRepositoryImpl(UnusedLocalEngine)

    @Test
    fun generateNormalizesRegistryErrorsWithProviderModelAndAttempt() = runBlocking {
        val result = repository.generate(unsupportedRequest())
        val error = result.exceptionOrNull() as AiProviderException

        assertEquals(AiFailureKind.CONFIGURATION, error.failure.kind)
        assertEquals("Broken provider", error.failure.provider)
        assertEquals("broken-model", error.failure.model)
        assertEquals(1, error.failure.attempt)
        assertTrue(error.message.orEmpty().contains("lần thử 1"))
    }

    @Test
    fun streamUsesTheSameStructuredFailureContract() = runBlocking {
        try {
            repository.generateStream(unsupportedRequest()).toList()
            fail("Expected structured provider error")
        } catch (error: AiProviderException) {
            assertEquals(AiFailureKind.CONFIGURATION, error.failure.kind)
            assertEquals("Broken provider", error.failure.provider)
            assertEquals("broken-model", error.failure.model)
        }
    }

    private fun unsupportedRequest() = AiGenerateRequest(
        model = AiModelConfig(
            id = "broken-model-profile",
            provider = AiProviderConfig(
                id = "broken-provider",
                name = "Broken provider",
                protocol = "unsupported_protocol",
                baseUrl = "https://invalid.test",
                apiKey = "redacted",
            ),
            displayName = "Broken model",
            modelId = "broken-model",
        ),
        messages = listOf(AiMessage(AiMessageRole.USER, "test")),
    )

    private object UnusedLocalEngine : LocalAiEngineGateway {
        override val nativeRuntimeAvailable: Boolean = false

        override fun generateStream(
            modelPath: String,
            request: AiGenerateRequest,
        ): Flow<AiStreamEvent> = emptyFlow()

        override suspend fun inspectModel(modelPath: String): Result<LocalAiModelMetadata> =
            Result.failure(AssertionError("unused"))

        override suspend fun importModel(sourceUri: String): Result<LocalAiModelMetadata> =
            Result.failure(AssertionError("unused"))

        override suspend fun unload() = Unit
    }
}
