package io.legado.app.domain.usecase

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.gateway.ExploreBooksGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreBooksUseCaseTest {

    @Test
    fun `missing module url uses first parsed explore kind`() = runBlocking {
        val source = BookSource(
            bookSourceUrl = SOURCE_URL,
            exploreUrl = "Home::/home/{{page}}.html\nRank::/rank/{{page}}.html",
        )
        val gateway = FakeExploreBooksGateway(
            source = source,
            kinds = listOf(
                ExploreKind("Home", "/home/{{page}}.html"),
                ExploreKind("Rank", "/rank/{{page}}.html"),
            )
        )

        ExploreBooksUseCase(gateway).execute(SOURCE_URL, moduleUrl = null, args = null)

        assertEquals("/home/{{page}}.html", gateway.lastExploreUrl)
    }

    @Test
    fun `blank module url falls back to first parsed explore kind`() = runBlocking {
        val source = BookSource(
            bookSourceUrl = SOURCE_URL,
            exploreUrl = "Home::/home/{{page}}.html",
        )
        val gateway = FakeExploreBooksGateway(
            source = source,
            kinds = listOf(ExploreKind("Home", "/home/{{page}}.html"))
        )

        ExploreBooksUseCase(gateway).execute(SOURCE_URL, moduleUrl = "", args = null)

        assertEquals("/home/{{page}}.html", gateway.lastExploreUrl)
    }

    private class FakeExploreBooksGateway(
        private val source: BookSource,
        private val kinds: List<ExploreKind> = emptyList(),
    ) : ExploreBooksGateway {

        var lastExploreUrl: String? = null

        override suspend fun getBookSource(sourceUrl: String): BookSource? {
            return source.takeIf { it.bookSourceUrl == sourceUrl }
        }

        override suspend fun getExploreKinds(bookSource: BookSource): List<ExploreKind> {
            return kinds
        }

        override suspend fun exploreBooks(
            bookSource: BookSource,
            url: String,
            page: Int,
            key: String?,
        ): List<SearchBook> {
            lastExploreUrl = url
            return emptyList()
        }

        override suspend fun saveSearchBooks(books: List<SearchBook>) = Unit
    }

    private companion object {
        const val SOURCE_URL = "https://source.example"
    }
}
