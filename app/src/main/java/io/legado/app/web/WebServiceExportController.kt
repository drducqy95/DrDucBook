package io.legado.app.web

import io.legado.app.api.controller.BookController
import com.bumptech.glide.Glide
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.webservice.WebServiceExportBookshelfRequest
import io.legado.app.domain.webservice.WebServiceExportBookTextRequest
import io.legado.app.domain.webservice.WebServiceExportChapterRequest
import io.legado.app.domain.webservice.WebServiceExportRequests
import io.legado.app.domain.webservice.WebServiceExportSourcesRequest
import io.legado.app.domain.webservice.WebServiceExportEbookRequest
import io.legado.app.service.export.EbookExportChapter
import io.legado.app.service.export.EbookExportContentSource
import io.legado.app.service.export.EbookExportFormat
import io.legado.app.service.export.EbookExportImage
import io.legado.app.service.export.EbookExportImageOptimization
import io.legado.app.service.export.EbookExportPayload
import io.legado.app.service.export.EbookExportWriter
import io.legado.app.service.export.selectExportChapterIndices
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.isJson
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.model.translation.TranslationManager
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import splitties.init.appCtx
import java.io.File

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

    suspend fun ebook(request: WebServiceExportEbookRequest): WebServiceExportFile {
        val bookUrl = request.bookUrl?.trim()?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("BOOK_URL_REQUIRED")
        val book = appDb.bookDao.getBook(bookUrl) ?: throw IllegalArgumentException("BOOK_NOT_FOUND")
        var chapters = appDb.bookChapterDao.getChapterList(bookUrl)
        if (chapters.isEmpty()) {
            BookController.refreshTocAwait(mapOf("url" to listOf(bookUrl)))
            chapters = appDb.bookChapterDao.getChapterList(bookUrl)
        }
        val selected = selectExportChapterIndices(request.scope, chapters.size)
        val selectedChapters = chapters.filterIndexed { index, _ -> index in selected }
        if (selectedChapters.isEmpty()) throw IllegalArgumentException("CHAPTER_SELECTION_EMPTY")
        val contentSource = EbookExportContentSource.from(request.contentSource)
        val processor = ContentProcessor.get(book.name, book.origin)
        val useReplace = book.getUseReplaceRule()
        val targetLanguage = io.legado.app.ui.config.translation.TranslationConfig.llmTargetLanguage
        val payload = EbookExportPayload(
            title = book.name,
            author = book.getRealAuthor(),
            intro = HtmlFormatter.format(book.getDisplayIntro()),
            language = if (contentSource.includesTranslation) targetLanguage else Locale.getDefault().language,
            description = HtmlFormatter.format(book.getDisplayIntro()),
            identifier = "urn:drducbook:book:${book.bookUrl.hashCode().toUInt().toString(16)}",
            cover = resolveCover(book),
            chapters = selectedChapters.map { chapter ->
                val original = chapterContent(bookUrl, chapter.index)
                val translated = if (contentSource.includesTranslation) {
                    TranslationManager.getPreferredCachedTranslation(book, chapter, targetLanguage)?.content
                } else null
                val raw = when {
                    contentSource == EbookExportContentSource.TRANSLATION -> translated ?: original
                    contentSource == EbookExportContentSource.BOTH && !translated.isNullOrBlank() ->
                        "$original\n\n$translated"
                    else -> original
                }
                chapter.isVip = false
                val processed = processor.getContent(
                    book = book,
                    chapter = chapter,
                    content = raw,
                    includeTitle = false,
                    useReplace = useReplace,
                    chineseConvert = false,
                    reSegment = false,
                ).toString()
                EbookExportChapter(
                    index = chapter.index,
                    title = chapter.getDisplayTitle(processor.getTitleReplaceRules(), useReplace)
                        .replace("🔒", ""),
                    plainText = HtmlFormatter.format(processed),
                    html = processed,
                    images = if (contentSource.includesOriginal) collectImages(book, chapter, original) else emptyList(),
                )
            },
            imageOptimization = EbookExportImageOptimization.from(request.imageOptimization),
        )
        val format = EbookExportFormat.from(request.format)
        val tempDir = File(appCtx.cacheDir, "web_ebook_export_${System.nanoTime()}").apply { mkdirs() }
        val outputName = "${safeFileName(book.name)}.${format.extension}"
        val output = EbookExportWriter(
            outputDirectory = FileDoc.fromDir(tempDir.absolutePath),
            charset = Charsets.UTF_8,
            imageOptimization = EbookExportImageOptimization.from(request.imageOptimization),
        ).write(payload, format, outputName).asFile()
            ?: throw IllegalStateException("EXPORT_OUTPUT_UNAVAILABLE")
        return WebServiceExportFile(
            fileName = outputName,
            contentType = when (format) {
                EbookExportFormat.PDF -> "application/pdf"
                EbookExportFormat.EPUB2, EbookExportFormat.EPUB3 -> "application/epub+zip"
                EbookExportFormat.CBZ -> "application/vnd.comicbook+zip"
                EbookExportFormat.HTML -> "text/html; charset=utf-8"
                EbookExportFormat.TXT -> "text/plain; charset=utf-8"
            },
            writeTo = { stream ->
                try {
                    output.inputStream().use { it.copyTo(stream) }
                } finally {
                    tempDir.deleteRecursively()
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

    private fun safeFileName(raw: String): String =
        raw.replace(Regex("[\\u0000-\\u001f\\\\/:*?\\\"<>|]"), "_")
            .trim().take(120).ifBlank { "book" }

    private suspend fun resolveCover(book: Book): File? {
        val displayCover = book.getDisplayCover().orEmpty().trim()
        val path = displayCover.removePrefix("file://")
        File(path).takeIf { it.isFile && it.length() > 0L }?.let { return it }
        return runCatching {
            Glide.with(appCtx).asFile().load(displayCover).submit().get()
        }.getOrNull()?.takeIf { it.isFile && it.length() > 0L }
    }

    private fun collectImages(book: Book, chapter: BookChapter, content: String): List<EbookExportImage> {
        val result = arrayListOf<EbookExportImage>()
        val seen = hashSetOf<String>()
        val matcher = AppPattern.imgPattern.matcher(content)
        while (matcher.find()) {
            val relative = matcher.group(1) ?: continue
            val source = NetworkUtils.getAbsoluteURL(chapter.url, relative)
            if (!seen.add(source)) continue
            val file = BookHelp.getImage(book, source)
            if (file.isFile && file.length() > 0L) {
                result += EbookExportImage(
                    source = source,
                    file = file,
                    fileName = "${source.hashCode().toUInt().toString(16)}.${BookHelp.getImageSuffix(source)}",
                    aliases = listOf(relative),
                )
            }
        }
        return result
    }

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
