package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnthropicHandlerTest {

    @Test
    fun apiKeyPlaceholderIsResolvedForClaudeCompatibleProviders() {
        val provider = AiProviderConfig(
            id = "glm",
            name = "GLM",
            protocol = AiProtocol.ANTHROPIC_MESSAGES,
            baseUrl = "https://example.test",
            apiKey = "secret",
            authType = AiProviderAuthType.HEADER,
            customHeaders = mapOf("x-api-key" to "{apiKey}"),
        )

        val headers = anthropicHeaders(provider, "secret")

        assertEquals("secret", headers["x-api-key"])
        assertFalse(headers.containsKey("Authorization"))
    }

    @Test
    fun bearerCompatibleProviderReceivesAuthorizationHeader() {
        val provider = AiProviderConfig(
            id = "compatible",
            name = "Compatible",
            protocol = AiProtocol.ANTHROPIC_MESSAGES,
            baseUrl = "https://example.test",
            apiKey = "secret",
            authType = AiProviderAuthType.BEARER,
        )

        assertEquals("Bearer secret", anthropicHeaders(provider, "secret")["Authorization"])
    }

    @Test
    fun kimiOAuthCredentialAddsStableDeviceHeaders() {
        val provider = AiProviderConfig(
            id = "oauth_kimi",
            name = "Kimi Code",
            protocol = AiProtocol.ANTHROPIC_MESSAGES,
            baseUrl = "https://api.kimi.com/coding",
            apiKey = "token",
            authType = AiProviderAuthType.HEADER,
            customHeaders = mapOf("x-api-key" to "{apiKey}"),
            runtimeMetadata = mapOf("deviceId" to "device-123"),
        )

        val headers = anthropicHeaders(provider, "token")

        assertEquals("token", headers["x-api-key"])
        assertEquals("device-123", headers["X-Msh-Device-Id"])
        assertEquals("9router", headers["X-Msh-Platform"])
    }
}
