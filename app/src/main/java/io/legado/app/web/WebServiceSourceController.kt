package io.legado.app.web

import com.jayway.jsonpath.JsonPath
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.domain.webservice.WebServiceSourceImportItem
import io.legado.app.domain.webservice.WebServiceSourceImportResponse
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.ByteArrayOutputStream
import java.net.URI

/** Web source importer with the same JSON/object/URL forms as the native importer. */
object WebServiceSourceController {
    private const val MAX_SOURCES = 50
    private const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024

    suspend fun import(
        payload: String,
        commit: Boolean,
        sourceType: String = "book",
    ): WebServiceSourceImportResponse = withContext(Dispatchers.IO) {
        val parsed = parse(payload.trim(), sourceType)
        val items = when (parsed) {
            is Parsed.Book -> parsed.values
                .distinctBy { it.bookSourceUrl.trim() }
                .take(MAX_SOURCES)
                .onEach { source ->
                    require(source.bookSourceUrl.isNotBlank()) { "SOURCE_URL_REQUIRED" }
                    require(source.bookSourceName.isNotBlank()) { "SOURCE_NAME_REQUIRED" }
                    source.enabled = true
                }
                .map { source ->
                    WebServiceSourceImportItem(
                        sourceUrl = source.bookSourceUrl,
                        name = source.bookSourceName,
                        existing = appDb.bookSourceDao.has(source.bookSourceUrl),
                        lastUpdateTime = source.lastUpdateTime,
                    )
                }
            is Parsed.Rss -> parsed.values
                .distinctBy { it.sourceUrl.trim() }
                .take(MAX_SOURCES)
                .onEach { source ->
                    require(source.sourceUrl.isNotBlank()) { "SOURCE_URL_REQUIRED" }
                    require(source.sourceName.isNotBlank()) { "SOURCE_NAME_REQUIRED" }
                    source.enabled = true
                }
                .map { source ->
                    WebServiceSourceImportItem(
                        sourceUrl = source.sourceUrl,
                        name = source.sourceName,
                        existing = appDb.rssSourceDao.getByKey(source.sourceUrl) != null,
                        lastUpdateTime = source.lastUpdateTime,
                    )
                }
        }
        require(items.isNotEmpty()) { "SOURCE_IMPORT_EMPTY" }
        if (commit) {
            when (parsed) {
                is Parsed.Book -> SourceHelp.insertBookSource(*parsed.values.toTypedArray())
                is Parsed.Rss -> SourceHelp.insertRssSource(*parsed.values.toTypedArray())
            }
            if (parsed is Parsed.Book) {
                WebServicePolicyStore.addWebDiscoverySources(appCtx, parsed.values.map { it.bookSourceUrl })
            }
        }
        WebServiceSourceImportResponse(items = items, committed = commit)
    }

    private suspend fun parse(payload: String, sourceType: String): Parsed {
        require(payload.isNotBlank()) { "SOURCE_PAYLOAD_REQUIRED" }
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
            "SOURCE_PAYLOAD_TOO_LARGE"
        }
        val uri = runCatching { URI(payload) }.getOrNull()
        if (uri?.scheme.equals("http", true) || uri?.scheme.equals("https", true)) {
            return fetchJson(payload, sourceType)
        }
        return parseJson(payload, sourceType)
    }

    private suspend fun fetchJson(url: String, sourceType: String): Parsed =
        okHttpClient.newCallResponseBody {
            url(url.removeSuffix("#requestWithoutUA"))
            if (url.endsWith("#requestWithoutUA")) header(AppConst.UA_NAME, "null")
        }.decompressed().byteStream().use { stream ->
            parseJson(stream.readBounded(MAX_PAYLOAD_BYTES).toString(Charsets.UTF_8), sourceType)
        }

    private suspend fun parseJson(raw: String, sourceType: String): Parsed {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return if (sourceType.equals("rss", true)) {
                Parsed.Rss(GSON.fromJsonArray<RssSource>(trimmed).getOrThrow())
            } else {
                Parsed.Book(GSON.fromJsonArray<BookSource>(trimmed).getOrThrow())
            }
        }
        val sourceUrls = runCatching {
            JsonPath.parse(trimmed).read<List<String>>("$.sourceUrls")
        }.getOrNull()
        if (!sourceUrls.isNullOrEmpty()) {
            val fetched = sourceUrls.take(MAX_SOURCES).map { sourceUrl ->
                fetchJson(sourceUrl, sourceType)
            }
            return if (sourceType.equals("rss", true)) {
                Parsed.Rss(fetched.flatMap { (it as? Parsed.Rss)?.values.orEmpty() })
            } else {
                Parsed.Book(fetched.flatMap { (it as? Parsed.Book)?.values.orEmpty() })
            }
        }
        return if (sourceType.equals("rss", true)) {
            Parsed.Rss(listOf(GSON.fromJsonObject<RssSource>(trimmed).getOrThrow()))
        } else {
            Parsed.Book(listOf(GSON.fromJsonObject<BookSource>(trimmed).getOrThrow()))
        }
    }

    private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "SOURCE_PAYLOAD_TOO_LARGE" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private sealed interface Parsed {
        data class Book(val values: List<BookSource>) : Parsed
        data class Rss(val values: List<RssSource>) : Parsed
    }
}
