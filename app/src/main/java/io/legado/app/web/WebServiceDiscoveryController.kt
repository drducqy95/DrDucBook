package io.legado.app.web

import io.legado.app.constant.BookType
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.webservice.WebServiceDiscoveryBook
import io.legado.app.domain.webservice.WebServiceDiscoveryKindResponse
import io.legado.app.domain.webservice.WebServiceDiscoveryKindsResponse
import io.legado.app.domain.webservice.WebServiceDiscoveryResponse
import io.legado.app.domain.webservice.WebServiceDiscoverySourceResponse
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.getExploreInfoMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import splitties.init.appCtx

object WebServiceDiscoveryController {
    private const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
    private const val SOURCE_LIMIT = 6
    private val cache = linkedMapOf<String, CachedResult>()

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    suspend fun home(
        type: String?,
        limit: Int,
        refresh: Boolean,
        sourceUrl: String? = null,
        exploreUrl: String? = null,
        args: String? = null,
        page: Int = 1,
    ): WebServiceDiscoveryResponse =
        withContext(Dispatchers.IO) {
            val normalizedType = type?.trim()?.lowercase().orEmpty()
            val normalizedSource = sourceUrl?.trim().orEmpty()
            val normalizedExplore = exploreUrl?.trim().orEmpty()
            val normalizedArgs = args?.trim().orEmpty()
            val cacheKey = listOf(
                normalizedType.ifBlank { "all" },
                normalizedSource.ifBlank { "all" },
                normalizedExplore.ifBlank { "default" },
                normalizedArgs,
                page.coerceAtLeast(1),
            ).joinToString("\u0000")
            val now = System.currentTimeMillis()
            synchronized(cache) {
                cache[cacheKey]?.takeIf { !refresh && now - it.createdAt < CACHE_TTL_MILLIS }?.let {
                    return@withContext it.response.copy(items = it.response.items.take(limit))
                }
            }

            val sourceDao = GlobalContext.get().get<BookSourceDao>()
            val exploreBooks = GlobalContext.get().get<ExploreBooksUseCase>()
            val webSelection = WebServicePolicyStore.read(appCtx).webDiscoverySourceUrls.toSet()
            val sourceCandidates = if (normalizedSource.isNotBlank()) {
                sourceDao.allEnabled.filter { it.bookSourceUrl == normalizedSource }
            } else if (webSelection.isEmpty()) {
                sourceDao.allEnabledExplore
            } else {
                sourceDao.allEnabled
            }
            val sources = sourceCandidates
                .asSequence()
                .filter { it.enabled }
                .filter { webSelection.isEmpty() || it.bookSourceUrl in webSelection }
                .take(SOURCE_LIMIT)
                .toList()
            val results = supervisorScope {
                sources.map { source ->
                    async {
                        runCatching {
                            withTimeout(12_000L) {
                                exploreBooks.execute(
                                    source.bookSourceUrl,
                                    normalizedExplore.takeIf(String::isNotBlank),
                                    normalizedArgs.takeIf(String::isNotBlank),
                                    page = page.coerceAtLeast(1),
                                ).books
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

    fun sources(): List<WebServiceDiscoverySourceResponse> {
        val selected = WebServicePolicyStore.read(appCtx).webDiscoverySourceUrls.toSet()
        return GlobalContext.get().get<BookSourceDao>().all
            .filter { it.enabled }
            .map {
                WebServiceDiscoverySourceResponse(
                    sourceUrl = it.bookSourceUrl,
                    name = WebTextRepair.repair(it.bookSourceName).orEmpty(),
                    group = WebTextRepair.repair(it.bookSourceGroup),
                    enabled = it.enabled,
                    selectedForWeb = selected.isEmpty() || it.bookSourceUrl in selected,
                )
            }
    }

    suspend fun kinds(sourceUrl: String): WebServiceDiscoveryKindsResponse = withContext(Dispatchers.IO) {
        val normalizedUrl = sourceUrl.trim().takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("DISCOVERY_SOURCE_REQUIRED")
        val source = GlobalContext.get().get<BookSourceDao>().getBookSource(normalizedUrl)
            ?: throw IllegalArgumentException("DISCOVERY_SOURCE_NOT_FOUND")
        if (!source.enabled) throw IllegalArgumentException("DISCOVERY_SOURCE_DISABLED")
        val infoMap = getExploreInfoMap(normalizedUrl)
        val kinds = source.exploreKinds().map { kind ->
            WebServiceDiscoveryKindResponse(
                title = WebTextRepair.repair(kind.title).orEmpty(),
                displayName = WebTextRepair.repair(kind.title).orEmpty(),
                url = kind.url,
                type = kind.type,
                action = kind.action,
                chars = kind.chars.orEmpty().filterNotNull().map { WebTextRepair.repair(it).orEmpty() },
                defaultValue = WebTextRepair.repair(kind.default),
                currentValue = infoMap[kind.title].orEmpty(),
            )
        }
        WebServiceDiscoveryKindsResponse(
            sourceUrl = source.bookSourceUrl,
            sourceName = WebTextRepair.repair(source.bookSourceName).orEmpty(),
            group = WebTextRepair.repair(source.bookSourceGroup),
            kinds = kinds,
        )
    }

    fun updateKindValues(sourceUrl: String, values: Map<String, String>) {
        val normalizedUrl = sourceUrl.trim().takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("DISCOVERY_SOURCE_REQUIRED")
        val source = GlobalContext.get().get<BookSourceDao>().getBookSource(normalizedUrl)
            ?: throw IllegalArgumentException("DISCOVERY_SOURCE_NOT_FOUND")
        require(source.enabled) { "DISCOVERY_SOURCE_DISABLED" }
        val infoMap = getExploreInfoMap(normalizedUrl)
        values.forEach { (key, value) ->
            val cleanKey = key.trim()
            if (cleanKey.isNotBlank()) infoMap[cleanKey] = value.take(500)
        }
        infoMap.saveNow()
        clearCache()
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
