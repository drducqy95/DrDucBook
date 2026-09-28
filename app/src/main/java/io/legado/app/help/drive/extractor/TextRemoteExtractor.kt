package io.legado.app.help.drive.extractor

import io.legado.app.lib.webdav.Authorization
import org.jsoup.Jsoup

object TextRemoteExtractor {

    private val mdFrontMatterRegex = Regex("""^---\s*[\r\n]+([\s\S]*?)[\r\n]+---""", RegexOption.MULTILINE)
    private val mdTitleRegex = Regex("""^#\s+(.+)$""", RegexOption.MULTILINE)
    private val mdImageRegex = Regex("""!\[.*?\]\((https?://[^\s)]+)\)""")

    private val txtTitleRegex = Regex("""(?:书名|Tên truyện|Tên sách|Tiêu đề)[:：\s]+([^\r\n]+)""", RegexOption.IGNORE_CASE)
    private val txtAuthorRegex = Regex("""(?:作者|Tác giả)[:：\s]+([^\r\n]+)""", RegexOption.IGNORE_CASE)
    private val txtIntroRegex = Regex("""(?:简介|内容简介|Giới thiệu|Tóm tắt)[:：\s]+([\s\S]{10,200})""", RegexOption.IGNORE_CASE)
    private val txtBookGuillemetRegex = Regex("""《([^》]+)》""")

    suspend fun extract(
        url: String,
        auth: Authorization?,
        format: String,
        fallbackFilename: String
    ): RemoteExtractedMetadata {
        val headBytes = RemoteRangeExtractor.readHead(url, auth, 4096)
        if (headBytes == null || headBytes.isEmpty()) {
            return RemoteExtractedMetadata(
                title = fallbackFilename.substringBeforeLast("."),
                format = format
            )
        }

        val text = String(headBytes, Charsets.UTF_8)

        return when (format.lowercase()) {
            "html", "htm" -> extractHtml(text, fallbackFilename)
            "md", "markdown" -> extractMarkdown(text, fallbackFilename)
            "txt" -> extractTxt(text, fallbackFilename)
            "doc" -> RemoteExtractedMetadata(
                title = fallbackFilename.substringBeforeLast("."),
                format = "doc"
            )
            else -> RemoteExtractedMetadata(
                title = fallbackFilename.substringBeforeLast("."),
                format = format
            )
        }
    }

    internal fun extractHtml(html: String, fallbackFilename: String): RemoteExtractedMetadata {
        val doc = Jsoup.parse(html)
        val title = doc.title().takeIf { it.isNotBlank() }
            ?: doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotBlank() }
            ?: fallbackFilename.substringBeforeLast(".")

        val author = doc.select("meta[name=author]").attr("content").takeIf { it.isNotBlank() }
            ?: doc.select("meta[property=book:author]").attr("content").takeIf { it.isNotBlank() }

        val intro = doc.select("meta[name=description]").attr("content").takeIf { it.isNotBlank() }
            ?: doc.select("meta[property=og:description]").attr("content").takeIf { it.isNotBlank() }

        val coverUrl = doc.select("meta[property=og:image]").attr("content").takeIf { it.isNotBlank() }
            ?: doc.select("meta[name=twitter:image]").attr("content").takeIf { it.isNotBlank() }

        return RemoteExtractedMetadata(
            title = title,
            author = author,
            intro = intro,
            coverUrl = coverUrl,
            format = "html"
        )
    }

    internal fun extractMarkdown(text: String, fallbackFilename: String): RemoteExtractedMetadata {
        var title: String? = null
        var author: String? = null
        var intro: String? = null
        var coverUrl: String? = null

        val frontMatterMatch = mdFrontMatterRegex.find(text)
        if (frontMatterMatch != null) {
            val fmLines = frontMatterMatch.groupValues[1].lines()
            for (line in fmLines) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim().trim('"', '\'')
                    when (key) {
                        "title" -> title = value
                        "author", "creator" -> author = value
                        "description", "intro", "summary" -> intro = value
                        "cover", "image", "thumbnail" -> coverUrl = value
                    }
                }
            }
        }

        if (title.isNullOrBlank()) {
            title = mdTitleRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        }
        if (title.isNullOrBlank()) {
            title = fallbackFilename.substringBeforeLast(".")
        }

        if (coverUrl.isNullOrBlank()) {
            coverUrl = mdImageRegex.find(text)?.groupValues?.getOrNull(1)
        }

        return RemoteExtractedMetadata(
            title = title,
            author = author,
            intro = intro,
            coverUrl = coverUrl,
            format = "md"
        )
    }

    private fun extractTxt(text: String, fallbackFilename: String): RemoteExtractedMetadata {
        var title = txtTitleRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        if (title.isNullOrBlank()) {
            title = txtBookGuillemetRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        }
        if (title.isNullOrBlank()) {
            title = fallbackFilename.substringBeforeLast(".")
        }

        val author = txtAuthorRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        val intro = txtIntroRegex.find(text)?.groupValues?.getOrNull(1)?.trim()

        return RemoteExtractedMetadata(
            title = title,
            author = author,
            intro = intro,
            format = "txt"
        )
    }
}
