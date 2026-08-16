package io.legado.app.data.repository.ai

import androidx.annotation.Keep
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
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
import okhttp3.Response

class OpenAiChatHandler : AiProtocolHandler {

    override val protocols = setOf(AiProtocol.OPENAI_CHAT_COMPLETIONS)

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
            runCatching { fetchModelsInternal(provider) }
        }

    private suspend fun generateInternal(request: AiGenerateRequest): AiGenerateResponse {
        val provider = request.model.provider
        require(provider.baseUrl.isNotBlank() && provider.hasRequiredCredential() && request.model.modelId.isNotBlank()) {
            "OpenAI-compatible configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "messages" to request.messages.toOpenAiChatMessages()
        )
        request.tools.takeIf { it.isNotEmpty() }?.let { body["tools"] = it.toOpenAiChatTools() }
        params.temperature?.let { body["temperature"] = it }
        params.maxOutputTokens?.let { body["max_tokens"] = it }
        params.topP?.let { body["top_p"] = it }
        if (hasReasoningCapability(request.model.capabilities) && params.reasoningLevel != AiReasoningLevel.AUTO) {
            body["reasoning_effort"] = params.reasoningLevel.toOpenAiEffort()
        }

        return retryWithBackoff(maxAttempts = keyRotator.attemptsAtLeast(3), keyRotator = keyRotator) {
            val response = aiOkHttpClient.newCallStrResponse {
                url(provider.baseUrl + provider.chatPath)
                postJson(GSON.toJson(body))
                addHeaders(openAiChatHeaders(provider, keyRotator.currentKey))
            }
            val responseBody = response.body.orEmpty()
            if (!response.isSuccessful()) {
                val detail = responseBody.toJsonObject()?.extractApiErrorMessage()
                throw Exception(
                    "HTTP ${response.code()}: ${detail ?: response.message()}"
                )
            }
            val text = extractOpenAiChatResponseText(responseBody)
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
        require(provider.baseUrl.isNotBlank() && provider.hasRequiredCredential() && request.model.modelId.isNotBlank()) {
            "OpenAI-compatible configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "messages" to request.messages.toOpenAiChatMessages(),
            "stream" to true
        )
        request.tools.takeIf { it.isNotEmpty() }?.let { body["tools"] = it.toOpenAiChatTools() }
        params.temperature?.let { body["temperature"] = it }
        params.maxOutputTokens?.let { body["max_tokens"] = it }
        params.topP?.let { body["top_p"] = it }
        if (hasReasoningCapability(request.model.capabilities) && params.reasoningLevel != AiReasoningLevel.AUTO) {
            body["reasoning_effort"] = params.reasoningLevel.toOpenAiEffort()
        }

        // For streaming, we retry before establishing the SSE connection.
        // Once streaming starts, errors are not retried (partial output would be confusing).
        val response = retryWithBackoff(maxAttempts = keyRotator.attemptsAtLeast(3), keyRotator = keyRotator) {
            aiOkHttpClient.newCallResponse {
                url(provider.baseUrl + provider.chatPath)
                postJson(GSON.toJson(body))
                addHeaders(openAiChatHeaders(provider, keyRotator.currentKey))
            }.also {
                if (!it.isSuccessful) {
                    throw Exception("HTTP ${it.code}: ${it.message}")
                }
            }
        }
        var contentSeen = false
        var reasoningSeen = false
        var finishReason: String? = null
        try {
            response.readSseData { data ->
                val root = data.toJsonObject()
                root?.extractApiErrorMessage()?.let { throw Exception(it) }
                val streamText = extractOpenAiChatStreamText(data)
                val chunk = runCatching {
                    GSON.fromJson(data, OpenAiChatStreamChunk::class.java)
                }.getOrElse {
                    throw Exception("Invalid OpenAI chat stream chunk", it)
                }
                chunk?.choices?.firstOrNull()?.finish_reason
                    ?.takeIf(String::isNotBlank)
                    ?.let { finishReason = it }
                streamText.reasoning?.let {
                    reasoningSeen = true
                    emitEvent(AiStreamEvent.Reasoning(it))
                }
                streamText.content?.let {
                    contentSeen = true
                    emitEvent(AiStreamEvent.Content(it))
                }
                val delta = chunk?.choices?.firstOrNull()?.delta
                delta?.tool_calls?.forEach { toolCall ->
                    emitEvent(
                        AiStreamEvent.ToolCallDelta(
                            id = toolCall.id,
                            index = toolCall.index,
                            name = toolCall.function?.name,
                            argumentsDelta = toolCall.function?.arguments,
                            rawType = toolCall.type ?: "tool_call"
                        )
                    )
                }
            }
            if (request.taskType == AiTaskType.TRANSLATE_CHAPTER &&
                !contentSeen && reasoningSeen
            ) {
                val detail = if (finishReason.equals("length", ignoreCase = true)) {
                    "reasoning consumed the output budget (finish_reason=length)"
                } else {
                    "provider returned reasoning without final content" +
                        finishReason?.let { " (finish_reason=$it)" }.orEmpty()
                }
                throw Exception("Empty AI response: $detail")
            }
        } finally {
            response.close()
        }
    }

    private suspend fun fetchModelsInternal(provider: AiProviderConfig): List<AiAvailableModel> {
        require(provider.baseUrl.isNotBlank() && provider.hasRequiredCredential()) {
            "AI provider configuration incomplete: baseUrl and apiKey are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val modelsUrl = provider.modelsPath?.let { provider.baseUrl + it }
            ?: provider.modelsUrl
            ?: (provider.baseUrl + "/models")
        return retryWithBackoff(maxAttempts = keyRotator.attemptsAtLeast(2), keyRotator = keyRotator) {
            val response = okHttpClient.newCallStrResponse {
                url(modelsUrl)
                addHeaders(openAiChatHeaders(provider, keyRotator.currentKey))
            }
            if (!response.isSuccessful()) {
                throw Exception("HTTP ${response.code()}: ${response.message()}")
            }
            val json = GSON.fromJson(response.body, OpenAiModelsResponse::class.java)
            json?.data.toAvailableModels()
        }
    }
}

internal fun AiProviderConfig.hasRequiredCredential(): Boolean =
    authType == AiProviderAuthType.NONE || apiKey.isNotBlank()

internal fun openAiChatHeaders(
    provider: AiProviderConfig,
    apiKey: String,
): Map<String, String> = buildMap {
    putAll(provider.headers)
    provider.customHeaders.forEach { (name, value) ->
        put(name, value.replace("{apiKey}", apiKey).replace("${'$'}API_KEY", apiKey))
    }
    put("Content-Type", "application/json")
    if (provider.authType == AiProviderAuthType.BEARER) {
        put("Authorization", "Bearer $apiKey")
    }
}

// ---- Message & tool format converters ----

internal fun List<AiMessage>.toOpenAiChatMessages(): List<Map<String, Any?>> {
    return mapNotNull { message ->
        when {
            message.role == AiMessageRole.TOOL -> mapOf(
                "role" to "tool",
                "tool_call_id" to message.toolCallId,
                "content" to message.content
            )
            message.toolCalls.isNotEmpty() -> {
                buildMap {
                    put("role", "assistant")
                    put("content", message.content.takeIf { it.isNotBlank() })
                    put(
                        "tool_calls",
                        message.toolCalls.map {
                            mapOf(
                                "id" to it.id,
                                "type" to "function",
                                "function" to mapOf(
                                    "name" to it.name,
                                    "arguments" to it.arguments
                                )
                            )
                        }
                    )
                }
            }
            else -> mapOf("role" to message.role, "content" to message.content)
        }
    }
}

internal fun List<AiToolDefinition>.toOpenAiChatTools(): List<Map<String, Any?>> {
    return map {
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to it.name,
                "description" to it.description,
                "parameters" to it.inputSchema
            )
        )
    }
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

