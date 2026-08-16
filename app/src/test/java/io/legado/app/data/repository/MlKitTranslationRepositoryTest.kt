package io.legado.app.data.repository

import io.legado.app.domain.gateway.MlKitMissingLanguageModelException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitTranslationRepositoryTest {

    @Test
    fun normalizeLanguageAcceptsChineseScriptTags() {
        assertEquals("zh", normalizeMlKitLanguageTag("zh-Hans"))
        assertEquals("zh", normalizeMlKitLanguageTag("cmn-Hant"))
        assertEquals("vi", normalizeMlKitLanguageTag("vi-VN"))
    }

    @Test
    fun inferSourceLanguageUsesHanTextWhenLanguageIdIsUndetermined() {
        assertEquals("zh", inferMlKitSourceLanguage("\u53F6\u957F\u751F\u770B\u7740\u79E6\u8001\u8BF4\u9053"))
        assertEquals("zh", inferMlKitSourceLanguage("\u597D\u3002"))
        assertEquals(null, inferMlKitSourceLanguage("hello world"))
        assertEquals(
            "zh",
            inferMlKitSourceLanguage(String(Character.toChars(0x31350)) + "\u53f6"),
        )
    }

    @Test
    fun inferSourceLanguageUsesKanaAndHangulForShortText() {
        assertEquals("ja", inferMlKitSourceLanguage("\u3042\u308A\u304C\u3068\u3046"))
        assertEquals("ko", inferMlKitSourceLanguage("\uC548\uB155"))
    }

    @Test
    fun missingModelsReportsBothSidesWhenNoneDownloaded() {
        assertEquals(
            listOf("zh", "vi"),
            missingMlKitTranslationModels(
                sourceLanguage = "zh",
                targetLanguage = "vi",
                downloadedLanguageTags = emptySet(),
            )
        )
    }

    @Test
    fun missingModelsReportsOnlyMissingSide() {
        assertEquals(
            listOf("vi"),
            missingMlKitTranslationModels(
                sourceLanguage = "zh",
                targetLanguage = "vi",
                downloadedLanguageTags = setOf("zh"),
            )
        )
    }

    @Test
    fun missingModelsDeduplicatesSameLanguagePair() {
        assertEquals(
            listOf("zh"),
            missingMlKitTranslationModels(
                sourceLanguage = "zh",
                targetLanguage = "zh",
                downloadedLanguageTags = emptySet(),
            )
        )
    }

    @Test
    fun missingModelExceptionPointsToLanguagePackManager() {
        val error = MlKitMissingLanguageModelException(
            sourceLanguage = "zh",
            targetLanguage = "vi",
            missingLanguageTags = listOf("zh", "vi"),
        )

        assertTrue(error.message.orEmpty().contains("Google ML Kit language packs"))
        assertTrue(error.message.orEmpty().contains("zh, vi"))
    }
}
