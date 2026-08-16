package io.legado.app.data.repository.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.data.repository.CodexOAuthMetadata
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.await
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAiResponsesHandler : AiProtocolHandler {

    override val protocols = setOf(
        AiProtocol.OPENAI_RESPONSES,
        AiProtocol.CODEX_SUBSCRIPTION,
        AiProtocol.GROK_CLI_SUBSCRIPTION,
    )

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        withContext(Dispatchers.IO) {
            runCatching { generateInternal(request) }
        }

    override suspend fun stream(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        streamInternal(request, emitEvent)
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (provider.protocol == AiProtocol.CODEX_SUBSCRIPTION) emptyList()
                else fetchOpenAiCompatibleModels(provider)
            }
        }

    private suspend fun generateInternal(request: AiGenerateRequest): AiGenerateResponse {
        val provider = request.model.provider
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank() && request.model.modelId.isNotBlank()) {
            "OpenAI Responses configuration incomplete: baseUrl, apiKey, and model are required"
        }
        if (
            provider.protocol == AiProtocol.CODEX_SUBSCRIPTION ||
            provider.protocol == AiProtocol.GROK_CLI_SUBSCRIPTION
        ) {
            val output = StringBuilder()
            streamInternal(request) { event ->
                if (event is AiStreamEvent.Content) output.append(event.text)
            }
            return output.toString().takeIf(String::isNotBlank)
                ?.let(::AiGenerateResponse)
                ?: error("Empty Codex response")
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val body = request.toOpenAiResponsesBody(stream = false)

        return retryWithBackoff(maxAttempts = keyRotator.attemptsAtLeast(3), keyRotator = keyRotator) {
            val response = aiOkHttpClient.newCallStrResponse {
                url(provider.responsesEndpointUrl())
                postJson(GSON.toJson(body))
                addHeaders(openAiResponsesHeaders(provider, keyRotator.currentKey, request.routeSessionKey))
            }
            val responseBody = response.body.orEmpty()
            if (!response.isSuccessful()) {
                throw Exception("HTTP ${response.code()}: ${responseBody.errorMessage(response.message())}")
            }
            val root = GSON.fromJson(responseBody, JsonObject::class.java)
            val text = root?.getString("output_text") ?: root.extractResponsesOutputText()
            if (text.isNullOrBlank()) {
                throw Exception("Empty AI response")
            } else {
                AiGenerateResponse(text = text, rawBody = responseBody)
            }
        }
    }

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        val provider = request.model.provider
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank() && request.model.modelId.isNotBlank()) {
            "OpenAI Responses configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val body = request.toOpenAiResponsesBody(stream = true)

        val response = retryWithBackoff(maxAttempts = keyRotator.attemptsAtLeast(3), keyRotator = keyRotator) {
            aiOkHttpClient.newCallResponse {
                url(provider.responsesEndpointUrl())
                postJson(GSON.toJson(body))
                addHeaders(openAiResponsesHeaders(provider, keyRotator.currentKey, request.routeSessionKey))
            }.also {
                if (!it.isSuccessful) {
                    val detail = runCatching { it.body.string() }.getOrNull()
                    throw Exception("HTTP ${it.code}: ${detail.errorMessage(it.message)}")
                }
            }
        }
        try {
            response.readSseData { data ->
                val root = data.toJsonObject() ?: run {
                    data.extractCodexStreamErrorMessage()?.let { throw Exception(it) }
                    throw Exception("Invalid OpenAI Responses stream chunk")
                }
                root.extractApiErrorMessage()?.let { throw Exception(it) }
                if (root.getString("type") == "error") {
                    throw Exception(root.findNestedProviderMessage() ?: "OpenAI response failed")
                }
                when (root.getString("type")) {
                    "response.output_text.delta",
                    "response.refusal.delta" -> {
                        root.getString("delta")?.takeIf { it.isNotEmpty() }?.let {
                            emitEvent(AiStreamEvent.Content(it))
                        }
                    }
                    "response.reasoning_summary_text.delta",
                    "response.reasoning_text.delta" -> {
                        root.getString("delta")?.takeIf { it.isNotEmpty() }?.let {
                            emitEvent(AiStreamEvent.Reasoning(it))
                        }
                    }
                    "response.output_item.added",
                    "response.output_item.done" -> {
                        root.get("item")?.asJsonObjectOrNull()?.let { item ->
                            if (item.getString("type")?.contains("call") == true) {
                                emitEvent(
                                    AiStreamEvent.ToolCallDelta(
                                        id = item.getString("call_id") ?: item.getString("id"),
                                        index = root.getString("output_index")?.toIntOrNull(),
                                        name = item.getString("name") ?: item.getString("server_label"),
                                        argumentsDelta = item.getString("arguments"),
                                        rawType = item.getString("type") ?: root.getString("type").orEmpty()
                                    )
                                )
                            }
                        }
                    }
                    "response.function_call_arguments.delta",
                    "response.mcp_call_arguments.delta",
                    "response.code_interpreter_call_code.delta",
                    "response.custom_tool_call_input.delta" -> {
                        emitEvent(
                            AiStreamEvent.ToolCallDelta(
                                id = root.getString("call_id"),
                                index = root.getString("output_index")?.toIntOrNull(),
                                name = null,
                                argumentsDelta = root.getString("delta"),
                                rawType = root.getString("type").orEmpty()
                            )
                        )
                    }
                    "response.function_call_arguments.done",
                    "response.mcp_call_arguments.done",
                    "response.code_interpreter_call_code.done",
                    "response.custom_tool_call_input.done",
                    "response.file_search_call.in_progress",
                    "response.file_search_call.searching",
                    "response.file_search_call.completed",
                    "response.web_search_call.in_progress",
                    "response.web_search_call.searching",
                    "response.web_search_call.completed",
                    "response.mcp_call.in_progress",
                    "response.mcp_call.completed",
                    "response.mcp_call.failed",
                    "response.mcp_list_tools.in_progress",
                    "response.mcp_list_tools.completed",
                    "response.mcp_list_tools.failed",
                    "response.code_interpreter_call.in_progress",
                    "response.code_interpreter_call.interpreting",
                    "response.code_interpreter_call.completed" -> {
                        emitEvent(
                            AiStreamEvent.ToolCallDelta(
                                id = root.getString("call_id"),
                                index = root.getString("output_index")?.toIntOrNull(),
                                name = null,
                                argumentsDelta = root.getString("arguments")
                                    ?: root.getString("code")
                                    ?: root.getString("input"),
                                rawType = root.getString("type").orEmpty()
                            )
                        )
                    }
                    "response.failed" -> {
                        throw Exception(root.extractResponseFailureMessage() ?: "OpenAI response failed")
                    }
                    "response.incomplete" -> {
                        throw Exception(root.extractResponseIncompleteMessage() ?: "OpenAI response incomplete")
                    }
                }
            }
        } finally {
            response.close()
        }
    }

    private suspend fun fetchOpenAiCompatibleModels(provider: AiProviderConfig): List<AiAvailableModel> {
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank()) {
            "AI provider configuration incomplete: baseUrl and apiKey are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val modelsUrl = provider.modelsPath?.let { provider.baseUrl + it }
            ?: provider.modelsUrl
            ?: (provider.baseUrl + "/models")
        return retryWithBackoff(maxAttempts = keyRotator.attemptsAtLeast(2), keyRotator = keyRotator) {
            val response = okHttpClient.newCallStrResponse {
                url(modelsUrl)
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "Authorization" to "Bearer ${keyRotator.currentKey}",
                        "Content-Type" to "application/json"
                    )
                )
            }
            if (!response.isSuccessful()) {
                throw Exception("HTTP ${response.code()}: ${response.message()}")
            }
            val json = GSON.fromJson(response.body, OpenAiModelsResponse::class.java)
            json?.data.toAvailableModels()
        }
    }
}