// ---- Data classes ----

@Keep
internal data class OpenAiModelsResponse(
    val data: List<OpenAiModelItem>?
)

@Keep
internal data class OpenAiModelItem(
    val id: String?,
    val name: String?,
    val display_name: String?,
    val displayName: String?,
    val context_window: Int?,
    val contextWindow: Int?,
    val max_tokens: Int?,
    val maxTokens: Int?,
    val max_output_tokens: Int?,
    val maxOutputTokens: Int?
)

@Keep
internal data class OpenAiChatStreamChunk(
    val choices: List<OpenAiChatStreamChoice>?
)

@Keep
internal data class OpenAiChatStreamChoice(
    val delta: OpenAiChatStreamDelta?,
    val finish_reason: String?,
)

@Keep
internal data class OpenAiChatStreamDelta(
    val content: JsonElement?,
    val reasoning_content: JsonElement?,
    val reasoning: JsonElement?,
    val tool_calls: List<OpenAiChatToolCall>?
)

@Keep
internal data class OpenAiChatToolCall(
    val index: Int?,
    val id: String?,
    val type: String?,
    val function: OpenAiChatToolCallFunction?
)

@Keep
internal data class OpenAiChatToolCallFunction(
    val name: String?,
    val arguments: String?
)

/**
 * OpenAI-compatible providers are inconsistent here: `message.content` can be a string, an array
 * of typed text parts, or an object. Streaming chat already handled text deltas, while the
 * non-streaming translation path previously accepted only a string and therefore reported
 * "translation failed" against otherwise working chat providers.
 */
