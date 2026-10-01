package io.legado.app.model.localBook

import androidx.core.net.toUri
import com.drducbook.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.FileDoc
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.StringUtils
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import splitties.init.appCtx
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Parser for document formats that are commonly selected from the system file picker but cannot
 * be consumed by [TextFile] as raw bytes. The output is deliberately plain text: NMT and the
 * reader must receive prose, not HTML/Markdown/XML markup or the private HTML markers emitted by
 * the EPUB formatter.
 */
object ExternalDocumentFile : BaseLocalBookParse {

    internal data class ParsedChapter(
        val title: String,
        val content: String,
    )

    internal data class ParsedDocument(
        val fingerprint: String,
        val title: String?,
        val author: String?,
        val intro: String? = null,
        val chapters: List<ParsedChapter>,
    )

    private var cached: Pair<String, ParsedDocument>? = null

    fun supports(book: Book): Boolean {
        return when (extension(book.originName)) {
            "html", "htm", "md", "markdown", "docx", "doc" -> true
            else -> false
        }
    }

    @Synchronized
    override fun upBookInfo(book: Book) {
        val parsed = parseCached(book)
        parsed.title?.takeIf(String::isNotBlank)?.let { title ->
            if (book.name.isBlank() || book.name == baseName(book.originName)) book.name = title
        }
        parsed.author?.takeIf(String::isNotBlank)?.let { author ->
            if (book.author.isBlank()) book.author = author
        }
        if (book.intro.isNullOrBlank()) {
            book.intro = parsed.intro ?: parsed.chapters.firstOrNull()?.content?.take(500)
        }
    }

    @Synchronized
    override fun getChapterList(book: Book): ArrayList<BookChapter> {
        val parsed = parseCached(book)
        return ArrayList(parsed.chapters.mapIndexed { index, chapter ->
            BookChapter(
                url = MD5Utils.md5Encode16("${book.originName}:$index:${chapter.title}"),
                title = chapter.title,
                bookUrl = book.bookUrl,
                index = index,
                start = 0L,
                end = chapter.content.length.toLong(),
                wordCount = StringUtils.wordCountFormat(chapter.content.length),
            )
        })
    }

    @Synchronized
    override fun getContent(book: Book, chapter: BookChapter): String? {
        return parseCached(book).chapters.getOrNull(chapter.index)?.content
    }

    override fun getImage(book: Book, href: String): InputStream? = null

    @Synchronized
    private fun parseCached(book: Book): ParsedDocument {
        val fingerprint = sourceFingerprint(book)
        cached?.takeIf { it.first == book.bookUrl && it.second.fingerprint == fingerprint }
            ?.let { return it.second }
        val parsed = parse(book, fingerprint)
        cached = book.bookUrl to parsed
        return parsed
    }

    private fun parse(book: Book, fingerprint: String): ParsedDocument {
        val format = extension(book.originName)
        val fallbackTitle = baseName(book.originName).ifBlank {
            runCatching { appCtx.getString(R.string.untitled_book) }.getOrDefault("Untitled Book")
        }
        return LocalBook.getBookInputStream(book).use { input ->
            when (format) {
                "html", "htm" -> parseHtml(input, fallbackTitle, fingerprint)
                "md", "markdown" -> parseMarkdown(input, fallbackTitle, fingerprint)
                "docx" -> parseDocx(input, fallbackTitle, fingerprint)
                "doc" -> parseDoc(input, fallbackTitle, fingerprint)
                else -> error("Unsupported external document format: $format")
            }
        }
    }

    internal fun parseHtml(
        input: InputStream,
        fallbackTitle: String,
        fingerprint: String,
    ): ParsedDocument {
        val document = Jsoup.parse(input, null, "")
        val intro = document.select("meta[name=description], meta[property=og:description], meta[property=book:description], meta[name=intro]")
            .firstOrNull()?.attr("content")?.trim()
            ?: document.select(".intro, #intro, .description, #description, .book-intro, .book-summary")
                .firstOrNull()?.text()?.trim()
        return ParsedDocument(
            fingerprint = fingerprint,
            title = document.title().trim().takeIf(String::isNotBlank),
            author = document.select("meta[name=author], meta[property=book:author]")
                .firstOrNull()?.attr("content")?.trim(),
            intro = intro?.takeIf(String::isNotBlank),
            chapters = splitChapters(stripHtmlForTranslation(document), fallbackTitle, detectExplicitChapters = true),
        )
    }

    internal fun stripHtmlForTranslation(document: org.jsoup.nodes.Document): String {
        val root = document.body().takeIf { it.children().isNotEmpty() } ?: document
        return normalizeText(buildString { appendHtml(root, this) })
    }