internal fun AiGenerateRequest.toOpenAiResponsesBody(stream: Boolean): MutableMap<String, Any?> {
    val provider = model.provider
    val isCodex = provider.protocol == AiProtocol.CODEX_SUBSCRIPTION
    val requestParams = params
    val codexInstructions = messages.firstOrNull { it.role == AiMessageRole.SYSTEM }
        ?.content
        ?.takeIf(String::isNotBlank)
    val inputMessages = if (isCodex) {
        messages.filterNot { it.role == AiMessageRole.SYSTEM }
    } else {
        messages
    }
    return mutableMapOf<String, Any?>(
        "model" to if (isCodex) model.modelId.codexUpstreamModelId().first else model.modelId,
        "input" to inputMessages.toOpenAiResponsesInput(codexMode = isCodex).ifEmpty {
            listOf(codexPlaceholderInput())
        },
    ).apply {
        if (stream || isCodex) put("stream", true)
        if (isCodex) {
            put("store", false)
            val (_, modelEffort) = model.modelId.codexUpstreamModelId()
            requestParams.toCodexReasoning(modelEffort).let { reasoning ->
                put("reasoning", reasoning)
                if (reasoning["effort"] != "none") {
                    put("include", listOf("reasoning.encrypted_content"))
                }
            }
            codexPromptCacheKey()?.let { put("prompt_cache_key", it) }
            put("instructions", codexInstructions ?: CODEX_DEFAULT_INSTRUCTIONS)
        }
        tools.takeIf { it.isNotEmpty() }?.let { put("tools", it.toOpenAiResponsesTools()) }
        if (!isCodex) {
            requestParams.temperature?.let { put("temperature", it) }
            requestParams.maxOutputTokens?.let { put("max_output_tokens", it) }
            requestParams.topP?.let { put("top_p", it) }
        }
        if (!isCodex && hasReasoningCapability(model.capabilities)) {
            put(
                "reasoning",
                buildMap<String, Any> {
                    put("summary", "auto")
                    if (requestParams.reasoningLevel != AiReasoningLevel.AUTO) {
                        put("effort", requestParams.reasoningLevel.effort)
                    }
                }
            )
        }
    }
}

