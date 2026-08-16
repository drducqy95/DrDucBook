package io.legado.app.ui.widget.components.text

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownBlockNormalizerTest {

    @Test
    fun `normalizes compact ai bullet lists`() {
        val raw = "Toi co the giup ban:\n\n" +
            "\u2022 \uD83D\uDCD6 Tim kiem sach- \uD83D\uDCDD Tom tat- \uD83D\uDD16 Quan ly."

        assertEquals(
            "Toi co the giup ban:\n\n" +
                "- \uD83D\uDCD6 Tim kiem sach\n" +
                "- \uD83D\uDCDD Tom tat\n" +
                "- \uD83D\uDD16 Quan ly.",
            normalizeMarkdownForChatRender(raw),
        )
    }

    @Test
    fun `keeps ordinary spaced dash text intact`() {
        val raw = "Cong thuc A - B van la mot cau binh thuong."

        assertEquals(raw, normalizeMarkdownForChatRender(raw))
    }

    @Test
    fun `separates compact vietnamese follow up sentence`() {
        val raw = "tien do docB\u1ea1n mu\u1ed1n doc tiep?"

        assertEquals(
            "tien do doc\n\nB\u1ea1n mu\u1ed1n doc tiep?",
            normalizeMarkdownForChatRender(raw),
        )
    }
}
