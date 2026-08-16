package io.legado.app.domain.usecase

import io.legado.app.domain.model.TranslationConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateChapterQualityTest {

    @Test
    fun vietnameseOutputRejectsRemainingCjkPhrase() {
        assertTrue(
            hasUntranslatedCjkForVietnamese(
                source = "\u4ed6\u8bf4\u53f6\u957f\u751f\u6765\u4e86",
                translated = "Han noi \u53f6\u957f\u751f da den.",
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
    }

    @Test
    fun vietnameseOutputRejectsSingleRemainingCjkCharacter() {
        assertTrue(
            hasUntranslatedCjkForVietnamese(
                source = "\u4ed6\u8bf4\u53f6\u957f\u751f\u6765\u4e86",
                translated = "Han noi Diep Truong Sinh \u6765.",
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
    }

    @Test
    fun vietnameseOutputRejectsCjkEvenWhenSourceIsDilutedByMarkers() {
        assertTrue(
            hasUntranslatedCjkForVietnamese(
                source = "[[P0]] Chapter 1: \u53f6",
                translated = "Chuong 1: Diep \u53f6",
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
    }

    @Test
    fun nonVietnameseTargetDoesNotUseVietnameseCjkGate() {
        assertFalse(
            hasUntranslatedCjkForVietnamese(
                source = "\u4ed6\u8bf4\u53f6\u957f\u751f\u6765\u4e86",
                translated = "\u53f6\u957f\u751f arrived.",
                targetLanguage = "zh",
            )
        )
    }
}
