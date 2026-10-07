package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitPronounNeutralizerTest {

    @Test
    fun testAncientModePronounNeutralization() {
        val input = "Anh ấy nhìn cô ấy và nói với họ."
        val expected = "Hắn nhìn nàng và nói với bọn họ."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.ANCIENT)
        assertEquals(expected, actual)
    }

    @Test
    fun testAutoModeEquivalentToAncient() {
        val input = "Anh ấy nhìn cô ấy và nói với họ."
        val expected = "Hắn nhìn nàng và nói với bọn họ."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.AUTO)
        assertEquals(expected, actual)
    }

    @Test
    fun testPluralPronounsAndElders() {
        val input = "Các anh ấy cùng các cô ấy đi gặp ông ấy và bà ấy."
        val expected = "Bọn họ cùng bọn họ đi gặp ông ta và bà ta."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.ANCIENT)
        assertEquals(expected, actual)
    }

    @Test
    fun testModernModePronounNeutralization() {
        val input = "Anh ấy nhìn cô ấy mỉm cười."
        val expected = "Anh nhìn cô mỉm cười."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.MODERN)
        assertEquals(expected, actual)
    }

    @Test
    fun testWesternModePronounNeutralization() {
        val input = "Anh ấy nhìn cô ấy và nói với họ."
        val expected = "Chàng nhìn nàng và nói với bọn họ."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.WESTERN)
        assertEquals(expected, actual)
    }

    @Test
    fun testOffModePreservesText() {
        val input = "Anh ấy nhìn cô ấy và nói với họ."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.OFF)
        assertEquals(input, actual)
    }

    @Test
    fun testSurnameAndCompoundWordProtection() {
        val input = "Dòng họ Lý có nhiều họ hàng, họ tên của anh ấy là gì?"
        val expected = "Dòng họ Lý có nhiều họ hàng, họ tên của hắn là gì?"
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.ANCIENT)
        assertEquals(expected, actual)
    }

    @Test
    fun testDialogueQuoteProtection() {
        val input = "Anh ấy nhìn cô ấy nói: “Anh ấy không phải kẻ thù!” rồi rời đi."
        val expected = "Hắn nhìn nàng nói: “Anh ấy không phải kẻ thù!” rồi rời đi."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.ANCIENT)
        assertEquals(expected, actual)
    }

    @Test
    fun testDialogueDashProtection() {
        val input = "— Anh ấy không đến — cô ấy thở dài."
        val expected = "— Anh ấy không đến — nàng thở dài."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.ANCIENT)
        assertEquals(expected, actual)
    }

    @Test
    fun testAnhTaAndCoTaNeutralization() {
        val input = "Anh ta và hắn ta đuổi theo cô ta."
        val expected = "Hắn và hắn đuổi theo nàng."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.ANCIENT)
        assertEquals(expected, actual)
    }

    @Test
    fun testFirstAndSecondPersonNarrativeLeakNeutralization() {
        val input = "Tô Hiểu Tôi cảm thấy trước mặt bạn có bóng đen."
        val expected = "Tô Hiểu cảm thấy trước mắt hắn có bóng đen."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.ANCIENT)
        assertEquals(expected, actual)
    }

    @Test
    fun testNovelSystemBracketsProtection() {
        val input = "Anh ta nhìn thấy 【Anh ấy đã mở khóa】 và [Cô ấy nhận thưởng]."
        val expected = "Hắn nhìn thấy 【Anh ấy đã mở khóa】 và [Cô ấy nhận thưởng]."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.ANCIENT)
        assertEquals(expected, actual)
    }

    @Test
    fun testExtendedSecondPersonNarrativeLeakNeutralization() {
        val input = "Nếu bạn muốn sống, bạn không thể lùi bước, bạn phải tiến lên."
        val expected = "Nếu hắn muốn sống, hắn không thể lùi bước, hắn phải tiến lên."
        val actual = MlKitPronounNeutralizer.neutralize(input, QuickTranslationPronounMode.ANCIENT)
        assertEquals(expected, actual)
    }
}
