package io.legado.app.data.repository.ai

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.coroutines.resume

data class ChatGptWebSession(
    val accessToken: String,
    val cookieHeader: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

object ChatGptWebSessionManager {

    private const val CHATGPT_BASE_URL = "https://chatgpt.com"
    private const val CHATGPT_SESSION_URL = "https://chatgpt.com/api/auth/session"

    // JWT regex matches header.payload.signature pattern
    private val JWT_REGEX = Regex("""\b(eyJ[a-zA-Z0-9_-]{15,}\.[a-zA-Z0-9_-]{15,}\.[a-zA-Z0-9_-]+)""")
    private val KEY_TOKEN_REGEX = Regex(""""(?:accessToken|token)"\s*:\s*"([^"]+)"""")
    private val LOOSE_JWT_REGEX = Regex("""(eyJ[a-zA-Z0-9_-]{15,}\.[a-zA-Z0-9_-]{15,}\.[a-zA-Z0-9_-]+)""")

    private val mutex = Mutex()
    @Volatile
    private var cachedSession: ChatGptWebSession? = null

    suspend fun resolveAccessToken(explicitCredential: String? = null): String =
        withContext(Dispatchers.IO) {
            val raw = explicitCredential?.trim().orEmpty()

            // 1. Direct JWT regex extraction (handles raw JWT, Bearer prefix, JSON with accessToken, DevTools copy, etc.)
            val matchedJwt = extractJwt(raw)
            if (matchedJwt != null) {
                return@withContext matchedJwt
            }

            mutex.withLock {
                val current = cachedSession
                val now = System.currentTimeMillis()
                // Cache token for 10 minutes
                if (current != null && (now - current.fetchedAt) < 10 * 60 * 1000L) {
                    return@withLock current.accessToken
                }

                val cookieHeader = resolveCookieHeader(explicitCredential)
                if (cookieHeader.isBlank()) {
                    error(
                        "Chưa có thông tin xác thực ChatGPT Web.\n" +
                        "👉 Vui lòng mở https://chatgpt.com/api/auth/session trên Chrome/Edge, chọn 'Chọn tất cả' -> 'Sao chép' rồi dán vào ô cấu hình."
                    )
                }

                // Try fast OkHttp fetch if cookies are provided
                val accessToken = fetchAccessTokenFromSession(cookieHeader)

                val finalToken = accessToken
                    ?: current?.accessToken
                    ?: error(
                        "Không thể lấy Access Token từ Cookie phiên ChatGPT Web (do Cloudflare bảo vệ hoặc cookie hết hạn).\n" +
                        "👉 Vui lòng mở https://chatgpt.com/api/auth/session trên trình duyệt máy bạn (Chrome/Edge), copy chuỗi accessToken (bắt đầu bằng 'eyJ...') hoặc toàn bộ JSON rồi dán vào ô cấu hình."
                    )

                ChatGptWebSession(
                    accessToken = finalToken,
                    cookieHeader = cookieHeader,
                    fetchedAt = now,
                ).also { cachedSession = it }

                finalToken
            }
        }

    fun invalidateSession() {
        cachedSession = null
    }

    fun extractJwt(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        // 1. Match "accessToken": "eyJ..." or "token": "eyJ..." key in partial or full JSON
        val keyMatch = KEY_TOKEN_REGEX.find(trimmed)
        if (keyMatch != null) {
            val candidate = keyMatch.groupValues[1].trim()
            val candidateJwt = JWT_REGEX.find(candidate)?.value
                ?: candidate.takeIf { it.startsWith("eyJ") && it.length > 50 && it.count { c -> c == '.' } >= 2 }
            if (candidateJwt != null) return candidateJwt
        }

        // 2. Check if wrapped in full JSON object
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val json = runCatching { GSON.fromJson(trimmed, JsonObject::class.java) }.getOrNull()
            val token = json?.get("accessToken")?.asString
                ?: json?.get("token")?.asString
            if (!token.isNullOrBlank()) {
                val subJwt = JWT_REGEX.find(token)?.value ?: token.takeIf { it.startsWith("eyJ") && it.length > 50 && it.count { c -> c == '.' } >= 2 }
                if (subJwt != null) return subJwt
            }
        }

        // 3. Regex match anywhere in text (handles Bearer eyJ..., raw eyJ...)
        val fullJwt = JWT_REGEX.find(trimmed)?.value
        if (fullJwt != null) return fullJwt

        // 4. Loose pattern fallback
        return LOOSE_JWT_REGEX.find(trimmed)?.value
    }

    fun isJwtTruncated(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (extractJwt(trimmed) != null) return false

        // Check if text looks like an incomplete JWT or contains accessToken with truncated value
        val hasKey = trimmed.contains("accessToken", ignoreCase = true) || trimmed.contains("\"token\"", ignoreCase = true)
        val hasEyJ = trimmed.contains("eyJ")
        if (hasKey || hasEyJ) {
            val candidate = if (hasKey) {
                KEY_TOKEN_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
                    ?: Regex("""eyJ[a-zA-Z0-9_.-]*""").find(trimmed)?.value
            } else {
                Regex("""eyJ[a-zA-Z0-9_.-]*""").find(trimmed)?.value
            }
            if (candidate != null && (candidate.count { it == '.' } < 2 || candidate.length < 50)) {
                return true
            }
        }
        return false
    }

    private fun resolveCookieHeader(explicitCredential: String?): String {
        val raw = explicitCredential?.trim().orEmpty()
        if (raw.isBlank()) {
            return runCatching {
                CookieManager.getInstance().getCookie(CHATGPT_BASE_URL)
            }.getOrNull().orEmpty()
        }

        // 1. Check Cookie-Editor JSON array format: [{"name":"...","value":"..."}, ...]
        if (raw.startsWith("[") && raw.endsWith("]")) {
            val parsedCookies = runCatching {
                val array = GSON.fromJson(raw, JsonArray::class.java)
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

        // 2. JSON object with "cookie" field
        if (raw.startsWith("{") && raw.endsWith("}")) {
            val json = runCatching { GSON.fromJson(raw, JsonObject::class.java) }.getOrNull()
            val cookie = json?.get("cookie")?.asString
            if (!cookie.isNullOrBlank()) return cookie
        }

        // 3. Raw cookie string key=value;
        if (raw.contains("=") || raw.contains(";")) {
            return raw
        }

        // 4. WebKit cookie manager fallback
        val webkitCookie = runCatching {
            CookieManager.getInstance().getCookie(CHATGPT_BASE_URL)
        }.getOrNull()
        if (!webkitCookie.isNullOrBlank()) {
            return webkitCookie
        }

        // 5. Raw token as session token cookie
        return "__Secure-next-auth.session-token=$raw"
    }

    private suspend fun fetchAccessTokenFromSession(cookieHeader: String): String? {
        val response = okHttpClient.newCallStrResponse {
            url(CHATGPT_SESSION_URL)
            addHeaders(
                mapOf(
                    "Cookie" to cookieHeader,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                    "Accept" to "application/json",
                    "Referer" to "https://chatgpt.com/",
                )
            )
        }
        if (!response.isSuccessful()) return null
        val body = response.body ?: return null
        val root = runCatching { GSON.fromJson(body, JsonObject::class.java) }.getOrNull()
        val token = root?.get("accessToken")?.asString
        if (!token.isNullOrBlank()) return token

        // Check if body has any JWT inside
        return extractJwt(body)
    }

    suspend fun exchangeCookieForTokenViaWebView(rawCookie: String): String? {
        val cookieHeader = resolveCookieHeader(rawCookie)
        if (cookieHeader.isBlank()) return null
        return fetchAccessTokenFromSession(cookieHeader)
    }
}

