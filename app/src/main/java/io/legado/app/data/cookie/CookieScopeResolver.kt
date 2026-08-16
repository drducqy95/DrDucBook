package io.legado.app.data.cookie

import io.legado.app.utils.NetworkUtils
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CookieScopeResolver {

    fun normalizeScopeKey(url: String): String {
        return NetworkUtils.getSubDomainOrNull(url)
            ?: url.toHttpUrlOrNull()?.host?.lowercase()?.trim()
            ?: url.trim().lowercase()
    }

    fun resolveLookupScopes(url: String): List<String> {
        val scopes = linkedSetOf(normalizeScopeKey(url))
        val host = url.toHttpUrlOrNull()?.host?.lowercase()?.trim().orEmpty()
        if (host.isBlank()) {
            return scopes.toList()
        }
        val parts = host.split('.').filter { it.isNotBlank() }
        if (parts.size >= 2) {
            for (i in 0 until parts.size - 1) {
                scopes.add(parts.drop(i).joinToString("."))
            }
        }
        scopes.add(host)
        return scopes.toList()
    }

    fun matches(url: String, domain: String, path: String, hostOnly: Boolean): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return normalizeScopeKey(url) == domain
        val host = httpUrl.host.lowercase()
        val cookieDomain = domain.lowercase()
        val domainMatches = if (hostOnly) {
            host == cookieDomain
        } else {
            host == cookieDomain || host.endsWith(".$cookieDomain")
        }
        if (!domainMatches) {
            return false
        }
        val requestPath = httpUrl.encodedPath.ifBlank { "/" }
        val cookiePath = path.ifBlank { "/" }
        return requestPath == cookiePath ||
            (requestPath.startsWith(cookiePath) && (
                cookiePath.endsWith("/") ||
                    requestPath.getOrNull(cookiePath.length) == '/'
                ))
    }
}
