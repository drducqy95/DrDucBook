package io.legado.app.help.drive

import io.legado.app.domain.model.OpdsCatalog
import io.legado.app.domain.model.OpdsEntry
import io.legado.app.domain.model.OpdsLink
import io.legado.app.domain.model.OpdsMimeTypes
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI

object OpdsParser {

    fun parse(xml: String, baseUrl: String): OpdsCatalog {
        val doc = runCatching {
            Jsoup.parse(xml, baseUrl, Parser.xmlParser())
        }.getOrElse {
            Jsoup.parse(xml, baseUrl)
        }

        val feed = doc.selectFirst("feed") ?: doc

        val catalogId = feed.children().firstOrNull { it.tagName().equals("id", ignoreCase = true) }?.text().orEmpty()
        val catalogTitle = feed.children().firstOrNull { it.tagName().equals("title", ignoreCase = true) }?.text().orEmpty()
        val catalogUpdated = feed.children().firstOrNull { it.tagName().equals("updated", ignoreCase = true) }?.text().orEmpty()

        val rootLinks = feed.children()
            .filter { it.tagName().equals("link", ignoreCase = true) }
            .map { parseLink(it, baseUrl) }

        val selfUrl = rootLinks.firstOrNull { it.rel == OpdsMimeTypes.REL_SELF }?.href
            ?: baseUrl

        val entries = feed.select("entry").map { entryEl ->
            val id = entryEl.children().firstOrNull { it.tagName().equals("id", ignoreCase = true) }?.text().orEmpty()
            val title = entryEl.children().firstOrNull { it.tagName().equals("title", ignoreCase = true) }?.text().orEmpty()
            val updated = entryEl.children().firstOrNull { it.tagName().equals("updated", ignoreCase = true) }?.text().orEmpty()
            val author = entryEl.selectFirst("author > name")?.text()
                ?: entryEl.selectFirst("author")?.ownText()
                ?: entryEl.selectFirst("creator")?.text()
            val summary = entryEl.children().firstOrNull { it.tagName().equals("summary", ignoreCase = true) }?.text()
            val content = entryEl.children().firstOrNull { it.tagName().equals("content", ignoreCase = true) }?.text()

            val entryLinks = entryEl.select("link").map { parseLink(it, baseUrl) }

            val categories = entryEl.select("category").mapNotNull { cat ->
                cat.attr("term").takeIf(String::isNotBlank)
                    ?: cat.attr("label").takeIf(String::isNotBlank)
            }

            val coverUrl = entryLinks.firstOrNull { it.rel == OpdsMimeTypes.REL_IMAGE }?.href
                ?: entryLinks.firstOrNull { it.isCoverImage }?.href
            val thumbnailUrl = entryLinks.firstOrNull { it.rel == OpdsMimeTypes.REL_THUMBNAIL }?.href
                ?: coverUrl

            OpdsEntry(
                id = id.ifBlank { title },
                title = title,
                updated = updated,
                author = author,
                summary = summary,
                content = content,
                coverUrl = coverUrl,
                thumbnailUrl = thumbnailUrl,
                links = entryLinks,
                categories = categories,
            )
        }

        return OpdsCatalog(
            id = catalogId.ifBlank { baseUrl },
            title = catalogTitle.ifBlank { "OPDS Catalog" },
            updated = catalogUpdated,
            selfUrl = selfUrl,
            links = rootLinks,
            entries = entries,
        )
    }

    fun parseOpenSearchTemplate(xml: String, baseUrl: String): String? {
        val doc = runCatching {
            Jsoup.parse(xml, baseUrl, Parser.xmlParser())
        }.getOrElse {
            Jsoup.parse(xml, baseUrl)
        }
        val urlEl = doc.selectFirst("Url[type*=atom+xml]")
            ?: doc.selectFirst("Url[type*=opds]")
            ?: doc.selectFirst("Url")
            ?: return null
        val rawTemplate = urlEl.attr("template").takeIf(String::isNotBlank) ?: return null
        return resolveUrl(baseUrl, rawTemplate)
    }

    fun resolveUrl(baseUrl: String, href: String): String {
        val trimmedHref = href.trim()
        if (trimmedHref.startsWith("http://", ignoreCase = true) ||
            trimmedHref.startsWith("https://", ignoreCase = true)
        ) {
            return trimmedHref
        }
        return try {
            val baseUri = URI.create(baseUrl)
            baseUri.resolve(trimmedHref).toString()
        } catch (_: Exception) {
            if (baseUrl.endsWith("/") || trimmedHref.startsWith("/")) {
                "${baseUrl.trimEnd('/')}/${trimmedHref.trimStart('/')}"
            } else {
                "$baseUrl/$trimmedHref"
            }
        }
    }

    private fun parseLink(linkEl: org.jsoup.nodes.Element, baseUrl: String): OpdsLink {
        val rawHref = linkEl.attr("href")
        val resolvedHref = if (rawHref.isNotBlank()) {
            linkEl.absUrl("href").takeIf(String::isNotBlank) ?: resolveUrl(baseUrl, rawHref)
        } else ""

        val rel = linkEl.attr("rel").trim()
        val type = linkEl.attr("type").trim()
        val title = linkEl.attr("title").trim().takeIf(String::isNotBlank)
        val length = linkEl.attr("length").toLongOrNull()

        return OpdsLink(
            href = resolvedHref,
            rel = rel,
            type = type,
            title = title,
            length = length,
        )
    }
}
