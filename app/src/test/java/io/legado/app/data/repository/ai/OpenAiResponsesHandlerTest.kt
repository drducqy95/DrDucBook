package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Base64

class OpenAiResponsesHandlerTest {

    @Test
    fun codexBodyMatchesSubscriptionConstraints() {
        val body = request(AiProtocol.CODEX_SUBSCRIPTION)
            .toOpenAiResponsesBody(stream = true)
        val input = responseInput(body)

        assertEquals(true, body["stream"])
        assertEquals(false, body["store"])
        assertFalse(body.containsKey("temperature"))
        assertFalse(body.containsKey("top_p"))
        assertFalse(body.containsKey("max_output_tokens"))
        assertEquals("user", input.first()["role"])
        assertEquals("message", input.first()["type"])
        assertEquals(
            listOf(mapOf("type" to "input_text", "text" to "Hello")),
            input.first()["content"],
        )
        assertEquals("System prompt", body["instructions"])
        assertEquals("low", responseReasoning(body)["effort"])
        assertEquals(listOf("reasoning.encrypted_content"), body["include"])
    }

    @Test
    fun normalResponsesBodyKeepsStandardGenerationParameters() {
        val body = request(AiProtocol.OPENAI_RESPONSES)
            .toOpenAiResponsesBody(stream = true)
        val input = responseInput(body)

        assertEquals(0.2f, body["temperature"])
        assertEquals(0.8f, body["top_p"])
        assertEquals(256, body["max_output_tokens"])
        assertFalse(body.containsKey("store"))
        assertEquals(AiMessageRole.SYSTEM, input.first()["role"])
    }

    @Test
    fun codexBodyMapsVirtualReviewModelAndReasoningSuffix() {
        val body = request(
            protocol = AiProtocol.CODEX_SUBSCRIPTION,
            modelId = "gpt-5.6-terra-review-high",
            routeSessionKey = "conversation-1",
        ).toOpenAiResponsesBody(stream = false)

        assertEquals("gpt-5.6-terra", body["model"])
        assertEquals(true, body["stream"])
        assertEquals("conversation-1", body["prompt_cache_key"])
        assertEquals("high", responseReasoning(body)["effort"])
        assertEquals("auto", responseReasoning(body)["summary"])
    }

    @Test
    fun codexBodyAccepts9RouterAliasPrefix() {
        val body = request(
            protocol = AiProtocol.CODEX_SUBSCRIPTION,
            modelId = "cx/gpt-5.6-terra-review-high",
            runtimeMetadata = mapOf("chatgptAccountId" to "acct-chatgpt"),
        ).toOpenAiResponsesBody(stream = false)

        assertEquals("gpt-5.6-terra", body["model"])
        assertEquals("acct-chatgpt", body["prompt_cache_key"])
        assertEquals("high", responseReasoning(body)["effort"])
    }

    @Test
    fun codexBodyCanDisableReasoningWithoutEncryptedInclude() {
        val body = request(
            protocol = AiProtocol.CODEX_SUBSCRIPTION,
            params = AiGenerationParams(reasoningLevel = AiReasoningLevel.OFF),
        ).toOpenAiResponsesBody(stream = true)

        assertEquals("none", responseReasoning(body)["effort"])
        assertFalse(body.containsKey("include"))
    }

    @Test
    fun codexHeadersCarryAccountAndStableSession() {
        val provider = provider(
            protocol = AiProtocol.CODEX_SUBSCRIPTION,
            runtimeMetadata = mapOf("accountId" to "account-1"),
        )

        val headers = openAiResponsesHeaders(
            provider = provider,
            accessToken = "token",
            routeSessionKey = "conversation-1",
        )

        assertEquals("Bearer token", headers["Authorization"])
        assertEquals("account-1", headers["ChatGPT-Account-ID"])
        assertEquals("conversation-1", headers["session_id"])
        assertEquals("codex_cli_rs", headers["originator"])
    }

