package io.legado.app.help.http

import android.webkit.CookieManager as WebkitCookieManager
import io.legado.app.constant.AppLog
import io.legado.app.data.cookie.CookieHeaderCodec
import io.legado.app.help.CacheManager
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.splitNotBlank
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Connection

@Suppress("ConstPropertyName")
object CookieManager {
    /**
     * <domain>_session_cookie session cookie, cleared when app restarts
     */
    const val cookieJarHeader = "CookieJar"

    fun saveResponse(response: Response) {
        val url = response.request.url
        saveCookiesFromHeaders(url, response.headers)
    }

    fun saveResponse(response: Connection.Response) {
        val url = response.url().toHttpUrlOrNull() ?: return
        saveCookiesFromHeaders(url, response.multiHeaders().toHeaders())
    }

    private fun saveCookiesFromHeaders(url: HttpUrl, headers: Headers) {
        val domain = NetworkUtils.getSubDomain(url.toString())
        val cookies = Cookie.parseAll(url, headers)
        val sessionCookie = cookies.filterNot { it.persistent }.getString()
        updateSessionCookie(domain, sessionCookie)
        val persistentCookies = cookies.filter { it.persistent }
        if (persistentCookies.isNotEmpty()) {
            CookieStore.saveResponse(url.toString(), persistentCookies)
        }
    }

    fun loadRequest(request: Request): Request {
        val url = request.url.toString()
        val domain = NetworkUtils.getSubDomain(url)
        val cookie = CookieStore.getCookie(url)
        val requestCookie = request.header("Cookie")
        val newCookie = mergeCookies(requestCookie, cookie) ?: return request

        kotlin.runCatching {
            return request.newBuilder()
                .header("Cookie", newCookie)
                .build()
        }.onFailure {
            CookieStore.removeCookie(url)
            AppLog.put(cookieFailureMessage(domain, it))
        }

        return request
    }

    private fun getSessionCookieMap(domain: String): MutableMap<String, String>? {
        return getSessionCookie(domain)?.let { CookieHeaderCodec.cookieToMap(it) }
    }

    fun getSessionCookie(domain: String): String? {
        return CacheManager.getFromMemory("${domain}_session_cookie") as? String
    }

    private fun updateSessionCookie(domain: String, cookies: String) {
        val sessionCookie = getSessionCookie(domain)
        if (sessionCookie.isNullOrEmpty()) {
            CacheManager.putMemory("${domain}_session_cookie", cookies)
            return
        }

        val ck = mergeCookies(sessionCookie, cookies) ?: return
        CacheManager.putMemory("${domain}_session_cookie", ck)
    }

    fun mergeCookies(vararg cookies: String?): String? {
        val cookieMap = mergeCookiesToMap(*cookies)
        return CookieHeaderCodec.mapToCookie(cookieMap)
    }

    fun mergeCookiesToMap(vararg cookies: String?): MutableMap<String, String> {
        return CookieHeaderCodec.mergeCookiesToMap(*cookies)
    }

    fun removeCookie(url: String, key: String) {
        val domain = NetworkUtils.getSubDomain(url)

        getSessionCookieMap(domain)?.let {
            it.remove(key)
            CookieHeaderCodec.mapToCookie(it)?.let { cookie ->
                CacheManager.putMemory("${domain}_session_cookie", cookie)
            }
        }

        CookieStore.removeCookie(url, key)
    }

    fun getCookieNoSession(url: String): String {
        return CookieStore.getCookieNoSession(url)
    }

    fun applyToWebView(url: String) {
        NetworkUtils.getBaseUrl(url) ?: return
        val cookies = CookieStore.getCookie(url).splitNotBlank(";")
        val cookieManager = WebkitCookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookies.forEach {
            cookieManager.setCookie(url, it)
        }
        cookieManager.flush()
    }

    fun syncFromWebView(url: String?, scopeUrl: String? = null) {
        val safeUrl = url?.takeIf { NetworkUtils.getBaseUrl(it) != null } ?: return
        val cookieManager = WebkitCookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val cookie = cookieManager.getCookie(safeUrl)
        CookieStore.setCookie(safeUrl, cookie)
        val fallbackScope = scopeUrl
            ?.takeIf { it.isNotBlank() && it != safeUrl && !cookie.isNullOrBlank() }
        if (fallbackScope != null) {
            CookieStore.replaceCookie(fallbackScope, cookie.orEmpty())
        }
        cookieManager.flush()
    }

    fun List<Cookie>.getString() = buildString {
        this@getString.forEachIndexed { index, cookie ->
            if (index > 0) append("; ")
            append(cookie.name).append('=').append(cookie.value)
        }
    }

    private fun Map<String, List<String>>.toHeaders(): Headers {
        return Headers.Builder().apply {
            this@toHeaders.forEach { (k, v) ->
                v.forEach {
                    add(k, it)
                }
            }
        }.build()
    }

}

internal fun cookieFailureMessage(domain: String, error: Throwable): String =
    "设置cookie出错，已清除cookie $domain (${error.javaClass.simpleName})"
