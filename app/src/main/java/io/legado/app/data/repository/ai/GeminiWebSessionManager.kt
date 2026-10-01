package io.legado.app.data.repository.ai

import android.webkit.CookieManager
import com.google.gson.JsonObject
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class GeminiWebSession(
    val cookies: Map<String, String>,
    val snlm0e: String,
    val cookieHeader: String,
    val buildLabel: String = "boq_assistant-bard-web-server_20250220.08_p0",
    val fetchedAt: Long = System.currentTimeMillis(),
)

object GeminiWebSessionManager {

    private const val GEMINI_BASE_URL = "https://gemini.google.com"
    private const val GEMINI_APP_URL = "https://gemini.google.com/app"
    private const val DEFAULT_BUILD_LABEL = "boq_assistant-bard-web-server_20250220.08_p0"

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
        Regex("""boq_assistant-bard-web-server_[0-9.]+_p\d+"""),
    )

    private const val MAX_CACHE_ENTRIES = 20
    private const val DEFAULT_KEY = "__default_webkit_cookie__"

    private fun normalizeKey(credential: String?): String =
        credential?.trim().orEmpty().ifBlank { DEFAULT_KEY }

    private val mutex = Mutex()
    private val sessionCache = ConcurrentHashMap<String, GeminiWebSession>()

    suspend fun getSession(explicitCredential: String? = null): GeminiWebSession =
        withContext(Dispatchers.IO) {
            val key = normalizeKey(explicitCredential)
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
                        if (jsonCookie.isNotBlank() && jsonSnlm0e.isNotBlank()) {
                            val cookies = parseCookies(jsonCookie)
                            val session = GeminiWebSession(
                                cookies = cookies,
                                snlm0e = jsonSnlm0e,
                                cookieHeader = jsonCookie,
                                buildLabel = jsonBl.ifBlank { DEFAULT_BUILD_LABEL },
                                fetchedAt = now,
                            )
                            putSession(key, session)
                            return@withLock session
                        }
                    }
                }

                val cookieHeader = resolveCookieHeader(explicitCredential)
                val cookies = if (cookieHeader.isNotBlank()) parseCookies(cookieHeader) else emptyMap()
                val (snlm0e, dynamicBl) = if (cookieHeader.isNotBlank()) {
                    runCatching { fetchSessionData(cookieHeader) }.getOrDefault(Pair(null, null))
                } else {
                    Pair(null, null)
                }

                val resolvedSnlm0e = snlm0e
                    ?: current?.snlm0e
                    ?: ""

                val resolvedBl = dynamicBl
                    ?: current?.buildLabel
                    ?: DEFAULT_BUILD_LABEL

                val session = GeminiWebSession(
                    cookies = cookies,
                    snlm0e = resolvedSnlm0e,
                    cookieHeader = cookieHeader,
                    buildLabel = resolvedBl,
                    fetchedAt = now,
                )
                putSession(key, session)
                session
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
    }

    private fun extractJsonField(json: JsonObject, vararg keys: String): String {
        for (key in keys) {
            val elem = json.get(key)
            if (elem != null && !elem.isJsonNull && elem.isJsonPrimitive) {
                val str = elem.asString.trim()
                if (str.isNotBlank()) return str
            }
        }
        // Also fallback to case-insensitive match on all member names
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

    private suspend fun fetchSessionData(cookieHeader: String): Pair<String?, String?> {
        val response = okHttpClient.newCallStrResponse {
            url(GEMINI_APP_URL)
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

        val body = response.body ?: return Pair(null, null)

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
        return Pair(snlm0e, bl)
    }
}
