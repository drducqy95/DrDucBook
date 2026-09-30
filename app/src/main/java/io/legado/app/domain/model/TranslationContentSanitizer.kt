package io.legado.app.domain.model

import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.Locale

/**
 * Converts reader-facing HTML (including Legado's internal EPUB markers) to provider-facing
 * prose. Reader content intentionally keeps markup for layout and rich text, but translation
 * providers must never receive or persist those tags as part of the translated text.
 */
object TranslationContentSanitizer {

    private val tagPattern = Regex(
        "<\\s*/?\\s*[A-Za-z][A-Za-z0-9:_-]*(?:\\s+[^<>]*?)?/?>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val commentPattern = Regex(
        "<!--.*?-->",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val blockTags = setOf(
        "address", "article", "aside", "blockquote", "br", "dd", "div", "dl",
        "dt", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4",
        "h5", "h6", "header", "hr", "li", "main", "nav", "ol", "p", "pre",
        "section", "table", "td", "th", "tr", "ul", "usehtml",
    )
    private val ignoredTags = setOf("head", "script", "style", "noscript", "svg")

    /** Returns stable plain text while preserving paragraph boundaries. */
    fun sanitize(value: String): String {
        if (value.isBlank()) return ""
        val unescaped = StringEscapeUtils.unescapeHtml4(value)
        if (!looksLikeMarkup(unescaped)) return normalize(unescaped)

        val document = Jsoup.parseBodyFragment(unescaped)
        document.select(ignoredTags.joinToString(", ")).remove()
        return normalize(buildString { appendNodes(document.body(), this) })
    }

    fun containsMarkup(value: String): Boolean = looksLikeMarkup(
        StringEscapeUtils.unescapeHtml4(value),
    )

    private fun looksLikeMarkup(value: String): Boolean =
        tagPattern.containsMatchIn(value) || commentPattern.containsMatchIn(value)

    private fun appendNodes(node: Node, output: StringBuilder) {
        when (node) {
            is TextNode -> output.append(node.getWholeText())
            is Element -> {
                val tag = node.normalName().lowercase(Locale.ROOT)
                if (tag in ignoredTags) return
                if (tag == "img") {
                    node.attr("alt").takeIf(String::isNotBlank)?.let(output::append)
                    return
                }
                val isBlock = tag in blockTags || tag.startsWith("legado-align-")
                if (isBlock) output.append('\n')
                node.childNodes().forEach { child -> appendNodes(child, output) }
                if (isBlock) output.append('\n')
            }
        }
    }

    private fun normalize(value: String): String {
        return value
            .replace('\u00A0', ' ')
            .replace('\u2009', ' ')
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
