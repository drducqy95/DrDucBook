package io.legado.app.help.vbook

import android.content.Context
import io.legado.app.data.cookie.CookieHeaderCodec
import org.json.JSONObject
import java.io.File

/** Keeps the legacy MTC plugin's private token cache in sync with its login WebView. */
internal object VbookMtcSessionCompat {

    fun ensurePublicFallback(context: Context, pluginId: String, pluginDirectory: File) {
        if (!isMtcPlugin(pluginDirectory)) return
        val preferences = preferences(context)
        val authorizationKey = storageKey(pluginId, AUTHORIZATION)
        val currentAuthorization = preferences.getString(authorizationKey, null)
        if (!currentAuthorization.isNullOrBlank() && currentAuthorization != PUBLIC_AUTHORIZATION) return
        preferences.edit()
            .putString(authorizationKey, PUBLIC_AUTHORIZATION)
            .putString(storageKey(pluginId, AUTHORIZATION_TIME), System.currentTimeMillis().toString())
            .apply()
    }

    fun syncBrowserCookie(context: Context, sourceUrl: String?, cookie: String?): Boolean {
        return syncBrowserSession(context, sourceUrl, cookie, null)
    }

    /**
     * MTC has used both a cookie and Web Storage for its access token over time.
     * Keep the plugin storage in sync with both so a successful WebView login is
     * also visible to the VBook runtime.
     */
    fun syncBrowserSession(
        context: Context,
        sourceUrl: String?,
        cookie: String?,
        localStorageJson: String?,
    ): Boolean {
        val pluginId = sourceUrl
            ?.takeIf { it.startsWith(VbookPluginAdapter.SOURCE_PREFIX) }
            ?.removePrefix(VbookPluginAdapter.SOURCE_PREFIX)
            ?.takeIf { it.matches(PLUGIN_ID_REGEX) }
            ?: return false
        val pluginDirectory = File(context.filesDir, "vbook_plugins/$pluginId")
        if (!isMtcPlugin(pluginDirectory)) return false
        val cookieToken = CookieHeaderCodec.cookieToMap(cookie.orEmpty())[ACCESS_TOKEN]
        val storageToken = localStorageJson
            ?.takeIf(String::isNotBlank)
            ?.let(::parseStorageToken)
        val accessToken = (cookieToken ?: storageToken)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?: return false
        val authorization = if (accessToken.startsWith("Bearer ", ignoreCase = true)) {
            accessToken
        } else {
            "Bearer $accessToken"
        }
        preferences(context).edit()
            .putString(storageKey(pluginId, AUTHORIZATION), authorization)
            .putString(storageKey(pluginId, AUTHORIZATION_TIME), System.currentTimeMillis().toString())
            .apply()
        return true
    }

    private fun parseStorageToken(raw: String): String? {
        return runCatching {
            val value = JSONObject(raw)
            sequenceOf("authorization", "accessToken", "access_token", "token")
                .mapNotNull { key -> value.optString(key).trim().takeIf(String::isNotEmpty) }
                .firstOrNull()
        }.getOrNull()
    }

    private fun isMtcPlugin(pluginDirectory: File): Boolean {
        val manifest = runCatching {
            JSONObject(File(pluginDirectory, "plugin.json").readText(Charsets.UTF_8))
        }.getOrNull() ?: return false
        val metadata = manifest.optJSONObject("metadata") ?: return false
        val name = metadata.optString("name").trim()
        val source = metadata.optString("source").lowercase()
        return name.equals("MTC", ignoreCase = true) || MTC_HOST_MARKERS.any(source::contains)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun storageKey(pluginId: String, key: String) = "$pluginId:$key"

    private const val PREFERENCES_NAME = "vbook_plugin_storage"
    private const val ACCESS_TOKEN = "accessToken"
    private const val AUTHORIZATION = "authorization"
    private const val AUTHORIZATION_TIME = "authorization_time"
    private const val PUBLIC_AUTHORIZATION = "Bearer public"
    private val PLUGIN_ID_REGEX = Regex("[a-f0-9]{16,64}")
    private val MTC_HOST_MARKERS = listOf("metruyencv", "metruyenchu", "mtccv")
}
