package io.legado.app.data.repository.ai

import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Command Code uses its own request envelope and returns AI SDK v5 NDJSON rather than SSE.
 * The endpoint is force-streaming, so both generate() and generateStream() consume the same
 * parser. Keeping this adapter separate avoids weakening the OpenAI SSE parser for other hosts.
 */
class CommandCodeHandler : AiProtocolHandler {

    override val protocols = setOf(AiProtocol.COMMAND_CODE)

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val output = StringBuilder()
                streamInternal(request) { event ->
                    if (event is AiStreamEvent.Content) output.append(event.text)
                }
                output.toString().takeIf(String::isNotBlank)
                    ?.let(::AiGenerateResponse)
                    ?: error("Command Code returned an empty response")
            }
        }

    override suspend fun stream(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
    ) = streamInternal(request, emitEvent)

    override suspend fun fetchModels(
        provider: AiProviderConfig,
    ): Result<List<AiAvailableModel>> = Result.success(COMMAND_CODE_CURATED_MODELS)

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
    ) {
        val provider = request.model.provider
        val rawUrl = provider.baseUrl.ifBlank { COMMAND_CODE_DEFAULT_URL }.trimEnd('/')
        val targetUrl = if (rawUrl.endsWith("/alpha/generate")) {
            rawUrl
        } else {
            "$rawUrl/alpha/generate"
        }
        require(provider.apiKey.isNotBlank()) {
            "Command Code configuration incomplete: API key is required"
        }
        require(request.model.modelId.isNotBlank()) { "Command Code model is required" }

        val body = buildCommandCodeRequestBody(request)

        val keyRotator = KeyRotator(provider.apiKey)
        val sessionId = UUID.randomUUID().toString()
        val response = retryWithBackoff(
            maxAttempts = keyRotator.attemptsAtLeast(3),
            keyRotator = keyRotator,
        ) {
            aiOkHttpClient.newCallResponse {
                url(targetUrl)
                postJson(GSON.toJson(body))
                addHeaders(commandCodeHeaders(provider, keyRotator.currentKey, sessionId))
            }.also {
                if (!it.isSuccessful) {
                    val detail = runCatching { it.peekBody(128 * 1024L).string() }.getOrNull()
                    val message = detail?.toJsonObject()?.extractApiErrorMessage()
                        ?: detail?.takeIf(String::isNotBlank)
                        ?: it.message
                    it.close()
                    error("HTTP ${it.code}: $message")
                }
            }
        }
        try {
            val state = CommandCodeStreamState()
            val source = response.body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line()?.trim().orEmpty()
                if (line.isBlank() || line == "[DONE]") continue
                for (event in parseCommandCodeEvent(line, state)) {
                    emitEvent(event)
                }
            }
            source.close()
        } finally {
            response.close()
        }
    }
}

internal class CommandCodeStreamState {
    val toolIndexes = LinkedHashMap<String, Int>()
    var nextToolIndex: Int = 0
}

internal fun commandCodeHeaders(
    provider: AiProviderConfig,
    apiKey: String = provider.apiKey,
    sessionId: String = UUID.randomUUID().toString(),
): Map<String, String> = buildMap {
    put("Content-Type", "application/json")
    put("Accept", "text/event-stream")
    put("x-command-code-version", "0.25.7")
    put("x-cli-environment", "cli")
    put("User-Agent", "command-code-cli/0.25.7")
    put("x-session-id", sessionId)
    putAll(provider.headers)
    provider.customHeaders.forEach { (name, value) ->
        put(name, value.replace("{apiKey}", apiKey).replace("${'$'}API_KEY", apiKey))
    }
    if (apiKey.isNotBlank()) put("Authorization", "Bearer $apiKey")
}

