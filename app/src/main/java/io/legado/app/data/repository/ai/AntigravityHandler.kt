package io.legado.app.data.repository.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import android.util.Log
import io.legado.app.constant.AppLog
import io.legado.app.data.repository.ANTIGRAVITY_DEFAULT_SYSTEM
import io.legado.app.data.repository.ANTIGRAVITY_IDE_BASE_URL
import io.legado.app.data.repository.ANTIGRAVITY_IDE_USER_AGENT
import io.legado.app.data.repository.ANTIGRAVITY_PRODUCTION_BASE_URL
import io.legado.app.data.repository.ANTIGRAVITY_SUPPORTED_MODELS
import io.legado.app.data.repository.generateAntigravityProjectId
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

/** Google Cloud Code Assist transport used by the Antigravity desktop subscription. */
class AntigravityHandler : AiProtocolHandler {

    override val protocols: Set<String> = setOf(AiProtocol.ANTIGRAVITY)

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val output = StringBuilder()
                streamInternal(request) { event ->
                    if (event is AiStreamEvent.Content) output.append(event.text)
                }
                output.toString().takeIf(String::isNotBlank)
                    ?.let(::AiGenerateResponse)
                    ?: error("Empty Antigravity response")
            }
        }

    override suspend fun stream(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
    ) = streamInternal(request, emitEvent)

    override suspend fun fetchModels(
        provider: AiProviderConfig,
    ): Result<List<AiAvailableModel>> = Result.success(ANTIGRAVITY_SUPPORTED_MODELS)

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
    ) {
        val provider = request.model.provider
        val projectId = provider.runtimeMetadata["projectId"]
            ?: provider.runtimeMetadata["cloudaicompanionProject"]
            ?: provider.runtimeMetadata["project"]
            ?: generateAntigravityProjectId()
        val configuredBaseUrl = provider.baseUrl.trimEnd('/').ifBlank { ANTIGRAVITY_IDE_BASE_URL }
        require(
            configuredBaseUrl.isNotBlank() &&
                provider.apiKey.isNotBlank() &&
                request.model.modelId.isNotBlank()
        ) {
            "Antigravity configuration incomplete: OAuth token and model are required"
        }
        val candidateBaseUrls = listOf(
            configuredBaseUrl,
            if (configuredBaseUrl == ANTIGRAVITY_IDE_BASE_URL) ANTIGRAVITY_PRODUCTION_BASE_URL else ANTIGRAVITY_IDE_BASE_URL,
        ).distinct()
        val sessionId = provider.runtimeMetadata["sessionId"]
            ?.takeIf(String::isNotBlank)
            ?: request.routeSessionKey?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString()
        val body = buildAntigravityRequestEnvelope(
            request = request,
            projectId = projectId,
            sessionId = sessionId,
        )
        val tokenRotator = KeyRotator(provider.apiKey)
        var lastException: Exception? = null
        var activeResponse: okhttp3.Response? = null

        for (targetBaseUrl in candidateBaseUrls) {
            try {
                Log.d("AntigravityHandler", "Calling Antigravity endpoint $targetBaseUrl for model ${request.model.modelId}")
                activeResponse = retryWithBackoff(maxAttempts = tokenRotator.attemptsAtLeast(2), keyRotator = tokenRotator) {
                    aiOkHttpClient.newCallResponse {
                        url("$targetBaseUrl/v1internal:streamGenerateContent?alt=sse")
                        postJson(GSON.toJson(body))
                        addHeaders(
                            provider.headers + provider.customHeaders + mapOf(
                                "Accept" to "text/event-stream",
                                "Authorization" to "Bearer ${tokenRotator.currentKey}",
                                "Content-Type" to "application/json",
                                "User-Agent" to ANTIGRAVITY_IDE_USER_AGENT,
                            )
                        )
                    }.also { resp ->
                        if (!resp.isSuccessful) {
                            val message = resp.body.string().take(1000)
                            resp.close()
                            Log.e("AntigravityHandler", "Antigravity HTTP ${resp.code} on $targetBaseUrl: $message")
                            AppLog.put("Antigravity HTTP ${resp.code} on $targetBaseUrl: $message")
                            error("HTTP ${resp.code}: ${message.ifBlank { resp.message }}")
                        }
                    }
                }
                break
            } catch (e: Exception) {
                lastException = e
                Log.w("AntigravityHandler", "Endpoint $targetBaseUrl failed: ${e.message}, attempting next candidate if available")
            }
        }
        val response = activeResponse ?: throw (lastException ?: Exception("All Antigravity endpoints failed"))
        try {
            response.readSseData { data ->
                val root = data.toJsonObject()
                    ?: error("Invalid Antigravity stream chunk")
                root.extractApiErrorMessage()?.let(::error)
                val payload = root.get("response")?.asJsonObjectOrNull() ?: root
                payload.antigravityParts().forEachIndexed { index, part ->
                    part.getString("text")?.takeIf(String::isNotEmpty)?.let { text ->
                        if (runCatching { part.get("thought")?.asBoolean }.getOrNull() == true) {
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
                            )
                        )
                    }
                }

                // Citations and grounding
                payload.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObjectOrNull()?.let { candidate ->
                    candidate.getAsJsonObject("citationMetadata")
                        ?.getAsJsonArray("citationSources")
                        ?.forEach { elem ->
                            elem.asJsonObjectOrNull()?.let { source ->
                                val uri = source.getString("uri")
                                if (!uri.isNullOrBlank()) {
                                    emitEvent(
                                        AiStreamEvent.Citation(
                                            startIndex = source.get("startIndex")?.asInt,
                                            endIndex = source.get("endIndex")?.asInt,
                                            uri = uri,
                                            title = source.getString("title").orEmpty(),
                                            snippet = source.getString("snippet").orEmpty(),
                                        )
                                    )
                                }
                            }
                        }
                    candidate.getAsJsonObject("groundingMetadata")
                        ?.getAsJsonArray("groundingChunks")
                        ?.forEach { elem ->
                            elem.asJsonObjectOrNull()?.getAsJsonObject("web")?.let { web ->
                                val uri = web.getString("uri")
                                if (!uri.isNullOrBlank()) {
                                    emitEvent(
                                        AiStreamEvent.Citation(
                                            uri = uri,
                                            title = web.getString("title").orEmpty(),
                                        )
                                    )
                                }
                            }
                        }
                }

                // Token usage
                payload.getAsJsonObject("usageMetadata")?.let { usage ->
                    val promptTokens = usage.get("promptTokenCount")?.asInt ?: 0
                    val completionTokens = usage.get("candidatesTokenCount")?.asInt ?: 0
                    val totalTokens = usage.get("totalTokenCount")?.asInt ?: (promptTokens + completionTokens)
                    val reasoningTokens = usage.get("thoughtsTokenCount")?.asInt ?: 0
                    emitEvent(
                        AiStreamEvent.Usage(
                            promptTokens = promptTokens,
                            completionTokens = completionTokens,
                            totalTokens = totalTokens,
                            reasoningTokens = reasoningTokens,
                        )
                    )
                }
            }
        } finally {
            response.close()
        }
    }
}

