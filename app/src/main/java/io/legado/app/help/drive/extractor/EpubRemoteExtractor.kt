package io.legado.app.help.drive.extractor

import io.legado.app.lib.webdav.Authorization
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

object EpubRemoteExtractor {

    suspend fun extract(
        url: String,
        auth: Authorization?,
        fileSize: Long
    ): RemoteExtractedMetadata? = try {
        val entries = ZipRemoteHelper.readCentralDirectory(url, auth, fileSize) ?: return null

        // 1. Locate OPF file
        var opfEntry = entries.firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
        if (opfEntry == null) {
            val containerEntry = entries.firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }
            if (containerEntry != null) {
                val containerBytes = ZipRemoteHelper.extractEntryBytes(url, auth, containerEntry)
                if (containerBytes != null) {
                    val containerDoc = Jsoup.parse(String(containerBytes, Charsets.UTF_8), "", Parser.xmlParser())
                    val rootfile = containerDoc.select("rootfile[full-path]").firstOrNull()?.attr("full-path")
                    if (!rootfile.isNullOrBlank()) {
                        opfEntry = entries.firstOrNull { it.name.equals(rootfile, ignoreCase = true) }
                    }
                }
            }
        }

        if (opfEntry == null) return null

        // 2. Extract OPF content
        val opfBytes = ZipRemoteHelper.extractEntryBytes(url, auth, opfEntry) ?: return null
        val opfXml = String(opfBytes, Charsets.UTF_8)
        val doc = Jsoup.parse(opfXml, "", Parser.xmlParser())

        val title = doc.getTagText("dc:title", "title")
        val author = doc.getTagText("dc:creator", "creator")
        val intro = doc.getTagText("dc:description", "description")

        // 3. Locate Cover image href
        var coverHref: String? = null
        val coverPropItem = doc.select("manifest > item[properties*=cover-image]").firstOrNull()
        if (coverPropItem != null) {
            coverHref = coverPropItem.attr("href")
        } else {
            val coverMeta = doc.select("metadata > meta[name=cover]").firstOrNull()
            val coverId = coverMeta?.attr("content")?.trim()
            if (!coverId.isNullOrBlank()) {
                val item = doc.getElementById(coverId)
                    ?: doc.select("manifest > item[id='$coverId']").firstOrNull()
                    ?: doc.select("manifest > item[id=$coverId]").firstOrNull()
                coverHref = item?.attr("href")
            }
        }

        if (coverHref.isNullOrBlank()) {
            val fallbackItem = doc.select("manifest > item[href~=(?i)cover\\.(jpe?g|png|webp)]").firstOrNull()
                ?: doc.select("manifest > item[id~=(?i)cover]").firstOrNull()
            coverHref = fallbackItem?.attr("href")
        }

        var coverUri: String? = null
        if (!coverHref.isNullOrBlank()) {
            val opfDir = if (opfEntry.name.contains("/")) opfEntry.name.substringBeforeLast("/") else ""
            val fullCoverPath = resolveZipPath(opfDir, coverHref)
            val coverFileName = coverHref.substringAfterLast("/")
            val coverEntry = entries.firstOrNull { it.name.equals(fullCoverPath, ignoreCase = true) }
                ?: entries.firstOrNull {
                    it.name.endsWith("/$coverFileName", ignoreCase = true) ||
                        it.name.equals(coverFileName, ignoreCase = true)
                }

            if (coverEntry != null) {
                val coverBytes = ZipRemoteHelper.extractEntryBytes(url, auth, coverEntry)
                if (coverBytes != null && coverBytes.isNotEmpty()) {
                    val coverKey = "$url#cover"
                    val savedPath = RemoteCoverCache.saveCoverBytes(coverKey, coverBytes)
                    if (savedPath != null) {
                        coverUri = "file://$savedPath"
                    }
                }
            }
        }

        RemoteExtractedMetadata(
            title = title,
            author = author,
            intro = intro,
            coverUrl = coverUri,
            format = "epub"
        )
    } catch (e: Throwable) {
        null
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        val cleanHref = href.replace("\\", "/")
        if (baseDir.isBlank() || cleanHref.startsWith("/")) return cleanHref.trimStart('/')
        val parts = "$baseDir/$cleanHref".split("/")
        val resolved = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> continue
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved.add(part)
            }
        }
        return resolved.joinToString("/")
    }

    private fun org.jsoup.nodes.Element.getTagText(vararg tags: String): String? {
        for (tag in tags) {
            val text = getElementsByTag(tag).firstOrNull()?.text()?.trim()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }
}
