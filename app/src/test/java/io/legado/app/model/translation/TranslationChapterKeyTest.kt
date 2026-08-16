package io.legado.app.model.translation

import org.junit.Assert.assertNotEquals
import org.junit.Test

class TranslationChapterKeyTest {

    @Test
    fun taskIdentity_isolatedByProviderAndTargetLanguage() {
        val googleVietnamese = TranslationChapterKey("book", 3, "google", "vi")
        val googleEnglish = TranslationChapterKey("book", 3, "google", "en")
        val nmtVietnamese = TranslationChapterKey("book", 3, "nmt", "vi")

        assertNotEquals(googleVietnamese, googleEnglish)
        assertNotEquals(googleVietnamese, nmtVietnamese)
    }
}
