package io.legado.app.domain.gateway

import io.legado.app.help.http.api.CookieManagerInterface
import okhttp3.Cookie

interface SourceCookieGateway : CookieManagerInterface {

    fun saveResponse(url: String, cookies: List<Cookie>)

    fun removeCookie(url: String, key: String)

    fun migrateLegacyCookies(): Int

    fun clear()
}