private fun AiGenerateRequest.codexPromptCacheKey(): String? {
    return routeSessionKey?.takeIf(String::isNotBlank)
        ?: model.provider.runtimeMetadata["sessionId"]?.takeIf(String::isNotBlank)
        ?: model.provider.runtimeMetadata["workspaceId"]?.takeIf(String::isNotBlank)
        ?: model.provider.runtimeMetadata["chatgptAccountId"]?.takeIf(String::isNotBlank)
        ?: model.provider.runtimeMetadata["accountId"]?.takeIf(String::isNotBlank)
}

private fun AiGenerationParams.toCodexReasoning(modelEffort: String?): Map<String, Any> {
    val explicit = when (reasoningLevel) {
        AiReasoningLevel.OFF -> "none"
        AiReasoningLevel.AUTO -> null
        else -> reasoningLevel.effort
    }
    return mapOf(
        "effort" to normalizeCodexReasoningEffort(explicit ?: modelEffort ?: "low"),
        "summary" to "auto",
    )
}

private fun normalizeCodexReasoningEffort(value: String): String =
    if (value == "max") "xhigh" else value

private fun String.codexUpstreamModelId(): Pair<String, String?> {
    val original = trim()
    var modelId = original.stripCodexModelPrefix()
    var effort: String? = null
    var changed: Boolean
    do {
        changed = false
        if (modelId.endsWith("-review")) {
            modelId = modelId.removeSuffix("-review")
            changed = true
        }
        CODEX_REASONING_SUFFIXES.firstOrNull { modelId.endsWith("-$it") }?.let { suffix ->
            modelId = modelId.removeSuffix("-$suffix")
            effort = normalizeCodexReasoningEffort(suffix)
            changed = true
        }
    } while (changed)
    return (modelId.takeIf(String::isNotBlank) ?: original) to effort
}

private fun String.stripCodexModelPrefix(): String {
    val prefix = substringBefore('/', missingDelimiterValue = "")
    return if (prefix in CODEX_MODEL_PREFIXES) substringAfter('/') else this
}

private fun codexPlaceholderInput(): Map<String, Any?> = mapOf(
    "type" to "message",
    "role" to AiMessageRole.USER,
    "content" to listOf(
        mapOf(
            "type" to "input_text",
            "text" to "...",
        )
    ),
)

private const val CODEX_DEFAULT_INSTRUCTIONS = "You are a helpful AI assistant."

private val CODEX_REASONING_SUFFIXES = listOf(
    "none",
    "minimal",
    "low",
    "medium",
    "high",
    "xhigh",
    "max",
)

private val CODEX_MODEL_PREFIXES = setOf("cx", "codex")

private val CODEX_SSE_ACCOUNT_FALLBACK_PATTERNS = listOf(
    "selected model is at capacity",
    "model_at_capacity",
)

private val CODEX_SSE_RETRY_PATTERNS = listOf(
    "server_is_overloaded",
    "service_unavailable_error",
)

