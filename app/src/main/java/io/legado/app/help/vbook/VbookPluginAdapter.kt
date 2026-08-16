package io.legado.app.help.vbook

import android.net.Uri
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.ResolvedMedia
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.removeAllBookType
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.getBookType
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.text.StringEscapeUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import splitties.init.appCtx
import java.io.File
import java.net.URI
import kotlin.coroutines.coroutineContext

interface VbookScriptExecutor {
    fun isVbookSource(): Boolean
    fun hasScript(scriptName: String): Boolean
    fun resolveScript(role: String, fallback: String): String = fallback
    fun executeScript(
        scriptName: String,
        functionName: String,
        args: Array<Any?>,
        configUrl: String,
        configValues: Map<String, String> = emptyMap(),
    ): String
}

object VbookPluginAdapter {

    fun canHandle(source: BookSource): Boolean {
        val pluginId = pluginId(source) ?: return false
        return File(appCtx.filesDir, "vbook_plugins/$pluginId/plugin.json").isFile
    }

    suspend fun search(source: BookSource, query: String, page: Int?): ArrayList<SearchBook> {
        repairInstalledSourceIfNeeded(source)
        val executor = executor(source)
        val script = executor.resolveScript("search", "search.js")
        val pageToken = page?.takeIf { it > 1 }?.toString()
        return parseBooks(source, executeArray(source, executor, script, arrayOf(query, pageToken)))
    }

    suspend fun exploreKinds(source: BookSource): List<ExploreKind> {
        repairInstalledSourceIfNeeded(source)
        val executor = executor(source)
        val script = executor.resolveScript("home", "home.js")
        val data = executeArray(source, executor, script, emptyArray())
        return buildList {
            for (index in 0 until data.length()) {
                coroutineContext.ensureActive()
                val item = data.requireObject(index, script)
                val title = item.requireAnyText(script, "title", "name", "label")
                val input = item.requireAnyText(script, "input", "url", "link", "href")
                val targetScript = item.optString("script", "gen.js").ifBlank { "gen.js" }
                add(
                    ExploreKind(
                        title = title,
                        url = Uri.Builder()
                            .scheme(SCHEME)
                            .authority("discover")
                            .appendQueryParameter("input", input)
                            .appendQueryParameter("script", targetScript)
                            .build()
                            .toString(),
                    )
                )
            }
        }
    }

    suspend fun explore(source: BookSource, url: String, page: Int?): ArrayList<SearchBook> {
        repairInstalledSourceIfNeeded(source)
        val resolvedUrl = resolveExploreUrl(source, url)
        val uri = Uri.parse(resolvedUrl)
        val input = uri.getQueryParameter("input")
            ?: throw VbookPluginException("Danh mục VBook thiếu tham số input")
        val script = uri.getQueryParameter("script").orEmpty().ifBlank { "gen.js" }
        val data = executeArray(
            source = source,
            executor = executor(source),
            scriptName = script,
            args = arrayOf(input, (page ?: 1).toString()),
        )
        return parseBooks(source, data)
    }

    private suspend fun resolveExploreUrl(source: BookSource, url: String): String {
        val uri = Uri.parse(url)
        if (!uri.getQueryParameter("input").isNullOrBlank()) return url
        if (uri.scheme != SCHEME || uri.authority != "home") return url
        return exploreKinds(source)
            .firstOrNull { !it.url.isNullOrBlank() }
            ?.url
            ?: throw VbookPluginException("Plugin VBook không có danh mục khám phá khả dụng")
    }

    suspend fun enrichBookInfo(source: BookSource, book: Book): Book {
        repairInstalledSourceIfNeeded(source)
        syncBookTypeIfNeeded(source, book)
        val executor = executor(source)
        val script = executor.resolveScript("detail", "detail.js")
        if (!executor.hasScript(script)) return book
        val data = executeEnvelope(source, executor, script, arrayOf(book.bookUrl)).opt("data")
        if (data !is JSONObject) return book

        data.firstText("name", "title")?.let { book.name = it }
        data.firstText("author", "description")?.let { book.author = it }
        data.firstText("cover", "coverUrl")?.let { book.coverUrl = resolveUrl(it, configUrl(source)) }
        data.firstText("intro", "content", "description")?.let { book.intro = it }
        data.firstText("category", "kind", "genres")?.let { book.kind = it }
        data.firstText("toc", "tocUrl", "link")?.let {
            book.tocUrl = resolveUrl(it, configUrl(source))
        }
        if (book.tocUrl.isBlank()) book.tocUrl = book.bookUrl
        return book
    }

