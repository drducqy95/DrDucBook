package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.dao.BookSourceHealthDao
import io.legado.app.domain.gateway.SourceDomainIndexGateway
import io.legado.app.domain.model.BookSourceHealthRow
import io.legado.app.help.AppCacheManager
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.vbook.VbookPluginAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

class BookSourceHealthRepository(
    private val sourceDomainIndexGateway: SourceDomainIndexGateway,
    private val healthDao: BookSourceHealthDao,
) {
    fun observeRows(): Flow<List<BookSourceHealthRow>> = combine(
        sourceDomainIndexGateway.index,
        healthDao.flowAll(),
    ) { index, healthRecords ->
        val healthByUrl = healthRecords.associateBy { record -> record.sourceUrl }
        index.entries.map { source ->
            val loginUrl = source.loginUrl?.takeIf(String::isNotBlank)
            BookSourceHealthRow(
                sourceUrl = source.sourceUrl,
                sourceName = source.name,
                sourceGroup = source.group,
                sourceType = source.key.type,
                homeUrl = source.preferredHomeUrl(),
                loginUrl = loginUrl,
                iconPath = source.iconPath,
                isVbook = source.isVbook,
                enabled = source.enabled,
                enabledExplore = source.enabled,
                hasLoginUrl = loginUrl != null,
                health = healthByUrl[source.sourceUrl],
            )
        }
    }.map { rows -> rows.sortedBy { row -> row.sourceName.lowercase() } }

    suspend fun deleteSourcesCompletely(keysOrUrls: Collection<String>) = withContext(Dispatchers.IO) {
        if (keysOrUrls.isEmpty()) return@withContext
        val allCandidateKeys = mutableSetOf<String>()
        val bookUrlsToDelete = mutableSetOf<String>()
        val rssUrlsToDelete = mutableSetOf<String>()
        val vbookIdsToDelete = mutableSetOf<String>()

        keysOrUrls.forEach { keyOrUrl ->
            val trimmed = keyOrUrl.trim()
            if (trimmed.isBlank()) return@forEach
            val (typePrefix, rawId) = when {
                trimmed.startsWith("book:", ignoreCase = true) -> "book" to trimmed.substring(5)
                trimmed.startsWith("rss:", ignoreCase = true) -> "rss" to trimmed.substring(4)
                trimmed.startsWith("vbook:", ignoreCase = true) -> "vbook" to trimmed.substring(6)
                trimmed.startsWith(VbookPluginAdapter.SOURCE_PREFIX, ignoreCase = true) -> "vbook" to trimmed
                else -> null to trimmed
            }

            allCandidateKeys.add(trimmed)
            allCandidateKeys.add(rawId)
            allCandidateKeys.add("book:$rawId")
            allCandidateKeys.add("rss:$rawId")
            allCandidateKeys.add("vbook:$rawId")

            when (typePrefix) {
                "rss" -> {
                    rssUrlsToDelete.add(rawId)
                    rssUrlsToDelete.add(trimmed)
                }
                "vbook" -> {
                    bookUrlsToDelete.add(rawId)
                    bookUrlsToDelete.add(trimmed)
                    val pluginId = rawId.removePrefix(VbookPluginAdapter.SOURCE_PREFIX).removePrefix("vbook:")
                    if (pluginId.isNotBlank()) {
                        vbookIdsToDelete.add(pluginId)
                    }
                }
                "book" -> {
                    bookUrlsToDelete.add(rawId)
                    bookUrlsToDelete.add(trimmed)
                }
                else -> {
                    if (rawId.startsWith(VbookPluginAdapter.SOURCE_PREFIX)) {
                        bookUrlsToDelete.add(rawId)
                        val pluginId = rawId.removePrefix(VbookPluginAdapter.SOURCE_PREFIX)
                        if (pluginId.isNotBlank()) {
                            vbookIdsToDelete.add(pluginId)
                        }
                    } else {
                        bookUrlsToDelete.add(rawId)
                        rssUrlsToDelete.add(rawId)
                    }
                }
            }
        }

        appDb.runInTransaction {
            bookUrlsToDelete.forEach { url ->
                appDb.bookSourceDao.delete(url)
                SourceConfig.removeSource(url)
            }
            rssUrlsToDelete.forEach { url ->
                appDb.rssSourceDao.delete(url)
                appDb.rssArticleDao.delete(url)
            }
            allCandidateKeys.forEach { key ->
                appDb.cacheDao.deleteSourceVariables(key)
            }
            healthDao.deleteBySourceUrlsBlocking(allCandidateKeys)
            appDb.sourceCheckDao.deleteRunsBySourceUrlsBlocking(allCandidateKeys)
        }

        vbookIdsToDelete.forEach { pluginId ->
            runCatching {
                File(appCtx.filesDir, "vbook_plugins/$pluginId").deleteRecursively()
            }
        }
        AppCacheManager.clearSourceVariables()
    }
}
