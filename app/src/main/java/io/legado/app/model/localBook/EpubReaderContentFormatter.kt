package io.legado.app.model.localBook

import io.legado.app.utils.HtmlFormatter
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/** Converts EPUB XHTML into bounded reader blocks while retaining meaningful inline layout. */
internal object EpubReaderContentFormatter {

    private const val maxAtomicBlockLength = 32_000
    private val containerTags = setOf("body", "html", "main", "article", "section", "div")
    private val blockTags = setOf(
        "address", "article", "aside", "blockquote", "div", "dl", "figure", "figcaption",
        "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li",
        "main", "nav", "ol", "p", "pre", "section", "table", "ul"
    )
    private val richTags = setOf(
        "big", "small", "b", "strong", "i", "em", "u", "font", "a", "ruby", "rt", "sub", "sup"
    )
    private val explicitDropCapClasses = setOf(
        "dropcap", "drop-cap", "drop_cap", "initial", "initial-letter", "first-letter",
        "lettrine", "legado-dropcap",
    )

    fun format(elements: Elements): String {
        applyTextAlignments(elements)
        applyDropCaps(elements)
        elements.select("script, style, link[rel=stylesheet]").remove()
        val blocks = buildList {
            elements.forEach { element -> emitContainer(element, this) }
        }
        return blocks.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n")
    }

    private fun applyTextAlignments(elements: Elements) {
        val css = elements.select("style").joinToString("\n") { it.data() + it.html() }
        cssRules(css).forEach { (selectorText, declarations) ->
            val alignment = textAlignment(declarations) ?: return@forEach
            selectorText.split(',').forEach { rawSelector ->
                val selector = rawSelector.trim()
                if (selector.isEmpty() || ':' in selector) return@forEach
                runCatching { elements.select(selector) }.getOrNull()?.forEach { element ->
                    element.attr("data-legado-align", alignment)
                }
            }
        }
        elements.select("*").forEach { element ->
            val inline = textAlignment(element.attr("style"))
            val inherited = element.parents().firstOrNull {
                it.hasAttr("data-legado-align")
            }?.attr("data-legado-align")
            val alignment = inline
                ?: element.attr("data-legado-align").takeIf(String::isNotBlank)
                ?: inherited
            if (alignment != null && isBlock(element)) {
                element.attr("data-legado-align", alignment)
            }
        }
    }

    private fun textAlignment(declarations: String): String? {
        return Regex(
            "(?i)(?:^|;)\\s*text-align\\s*:\\s*(left|right|center|start|end|justify)\\b"
        ).find(declarations)?.groupValues?.get(1)?.lowercase()
    }

    private fun applyDropCaps(elements: Elements) {
        val selectors = linkedSetOf<String>()
        elements.select("style").forEach { style ->
            cssRules(style.data() + style.html()).forEach { (selectorText, declarations) ->
                val hasLargeType = declarations.contains("font-size", ignoreCase = true)
                val hasFloat = declarations.replace(" ", "")
                    .contains("float:left", ignoreCase = true)
                selectorText.split(',').forEach { rawSelector ->
                    val selector = rawSelector.trim()
                    when {
                        selector.contains("first-letter", ignoreCase = true) && hasLargeType -> {
                            selectors += selector
                                .replace("::first-letter", "", ignoreCase = true)
                                .replace(":first-letter", "", ignoreCase = true)
                                .trim()
                                .ifEmpty { "p" }
                        }

                        hasLargeType && hasFloat -> selectors += selector
                    }
                }
            }
        }

        elements.select("*").forEach { element ->
            val classes = element.classNames().map(String::lowercase)
            val style = element.attr("style").replace(" ", "")
            if (classes.any(explicitDropCapClasses::contains) ||
                (style.contains("float:left", ignoreCase = true) &&
                    style.contains("font-size", ignoreCase = true))
            ) {
                wrapElementAsDropCap(element)
            }
        }
        selectors.forEach { selector ->
            runCatching { elements.select(selector) }
                .getOrNull()
                ?.forEach(::wrapFirstVisibleCharacter)
        }
    }

    private fun cssRules(css: String): Sequence<Pair<String, String>> = sequence {
        var cursor = 0
        while (cursor < css.length) {
            val open = css.indexOf('{', cursor)
            if (open < 0) break
            val close = css.indexOf('}', open + 1)
            if (close < 0) break
            val selector = css.substring(cursor, open).trim()
            val declarations = css.substring(open + 1, close)
            if (selector.isNotEmpty()) yield(selector to declarations)
            cursor = close + 1
        }
    }

