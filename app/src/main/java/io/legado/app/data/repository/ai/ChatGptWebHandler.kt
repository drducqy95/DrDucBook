package io.legado.app.data.repository.ai

import android.webkit.CookieManager
import androidx.annotation.Keep
import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class ChatGptWebHandler : AiProtocolHandler {

    override val protocols: Set<String> = setOf(AiProtocol.CHATGPT_WEB)

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fullText = StringBuilder()
                streamInternal(request) { event ->
                    if (event is AiStreamEvent.Content) {
                        fullText.append(event.text)
                    }
                }
                val text = fullText.toString().trim()
                if (text.isBlank()) {
                    throw Exception("ChatGPT Web returned empty response")
                }
                AiGenerateResponse(text = text, rawBody = text)
            }
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
                listOf(
                    AiAvailableModel(
                        id = "auto",
                        name = "Auto (Web)",
                        contextWindow = 128_000,
                        maxOutputTokens = 4_096,
                    ),
                    AiAvailableModel(
                        id = "gpt-4o",
                        name = "GPT-4o (Web)",
                        contextWindow = 128_000,
                        maxOutputTokens = 4_096,
                    ),
                    AiAvailableModel(
                        id = "gpt-4o-mini",
                        name = "GPT-4o mini (Web)",
                        contextWindow = 128_000,
                        maxOutputTokens = 4_096,
                    ),
                    AiAvailableModel(
                        id = "o3-mini",
                        name = "o3 Mini (Web)",
                        contextWindow = 128_000,
                        maxOutputTokens = 16_384,
                    ),
                    AiAvailableModel(
                        id = "o1",
                        name = "o1 (Web)",
                        contextWindow = 128_000,
                        maxOutputTokens = 16_384,
                    ),
                    AiAvailableModel(
                        id = "o1-preview",
                        name = "o1 Preview (Web)",
                        contextWindow = 128_000,
                        maxOutputTokens = 4_096,
                    ),
                    AiAvailableModel(
                        id = "o1-mini",
                        name = "o1 Mini (Web)",
                        contextWindow = 128_000,
                        maxOutputTokens = 4_096,
                    ),
                )
            }
        }

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        val accessToken = ChatGptWebSessionManager.resolveAccessToken(request.model.provider.apiKey)
        val prompt = buildFullPrompt(request)
        val model = request.model.modelId.ifBlank { "auto" }

        val messageId = UUID.randomUUID().toString()
        val parentMessageId = UUID.randomUUID().toString()

        val bodyMap = mapOf(
            "action" to "next",
            "messages" to listOf(
                mapOf(
                    "id" to messageId,
                    "author" to mapOf("role" to "user"),
                    "content" to mapOf(
                        "content_type" to "text",
                        "parts" to listOf(prompt)
                    )
                )
            ),
            "model" to model,
            "parent_message_id" to parentMessageId,
            "timezone_offset_min" to -420,
            "suggestions" to emptyList<String>(),
            "history_and_training_disabled" to false,
            "conversation_mode" to mapOf("kind" to "primary_assistant"),
            "force_paragen" to false,
        )

        val jsonBody = GSON.toJson(bodyMap)
        val requestBody = jsonBody.toRequestBody("application/json;charset=utf-8".toMediaType())
        val url = if (request.model.provider.baseUrl.isNotBlank()) {
            request.model.provider.baseUrl.trimEnd('/') + request.model.provider.chatPath.ifEmpty { "/backend-api/conversation" }
        } else {
            "https://chatgpt.com/backend-api/conversation"
        }

        val explicitCred = request.model.provider.apiKey.trim()
        val cookieFromCred = if (explicitCred.startsWith("{") && explicitCred.endsWith("}")) {
            val json = runCatching { GSON.fromJson(explicitCred, JsonObject::class.java) }.getOrNull()
            json?.get("cookie")?.asString.orEmpty()
        } else if (explicitCred.contains("=") || explicitCred.contains(";")) {
            explicitCred
        } else {
            ""
        }

        val cookies = if (cookieFromCred.isNotBlank()) {
            cookieFromCred
        } else if (explicitCred.isBlank()) {
            runCatching {
                CookieManager.getInstance().getCookie("https://chatgpt.com")
            }.getOrNull().orEmpty()
        } else {
            ""
        }

        val oaiDeviceId = UUID.randomUUID().toString()
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        val sentinelTokens = ChatGptWebSentinel.fetchSentinelTokens(
            baseUrl = request.model.provider.baseUrl,
            accessToken = accessToken,
            cookies = cookies,
            userAgent = userAgent,
            deviceId = oaiDeviceId,
        )

        val headersMap = mutableMapOf(
            "Authorization" to "Bearer $accessToken",
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream",
            "User-Agent" to userAgent,
            "Origin" to "https://chatgpt.com",
            "Referer" to "https://chatgpt.com/",
            "oai-device-id" to oaiDeviceId,
            "oai-language" to "en-US",
        )
        if (!sentinelTokens.chatRequirementsToken.isNullOrBlank()) {
            headersMap["openai-sentinel-chat-requirements-token"] = sentinelTokens.chatRequirementsToken
        }
        if (!sentinelTokens.proofToken.isNullOrBlank()) {
            headersMap["openai-sentinel-proof-token"] = sentinelTokens.proofToken
        }
        if (cookies.isNotBlank()) {
            headersMap["Cookie"] = cookies
        }

        val response = okHttpClient.newCallResponse {
            url(url)
            post(requestBody)
            addHeaders(headersMap)
        }

        if (!response.isSuccessful) {
            val errorBody = runCatching { response.body.string() }.getOrNull().orEmpty()
            if (response.code == 401 || response.code == 403) {
                ChatGptWebSessionManager.invalidateSession(request.model.provider.apiKey)
                val hint = if (errorBody.contains("token_expired", ignoreCase = true) || errorBody.contains("expired", ignoreCase = true)) {
                    "Access Token đã hết hạn."
                } else {
                    "Access Token không hợp lệ hoặc thiếu quyền."
                }
                throw Exception(
                    "ChatGPT Web từ chối quyền truy cập (HTTP ${response.code}). $hint\n" +
                    "👉 Vui lòng mở https://chatgpt.com/api/auth/session trên trình duyệt ngoài (Chrome/Edge), copy chuỗi accessToken (bắt đầu bằng 'eyJ...') hoặc toàn bộ JSON rồi dán vào app." +
                    if (errorBody.isNotBlank()) "\nChi tiết lỗi từ OpenAI: $errorBody" else ""
                )
            }
            throw Exception("ChatGPT Web HTTP ${response.code}: ${response.message.ifBlank { errorBody }}")
        }

        var currentStreamKey: String? = null
        var emittedTextLength = 0
        var emittedAny = false

        try {
            response.readSseData { data ->
                if (data == "[DONE]") return@readSseData
                val root = runCatching { GSON.fromJson(data, JsonObject::class.java) }.getOrNull() ?: return@readSseData

                // Check error status safely (avoid UnsupportedOperationException on JsonNull or JsonObject)
                val errorElem = root.get("error")
                if (errorElem != null && !errorElem.isJsonNull) {
                    val errorMsg = if (errorElem.isJsonObject) {
                        val obj = errorElem.asJsonObject
                        obj.get("message")?.takeIf { !it.isJsonNull }?.asString
                            ?: obj.get("detail")?.takeIf { !it.isJsonNull }?.asString
                            ?: errorElem.toString()
                    } else {
                        runCatching { errorElem.asString }.getOrNull() ?: errorElem.toString()
                    }
                    if (errorMsg.isNotBlank()) {
                        throw Exception("ChatGPT Web error: $errorMsg")
                    }
                }

                // Only extract content from assistant messages (skip system/user echos)
                val messageObj = root.optJsonObject("message") ?: return@readSseData
                val authorRole = messageObj.optJsonObject("author")
                    ?.get("role")?.takeIf { !it.isJsonNull }?.asString
                if (authorRole != "assistant") return@readSseData

                val msgId = messageObj.get("id")?.takeIf { !it.isJsonNull }?.asString
                val channel = messageObj.get("channel")?.takeIf { !it.isJsonNull }?.asString
                val streamKey = "${msgId.orEmpty()}:${channel.orEmpty()}"
                if (streamKey != currentStreamKey) {
                    currentStreamKey = streamKey
                    emittedTextLength = 0
                }

                val contentObj = messageObj.optJsonObject("content")
                val contentType = contentObj?.get("content_type")?.takeIf { !it.isJsonNull }?.asString
                if (contentType == "text") {
                    val partsArray = contentObj.optJsonArray("parts")
                    if (partsArray != null && partsArray.size() > 0) {
                        val fullText = partsArray.joinToString("") { part ->
                            if (part.isJsonPrimitive) part.asString else ""
                        }
                        if (fullText.length > emittedTextLength) {
                            val delta = fullText.substring(emittedTextLength)
                            emittedTextLength = fullText.length
                            emittedAny = true
                            if (channel == "thought") {
                                emitEvent(AiStreamEvent.Reasoning(delta))
                            } else {
                                emitEvent(AiStreamEvent.Content(delta))
                            }
                        }
                    }
                }
            }
        } finally {
            response.close()
        }

        if (!emittedAny) {
            throw Exception("Không trích xuất được phản hồi từ ChatGPT Web. Vui lòng kiểm tra lại phiên đăng nhập.")
        }
    }

    private fun buildFullPrompt(request: AiGenerateRequest): String {
        val systemPrompt = request.messages
            .filter { it.role == AiMessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .takeIf { it.isNotBlank() }

        val nonSystem = request.messages.filter { it.role != AiMessageRole.SYSTEM }
        val conversation = if (nonSystem.size == 1 && nonSystem.first().role == AiMessageRole.USER) {
            nonSystem.first().content
        } else {
            nonSystem.joinToString("\n\n") { msg ->
                val roleName = if (msg.role == AiMessageRole.USER) "User" else "Assistant"
                "$roleName: ${msg.content}"
            }
        }

        return if (systemPrompt != null) {
            "Instruction:\n$systemPrompt\n\n$conversation"
        } else {
            conversation
        }
    }


}
