package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TranslationContentSanitizerTest {

    @Test
    fun removesEpubReaderMarkersAndKeepsParagraphs() {
        val value = TranslationContentSanitizer.sanitize(
            "<usehtml><legado-align-center><p>第一段。</p></legado-align-center></usehtml>" +
                "<usehtml><p>第二段。</p></usehtml>",
        )

        assertEquals("第一段。\n\n第二段。", value)
        assertFalse(TranslationContentSanitizer.containsMarkup(value))
    }

    @Test
    fun removesEscapedTagsAndIgnoredDocumentNodes() {
        val value = TranslationContentSanitizer.sanitize(
            "&lt;style&gt;.x{display:none}&lt;/style&gt;" +
                "&lt;p&gt;正文&lt;/p&gt;",
        )

        assertEquals("正文", value)
        assertFalse(value.contains("style"))
    }

    @Test
    fun leavesPlainTextUnchangedApartFromWhitespaceNormalization() {
        val value = TranslationContentSanitizer.sanitize("第一行。\n\n第二行。")

        assertEquals("第一行。\n\n第二行。", value)
        assertFalse(TranslationContentSanitizer.containsMarkup(value))
    }
}