/** Builds the envelope accepted by `/alpha/generate` (not an OpenAI root request). */
internal fun buildCommandCodeRequestBody(
    request: AiGenerateRequest,
    threadId: String = UUID.randomUUID().toString(),
    workingDir: String = System.getProperty("user.dir").orEmpty().ifBlank { "/" },
    date: String = LocalDate.now(ZoneOffset.UTC).toString(),
    environment: String = "cli",
): Map<String, Any?> {
    val system = request.messages.asSequence()
        .filter { it.role == AiMessageRole.SYSTEM }
        .map(AiMessage::content)
        .filter(String::isNotBlank)
        .joinToString("\n\n")
    val params = mutableMapOf<String, Any?>(
        "model" to request.model.modelId,
        "messages" to request.messages.toCommandCodeMessages(),
        // The upstream endpoint is streaming-only even for a single response.
        "stream" to true,
        "max_tokens" to (request.params.maxOutputTokens ?: COMMAND_CODE_DEFAULT_MAX_TOKENS),
        "temperature" to (request.params.temperature ?: COMMAND_CODE_DEFAULT_TEMPERATURE),
    )
    system.takeIf(String::isNotBlank)?.let { params["system"] = it }
    request.tools.takeIf(List<AiToolDefinition>::isNotEmpty)?.let {
        params["tools"] = it.toCommandCodeTools()
    }
    request.params.topP?.let { params["top_p"] = it }
    return mapOf(
        "threadId" to threadId,
        "memory" to "",
        "config" to mapOf(
            "workingDir" to workingDir,
            "date" to date,
            "environment" to environment,
            "structure" to emptyList<Any>(),
            "isGitRepo" to false,
            "currentBranch" to "",
            "mainBranch" to "",
            "gitStatus" to "",
            "recentCommits" to emptyList<Any>(),
        ),
        "params" to params,
    )
}

private fun List<AiMessage>.toCommandCodeMessages(): List<Map<String, Any?>> = mapNotNull { message ->
    when (message.role) {
        AiMessageRole.SYSTEM -> null
        AiMessageRole.TOOL -> mapOf(
            "role" to AiMessageRole.TOOL,
            "content" to listOf(
                mapOf(
                    "type" to "tool-result",
                    "toolCallId" to message.toolCallId.orEmpty(),
                    "toolName" to message.name.orEmpty(),
                    "output" to mapOf("type" to "text", "value" to message.content),
                )
            ),
        )
        AiMessageRole.ASSISTANT -> mapOf(
            "role" to AiMessageRole.ASSISTANT,
            "content" to buildList {
                message.content.takeIf(String::isNotBlank)?.let {
                    add(mapOf("type" to "text", "text" to it))
                }
                message.toolCalls.forEach { call ->
                    add(
                        mapOf(
                            "type" to "tool-call",
                            "toolCallId" to call.id,
                            "toolName" to call.name,
                            "input" to parseCommandCodeToolInput(call.arguments),
                        )
                    )
                }
                if (isEmpty()) add(mapOf("type" to "text", "text" to ""))
            },
        )
        else -> mapOf(
            "role" to AiMessageRole.USER,
            "content" to listOf(mapOf("type" to "text", "text" to message.content)),
        )
    }
}

private fun parseCommandCodeToolInput(arguments: String): Any = runCatching {
    GSON.fromJson(arguments, Any::class.java)
}.getOrNull() ?: emptyMap<String, Any?>()

private fun List<AiToolDefinition>.toCommandCodeTools(): List<Map<String, Any?>> = map { tool ->
    mapOf(
        "name" to tool.name,
        "description" to tool.description,
        "input_schema" to tool.inputSchema,
    )
}

private const val COMMAND_CODE_DEFAULT_MAX_TOKENS = 64_000
private const val COMMAND_CODE_DEFAULT_TEMPERATURE = 0.3f
internal const val COMMAND_CODE_DEFAULT_URL = "https://api.commandcode.ai/alpha/generate"

