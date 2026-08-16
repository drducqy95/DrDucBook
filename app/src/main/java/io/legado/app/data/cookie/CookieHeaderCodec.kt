package io.legado.app.data.cookie

import io.legado.app.constant.AppPattern.equalsRegex
import io.legado.app.constant.AppPattern.semicolonRegex

object CookieHeaderCodec {

    fun cookieToMap(cookie: String): MutableMap<String, String> {
        val cookieMap = mutableMapOf<String, String>()
        if (cookie.isBlank()) {
            return cookieMap
        }
        val pairArray = cookie.split(semicolonRegex).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (pair in pairArray) {
            val pairs = pair.split(equalsRegex, 2).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (pairs.size <= 1) {
                continue
            }
            val key = pairs[0].trim { it <= ' ' }
            val value = pairs[1].trim { it <= ' ' }
            if (value.isNotBlank() || value == "null") {
                cookieMap[key] = value
            }
        }
        return cookieMap
    }

    fun mapToCookie(cookieMap: Map<String, String>?): String? {
        if (cookieMap.isNullOrEmpty()) {
            return null
        }
        return buildString {
            cookieMap.entries.forEachIndexed { index, entry ->
                if (index > 0) append("; ")
                append(entry.key).append("=").append(entry.value)
            }
        }
    }

    fun mergeCookies(vararg cookies: String?): String? {
        val cookieMap = mergeCookiesToMap(*cookies)
        return mapToCookie(cookieMap)
    }

    fun mergeCookiesToMap(vararg cookies: String?): MutableMap<String, String> {
        val maps = cookies.filterNotNull().mapNotNull { cookie ->
            val trimmed = cookie.trim()
            if (trimmed.isBlank()) null else cookieToMap(trimmed)
        }
        if (maps.isEmpty()) {
            return mutableMapOf()
        }
        return maps.reduce { acc, cookieMap ->
            acc.apply { putAll(cookieMap) }
        }
    }
}
