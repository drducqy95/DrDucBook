package io.legado.app.web

import io.legado.app.domain.model.OpdsCatalog
import io.legado.app.domain.model.OpdsEntry
import io.legado.app.domain.model.OpdsLink
import io.legado.app.domain.model.OpdsMimeTypes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object OpdsAtomSerializer {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun serialize(catalog: OpdsCatalog): String {
        val updated = catalog.updated.ifBlank {
            isoDateFormat.format(Date())
        }

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<feed xmlns=\"http://www.w3.org/2005/Atom\"\n")
            append("      xmlns:dc=\"http://purl.org/dc/terms/\"\n")
            append("      xmlns:opds=\"http://opds-spec.org/2010/catalog\">\n")

            append("  <id>").append(escapeXml(catalog.id)).append("</id>\n")
            append("  <title>").append(escapeXml(catalog.title)).append("</title>\n")
            append("  <updated>").append(escapeXml(updated)).append("</updated>\n")

            for (link in catalog.links) {
                appendLink(link, indent = "  ")
            }

            for (entry in catalog.entries) {
                appendEntry(entry, indent = "  ")
            }

            append("</feed>\n")
        }
    }

    private fun StringBuilder.appendEntry(entry: OpdsEntry, indent: String) {
        val updated = entry.updated.ifBlank {
            isoDateFormat.format(Date())
        }

        append(indent).append("<entry>\n")
        append(indent).append("  <id>").append(escapeXml(entry.id)).append("</id>\n")
        append(indent).append("  <title>").append(escapeXml(entry.title)).append("</title>\n")
        append(indent).append("  <updated>").append(escapeXml(updated)).append("</updated>\n")

        if (!entry.author.isNullOrBlank()) {
            append(indent).append("  <author><name>").append(escapeXml(entry.author)).append("</name></author>\n")
        }

        if (!entry.summary.isNullOrBlank()) {
            append(indent).append("  <summary type=\"text\">").append(escapeXml(entry.summary)).append("</summary>\n")
        }

        if (!entry.content.isNullOrBlank()) {
            append(indent).append("  <content type=\"text\">").append(escapeXml(entry.content)).append("</content>\n")
        }

        for (category in entry.categories) {
            append(indent).append("  <category term=\"").append(escapeXml(category)).append("\" />\n")
        }

        for (link in entry.links) {
            appendLink(link, indent = "$indent  ")
        }

        if (!entry.coverUrl.isNullOrBlank()) {
            append(indent).append("  <link rel=\"").append(OpdsMimeTypes.REL_IMAGE)
                .append("\" href=\"").append(escapeXml(entry.coverUrl))
                .append("\" type=\"").append(OpdsMimeTypes.MIME_JPEG).append("\" />\n")
        }

        if (!entry.thumbnailUrl.isNullOrBlank()) {
            append(indent).append("  <link rel=\"").append(OpdsMimeTypes.REL_THUMBNAIL)
                .append("\" href=\"").append(escapeXml(entry.thumbnailUrl))
                .append("\" type=\"").append(OpdsMimeTypes.MIME_JPEG).append("\" />\n")
        }

        append(indent).append("</entry>\n")
    }

    private fun StringBuilder.appendLink(link: OpdsLink, indent: String) {
        append(indent).append("<link rel=\"").append(escapeXml(link.rel)).append("\"")
        append(" href=\"").append(escapeXml(link.href)).append("\"")

        if (link.type.isNotBlank()) {
            append(" type=\"").append(escapeXml(link.type)).append("\"")
        }
        if (!link.title.isNullOrBlank()) {
            append(" title=\"").append(escapeXml(link.title)).append("\"")
        }
        if (link.length != null && link.length > 0) {
            append(" length=\"").append(link.length).append("\"")
        }
        append(" />\n")
    }

    internal fun escapeXml(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length)
        for (i in 0 until text.length) {
            when (val c = text[i]) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
