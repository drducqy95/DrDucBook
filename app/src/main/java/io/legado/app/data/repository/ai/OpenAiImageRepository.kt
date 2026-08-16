package io.legado.app.data.repository.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.gateway.AiImageGateway
import io.legado.app.domain.model.AiImageGenerateRequest
import io.legado.app.domain.model.AiImageGenerateResult
import io.legado.app.domain.model.AiProtocol
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

class OpenAiImageRepository : AiImageGateway {

    override suspend fun generate(request: AiImageGenerateRequest): AiImageGenerateResult =
        withContext(Dispatchers.IO) {
            val provider = request.model.provider
            require(
                provider.protocol == AiProtocol.OPENAI_CHAT_COMPLETIONS ||
                    provider.protocol == AiProtocol.OPENAI_RESPONSES
            ) { "Story image generation currently requires an OpenAI-compatible provider" }
            require(
                provider.baseUrl.isNotBlank() &&
                    provider.hasRequiredCredential() &&
                    request.model.modelId.isNotBlank()
            ) { "Image provider configuration is incomplete" }
            val keyRotator = KeyRotator(provider.apiKey)
            retryWithBackoff(
                maxAttempts = keyRotator.attemptsAtLeast(2),
                keyRotator = keyRotator,
            ) {
                val response = okHttpClient.newCallStrResponse {
                    url(provider.baseUrl.trimEnd('/') + "/images/generations")
                    postJson(
                        GSON.toJson(
                            linkedMapOf(
                                "model" to request.model.modelId,
                                "prompt" to request.prompt,
                                "size" to request.size,
                                "quality" to request.quality,
                                "n" to 1,
                                "output_format" to "png",
                            )
                        )
                    )
                    addHeaders(openAiChatHeaders(provider, keyRotator.currentKey))
                }
                if (!response.isSuccessful()) {
                    throw Exception("HTTP ${response.code()}: ${response.body.orEmpty().take(500)}")
                }
                response.body.orEmpty().toImageResult()
            }
        }

    private suspend fun String.toImageResult(): AiImageGenerateResult {
        val root = JsonParser.parseString(this).asJsonObject
        val item = root.getAsJsonArray("data")?.firstOrNull()?.asJsonObject
            ?: throw Exception("Image provider returned no image")
        val bytes = item.string("b64_json")?.takeIf(String::isNotBlank)?.let { encoded ->
            Base64.getDecoder().decode(encoded)
        } ?: item.string("url")?.takeIf(String::isNotBlank)?.let { imageUrl ->
            val download = okHttpClient.newCallResponse { url(imageUrl) }
            if (!download.isSuccessful) throw Exception("Image download failed: HTTP ${download.code}")
            download.body.bytes()
        } ?: throw Exception("Image provider returned neither b64_json nor url")
        return AiImageGenerateResult(
            bytes = bytes,
            mimeType = "image/png",
            revisedPrompt = item.string("revised_prompt"),
        )
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
}
