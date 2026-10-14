package io.legado.app.data.repository.ai

import android.webkit.CookieManager
import com.google.gson.JsonObject
import io.legado.app.constant.AppLog
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

data class GeminiWebSession(
    val cookies: Map<String, String>,
    val snlm0e: String,
    val cookieHeader: String,
    val buildLabel: String = "boq_gemini-web-uiserver_20261012.15_p0",
    val authUser: String = "",
    val sessionId: String = "",
    val pushId: String = "feeds/mcudyrk2a4khkz",
    val fetchedAt: Long = System.currentTimeMillis(),
)

data class GeminiWebConversationMetadata(
    val cid: String,
    val rid: String = "",
    val rcid: String = "",
    val model: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val turnCount: Int = 1,
)

object GeminiWebConversationStore {
    private const val MAX_CONVERSATIONS = 50
    private const val MAX_TURNS_PER_CONVERSATION = 30
    private const val CONVERSATION_TTL_MS = 4 * 60 * 60 * 1000L // 4 hours

    private val conversations = ConcurrentHashMap<String, GeminiWebConversationMetadata>()

    fun get(sessionKey: String, currentModelId: String): GeminiWebConversationMetadata? {
        val meta = conversations[sessionKey] ?: return null
        val now = System.currentTimeMillis()

        if (now - meta.updatedAt > CONVERSATION_TTL_MS) {
            conversations.remove(sessionKey)
            return null
        }
        if (meta.turnCount >= MAX_TURNS_PER_CONVERSATION) {
            conversations.remove(sessionKey)
            return null
        }
        if (meta.model.isNotBlank() && meta.model != currentModelId) {
            conversations.remove(sessionKey)
            return null
        }
        return meta
    }

    fun put(sessionKey: String, meta: GeminiWebConversationMetadata) {
        if (conversations.size >= MAX_CONVERSATIONS && !conversations.containsKey(sessionKey)) {
            val oldestKey = conversations.entries.minByOrNull { it.value.updatedAt }?.key
            if (oldestKey != null) conversations.remove(oldestKey)
        }
        conversations[sessionKey] = meta
    }

    fun remove(sessionKey: String) {
        conversations.remove(sessionKey)
    }

    fun clearAll() {
        conversations.clear()
    }
}

object GeminiWebSessionManager {

    private const val GEMINI_BASE_URL = "https://gemini.google.com"
    private const val GEMINI_APP_URL = "https://gemini.google.com/app"
    private const val DEFAULT_BUILD_LABEL = "boq_gemini-web-uiserver_20261012.15_p0"
    private const val GOOGLE_ROTATE_COOKIES_URL = "https://accounts.google.com/RotateCookies"
    private const val ROTATE_COOKIES_PAYLOAD = """[000,"-0000000000000000000"]"""

