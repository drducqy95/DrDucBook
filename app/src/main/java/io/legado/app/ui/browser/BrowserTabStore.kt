package io.legado.app.ui.browser

import android.app.Application
import android.content.Context
import io.legado.app.domain.model.SourceKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class BrowserTabStore(application: Application) {

    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun restore(): BrowserSessionSnapshot {
        val stored = preferences.getString(KEY_SESSION, null)
            ?.let { encoded -> runCatching { json.decodeFromString<StoredSession>(encoded) }.getOrNull() }
        val tabs = stored?.tabs.orEmpty()
            .take(MAX_PERSISTED_TABS)
            .mapNotNull { tab -> tab.toBrowserTabUi() }
        if (tabs.isEmpty()) {
            val initial = newTab()
            return BrowserSessionSnapshot(listOf(initial), initial.id)
        }
        val activeId = stored?.activeTabId?.takeIf { id -> tabs.any { it.id == id } }
            ?: tabs.first().id
        return BrowserSessionSnapshot(tabs, activeId)
    }

    fun save(tabs: List<BrowserTabUi>, activeTabId: String) {
        val safeTabs = tabs.asSequence()
            .filter { tab -> tab.isHome || isSafeBrowserUrl(tab.url) }
            .take(MAX_PERSISTED_TABS)
            .map {
                StoredTab(
                    id = it.id,
                    url = if (it.isHome) HOME_URL else it.url,
                    title = it.title.take(MAX_TITLE_LENGTH),
                    sourceKey = it.sourceKey,
                    isHome = it.isHome,
                )
            }
            .toList()
        val session = StoredSession(
            tabs = safeTabs,
            activeTabId = activeTabId.takeIf { id -> safeTabs.any { it.id == id } },
        )
        preferences.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
    }

    fun newTab(url: String = HOME_URL): BrowserTabUi {
        val isHome = url.isBlank()
        return BrowserTabUi(
            id = UUID.randomUUID().toString(),
            url = if (isHome) HOME_URL else url,
            title = if (isHome) "" else url,
            isHome = isHome,
        )
    }

    companion object {
        const val HOME_URL = ""
        private const val PREFERENCES_NAME = "browser_tabs"
        private const val KEY_SESSION = "normal_session"
        private const val MAX_PERSISTED_TABS = 12
        private const val MAX_TITLE_LENGTH = 200
    }
}

data class BrowserSessionSnapshot(
    val tabs: List<BrowserTabUi>,
    val activeTabId: String,
)

internal fun normalizeBrowserInput(input: String): String {
    val value = input.trim()
    if (value.isEmpty()) return BrowserTabStore.HOME_URL
    if (isSafeBrowserUrl(value)) return value
    if (!value.contains(' ') && value.contains('.')) {
        val candidate = "https://$value"
        if (isSafeBrowserUrl(candidate)) return candidate
    }
    return "https://www.google.com/search?q=" + URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.name(),
    )
}

internal fun isSafeBrowserUrl(url: String): Boolean {
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}

@Serializable
private data class StoredSession(
    val tabs: List<StoredTab> = emptyList(),
    val activeTabId: String? = null,
)

@Serializable
private data class StoredTab(
    val id: String,
    val url: String,
    val title: String,
    val sourceKey: SourceKey? = null,
    val isHome: Boolean = false,
)

private fun StoredTab.toBrowserTabUi(): BrowserTabUi? = when {
    isHome -> BrowserTabUi(
        id = id,
        url = BrowserTabStore.HOME_URL,
        title = title,
        sourceKey = sourceKey,
        isHome = true,
    )

    isSafeBrowserUrl(url) -> BrowserTabUi(
        id = id,
        url = url,
        title = title.ifBlank { url },
        sourceKey = sourceKey,
    )

    else -> null
}