internal fun AiProviderConfig.responsesEndpointUrl(): String {
    val base = baseUrl.trimEnd('/')
    val path = responsesPath
        .takeIf(String::isNotBlank)
        ?.let { if (it.startsWith('/')) it else "/$it" }
        ?: "/responses"
    return if (base.endsWith(path)) base else base + path
}

internal fun openAiResponsesHeaders(
    provider: AiProviderConfig,
    accessToken: String,
    routeSessionKey: String? = null,
): Map<String, String> = buildMap {
    putAll(provider.headers)
    provider.customHeaders.forEach { (name, value) ->
        put(name, value.replace("{apiKey}", accessToken).replace("${'$'}API_KEY", accessToken))
    }
    put("Authorization", "Bearer $accessToken")
    put("Content-Type", "application/json")
    if (provider.protocol == AiProtocol.CODEX_SUBSCRIPTION) {
        put("originator", "codex_cli_rs")
        put("User-Agent", "codex_cli_rs/0.136.0")
        put("Accept", "text/event-stream")
        val accountId = provider.runtimeMetadata["workspaceId"]?.takeIf(String::isNotBlank)
            ?: provider.runtimeMetadata["chatgptAccountId"]?.takeIf(String::isNotBlank)
            ?: CodexOAuthMetadata.extractAccountId(accessToken)
            ?: provider.runtimeMetadata["accountId"]?.takeIf(::isUsableCodexAccountId)
        accountId?.let {
            put("ChatGPT-Account-ID", it)
        }
        put(
            "session_id",
            routeSessionKey?.takeIf(String::isNotBlank)
                ?: provider.runtimeMetadata["sessionId"]?.takeIf(String::isNotBlank)
                ?: accountId
                ?: "default",
        )
    }
}

private fun isUsableCodexAccountId(value: String): Boolean =
    value.isNotBlank() && '@' !in value

// ---- Message & tool format converters ----

internal fun List<AiMessage>.toOpenAiResponsesInput(
    codexMode: Boolean = false,
): List<Map<String, Any?>> {
    if (codexMode) return toCodexResponsesInput()
    return flatMap { message ->
        when {
            message.role == AiMessageRole.TOOL -> listOf(
                mapOf(
                    "type" to "function_call_output",
                    "call_id" to message.toolCallId,
                    "output" to message.content
                )
            )
            message.toolCalls.isNotEmpty() -> {
                val textMessage = message.content.takeIf { it.isNotBlank() }?.let {
                    mapOf("role" to "assistant", "content" to it)
                }
                val toolCalls = message.toolCalls.map {
                    mapOf(
                        "type" to "function_call",
                        "call_id" to it.id,
                        "name" to it.name,
                        "arguments" to it.arguments
                    )
                }
                listOfNotNull(textMessage) + toolCalls
            }
            else -> listOf(
                mapOf(
                    "role" to if (codexMode && message.role == AiMessageRole.SYSTEM) {
                        "developer"
                    } else {
                        message.role
                    },
                    "content" to message.content,
                )
            )
        }
    }
}

private fun List<AiMessage>.toCodexResponsesInput(): List<Map<String, Any?>> = flatMap { message ->
    when {
        message.role == AiMessageRole.SYSTEM -> emptyList()
        message.role == AiMessageRole.TOOL -> listOf(
            mapOf(
                "type" to "function_call_output",
                "call_id" to message.toolCallId?.take(CODEX_MAX_CALL_ID_LENGTH),
                "output" to message.content,
            )
        )
        message.toolCalls.isNotEmpty() -> {
            val textMessage = message.content.takeIf(String::isNotBlank)?.let {
                codexMessageItem(AiMessageRole.ASSISTANT, "output_text", it)
            }
            val toolCalls = message.toolCalls.map { call ->
                mapOf(
                    "type" to "function_call",
                    "call_id" to call.id.take(CODEX_MAX_CALL_ID_LENGTH),
                    "name" to call.name,
                    "arguments" to call.arguments,
                )
            }
            listOfNotNull(textMessage) + toolCalls
        }
        else -> listOf(
            codexMessageItem(
                role = message.role,
                contentType = if (message.role == AiMessageRole.ASSISTANT) {
                    "output_text"
                } else {
                    "input_text"
                },
                text = message.content,
            )
        )
    }
}

private fun codexMessageItem(
    role: String,
    contentType: String,
    text: String,
): Map<String, Any?> = mapOf(
    "type" to "message",
    "role" to role,
    "content" to listOf(mapOf("type" to contentType, "text" to text)),
)