/** Build the protobuf JSON envelope emitted by the Antigravity IDE client. */
internal fun buildAntigravityRequestEnvelope(
    request: AiGenerateRequest,
    projectId: String,
    sessionId: String,
    currentTimeMillis: Long = System.currentTimeMillis(),
): Map<String, Any?> {
    val isClaude = request.model.modelId.contains("claude", ignoreCase = true)
    val requestBody = buildGeminiRequestBody(request)
        .toAntigravityRequest(isClaude = isClaude)
        .toMutableMap()
        .apply {
            put("sessionId", sessionId)
            if ((get("tools") as? List<*>)?.isNotEmpty() == true) {
                put(
                    "toolConfig",
                    mapOf("functionCallingConfig" to mapOf("mode" to "VALIDATED")),
                )
            }
        }

    // #9030 — The upstream Antigravity / Cloud Code endpoint rejects custom or oversized systemInstruction
    // with 429 RESOURCE_EXHAUSTED.
    // Keep ONLY ANTIGRAVITY_DEFAULT_SYSTEM in systemInstruction, and relocate any client system
    // instruction into the first user message contents[0]!
    val clientSystemParts = (requestBody["systemInstruction"] as? Map<*, *>)
        ?.get("parts") as? List<*>

    requestBody["systemInstruction"] = mapOf(
        "role" to "system",
        "parts" to listOf(mapOf("text" to ANTIGRAVITY_DEFAULT_SYSTEM)),
    )

    if (!clientSystemParts.isNullOrEmpty()) {
        @Suppress("UNCHECKED_CAST")
        val rawContents = requestBody["contents"] as? List<Map<String, Any?>>
        val contents = rawContents?.map { it.toMutableMap() }?.toMutableList() ?: mutableListOf()
        if (contents.isNotEmpty()) {
            val first = contents[0]
            @Suppress("UNCHECKED_CAST")
            val existingParts = (first["parts"] as? List<Any?>)?.toMutableList() ?: mutableListOf()
            existingParts.addAll(0, clientSystemParts as List<Any?>)
            first["parts"] = existingParts
            contents[0] = first
            requestBody["contents"] = contents
        } else {
            requestBody["contents"] = listOf(
                mapOf(
                    "role" to "user",
                    "parts" to clientSystemParts,
                )
            )
        }
    }

    val enabledCredits = request.model.provider.runtimeMetadata["enabledCreditTypes"]
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.takeIf(List<*>::isNotEmpty)
        ?: listOf("GOOGLE_ONE_AI")

    return mapOf(
        "project" to projectId,
        "model" to request.model.modelId,
        "userAgent" to "antigravity",
        "requestType" to "agent",
        "requestId" to buildAntigravityIdeRequestId(
            sessionId = sessionId,
            modelId = request.model.modelId,
            contentCount = (requestBody["contents"] as? List<*>)?.size ?: 1,
            currentTimeMillis = currentTimeMillis,
        ),
        "enabledCreditTypes" to enabledCredits,
        "request" to requestBody,
    )
}

