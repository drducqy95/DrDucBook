package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class AntigravityHandlerTest {

    @Test
    fun buildsIdeEnvelopeWithSessionInsideRequest() {
        val body = buildAntigravityRequestEnvelope(
            request = request(),
            projectId = "cloud-project",
            sessionId = "session-123",
            currentTimeMillis = 1_700_000_000_000L,
        )

        assertEquals("cloud-project", body["project"])
        assertEquals("gemini-pro-agent", body["model"])
        assertEquals("agent", body["requestType"])
        assertEquals("antigravity", body["userAgent"])
        assertFalse(body.containsKey("sessionId"))

        val nestedRequest = body["request"] as Map<*, *>
        assertEquals("session-123", nestedRequest["sessionId"])
        assertNotNull(nestedRequest["contents"])
        assertEquals(
            "agent/8eb7b8a2-38ff-5a67-bcb8-a7c513db5b78/1700000000000/" +
                "b89029b7-f8fd-5ce0-9470-e47ea4edaeb7/1",
            body["requestId"],
        )
    }

    @Test
    fun enablesValidatedFunctionCallingForAntigravityTools() {
        val body = buildAntigravityRequestEnvelope(
            request = request(
                tools = listOf(
                    AiToolDefinition(
                        name = "search_books",
                        description = "Search books",
                        inputSchema = mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "query" to mapOf("type" to "string")
                            ),
                        ),
                    )
                )
            ),
            projectId = "cloud-project",
            sessionId = "session-123",
            currentTimeMillis = 1_700_000_000_000L,
        )

        val nestedRequest = body["request"] as Map<*, *>
        assertEquals(
            mapOf("functionCallingConfig" to mapOf("mode" to "VALIDATED")),
            nestedRequest["toolConfig"],
        )
    }

    @Test
    fun requestsJsonForTranslationAndKeepsItInAntigravityEnvelope() {
        val body = buildAntigravityRequestEnvelope(
            request = request(taskType = AiTaskType.TRANSLATE_CHAPTER),
            projectId = "cloud-project",
            sessionId = "session-123",
            currentTimeMillis = 1_700_000_000_000L,
        )

        val nestedRequest = body["request"] as Map<*, *>
        val generationConfig = nestedRequest["generationConfig"] as Map<*, *>
        assertEquals("application/json", generationConfig["responseMimeType"])
    }

    private fun request(
        tools: List<AiToolDefinition> = emptyList(),
        taskType: String? = null,
    ) = AiGenerateRequest(
        model = AiModelConfig(
            id = "antigravity-model",
            provider = AiProviderConfig(
                id = "oauth_antigravity",
                name = "Google Antigravity",
                protocol = AiProtocol.ANTIGRAVITY,
                baseUrl = "https://daily-cloudcode-pa.googleapis.com",
                apiKey = "access-token",
            ),
            displayName = "Gemini 3.1 Pro (High)",
            modelId = "gemini-pro-agent",
        ),
        messages = listOf(AiMessage(AiMessageRole.USER, "Hello")),
        tools = tools,
        taskType = taskType,
    )
}