    @Test
    fun codexHeadersFollow9RouterAccountFallbacks() {
        val provider = provider(
            protocol = AiProtocol.CODEX_SUBSCRIPTION,
            runtimeMetadata = mapOf(
                "workspaceId" to "workspace-1",
                "chatgptAccountId" to "chatgpt-1",
                "accountId" to "account-1",
            ),
        )

        val headers = openAiResponsesHeaders(
            provider = provider,
            accessToken = "token",
        )

        assertEquals("workspace-1", headers["ChatGPT-Account-ID"])
        assertEquals("workspace-1", headers["session_id"])
    }

    @Test
    fun codexHeadersUsePersistedSessionWhenRouteDoesNotProvideOne() {
        val provider = provider(
            protocol = AiProtocol.CODEX_SUBSCRIPTION,
            runtimeMetadata = mapOf(
                "sessionId" to "persisted-session",
                "accountId" to "account-1",
            ),
        )

        val headers = openAiResponsesHeaders(provider, "token")

        assertEquals("persisted-session", headers["session_id"])
    }

    @Test
    fun codexHeadersDeriveAccountIdFromAccessTokenClaim() {
        val provider = provider(protocol = AiProtocol.CODEX_SUBSCRIPTION)
        val token = unsignedJwt(
            """{"https://api.openai.com/auth.chatgpt_account_id":"acct-from-token"}"""
        )

        val headers = openAiResponsesHeaders(
            provider = provider,
            accessToken = token,
        )

        assertEquals("acct-from-token", headers["ChatGPT-Account-ID"])
        assertEquals("acct-from-token", headers["session_id"])
        assertEquals("text/event-stream", headers["Accept"])
    }

    @Test
    fun codexHeadersDoNotUseEmailAsAccountIdFallback() {
        val provider = provider(
            protocol = AiProtocol.CODEX_SUBSCRIPTION,
            runtimeMetadata = mapOf("accountId" to "user@example.com"),
        )

        val headers = openAiResponsesHeaders(
            provider = provider,
            accessToken = "token",
        )

        assertFalse(headers.containsKey("ChatGPT-Account-ID"))
        assertEquals("default", headers["session_id"])
    }

    @Test
    fun responsesEndpointAccepts9RouterFullEndpointBaseUrl() {
        val provider = provider(AiProtocol.CODEX_SUBSCRIPTION).copy(
            baseUrl = "https://chatgpt.com/backend-api/codex/responses",
            responsesPath = "/responses",
        )

        assertEquals(
            "https://chatgpt.com/backend-api/codex/responses",
            provider.responsesEndpointUrl(),
        )
    }

    @Test
    fun codexStreamErrorMessageMatches9RouterCapacityPattern() {
        val data = """
            event: error
            data: {"error":{"message":"Selected model is at capacity. Please try a different model."}}
        """.trimIndent()

        assertEquals(
            "Selected model is at capacity. Please try a different model.",
            data.extractCodexStreamErrorMessage(),
        )
    }

    private fun request(
        protocol: String,
        modelId: String = "gpt-test",
        params: AiGenerationParams = AiGenerationParams(
            temperature = 0.2f,
            maxOutputTokens = 256,
            topP = 0.8f,
        ),
        routeSessionKey: String? = null,
        runtimeMetadata: Map<String, String> = emptyMap(),
    ) = AiGenerateRequest(
        model = AiModelConfig(
            id = "model",
            provider = provider(protocol, runtimeMetadata),
            displayName = "Model",
            modelId = modelId,
        ),
        messages = listOf(
            AiMessage(AiMessageRole.SYSTEM, "System prompt"),
            AiMessage(AiMessageRole.USER, "Hello"),
        ),
        params = params,
        routeSessionKey = routeSessionKey,
    )

    private fun provider(
        protocol: String,
        runtimeMetadata: Map<String, String> = emptyMap(),
    ) = AiProviderConfig(
        id = "provider",
        name = "Provider",
        protocol = protocol,
        baseUrl = "https://example.test/v1",
        apiKey = "token",
        runtimeMetadata = runtimeMetadata,
    )

    private fun unsignedJwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body."
    }

    @Suppress("UNCHECKED_CAST")
    private fun responseInput(body: Map<String, Any?>): List<Map<String, Any?>> =
        body["input"] as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun responseReasoning(body: Map<String, Any?>): Map<String, Any?> =
        body["reasoning"] as Map<String, Any?>
}
