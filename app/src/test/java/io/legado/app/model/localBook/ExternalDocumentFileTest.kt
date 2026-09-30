package io.legado.app.model.localBook

import org.jsoup.Jsoup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDocumentFileTest {

    @Test
    fun htmlMarkupIsRemovedBeforeTranslation() {
        val text = ExternalDocumentFile.stripHtmlForTranslation(
            Jsoup.parse(
                """
                <html><body>
                <Usehtml><LKAdo-alignterke><h1>Giới thiệu</h1></LKAdo-alignterke></Usehtml>
                <p>Thiên địa có linh cơ, vạn vật ăn linh cơ mà phát triển.</p>
                </body></html>
                """.trimIndent()
            )
        )

        assertTrue(text.contains("# Giới thiệu"))
        assertTrue(text.contains("Thiên địa có linh cơ"))
        assertFalse(text.contains("<Usehtml>"))
        assertFalse(text.contains("LKAdo-alignterke"))
    }

    @Test
    fun markdownFormattingIsRemovedButHeadingsRemainAsChapterMarkers() {
        val text = ExternalDocumentFile.stripMarkdownForTranslation(
            """
            # Chương 1

            **Nội dung** với [liên kết](https://example.com).
            """.trimIndent()
        )

        assertTrue(text.contains("# Chương 1"))
        assertTrue(text.contains("Nội dung với liên kết."))
        assertFalse(text.contains("**"))
        assertFalse(text.contains("https://example.com"))
    }
}