internal fun List<AiToolDefinition>.toOpenAiResponsesTools(): List<Map<String, Any?>> {
    return map {
        mapOf(
            "type" to "function",
            "name" to it.name,
            "description" to it.description,
            "parameters" to it.inputSchema.normalizedResponsesToolSchema()
        )
    }
}

private fun Map<String, Any?>.normalizedResponsesToolSchema(): Map<String, Any?> =
    if (this["type"] == "object" && !containsKey("properties")) {
        this + ("properties" to emptyMap<String, Any?>())
    } else {
        this
    }

private const val CODEX_MAX_CALL_ID_LENGTH = 64

private fun JsonObject.extractResponseFailureMessage(): String? {
    val response = get("response")?.asJsonObjectOrNull() ?: return null
    val error = response.get("error")?.asJsonObjectOrNull()
    return error?.getString("message")
        ?: response.findNestedProviderMessage()
        ?: response.getString("status")
}

private fun JsonObject.extractResponseIncompleteMessage(): String? {
    val response = get("response")?.asJsonObjectOrNull() ?: return null
    val reason = response.get("incomplete_details")
        ?.asJsonObjectOrNull()
        ?.getString("reason")
    return reason?.let { "OpenAI response incomplete: $it" } ?: response.getString("status")
}

private fun JsonObject.extractResponsesOutputText(): String? {
    return get("output")
        ?.asJsonArrayOrNull()
        ?.flatMap { output ->
            output.asJsonObjectOrNull()
                ?.get("content")
                ?.asJsonArrayOrNull()
                ?.mapNotNull { content ->
                    content.asJsonObjectOrNull()
                        ?.takeIf { it.getString("type") == "output_text" }
                        ?.getString("text")
                }
                .orEmpty()
        }
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
}

private fun String?.errorMessage(fallback: String): String {
    val body = orEmpty()
    return body.toJsonObject()?.extractApiErrorMessage()
        ?: body.extractCodexStreamErrorMessage()
        ?: body.takeIf(String::isNotBlank)?.take(500)
        ?: fallback
}

internal fun String.extractCodexStreamErrorMessage(): String? {
    val lower = lowercase()
    CODEX_SSE_ACCOUNT_FALLBACK_PATTERNS.firstOrNull(lower::contains)?.let {
        return findCodexCapacityMessage() ?: CODEX_MODEL_CAPACITY_MESSAGE
    }
    CODEX_SSE_RETRY_PATTERNS.firstOrNull(lower::contains)?.let { return it }
    return null
}

private fun String.findCodexCapacityMessage(): String? =
    Regex(
        "Selected model is at capacity\\. Please try a different model\\.",
        RegexOption.IGNORE_CASE,
    ).find(this)?.value

private const val CODEX_MODEL_CAPACITY_MESSAGE =
    "Selected model is at capacity. Please try a different model."

private fun JsonElement.findNestedProviderMessage(depth: Int = 0): String? {
    if (depth > 6) return null
    if (isJsonObject) {
        val obj = asJsonObject
        obj.getString("message")?.takeIf(String::isNotBlank)?.let { return it }
        obj.get("error")?.findNestedProviderMessage(depth + 1)?.let { return it }
        obj.get("response")?.findNestedProviderMessage(depth + 1)?.let { return it }
        obj.entrySet().forEach { (_, value) ->
            value.findNestedProviderMessage(depth + 1)?.let { return it }
        }
    } else if (isJsonArray) {
        asJsonArray.forEach { child ->
            child.findNestedProviderMessage(depth + 1)?.let { return it }
        }
    }
    return null
}

private fun List<OpenAiModelItem>?.toAvailableModels(): List<AiAvailableModel> {
    return orEmpty()
        .mapNotNull { item ->
            val id = item.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiAvailableModel(
                id = id,
                name = item.display_name?.takeIf { it.isNotBlank() }
                    ?: item.displayName?.takeIf { it.isNotBlank() }
                    ?: item.name?.takeIf { it.isNotBlank() }
                    ?: id,
                contextWindow = item.context_window ?: item.contextWindow ?: 0,
                maxOutputTokens = item.max_tokens
                    ?: item.maxTokens
                    ?: item.max_output_tokens
                    ?: item.maxOutputTokens
                    ?: 0
            )
        }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }
}
