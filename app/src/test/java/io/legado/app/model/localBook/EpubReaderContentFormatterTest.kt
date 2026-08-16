package io.legado.app.model.localBook

import org.jsoup.Jsoup
import org.jsoup.select.Elements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderContentFormatterTest {

    @Test
    fun preservesMixedTextImageOrderAndCssFirstLetter() {
        val document = Jsoup.parse(
            """
            <html><head><style>
              p.chapter::first-letter { float: left; font-size: 3em; }
            </style></head><body>
              <p class="chapter">Once upon <img src="images/one.jpg"> a time.</p>
              <p>Second paragraph.</p>
            </body></html>
            """.trimIndent()
        )
        document.body().prependElement("style").appendText(document.selectFirst("style")!!.data())

        val output = EpubReaderContentFormatter.format(Elements(document.body()))

        assertTrue(output.startsWith("<usehtml>"))
        assertTrue(output.contains("<span data-legado-dropcap=\"true\">O</span>"))
        assertTrue(output.indexOf("nce upon") < output.indexOf("images/one.jpg"))
        assertTrue(output.indexOf("images/one.jpg") < output.indexOf("a time."))
        assertTrue(output.endsWith("Second paragraph."))
    }

    @Test
    fun keepsEachTopLevelParagraphAsBoundedBlock() {
        val document = Jsoup.parseBodyFragment(
            "<div><p><span class='dropcap'>A</span>lpha</p><p>Beta</p></div>"
        )

        val output = EpubReaderContentFormatter.format(Elements(document.body()))
        val lines = output.lines()

        assertEquals(2, lines.size)
        assertTrue(lines.first().startsWith("<usehtml>"))
        assertFalse(lines.last().contains("usehtml"))
        assertEquals("Beta", lines.last())
    }

    @Test
    fun emitsAReaderDropCapMarkerForTheRealCalibreClassShape() {
        val document = Jsoup.parseBodyFragment(
            "<p class='calibre13'><span class='dropcap'>V</span>ào giữa tháng 9 năm 2000.</p>"
        )

        val output = EpubReaderContentFormatter.format(Elements(document.body()))

        assertTrue(output.contains("<span class=\"dropcap\" data-legado-dropcap=\"true\">V</span>"))
        assertTrue(output.contains("ào giữa tháng 9 năm 2000."))
    }

    @Test
    fun materializesExternalCssAlignmentForReaderLayout() {
        val document = Jsoup.parse(
            """
            <html><head><style>
              .centered { text-align: center; }
              .body { text-align: justify; }
            </style></head><body>
              <p class='centered'>Website: example.com</p>
              <p class='body'>A justified paragraph.</p>
            </body></html>
            """.trimIndent()
        )
        document.body().prependElement("style").appendText(document.selectFirst("style")!!.data())

        val output = EpubReaderContentFormatter.format(Elements(document.body()))

        assertTrue(output.contains("<legado-align-center>"))
        assertTrue(output.contains("<legado-align-justify>"))
    }

    @Test
    fun supportsLettrineAndKeepsTheWholeCombiningGrapheme() {
        val document = Jsoup.parseBodyFragment(
            "<p><span class='lettrine'>A\u0301</span>nh sang.</p>"
        )

        val output = EpubReaderContentFormatter.format(Elements(document.body()))

        assertTrue(output.contains("data-legado-dropcap=\"true\">A\u0301</span>"))
    }

    @Test
    fun skipsHeadingsAndPunctuationOnlyParagraphs() {
        val document = Jsoup.parseBodyFragment(
            "<h1 class='dropcap'>Heading</h1><p class='dropcap'>... !?</p>"
        )

        val output = EpubReaderContentFormatter.format(Elements(document.body()))

        assertFalse(output.contains("data-legado-dropcap"))
    }
}
