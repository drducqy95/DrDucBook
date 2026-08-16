package io.legado.app.data.repository.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiHandler : AiProtocolHandler {

    override val protocols = setOf(AiProtocol.GEMINI_GENERATE_CONTENT)

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        withContext(Dispatchers.IO) {
            runCatching { generateInternal(request) }
        }

    override suspend fun stream(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
    ) {
        streamInternal(request, emitEvent)
    }

    override suspend fun fetchModels(
        provider: AiProviderConfig,
    ): Result<List<AiAvailableModel>> = withContext(Dispatchers.IO) {
        runCatching { fetchModelsInternal(provider) }
    }

    private suspend fun generateInternal(request: AiGenerateRequest): AiGenerateResponse {
        val provider = request.model.provider
        validate(request)
        val keys = KeyRotator(provider.apiKey)
        return retryWithBackoff(maxAttempts = keys.attemptsAtLeast(3), keyRotator = keys) {
            val response = aiOkHttpClient.newCallStrResponse {
                url(geminiGenerateUrl(provider, request.model.modelId, streaming = false))
                postJson(GSON.toJson(buildGeminiRequestBody(request)))
                addHeaders(geminiHeaders(provider, keys.currentKey))
            }
            val body = response.body.orEmpty()
            if (!response.isSuccessful()) {
                val detail = body.toJsonObject()?.extractApiErrorMessage()
                throw Exception("HTTP ${response.code()}: ${detail ?: response.message()}")
            }
            val root = body.toJsonObject() ?: throw Exception("Invalid Gemini response")
            root.extractApiErrorMessage()?.let { throw Exception(it) }
            val text = extractGeminiText(root)
            if (text.isBlank()) {
                throw Exception(extractGeminiBlockedReason(root) ?: "Empty Gemini response")
            }
            AiGenerateResponse(text = text, rawBody = body)
        }
    }

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
    ) {
        val provider = request.model.provider
        validate(request)
        val keys = KeyRotator(provider.apiKey)
        val response = retryWithBackoff(maxAttempts = keys.attemptsAtLeast(3), keyRotator = keys) {
            aiOkHttpClient.newCallResponse {
                url(geminiGenerateUrl(provider, request.model.modelId, streaming = true))
                postJson(GSON.toJson(buildGeminiRequestBody(request)))
                addHeaders(geminiHeaders(provider, keys.currentKey))
            }.also {
                if (!it.isSuccessful) {
                    val detail = runCatching {
                        it.peekBody(128 * 1024L).string()
                    }.getOrNull()
                        ?.toJsonObject()
                        ?.extractApiErrorMessage()
                    throw Exception("HTTP ${it.code}: ${detail ?: it.message}")
                }
            }
        }
        try {
            response.readSseData { data ->
                val root = data.toJsonObject()
                    ?: throw Exception("Invalid Gemini stream chunk")
                root.extractApiErrorMessage()?.let { throw Exception(it) }
                val parts = root.geminiResponseParts()
                parts.forEachIndexed { index, part ->
                    val text = part.getString("text")
                    if (!text.isNullOrEmpty()) {
                        if (part.get("thought")?.asBoolean == true) {
                            emitEvent(AiStreamEvent.Reasoning(text))
                        } else {
                            emitEvent(AiStreamEvent.Content(text))
                        }
                    }
                    part.get("functionCall")?.asJsonObjectOrNull()?.let { call ->
                        emitEvent(
                            AiStreamEvent.ToolCallDelta(
                                id = call.getString("id"),
                                index = index,
                                name = call.getString("name"),
                                argumentsDelta = call.get("args")?.let(GSON::toJson),
                                rawType = "functionCall",
                                metadata = part.getString("thoughtSignature"),
                            )
                        )
                    }
                }
            }
        } finally {
            response.close()
        }
    }

    private suspend fun fetchModelsInternal(
        provider: AiProviderConfig,
    ): List<AiAvailableModel> {
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank()) {
            "Gemini configuration incomplete: baseUrl and apiKey are required"
        }
        val keys = KeyRotator(provider.apiKey)
        return retryWithBackoff(maxAttempts = keys.attemptsAtLeast(2), keyRotator = keys) {
            val response = okHttpClient.newCallStrResponse {
                url(
                    provider.modelsUrl
                        ?: provider.modelsPath?.let { provider.baseUrl.trimEnd('/') + it }
                        ?: provider.baseUrl.trimEnd('/') + "/models?pageSize=1000"
                )
                addHeaders(geminiHeaders(provider, keys.currentKey))
            }
            val body = response.body.orEmpty()
            if (!response.isSuccessful()) {
                val detail = body.toJsonObject()?.extractApiErrorMessage()
                throw Exception("HTTP ${response.code()}: ${detail ?: response.message()}")
            }
            val root = body.toJsonObject() ?: throw Exception("Invalid Gemini models response")
            root.getAsJsonArray("models")?.toList().orEmpty().mapNotNull { element ->
                val model = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val methods = model.getAsJsonArray("supportedGenerationMethods")
                    ?: model.getAsJsonArray("supportedActions")
                if (methods != null && methods.none { it.asString == "generateContent" }) {
                    return@mapNotNull null
                }
                val id = model.getString("baseModelId")
                    ?: model.getString("name")?.removePrefix("models/")
                    ?: return@mapNotNull null
                AiAvailableModel(
                    id = id,
                    name = model.getString("displayName") ?: id,
                    contextWindow = model.get("inputTokenLimit")?.asInt ?: 0,
                    maxOutputTokens = model.get("outputTokenLimit")?.asInt ?: 0,
                )
            }.distinctBy { it.id }
                .sortedWith(
                    compareBy<AiAvailableModel> {
                        preferredGeminiModelRank(it.id)
                    }.thenBy { it.name.lowercase() }
                )
        }
    }

    private fun validate(request: AiGenerateRequest) {
        require(
            request.model.provider.baseUrl.isNotBlank() &&
                request.model.provider.apiKey.isNotBlank() &&
                request.model.modelId.isNotBlank()
        ) {
            "Gemini configuration incomplete: baseUrl, apiKey, and model are required"
        }
    }
}

