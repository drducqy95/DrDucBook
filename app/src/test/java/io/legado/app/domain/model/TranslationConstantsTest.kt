package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationConstantsTest {

    @Test
    fun targetLanguages_containsVietnameseWithStableCode() {
        assertTrue(TranslationConstants.targetLanguages.contains("vi" to "Tiếng Việt"))
        assertEquals(
            TranslationConstants.targetLanguages.size,
            TranslationConstants.targetLanguages.map { it.first }.distinct().size,
        )
    }

    @Test
    fun translationProviders_exposeAllSupportedStrategies() {
        assertEquals(
            setOf(
                TranslationConstants.PROVIDER_GOOGLE,
                TranslationConstants.PROVIDER_ML_KIT,
                TranslationConstants.PROVIDER_QUICK_TRANSLATOR,
                TranslationConstants.PROVIDER_NMT,
                TranslationConstants.PROVIDER_APP_AI,
            ),
            TranslationConstants.providerValues.toSet(),
        )
    }

    @Test
    fun localProviders_onlyExposeVietnameseTarget() {
        val expected = listOf(TranslationConstants.TARGET_VIETNAMESE to "Tiếng Việt")

        assertEquals(
            expected,
            TranslationConstants.targetLanguagesForProvider(
                TranslationConstants.PROVIDER_QUICK_TRANSLATOR
            ),
        )
        assertEquals(
            expected,
            TranslationConstants.targetLanguagesForProvider(TranslationConstants.PROVIDER_NMT),
        )
        assertTrue(
            !TranslationConstants.supportsTargetLanguage(
                TranslationConstants.PROVIDER_NMT,
                "en",
            )
        )
    }

    @Test
    fun networkAndAiProvidersRetainAllTargetLanguages() {
        assertEquals(
            TranslationConstants.targetLanguages,
            TranslationConstants.targetLanguagesForProvider(TranslationConstants.PROVIDER_GOOGLE),
        )
        assertEquals(
            TranslationConstants.targetLanguages,
            TranslationConstants.targetLanguagesForProvider(TranslationConstants.PROVIDER_APP_AI),
        )
    }

    @Test
    fun wifiRestriction_onlyAppliesToNetworkTranslationProviders() {
        assertTrue(
            TranslationConstants.requiresNetworkTranslation(TranslationConstants.PROVIDER_GOOGLE)
        )
        assertTrue(
            TranslationConstants.requiresNetworkTranslation(TranslationConstants.PROVIDER_APP_AI)
        )
        assertTrue(
            !TranslationConstants.requiresNetworkTranslation(
                TranslationConstants.PROVIDER_QUICK_TRANSLATOR
            )
        )
        assertTrue(
            !TranslationConstants.requiresNetworkTranslation(TranslationConstants.PROVIDER_NMT)
        )
    }
}
