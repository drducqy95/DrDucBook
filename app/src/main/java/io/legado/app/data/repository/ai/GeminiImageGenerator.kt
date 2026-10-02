package io.legado.app.data.repository.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.model.AiImageGenerateRequest
import io.legado.app.domain.model.AiImageGenerateResult
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Generates images via the Gemini generateContent API using `responseModalities: ["IMAGE"]`.
 *
 * Models that support native image generation (gemini-2.0-flash-exp, gemini-2.0-flash-preview-image-generation,
 * imagen-3.0-generate-002, etc.) accept a text prompt and return inline base64-encoded image data
 * in the response `parts` array.
 */
object GeminiImageGenerator {

    suspend fun generate(request: AiImageGenerateRequest): AiImageGenerateResult =
        withContext(Dispatchers.IO) {
            val provider = request.model.provider
            val keys = KeyRotator(provider.apiKey)
            retryWithBackoff(maxAttempts = keys.attemptsAtLeast(2), keyRotator = keys) {
                val body = buildGeminiImageRequestBody(request)
                val modelId = request.model.modelId.removePrefix("models/")
                val url = "${provider.baseUrl.trimEnd('/')}/models/$modelId:generateContent"
                val response = aiOkHttpClient.newCallStrResponse {
                    url(url)
                    postJson(GSON.toJson(body))
                    addHeaders(geminiImageHeaders(provider, keys.currentKey))
                }
                val responseBody = response.body.orEmpty()
                if (!response.isSuccessful()) {
                    val detail = responseBody.toJsonObject()?.extractApiErrorMessage()
                    throw Exception("HTTP ${response.code()}: ${detail ?: response.message()}")
                }
                parseGeminiImageResponse(responseBody)
            }
        }

    private fun buildGeminiImageRequestBody(request: AiImageGenerateRequest): Map<String, Any?> =
        buildMap {
            put(
                "contents", listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to request.prompt)),
                    )
                )
            )
            put(
                "generationConfig", buildMap {
                    put("responseModalities", listOf("IMAGE", "TEXT"))
                    // Gemini image generation does not support maxOutputTokens or temperature
                    // for image output, but we keep the config minimal.
                }
            )
        }

    private fun parseGeminiImageResponse(body: String): AiImageGenerateResult {
        val root = JsonParser.parseString(body).asJsonObject
        root.extractApiErrorMessage()?.let { throw Exception(it) }

        val candidates = root.optJsonArray("candidates")
            ?: throw Exception("Gemini image response: no candidates")

        for (candidate in candidates) {
            val parts = candidate.asJsonObjectOrNull()
                ?.optJsonObject("content")
                ?.optJsonArray("parts")
                ?: continue

            for (part in parts) {
                val partObj = part.asJsonObjectOrNull() ?: continue
                val inlineData = partObj.optJsonObject("inlineData") ?: continue
                val mimeType = inlineData.getString("mimeType")
                    ?: continue
                val b64 = inlineData.getString("data")
                    ?.takeIf(String::isNotBlank)
                    ?: continue

                return AiImageGenerateResult(
                    bytes = Base64.getDecoder().decode(b64),
                    mimeType = mimeType,
                    revisedPrompt = extractTextFromParts(parts),
                )
            }
        }

        val blockedReason = extractGeminiBlockedReason(root)
        throw Exception(blockedReason ?: "Gemini returned no image data. Model may not support image generation.")
    }

    private fun extractTextFromParts(parts: com.google.gson.JsonArray): String? =
        parts.mapNotNull { it.asJsonObjectOrNull()?.getString("text") }
            .joinToString("")
            .takeIf(String::isNotBlank)

    private fun extractGeminiBlockedReason(root: JsonObject): String? =
        root.optJsonObject("promptFeedback")?.getString("blockReason")
            ?: root.optJsonArray("candidates")?.firstOrNull()?.asJsonObjectOrNull()
                ?.getString("finishReason")?.takeIf { it != "STOP" }

    private fun geminiImageHeaders(provider: AiProviderConfig, apiKey: String): Map<String, String> =
        provider.headers + provider.customHeaders + mapOf(
            "x-goog-api-key" to apiKey,
            "Content-Type" to "application/json",
        )
}
