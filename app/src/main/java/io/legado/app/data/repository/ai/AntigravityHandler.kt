package io.legado.app.data.repository.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.data.repository.ANTIGRAVITY_IDE_BASE_URL
import io.legado.app.data.repository.ANTIGRAVITY_IDE_USER_AGENT
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
    ): Result<List<AiAvailableModel>> = Result.success(emptyList())

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
    ) {
        val provider = request.model.provider
        val projectId = provider.runtimeMetadata["projectId"]
            ?: provider.runtimeMetadata["cloudaicompanionProject"]
            ?: provider.runtimeMetadata["project"]
            ?: generateAntigravityProjectId()
        require(
            provider.baseUrl.isNotBlank() &&
                provider.apiKey.isNotBlank() &&
                request.model.modelId.isNotBlank()
        ) {
            "Antigravity configuration incomplete: OAuth token and model are required"
        }
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
        val response = retryWithBackoff(maxAttempts = tokenRotator.attemptsAtLeast(3), keyRotator = tokenRotator) {
            aiOkHttpClient.newCallResponse {
                url(provider.baseUrl.trimEnd('/').ifBlank { ANTIGRAVITY_IDE_BASE_URL } + "/v1internal:streamGenerateContent?alt=sse")
                postJson(GSON.toJson(body))
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "Accept" to "text/event-stream",
                        "Authorization" to "Bearer ${tokenRotator.currentKey}",
                        "Content-Type" to "application/json",
                        // Antigravity gates the managed endpoint on the IDE fingerprint rather than
                        // the Android host running Legado.
                        "User-Agent" to ANTIGRAVITY_IDE_USER_AGENT,
                    )
                )
            }.also {
                if (!it.isSuccessful) {
                    val message = it.body.string().take(500)
                    it.close()
                    error("HTTP ${it.code}: ${message.ifBlank { it.message }}")
                }
            }
        }
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
    val requestBody = buildGeminiRequestBody(request)
        .toAntigravityRequest()
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
private fun Map<String, Any?>.toAntigravityRequest(): Map<String, Any?> {
    val result = toMutableMap()
    val generationConfig = (result["generationConfig"] as? Map<*, *>)
        ?.entries
        ?.associate { (key, value) -> key.toString() to value }
        ?.toMutableMap()
    generationConfig?.get("maxOutputTokens")?.let { value ->
        val maxTokens = (value as? Number)?.toInt()
        if (maxTokens != null) generationConfig["maxOutputTokens"] = maxTokens.coerceAtMost(64_000)
    }
    if (generationConfig != null) result["generationConfig"] = generationConfig
    result.remove("output_config")
    result.remove("thinking")
    result.remove("reasoning")
    result.remove("reasoning_effort")
    result.remove("enable_thinking")
    result.remove("thinking_budget")
    result.remove("thinkingConfig")
    return result
}
