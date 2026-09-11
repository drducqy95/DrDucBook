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

    @Test
    fun indentsNarrativeParagraphsTwoSpacesAndPreservesDialogueLines() {
        val input = """
            Trời vừa rạng sáng, Diệp Trường Sinh bước ra khỏi động phủ.
            — Ngươi muốn đi đâu?
            Một giọng nói vang lên từ phía sau.
            "Ta đi hái thuốc."
            — Cẩn thận một chút, sơn cốc dạo này không yên ổn.
            Gió núi thổi qua tà áo hắn, mang theo hơi lạnh buổi sớm.
        """.trimIndent()

        val expected = """
              Trời vừa rạng sáng, Diệp Trường Sinh bước ra khỏi động phủ.
            — Ngươi muốn đi đâu?
              Một giọng nói vang lên từ phía sau.
            "Ta đi hái thuốc."
            — Cẩn thận một chút, sơn cốc dạo này không yên ổn.
              Gió núi thổi qua tà áo hắn, mang theo hơi lạnh buổi sớm.
        """.trimIndent()

        assertEquals(
            expected,
            VietnameseTranslationPostProcessor.indentNarrativeParagraphs(input),
        )
    }

    @Test
    fun preservesBlankLinesAndNormalizesExistingNarrativeIndentation() {
        val input = "    Đoạn một có thụt 4 space.\n\n— Lời thoại không thụt.\n\nĐoạn hai không có thụt."
        val expected = "  Đoạn một có thụt 4 space.\n\n— Lời thoại không thụt.\n\n  Đoạn hai không có thụt."

        assertEquals(
            expected,
            VietnameseTranslationPostProcessor.indentNarrativeParagraphs(input),
        )
    }
}