    private fun parseMarkdown(
        input: InputStream,
        fallbackTitle: String,
        fingerprint: String,
    ): ParsedDocument {
        val source = decodeText(input.readBytes())
        val frontMatter = parseFrontMatter(source)
        return ParsedDocument(
            fingerprint = fingerprint,
            title = frontMatter.title ?: markdownTitle(source),
            author = frontMatter.author,
            chapters = splitChapters(stripMarkdownForTranslation(frontMatter.body), fallbackTitle),
        )
    }

    internal fun stripMarkdownForTranslation(source: String): String = markdownToText(source)

    private fun parseDocx(
        input: InputStream,
        fallbackTitle: String,
        fingerprint: String,
    ): ParsedDocument {
        val entries = readDocxEntries(input)
        val documentXml = entries["word/document.xml"]
            ?: error("DOCX document.xml is missing")
        val document = Jsoup.parse(String(documentXml, Charsets.UTF_8), "", Parser.xmlParser())
        val rendered = buildString {
            document.getAllElements()
                .filter { localName(it.tagName()) == "p" }
                .forEach { paragraph ->
                    val text = docxParagraphText(paragraph)
                    if (text.isNotBlank()) {
                        val style = paragraph.getAllElements()
                            .firstOrNull { localName(it.tagName()) == "pStyle" }
                            ?.attributeValue("val")
                            .orEmpty()
                        if (style.contains("heading", ignoreCase = true) ||
                            style.equals("title", ignoreCase = true) ||
                            style.equals("subtitle", ignoreCase = true)
                        ) {
                            append("\n\n# ").append(text.trim()).append('\n')
                        } else {
                            append(text.trim()).append("\n\n")
                        }
                    }
                }
        }
        val core = entries["docProps/core.xml"]?.let { Jsoup.parse(String(it, Charsets.UTF_8), "", Parser.xmlParser()) }
        return ParsedDocument(
            fingerprint = fingerprint,
            title = core?.firstStringByLocalName("title"),
            author = core?.firstStringByLocalName("creator"),
            chapters = splitChapters(normalizeText(rendered), fallbackTitle),
        )
    }

    private fun parseDoc(
        input: InputStream,
        fallbackTitle: String,
        fingerprint: String,
    ): ParsedDocument {
        val text = HWPFDocument(input).use { document ->
            WordExtractor(document).use { extractor -> extractor.text }
        }
        return ParsedDocument(
            fingerprint = fingerprint,
            title = null,
            author = null,
            chapters = splitChapters(normalizeText(text), fallbackTitle, detectExplicitChapters = true),
        )
    }