private fun buildAntigravityIdeRequestId(
    sessionId: String,
    modelId: String,
    contentCount: Int,
    currentTimeMillis: Long,
): String {
    val conversationId = antigravityUuidFromSeed("antigravity:conversation:$sessionId")
    val trajectoryId = antigravityUuidFromSeed(
        "antigravity:trajectory:$sessionId:$modelId:agent"
    )
    val step = (contentCount * 2 - 1).coerceAtLeast(1)
    return "agent/$conversationId/$currentTimeMillis/$trajectoryId/$step"
}

private fun antigravityUuidFromSeed(seed: String): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(seed.toByteArray(Charsets.UTF_8))
        .copyOfRange(0, 16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-" +
        "${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}

private fun JsonObject.antigravityParts(): List<JsonObject> {
    val candidateParts = getAsJsonArray("candidates")?.toList().orEmpty().flatMap { candidate ->
        candidate.asJsonObjectOrNull()
            ?.get("content")
            ?.asJsonObjectOrNull()
            ?.getAsJsonArray("parts")
            ?.toList()
            .orEmpty()
            .mapNotNull(JsonElement::asJsonObjectOrNull)
    }
    // Cloud Code Assist has emitted both Gemini candidates and the newer serverContent/modelTurn
    // envelope during rollout. Accept both so a backend shape change does not look like an empty
    // translation to the user.
    val serverParts = get("serverContent")
        ?.asJsonObjectOrNull()
        ?.get("modelTurn")
        ?.asJsonObjectOrNull()
        ?.getAsJsonArray("parts")
        ?.toList()
        .orEmpty()
        .mapNotNull(JsonElement::asJsonObjectOrNull)
    return candidateParts + serverParts
}

/** Remove fields accepted by Gemini public API but rejected by Cloud Code Assist. */
private fun Map<String, Any?>.toAntigravityRequest(isClaude: Boolean = false): Map<String, Any?> {
    val result = toMutableMap()
    val generationConfig = (result["generationConfig"] as? Map<*, *>)
        ?.entries
        ?.associate { (key, value) -> key.toString() to value }
        ?.toMutableMap()
    generationConfig?.get("maxOutputTokens")?.let { value ->
        val maxTokens = (value as? Number)?.toInt()
        if (maxTokens != null) {
            val cap = if (isClaude) 16_384 else 64_000
            generationConfig["maxOutputTokens"] = maxTokens.coerceAtMost(cap)
        }
    }
    if (generationConfig != null) {
        if (isClaude) {
            generationConfig.remove("thinkingConfig")
        }
        result["generationConfig"] = generationConfig
    }
    result.remove("output_config")
    result.remove("thinking")
    result.remove("reasoning")
    result.remove("reasoning_effort")
    result.remove("enable_thinking")
    result.remove("thinking_budget")
    if (isClaude) {
        result.remove("thinkingConfig")
    }
    return result
}
