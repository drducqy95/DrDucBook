package io.legado.app.service.export

import io.legado.app.domain.model.AuthoringStyle
import io.legado.app.domain.model.EbookBlock
import io.legado.app.domain.model.EbookCodeBlock
import io.legado.app.domain.model.EbookDividerBlock
import io.legado.app.domain.model.EbookDividerType
import io.legado.app.domain.model.EbookDocument
import io.legado.app.domain.model.EbookDocumentChapter
import io.legado.app.domain.model.EbookHeadingBlock
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.domain.model.EbookLayoutMode
import io.legado.app.domain.model.EbookListBlock
import io.legado.app.domain.model.EbookPageBreakBlock
import io.legado.app.domain.model.EbookParagraphBlock
import io.legado.app.domain.model.EbookQuoteBlock
import io.legado.app.domain.model.blockPlainText

data class RenderedEbookChapter(
    val id: String,
    val title: String,
    val html: String,
    val plainText: String,
    val blocks: List<EbookBlock>,
)

data class RenderedEbook(
    val layoutMode: EbookLayoutMode,
    val viewportWidth: Float?,
    val viewportHeight: Float?,
    val css: String,
    val chapters: List<RenderedEbookChapter>,
)

object EbookLayoutRenderer {

    fun render(document: EbookDocument, style: AuthoringStyle): RenderedEbook = RenderedEbook(
        layoutMode = document.layoutMode,
        viewportWidth = document.pageSize?.width,
        viewportHeight = document.pageSize?.height,
        css = buildCss(document, style),
        chapters = document.chapters.map { renderChapter(document, it, style) },
    )

    fun renderChapter(
        document: EbookDocument,
        chapter: EbookDocumentChapter,
        style: AuthoringStyle,
    ): RenderedEbookChapter {
        val ordered = chapter.blocks.sortedBy(EbookBlock::readingOrder)
        val firstDropCapId = if (style.dropCap) {
            ordered.filterIsInstance<EbookParagraphBlock>()
                .firstOrNull { paragraph -> paragraph.text.any(Char::isLetterOrDigit) }
                ?.id
        } else null
        val html = ordered.joinToString("\n") { block ->
            renderBlock(block, document, block.id == firstDropCapId)
        }
        return RenderedEbookChapter(
            id = chapter.id,
            title = chapter.title,
            html = html,
            plainText = ordered.joinToString("\n\n", transform = ::blockPlainText),
            blocks = ordered,
        )
    }

    private fun renderBlock(block: EbookBlock, document: EbookDocument, dropCap: Boolean): String {
        if (block.geometry?.isHidden == true) return ""
        val content = when (block) {
            is EbookParagraphBlock -> "<p${if (dropCap) " class=\"legado-dropcap\"" else ""}>${inline(block.text, block.style)}</p>"
            is EbookHeadingBlock -> "<h${block.level.coerceIn(1, 6)}>${xml(block.text)}</h${block.level.coerceIn(1, 6)}>"
            is EbookQuoteBlock -> "<blockquote>${xml(block.text)}${block.attribution.takeIf(String::isNotBlank)?.let { "<cite>${xml(it)}</cite>" }.orEmpty()}</blockquote>"
            is EbookImageBlock -> buildString {
                append("<figure><img src=\"").append(xml(block.uri)).append("\" alt=\"")
                    .append(xml(block.alt)).append("\"/>")
                if (block.caption.isNotBlank()) append("<figcaption>").append(xml(block.caption)).append("</figcaption>")
                append("</figure>")
            }
            is EbookDividerBlock -> when (block.type) {
                EbookDividerType.LINE -> "<hr/>"
                EbookDividerType.ORNAMENT -> "<div class=\"ornament\">* * *</div>"
                EbookDividerType.SPACE -> "<div class=\"spacer\"></div>"
            }
            is EbookPageBreakBlock -> "<div class=\"page-break\"></div>"
            is EbookCodeBlock -> "<pre><code class=\"language-${xml(block.language)}\">${xml(block.text)}</code></pre>"
            is EbookListBlock -> {
                val tag = if (block.ordered) "ol" else "ul"
                "<$tag>${block.items.joinToString("") { "<li>${xml(it)}</li>" }}</$tag>"
            }
        }
        if (document.layoutMode != EbookLayoutMode.FIXED_PAGE) return content
        val geometry = block.geometry ?: return content
        val pageHeight = document.pageSize?.height ?: 1123f
        val absoluteTop = geometry.y + geometry.page.coerceAtLeast(0) * pageHeight
        return "<div class=\"fixed-block\" data-page=\"${geometry.page}\" style=\"" +
            "left:${geometry.x}px;top:${absoluteTop}px;width:${geometry.width}px;height:${geometry.height}px;" +
            "transform:rotate(${geometry.rotation}deg);z-index:${geometry.zIndex}\">$content</div>"
    }

    private fun inline(text: String, style: io.legado.app.domain.model.EbookInlineStyle): String {
        var value = xml(normalizeLineBreaks(text)).replace("\n", "<br/>")
        if (style.bold) value = "<strong>$value</strong>"
        if (style.italic) value = "<em>$value</em>"
        if (style.underline) value = "<u>$value</u>"
        if (style.strikethrough) value = "<s>$value</s>"
        return value
    }

    private fun buildCss(document: EbookDocument, style: AuthoringStyle): String = buildString {
        append("body{font-family:").append(css(style.fontFamily)).append(";font-size:")
            .append(style.fontSizeSp).append("px;line-height:")
            .append(style.lineHeightPercent / 100f).append("}")
        append("p{text-indent:").append(style.paragraphIndentEm).append("em}")
        append("img{max-width:100%;height:auto}.page-break{break-before:page}")
        append(".legado-dropcap::first-letter{float:left;font-size:3.2em;line-height:.82;padding:.08em .12em 0 0}")
        if (document.layoutMode == EbookLayoutMode.FIXED_PAGE) {
            val page = document.pageSize
            val pageCount = (document.chapters.flatMap(EbookDocumentChapter::blocks)
                .maxOfOrNull { it.geometry?.page ?: 0 } ?: 0) + 1
            append("body{position:relative;margin:0;width:").append(page?.width ?: 794f)
                .append("px;height:").append((page?.height ?: 1123f) * pageCount).append("px}")
            append(".fixed-block{position:absolute;overflow:hidden;transform-origin:center}")
        }
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun css(value: String): String = value.replace(Regex("[^A-Za-z0-9 ,'-]"), "")

    private fun normalizeLineBreaks(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
}