    private fun readDocxEntries(input: InputStream): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory &&
                    (entry.name == "word/document.xml" || entry.name == "docProps/core.xml")
                ) {
                    result[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun docxParagraphText(paragraph: Element): String = buildString {
        paragraph.getAllElements().drop(1).forEach { element ->
            when (localName(element.tagName())) {
                "t" -> append(element.text())
                "tab" -> append('\t')
                "br", "cr" -> append('\n')
            }
        }
    }

    private fun appendHtml(node: Node, output: StringBuilder) {
        when (node) {
            is TextNode -> output.append(node.getWholeText())
            is Element -> {
                val tag = node.tagName().lowercase(Locale.ROOT)
                if (tag in setOf("script", "style", "head", "noscript", "svg")) return
                if (tag == "img") {
                    node.attr("alt").takeIf(String::isNotBlank)?.let { output.append(it) }
                    return
                }
                val isHeading = tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6'
                val isBlock = isHeading || tag in HTML_BLOCK_TAGS ||
                    tag == "usehtml" || tag.startsWith("legado-")
                if (isHeading) output.append("\n\n# ")
                else if (isBlock) output.append('\n')
                node.childNodes().forEach { child -> appendHtml(child, output) }
                if (isBlock) output.append('\n')
            }
        }
    }

    private fun markdownToText(source: String): String {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
        val output = StringBuilder()
        var fenced = false
        lines.forEach { original ->
            val line = original.trimEnd()
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                fenced = !fenced
                if (!fenced) output.append('\n')
                return@forEach
            }
            if (line.trim() == "---" && output.isEmpty()) return@forEach
            val heading = Regex("^\\s{0,3}#{1,6}\\s+(.+?)\\s*#*\\s*$").find(line)
            val converted = when {
                heading != null -> "# ${stripMarkdownInline(heading.groupValues[1])}"
                line.trimStart().startsWith(">") -> stripMarkdownInline(
                    line.trimStart().removePrefix(">")
                )
                else -> stripMarkdownInline(
                    line.replace(Regex("^\\s*(?:[-+*]|\\d+[.)])\\s+"), "• ")
                )
            }
            output.append(converted).append('\n')
        }
        return output.toString()
    }

    private fun stripMarkdownInline(value: String): String {
        return value
            .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("(`{1,3})(.*?)\\1"), "$2")
            .replace(Regex("(\\*\\*|__)(.*?)\\1"), "$2")
            .replace(Regex("(?<!\\w)([*_~])(.*?)(\\1)(?!\\w)"), "$2")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
    }

    private fun splitChapters(
        text: String,
        fallbackTitle: String,
        detectExplicitChapters: Boolean = false,
    ): List<ParsedChapter> {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return listOf(ParsedChapter(fallbackTitle, ""))
        val chapters = mutableListOf<ParsedChapter>()
        var currentTitle = fallbackTitle
        val currentBody = StringBuilder()
        var hasHeading = false

        fun flush() {
            val content = normalizeText(currentBody.toString())
            if (content.isNotBlank()) {
                val introTitle = runCatching { appCtx.getString(R.string.ebook_editor_description) }
                    .getOrDefault("Introduction")
                chapters += ParsedChapter(
                    title = if (hasHeading) currentTitle else introTitle,
                    content = content,
                )
            }
            currentBody.clear()
        }

        normalized.lines().forEach { line ->
            val heading = Regex("^#{1,6}\\s+(.+)$").matchEntire(line.trim())
            val explicit = detectExplicitChapters && EXPLICIT_CHAPTER_REGEX.matches(line.trim())
            if (heading != null || explicit) {
                flush()
                currentTitle = (heading?.groupValues?.getOrNull(1) ?: line).trim()
                hasHeading = true
            } else {
                currentBody.append(line).append('\n')
            }
        }
        flush()
        return chapters.ifEmpty { listOf(ParsedChapter(fallbackTitle, normalized)) }
    }

    private fun parseFrontMatter(source: String): FrontMatter {
        if (!source.trimStart().startsWith("---")) return FrontMatter(null, null, source)
        val start = source.indexOf("---")
        val end = source.indexOf("\n---", start + 3)
        if (end < 0) return FrontMatter(null, null, source)
        var title: String? = null
        var author: String? = null
        source.substring(start + 3, end).lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().lowercase(Locale.ROOT)
            val value = line.substring(separator + 1).trim().trim('"', '\'')
            when (key) {
                "title" -> title = value
                "author", "creator" -> author = value
            }
        }
        return FrontMatter(title, author, source.substring(end + 4))
    }

    private fun markdownTitle(source: String): String? = source.lineSequence()
        .mapNotNull { Regex("^\\s*#\\s+(.+?)\\s*$").matchEntire(it)?.groupValues?.get(1) }
        .firstOrNull()
        ?.let(::stripMarkdownInline)

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val charset = when {
            bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) -> Charsets.UTF_16LE
            bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) -> Charsets.UTF_16BE
            bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) -> Charsets.UTF_8
            else -> runCatching { Charset.forName(EncodingDetect.getEncode(bytes)) }
                .getOrDefault(Charsets.UTF_8)
        }
        return String(bytes, charset).removePrefix("\uFEFF")
    }

    private fun normalizeText(value: String): String {
        val lines = value.replace('\u00A0', ' ').replace("\r\n", "\n").replace('\r', '\n')
            .lineSequence()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
        val output = StringBuilder()
        var blankLines = 0
        lines.forEach { line ->
            if (line.isBlank()) {
                if (blankLines < 2 && output.isNotEmpty()) {
                    output.append('\n')
                    blankLines++
                }
            } else {
                output.append(line).append('\n')
                blankLines = 0
            }
        }
        return output.toString().trim()
    }

    private fun sourceFingerprint(book: Book): String {
        return runCatching {
            val doc = FileDoc.fromUri(book.bookUrl.toUri(), false)
            "${doc.lastModified}:${doc.size}"
        }.getOrDefault(book.bookUrl)
    }

    private fun extension(name: String): String = name.substringAfterLast('.', "")
        .lowercase(Locale.ROOT)

    private fun baseName(name: String): String = name.substringBeforeLast('.', name).trim()

    private fun localName(name: String): String = name.substringAfterLast(':').lowercase(Locale.ROOT)

    private fun Element.attributeValue(attributeLocalName: String): String? = attributes()
        .asList()
        .firstOrNull { localName(it.key) == attributeLocalName }
        ?.value

    private fun org.jsoup.nodes.Document.firstStringByLocalName(name: String): String? =
        getAllElements().firstOrNull { localName(it.tagName()) == name }
            ?.text()?.trim()?.takeIf(String::isNotBlank)

    private data class FrontMatter(
        val title: String?,
        val author: String?,
        val body: String,
    )

    private val HTML_BLOCK_TAGS = setOf(
        "address", "article", "aside", "blockquote", "br", "dd", "div", "dl", "dt",
        "figcaption", "figure", "footer", "form", "header", "hr", "li", "main", "nav",
        "ol", "p", "pre", "section", "table", "td", "th", "tr", "ul",
    )
    private val EXPLICIT_CHAPTER_REGEX = Regex(
        "^(?:第\\s*[0-9一二三四五六七八九十百千万零〇]+\\s*[章节卷回]|(?:chapter|part)\\s+\\d+\\b).*$",
        RegexOption.IGNORE_CASE,
    )
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
