package io.legado.app.service

import android.content.Context
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.putPrefStringSync
import io.legado.app.utils.removePref
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CheckSourceSession(
    val sourceUrls: List<String>,
    val pendingSourceUrls: List<String>,
    val profile: SourceCheckProfile,
    val timeoutMs: Long,
    val checkSearch: Boolean = true,
    val checkDiscovery: Boolean = true,
    val checkInfo: Boolean = true,
    val checkCategory: Boolean = true,
    val checkContent: Boolean = true,
    val healthyCount: Int = 0,
    val failedCount: Int = 0,
    val paused: Boolean = false,
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val totalCount: Int
        get() = sourceUrls.size
}

internal object CheckSourceSessionStore {

    private const val PREF_KEY = "check_source_session"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(context: Context, session: CheckSourceSession) {
        context.putPrefStringSync(PREF_KEY, json.encodeToString(session))
    }

    fun load(context: Context): CheckSourceSession? {
        val value = context.defaultSharedPreferences.getString(PREF_KEY, null) ?: return null
        return runCatching { json.decodeFromString<CheckSourceSession>(value) }.getOrNull()
    }

    fun clear(context: Context) {
        context.removePref(PREF_KEY)
    }

    fun hasSession(context: Context): Boolean = load(context) != null
}
