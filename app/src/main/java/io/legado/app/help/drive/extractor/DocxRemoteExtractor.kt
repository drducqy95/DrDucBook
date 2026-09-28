package io.legado.app.help.drive.extractor

import io.legado.app.lib.webdav.Authorization
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

object DocxRemoteExtractor {

    suspend fun extract(
        url: String,
        auth: Authorization?,
        fileSize: Long
    ): RemoteExtractedMetadata? = try {
        val entries = ZipRemoteHelper.readCentralDirectory(url, auth, fileSize) ?: return null

        // 1. Locate docProps/core.xml
        val coreEntry = entries.firstOrNull { it.name.equals("docProps/core.xml", ignoreCase = true) }
        var title: String? = null
        var author: String? = null
        var intro: String? = null

        if (coreEntry != null) {
            val coreBytes = ZipRemoteHelper.extractEntryBytes(url, auth, coreEntry)
            if (coreBytes != null) {
                val doc = Jsoup.parse(String(coreBytes, Charsets.UTF_8), "", Parser.xmlParser())
                title = doc.getTagText("dc:title", "title")
                author = doc.getTagText("dc:creator", "creator")
                intro = doc.getTagText("dc:description", "description", "cp:subject", "subject", "cp:keywords", "keywords")
            }
        }

        // 2. Locate thumbnail image
        val thumbEntry = entries.firstOrNull { it.name.startsWith("docProps/thumbnail", ignoreCase = true) }
            ?: entries.firstOrNull { it.name.matches(Regex("word/media/image1\\.(jpe?g|png)", RegexOption.IGNORE_CASE)) }

        var coverUri: String? = null
        if (thumbEntry != null) {
            val imageBytes = ZipRemoteHelper.extractEntryBytes(url, auth, thumbEntry)
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                val coverKey = "$url#docxthumb"
                val savedPath = RemoteCoverCache.saveCoverBytes(coverKey, imageBytes)
                if (savedPath != null) {
                    coverUri = "file://$savedPath"
                }
            }
        }

        RemoteExtractedMetadata(
            title = title,
            author = author,
            intro = intro,
            coverUrl = coverUri,
            format = "docx"
        )
    } catch (e: Throwable) {
        null
    }

    private fun org.jsoup.nodes.Element.getTagText(vararg tags: String): String? {
        for (tag in tags) {
            val text = getElementsByTag(tag).firstOrNull()?.text()?.trim()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }
}
