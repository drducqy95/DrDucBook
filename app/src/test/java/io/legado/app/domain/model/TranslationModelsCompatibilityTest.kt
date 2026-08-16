package io.legado.app.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationModelsCompatibilityTest {

    @Test
    fun legacyDictionaryPairWithoutTypeGetsVietphraseType() {
        val legacy = Gson().fromJson(
            """{"original":"\u7cfb\u7edf","translation":"he thong"}""",
            DictPair::class.java,
        )

        val normalized = legacy.normalizedForRuntime()

        assertEquals("\u7cfb\u7edf", normalized.original)
        assertEquals("he thong", normalized.translation)
        assertEquals(QuickDictionaryType.VIETPHRASE, normalized.type)
    }
}
