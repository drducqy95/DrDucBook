package io.legado.app.web

import io.legado.app.constant.BookType
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.webservice.WebServiceDiscoveryBook
import io.legado.app.domain.webservice.WebServiceDiscoveryResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext

object WebServiceDiscoveryController {
    private const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
    private const val SOURCE_LIMIT = 6
    private val cache = linkedMapOf<String, CachedResult>()

    suspend fun home(type: String?, limit: Int, refresh: Boolean): WebServiceDiscoveryResponse =
        withContext(Dispatchers.IO) {
            val normalizedType = type?.trim()?.lowercase().orEmpty()
            val cacheKey = normalizedType.ifBlank { "all" }
            val now = System.currentTimeMillis()
            synchronized(cache) {
                cache[cacheKey]?.takeIf { !refresh && now - it.createdAt < CACHE_TTL_MILLIS }?.let {
                    return@withContext it.response.copy(items = it.response.items.take(limit))
                }
            }

            val sourceDao = GlobalContext.get().get<BookSourceDao>()
            val exploreBooks = GlobalContext.get().get<ExploreBooksUseCase>()
            val sources = sourceDao.allEnabledExplore
                .asSequence()
                .filter { it.enabled }
                .take(SOURCE_LIMIT)
                .toList()
            val results = supervisorScope {
                sources.map { source ->
                    async {
                        runCatching {
                            withTimeout(12_000L) {
                                exploreBooks.execute(source.bookSourceUrl, null, null).books
                            }
                        }
                    }
                }.awaitAll()
            }
            val sourceErrors = results.count { it.isFailure }
            val items = results.asSequence()
                .flatMap { it.getOrNull().orEmpty().asSequence() }
                .filter { matchesType(it.type, normalizedType) }
                .map { it.toWebBook() }
                .distinctBy { it.bookUrl.ifBlank { "${it.name}|${it.author}" } }
                .take(limit)
                .toList()
            val response = WebServiceDiscoveryResponse(items, sourceErrors, now)
            synchronized(cache) {
                cache[cacheKey] = CachedResult(now, response)
                while (cache.size > 8) cache.remove(cache.keys.first())
            }
            response
        }

    private fun matchesType(type: Int, requested: String): Boolean = when (requested) {
        "text" -> type and BookType.text != 0
        "image" -> type and BookType.image != 0
        "audio" -> type and BookType.audio != 0
        "video" -> type and BookType.video != 0
        else -> true
    }

    private fun SearchBook.toWebBook() = WebServiceDiscoveryBook(
        bookUrl = bookUrl,
        origin = origin,
        originName = originName,
        type = type,
        name = WebTextRepair.repair(name).orEmpty(),
        author = WebTextRepair.repair(author).orEmpty(),
        kind = WebTextRepair.repair(kind),
        coverUrl = coverUrl,
        intro = WebTextRepair.repair(intro),
        latestChapterTitle = WebTextRepair.repair(latestChapterTitle),
        tocUrl = tocUrl,
        time = time,
        originOrder = originOrder,
        chapterWordCountText = chapterWordCountText,
        chapterWordCount = chapterWordCount,
        respondTime = respondTime,
    )

    private data class CachedResult(val createdAt: Long, val response: WebServiceDiscoveryResponse)
}