    private fun wrapElementAsDropCap(element: Element) {
        if (element.normalName() in setOf("h1", "h2", "h3", "h4", "h5", "h6") ||
            element.select("img").isNotEmpty() && element.text().isBlank()
        ) return
        if (element.tagName().equals("p", ignoreCase = true)) {
            wrapFirstVisibleCharacter(element)
            return
        }
        if (element.parents().any { it.hasAttr("data-legado-dropcap") }) return
        element.tagName("span")
        element.attr("data-legado-dropcap", "true")
    }

    private fun wrapFirstVisibleCharacter(element: Element) {
        if (element.normalName() in setOf("h1", "h2", "h3", "h4", "h5", "h6") ||
            element.select("img").isNotEmpty() && element.text().isBlank()
        ) return
        if (element.hasAttr("data-legado-dropcap") ||
            element.select("[data-legado-dropcap]").isNotEmpty()
        ) return
        val textNode = element.textNodesDeep().firstOrNull { node ->
            node.wholeText.any { !it.isWhitespace() }
        } ?: return
        val text = textNode.wholeText
        val start = firstDropCapIndex(text)
        if (start < 0) return
        val charLength = firstGraphemeEnd(text, start) - start
        val before = text.substring(0, start)
        val initial = text.substring(start, start + charLength)
        val after = text.substring(start + charLength)
        val outer = Element("span")
            .attr("data-legado-dropcap", "true")
            .text(initial)
        textNode.text(before)
        if (after.isNotEmpty()) textNode.after(TextNode(after))
        textNode.after(outer)
    }

    private fun firstDropCapIndex(text: String): Int {
        var index = text.indexOfFirst { !it.isWhitespace() }
        while (index in text.indices) {
            val codePoint = text.codePointAt(index)
            if (Character.isLetterOrDigit(codePoint)) return index
            index += Character.charCount(codePoint)
        }
        return -1
    }

    private fun firstGraphemeEnd(text: String, start: Int): Int {
        var end = start + Character.charCount(text.codePointAt(start))
        while (end < text.length) {
            val type = Character.getType(text.codePointAt(end))
            if (type != Character.NON_SPACING_MARK.toInt() &&
                type != Character.COMBINING_SPACING_MARK.toInt() &&
                type != Character.ENCLOSING_MARK.toInt()
            ) break
            end += Character.charCount(text.codePointAt(end))
        }
        return end
    }

    private fun Element.textNodesDeep(): Sequence<TextNode> = sequence {
        childNodes().forEach { node ->
            when (node) {
                is TextNode -> yield(node)
                is Element -> yieldAll(node.textNodesDeep())
            }
        }
    }

    private fun emitContainer(container: Element, output: MutableList<String>) {
        val inlineNodes = arrayListOf<Node>()
        fun flushInline() {
            if (inlineNodes.isEmpty()) return
            val wrapper = Element("div")
            inlineNodes.forEach { wrapper.appendChild(it.clone()) }
            emitAtomic(wrapper, output)
            inlineNodes.clear()
        }

        container.childNodes().forEach { node ->
            if (node is Element && isBlock(node)) {
                flushInline()
                if (node.normalName() in containerTags &&
                    (node.childNodes().any { it is Element && isBlock(it) } ||
                        node.outerHtml().length > maxAtomicBlockLength)
                ) {
                    emitContainer(node, output)
                } else {
                    emitAtomic(node, output)
                }
            } else {
                inlineNodes += node
            }
        }
        flushInline()
    }

    private fun emitAtomic(element: Element, output: MutableList<String>) {
        if (!element.hasText() && element.select("img").isEmpty()) return
        val html = element.outerHtml().replace(Regex("[\\r\\n]+"), " ")
        val hasRichLayout = element.getAllElements().any { child ->
            child.normalName() in richTags ||
                child.hasAttr("data-legado-dropcap") ||
                child.hasAttr("data-legado-align")
        }
        val rendered = if (hasRichLayout) {
            val alignment = element.attr("data-legado-align")
            val alignedHtml = if (alignment.isBlank()) html else {
                "<legado-align-$alignment>$html</legado-align-$alignment>"
            }
            "<usehtml>$alignedHtml</usehtml>"
        } else {
            HtmlFormatter.formatKeepImg(html)
        }
        if (rendered.isNotBlank()) output += rendered
    }

    private fun isBlock(element: Element): Boolean = element.normalName() in blockTags
}
