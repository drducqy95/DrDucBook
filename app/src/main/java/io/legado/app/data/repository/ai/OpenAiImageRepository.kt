package io.legado.app.data.repository.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.AiImageGateway
import io.legado.app.domain.gateway.AiSecretStore
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiCredentialStatus
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiImageGenerateRequest
import io.legado.app.domain.model.AiImageGenerateResult
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiTaskType
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Universal multi-provider image generation gateway.
 *
 * Dispatches to the appropriate backend based on the provider protocol:
 * - **OpenAI / OpenAI Responses**: Uses `/images/generations` endpoint (DALL-E, Flux, etc.).
 *   If the model is a chat model or the endpoint is unavailable, falls back to text+image pipeline.
 * - **Google Gemini API**: Uses `generateContent` with `responseModalities: ["IMAGE"]` via [GeminiImageGenerator].
 * - **ChatGPT Web & Gemini Web**: Uses conversation endpoint to generate image / image URL,
 *   extracts image from Markdown/web URL, and downloads it.
 * - **Universal Fallback**: If a text-only LLM does not return an image URL, generates high-quality
 *   illustration via Pollinations.ai FLUX engine using the refined story illustration prompt.
 */
class OpenAiImageRepository(
    private val secretStore: AiSecretStore? = null,
    private val appDb: AppDatabase? = null,
    private val textGatewayLazy: Lazy<AiTextGateway?>? = null,
) : AiImageGateway {

    companion object {
        private val IMAGE_URL_REGEX = Regex(
            """https?://[^\s"'<>]+\.(?:png|jpg|jpeg|webp)(?:\?[^\s"'<>]*)?""",
            RegexOption.IGNORE_CASE
        )
        private val MARKDOWN_IMAGE_REGEX = Regex("""!\[.*?\]\((https?://[^\s\)]+)\)""")
        private val GOOGLE_IMAGE_REGEX = Regex("""https?://lh\d+\.googleusercontent\.com/[^\s"'<>]+""")
        private val OPENAI_IMAGE_REGEX = Regex("""https?://files\.oaiusercontent\.com/[^\s"'<>]+""")
        private val DATA_URI_REGEX = Regex("""data:image/(?:png|jpeg|jpg|webp);base64,([A-Za-z0-9+/=]+)""")
    }

    override suspend fun generate(request: AiImageGenerateRequest): AiImageGenerateResult =
        withContext(Dispatchers.IO) {
            val resolved = resolveCredentials(request)
            when (resolved.model.provider.protocol) {
                AiProtocol.GEMINI_GENERATE_CONTENT -> {
                    try {
                        GeminiImageGenerator.generate(resolved)
                    } catch (geminiError: Throwable) {
                        // Fallback to text gateway or visual prompt generation if native image modality fails
                        runCatching { generateViaTextGateway(resolved) }
                            .getOrElse { generateViaPollinations(resolved.prompt) }
                    }
                }
                AiProtocol.OPENAI_CHAT_COMPLETIONS,
                AiProtocol.OPENAI_RESPONSES -> {
                    try {
                        generateOpenAi(resolved)
                    } catch (openAiError: Throwable) {
                        // If model is not supported on /images/generations (e.g. gpt-4o), fallback
                        runCatching { generateViaTextGateway(resolved) }
                            .getOrElse { generateViaPollinations(resolved.prompt) }
                    }
                }
                AiProtocol.CHATGPT_WEB,
                AiProtocol.GEMINI_WEB -> {
                    generateViaTextGateway(resolved)
                }
                else -> {
                    // Other LLMs (Anthropic, DeepSeek, Local, etc.)
                    runCatching { generateViaTextGateway(resolved) }
                        .getOrElse { generateViaPollinations(resolved.prompt) }
                }
            }
        }

    /**
     * Resolves API key from inline field or AiSecretStore/router credentials.
     */
    private suspend fun resolveCredentials(request: AiImageGenerateRequest): AiImageGenerateRequest {
        val rawProvider = request.model.provider
        val resolvedApiKey = if (rawProvider.apiKey.isNotBlank()) {
            rawProvider.apiKey
        } else {
            val credential = appDb?.aiRouterDao?.getCredentialsForProvider(rawProvider.id)
                ?.firstOrNull { it.enabled && AiCredentialStatus.isRouterEligible(it.status) }
            if (credential != null && secretStore != null) {
                secretStore.get(credential.secretRef).orEmpty()
            } else ""
        }
        val provider = rawProvider.copy(apiKey = resolvedApiKey)
        return request.copy(model = request.model.copy(provider = provider))
    }

    /**
     * OpenAI-compatible image generation via /images/generations endpoint.
     */
    private suspend fun generateOpenAi(request: AiImageGenerateRequest): AiImageGenerateResult {
        val provider = request.model.provider
        require(
            provider.baseUrl.isNotBlank() &&
                provider.hasRequiredCredential() &&
                request.model.modelId.isNotBlank()
        ) { "Cấu hình provider tạo ảnh chưa đầy đủ (thiếu Base URL hoặc API Key). Vui lòng kiểm tra Cài đặt AI." }

        val keyRotator = KeyRotator(provider.apiKey)
        val effectiveQuality = if (request.quality.equals("hd", ignoreCase = true)) "hd" else "standard"
        return retryWithBackoff(
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
                            "quality" to effectiveQuality,
                            "n" to 1,
                            "response_format" to "b64_json",
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

    /**
     * Generates or extracts an image via chat/text gateway (for ChatGPT Web, Gemini Web, and other chat models).
     */
    private suspend fun generateViaTextGateway(
        request: AiImageGenerateRequest,
    ): AiImageGenerateResult {
        val textGateway = textGatewayLazy?.value
        if (textGateway != null) {
            val imagePrompt = buildString {
                append("Please generate or illustrate the following fiction wiki visual subject:\n\n")
                append(request.prompt)
                append("\n\nIf you can generate an image, please provide the direct image URL or markdown image.")
            }
            val textResult = runCatching {
                textGateway.generate(
                    AiGenerateRequest(
                        model = request.model,
                        messages = listOf(
                            AiMessage(
                                role = AiMessageRole.USER,
                                content = imagePrompt,
                            )
                        ),
                        taskType = AiTaskType.GENERATE_STORY_IMAGE,
                    )
                ).getOrNull()
            }.getOrNull()

            if (textResult != null && textResult.text.isNotBlank()) {
                val extractedBytes = extractImageFromText(textResult.text)
                if (extractedBytes != null && extractedBytes.isNotEmpty()) {
                    return AiImageGenerateResult(
                        bytes = extractedBytes,
                        mimeType = "image/png",
                        revisedPrompt = textResult.text.take(200),
                    )
                }
            }
        }

        // If no image could be extracted from web chat response, generate via Pollinations FLUX engine
        return generateViaPollinations(request.prompt)
    }

    /**
     * Extracts an image from text output (base64 data URI, Markdown image link, or cloud storage image URL).
     */
    private suspend fun extractImageFromText(text: String): ByteArray? {
        // 1. Data URI base64
        DATA_URI_REGEX.find(text)?.let { match ->
            val b64 = match.groupValues[1]
            return runCatching { Base64.getDecoder().decode(b64) }.getOrNull()
        }
        // 2. Direct / Markdown / Cloud storage image URL
        val url = MARKDOWN_IMAGE_REGEX.find(text)?.groupValues?.get(1)
            ?: GOOGLE_IMAGE_REGEX.find(text)?.value
            ?: OPENAI_IMAGE_REGEX.find(text)?.value
            ?: IMAGE_URL_REGEX.find(text)?.value

        if (url != null) {
            val response = runCatching {
                okHttpClient.newCallResponse { url(url) }
            }.getOrNull()
            if (response != null && response.isSuccessful) {
                return response.body.bytes()
            }
        }
        return null
    }

    /**
     * Pollinations.ai free FLUX engine fallback.
     */
    private suspend fun generateViaPollinations(prompt: String): AiImageGenerateResult {
        val cleanPrompt = prompt.lines()
            .filter { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("No caption", ignoreCase = true) &&
                    !trimmed.startsWith("Create a faithful", ignoreCase = true) &&
                    !trimmed.startsWith("VERIFIED", ignoreCase = true)
            }
            .joinToString(" ")
            .take(400)
            .ifBlank { prompt.take(400) }

        val encoded = URLEncoder.encode(cleanPrompt, StandardCharsets.UTF_8.name())
        val url = "https://image.pollinations.ai/prompt/$encoded?width=1024&height=1024&nologo=true&model=flux"
        val response = okHttpClient.newCallResponse { url(url) }
        if (!response.isSuccessful) {
            throw Exception("Tạo ảnh từ provider thất bại (HTTP ${response.code}). Vui lòng kiểm tra kết nối mạng hoặc thử lại với model khác.")
        }
        val bytes = response.body.bytes()
        require(bytes.isNotEmpty()) { "Tạo ảnh trả về dữ liệu rỗng" }
        return AiImageGenerateResult(
            bytes = bytes,
            mimeType = "image/png",
            revisedPrompt = cleanPrompt,
        )
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
