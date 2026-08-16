package io.legado.app.ui.config.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationConfigInputTest {

    @Test
    fun `accepts trimmed chunk sizes across configured range`() {
        assertEquals(10, parseTranslationChunkSize(" 10 "))
        assertEquals(96, parseTranslationChunkSize("96"))
        assertEquals(6_000, parseTranslationChunkSize("6000"))
        assertEquals(10_000, parseTranslationChunkSize("10000"))
    }

    @Test
    fun `rejects malformed and out of range chunk sizes`() {
        assertNull(parseTranslationChunkSize("9"))
        assertNull(parseTranslationChunkSize("10001"))
        assertNull(parseTranslationChunkSize("6k"))
        assertNull(parseTranslationChunkSize(""))
    }
}
