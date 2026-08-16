package io.legado.app.ui.browser

import io.legado.app.domain.model.SourceBookmarkPreference
import io.legado.app.domain.model.SourceDomainEntry
import io.legado.app.domain.model.SourceKey
import io.legado.app.domain.model.SourceKeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserHomeDataTest {

    @Test
    fun buildsSourceShortcutsFromEnabledHttpSourcesWithPinHideAndSearch() {
        val pinnedKey = SourceKey(SourceKeyType.BOOK, "https://pinned.example/source")
        val hiddenKey = SourceKey(SourceKeyType.BOOK, "https://hidden.example/source")
        val disabledKey = SourceKey(SourceKeyType.BOOK, "https://disabled.example/source")
        val nonHttpKey = SourceKey(SourceKeyType.BOOK, "vbook://plugin/source")
        val rssKey = SourceKey(SourceKeyType.RSS, "https://rss.example/feed")
        val entries = listOf(
            entry(rssKey, "RSS Daily", group = "News", order = 3),
            entry(pinnedKey, "Pinned Source", order = 9),
            entry(hiddenKey, "Hidden Source", order = 1),
            entry(disabledKey, "Disabled Source", enabled = false),
            entry(nonHttpKey, "Plugin Source", homeUrl = "vbook://plugin/home"),
        )

        val shortcuts = buildBrowserSourceShortcuts(
            entries = entries,
            healthRows = emptyList(),
            preferences = mapOf(
                pinnedKey to SourceBookmarkPreference(pinnedKey, pinned = true, sortOrder = -1),
                hiddenKey to SourceBookmarkPreference(hiddenKey, hidden = true),
            ),
            query = "source",
            maxItems = 10,
        )

        assertEquals(listOf(pinnedKey), shortcuts.map { it.sourceKey })
        assertTrue(shortcuts.single().pinned)
    }

    @Test
    fun sourceShortcutSearchCanMatchGroup() {
        val rssKey = SourceKey(SourceKeyType.RSS, "https://rss.example/feed")

        val shortcuts = buildBrowserSourceShortcuts(
            entries = listOf(entry(rssKey, "RSS Daily", group = "News")),
            healthRows = emptyList(),
            preferences = emptyMap(),
            query = "news",
            maxItems = 10,
        )

        assertEquals(rssKey, shortcuts.single().sourceKey)
    }

    private fun entry(
        key: SourceKey,
        name: String,
        group: String? = null,
        homeUrl: String? = key.id,
        enabled: Boolean = true,
        order: Int = 0,
    ): SourceDomainEntry = SourceDomainEntry(
        key = key,
        name = name,
        group = group,
        sourceUrl = key.id,
        homeUrl = homeUrl,
        enabled = enabled,
        order = order,
    )
}