    private val SNLM0E_REGEXES = listOf(
        Regex(""""SNlM0e":"([^"]+)""""),
        Regex("""'SNlM0e':\s*'([^']+)'"""),
        Regex("""\\\"SNlM0e\\\":\\\"([^\\\"]+)\\\""""),
        Regex("""["']?SNlM0e["']?\s*[:=]\s*["']([^"']+)["']"""),
        Regex("""\["SNlM0e",\s*null,\s*"([^"]+)"\]"""),
        Regex("""data-at="([^"]+)""""),
    )
    private val BL_REGEXES = listOf(
        Regex(""""cfb2h":"([^"]+)""""),
        Regex("""'cfb2h':\s*'([^']+)'"""),
        Regex("""["']?cfb2h["']?\s*[:=]\s*["']([^"']+)["']"""),
        Regex("""boq_gemini-web-uiserver_[0-9.]+_p\d+"""),
        Regex("""boq_assistant-bard-web-server_[0-9.]+_p\d+"""),
    )
    private val SESSION_ID_REGEXES = listOf(
        Regex(""""FdrFJe":"([^"]+)""""),
        Regex("""'FdrFJe':\s*'([^']+)'"""),
        Regex("""\["FdrFJe",\s*null,\s*"([^"]+)"\]"""),
    )
    private val PUSH_ID_REGEXES = listOf(
        Regex(""""qKIAYe":"([^"]+)""""),
        Regex("""'qKIAYe':\s*'([^']+)'"""),
    )

    private const val MAX_CACHE_ENTRIES = 20
    private const val DEFAULT_KEY = "__default_webkit_cookie__"

    private fun normalizeKey(credential: String?): String =
        credential?.trim().orEmpty().ifBlank { DEFAULT_KEY }

    private val mutex = Mutex()
    private val sessionCache = ConcurrentHashMap<String, GeminiWebSession>()

    fun getGeminiUrl(endpoint: String, authUser: String): String {
        if (authUser.isBlank() || authUser == "0") return endpoint
        return endpoint.replace("https://gemini.google.com/", "https://gemini.google.com/u/$authUser/")
    }

    suspend fun getSession(explicitCredential: String? = null): GeminiWebSession =
        withContext(Dispatchers.IO) {
            val key = normalizeKey(explicitCredential)
            val authUser = resolveAuthUser(explicitCredential)
            mutex.withLock {
                val current = sessionCache[key]
                val now = System.currentTimeMillis()
                // Cache valid for 30 minutes
                if (current != null && (now - current.fetchedAt) < 30 * 60 * 1000L) {
                    return@withLock current
                }

                // Support bundled JSON payload { "cookie": "...", "snlm0e": "...", "bl": "..." } with case-insensitive / alias keys
                val rawTrimmed = explicitCredential?.trim().orEmpty()
                if (rawTrimmed.startsWith("{") && rawTrimmed.endsWith("}")) {
                    val parsed = runCatching { GSON.fromJson(rawTrimmed, JsonObject::class.java) }.getOrNull()
                    if (parsed != null) {
                        val jsonCookie = extractJsonField(parsed, "cookie", "cookies")
                        val jsonSnlm0e = extractJsonField(parsed, "snlm0e", "SNlM0e", "at", "token")
                        val jsonBl = extractJsonField(parsed, "bl", "cfb2h", "buildLabel", "build_label")
                        val jsonAuthUser = extractJsonField(parsed, "authUser", "auth_user", "userIndex", "u").ifBlank { authUser }
                        val jsonSessionId = extractJsonField(parsed, "sessionId", "session_id", "FdrFJe")
                        val jsonPushId = extractJsonField(parsed, "pushId", "push_id", "qKIAYe").ifBlank { "feeds/mcudyrk2a4khkz" }

                        if (jsonCookie.isNotBlank() && jsonSnlm0e.isNotBlank()) {
                            val cookies = parseCookies(jsonCookie)
                            val session = GeminiWebSession(
                                cookies = cookies,
                                snlm0e = jsonSnlm0e,
                                cookieHeader = jsonCookie,
                                buildLabel = jsonBl.ifBlank { DEFAULT_BUILD_LABEL },
                                authUser = jsonAuthUser,
                                sessionId = jsonSessionId,
                                pushId = jsonPushId,
                                fetchedAt = now,
                            )
                            putSession(key, session)
                            return@withLock session
                        }
                    }
                }

                var cookieHeader = resolveCookieHeader(explicitCredential)
                var cookies = if (cookieHeader.isNotBlank()) parseCookies(cookieHeader) else emptyMap()

                // Auto-Rotate: If only __Secure-1PSID is provided, or if __Secure-1PSIDTS is missing, try rotation first
                if (cookies.containsKey("__Secure-1PSID") && !cookies.containsKey("__Secure-1PSIDTS")) {
                    AppLog.put("Gemini Web: Phát hiện thiếu __Secure-1PSIDTS, đang tự động gọi RotateCookies...")
                    val (rotatedHeader, newPsidts) = rotateCookies(cookieHeader)
                    if (!newPsidts.isNullOrBlank()) {
                        cookieHeader = rotatedHeader
                        cookies = parseCookies(cookieHeader)
                        AppLog.put("Gemini Web: Tự động nhận __Secure-1PSIDTS thành công.")
                    }
                }

                val sessionData = if (cookieHeader.isNotBlank()) {
                    runCatching { fetchSessionData(cookieHeader, authUser) }.getOrDefault(SessionInitData())
                } else {
                    SessionInitData()
                }

                val resolvedSnlm0e = sessionData.snlm0e
                    ?: current?.snlm0e
                    ?: ""

                val resolvedBl = sessionData.buildLabel
                    ?: current?.buildLabel
                    ?: DEFAULT_BUILD_LABEL

                val session = GeminiWebSession(
                    cookies = cookies,
                    snlm0e = resolvedSnlm0e,
                    cookieHeader = cookieHeader,
                    buildLabel = resolvedBl,
                    authUser = authUser,
                    sessionId = sessionData.sessionId ?: current?.sessionId ?: "",
                    pushId = sessionData.pushId ?: current?.pushId ?: "feeds/mcudyrk2a4khkz",
                    fetchedAt = now,
                )
                putSession(key, session)
                session
            }
        }

    private data class SessionInitData(
        val snlm0e: String? = null,
        val buildLabel: String? = null,
        val sessionId: String? = null,
        val pushId: String? = null,
    )

    suspend fun rotateCookies(cookieHeader: String): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val mediaType = "application/json;charset=utf-8".toMediaType()
                val requestBody = ROTATE_COOKIES_PAYLOAD.toRequestBody(mediaType)
                val response = aiOkHttpClient.newCallResponse {
                    url(GOOGLE_ROTATE_COOKIES_URL)
                    post(requestBody)
                    addHeaders(
                        mapOf(
                            "Content-Type" to "application/json",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                            "Cookie" to cookieHeader,
                        )
                    )
                }

                if (!response.isSuccessful) {
                    AppLog.put("RotateCookies failed HTTP ${response.code}: ${response.message}")
                    return@runCatching Pair(cookieHeader, null)
                }

                val setCookieHeaders = response.headers("Set-Cookie")
                var newPsidts: String? = null
                val updatedCookies = parseCookies(cookieHeader).toMutableMap()

                for (header in setCookieHeaders) {
                    val cookiePair = header.substringBefore(";").trim()
                    val eqIndex = cookiePair.indexOf('=')
                    if (eqIndex > 0) {
                        val name = cookiePair.substring(0, eqIndex).trim()
                        val value = cookiePair.substring(eqIndex + 1).trim()
                        updatedCookies[name] = value
                        if (name == "__Secure-1PSIDTS") {
                            newPsidts = value
                        }
                    }
                }

                val mergedCookieHeader = updatedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                Pair(mergedCookieHeader, newPsidts)
            }.getOrDefault(Pair(cookieHeader, null))
        }

    suspend fun rotateSession(explicitCredential: String? = null): GeminiWebSession? =
        withContext(Dispatchers.IO) {
            val key = normalizeKey(explicitCredential)
            mutex.withLock {
                val current = sessionCache[key]
                val baseCookie = current?.cookieHeader ?: resolveCookieHeader(explicitCredential)
                if (baseCookie.isBlank()) return@withLock null

                val (newCookieHeader, newPsidts) = rotateCookies(baseCookie)
                if (newPsidts.isNullOrBlank() && newCookieHeader == baseCookie) {
                    return@withLock null
                }

                val cookies = parseCookies(newCookieHeader)
                val authUser = resolveAuthUser(explicitCredential)
                val sessionData = runCatching { fetchSessionData(newCookieHeader, authUser) }.getOrDefault(SessionInitData())

                val newSession = GeminiWebSession(
                    cookies = cookies,
                    snlm0e = sessionData.snlm0e ?: current?.snlm0e ?: "",
                    cookieHeader = newCookieHeader,
                    buildLabel = sessionData.buildLabel ?: current?.buildLabel ?: DEFAULT_BUILD_LABEL,
                    authUser = authUser,
                    sessionId = sessionData.sessionId ?: current?.sessionId ?: "",
                    pushId = sessionData.pushId ?: current?.pushId ?: "feeds/mcudyrk2a4khkz",
                    fetchedAt = System.currentTimeMillis(),
                )
                putSession(key, newSession)
                GeminiWebConversationStore.clearAll()
                newSession
            }
        }

    private fun putSession(key: String, session: GeminiWebSession) {
        if (sessionCache.size >= MAX_CACHE_ENTRIES && !sessionCache.containsKey(key)) {
            val oldestKey = sessionCache.entries.minByOrNull { it.value.fetchedAt }?.key
            if (oldestKey != null) sessionCache.remove(oldestKey)
        }
        sessionCache[key] = session
    }

    fun invalidateSession(explicitCredential: String? = null) {
        if (explicitCredential == null) {
            sessionCache.clear()
        } else {
            sessionCache.remove(normalizeKey(explicitCredential))
        }
        GeminiWebConversationStore.clearAll()
    }

    internal fun resolveAuthUser(explicitCredential: String?): String {
        val raw = explicitCredential?.trim().orEmpty()
        if (raw.startsWith("{") && raw.endsWith("}")) {
            val parsed = runCatching { GSON.fromJson(raw, JsonObject::class.java) }.getOrNull()
            if (parsed != null) {
                val user = extractJsonField(parsed, "authUser", "auth_user", "userIndex", "u")
                if (user.isNotBlank()) return user
            }
        }
        val urlMatch = Regex("""gemini\.google\.com/u/(\d+)""").find(raw)
        if (urlMatch != null) {
            return urlMatch.groupValues[1]
        }
        return ""
    }

    private fun extractJsonField(json: JsonObject, vararg keys: String): String {
        for (key in keys) {
            val elem = json.get(key)
            if (elem != null && !elem.isJsonNull && elem.isJsonPrimitive) {
                val str = elem.asString.trim()
                if (str.isNotBlank()) return str
            }
        }
        for (entry in json.entrySet()) {
            for (key in keys) {
                if (entry.key.equals(key, ignoreCase = true)) {
                    val elem = entry.value
                    if (elem != null && !elem.isJsonNull && elem.isJsonPrimitive) {
                        val str = elem.asString.trim()
                        if (str.isNotBlank()) return str
                    }
                }
            }
        }
        return ""
    }

    private fun resolveCookieHeader(explicitCredential: String?): String {
        val raw = explicitCredential?.trim().orEmpty()
        // 1. Support Cookie-Editor JSON array format: [{"name":"...","value":"..."}, ...]
        if (raw.startsWith("[") && raw.endsWith("]")) {
            val parsedCookies = runCatching {
                val array = GSON.fromJson(raw, com.google.gson.JsonArray::class.java)
                val list = mutableListOf<String>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    val name = obj.get("name")?.asString
                    val value = obj.get("value")?.asString
                    if (!name.isNullOrBlank() && value != null) {
                        list.add("$name=$value")
                    }
                }
                list.joinToString("; ")
            }.getOrNull()
            if (!parsedCookies.isNullOrBlank()) return parsedCookies
        }

        // 2. Bundled JSON object { "cookie": "..." }
        if (raw.startsWith("{") && raw.endsWith("}")) {
            val parsed = runCatching { GSON.fromJson(raw, JsonObject::class.java) }.getOrNull()
            if (parsed != null) {
                val jsonCookie = extractJsonField(parsed, "cookie", "cookies")
                if (jsonCookie.isNotBlank()) return jsonCookie.trim()
            }
        }

        // 3. Raw cookie string
        if (raw.isNotBlank() && (raw.contains("=") || raw.contains(";"))) {
            return raw
        }

        // 4. Raw PSID value (when credential is provided as direct PSID string)
        if (raw.isNotBlank()) {
            return "__Secure-1PSID=$raw"
        }

        // 5. WebKit CookieManager (fallback only when explicit credential is blank, e.g. single account guest mode)
        val webkitCookie = runCatching {
            CookieManager.getInstance().getCookie(GEMINI_BASE_URL)
        }.getOrNull()
        if (!webkitCookie.isNullOrBlank()) {
            return webkitCookie
        }
        return ""
    }

    private fun parseCookies(cookieHeader: String): Map<String, String> {
        return cookieHeader.split(";")
            .mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .toMap()
    }

    private suspend fun fetchSessionData(cookieHeader: String, authUser: String = ""): SessionInitData {
        val appUrl = getGeminiUrl(GEMINI_APP_URL, authUser)
        val response = okHttpClient.newCallStrResponse {
            url(appUrl)
            addHeaders(
                mapOf(
                    "Cookie" to cookieHeader,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                    "Accept-Language" to "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7",
                )
            )
        }

        val code = response.code()
        if (code == 401 || code == 403) {
            throw Exception("Gemini Web từ chối truy cập (HTTP $code). Phiên Cookie đã hết hạn hoặc không hợp lệ.")
        }
        if (!response.isSuccessful()) {
            throw Exception("Không thể kết nối đến Gemini Web (HTTP $code: ${response.message()})")
        }

        val body = response.body ?: return SessionInitData()

        // Check if Google redirected to Google Accounts login page
        if (body.contains("accounts.google.com/ServiceLogin", ignoreCase = true) ||
            body.contains("accounts.google.com/InteractiveLogin", ignoreCase = true) ||
            body.contains("Sign in - Google Accounts", ignoreCase = true) ||
            (body.contains("identifierId") && body.contains("Email or phone"))
        ) {
            throw Exception(
                "Google đã chuyển hướng yêu cầu đến trang đăng nhập tài khoản (accounts.google.com). " +
                "Cookie của bạn đã hết hạn hoặc thiếu cookie __Secure-1PSIDTS. " +
                "👉 Vui lòng đăng nhập lại Google trên trình duyệt (Chrome/Edge), sau đó copy toàn bộ Cookie hoặc chạy lệnh trích xuất Console để lấy đầy đủ cả Cookie và Token."
            )
        }

        var snlm0e: String? = null
        for (regex in SNLM0E_REGEXES) {
            val found = regex.find(body)?.groupValues?.getOrNull(1)
            if (!found.isNullOrBlank()) {
                snlm0e = found
                break
            }
        }
        var bl: String? = null
        for (regex in BL_REGEXES) {
            val match = regex.find(body)
            if (match != null) {
                val groupVal = match.groupValues.getOrNull(1)
                bl = if (!groupVal.isNullOrBlank()) groupVal else match.value
                break
            }
        }
        var sessionId: String? = null
        for (regex in SESSION_ID_REGEXES) {
            val found = regex.find(body)?.groupValues?.getOrNull(1)
            if (!found.isNullOrBlank()) {
                sessionId = found
                break
            }
        }
        var pushId: String? = null
        for (regex in PUSH_ID_REGEXES) {
            val found = regex.find(body)?.groupValues?.getOrNull(1)
            if (!found.isNullOrBlank()) {
                pushId = found
                break
            }
        }

        return SessionInitData(
            snlm0e = snlm0e,
            buildLabel = bl,
            sessionId = sessionId,
            pushId = pushId,
        )
    }
}