private val PREFERRED_GEMINI_TRANSLATION_MODELS = listOf(
    "gemini-3.6-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.5-flash",
    "gemini-3.1-flash-lite",
    "gemini-3.1-flash",
    "gemini-2.5-flash-lite",
    "gemini-2.5-flash",
)

private fun preferredGeminiModelRank(modelId: String): Int {
    val normalized = modelId.lowercase()
    val exact = PREFERRED_GEMINI_TRANSLATION_MODELS.indexOf(normalized)
    return when {
        exact >= 0 -> exact
        normalized.endsWith("-preview") -> 50
        "flash-lite" in normalized -> 20
        "flash" in normalized -> 30
        else -> 100
    }
}

internal fun buildGeminiRequestBody(request: AiGenerateRequest): Map<String, Any?> {
    val systemInstruction = request.messages
        .filter { it.role == AiMessageRole.SYSTEM }
        .joinToString("\n\n") { it.content }
        .takeIf(String::isNotBlank)
    return buildMap {
        put("contents", request.messages.toGeminiContents())
        systemInstruction?.let {
            put("systemInstruction", mapOf("parts" to listOf(mapOf("text" to it))))
        }
        buildGeminiGenerationConfig(request)?.let { put("generationConfig", it) }
        request.tools.takeIf(List<*>::isNotEmpty)?.let { tools ->
            put(
                "tools",
                listOf(
                    mapOf(
                        "functionDeclarations" to tools.map { tool ->
                            buildMap {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", sanitizeGeminiSchema(tool.inputSchema))
                            }
                        }
                    )
                )
            )
        }
    }
}

/**
 * Gemini function declarations accept only a subset of JSON Schema/OpenAPI Schema. Tool schemas
 * are shared with OpenAI-compatible providers, so unsupported keywords must be removed here
 * instead of weakening the canonical tool contract for every provider.
 */
internal fun sanitizeGeminiSchema(schema: Map<String, Any?>): Map<String, Any?> = buildMap {
    schema.forEach { (key, value) ->
        when (key) {
            "type",
            "format",
            "title",
            "description",
            "nullable",
            "default",
            "minimum",
            "maximum",
            "minItems",
            "maxItems",
            "minLength",
            "maxLength",
            "pattern",
            -> put(key, value)

            "enum",
            "required",
            -> if (value is Iterable<*>) put(key, value.toList())

            "properties" -> {
                val properties = value as? Map<*, *> ?: return@forEach
                put(
                    key,
                    properties.mapNotNull { (name, propertySchema) ->
                        val propertyName = name as? String ?: return@mapNotNull null
                        val nested = propertySchema as? Map<*, *> ?: return@mapNotNull null
                        @Suppress("UNCHECKED_CAST")
                        propertyName to sanitizeGeminiSchema(nested as Map<String, Any?>)
                    }.toMap()
                )
            }

            "items" -> {
                val items = value as? Map<*, *> ?: return@forEach
                @Suppress("UNCHECKED_CAST")
                put(key, sanitizeGeminiSchema(items as Map<String, Any?>))
            }

            "anyOf",
            "oneOf",
            -> {
                val alternatives = value as? Iterable<*> ?: return@forEach
                put(
                    key,
                    alternatives.mapNotNull { alternative ->
                        val nested = alternative as? Map<*, *> ?: return@mapNotNull null
                        @Suppress("UNCHECKED_CAST")
                        sanitizeGeminiSchema(nested as Map<String, Any?>)
                    }
                )
            }
        }
    }
}

