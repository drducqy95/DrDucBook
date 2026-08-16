package io.legado.app.data.repository.ai

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandCodeHandlerTest {

    @Test
    fun buildsCommandCodeEnvelopeInsteadOfOpenAiRootBody() {
        val body = buildCommandCodeRequestBody(
            request = request(),
            threadId = "thread-1",
            workingDir = "/workspace",
            date = "2026-08-11",
            environment = "android",
        )
        val config = body.map("config")
        val params = body.map("params")
        val messages = params.list("messages")

        assertEquals("thread-1", body["threadId"])
        assertEquals("", body["memory"])
        assertFalse(body.containsKey("model"))
        assertEquals("/workspace", config["workingDir"])
        assertEquals("2026-08-11", config["date"])
        assertEquals("android", config["environment"])
        assertEquals(false, config["isGitRepo"])
        assertEquals("deepseek/deepseek-v4-pro", params["model"])
        assertEquals(true, params["stream"])
        assertEquals(64_000, params["max_tokens"])
        assertEquals(0.3f, params["temperature"])
        assertEquals("System A\n\nSystem B", params["system"])
        assertFalse(messages.any { it["role"] == AiMessageRole.SYSTEM })
    }

    @Test
    fun convertsContentAndToolsToCommandCodeBlocks() {
        val params = buildCommandCodeRequestBody(
            request = request(),
            threadId = "thread-1",
            workingDir = "/workspace",
            date = "2026-08-11",
            environment = "android",
        ).map("params")
        val messages = params.list("messages")
        val userContent = messages[0].list("content")
        val assistantContent = messages[1].list("content")
        val toolContent = messages[2].list("content")
        val tools = params.list("tools")

        assertEquals(mapOf("type" to "text", "text" to "Hello"), userContent.single())
        assertEquals("tool-call", assistantContent.last()["type"])
        assertEquals("call-1", assistantContent.last()["toolCallId"])
        assertEquals(mapOf("q" to "x"), assistantContent.last()["input"])
        assertEquals("tool-result", toolContent.single()["type"])
        assertEquals(mapOf("type" to "text", "value" to "Result"), toolContent.single()["output"])
        assertEquals("search", tools.single()["name"])
        assertTrue(tools.single().containsKey("input_schema"))
        assertFalse(tools.single().containsKey("function"))
    }

    @Test
    fun parsesTextAndReasoningDeltas() {
        val state = CommandCodeStreamState()
        assertEquals(
            listOf(AiStreamEvent.Content("Xin chào")),
            parseCommandCodeEvent("{\"type\":\"text-delta\",\"text\":\"Xin chào\"}", state),
        )
        assertEquals(
            listOf(AiStreamEvent.Reasoning("đang nghĩ")),
            parseCommandCodeEvent("data: {\"type\":\"reasoning-delta\",\"text\":\"đang nghĩ\"}", state),
        )
    }

    @Test
    fun keepsToolIndexAcrossInputDeltas() {
        val state = CommandCodeStreamState()
        val start = parseCommandCodeEvent(
            "{\"type\":\"tool-input-start\",\"id\":\"call-1\",\"toolName\":\"search\"}",
            state,
        ).single() as AiStreamEvent.ToolCallDelta
        val delta = parseCommandCodeEvent(
            """{"type":"tool-input-delta","id":"call-1","delta":"{\"q\":\"x\"}"}""",
            state,
        ).single() as AiStreamEvent.ToolCallDelta

        assertEquals(0, start.index)
        assertEquals(0, delta.index)
        assertEquals("search", start.name)
        assertEquals("{\"q\":\"x\"}", delta.argumentsDelta)
    }

    @Test
    fun ignoresLifecycleEventsAndDoneMarker() {
        val state = CommandCodeStreamState()
        assertTrue(parseCommandCodeEvent("{\"type\":\"start\"}", state).isEmpty())
        assertTrue(parseCommandCodeEvent("[DONE]", state).isEmpty())
    }

    private fun request() = AiGenerateRequest(
        model = AiModelConfig(
            id = "model",
            provider = AiProviderConfig(
                id = "commandcode",
                name = "Command Code",
                protocol = AiProtocol.COMMAND_CODE,
                baseUrl = "https://api.commandcode.ai/alpha/generate",
                apiKey = "user_test",
            ),
            displayName = "DeepSeek V4 Pro",
            modelId = "deepseek/deepseek-v4-pro",
        ),
        messages = listOf(
            AiMessage(AiMessageRole.SYSTEM, "System A"),
            AiMessage(AiMessageRole.SYSTEM, "System B"),
            AiMessage(AiMessageRole.USER, "Hello"),
            AiMessage(
                role = AiMessageRole.ASSISTANT,
                content = "Working",
                toolCalls = listOf(AiToolCall("call-1", "search", "{\"q\":\"x\"}")),
            ),
            AiMessage(
                role = AiMessageRole.TOOL,
                content = "Result",
                toolCallId = "call-1",
                name = "search",
            ),
        ),
        params = AiGenerationParams(),
        tools = listOf(
            AiToolDefinition(
                name = "search",
                description = "Search",
                inputSchema = mapOf("type" to "object"),
            )
        ),
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String): Map<String, Any?> =
        get(key) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String): List<Map<String, Any?>> =
        get(key) as List<Map<String, Any?>>
}
