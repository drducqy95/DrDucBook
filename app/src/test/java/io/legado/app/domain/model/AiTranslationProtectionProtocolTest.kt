package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationProtectionProtocolTest {

    @Test
    fun protectsMarkupUrlsAndAppPlaceholders() {
        val protected = AiTranslationProtectionProtocol.protect(
            "Open <ruby>Ten</ruby> at https://example.test/a?q=1 and keep {name}."
        )

        assertFalse(protected.value.contains("<ruby>"))
        assertFalse(protected.value.contains("https://example.test"))
        assertFalse(protected.value.contains("{name}"))
        assertTrue(protected.value.contains("__LG_KEEP_000__"))
        assertTrue(protected.value.contains("__LG_KEEP_001__"))
        assertTrue(protected.value.contains("__LG_KEEP_002__"))
    }

    @Test
    fun restoresProtectedTokensAfterTranslation() {
        val protected = AiTranslationProtectionProtocol.protect(
            "Xin <b>chao</b> {reader}"
        )

        assertEquals(
            "Chao <b>chao</b> {reader}",
            protected.restore("Chao __LG_KEEP_000__chao__LG_KEEP_001__ __LG_KEEP_002__"),
        )
    }

    @Test
    fun reportsMissingProtectedTokens() {
        val protected = AiTranslationProtectionProtocol.protect("<b>A</b> {name}")

        assertEquals(
            listOf("__LG_KEEP_001__"),
            protected.missingPlaceholders("__LG_KEEP_000__A __LG_KEEP_002__"),
        )
    }

    @Test
    fun reportsDuplicatedOrReorderedProtectedTokens() {
        val protected = AiTranslationProtectionProtocol.protect("<b>A</b>")

        assertTrue(
            protected.integrityViolations(
                "__LG_KEEP_000__A__LG_KEEP_000____LG_KEEP_001__"
            ).isNotEmpty()
        )
        assertTrue(
            protected.integrityViolations(
                "__LG_KEEP_001__A__LG_KEEP_000__"
            ).isNotEmpty()
        )
        assertTrue(
            protected.integrityViolations(
                "__LG_KEEP_000__A__LG_KEEP_001__"
            ).isEmpty()
        )
    }

    @Test
    fun generatedPlaceholdersDoNotCollideWithSourceText() {
        val source = "literal __LG_KEEP_000__ then <b>text</b>"

        val protected = AiTranslationProtectionProtocol.protect(source)

        assertFalse(protected.replacements.any { it.placeholder == "__LG_KEEP_000__" })
        assertEquals(source, protected.restore(protected.value))
    }
}
