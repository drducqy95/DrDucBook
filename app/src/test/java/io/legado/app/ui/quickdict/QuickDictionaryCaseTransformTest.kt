package io.legado.app.ui.quickdict

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickDictionaryCaseTransformTest {

    @Test
    fun capitalizesTheRequestedNumberOfWordsWithoutChangingSpacing() {
        val source = "cực  sơn quyền quán, đại sư huynh"

        assertEquals(
            "Cực  sơn quyền quán, đại sư huynh",
            applyQuickDictionaryCaseTransform(
                source,
                QuickDictionaryCaseTransform.CAPITALIZE_ONE,
            ),
        )
        assertEquals(
            "Cực  Sơn Quyền quán, đại sư huynh",
            applyQuickDictionaryCaseTransform(
                source,
                QuickDictionaryCaseTransform.CAPITALIZE_THREE,
            ),
        )
        assertEquals(
            "Cực  Sơn Quyền Quán, Đại Sư Huynh",
            applyQuickDictionaryCaseTransform(
                source,
                QuickDictionaryCaseTransform.CAPITALIZE_ALL,
            ),
        )
    }

    @Test
    fun supportsLowercaseAndUppercaseActions() {
        val source = "Cực Sơn quyền quán"

        assertEquals(
            "cực sơn quyền quán",
            applyQuickDictionaryCaseTransform(source, QuickDictionaryCaseTransform.LOWERCASE),
        )
        assertEquals(
            "CỰC SƠN QUYỀN QUÁN",
            applyQuickDictionaryCaseTransform(source, QuickDictionaryCaseTransform.UPPERCASE),
        )
    }

    @Test
    fun contextPreviewKeepsRawAnchoredBetweenAdjacentSourceText() {
        val raw = "解开，走到李追远面前：“来"
        val preview = quickDictionaryContextPreview(
            contextBefore = "甲朝里头抠着，终于将堵在里头的棉球给弄了出来。叮叮叮。随后他伸手解开绳结，",
            raw = raw,
            contextAfter = "，李追远抬头看向门外，示意众人安静下来。",
            contextChars = 8,
        )

        assertEquals("…他伸手解开绳结，$raw，李追远抬头看向…", preview.text)
        assertEquals(raw, preview.raw)
    }
}
