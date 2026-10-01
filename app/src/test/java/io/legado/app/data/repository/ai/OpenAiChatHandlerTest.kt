package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChatHandlerTest {

    @Test
    fun extractsClassicStringContent() {
        val body = """{"choices":[{"message":{"content":"Bản dịch"}}]}"""

        assertEquals("Bản dịch", extractOpenAiChatResponseText(body))
    }

    @Test
    fun extractsTypedContentPartsUsedByCompatibleProviders() {
        val body = """
            {
              "choices": [{
                "message": {
                  "content": [
                    {"type": "text", "text": "Đoạn một."},
                    {"type": "text", "text": "\n\nĐoạn hai."}
                  ]
                }
              }]
            }
        """.trimIndent()

        assertEquals(
            "Đoạn một.\n\nĐoạn hai.",
            extractOpenAiChatResponseText(body),
        )
    }

    @Test
    fun extractsLegacyChoiceTextFallback() {
        val body = """{"choices":[{"text":"Bản dịch cũ"}]}"""

        assertEquals("Bản dịch cũ", extractOpenAiChatResponseText(body))
    }

    @Test
    fun extractsContentFromNonStreamingJsonReturnedToStreamRequest() {
        val body = """{"choices":[{"message":{"content":"Ban dich"}}]}"""

        assertEquals(
            OpenAiChatStreamText(reasoning = null, content = "Ban dich"),
            extractOpenAiChatStreamText(body),
        )
    }

    @Test
    fun extractsReasoningFromNonStreamingJsonReturnedToStreamRequest() {
        val body = """
            {"choices":[{"message":{"reasoning_content":"Dang xu ly","content":"Ban dich"}}]}
        """.trimIndent()

        assertEquals(
            OpenAiChatStreamText(reasoning = "Dang xu ly", content = "Ban dich"),
            extractOpenAiChatStreamText(body),
        )
    }

    @Test
    fun extractsTypedDeltaContentFromCompatibleStream() {
        val body = """
            {
              "choices": [{
                "delta": {
                  "reasoning_content": {"text": "Dang xu ly"},
                  "content": [{"type": "text", "text": "Ban dich"}]
                }
              }]
            }
        """.trimIndent()

        assertEquals(
            OpenAiChatStreamText(reasoning = "Dang xu ly", content = "Ban dich"),
            extractOpenAiChatStreamText(body),
        )
    }

    @Test
    fun noAuthProviderDoesNotRequireOrSendApiKey() {
        val provider = AiProviderConfig(
            id = "free",
            name = "Free",
            protocol = "openai_chat_completions",
            baseUrl = "https://example.test/v1",
            apiKey = "",
            authType = AiProviderAuthType.NONE,
        )

        assertTrue(provider.hasRequiredCredential())
        assertFalse(openAiChatHeaders(provider, "").containsKey("Authorization"))
    }

    @Test
    fun customHeaderSupportsApiKeyPlaceholders() {
        val provider = AiProviderConfig(
            id = "header",
            name = "Header",
            protocol = "openai_chat_completions",
            baseUrl = "https://example.test/v1",
            apiKey = "secret",
            authType = AiProviderAuthType.HEADER,
            customHeaders = mapOf(
                "x-api-key" to "{apiKey}",
                "x-token" to "\$API_KEY",
            ),
        )

        val headers = openAiChatHeaders(provider, "secret")

        assertEquals("secret", headers["x-api-key"])
        assertEquals("secret", headers["x-token"])
        assertFalse(headers.containsKey("Authorization"))
    }

    @Test
    fun openAiChatHeadersInjectsOpenCodeCliHeadersWhenBaseUrlIsOpencode() {
        val provider = AiProviderConfig(
            id = "opencode_free",
            name = "OpenCode Free",
            protocol = "openai_chat_completions",
            baseUrl = "https://opencode.ai/zen/v1",
            apiKey = "",
            authType = AiProviderAuthType.NONE,
        )

        val headers = openAiChatHeaders(provider, "")

        assertEquals("opencode/1.1.2/cli", headers["User-Agent"])
        assertEquals("cli", headers["x-opencode-client"])
        assertTrue(headers.containsKey("x-opencode-session"))
        assertTrue(headers["x-opencode-session"]?.isNotBlank() == true)
        assertTrue(headers.containsKey("x-opencode-project"))
        assertTrue(headers["x-opencode-project"]?.isNotBlank() == true)
        assertTrue(headers.containsKey("x-opencode-request"))
        assertTrue(headers["x-opencode-request"]?.isNotBlank() == true)
    }

    @Test
    fun openAiChatHeadersDoesNotInjectOpenCodeHeadersForOtherProviders() {
        val provider = AiProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            protocol = "openai_chat_completions",
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "sk-test",
            authType = AiProviderAuthType.BEARER,
        )

        val headers = openAiChatHeaders(provider, "sk-test")

        assertFalse(headers.containsKey("x-opencode-client"))
        assertFalse(headers.containsKey("x-opencode-session"))
        assertFalse(headers.containsKey("x-opencode-project"))
        assertFalse(headers.containsKey("x-opencode-request"))
        assertFalse(headers.containsKey("User-Agent"))
        assertEquals("Bearer sk-test", headers["Authorization"])
    }
}

