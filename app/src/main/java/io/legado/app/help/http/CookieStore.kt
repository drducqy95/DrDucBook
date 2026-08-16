@file:Suppress("unused")

package io.legado.app.help.http

import android.text.TextUtils
import androidx.annotation.Keep
import io.legado.app.constant.AppLog
import io.legado.app.data.cookie.CookieHeaderCodec
import io.legado.app.domain.gateway.SourceCookieGateway
import io.legado.app.help.CacheManager
import io.legado.app.help.http.api.CookieManagerInterface
import io.legado.app.utils.removeCookie
import io.legado.app.utils.NetworkUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Keep
object CookieStore : CookieManagerInterface, SourceCookieGateway, KoinComponent {

    private val cookieGateway: SourceCookieGateway by inject()

    override fun saveResponse(url: String, cookies: List<okhttp3.Cookie>) {
        cookieGateway.saveResponse(url, cookies)
    }

    override fun setCookie(url: String, cookie: String?) {
        try {
            val domain = NetworkUtils.getSubDomain(url)
            if (cookie.isNullOrBlank()) {
                removeCookie(url)
                return
            }
            CacheManager.putMemory("${domain}_cookie", cookie)
            cookieGateway.setCookie(url, cookie)
        } catch (e: Exception) {
            AppLog.put("保存Cookie失败\n$e", e)
        }
    }

    override fun replaceCookie(url: String, cookie: String) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(cookie)) {
            return
        }
        val oldCookie = getCookieNoSession(url)
        if (TextUtils.isEmpty(oldCookie)) {
            setCookie(url, cookie)
            return
        }
        val newCookie = CookieHeaderCodec.mergeCookies(oldCookie, cookie)
        setCookie(url, newCookie)
    }

    override fun getCookie(url: String): String {
        val domain = NetworkUtils.getSubDomain(url)
        val cookie = getCookieNoSession(url)
        val sessionCookie = CookieManager.getSessionCookie(domain)
        val cookieMap = CookieHeaderCodec.mergeCookiesToMap(cookie, sessionCookie)
        var ck = mapToCookie(cookieMap) ?: ""
        while (ck.length > 4096) {
            val removeKey = cookieMap.keys.random()
            removeCookie(url, removeKey)
            cookieMap.remove(removeKey)
            ck = mapToCookie(cookieMap) ?: ""
        }
        return ck
    }

    fun getCookieNoSession(url: String): String {
        val domain = NetworkUtils.getSubDomain(url)
        val cacheCookie = CacheManager.getFromMemory("${domain}_cookie") as? String
        if (cacheCookie != null) {
            return cacheCookie
        }
        val cookie = cookieGateway.getCookie(url)
        if (cookie.isNotBlank()) {
            CacheManager.putMemory("${domain}_cookie", cookie)
        }
        return cookie
    }

    fun getKey(url: String, key: String): String {
        val cookie = getCookie(url)
        val sessionCookie = CookieManager.getSessionCookie(NetworkUtils.getSubDomain(url))
        val cookieMap = CookieHeaderCodec.mergeCookiesToMap(cookie, sessionCookie)
        return cookieMap[key] ?: ""
    }

    override fun removeCookie(url: String) {
        val domain = NetworkUtils.getSubDomain(url)
        CacheManager.deleteMemory("${domain}_cookie")
        CacheManager.deleteMemory("${domain}_session_cookie")
        cookieGateway.removeCookie(url)
        android.webkit.CookieManager.getInstance().removeCookie(url)
    }

    override fun removeCookie(url: String, key: String) {
        val domain = NetworkUtils.getSubDomain(url)
        getSessionCookieMap(domain)?.let {
            it.remove(key)
            CookieHeaderCodec.mapToCookie(it)?.let { cookie ->
                CacheManager.putMemory("${domain}_session_cookie", cookie)
            } ?: CacheManager.deleteMemory("${domain}_session_cookie")
        }
        val cookie = getCookieNoSession(url)
        if (cookie.isNotEmpty()) {
            val cookieMap = CookieHeaderCodec.cookieToMap(cookie)
            if (cookieMap.remove(key) != null) {
                CookieHeaderCodec.mapToCookie(cookieMap)?.let { updated ->
                    CacheManager.putMemory("${domain}_cookie", updated)
                } ?: CacheManager.deleteMemory("${domain}_cookie")
            }
        }
        cookieGateway.removeCookie(url, key)
    }

    override fun cookieToMap(cookie: String): MutableMap<String, String> {
        return CookieHeaderCodec.cookieToMap(cookie)
    }

    override fun mapToCookie(cookieMap: Map<String, String>?): String? {
        return CookieHeaderCodec.mapToCookie(cookieMap)
    }

    override fun migrateLegacyCookies(): Int {
        return cookieGateway.migrateLegacyCookies()
    }

    override fun clear() {
        cookieGateway.clear()
    }

    private fun getSessionCookieMap(domain: String): MutableMap<String, String>? {
        return CookieManager.getSessionCookie(domain)?.let { CookieHeaderCodec.cookieToMap(it) }
    }
}
