package io.legado.app.data.repository.ai

import androidx.annotation.Keep
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.legado.app.constant.AppLog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

class GeminiWebHandler : AiProtocolHandler {

    companion object {
        val COMPACT_GSON = com.google.gson.Gson()
    }

    override val protocols: Set<String> = setOf(AiProtocol.GEMINI_WEB)

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
                    throw Exception("Gemini Web returned empty response")
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
                        id = "gemini-web-default",
                        name = "Gemini Web (Default)",
                        contextWindow = 32_000,
                        maxOutputTokens = 8_192,
                    ),
                    AiAvailableModel(
                        id = "gemini-web-flash",
                        name = "Gemini Web (Flash)",
                        contextWindow = 1_000_000,
                        maxOutputTokens = 8_192,
                    ),
                    AiAvailableModel(
                        id = "gemini-web-pro",
                        name = "Gemini Web (Advanced)",
                        contextWindow = 2_000_000,
                        maxOutputTokens = 8_192,
                    ),
                    AiAvailableModel(
                        id = "gemini-web-thinking",
                        name = "Gemini Web (Thinking)",
                        contextWindow = 1_000_000,
                        maxOutputTokens = 8_192,
                    ),
                )
            }
        }

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        val session = GeminiWebSessionManager.getSession(request.model.provider.apiKey)
        val prompt = buildFullPrompt(request)
        val reqId = (100000..999999).random()

        val fReqPayload = buildFReqPayload(prompt, request.model.modelId)
        val formBodyString = "f.req=" + URLEncoder.encode(fReqPayload, StandardCharsets.UTF_8.name()) +
            if (session.snlm0e.isNotBlank()) "&at=" + URLEncoder.encode(session.snlm0e, StandardCharsets.UTF_8.name()) else ""

        val requestBody = formBodyString.toRequestBody("application/x-www-form-urlencoded;charset=utf-8".toMediaType())
        val bl = session.buildLabel.ifBlank { "boq_assistant-bard-web-server_20250220.08_p0" }
        val encodedBl = URLEncoder.encode(bl, StandardCharsets.UTF_8.name())
        val url = "https://gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate" +
            "?bl=$encodedBl&hl=vi&_reqid=$reqId&rt=c"

        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Accept-Language" to "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7",
            "X-Same-Domain" to "1",
            "Origin" to "https://gemini.google.com",
            "Referer" to "https://gemini.google.com/app",
        )
        if (session.cookieHeader.isNotBlank()) {
            headers["Cookie"] = session.cookieHeader
        }
        val sapisid = session.cookies["SAPISID"] ?: session.cookies["__Secure-3PAPISID"]
        if (!sapisid.isNullOrBlank()) {
            headers["Authorization"] = generateSapisidHash(sapisid)
        }

        val response = okHttpClient.newCallResponse {
            url(url)
            post(requestBody)
            addHeaders(headers)
        }

        if (!response.isSuccessful) {
            if (response.code == 401 || response.code == 403) {
                GeminiWebSessionManager.invalidateSession(request.model.provider.apiKey)
            }
            throw Exception("Gemini Web HTTP ${response.code}: ${response.message}")
        }

        val bodySource = response.body.source()
        val accumulator = GeminiStreamAccumulator(emitEvent)

        try {
            while (!bodySource.exhausted()) {
                val line = bodySource.readUtf8Line() ?: break
                if (line.isBlank() || line.startsWith(")]}'")) continue

                // Check for explicit RPC error envelopes
                checkRpcError(line, request.model.provider.apiKey)

                // Lines in batchexecute response format:
                // Length prefix line, followed by JSON payload containing [["wrb.fr", null, "JSON_STRING", ...]]
                val extractedText = extractTextFromChunk(line)
                if (!extractedText.isNullOrBlank()) {
                    accumulator.onTextUpdate(extractedText)
                }
            }
        } finally {
            response.close()
        }

        if (!accumulator.emittedAny) {
            throw Exception(
                "Không trích xuất được phản hồi từ Gemini Web. " +
                "Có thể phiên đăng nhập đã hết hạn hoặc cookie __Secure-1PSIDTS cần được làm mới. " +
                "Vui lòng lấy lại Cookie từ Chrome/Edge đã đăng nhập."
            )
        }
    }

    internal fun checkRpcError(line: String, credentialKey: String? = null) {
        val trimmed = line.trim()
        if (trimmed.startsWith("[[\"er\"") || trimmed.contains("[\"er\",") || trimmed.contains("\"er\",null,")) {
            GeminiWebSessionManager.invalidateSession(credentialKey)
            throw Exception("Google Gemini Web RPC Error: Phiên đăng nhập không hợp lệ hoặc đã hết hạn (RPC er). Vui lòng cập nhật Cookie.")
        }
        if (trimmed.contains("BardErrorInfo")) {
            val errorMatch = Regex("""BardErrorInfo\s*\[(\d+)\]""").find(trimmed)
            val code = errorMatch?.groupValues?.getOrNull(1) ?: "unknown"
            if (code == "1" || code == "2" || code == "3") {
                GeminiWebSessionManager.invalidateSession(credentialKey)
            }
            throw Exception("Google Gemini Web từ chối yêu cầu (BardErrorInfo [$code]). Có thể phiên đăng nhập đã hết hạn hoặc cookie cần làm mới.")
        }
        if (trimmed.contains("User prompt blocked", ignoreCase = true) || trimmed.contains("SAFETY_VIOLATION", ignoreCase = true)) {
            throw Exception("Yêu cầu bị chặn bởi bộ lọc an toàn của Gemini Web.")
        }
    }

    internal fun generateSapisidHash(sapisid: String): String {
        val ts = System.currentTimeMillis() / 1000L
        val input = "$ts $sapisid https://gemini.google.com"
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${ts}_$hex"
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

        // Model-aware instruction tuning for Gemini Web variants
        val modelId = request.model.modelId.lowercase()
        val modelDirective = when {
            modelId.contains("thinking") -> "Hãy suy nghĩ cẩn thận từng bước và đặt phần phân tích suy nghĩ trong cặp thẻ <thought>...</thought> trước khi trả lời."
            modelId.contains("flash") -> "Hãy phản hồi nhanh, cô đọng và đi thẳng vào nội dung chính."
            modelId.contains("pro") -> "Hãy đưa ra câu trả lời chuyên sâu, đầy đủ và chi tiết."
            else -> null
        }

        val fullSystem = listOfNotNull(systemPrompt, modelDirective).joinToString("\n\n").takeIf { it.isNotBlank() }

        return if (fullSystem != null) {
            "Instruction:\n$fullSystem\n\n$conversation"
        } else {
            conversation
        }
    }

    internal fun buildFReqPayload(prompt: String, modelId: String = ""): String {
        // Map model IDs to Google's MODE_CATEGORY and think_mode
        // 1=FAST, 2=THINKING, 3=PRO, 4=AUTO, 5=FAST_DYNAMIC_THINKING, 6=FLASH_LITE
        val idLower = modelId.lowercase()
        val (geminiModelCode, thinkMode) = when {
            idLower.contains("thinking") -> Pair(2, 0)
            idLower.contains("pro") -> Pair(3, 4)
            idLower.contains("lite") -> Pair(6, 4)
            else -> Pair(1, 4)
        }

        val innerArray = JsonArray()
        for (i in 0 until 80) {
            innerArray.add(JsonNull.INSTANCE)
        }

        innerArray.set(0, JsonArray().apply {
            add(prompt)
            add(0)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(0)
        })
        innerArray.set(1, JsonArray().apply { add("vi") })
        innerArray.set(2, JsonArray().apply {
            add("")
            add("")
            add("")
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add("")
        })
        innerArray.set(6, JsonArray().apply { add(0) })
        innerArray.set(7, JsonPrimitive(1))
        innerArray.set(10, JsonPrimitive(1))
        innerArray.set(11, JsonPrimitive(0))
        innerArray.set(17, JsonArray().apply {
            add(JsonArray().apply { add(thinkMode) })
        })
        innerArray.set(18, JsonPrimitive(0))
        innerArray.set(27, JsonPrimitive(1))
        innerArray.set(30, JsonArray().apply { add(4) })
        innerArray.set(41, JsonArray().apply { add(2) })
        innerArray.set(53, JsonPrimitive(0))
        innerArray.set(59, JsonPrimitive(UUID.randomUUID().toString()))
        innerArray.set(61, JsonArray())
        innerArray.set(68, JsonPrimitive(1))
        innerArray.set(79, JsonPrimitive(geminiModelCode))

        val outerArray = JsonArray().apply {
            add(JsonNull.INSTANCE)
            add(COMPACT_GSON.toJson(innerArray))
        }
        return COMPACT_GSON.toJson(outerArray)
    }

    internal fun extractTextFromChunk(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("[")) return null
        return runCatching {
            val root = COMPACT_GSON.fromJson(trimmed, JsonElement::class.java)
            if (!root.isJsonArray) return@runCatching null
            val rootArray = root.asJsonArray

            // Fast path: standard Google batchexecute wrb.fr payload
            for (elem in rootArray) {
                if (elem.isJsonArray) {
                    val rpcItem = elem.asJsonArray
                    if (rpcItem.size() > 2 &&
                        rpcItem[0].isJsonPrimitive &&
                        rpcItem[0].asString == "wrb.fr" &&
                        rpcItem[2].isJsonPrimitive
                    ) {
                        val innerJson = rpcItem[2].asString
                        val text = parseInnerJson(innerJson)
                        if (!text.isNullOrBlank()) return@runCatching text
                    }
                }
            }

            // Fallback path: deep recursive inspection
            findNestedText(rootArray)
        }.getOrNull()
    }

    internal fun parseInnerJson(innerJson: String): String? {
        val trimmed = innerJson.trim()
        if (!trimmed.startsWith("[")) return null
        return runCatching {
            val inner = COMPACT_GSON.fromJson(trimmed, JsonElement::class.java)
            if (!inner.isJsonArray) return@runCatching null
            val innerArray = inner.asJsonArray

            // In Google Gemini response envelope:
            // inner[4] is the list of candidates: [[ "rc_xxx", [ "text", ... ], ... ]]
            if (innerArray.size() > 4 && innerArray[4].isJsonArray) {
                val candidates = innerArray[4].asJsonArray
                if (candidates.size() > 0 && candidates[0].isJsonArray) {
                    val cand = candidates[0].asJsonArray
                    if (cand.size() > 1 && cand[1].isJsonArray) {
                        val parts = cand[1].asJsonArray
                        val textBuilder = StringBuilder()
                        for (part in parts) {
                            if (part.isJsonPrimitive) {
                                textBuilder.append(part.asString)
                            } else if (part.isJsonArray) {
                                for (sub in part.asJsonArray) {
                                    if (sub.isJsonPrimitive) textBuilder.append(sub.asString)
                                }
                            }
                        }
                        val text = textBuilder.toString()
                        if (text.isNotBlank()) return@runCatching text
                    }
                }
            }

            // If index 4 was not the candidate list, search all elements of innerArray
            extractCandidateText(innerArray)
        }.getOrNull()
    }

    private fun findNestedText(array: JsonArray): String? {
        for (elem in array) {
            if (elem.isJsonArray) {
                val found = findNestedText(elem.asJsonArray)
                if (!found.isNullOrBlank()) return found
            } else if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                val str = elem.asString.trim()
                if (str.startsWith("[") && str.endsWith("]")) {
                    val innerText = parseInnerJson(str)
                    if (!innerText.isNullOrBlank()) return innerText
                }
            }
        }
        return null
    }

    internal fun extractCandidateText(array: JsonArray): String? {
        for (elem in array) {
            if (elem.isJsonArray) {
                val sub = elem.asJsonArray
                // Direct candidate item: ["rc_xxx", ["text", ...], ...]
                if (sub.size() > 1 && sub[0].isJsonPrimitive && sub[0].asString.startsWith("rc_") && sub[1].isJsonArray) {
                    val textArray = sub[1].asJsonArray
                    val textParts = mutableListOf<String>()
                    for (part in textArray) {
                        if (part.isJsonPrimitive) textParts.add(part.asString)
                    }
                    val combined = textParts.joinToString("")
                    if (combined.isNotBlank()) return combined
                }
                // List of candidates: [ ["rc_xxx", ["text", ...]], ... ]
                if (sub.size() > 0 && sub[0].isJsonArray) {
                    val candidate = sub[0].asJsonArray
                    if (candidate.size() > 1 && candidate[1].isJsonArray) {
                        val textArray = candidate[1].asJsonArray
                        if (textArray.size() > 0) {
                            val textParts = mutableListOf<String>()
                            for (part in textArray) {
                                if (part.isJsonPrimitive) {
                                    textParts.add(part.asString)
                                } else if (part.isJsonArray) {
                                    val subArr = part.asJsonArray
                                    for (subItem in subArr) {
                                        if (subItem.isJsonPrimitive) textParts.add(subItem.asString)
                                    }
                                }
                            }
                            val combined = textParts.joinToString("")
                            if (combined.isNotBlank()) return combined
                        }
                    }
                }
                val candidateText = extractCandidateText(sub)
                if (!candidateText.isNullOrBlank()) return candidateText
            }
        }
        return null
    }

    internal fun cleanGeminiText(text: String): String {
        return text.replace(Regex("""```(?:python|javascript|text)\?code_(?:reference|stdout)&code_event_index=\d+\n.*?```\n?""", RegexOption.DOT_MATCHES_ALL), "")
    }

    private class GeminiStreamAccumulator(
        private val emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        private var lastEmittedThoughtLength = 0
        private var lastEmittedContentLength = 0
        var emittedAny = false
            private set

        suspend fun onTextUpdate(fullText: String) {
            val cleaned = cleanGeminiText(fullText)
            if (cleaned.isEmpty()) return
            emittedAny = true

            val thoughtStartIndex = cleaned.indexOf("<thought>")
            if (thoughtStartIndex != -1) {
                val thoughtContentStart = thoughtStartIndex + "<thought>".length
                val thoughtEndIndex = cleaned.indexOf("</thought>", thoughtContentStart)

                if (thoughtEndIndex == -1) {
                    // Thought is still streaming
                    val currentThought = cleaned.substring(thoughtContentStart)
                    if (currentThought.length > lastEmittedThoughtLength) {
                        val delta = currentThought.substring(lastEmittedThoughtLength)
                        lastEmittedThoughtLength = currentThought.length
                        emitEvent(AiStreamEvent.Reasoning(delta))
                    }
                } else {
                    // Thought has closed
                    val fullThought = cleaned.substring(thoughtContentStart, thoughtEndIndex)
                    if (fullThought.length > lastEmittedThoughtLength) {
                        val delta = fullThought.substring(lastEmittedThoughtLength)
                        lastEmittedThoughtLength = fullThought.length
                        emitEvent(AiStreamEvent.Reasoning(delta))
                    }

                    val contentAfterThought = cleaned.substring(thoughtEndIndex + "</thought>".length).trimStart('\r', '\n')
                    if (contentAfterThought.length > lastEmittedContentLength) {
                        val delta = contentAfterThought.substring(lastEmittedContentLength)
                        lastEmittedContentLength = contentAfterThought.length
                        emitEvent(AiStreamEvent.Content(delta))
                    }
                }
            } else {
                // Standard cumulative content without thought tag
                if (cleaned.length > lastEmittedContentLength) {
                    val delta = cleaned.substring(lastEmittedContentLength)
                    lastEmittedContentLength = cleaned.length
                    emitEvent(AiStreamEvent.Content(delta))
                }
            }
        }

        private fun cleanGeminiText(text: String): String {
            return text.replace(Regex("""```(?:python|javascript|text)\?code_(?:reference|stdout)&code_event_index=\d+\n.*?```\n?""", RegexOption.DOT_MATCHES_ALL), "")
        }
    }
}
