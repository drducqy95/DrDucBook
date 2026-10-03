package io.legado.app.web

import io.legado.app.constant.AppPattern.archiveFileRegex
import io.legado.app.constant.AppPattern.bookFileRegex
import io.legado.app.domain.model.OpdsCatalog
import io.legado.app.domain.model.OpdsEntry
import io.legado.app.domain.model.OpdsLink
import io.legado.app.domain.model.OpdsMimeTypes
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DriveFileInfo(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String = "",
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val intro: String? = null,
)

object OpdsDriveMapper {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun mapDirectoryToCatalog(
        sourceId: String,
        sourceName: String,
        currentPath: String,
        files: List<DriveFileInfo>,
        baseServerUrl: String,
    ): OpdsCatalog {
        val normalizedPath = if (currentPath.startsWith("/")) currentPath else "/$currentPath"
        val folderTitle = if (normalizedPath == "/") sourceName else normalizedPath.trimEnd('/').substringAfterLast("/")
        val catalogId = "urn:drducbook:opds:$sourceId:${encodePathSegment(normalizedPath)}"

        val selfHref = buildCatalogUrl(baseServerUrl, normalizedPath)
        val startHref = buildCatalogUrl(baseServerUrl, "/")

        val rootLinks = mutableListOf(
            OpdsLink(href = selfHref, rel = OpdsMimeTypes.REL_SELF, type = OpdsMimeTypes.NAVIGATION),
            OpdsLink(href = startHref, rel = OpdsMimeTypes.REL_START, type = OpdsMimeTypes.NAVIGATION),
        )

        if (normalizedPath != "/") {
            val parentPath = normalizedPath.trimEnd('/').substringBeforeLast("/", "").ifEmpty { "/" }
            rootLinks.add(
                OpdsLink(
                    href = buildCatalogUrl(baseServerUrl, parentPath),
                    rel = OpdsMimeTypes.REL_UP,
                    type = OpdsMimeTypes.NAVIGATION,
                    title = "Parent Directory"
                )
            )
        }

        val entries = mutableListOf<OpdsEntry>()

        // 1. Folders first
        files.filter { it.isDir }.sortedBy { it.name.lowercase() }.forEach { folder ->
            val subPath = combinePath(normalizedPath, folder.name)
            val subUrl = buildCatalogUrl(baseServerUrl, subPath)
            entries.add(
                OpdsEntry(
                    id = "urn:drducbook:opds:folder:$sourceId:${encodePathSegment(subPath)}",
                    title = folder.name,
                    updated = formatTimestamp(folder.lastModified),
                    links = listOf(
                        OpdsLink(
                            href = subUrl,
                            rel = OpdsMimeTypes.REL_SUBSECTION,
                            type = OpdsMimeTypes.NAVIGATION,
                            title = folder.name,
                        )
                    )
                )
            )
        }

        // 2. Book files
        files.filter { !it.isDir && isBookOrArchive(it.name) }.sortedBy { it.name.lowercase() }.forEach { file ->
            val filePath = combinePath(normalizedPath, file.name)
            val downloadUrl = buildDownloadUrl(baseServerUrl, filePath)
            val mime = resolveMimeType(file.name, file.mimeType)
            val title = file.name.substringBeforeLast(".")

            val entryLinks = mutableListOf(
                OpdsLink(
                    href = downloadUrl,
                    rel = OpdsMimeTypes.REL_ACQUISITION,
                    type = mime,
                    title = file.name,
                    length = file.size.takeIf { it > 0 }
                )
            )

            val coverUrl = if (!file.thumbnailUrl.isNullOrBlank()) {
                file.thumbnailUrl
            } else {
                buildCoverUrl(baseServerUrl, filePath)
            }

            entries.add(
                OpdsEntry(
                    id = "urn:drducbook:opds:book:$sourceId:${encodePathSegment(filePath)}",
                    title = title,
                    author = file.author,
                    summary = file.intro,
                    updated = formatTimestamp(file.lastModified),
                    coverUrl = coverUrl,
                    thumbnailUrl = coverUrl,
                    links = entryLinks,
                )
            )
        }

        return OpdsCatalog(
            id = catalogId,
            title = folderTitle,
            updated = isoDateFormat.format(Date()),
            selfUrl = selfHref,
            links = rootLinks,
            entries = entries,
        )
    }

    fun isBookOrArchive(filename: String): Boolean {
        return bookFileRegex.matches(filename) || archiveFileRegex.matches(filename)
    }

    fun resolveMimeType(filename: String, fallback: String = ""): String {
        val ext = filename.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "epub" -> OpdsMimeTypes.MIME_EPUB
            "pdf" -> OpdsMimeTypes.MIME_PDF
            "txt" -> OpdsMimeTypes.MIME_TXT
            "mobi", "azw", "azw3" -> OpdsMimeTypes.MIME_MOBI
            "cbz" -> OpdsMimeTypes.MIME_CBZ
            "cbr" -> OpdsMimeTypes.MIME_CBR
            else -> fallback.ifBlank { "application/octet-stream" }
        }
    }

    private fun combinePath(base: String, name: String): String {
        val trimmedBase = base.trimEnd('/')
        return if (trimmedBase.isEmpty()) "/$name" else "$trimmedBase/$name"
    }

    private fun buildCatalogUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val encodedPath = encodePathForUrl(path)
        return "$cleanBase/opds/catalog$encodedPath"
    }

    private fun buildDownloadUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val encodedPath = encodePathForUrl(path)
        return "$cleanBase/opds/download$encodedPath"
    }

    private fun buildCoverUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val encodedPath = encodePathForUrl(path)
        return "$cleanBase/opds/cover$encodedPath"
    }

    private fun encodePathForUrl(path: String): String {
        return path.split("/").joinToString("/") { segment ->
            if (segment.isBlank()) "" else encodePathSegment(segment)
        }
    }

    private fun encodePathSegment(segment: String): String {
        return URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }

    private fun formatTimestamp(timestamp: Long): String {
        return if (timestamp > 0) {
            isoDateFormat.format(Date(timestamp))
        } else {
            isoDateFormat.format(Date())
        }
    }
}
