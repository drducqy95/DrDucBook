package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind

interface ExploreBooksGateway {
    suspend fun getBookSource(sourceUrl: String): BookSource?
    suspend fun getExploreKinds(bookSource: BookSource): List<ExploreKind>
    suspend fun exploreBooks(
        bookSource: BookSource,
        url: String,
        page: Int,
        key: String? = null
    ): List<SearchBook>
    suspend fun saveSearchBooks(books: List<SearchBook>)
}