private fun List<AiMessage>.toGeminiContents(): List<Map<String, Any?>> = mapNotNull { message ->
    if (message.role == AiMessageRole.SYSTEM) return@mapNotNull null
    val parts = buildList<Map<String, Any?>> {
        if (message.role != AiMessageRole.TOOL && message.content.isNotBlank()) {
            add(mapOf("text" to message.content))
        }
        if (message.role == AiMessageRole.ASSISTANT) {
            message.toolCalls.forEach { call ->
                add(
                    buildMap {
                        put(
                            "functionCall",
                            buildMap<String, Any?> {
                            put("name", call.name)
                            put("args", parseGeminiArguments(call.arguments))
                            }
                        )
                        call.metadata?.takeIf(String::isNotBlank)?.let {
                            put("thoughtSignature", it)
                        }
                    }
                )
            }
        }
        if (message.role == AiMessageRole.TOOL) {
            add(
                mapOf(
                    "functionResponse" to buildMap<String, Any?> {
                        put("name", message.name.orEmpty())
                        put("response", parseGeminiToolResponse(message.content))
                    }
                )
            )
        }
    }
    parts.takeIf(List<*>::isNotEmpty)?.let {
        mapOf(
            "role" to if (message.role == AiMessageRole.ASSISTANT) "model" else "user",
            "parts" to it,
        )
    }
}

private fun buildGeminiGenerationConfig(request: AiGenerateRequest): Map<String, Any?>? {
    val params = request.params
    val config = buildMap<String, Any?> {
        if (request.taskType == AiTaskType.TRANSLATE_CHAPTER) {
            put("responseMimeType", "application/json")
        }
        params.temperature?.let { put("temperature", it) }
        params.maxOutputTokens?.let { put("maxOutputTokens", it) }
        params.topP?.let { put("topP", it) }
        params.topK?.let { put("topK", it) }
        buildGeminiThinkingConfig(request.model.modelId, params.reasoningLevel)?.let {
            put("thinkingConfig", it)
        }
    }
    return config.takeIf(Map<*, *>::isNotEmpty)
}

internal fun buildGeminiThinkingConfig(
    modelId: String,
    level: AiReasoningLevel,
): Map<String, Any?>? {
    if (level == AiReasoningLevel.AUTO || level == AiReasoningLevel.OFF) return null
    val normalizedModel = modelId.lowercase()
    return when {
        "gemini-3" in normalizedModel -> mapOf(
            "thinkingLevel" to when (level) {
                AiReasoningLevel.LOW -> "low"
                AiReasoningLevel.MEDIUM -> "medium"
                AiReasoningLevel.HIGH,
                AiReasoningLevel.XHIGH -> "high"
                AiReasoningLevel.OFF,
                AiReasoningLevel.AUTO -> error("Handled above")
            }
        )
        "gemini-2.5" in normalizedModel -> mapOf(
            "thinkingBudget" to when (level) {
                AiReasoningLevel.OFF -> 0
                AiReasoningLevel.LOW -> 1_000
                AiReasoningLevel.MEDIUM -> 2_000
                AiReasoningLevel.HIGH -> 8_000
                AiReasoningLevel.XHIGH -> 16_000
                AiReasoningLevel.AUTO -> error("Handled above")
            }
        )
        else -> null
    }
}

internal fun extractGeminiText(root: JsonObject): String = root.geminiResponseParts()
    .filterNot { it.get("thought")?.asBoolean == true }
    .mapNotNull { it.getString("text") }
    .joinToString("")

private fun JsonObject.geminiResponseParts(): List<JsonObject> =
    getAsJsonArray("candidates")?.toList().orEmpty()
        .flatMap { candidate ->
            candidate.asJsonObjectOrNull()
                ?.get("content")
                ?.asJsonObjectOrNull()
                ?.getAsJsonArray("parts")
                ?.toList()
                .orEmpty()
                .mapNotNull(JsonElement::asJsonObjectOrNull)
        }

private fun extractGeminiBlockedReason(root: JsonObject): String? =
    root.get("promptFeedback")?.asJsonObjectOrNull()?.getString("blockReason")
        ?: root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObjectOrNull()
            ?.getString("finishReason")

private fun geminiGenerateUrl(
    provider: AiProviderConfig,
    rawModelId: String,
    streaming: Boolean,
): String {
    val modelId = rawModelId.removePrefix("models/")
    val method = if (streaming) "streamGenerateContent?alt=sse" else "generateContent"
    return "${provider.baseUrl.trimEnd('/')}/models/$modelId:$method"
}

private fun geminiHeaders(provider: AiProviderConfig, apiKey: String): Map<String, String> =
    provider.headers + provider.customHeaders + mapOf(
        "x-goog-api-key" to apiKey,
        "Content-Type" to "application/json",
    )

private fun parseGeminiArguments(raw: String): Any = runCatching {
    GSON.fromJson(raw, JsonElement::class.java)
}.getOrNull()?.takeUnless(JsonElement::isJsonNull) ?: emptyMap<String, Any?>()

private fun parseGeminiToolResponse(raw: String): Any = runCatching {
    GSON.fromJson(raw, JsonElement::class.java)
}.getOrNull()?.takeUnless(JsonElement::isJsonNull) ?: mapOf("result" to raw)
