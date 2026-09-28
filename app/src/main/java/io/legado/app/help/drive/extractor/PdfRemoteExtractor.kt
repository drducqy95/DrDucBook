package io.legado.app.help.drive.extractor

import io.legado.app.lib.webdav.Authorization

object PdfRemoteExtractor {

    private val titleRegex = Regex("""/Title\s*\(([^)]+)\)""")
    private val authorRegex = Regex("""/Author\s*\(([^)]+)\)""")
    private val subjectRegex = Regex("""/Subject\s*\(([^)]+)\)""")

    suspend fun extract(
        url: String,
        auth: Authorization?,
        fileSize: Long,
        existingThumbnailUrl: String? = null
    ): RemoteExtractedMetadata? {
        if (fileSize < 64) return null
        val tailLen = minOf(fileSize, 16384L)
        val tailBytes = RemoteRangeExtractor.readTail(url, auth, fileSize, tailLen) ?: return null
        val tailStr = String(tailBytes, Charsets.ISO_8859_1)

        val title = titleRegex.find(tailStr)?.groupValues?.getOrNull(1)?.let { decodePdfString(it) }
        val author = authorRegex.find(tailStr)?.groupValues?.getOrNull(1)?.let { decodePdfString(it) }
        val intro = subjectRegex.find(tailStr)?.groupValues?.getOrNull(1)?.let { decodePdfString(it) }

        return RemoteExtractedMetadata(
            title = title,
            author = author,
            intro = intro,
            coverUrl = existingThumbnailUrl,
            format = "pdf"
        )
    }

    private fun decodePdfString(raw: String): String {
        return raw.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .trim()
    }
}
