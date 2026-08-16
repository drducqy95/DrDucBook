package io.legado.app.ui.browser

import io.legado.app.domain.model.BookSourceHealthRow
import io.legado.app.domain.model.SourceBookmarkPreference
import io.legado.app.domain.model.SourceDomainEntry
import io.legado.app.domain.model.SourceKey

internal fun buildBrowserSourceShortcuts(
    entries: List<SourceDomainEntry>,
    healthRows: List<BookSourceHealthRow>,
    preferences: Map<SourceKey, SourceBookmarkPreference>,
    query: String,
    maxItems: Int,
): List<BrowserSourceShortcutUi> {
    val healthBySourceUrl = healthRows.associateBy { row -> row.sourceUrl }
    return entries
        .asSequence()
        .filter { entry -> entry.enabled }
        .mapNotNull { entry ->
            val homeUrl = entry.preferredHomeUrl() ?: return@mapNotNull null
            val preference = preferences[entry.key]
            if (preference?.hidden == true) return@mapNotNull null
            if (!entry.matchesBrowserQuery(query)) return@mapNotNull null
            val health = healthBySourceUrl[entry.sourceUrl]?.health
            BrowserSourceShortcutUi(
                sourceKey = entry.key,
                sourceType = entry.key.type,
                name = entry.name,
                group = entry.group,
                sourceUrl = entry.sourceUrl,
                homeUrl = homeUrl,
                loginUrl = entry.loginUrl,
                iconPath = entry.iconPath,
                enabled = entry.enabled,
                isVbook = entry.isVbook,
                pinned = preference?.pinned == true,
                healthStatus = health?.statusValue,
                latencyMs = health?.latencyMs,
            )
        }
        .sortedWith(
            compareByDescending<BrowserSourceShortcutUi> { it.pinned }
                .thenBy { preferences[it.sourceKey]?.sortOrder ?: 0 }
                .thenBy { it.name.lowercase() }
        )
        .take(maxItems)
        .toList()
}

private fun SourceDomainEntry.matchesBrowserQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return name.contains(query, ignoreCase = true) ||
        sourceUrl.contains(query, ignoreCase = true) ||
        group.orEmpty().contains(query, ignoreCase = true)
}