internal val COMMAND_CODE_CURATED_MODELS = listOf(
    AiAvailableModel(
        id = "deepseek/deepseek-v4-pro",
        name = "DeepSeek V4 Pro",
        contextWindow = 128_000,
        maxOutputTokens = 16_384,
    ),
    AiAvailableModel(
        id = "deepseek/deepseek-chat",
        name = "DeepSeek Chat",
        contextWindow = 64_000,
        maxOutputTokens = 8_192,
    ),
    AiAvailableModel(
        id = "deepseek/deepseek-reasoner",
        name = "DeepSeek Reasoner",
        contextWindow = 64_000,
        maxOutputTokens = 8_192,
    ),
    AiAvailableModel(
        id = "anthropic/claude-3-7-sonnet",
        name = "Claude 3.7 Sonnet",
        contextWindow = 200_000,
        maxOutputTokens = 16_384,
    ),
    AiAvailableModel(
        id = "anthropic/claude-3-5-sonnet",
        name = "Claude 3.5 Sonnet",
        contextWindow = 200_000,
        maxOutputTokens = 8_192,
    ),
    AiAvailableModel(
        id = "openai/gpt-4o",
        name = "GPT-4o",
        contextWindow = 128_000,
        maxOutputTokens = 4_096,
    ),
    AiAvailableModel(
        id = "google/gemini-2.0-flash",
        name = "Gemini 2.0 Flash",
        contextWindow = 1_000_000,
        maxOutputTokens = 8_192,
    ),
)

/** Converts one AI SDK v5 NDJSON event into the app's provider-neutral stream events. */
internal fun parseCommandCodeEvent(
    raw: String,
    state: CommandCodeStreamState = CommandCodeStreamState(),
): List<AiStreamEvent> {
    val json = raw.removePrefix("data:").trim()
    if (json.isBlank() || json == "[DONE]") return emptyList()
    val root = json.toJsonObject() ?: return emptyList()
    val type = root.getString("type") ?: return emptyList()
    return when (type) {
        "text-delta" -> root.stringAny("text", "delta")
            ?.takeIf(String::isNotEmpty)
            ?.let { listOf(AiStreamEvent.Content(it)) }
            .orEmpty()
        "reasoning-delta" -> root.getString("text")
            ?.takeIf(String::isNotEmpty)
            ?.let { listOf(AiStreamEvent.Reasoning(it)) }
            .orEmpty()
        "tool-input-start" -> {
            val id = root.getString("id") ?: root.getString("toolCallId") ?: "tool-${state.nextToolIndex}"
            val index = state.toolIndexes.getOrPut(id) { state.nextToolIndex++ }
            listOf(
                AiStreamEvent.ToolCallDelta(
                    id = id,
                    index = index,
                    name = root.getString("toolName"),
                    argumentsDelta = "",
                    rawType = type,
                )
            )
        }
        "tool-input-delta" -> {
            val id = root.getString("id") ?: root.getString("toolCallId") ?: return emptyList()
            val index = state.toolIndexes[id] ?: return emptyList()
            listOf(
                AiStreamEvent.ToolCallDelta(
                    id = id,
                    index = index,
                    name = null,
                    argumentsDelta = root.getString("delta") ?: root.getString("inputTextDelta"),
                    rawType = type,
                )
            )
        }
        "tool-call" -> {
            val id = root.getString("toolCallId") ?: "tool-${state.nextToolIndex}"
            val index = state.toolIndexes.getOrPut(id) { state.nextToolIndex++ }
            listOf(
                AiStreamEvent.ToolCallDelta(
                    id = id,
                    index = index,
                    name = root.getString("toolName"),
                    argumentsDelta = root.get("input")?.let(GSON::toJson),
                    rawType = type,
                )
            )
        }
        "error" -> error(root.getString("message") ?: root.get("error")?.toString() ?: "Command Code error")
        "finish", "usage" -> emptyList()
        else -> emptyList()
    }
}

private fun JsonObject.stringAny(vararg names: String): String? =
    names.asSequence().mapNotNull(::getString).firstOrNull()
