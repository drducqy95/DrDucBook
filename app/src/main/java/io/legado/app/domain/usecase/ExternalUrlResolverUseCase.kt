package io.legado.app.domain.usecase

import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.SearchBookDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.UrlSanitizer
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ResolvedExternalUrl {
    data class BookDetail(
        val bookSource: BookSource,
        val bookUrl: String,
        val book: Book,
        val inBookshelf: Boolean = false,
    ) : ResolvedExternalUrl

    data class ExploreCategory(
        val bookSource: BookSource,
        val exploreUrl: String,
        val title: String? = null,
    ) : ResolvedExternalUrl

    data class Unmatched(
        val url: String,
        val reason: String? = null,
    ) : ResolvedExternalUrl
}

class ExternalUrlResolverUseCase(
    private val bookSourceDao: BookSourceDao,
    private val bookDao: BookDao,
    private val searchBookDao: SearchBookDao,
) {

    suspend fun resolve(rawUrl: String): ResolvedExternalUrl = withContext(Dispatchers.IO) {
        val sanitizedUrl = UrlSanitizer.sanitize(rawUrl).trim()
        if (sanitizedUrl.isBlank() ||
            (!sanitizedUrl.startsWith("http://", ignoreCase = true) &&
                !sanitizedUrl.startsWith("https://", ignoreCase = true))
        ) {
            return@withContext ResolvedExternalUrl.Unmatched(rawUrl, "Invalid URL")
        }

        // 1. Check if the book already exists in local DB
        val dbBook = bookDao.getBook(sanitizedUrl)
        if (dbBook != null) {
            val source = bookSourceDao.getBookSource(dbBook.origin)
            if (source != null) {
                return@withContext ResolvedExternalUrl.BookDetail(
                    bookSource = source,
                    bookUrl = sanitizedUrl,
                    book = dbBook,
                    inBookshelf = !dbBook.isNotShelf,
                )
            }
        }

        // 2. Resolve matching BookSource
        val source = findMatchingBookSource(sanitizedUrl)
            ?: return@withContext ResolvedExternalUrl.Unmatched(sanitizedUrl, "No matching BookSource found")

        val baseUrl = NetworkUtils.getBaseUrl(sanitizedUrl)

        // 3. Check if this URL is likely an Explore/Category URL
        val isRootOrExplore = isExploreOrCategoryUrl(sanitizedUrl, baseUrl, source)

        // 4. Try fetching Book info if not purely root/explore
        val tempBook = Book(
            bookUrl = sanitizedUrl,
            origin = source.bookSourceUrl,
            originName = source.bookSourceName,
        )
        val loadedBook = if (!isRootOrExplore) {
            runCatching {
                WebBook.getBookInfoAwait(source, tempBook)
            }.getOrNull()
        } else {
            null
        }

        if (loadedBook != null && loadedBook.name.isNotBlank()) {
            runCatching {
                searchBookDao.insert(loadedBook.toSearchBook())
            }
            return@withContext ResolvedExternalUrl.BookDetail(
                bookSource = source,
                bookUrl = sanitizedUrl,
                book = loadedBook,
                inBookshelf = false,
            )
        }

        // If not a book detail or loading failed, but it matches explore/root
        if (isRootOrExplore || source.exploreUrl?.isNotBlank() == true) {
            return@withContext ResolvedExternalUrl.ExploreCategory(
                bookSource = source,
                exploreUrl = sanitizedUrl,
                title = source.bookSourceName,
            )
        }

        // If it specifically matched bookUrlPattern, treat as BookDetail even if network fetch was empty
        val pattern = source.bookUrlPattern?.takeIf { it.isNotBlank() }
        if (pattern != null && runCatching { sanitizedUrl.matches(pattern.toRegex()) }.getOrDefault(false)) {
            return@withContext ResolvedExternalUrl.BookDetail(
                bookSource = source,
                bookUrl = sanitizedUrl,
                book = tempBook,
                inBookshelf = false,
            )
        }

        ResolvedExternalUrl.Unmatched(sanitizedUrl, "Cannot resolve book content or explore page")
    }

    suspend fun findMatchingBookSource(sanitizedUrl: String): BookSource? {
        // A. Match UrlOption {origin: "..."}
        val urlMatcher = AnalyzeUrl.paramPattern.matcher(sanitizedUrl)
        if (urlMatcher.find()) {
            val origin = GSON.fromJsonObject<AnalyzeUrl.UrlOption>(
                sanitizedUrl.substring(urlMatcher.end())
            ).getOrNull()?.getOrigin()
            if (!origin.isNullOrBlank()) {
                val s = bookSourceDao.getBookSource(origin)
                if (s != null && s.enabled) return s
            }
        }

        // B. Match by BaseUrl
        val baseUrl = NetworkUtils.getBaseUrl(sanitizedUrl)
        if (baseUrl != null) {
            val sourceExact = bookSourceDao.getBookSourceAddBook(baseUrl)
            if (sourceExact != null && sourceExact.enabled) return sourceExact

            val altBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else "$baseUrl/"
            val sourceAlt = bookSourceDao.getBookSourceAddBook(altBaseUrl)
            if (sourceAlt != null && sourceAlt.enabled) return sourceAlt
        }

        // C. Match by hasBookUrlPattern
        val candidateParts = bookSourceDao.hasBookUrlPattern
        for (part in candidateParts) {
            try {
                val bs = bookSourceDao.getBookSource(part.bookSourceUrl) ?: continue
                if (!bs.enabled) continue
                val pattern = bs.bookUrlPattern?.takeIf { it.isNotBlank() } ?: continue
                if (sanitizedUrl.matches(pattern.toRegex())) {
                    return bs
                }
            } catch (_: Exception) {}
        }

        // D. Match by Domain
        if (baseUrl != null) {
            val domain = NetworkUtils.getDomain(sanitizedUrl)
            if (domain.isNotBlank()) {
                val allSources = bookSourceDao.allEnabled
                for (bs in allSources) {
                    val bsDomain = NetworkUtils.getDomain(bs.bookSourceUrl)
                    if (bsDomain.isNotBlank() && (domain.equals(bsDomain, ignoreCase = true) ||
                            domain.endsWith(".$bsDomain", ignoreCase = true) ||
                            bsDomain.endsWith(".$domain", ignoreCase = true))
                    ) {
                        return bs
                    }
                }
            }
        }

        return null
    }

    private fun isExploreOrCategoryUrl(url: String, baseUrl: String?, source: BookSource): Boolean {
        if (baseUrl != null) {
            val cleanUrl = if (url.endsWith("/")) url.dropLast(1) else url
            val cleanBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            if (cleanUrl.equals(cleanBase, ignoreCase = true)) return true
        }
        val explore = source.exploreUrl
        if (!explore.isNullOrBlank() && (explore.contains(url) || url.contains(explore))) {
            return true
        }
        return false
    }
}
