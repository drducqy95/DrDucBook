package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContentModeTest {

    @Test
    fun mlKitUsesItsOwnReaderCacheIdentity() {
        assertEquals(
            ReaderTranslationCacheIdentity(
                provider = TranslationConstants.PROVIDER_ML_KIT,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            ),
            ReaderContentMode.ML_KIT.translationCacheIdentity(
                TranslationConstants.TARGET_VIETNAMESE,
            ),
        )
    }

    @Test
    fun lateAiResultCannotReplaceQuickTranslatorDisplay() {
        assertFalse(
            ReaderContentMode.QUICK_TRANSLATOR.displaysTranslationProvider(
                provider = TranslationConstants.PROVIDER_APP_AI,
                selectedTargetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
        assertTrue(
            ReaderContentMode.AI.displaysTranslationProvider(
                provider = TranslationConstants.PROVIDER_APP_AI,
                selectedTargetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
    }

    @Test
    fun aggregateTranslationModeAcceptsPreferredProviderResults() {
        assertTrue(
            ReaderContentMode.TRANSLATION.displaysTranslationProvider(
                provider = TranslationConstants.PROVIDER_APP_AI,
                selectedTargetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
        assertTrue(
            ReaderContentMode.TRANSLATION.displaysTranslationProvider(
                provider = TranslationConstants.PROVIDER_NMT,
                selectedTargetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
        assertTrue(
            ReaderContentMode.TRANSLATION.displaysTranslationProvider(
                provider = TranslationConstants.PROVIDER_QUICK_TRANSLATOR,
                selectedTargetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
    }

    @Test
    fun quickDictionaryEditingIsOnlyAvailableInQuickTranslatorMode() {
        assertTrue(ReaderContentMode.QUICK_TRANSLATOR.supportsQuickDictionaryEditing())
        ReaderContentMode.entries
            .filterNot { it == ReaderContentMode.QUICK_TRANSLATOR }
            .forEach { mode -> assertFalse(mode.supportsQuickDictionaryEditing()) }
    }
}
