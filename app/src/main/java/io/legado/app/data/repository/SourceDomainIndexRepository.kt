package io.legado.app.data.repository

import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.RssSourceDao
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.domain.gateway.SourceDomainIndexGateway
import io.legado.app.domain.model.SourceDomainEntry
import io.legado.app.domain.model.SourceDomainIndex
import io.legado.app.domain.model.SourceKey
import io.legado.app.domain.model.SourceKeyType
import io.legado.app.help.vbook.VbookPluginAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class SourceDomainIndexRepository(
    bookSourceDao: BookSourceDao,
    rssSourceDao: RssSourceDao,
) : SourceDomainIndexGateway {

    override val index: Flow<SourceDomainIndex> = combine(
        bookSourceDao.flowSourceContextCandidates(),
        rssSourceDao.flowAll(),
    ) { bookSources, rssSources ->
        SourceDomainIndex(
            entries = bookSources.map(::bookEntry) + rssSources.map(::rssEntry),
        )
    }.flowOn(Dispatchers.IO)

    private fun bookEntry(source: BookSource): SourceDomainEntry {
        val matchUrls = listOfNotNull(
            source.loginUrl,
            source.exploreUrl,
            source.searchUrl,
        )
        return SourceDomainEntry(
            key = SourceKey(SourceKeyType.BOOK, source.bookSourceUrl),
            name = source.bookSourceName,
            group = source.bookSourceGroup,
            sourceUrl = source.bookSourceUrl,
            homeUrl = firstHttpUrl(
                source.loginUrl,
                source.bookSourceUrl,
                source.exploreUrl,
                source.searchUrl,
            ),
            loginUrl = firstHttpUrl(source.loginUrl),
            enabled = source.enabled,
            isVbook = source.bookSourceUrl.startsWith(VbookPluginAdapter.SOURCE_PREFIX),
            order = source.customOrder,
            matchUrls = matchUrls,
        )
    }

    private fun rssEntry(source: RssSource): SourceDomainEntry {
        val matchUrls = listOfNotNull(
            source.loginUrl,
            source.sortUrl,
            source.searchUrl,
        )
        return SourceDomainEntry(
            key = SourceKey(SourceKeyType.RSS, source.sourceUrl),
            name = source.sourceName,
            group = source.sourceGroup,
            sourceUrl = source.sourceUrl,
            homeUrl = firstHttpUrl(
                source.loginUrl,
                source.sourceUrl,
                source.sortUrl,
                source.searchUrl,
            ),
            loginUrl = firstHttpUrl(source.loginUrl),
            iconPath = source.sourceIcon.takeIf(String::isNotBlank),
            enabled = source.enabled,
            order = source.customOrder,
            matchUrls = matchUrls,
        )
    }

    private fun firstHttpUrl(vararg values: String?): String? =
        values.asSequence()
            .filterNotNull()
            .map(String::trim)
            .firstOrNull {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }
}
