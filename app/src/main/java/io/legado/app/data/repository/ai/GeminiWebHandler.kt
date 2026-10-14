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
                var capturedCid: String? = null
                var capturedRcid: String? = null
                streamInternal(
                    request = request,
                    emitEvent = { event ->
                        if (event is AiStreamEvent.Content) {
                            fullText.append(event.text)
                        }
                    },
                    onMetadataCaptured = { cid, rcid ->
                        capturedCid = cid
                        capturedRcid = rcid
                    }
                )
                val text = fullText.toString().trim()
                if (text.isBlank()) {
                    throw Exception("Gemini Web returned empty response")
                }
                AiGenerateResponse(
                    text = text,
                    rawBody = text,
                    conversationId = capturedCid,
                    responseChoiceId = capturedRcid,
                )
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
                        id = "gemini-web-3.5-flash-lite",
                        name = "3.5 Flash-Lite (Câu trả lời nhanh nhất · Free)",
                        contextWindow = 1_000_000,
                        maxOutputTokens = 16_384,
                    ),
                    AiAvailableModel(
                        id = "gemini-web-3.8-flash",
                        name = "3.8 Flash (Trợ giúp toàn diện)",
                        contextWindow = 1_000_000,
                        maxOutputTokens = 32_768,
                    ),
                    AiAvailableModel(
                        id = "gemini-web-3.1-pro",
                        name = "3.1 Pro (Suy luận nâng cao)",
                        contextWindow = 1_000_000,
                        maxOutputTokens = 64_000,
                    ),
                    AiAvailableModel(
                        id = "gemini-web-4-argon",
                        name = "4 Argon (Nhiệm vụ phức tạp · Tư duy: Tự động)",
                        contextWindow = 1_000_000,
                        maxOutputTokens = 64_000,
                    ),
                    AiAvailableModel(
                        id = "gemini-web-4-argon-low",
                        name = "4 Argon (Cấp độ tư duy: Thấp)",
                        contextWindow = 1_000_000,
                        maxOutputTokens = 64_000,
                    ),
                    AiAvailableModel(
                        id = "gemini-web-4-argon-medium",
                        name = "4 Argon (Cấp độ tư duy: Trung bình)",
                        contextWindow = 1_000_000,
                        maxOutputTokens = 64_000,
                    ),
                    AiAvailableModel(
                        id = "gemini-web-4-argon-high",
                        name = "4 Argon (Cấp độ tư duy: Cao)",
                        contextWindow = 1_000_000,
                        maxOutputTokens = 64_000,
                    ),
                )
            }
        }

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
        onMetadataCaptured: ((String?, String?) -> Unit)? = null,
    ) {
        val apiKey = request.model.provider.apiKey
        val sessionKey = request.routeSessionKey
        val multiTurnEnabled = request.providerMetadata["multiTurn"] != "false" &&
            !sessionKey.isNullOrBlank()

        var retryCount = 0
        val maxRetries = 1

        while (true) {
            try {
                executeStreamRequest(
                    request = request,
                    sessionKey = sessionKey,
                    multiTurnEnabled = multiTurnEnabled,
                    emitEvent = emitEvent,
                    onMetadataCaptured = onMetadataCaptured,
                )
                return
            } catch (error: Throwable) {
                val isAuthOrRpcError = error.message?.contains("RPC er", ignoreCase = true) == true ||
                    error.message?.contains("401", ignoreCase = true) == true ||
                    error.message?.contains("403", ignoreCase = true) == true ||
                    error.message?.contains("BardErrorInfo", ignoreCase = true) == true ||
                    error.message?.contains("hết hạn", ignoreCase = true) == true

                if (isAuthOrRpcError && retryCount < maxRetries) {
                    retryCount++
                    AppLog.put("Gemini Web: Gặp lỗi phiên (${error.message}), đang kích hoạt RotateCookies tự động làm mới...")
                    val rotatedSession = GeminiWebSessionManager.rotateSession(apiKey)
                    if (rotatedSession != null) {
                        AppLog.put("Gemini Web: Làm mới phiên thành công, tự động thử lại request...")
                        continue
                    }
                }

                // If error was related to an expired or invalid conversation ID, retry once stateless
                if (multiTurnEnabled && retryCount < maxRetries && !sessionKey.isNullOrBlank()) {
                    val isConversationError = error.message?.contains("conversation", ignoreCase = true) == true ||
                        error.message?.contains("Không trích xuất được phản hồi", ignoreCase = true) == true
                    if (isConversationError) {
                        retryCount++
                        AppLog.put("Gemini Web: Đặt lại conversation cho book $sessionKey và thử lại dạng stateless...")
                        GeminiWebConversationStore.remove(sessionKey)
                        continue
                    }
                }

                throw error
            }
        }
    }

    private suspend fun executeStreamRequest(
        request: AiGenerateRequest,
        sessionKey: String?,
        multiTurnEnabled: Boolean,
        emitEvent: suspend (AiStreamEvent) -> Unit,
        onMetadataCaptured: ((String?, String?) -> Unit)?,
    ) {
        val session = GeminiWebSessionManager.getSession(request.model.provider.apiKey)
        val prompt = buildFullPrompt(request)
        val reqId = (100000..999999).random()

        val conversationMeta = if (multiTurnEnabled && !sessionKey.isNullOrBlank()) {
            GeminiWebConversationStore.get(sessionKey, request.model.modelId)
        } else null

        val (geminiModelCode, _) = resolveGeminiWebModelSpec(request.model.modelId)
        val fileId = request.providerMetadata["file_id"] ?: request.model.provider.runtimeMetadata["file_id"]
        val fileName = request.providerMetadata["file_name"] ?: request.model.provider.runtimeMetadata["file_name"] ?: "input_file_0.jpg"
        val fileAttachment: Pair<String, String>? = if (!fileId.isNullOrBlank()) Pair(fileId, fileName) else null
        val fReqPayload = buildFReqPayload(prompt, request.model.modelId, conversationMeta, fileAttachment)
        val formBodyString = "f.req=" + URLEncoder.encode(fReqPayload, StandardCharsets.UTF_8.name()) +
            if (session.snlm0e.isNotBlank()) "&at=" + URLEncoder.encode(session.snlm0e, StandardCharsets.UTF_8.name()) else ""

        val requestBody = formBodyString.toRequestBody("application/x-www-form-urlencoded;charset=utf-8".toMediaType())
        val bl = session.buildLabel.ifBlank { "boq_gemini-web-uiserver_20261012.15_p0" }
        val encodedBl = URLEncoder.encode(bl, StandardCharsets.UTF_8.name())

        val baseUrl = GeminiWebSessionManager.getGeminiUrl(
            "https://gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate",
            session.authUser
        )
        val url = buildString {
            append(baseUrl)
            append("?bl=").append(encodedBl)
            append("&hl=vi")
            append("&_reqid=").append(reqId)
            append("&rt=c")
            if (session.sessionId.isNotBlank()) {
                append("&f.sid=").append(URLEncoder.encode(session.sessionId, StandardCharsets.UTF_8.name()))
            }
            append("&source-path=").append(URLEncoder.encode("/app", StandardCharsets.UTF_8.name()))
        }

        val refererUrl = GeminiWebSessionManager.getGeminiUrl("https://gemini.google.com/app", session.authUser)
        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Accept-Language" to "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7",
            "X-Same-Domain" to "1",
            "Origin" to "https://gemini.google.com",
            "Referer" to refererUrl,
        )

        // Browser fidelity JSPB headers (G04)
        val generationId = UUID.randomUUID().toString().uppercase()
        headers.putAll(buildJspbHeaders(geminiModelCode, generationId))

        if (session.cookieHeader.isNotBlank()) {
            headers["Cookie"] = session.cookieHeader
        }
        val sapisid = session.cookies["SAPISID"] ?: session.cookies["__Secure-3PAPISID"]
        if (!sapisid.isNullOrBlank()) {
            headers["Authorization"] = generateSapisidHash(sapisid)
        }

        val response = aiOkHttpClient.newCallResponse {
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
        var capturedCid: String? = null
        var capturedRcid: String? = null

        try {
            while (!bodySource.exhausted()) {
                val line = bodySource.readUtf8Line() ?: break
                if (line.isBlank() || line.startsWith(")]}'")) continue

                // Check for explicit RPC error envelopes
                checkRpcError(line, request.model.provider.apiKey)

                // Lines in batchexecute response format:
                val extractedText = extractTextFromChunk(line)
                if (!extractedText.isNullOrBlank()) {
                    accumulator.onTextUpdate(extractedText)
                }

                // Extract conversation metadata if not yet extracted
                if (capturedCid == null) {
                    val (cid, rcid) = extractConversationMetadataFromChunk(line)
                    if (cid != null) {
                        capturedCid = cid
                        capturedRcid = rcid
                        onMetadataCaptured?.invoke(cid, rcid)
                    }
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

        // Save conversation metadata for subsequent chapter translation
        if (multiTurnEnabled && !sessionKey.isNullOrBlank() && !capturedCid.isNullOrBlank()) {
            val turnCount = (conversationMeta?.turnCount ?: 0) + 1
            GeminiWebConversationStore.put(
                sessionKey,
                GeminiWebConversationMetadata(
                    cid = capturedCid,
                    rid = capturedRcid ?: "",
                    rcid = capturedRcid ?: "",
                    model = request.model.modelId,
                    updatedAt = System.currentTimeMillis(),
                    turnCount = turnCount,
                )
            )
        }
    }

    internal fun buildJspbHeaders(
        modelNumber: Int,
        generationId: String
    ): Map<String, String> {
        val modelHeader = arrayOfNulls<Any?>(17)
        modelHeader[0] = 1
        modelHeader[7] = 0
        modelHeader[8] = listOf(4, 5, 6, 8)
        modelHeader[11] = 1
        modelHeader[14] = modelNumber
        modelHeader[15] = 1
        modelHeader[16] = generationId

        return mapOf(
            "x-goog-ext-525001261-jspb" to COMPACT_GSON.toJson(modelHeader),
            "x-goog-ext-525005358-jspb" to COMPACT_GSON.toJson(listOf(UUID.randomUUID().toString(), 1)),
            "x-goog-ext-73010989-jspb" to "[0]",
            "x-goog-ext-73010990-jspb" to "[0,0,0]",
        )
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

    internal fun resolveGeminiWebModelSpec(modelId: String): Pair<Int, Int> {
        val raw = modelId.lowercase()
        val normalized = when {
            raw.contains("3.5") || raw.contains("flash-lite") || raw == "gemini-web-flash-lite" -> "3.5-flash-lite"
            raw.contains("3.8") || raw.contains("flash") || raw == "gemini-web-default" -> "3.8-flash"
            raw.contains("3.1") || raw.contains("pro") || raw == "gemini-web-pro" -> "3.1-pro"
            raw.contains("4") || raw.contains("argon") || raw.contains("thinking") -> {
                when {
                    raw.contains("low") -> "4-argon-low"
                    raw.contains("medium") || raw.contains("med") -> "4-argon-medium"
                    raw.contains("high") -> "4-argon-high"
                    else -> "4-argon"
                }
            }
            else -> "3.8-flash"
        }

        return when (normalized) {
            "3.5-flash-lite" -> Pair(6, 4)
            "3.8-flash" -> Pair(1, 4)
            "3.1-pro" -> Pair(3, 4)
            "4-argon-low" -> Pair(2, 1)
            "4-argon-medium" -> Pair(2, 2)
            "4-argon-high" -> Pair(2, 3)
            "4-argon" -> Pair(2, 0)
            else -> Pair(1, 4)
        }
    }

    private fun buildFullPrompt(request: AiGenerateRequest): String {
        val rawSystemPrompt = request.messages
            .filter { it.role == AiMessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .takeIf { it.isNotBlank() }

        val nonSystem = request.messages.filter { it.role != AiMessageRole.SYSTEM }
        val rawConversation = if (nonSystem.size == 1 && nonSystem.first().role == AiMessageRole.USER) {
            nonSystem.first().content
        } else {
            nonSystem.joinToString("\n\n") { msg ->
                val roleName = if (msg.role == AiMessageRole.USER) "User" else "Assistant"
                "$roleName: ${msg.content}"
            }
        }

        // Skill extraction: Check for [SKILL: ...] or @Skill("...") in system prompt or user conversation
        val skillRegex = Regex("""(?:\[SKILL:\s*([^\]]+)\]|@Skill\(\s*["']([^"']+)["']\s*\))""", RegexOption.IGNORE_CASE)
        val matchedSkill = skillRegex.find(rawSystemPrompt.orEmpty())?.groupValues?.let { it.getOrNull(1)?.ifBlank { null } ?: it.getOrNull(2) }
            ?: skillRegex.find(rawConversation)?.groupValues?.let { it.getOrNull(1)?.ifBlank { null } ?: it.getOrNull(2) }

        val skillActivationHeader = if (!matchedSkill.isNullOrBlank()) {
            val skillName = matchedSkill.trim()
            """<gemini_skill_activation>
[Kích hoạt Kỹ năng: "$skillName"]
Hãy sử dụng Kỹ năng "$skillName" đã được thiết lập của tôi và tuân thủ nghiêm ngặt toàn bộ quy tắc dịch thuật, văn phong, đại từ xưng hô cùng định dạng đầu ra trong Kỹ năng đó.
</gemini_skill_activation>"""
        } else {
            null
        }

        val cleanedSystemPrompt = rawSystemPrompt?.replace(skillRegex, "")?.trim()
        val cleanedConversation = rawConversation.replace(skillRegex, "").trim()

        // Model-aware instruction tuning for Gemini Web variants
        val modelId = request.model.modelId.lowercase()
        val modelDirective = when {
            modelId.contains("argon") || modelId.contains("thinking") ->
                "Hãy suy nghĩ cẩn thận từng bước và đặt toàn bộ phần phân tích suy nghĩ trong cặp thẻ <thought>...</thought> trước khi đưa ra câu trả lời chính thức."
            modelId.contains("lite") -> "Hãy phản hồi cực nhanh, ngắn gọn và tập trung chính xác vào yêu cầu."
            modelId.contains("flash") -> "Hãy phản hồi nhanh, cô đọng và đi thẳng vào nội dung chính."
            modelId.contains("pro") -> "Hãy đưa ra câu trả lời chuyên sâu, đầy đủ và chi tiết."
            else -> null
        }

        val fullSystemParts = listOfNotNull(skillActivationHeader, cleanedSystemPrompt, modelDirective)
        val fullSystem = fullSystemParts.joinToString("\n\n").takeIf { it.isNotBlank() }

        return if (fullSystem != null) {
            "Instruction:\n$fullSystem\n\n$cleanedConversation"
        } else {
            cleanedConversation
        }
    }

    internal fun buildFReqPayload(
        prompt: String,
        modelId: String = "",
        conversationMeta: GeminiWebConversationMetadata? = null,
        fileAttachment: Pair<String, String>? = null,
    ): String {
        val (geminiModelCode, thinkMode) = resolveGeminiWebModelSpec(modelId)

        val innerArray = JsonArray()
        for (i in 0 until 81) {
            innerArray.add(JsonNull.INSTANCE)
        }

        val fileAttachmentArray = if (fileAttachment != null && fileAttachment.first.isNotBlank()) {
            JsonArray().apply {
                add(JsonArray().apply {
                    add(JsonArray().apply {
                        add(fileAttachment.first)
                    })
                    add(fileAttachment.second.ifBlank { "input_file_0.jpg" })
                })
            }
        } else {
            JsonNull.INSTANCE
        }

        innerArray.set(0, JsonArray().apply {
            add(prompt)
            add(0)
            add(JsonNull.INSTANCE)
            add(fileAttachmentArray)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(0)
        })
        innerArray.set(1, JsonArray().apply { add("vi") })
        innerArray.set(2, JsonArray().apply {
            if (conversationMeta != null && conversationMeta.cid.isNotBlank()) {
                add(conversationMeta.cid)
                add(conversationMeta.rid)
                add(conversationMeta.rcid)
            } else {
                add("")
                add("")
                add("")
            }
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add(JsonNull.INSTANCE)
            add("")
        })
        innerArray.set(6, JsonArray().apply { add(1) })
        innerArray.set(7, JsonPrimitive(1))
        innerArray.set(10, JsonPrimitive(1))
        innerArray.set(11, JsonPrimitive(0))
        innerArray.set(17, JsonArray().apply {
            add(JsonArray().apply { add(thinkMode) })
        })
        innerArray.set(18, JsonPrimitive(0))
        innerArray.set(27, JsonPrimitive(1))
        innerArray.set(30, JsonArray().apply { add(4) })
        innerArray.set(41, JsonArray().apply { add(1) })
        innerArray.set(45, JsonPrimitive(1)) // isTemporary flag
        innerArray.set(53, JsonPrimitive(0))
        innerArray.set(59, JsonPrimitive(UUID.randomUUID().toString().uppercase()))
        innerArray.set(61, JsonArray())
        innerArray.set(68, JsonPrimitive(1))
        innerArray.set(79, JsonPrimitive(geminiModelCode))
        innerArray.set(80, JsonPrimitive(1)) // 81st element

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

    internal fun extractConversationMetadataFromChunk(line: String): Pair<String?, String?> {
        val trimmed = line.trim()
        if (!trimmed.startsWith("[")) return Pair(null, null)
        return runCatching {
            val root = COMPACT_GSON.fromJson(trimmed, JsonElement::class.java)
            if (!root.isJsonArray) return@runCatching Pair(null, null)
            val rootArray = root.asJsonArray

            for (elem in rootArray) {
                if (elem.isJsonArray) {
                    val rpcItem = elem.asJsonArray
                    if (rpcItem.size() > 2 &&
                        rpcItem[0].isJsonPrimitive &&
                        rpcItem[0].asString == "wrb.fr" &&
                        rpcItem[2].isJsonPrimitive
                    ) {
                        val innerJson = rpcItem[2].asString
                        val meta = parseConversationMetadataFromInnerJson(innerJson)
                        if (meta.first != null) return@runCatching meta
                    }
                }
            }
            Pair(null, null)
        }.getOrDefault(Pair(null, null))
    }

    internal fun parseConversationMetadataFromInnerJson(innerJson: String): Pair<String?, String?> {
        val trimmed = innerJson.trim()
        if (!trimmed.startsWith("[")) return Pair(null, null)
        return runCatching {
            val inner = COMPACT_GSON.fromJson(trimmed, JsonElement::class.java)
            if (!inner.isJsonArray) return@runCatching Pair(null, null)
            val arr = inner.asJsonArray

            // 1. cid from arr[1]
            val cid = when {
                arr.size() > 1 && arr[1].isJsonPrimitive -> arr[1].asString.takeIf { it.isNotBlank() }
                arr.size() > 1 && arr[1].isJsonArray && arr[1].asJsonArray.size() > 0 ->
                    arr[1].asJsonArray[0].takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                else -> null
            }

            // 2. rcid from arr[4][0][0]
            var rcid: String? = null
            if (arr.size() > 4 && arr[4].isJsonArray) {
                val candidates = arr[4].asJsonArray
                if (candidates.size() > 0 && candidates[0].isJsonArray) {
                    val cand0 = candidates[0].asJsonArray
                    if (cand0.size() > 0 && cand0[0].isJsonPrimitive) {
                        rcid = cand0[0].asString.takeIf { it.isNotBlank() }
                    }
                }
            }

            Pair(cid, rcid)
        }.getOrDefault(Pair(null, null))
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

            // Newer StreamGenerate format: payload[2] is a map {"7": ["text"], "44": true}
            if (innerArray.size() > 2 && innerArray[2].isJsonObject) {
                val fields = innerArray[2].asJsonObject
                val textField = fields.getAsJsonArray("7")
                if (textField != null && textField.size() > 0 && textField[0].isJsonPrimitive) {
                    val text = textField[0].asString
                    if (text.isNotBlank()) return@runCatching text
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

            // Look for <thought>, ```thought, or Google native <ctrl94>thought / <ctrl95>
            val thoughtTag = when {
                cleaned.contains("<thought>") -> "<thought>" to "</thought>"
                cleaned.contains("```thought") -> "```thought" to "```"
                cleaned.contains("<ctrl94>thought") -> "<ctrl94>thought" to "<ctrl95>"
                cleaned.contains("<ctrl94>") -> "<ctrl94>" to "<ctrl95>"
                else -> null
            }

            if (thoughtTag != null) {
                val (openTag, closeTag) = thoughtTag
                val thoughtStartIndex = cleaned.indexOf(openTag)
                val thoughtContentStart = thoughtStartIndex + openTag.length
                val thoughtEndIndex = cleaned.indexOf(closeTag, thoughtContentStart)

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

                    val contentAfterThought = cleaned.substring(thoughtEndIndex + closeTag.length).trimStart('\r', '\n')
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
