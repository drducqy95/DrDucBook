package io.legado.app.domain.usecase

import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.SearchBookDao
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class ExternalUrlResolverUseCaseTest {

    private fun createUseCase(
        bookSources: List<BookSource> = emptyList(),
    ): ExternalUrlResolverUseCase {
        val bookSourceDao = Proxy.newProxyInstance(
            BookSourceDao::class.java.classLoader,
            arrayOf(BookSourceDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBookSource" -> {
                    val url = args[0] as String
                    bookSources.firstOrNull { it.bookSourceUrl == url }
                }
                "getBookSourceAddBook" -> {
                    val baseUrl = args[0] as String
                    bookSources.firstOrNull { it.bookSourceUrl == baseUrl || it.bookSourceUrl == "$baseUrl/" || "$baseUrl/".startsWith(it.bookSourceUrl) }
                }
                "getHasBookUrlPattern" -> {
                    bookSources.filter { !it.bookUrlPattern.isNullOrBlank() }.map {
                        BookSourcePart(
                            bookSourceUrl = it.bookSourceUrl,
                            bookSourceName = it.bookSourceName,
                            bookSourceGroup = it.bookSourceGroup,
                            enabled = it.enabled,
                            enabledExplore = it.enabledExplore,
                            weight = it.weight,
                            customOrder = it.customOrder,
                        )
                    }
                }
                "getAllEnabled" -> bookSources.filter { it.enabled }
                else -> null
            }
        } as BookSourceDao

        val bookDao = Proxy.newProxyInstance(
            BookDao::class.java.classLoader,
            arrayOf(BookDao::class.java)
        ) { _, _, _ -> null } as BookDao

        val searchBookDao = Proxy.newProxyInstance(
            SearchBookDao::class.java.classLoader,
            arrayOf(SearchBookDao::class.java)
        ) { _, _, _ -> null } as SearchBookDao

        return ExternalUrlResolverUseCase(bookSourceDao, bookDao, searchBookDao)
    }

    @Test
    fun `invalid URL returns Unmatched`() = runBlocking {
        val useCase = createUseCase()

        val result1 = useCase.resolve("")
        assertTrue(result1 is ResolvedExternalUrl.Unmatched)

        val result2 = useCase.resolve("not_a_valid_url")
        assertTrue(result2 is ResolvedExternalUrl.Unmatched)

        val result3 = useCase.resolve("ftp://example.com/file")
        assertTrue(result3 is ResolvedExternalUrl.Unmatched)
    }

    @Test
    fun `unmatched BookSource returns Unmatched`() = runBlocking {
        val useCase = createUseCase(emptyList())

        val result = useCase.resolve("https://unknown-domain.com/novel/123")
        assertTrue(result is ResolvedExternalUrl.Unmatched)
    }

    @Test
    fun `matching BookSource by bookUrlPattern finds source`() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://www.52shuku.net",
            bookSourceName = "52Thư Khố",
            bookUrlPattern = """.*52shuku\.net/.*\.html""",
            enabled = true
        )
        val useCase = createUseCase(listOf(source))

        val found = useCase.findMatchingBookSource("https://www.52shuku.net/yanqing/03_b/bjYOd.html")
        assertEquals(source.bookSourceUrl, found?.bookSourceUrl)
    }

    @Test
    fun `matching BookSource by domain finds source`() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://fanqienovel.com",
            bookSourceName = "Phiên Già",
            enabled = true
        )
        val useCase = createUseCase(listOf(source))

        val found = useCase.findMatchingBookSource("https://fanqienovel.com/page/71839210928")
        assertEquals(source.bookSourceUrl, found?.bookSourceUrl)
    }
}
