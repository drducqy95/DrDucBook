package io.legado.app.ui.book.read.page.provider

import android.app.Application
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DropCapHtmlSupportTest {

    @Test
    fun findsAUnicodeGraphemeAfterOpeningPunctuation() {
        assertEquals("A\u0301", DropCapHtmlSupport.firstGrapheme("\u201cA\u0301nh"))
        assertEquals("", DropCapHtmlSupport.firstGrapheme("...!?"))
    }

    @Test
    fun convertsReaderMarkerIntoAThreeLineLeadingMargin() {
        val output = DropCapHtmlSupport.parse(
            html = "<p><span data-legado-dropcap='true'>V</span>ào giữa tháng 9 năm 2000.</p>",
            basePaint = TextPaint().apply { textSize = 24f },
            targetHeight = 90f,
            gapPx = 8f,
        )

        assertFalse(output.toString().contains('\uE000'))
        assertTrue(output.toString().startsWith("ào giữa tháng 9"))
        val spans = output.getSpans(0, output.length, DropCapLayoutSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals("V", spans.single().initial)
        assertTrue(spans.single().indent > 8)
        assertTrue(spans.single().textSize > 24f)
    }

    @Test
    fun indentsAndReflowsOnlyTheFirstThreeLines() {
        val output = DropCapHtmlSupport.parse(
            html = "<p><span data-legado-dropcap='true'>V</span>ào giữa tháng 9" +
                "<br>đây là dòng thứ hai<br>đây là dòng thứ ba<br>đây là dòng thứ tư</p>",
            basePaint = TextPaint().apply { textSize = 24f },
            targetHeight = 90f,
            gapPx = 8f,
        )
        val paint = TextPaint().apply { textSize = 24f }
        fun layout(indents: IntArray? = null): StaticLayout {
            val builder = StaticLayout.Builder.obtain(output, 0, output.length, paint, 260)
            indents?.let { builder.setIndents(it, null) }
            return builder.build()
        }
        val initial = layout()
        val indents = DropCapHtmlSupport.lineIndents(output, initial)!!

        assertTrue(initial.lineCount > 3)
        assertTrue(indents[0] > 0)
        assertEquals(indents[0], indents[1])
        assertEquals(indents[0], indents[2])
        assertEquals(0, indents[3])
    }

    @Test
    fun justificationUsesTheWidthRemainingAfterTheDropCapInset() {
        assertEquals(
            760,
            calculateHtmlLineAvailableWidth(
                visibleWidth = 1000,
                absoluteStartX = 40,
                firstColumnStart = 280f,
                fallbackLineLeft = 0f,
            ),
        )
    }

    @Test
    fun dropCapMetricsFollowTheReaderTypeface() {
        fun span(typeface: Typeface): DropCapLayoutSpan {
            val output = DropCapHtmlSupport.parse(
                html = "<p><span data-legado-dropcap='true'>W</span>rapped text</p>",
                basePaint = TextPaint().apply {
                    textSize = 24f
                    this.typeface = typeface
                },
                targetHeight = 64f,
                gapPx = 8f,
            )
            return output.getSpans(
                0,
                output.length,
                DropCapLayoutSpan::class.java,
            ).single()
        }

        assertSame(Typeface.MONOSPACE, span(Typeface.MONOSPACE).typeface)
        assertSame(Typeface.SERIF, span(Typeface.SERIF).typeface)
    }

    @Test
    fun preservesEpubParagraphAlignment() {
        val output = DropCapHtmlSupport.parse(
            html = "<legado-align-center><p>Centered text</p></legado-align-center>",
            basePaint = TextPaint().apply { textSize = 24f },
            targetHeight = 64f,
            gapPx = 8f,
        )

        val span = output.getSpans(0, output.length, EpubTextAlignmentSpan::class.java).single()
        assertEquals(Layout.Alignment.ALIGN_CENTER, span.alignment)
        assertFalse(span.justify)
    }
}
