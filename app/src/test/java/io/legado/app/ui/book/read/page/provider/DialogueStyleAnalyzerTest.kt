package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueStyleAnalyzerTest {

    @Test
    fun marksQuotedDialogueButNotNarration() {
        val text = "Anh nói: “Tôi sẽ trở lại.” rồi rời đi."
        val mask = DialogueStyleAnalyzer.italicMask(text)

        assertFalse(mask[text.indexOf("Anh")])
        assertTrue(mask[text.indexOf('“')])
        assertTrue(mask[text.indexOf("trở")])
        assertFalse(mask[text.indexOf("rồi")])
    }

    @Test
    fun marksWholeLineThatStartsWithDialogueDash() {
        val text = "  — Tôi đồng ý.\nĐây là lời kể."
        val mask = DialogueStyleAnalyzer.italicMask(text)

        assertTrue(mask[text.indexOf('—')])
        assertTrue(mask[text.indexOf("đồng")])
        assertFalse(mask[text.indexOf("Đây")])
    }

    @Test
    fun ignoresUnmatchedQuoteAndHyphenatedWord() {
        val text = "Một dấu “ chưa đóng.\nWi-Fi vẫn hoạt động."
        val mask = DialogueStyleAnalyzer.italicMask(text)

        assertFalse(mask.any { it })
    }
}
