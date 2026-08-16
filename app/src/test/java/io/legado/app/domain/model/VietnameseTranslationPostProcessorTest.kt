package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VietnameseTranslationPostProcessorTest {

    @Test
    fun capitalizesParagraphAndSentenceStartsWithoutChangingLayout() {
        val input = "  xin chào. “bạn khỏe chứ?”\r\n\tđây là đoạn hai!  vẫn tiếp tục."

        assertEquals(
            "  Xin chào. “Bạn khỏe chứ?”\r\n\tĐây là đoạn hai!  Vẫn tiếp tục.",
            VietnameseTranslationPostProcessor.capitalizeSentences(input),
        )
    }

    @Test
    fun doesNotTreatDecimalPointAsSentenceBoundary() {
        assertEquals(
            "Giá là 1.5 triệu. 1 cái bánh.",
            VietnameseTranslationPostProcessor.capitalizeSentences("giá là 1.5 triệu. 1 cái bánh."),
        )
    }

    @Test
    fun capitalizesAfterCjkPunctuationAndUnicodeParagraphBreaks() {
        assertEquals(
            "Câu một。 Câu hai！ Câu ba？\u2029Đoạn mới.",
            VietnameseTranslationPostProcessor.capitalizeSentences(
                "câu một。 câu hai！ câu ba？\u2029đoạn mới."
            ),
        )
    }

    @Test
    fun skipsMarkupBeforeCapitalizingVisibleText() {
        assertEquals(
            "<em>Xin chào.</em> <strong>Bạn khỏe?</strong>\n\t— Đoạn mới.",
            VietnameseTranslationPostProcessor.capitalizeSentences(
                "<em>xin chào.</em> <strong>bạn khỏe?</strong>\n\t— đoạn mới."
            ),
        )
    }
}
