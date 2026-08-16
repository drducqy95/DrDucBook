package io.legado.app.data.repository

import io.legado.app.data.dao.BookSourceHealthDao
import io.legado.app.domain.gateway.SourceDomainIndexGateway
import io.legado.app.domain.model.BookSourceHealthRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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
}
