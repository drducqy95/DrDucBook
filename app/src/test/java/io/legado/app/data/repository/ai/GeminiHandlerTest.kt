package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderPresets
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolCall
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiHandlerTest {

    @Test
    fun extractsVisibleTextAndSkipsThoughtParts() {
        val root = GSON.fromJson(
            """{
              "candidates": [{"content": {"parts": [
                {"text": "internal", "thought": true},
                {"text": "Bản "},
                {"text": "dịch"}
              ]}}]
            }""".trimIndent(),
            com.google.gson.JsonObject::class.java,
        )

        assertEquals("Bản dịch", extractGeminiText(root))
    }

    @Test
    fun buildsGeminiNativeSystemInstructionAndConversationRoles() {
        val request = AiGenerateRequest(
            model = AiModelConfig(
                id = "gemini-test",
                provider = AiProviderConfig(
                    id = "gemini",
                    name = "Gemini",
                    protocol = AiProtocol.GEMINI_GENERATE_CONTENT,
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                    apiKey = "secret",
                ),
                displayName = "Gemini",
                modelId = "gemini-3.1-flash-lite",
            ),
            messages = listOf(
                AiMessage(AiMessageRole.SYSTEM, "Translate faithfully"),
                AiMessage(AiMessageRole.USER, "第一章"),
                AiMessage(AiMessageRole.ASSISTANT, "Chương 1"),
            ),
            params = AiGenerationParams(topP = 0.6f, topK = 20),
        )

        val body = buildGeminiRequestBody(request)
        val systemInstruction = body["systemInstruction"] as Map<*, *>
        val contents = body["contents"] as List<*>
        val generationConfig = body["generationConfig"] as Map<*, *>

        assertNotNull(systemInstruction["parts"])
        assertEquals("user", (contents[0] as Map<*, *>)["role"])
        assertEquals("model", (contents[1] as Map<*, *>)["role"])
        assertEquals(0.6f, generationConfig["topP"])
        assertEquals(20, generationConfig["topK"])
    }

    @Test
    fun mapsThinkingControlsByGeminiModelGeneration() {
        assertNull(buildGeminiThinkingConfig("gemini-3.1-flash-lite", AiReasoningLevel.OFF))
        assertNull(buildGeminiThinkingConfig("gemini-3.1-flash-lite", AiReasoningLevel.AUTO))
        assertEquals(
            mapOf("thinkingBudget" to 1_000),
            buildGeminiThinkingConfig("gemini-2.5-flash", AiReasoningLevel.LOW),
        )
        assertEquals(
            mapOf("thinkingLevel" to "low"),
            buildGeminiThinkingConfig("gemini-3.1-flash-lite", AiReasoningLevel.LOW),
        )
    }

    @Test
    fun stripsUnsupportedKeywordsFromGeminiToolSchemas() {
        val request = AiGenerateRequest(
            model = AiModelConfig(
                id = "gemini-test",
                provider = AiProviderConfig(
                    id = "gemini",
                    name = "Gemini",
                    protocol = AiProtocol.GEMINI_GENERATE_CONTENT,
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                    apiKey = "secret",
                ),
                displayName = "Gemini",
                modelId = "gemini-3.1-flash-lite",
            ),
            messages = listOf(AiMessage(AiMessageRole.USER, "Find my book")),
            tools = listOf(
                AiToolDefinition(
                    name = "search_books",
                    description = "Search books",
                    inputSchema = mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "Search keyword",
                                "additionalProperties" to false,
                            )
                        ),
                        "additionalProperties" to false,
                        "unsupportedKeyword" to "must be removed",
                    ),
                )
            ),
        )

        val body = buildGeminiRequestBody(request)
        val tools = body["tools"] as List<*>
        val declarations = ((tools.single() as Map<*, *>)["functionDeclarations"] as List<*>)
        val parameters = (declarations.single() as Map<*, *>)["parameters"] as Map<*, *>
        val query = (parameters["properties"] as Map<*, *>)["query"] as Map<*, *>

        assertEquals("object", parameters["type"])
        assertEquals("string", query["type"])
        assertFalse(parameters.containsKey("additionalProperties"))
        assertFalse(query.containsKey("additionalProperties"))
        assertFalse(parameters.containsKey("unsupportedKeyword"))
    }

    @Test
    fun buildsGeminiToolFollowUpWithoutSyntheticCallIds() {
        val request = AiGenerateRequest(
            model = AiModelConfig(
                id = "gemini-test",
                provider = AiProviderConfig(
                    id = "gemini",
                    name = "Gemini",
                    protocol = AiProtocol.GEMINI_GENERATE_CONTENT,
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                    apiKey = "secret",
                ),
                displayName = "Gemini",
                modelId = "gemini-3.1-flash-lite",
            ),
            messages = listOf(
                AiMessage(AiMessageRole.USER, "List books"),
                AiMessage(
                    role = AiMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(
                        AiToolCall(
                            id = "tool_index_0",
                            name = "search_books",
                            arguments = "{\"limit\":3}",
                            metadata = "opaque-thought-signature",
                        )
                    ),
                ),
                AiMessage(
                    role = AiMessageRole.TOOL,
                    content = "{\"books\":[]}",
                    toolCallId = "tool_index_0",
                    name = "search_books",
                ),
            ),
        )

        val contents = buildGeminiRequestBody(request)["contents"] as List<*>
        val functionCall = (((contents[1] as Map<*, *>)["parts"] as List<*>).single()
            as Map<*, *>)["functionCall"] as Map<*, *>
        val responseParts = (contents[2] as Map<*, *>)["parts"] as List<*>
        val functionResponse = ((responseParts.single()
            as Map<*, *>)["functionResponse"] as Map<*, *>
        )

        assertEquals("search_books", functionCall["name"])
        assertEquals("search_books", functionResponse["name"])
        assertEquals(
            "opaque-thought-signature",
            ((contents[1] as Map<*, *>)["parts"] as List<*>).single()
                .let { it as Map<*, *> }["thoughtSignature"],
        )
        assertEquals(1, responseParts.size)
        assertFalse(functionCall.containsKey("id"))
        assertFalse(functionResponse.containsKey("id"))
    }

    @Test
    fun usesGemini36FlashAsDefaultGeminiModel() {
        val preset = AiProviderPresets.items.single { it.id == "gemini" }

        assertEquals("gemini-3.6-flash", preset.modelId)
        assertEquals("Gemini 3.6 Flash", preset.modelName)
    }
}
