package io.legado.app.ui.book.read.page.provider

import android.text.Editable
import android.text.Html
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import org.xml.sax.XMLReader
import kotlin.math.ceil
import kotlin.math.max

internal object DropCapHtmlSupport {

    const val lineCount = 3
    const val visualLineCount = 2.35f
    private const val marker = '\uE000'
    private val markerRegex = Regex(
        """<span\b(?=[^>]*\bdata-legado-dropcap\s*=\s*["']true["'])[^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun parse(
        html: String,
        basePaint: TextPaint,
        targetHeight: Float,
        gapPx: Float,
    ): Spanned {
        val initials = arrayListOf<String>()
        val markedHtml = markerRegex.replace(html) { match ->
            val initial = firstGrapheme(
                match.groupValues[1]
                    .parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT)
                    .toString()
                    .trim(),
            )
            if (initial.isEmpty()) {
                match.value
            } else {
                initials += initial
                marker.toString()
            }
        }
        if (initials.isEmpty()) {
            return parseReaderHtml(markedHtml)
        }

        val builder = SpannableStringBuilder(
            parseReaderHtml(markedHtml),
        )
        var searchFrom = 0
        initials.forEach { initial ->
            val markerIndex = builder.indexOf(marker.toString(), searchFrom)
            if (markerIndex < 0) return@forEach
            builder.delete(markerIndex, markerIndex + 1)

            val paragraphStart = builder.lastIndexOf('\n', markerIndex - 1) + 1
            val nextLineBreak = builder.indexOf('\n', markerIndex)
            val paragraphEnd = if (nextLineBreak >= 0) nextLineBreak + 1 else builder.length
            if (paragraphEnd <= paragraphStart) return@forEach

            val dropCapPaint = TextPaint(basePaint).apply {
                textSize = basePaint.textSize * 3f
            }
            val bounds = Rect()
            dropCapPaint.getTextBounds(initial, 0, initial.length, bounds)
            if (bounds.height() > 0) {
                dropCapPaint.textSize *= targetHeight / bounds.height()
            }
            val dropCapTextSize = dropCapPaint.textSize
            val dropCapWidth = max(
                bounds.width().toFloat(),
                dropCapPaint.measureText(initial) - bounds.left,
            )
            builder.setSpan(
                DropCapLayoutSpan(
                    initial = initial,
                    textSize = dropCapTextSize,
                    width = dropCapWidth,
                    gap = gapPx,
                    typeface = basePaint.typeface,
                ),
                paragraphStart,
                paragraphEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            searchFrom = markerIndex
        }
        return builder
    }

    private fun parseReaderHtml(html: String): Spanned = HtmlCompat.fromHtml(
        html,
        HtmlCompat.FROM_HTML_MODE_COMPACT,
        null,
        EpubAlignmentTagHandler,
    )

    fun lineIndents(spanned: Spanned, layout: StaticLayout): IntArray? {
        val spans = spanned.getSpans(0, spanned.length, DropCapLayoutSpan::class.java)
        if (spans.isEmpty()) return null
        val indents = IntArray(
            (layout.lineCount + spans.size * lineCount + 1).coerceAtLeast(1),
        )
        spans.forEach { span ->
            val startLine = layout.getLineForOffset(spanned.getSpanStart(span))
            val indent = span.indent
            repeat(lineCount) { offset ->
                val line = startLine + offset
                if (line in indents.indices) {
                    indents[line] = maxOf(indents[line], indent)
                }
            }
        }
        return indents
    }

    internal fun firstGrapheme(text: String): String {
        if (text.isEmpty()) return ""
        var start = 0
        while (start < text.length) {
            val codePoint = text.codePointAt(start)
            if (Character.isLetterOrDigit(codePoint)) break
            start += Character.charCount(codePoint)
        }
        if (start >= text.length) return ""
        var end = start + Character.charCount(text.codePointAt(start))
        while (end < text.length) {
            val codePoint = text.codePointAt(end)
            val type = Character.getType(codePoint)
            if (type != Character.NON_SPACING_MARK.toInt() &&
                type != Character.COMBINING_SPACING_MARK.toInt() &&
                type != Character.ENCLOSING_MARK.toInt()
            ) {
                break
            }
            end += Character.charCount(codePoint)
        }
        return text.substring(start, end)
    }
}

internal data class EpubTextAlignmentSpan(
    val value: String,
) : AlignmentSpan {
    val justify: Boolean get() = value == "justify"

    override fun getAlignment(): Layout.Alignment = when (value) {
        "center" -> Layout.Alignment.ALIGN_CENTER
        "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }
}

private object EpubAlignmentTagHandler : Html.TagHandler {
    private data class Start(val tag: String)

    override fun handleTag(
        opening: Boolean,
        tag: String?,
        output: Editable?,
        xmlReader: XMLReader?,
    ) {
        val builder = output ?: return
        val normalized = tag?.lowercase().orEmpty()
        if (!normalized.startsWith("legado-align-")) return
        if (opening) {
            builder.setSpan(
                Start(normalized),
                builder.length,
                builder.length,
                Spanned.SPAN_MARK_MARK,
            )
            return
        }
        val start = builder.getSpans(0, builder.length, Start::class.java)
            .lastOrNull { it.tag == normalized }
            ?: return
        val from = builder.getSpanStart(start)
        builder.removeSpan(start)
        if (from < builder.length) {
            builder.setSpan(
                EpubTextAlignmentSpan(normalized.removePrefix("legado-align-")),
                from,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}

internal data class DropCapLayoutSpan(
    val initial: String,
    val textSize: Float,
    val width: Float,
    val gap: Float,
    val typeface: Typeface?,
) {
    val indent: Int get() = ceil(width + gap).toInt()
}