    suspend fun chapters(source: BookSource, book: Book): List<BookChapter> {
        repairInstalledSourceIfNeeded(source)
        syncBookTypeIfNeeded(source, book)
        val executor = executor(source)
        val pageScript = executor.resolveScript("page", "page.js")
        val pages = if (executor.hasScript(pageScript)) {
            executeArray(source, executor, pageScript, arrayOf(book.tocUrl.ifBlank { book.bookUrl }))
                .stringValues(pageScript)
                .ifEmpty { listOf(book.tocUrl.ifBlank { book.bookUrl }) }
        } else {
            listOf(book.tocUrl.ifBlank { book.bookUrl })
        }
        val tocScript = executor.resolveScript("toc", "toc.js")
        val result = arrayListOf<BookChapter>()
        pages.forEach { pageUrl ->
            coroutineContext.ensureActive()
            val data = executeArray(source, executor, tocScript, arrayOf(pageUrl))
            for (index in 0 until data.length()) {
                chapterFromItem(
                    item = data.opt(index),
                    scriptName = tocScript,
                    pageUrl = pageUrl,
                    bookUrl = book.bookUrl,
                    index = result.size,
                    defaultHost = configUrl(source),
                )?.let(result::add)
            }
        }
        book.totalChapterNum = result.size
        book.latestChapterTitle = result.lastOrNull()?.title
        book.lastCheckTime = System.currentTimeMillis()
        return result
    }

    private fun chapterFromItem(
        item: Any?,
        scriptName: String,
        pageUrl: String,
        bookUrl: String,
        index: Int,
        defaultHost: String,
    ): BookChapter? {
        return when (item) {
            is String -> item
                .takeIf(String::isNotBlank)
                ?.let { url ->
                    BookChapter(
                        title = chapterTitleFromUrl(url, index),
                        url = resolveUrl(url, defaultHost),
                        baseUrl = pageUrl,
                        bookUrl = bookUrl,
                        index = index,
                        variable = chapterVariable(url),
                    )
                }

            is JSONArray -> {
                val values = item.stringValuesOrEmpty()
                val url = chapterUrlFromPair(values) ?: return null
                BookChapter(
                    title = chapterTitleFromPair(values, url) ?: chapterTitleFromUrl(url, index),
                    url = resolveUrl(url, defaultHost),
                    baseUrl = pageUrl,
                    bookUrl = bookUrl,
                    index = index,
                    variable = chapterVariable(url),
                )
            }

            is JSONObject -> {
                if (item.optString("type").equals("section", ignoreCase = true)) return null
                val url = item.firstText(
                    "url",
                    "link",
                    "href",
                    "uri",
                    "permalink",
                    "chapterUrl",
                    "chapter_url",
                    "chapterLink",
                    "chapter_link",
                    "chapUrl",
                    "chap_url",
                    "episodeUrl",
                    "episode_url",
                    "readUrl",
                    "read_url",
                    "path",
                    "input",
                    "data",
                    "id",
                    "chapterId",
                    "chapter_id",
                    "slug",
                    "chapterSlug",
                    "chapter_slug",
                ) ?: return null
                val host = item.optString("host").ifBlank { defaultHost }
                BookChapter(
                    title = item.firstText(
                        "name",
                        "title",
                        "chapterName",
                        "chapter_name",
                        "episodeName",
                        "episode_name",
                        "label",
                    )
                        ?: chapterTitleFromUrl(url, index),
                    url = resolveUrl(url, host),
                    baseUrl = pageUrl,
                    bookUrl = bookUrl,
                    index = index,
                    variable = chapterVariable(url),
                )
            }

            null, JSONObject.NULL -> null
            else -> throw VbookPluginException("$scriptName có phần tử chương không hợp lệ")
        }
    }

    private fun JSONArray.stringValuesOrEmpty(): List<String> = buildList {
        for (index in 0 until length()) {
            val value = opt(index)
            if (value is String && value.isNotBlank()) add(value)
            else if (value is Number) add(value.toString())
        }
    }

    private fun chapterUrlFromPair(values: List<String>): String? {
        if (values.isEmpty()) return null
        if (values.size == 1) return values.first()
        val first = values[0]
        val second = values[1]
        return when {
            looksLikeChapterLocation(second) -> second
            looksLikeChapterLocation(first) -> first
            first.isCompactInput() && !second.isCompactInput() -> first
            else -> second
        }
    }

