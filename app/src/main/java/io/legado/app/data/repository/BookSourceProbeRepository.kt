package io.legado.app.data.repository

import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.BookSourceProbeGateway
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook

class BookSourceProbeRepository : BookSourceProbeGateway {

    override suspend fun probe(source: BookSource) {
        val sample = when {
            !source.searchUrl.isNullOrBlank() -> {
                WebBook.searchBookAwait(
                    bookSource = source,
                    key = source.getCheckKeyword(DEFAULT_SEARCH_KEYWORD),
                ).firstOrNull()
            }

            !source.exploreUrl.isNullOrBlank() -> {
                val exploreUrl = source.exploreKinds()
                    .firstOrNull { !it.url.isNullOrBlank() }
                    ?.url
                    ?: throw NoStackTraceException("Explore rule has no usable URL")
                WebBook.exploreBookAwait(source, exploreUrl).firstOrNull()
            }

            else -> throw NoStackTraceException("Source has no search or explore rule")
        } ?: throw NoStackTraceException("Source rule returned no items")

        if (sample.name.isBlank() || sample.bookUrl.isBlank()) {
            throw NoStackTraceException("Source parse rule returned an item without title or URL")
        }
    }

    private companion object {
        const val DEFAULT_SEARCH_KEYWORD = "test"
    }
}
