package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitSuggestionTest {

    @Test
    fun testPresetsContainAllRequiredLanguagePairs() {
        val presets = MlKitLanguagePair.PRESETS
        assertEquals(4, presets.size)

        val ja = presets.first { it.id == "zh-ja" }
        assertEquals("zh", ja.sourceLang)
        assertEquals("ja", ja.targetLang)
        assertEquals("JA", ja.shortTag)

        val en = presets.first { it.id == "zh-en" }
        assertEquals("zh", en.sourceLang)
        assertEquals("en", en.targetLang)
        assertEquals("EN", en.shortTag)

        val ko = presets.first { it.id == "zh-ko" }
        assertEquals("zh", ko.sourceLang)
        assertEquals("ko", ko.targetLang)
        assertEquals("KO", ko.shortTag)

        val vi = presets.first { it.id == "zh-vi" }
        assertEquals("zh", vi.sourceLang)
        assertEquals("vi", vi.targetLang)
        assertEquals("VI", vi.shortTag)
    }

    @Test
    fun testLanguagePairsSupportedByTranslationConstants() {
        MlKitLanguagePair.PRESETS.forEach { pair ->
            assertTrue(
                "Target language ${pair.targetLang} must be supported by ML Kit",
                TranslationConstants.supportsTargetLanguage(
                    TranslationConstants.PROVIDER_ML_KIT,
                    pair.targetLang
                )
            )
        }
    }

    @Test
    fun testJapaneseKanaRomajizer() {
        assertEquals("Riyon", JapaneseKanaRomajizer.toRomaji("リヨン"))
        assertEquals("Naruto", JapaneseKanaRomajizer.toRomaji("ナルト"))
        assertEquals("Sasuke", JapaneseKanaRomajizer.toRomaji("サスケ"))
        assertEquals("Uchiha", JapaneseKanaRomajizer.toRomaji("うちは"))
        assertEquals("Uchiha Madara", JapaneseKanaRomajizer.toRomaji("うちはマダラ"))
    }

    @Test
    fun testKoreanHangulRomanizer() {
        assertEquals("Riong", KoreanHangulRomanizer.toLatin("리옹"))
    }

    @Test
    fun testSinoForeignNameDetectorAnimeAndForeignNames() {
        val uchiha = SinoForeignNameDetector.classifyName("宇智波")
        assertEquals("Uchiha", uchiha?.suggested)
        assertEquals("japanese", uchiha?.origin)

        val madara = SinoForeignNameDetector.classifyName("宇智波斑")
        assertEquals("Uchiha Madara", madara?.suggested)
        assertEquals("japanese", madara?.origin)

        val naruto = SinoForeignNameDetector.classifyName("漩涡鸣人")
        assertEquals("Uzumaki Naruto", naruto?.suggested)
        assertEquals("japanese", naruto?.origin)

        val byakugan = SinoForeignNameDetector.classifyName("白眼")
        assertEquals("Byakugan", byakugan?.suggested)
        assertEquals("japanese", byakugan?.origin)

        val lyon = SinoForeignNameDetector.classifyName("李昂")
        assertEquals("Lyon", lyon?.suggested)

        val caesar = SinoForeignNameDetector.classifyName("凯撒")
        assertEquals("Caesar", caesar?.suggested)
    }
}