internal fun extractOpenAiChatResponseText(body: String): String? {
    val root = body.toJsonObject() ?: return null
    root.extractApiErrorMessage()?.let { throw IllegalStateException(it) }
    val choice = root.get("choices")
        ?.asJsonArrayOrNull()
        ?.firstOrNull()
        ?.asJsonObjectOrNull()
    val content = choice
        ?.get("message")
        ?.asJsonObjectOrNull()
        ?.get("content")
        ?.textContent()
        ?: choice?.get("text")?.textContent()
        ?: root.get("output_text")?.textContent()
    return content?.takeIf(String::isNotBlank)
}

internal data class OpenAiChatStreamText(
    val reasoning: String?,
    val content: String?,
)

internal fun extractOpenAiChatStreamText(data: String): OpenAiChatStreamText {
    val root = data.toJsonObject()
        ?: return OpenAiChatStreamText(reasoning = null, content = null)
    val choice = root.get("choices")
        ?.asJsonArrayOrNull()
        ?.firstOrNull()
        ?.asJsonObjectOrNull()
    val delta = choice?.get("delta")?.asJsonObjectOrNull()
    val message = choice?.get("message")?.asJsonObjectOrNull()
    val reasoning = delta?.get("reasoning_content")?.textContent()
        ?: delta?.get("reasoning")?.textContent()
        ?: message?.get("reasoning_content")?.textContent()
        ?: message?.get("reasoning")?.textContent()
    val content = delta?.get("content")?.textContent()
        ?: message?.get("content")?.textContent()
        ?: choice?.get("text")?.textContent()
        ?: root.get("output_text")?.textContent()
    return OpenAiChatStreamText(
        reasoning = reasoning?.takeIf(String::isNotBlank),
        content = content?.takeIf(String::isNotBlank),
    )
}

private fun JsonElement.textContent(): String? {
    if (isJsonNull) return null
    if (isJsonPrimitive) return runCatching { asString }.getOrNull()
    if (isJsonArray) {
        return asJsonArray.mapNotNull { it.textContent() }
            .joinToString("")
            .takeIf(String::isNotBlank)
    }
    if (isJsonObject) {
        val value: JsonObject = asJsonObject
        return value.get("text")?.textContent()
            ?: value.get("content")?.textContent()
            ?: value.get("value")?.textContent()
    }
    return null
}