    private fun chapterTitleFromPair(values: List<String>, url: String): String? =
        values.firstOrNull { it != url && !looksLikeChapterLocation(it) }
            ?.takeIf(String::isNotBlank)

    private fun looksLikeChapterLocation(value: String): Boolean {
        val text = value.trim()
        return text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true) ||
            text.startsWith("//") ||
            text.startsWith("/") ||
            text.contains("/") ||
            text.contains("?") ||
            text.contains(".html", ignoreCase = true) ||
            text.contains(".htm", ignoreCase = true)
    }

    private fun String.isCompactInput(): Boolean {
        val text = trim()
        return text.isNotBlank() && text.length <= 96 && text.none(Char::isWhitespace)
    }

    private fun chapterVariable(input: String): String? =
        input.takeIf(String::isNotBlank)
            ?.let { JSONObject().put(CHAPTER_INPUT_KEY, it).toString() }

    private fun chapterInputCandidates(chapter: BookChapter): List<String> = buildList {
        add(chapter.getAbsoluteURL())
        chapter.variableMap[CHAPTER_INPUT_KEY]?.let(::add)
        add(chapter.url)
    }.map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    suspend fun content(
        source: BookSource,
        book: Book,
        chapter: BookChapter,
        needSave: Boolean,
    ): String {
        repairInstalledSourceIfNeeded(source)
        syncBookTypeIfNeeded(source, book)
        val executor = executor(source)
        val script = executor.resolveScript("chap", "chap.js")
        var lastError: Throwable? = null
        for (input in chapterInputCandidates(chapter)) {
            coroutineContext.ensureActive()
            val data = try {
                executeEnvelope(source, executor, script, arrayOf(input)).opt("data")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
                continue
        }
        val rawValue = contentText(data)
        val detectedType = detectContentType(data, rawValue)
        if (detectedType != null && detectedType != source.bookSourceType) {
            source.bookSourceType = detectedType
            runCatching {
                withContext(Dispatchers.IO) { appDb.bookSourceDao.update(source) }
            }
            syncBookTypeIfNeeded(source, book)
            recordRuntimeCapabilities(source, "chap", data.toString())
        }
        val raw = if ((detectedType ?: source.bookSourceType) == BookSourceType.image) {
            normalizeImageContentText(
                value = data,
                raw = rawValue,
                chapterUrl = input,
                sourceUrl = configUrl(source),
            )
        } else {
            normalizeContentText(contentText(data))
        }
        val content = raw.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n\n")
        if (content.isBlank()) continue
        if (content.isBlank()) throw VbookPluginException("Plugin VBook trả về nội dung trống")
        if (needSave) BookHelp.saveContent(source, book, chapter, content)
        return content
        }
        lastError?.let { throw it }
        throw VbookPluginException("Plugin VBook tra ve noi dung trong")
    }

    /**
     * Classifies a chapter response from its payload, not only from the manifest declaration.
     * This fixes plugins that declare text (or omit a type) while chap.js actually returns image
     * URLs, and also prevents feeding media URLs to the text reader.
     */
    private fun detectContentType(value: Any?, raw: String): Int? {
        val serialized = value.toJsonText() + "\n" + raw
        val lower = serialized.lowercase()
        if (listOf(".m3u8", ".mpd", ".mp4", "video_url", "videoUrl").any(lower::contains)) {
            return BookSourceType.video
        }
        if (listOf(".mp3", ".m4a", "audio_url", "audioUrl").any(lower::contains)) {
            return BookSourceType.audio
        }
        val images = contentImageUrls(value, "", "")
        val imageOnlyLines = raw.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .all(::isPotentialImageReference)
        val hasImageMarkup = lower.contains("<img") || images.isNotEmpty()
        return BookSourceType.image.takeIf {
            hasImageMarkup && (imageOnlyLines || raw.isBlank() || raw.length < images.size * 160)
        }
    }

    private fun contentText(value: Any?): String {
        return when (value) {
            is String -> value
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    when (val item = value.opt(index)) {
                        is String -> add(item)
                        is JSONObject -> item.firstText(
                            "content",
                            "html",
                            "text",
                            "url",
                            "src",
                            "link",
                            "image",
                        )?.let(::add)
                    }
                }
            }.joinToString("\n")

            is JSONObject -> {
                val direct = value.firstValue(
                    "content",
                    "html",
                    "text",
                    "data",
                    "body",
                    "chapter",
                    "images",
                    "items",
                    "list",
                )
                if (direct != null && direct != JSONObject.NULL && direct !== value) {
                    contentText(direct)
                } else {
                    value.firstText("content", "html", "text", "data", "body").orEmpty()
                }
            }

            else -> ""
        }
    }

    private fun normalizeImageContentText(
        value: Any?,
        raw: String,
        chapterUrl: String,
        sourceUrl: String,
    ): String {
        val images = contentImageUrls(value, chapterUrl, sourceUrl)
            .ifEmpty { contentImageUrls(raw, chapterUrl, sourceUrl) }
        if (images.isNotEmpty()) {
            return images.joinToString("\n") { imageUrl ->
                """<img src="${withImageReferer(imageUrl, chapterUrl, sourceUrl)}">"""
            }
        }
        return normalizeContentText(raw)
    }

    private fun withImageReferer(
        imageUrl: String,
        chapterUrl: String,
        sourceUrl: String,
    ): String {
        if (!imageUrl.startsWith("http", ignoreCase = true)) return imageUrl
        if (urlOptionRegex.containsMatchIn(imageUrl)) return imageUrl
        val referer = chapterUrl.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: sourceUrl.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: return imageUrl
        val option = JSONObject()
            .put("headers", JSONObject().put("Referer", referer))
            .toString()
            .replace("\\/", "/")
        return "$imageUrl,$option"
    }

    private fun contentImageUrls(
        value: Any?,
        chapterUrl: String,
        sourceUrl: String,
    ): List<String> {
        return when (value) {
            is String -> imageUrlsFromString(value, chapterUrl, sourceUrl)
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    addAll(contentImageUrls(value.opt(index), chapterUrl, sourceUrl))
                }
            }

            is JSONObject -> {
                val containerImages = buildList {
                    imageContainerKeys.forEach { key ->
                        val direct = value.opt(key)
                        if (direct != null && direct != JSONObject.NULL && direct !== value) {
                            addAll(contentImageUrls(direct, chapterUrl, sourceUrl))
                        }
                    }
                }
                containerImages.ifEmpty {
                    bestImageUrlFromObject(value, chapterUrl, sourceUrl)?.let(::listOf).orEmpty()
                }
            }

            else -> emptyList()
        }.distinct()
    }

    private fun bestImageUrlFromObject(
        value: JSONObject,
        chapterUrl: String,
        sourceUrl: String,
    ): String? {
        val primary = imageObjectPrimaryKeys.flatMap { key ->
            contentImageUrls(value.opt(key), chapterUrl, sourceUrl)
        }
        val fallback = imageObjectFallbackKeys.flatMap { key ->
            contentImageUrls(value.opt(key), chapterUrl, sourceUrl)
        }
        val candidates = (primary + fallback).distinct()
        return candidates.firstOrNull { it.hasStrongImageSignal() && !it.isSuspiciousImageUrl() }
            ?: candidates.firstOrNull { it.hasStrongImageSignal() }
            ?: candidates.firstOrNull { !it.isSuspiciousImageUrl() }
            ?: candidates.firstOrNull()
    }

    private fun String.hasStrongImageSignal(): Boolean =
        startsWith("data:image/", ignoreCase = true) ||
            imageExtensionRegex.containsMatchIn(substringBefore('#').substringBefore('?'))

    private fun String.isSuspiciousImageUrl(): Boolean {
        val lower = lowercase()
        return "loading" in lower ||
            "placeholder" in lower ||
            "blank." in lower ||
            "/blank" in lower ||
            "lazy" in lower ||
            "default" in lower
    }

    private fun imageUrlsFromString(
        raw: String,
        chapterUrl: String,
        sourceUrl: String,
    ): List<String> {
        val decoded = raw.decodeHtmlEntities()
        val tagUrls = imageSrcRegex.findAll(decoded)
            .mapNotNull { match ->
                match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value
            }
            .map { resolveImageUrl(it, chapterUrl, sourceUrl) }
            .toList()
        if (tagUrls.isNotEmpty()) return tagUrls

        val normalized = normalizeContentText(decoded)
        val lines = normalized.lineSequence()
            .map { it.trim().trim('"', '\'') }
            .filter(String::isNotBlank)
            .toList()
        if (lines.isNotEmpty() && lines.all(::isPotentialImageReference)) {
            return lines.map { resolveImageUrl(it, chapterUrl, sourceUrl) }
        }
        return imageUrlInTextRegex.findAll(decoded)
            .map { resolveImageUrl(it.value, chapterUrl, sourceUrl) }
            .toList()
    }

    private fun normalizeContentText(raw: String): String {
        val unescaped = raw.decodeHtmlEntities()
            .replace('\u00A0', ' ')
            .replace('\u2009', ' ')
            .replace("\u200C", "")
            .replace("\u200D", "")
        if (!htmlContentMarkerRegex.containsMatchIn(unescaped)) return unescaped
        return unescaped
            .replace(htmlCommentRegex, "")
            .replace(htmlBreakRegex, "\n")
            .replace(htmlBlockTagRegex, "\n")
            .replace(htmlTagRegex, "")
            .decodeHtmlEntities()
            .replace('\u00A0', ' ')
    }

    private fun String.decodeHtmlEntities(): String =
        if (indexOf('&') >= 0) StringEscapeUtils.unescapeHtml4(this) else this

    suspend fun resolveMedia(
        source: BookSource,
        book: Book,
        chapter: BookChapter,
    ): ResolvedMedia {
        repairInstalledSourceIfNeeded(source)
        val executor = executor(source)
        val absoluteChapterUrl = chapter.getAbsoluteURL()
        val chapScript = executor.resolveScript("chap", "chap.js")
        val chapData = if (executor.hasScript(chapScript)) {
            executeEnvelope(source, executor, chapScript, arrayOf(absoluteChapterUrl)).opt("data")
        } else {
            absoluteChapterUrl
        }
        val candidates = VbookMediaParser.parseServers(
            json = chapData.toJsonText(),
            fallbackUrl = absoluteChapterUrl,
        )
        if (candidates.isEmpty()) {
            throw VbookPluginException("Plugin VBook không trả về máy chủ media hợp lệ")
        }
        val defaultKind = when {
            source.bookSourceType == BookSourceType.video ->
                MediaContentKind.VIDEO
            source.bookSourceType == BookSourceType.audio ->
                MediaContentKind.AUDIO
            else -> MediaContentKind.UNKNOWN
        }
        val trackScript = executor.resolveScript("track", "track.js")
        val variants = arrayListOf<io.legado.app.domain.model.ResolvedMediaVariant>()
        val subtitles = arrayListOf<io.legado.app.domain.model.ResolvedSubtitleTrack>()
        val audioTracks = arrayListOf<io.legado.app.domain.model.ResolvedAudioTrack>()
        candidates.forEachIndexed { index, candidate ->
            coroutineContext.ensureActive()
            val trackData = if (executor.hasScript(trackScript)) {
                try {
                    executeEnvelope(
                        source = source,
                        executor = executor,
                        scriptName = trackScript,
                        args = arrayOf(candidate.data),
                    ).also { envelope ->
                        recordRuntimeCapabilities(source, "track", envelope.toString())
                    }.opt("data")
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                }
            } else {
                candidate.data
            }
            val parsed = VbookMediaParser.parseTrack(
                json = trackData.toJsonText(),
                candidate = candidate,
                defaultKind = defaultKind,
                idPrefix = "${chapter.url}\u0000$index",
            )
            variants += parsed.variants
            subtitles += parsed.subtitles
            audioTracks += parsed.audioTracks
        }
        val distinctVariants = variants.distinctBy { "${it.uri}\u0000${it.title}" }
        if (distinctVariants.isEmpty()) {
            throw VbookPluginException("Plugin VBook không phân giải được URL media")
        }
        return ResolvedMedia(
            sourceId = source.bookSourceUrl,
            contentId = chapter.url,
            title = chapter.title.ifBlank { book.name },
            variants = distinctVariants,
            subtitles = subtitles.distinctBy { it.uri },
            audioTracks = audioTracks.distinctBy { it.uri },
            resolvedAt = System.currentTimeMillis(),
        )
    }

    private fun executor(source: BookSource): VbookScriptExecutor {
        val pluginId = pluginId(source)
            ?: throw VbookPluginException("Nguồn không chứa mã plugin VBook hợp lệ")
        return VbookExecutor(appCtx, pluginId, okHttpClient)
    }

    private suspend fun repairInstalledSourceIfNeeded(source: BookSource) {
        runCatching {
            withContext(Dispatchers.IO) {
                if (VbookPluginImporter.reconcileInstalledSourceType(appCtx, source)) {
                    appDb.bookSourceDao.update(source)
                }
            }
        }
    }

    private suspend fun syncBookTypeIfNeeded(source: BookSource, book: Book) {
        if (book.config.fixedType) return
        val oldType = book.type
        book.removeAllBookType()
        book.addType(source.getBookType())
        if (book.type == oldType) return
        runCatching {
            withContext(Dispatchers.IO) {
                appDb.bookDao.update(book)
            }
        }
    }

    private fun pluginId(source: BookSource): String? = source.bookSourceUrl
        .takeIf { it.startsWith(SOURCE_PREFIX) }
        ?.removePrefix(SOURCE_PREFIX)
        ?.takeIf { it.matches(Regex("[a-f0-9]{16,64}")) }

    private fun recordRuntimeCapabilities(
        source: BookSource,
        role: String,
        resultJson: String,
    ) {
        val id = pluginId(source) ?: return
        val directory = File(appCtx.filesDir, "vbook_plugins/$id")
        runCatching {
            val profile = VbookPluginInspector.loadOrInspect(directory, id)
            val merged = VbookPluginInspector.mergeRuntimeResult(profile, role, resultJson)
            if (merged !== profile) {
                VbookPluginInspector.writeProfile(directory, merged)
            }
        }
    }

    private fun configUrl(source: BookSource): String {
        val id = pluginId(source) ?: return source.bookSourceUrl
        return runCatching {
            JSONObject(File(appCtx.filesDir, "vbook_plugins/$id/plugin.json").readText())
                .optJSONObject("metadata")
                ?.optString("source")
                .orEmpty()
                .ifBlank { source.bookSourceUrl }
        }.getOrDefault(source.bookSourceUrl)
    }

    private fun executeArray(
        source: BookSource,
        executor: VbookScriptExecutor,
        scriptName: String,
        args: Array<Any?>,
    ): JSONArray {
        val data = executeEnvelope(source, executor, scriptName, args).opt("data")
        dataArray(data)?.let { return it }
        if (data !is JSONArray) throw VbookPluginException("$scriptName phải trả về data dạng mảng")
        return data
    }

    private fun dataArray(value: Any?): JSONArray? {
        return when (value) {
            is JSONArray -> value
            is JSONObject -> {
                arrayPayloadKeys.asSequence()
                    .map { value.opt(it) }
                    .filter { it != null && it != JSONObject.NULL && it !== value }
                    .mapNotNull(::dataArray)
                    .firstOrNull()
            }

            is String -> {
                val text = value.trim()
                if (text.isBlank()) return JSONArray()
                runCatching {
                    when (val parsed = JSONTokener(text).nextValue()) {
                        is JSONArray -> parsed
                        is JSONObject -> dataArray(parsed)
                        else -> null
                    }
                }.getOrNull()
            }

            null, JSONObject.NULL -> JSONArray()
            else -> null
        }
    }

    private fun executeEnvelope(
        source: BookSource,
        executor: VbookScriptExecutor,
        scriptName: String,
        args: Array<Any?>,
    ): JSONObject {
        if (!executor.hasScript(scriptName)) {
            throw VbookPluginException("Không tìm thấy script VBook bắt buộc: $scriptName")
        }
        val configValues = runtimeConfigValues(source)
        val configOverride = configValues.remove("CONFIG_URL").orEmpty()
        val raw = executor.executeScript(
            scriptName = scriptName,
            functionName = "execute",
            args = args,
            configUrl = configOverride,
            configValues = configValues,
        )
        return parseEnvelope(scriptName, raw)
    }

    private fun runtimeConfigValues(source: BookSource): MutableMap<String, String> {
        val loginUi = source.loginUi?.takeIf(String::isNotBlank) ?: return mutableMapOf()
        return runCatching(source::getLoginInfoMap).getOrElse {
            GSON.fromJsonArray<RowUi>(loginUi).getOrNull()
                ?.filter { row -> row.type != RowUi.Type.button }
                ?.associateTo(mutableMapOf()) { row -> row.name to row.default.orEmpty() }
                ?: mutableMapOf()
        }
    }

    private fun parseEnvelope(scriptName: String, raw: String): JSONObject {
        if (raw.isBlank()) throw VbookPluginException("$scriptName returned an empty result")
        val parsed = try {
            JSONTokener(raw).nextValue()
        } catch (error: JSONException) {
            throw VbookPluginException("$scriptName returned invalid JSON", error)
        }
        val envelope = when (parsed) {
            is JSONObject -> parsed.normalizedEnvelope(scriptName)
            is JSONArray -> JSONObject().put("success", true).put("data", parsed)
            else -> JSONObject().put("success", true).put("data", parsed)
        }
        if (!envelope.optBoolean("success", false)) {
            throw VbookPluginException(envelope.firstText("message", "error", "data") ?: "$scriptName returned an error")
        }
        return envelope
    }

    private fun JSONObject.normalizedEnvelope(scriptName: String): JSONObject {
        val success = when {
            has("success") -> optBoolean("success", false)
            has("code") -> opt("code").isSuccessCode()
            has("status") -> opt("status").isSuccessCode()
            else -> true
        }
        if (!success) {
            return JSONObject()
                .put("success", false)
                .put("message", firstText("message", "error", "data") ?: "$scriptName returned an error")
        }
        val normalized = JSONObject(toString()).put("success", true)
        if (!normalized.has("data")) {
            val fallbackData = firstValue(
                "result",
                "results",
                "items",
                "list",
                "books",
                "chapters",
                "chapterList",
                "chapter_list",
                "episodes",
                "episodeList",
                "episode_list",
            )
            normalized.put("data", fallbackData ?: JSONObject.NULL)
        }
        return normalized
    }

    private fun Any?.isSuccessCode(): Boolean = when (this) {
        is Number -> toInt() == 0 || toInt() in 200..299
        is String -> trim() == "0" || trim().toIntOrNull() in 200..299
        else -> false
    }

    private fun parseBooks(source: BookSource, data: JSONArray): ArrayList<SearchBook> {
        val result = arrayListOf<SearchBook>()
        for (index in 0 until data.length()) {
            val item = data.requireObject(index, "book list")
            val host = item.optString("host").ifBlank { configUrl(source) }
            val bookUrl = resolveUrl(item.requireAnyText("book list", "link", "url", "href", "bookUrl"), host)
            result += SearchBook(
                bookUrl = bookUrl,
                origin = source.bookSourceUrl,
                originName = source.bookSourceName,
                name = item.requireAnyText("book list", "name", "title"),
                author = item.firstText("author", "description").orEmpty(),
                coverUrl = item.firstText("cover", "coverUrl")?.let { resolveUrl(it, host) },
                intro = item.firstText("intro", "content"),
                kind = item.firstText("category", "kind", "genres"),
                tocUrl = bookUrl,
                type = source.getBookType(),
            )
        }
        return result
    }

    private fun JSONArray.requireObject(index: Int, scriptName: String): JSONObject =
        optJSONObject(index)
            ?: throw VbookPluginException("$scriptName có phần tử thứ $index không phải object")

    private fun JSONArray.stringValues(scriptName: String): List<String> = buildList {
        for (index in 0 until length()) {
            val value = opt(index)
            if (value !is String || value.isBlank()) {
                throw VbookPluginException("$scriptName chứa URL trang không hợp lệ")
            }
            add(value)
        }
    }

    private fun JSONObject.requireAnyText(scriptName: String, vararg keys: String): String =
        firstText(*keys) ?: throw VbookPluginException("$scriptName is missing ${keys.first()}")

    private fun JSONObject.requireText(key: String, scriptName: String): String =
        optString(key).takeIf(String::isNotBlank)
            ?: throw VbookPluginException("$scriptName thiếu trường $key hợp lệ")

    private fun JSONObject.firstText(vararg keys: String): String? = keys.asSequence()
        .map(::optString)
        .firstOrNull(String::isNotBlank)

    private fun JSONObject.firstValue(vararg keys: String): Any? = keys.asSequence()
        .map(::opt)
        .firstOrNull { it != null && it != JSONObject.NULL }

    private fun resolveUrl(raw: String, baseUrl: String): String {
        val value = raw.trim()
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        val cleanBase = baseUrl.trimEnd('/') + "/"
        return runCatching { URI(cleanBase).resolve(value).toString() }
            .getOrElse {
                if (value.startsWith('/')) baseUrl.trimEnd('/') + value else cleanBase + value
            }
    }

    private fun resolveImageUrl(raw: String, chapterUrl: String, sourceUrl: String): String {
        val value = raw.trim()
        if (
            value.startsWith("data:", ignoreCase = true) ||
            value.startsWith("content:", ignoreCase = true) ||
            value.startsWith("file:", ignoreCase = true)
        ) {
            return value
        }
        if (value.startsWith("//")) return "https:$value"
        val baseUrl = chapterUrl.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: sourceUrl
        return resolveUrl(value, baseUrl)
    }

    private fun isPotentialImageReference(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("//") ||
            lower.startsWith("data:image/") ||
            lower.startsWith("content://") ||
            lower.startsWith("file://") ||
            imageExtensionRegex.containsMatchIn(lower) ||
            (lower.startsWith("/") && lower.length > 1)
    }

    private fun chapterTitleFromUrl(url: String, index: Int): String =
        url.substringBefore('?')
            .trimEnd('/')
            .substringAfterLast('/')
            .takeIf(String::isNotBlank)
            ?: "Chapter ${index + 1}"

    const val SCHEME = "vbook"
    const val SOURCE_PREFIX = "$SCHEME://plugin/"

    private const val CHAPTER_INPUT_KEY = "vbookChapterInput"
    private val arrayPayloadKeys = listOf(
        "data",
        "result",
        "results",
        "items",
        "item",
        "list",
        "books",
        "bookList",
        "book_list",
        "chapters",
        "chapterList",
        "chapter_list",
        "episodes",
        "episodeList",
        "episode_list",
    )
    private val imageContainerKeys = listOf(
        "images",
        "imgs",
        "pages",
        "pictures",
        "photos",
        "items",
        "list",
        "data",
        "content",
        "html",
        "body",
        "chapter",
    )
    private val imageObjectPrimaryKeys = listOf(
        "data-original",
        "data-src",
        "data-lazy-src",
        "data-sv1",
        "original",
        "image",
        "img",
        "src",
        "url",
        "link",
        "href",
    )
    private val imageObjectFallbackKeys = listOf(
        "fallback",
        "fallbacks",
        "backup",
        "backups",
        "data-sv2",
        "data-sv3",
        "data-sv4",
    )
    private val htmlContentMarkerRegex = Regex(
        "<\\s*/?\\s*(?:p|br|div|span|section|article|blockquote|h[1-6]|ul|ol|li|table|tr|td|font|strong|em|b|i|u|a)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val htmlCommentRegex = Regex("<!--.*?-->", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val htmlBreakRegex = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val htmlBlockTagRegex = Regex(
        "</?\\s*(?:p|div|section|article|blockquote|h[1-6]|ul|ol|li|table|tr|td|hr|dd|dl)\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val htmlTagRegex = Regex(
        "</?\\s*[a-z][a-z0-9:-]*\\b[^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val imageSrcRegex = Regex(
        "<img\\b[^>]*(?:data-src|data-original|src)\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s>]+))[^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val imageUrlInTextRegex = Regex(
        "(?:https?:)?//[^\\s\"'<>]+?\\.(?:jpg|jpeg|png|webp|gif|avif)(?:[^\\s\"'<>]*)?",
        RegexOption.IGNORE_CASE,
    )
    private val imageExtensionRegex = Regex(
        "\\.(?:jpg|jpeg|png|webp|gif|avif)(?:[?#].*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val urlOptionRegex = Regex(",\\s*\\{")
}

private fun Any?.toJsonText(): String = when (this) {
    null, JSONObject.NULL -> "null"
    is JSONObject, is JSONArray -> toString()
    else -> GSON.toJson(this)
}

enum class VbookPluginErrorKind {
    AUTH_REQUIRED,
    RATE_LIMITED,
    NETWORK,
    INVALID_RESPONSE,
    PLUGIN,
}

class VbookPluginException(
    message: String,
    cause: Throwable? = null,
    val kind: VbookPluginErrorKind = classifyVbookPluginError(message, cause),
) : RuntimeException(message, cause)

private fun classifyVbookPluginError(
    message: String,
    cause: Throwable?,
): VbookPluginErrorKind {
    val normalized = message.lowercase()
    if (listOf(
            "401",
            "403",
            "unauthorized",
            "forbidden",
            "access denied",
            "đăng nhập",
            "dang nhap",
            "authorization",
            "access token",
            "phiên đăng nhập",
            "session expired",
        ).any(normalized::contains)
    ) return VbookPluginErrorKind.AUTH_REQUIRED
    if (listOf("429", "rate limit", "too many requests", "quá nhiều yêu cầu")
            .any(normalized::contains)
    ) return VbookPluginErrorKind.RATE_LIMITED
    if (cause is java.net.SocketTimeoutException || cause is java.io.IOException) {
        return VbookPluginErrorKind.NETWORK
    }
    if (listOf("invalid json", "empty result", "invalid response", "nội dung trống")
            .any(normalized::contains)
    ) return VbookPluginErrorKind.INVALID_RESPONSE
    return VbookPluginErrorKind.PLUGIN
}
