package io.legado.app.web

import io.legado.app.api.controller.BookController
import io.legado.app.data.appDb
import io.legado.app.domain.webservice.WebServiceExportBookshelfRequest
import io.legado.app.domain.webservice.WebServiceExportBookTextRequest
import io.legado.app.domain.webservice.WebServiceExportChapterRequest
import io.legado.app.domain.webservice.WebServiceExportRequests
import io.legado.app.domain.webservice.WebServiceExportSourcesRequest
import io.legado.app.utils.GSON
import io.legado.app.utils.isJson
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WebServiceExportController {

    fun sources(request: WebServiceExportSourcesRequest): WebServiceExportFile {
        val keys = WebServiceExportRequests.normalizedKeys(request.sourceKeys)
        val sourceType = WebServiceExportRequests.normalizeSourceType(request.sourceType)
        val prefix = if (sourceType == WebServiceExportRequests.SOURCE_TYPE_RSS) {
            "rss_sources"
        } else {
            "book_sources"
        }
        request.payloadJson
            ?.trim()
            ?.takeIf { it.isJson() }
            ?.let { return jsonTextFile(prefix, it) }
        val payload = if (sourceType == WebServiceExportRequests.SOURCE_TYPE_RSS) {
            appDb.rssSourceDao.all
                .filterIfKeys(keys) { it.sourceUrl }
        } else {
            appDb.bookSourceDao.all
                .filterIfKeys(keys) { it.bookSourceUrl }
        }
        return jsonFile(prefix, payload)
    }

    fun bookshelf(request: WebServiceExportBookshelfRequest): WebServiceExportFile {
        val bookUrls = WebServiceExportRequests.normalizedKeys(request.bookUrls)
        val books = appDb.bookDao.all.filterIfKeys(bookUrls) { it.bookUrl }
        return jsonFile("bookshelf", books)
    }

    suspend fun chapter(request: WebServiceExportChapterRequest): WebServiceExportFile {
        val bookUrl = request.bookUrl?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("BOOK_URL_REQUIRED")
        val chapterIndex = request.chapterIndex
            ?: throw IllegalArgumentException("CHAPTER_INDEX_REQUIRED")
        val returnData = BookController.getBookContentAwait(
            mapOf(
                "url" to listOf(bookUrl),
                "index" to listOf(chapterIndex.toString()),
            )
        )
        if (!returnData.isSuccess) {
            throw IllegalArgumentException(returnData.errorMsg)
        }
        val content = returnData.data as? String
            ?: throw IllegalArgumentException("CHAPTER_CONTENT_EMPTY")
        return WebServiceExportFile(
            fileName = "chapter_${chapterIndex}_${timestamp()}.txt",
            contentType = "text/plain; charset=utf-8",
            writeTo = { output -> output.writeUtf8(content) },
        )
    }

    suspend fun bookText(request: WebServiceExportBookTextRequest): WebServiceExportFile {
        val bookUrl = request.bookUrl?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("BOOK_URL_REQUIRED")
        val book = appDb.bookDao.getBook(bookUrl)
            ?: throw IllegalArgumentException("BOOK_NOT_FOUND")
        var chapters = appDb.bookChapterDao.getChapterList(bookUrl)
        if (chapters.isEmpty()) {
            BookController.refreshTocAwait(mapOf("url" to listOf(bookUrl)))
            chapters = appDb.bookChapterDao.getChapterList(bookUrl)
        }
        if (chapters.isEmpty()) throw IllegalArgumentException("CHAPTER_LIST_EMPTY")

        val selectedIndices = WebServiceExportRequests.normalizedChapterIndices(request.chapterIndices)
        val selectedChapters = if (selectedIndices.isEmpty()) {
            chapters
        } else {
            chapters.filter { it.index in selectedIndices }
        }
        if (selectedChapters.isEmpty()) throw IllegalArgumentException("CHAPTER_SELECTION_EMPTY")

        return WebServiceExportFile(
            fileName = "book_${timestamp()}.txt",
            contentType = "text/plain; charset=utf-8",
            writeTo = { output ->
                val writer = OutputStreamWriter(output, Charsets.UTF_8)
                writer.appendLine(book.name)
                if (book.author.isNotBlank()) writer.appendLine(book.author)
                writer.appendLine()
                selectedChapters.forEach { chapter ->
                    writer.appendLine(chapter.title)
                    writer.appendLine()
                    writer.appendLine(chapterContent(bookUrl, chapter.index))
                    writer.appendLine()
                    writer.flush()
                }
            },
        )
    }

    private fun jsonFile(
        prefix: String,
        payload: Any,
    ): WebServiceExportFile =
        WebServiceExportFile(
            fileName = "${prefix}_${timestamp()}.json",
            contentType = "application/json; charset=utf-8",
            writeTo = { output ->
                val writer = OutputStreamWriter(output, Charsets.UTF_8)
                GSON.toJson(payload, writer)
                writer.flush()
            },
        )

    private fun jsonTextFile(
        prefix: String,
        json: String,
    ): WebServiceExportFile =
        WebServiceExportFile(
            fileName = "${prefix}_${timestamp()}.json",
            contentType = "application/json; charset=utf-8",
            writeTo = { output -> output.writeUtf8(json) },
        )

    private inline fun <T> List<T>.filterIfKeys(
        keys: Set<String>,
        crossinline keyOf: (T) -> String,
    ): List<T> =
        if (keys.isEmpty()) this else filter { keyOf(it) in keys }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())

    private suspend fun chapterContent(
        bookUrl: String,
        chapterIndex: Int,
    ): String {
        val returnData = BookController.getBookContentAwait(
            mapOf(
                "url" to listOf(bookUrl),
                "index" to listOf(chapterIndex.toString()),
            )
        )
        if (!returnData.isSuccess) {
            throw IllegalArgumentException(returnData.errorMsg)
        }
        return returnData.data as? String
            ?: throw IllegalArgumentException("CHAPTER_CONTENT_EMPTY")
    }

    private fun OutputStream.writeUtf8(text: String) {
        val writer = OutputStreamWriter(this, Charsets.UTF_8)
        writer.write(text)
        writer.flush()
    }
}

data class WebServiceExportFile(
    val fileName: String,
    val contentType: String,
    val writeTo: suspend (OutputStream) -> Unit,
)
