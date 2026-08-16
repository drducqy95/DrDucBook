package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiTranslationLayoutProtocolTest {

    @Test
    fun decodesCompleteResponseWhenAllMarkersArePresentInOrder() {
        val encoded = AiTranslationLayoutProtocol.encode("One\n\nTwo\n\nThree")

        assertEquals(3, encoded.paragraphCount)
        assertEquals(
            "Mot\n\nHai\n\nBa",
            AiTranslationLayoutProtocol.decodeComplete(
                "[[P0]]\nMot\n\n[[P1]]\nHai\n\n[[P2]]\nBa",
                encoded.paragraphCount,
            )
        )
    }

    @Test
    fun rejectsMissingMarkerButCanStripSurvivingMarkersForLegacyValidation() {
        val response = "[[P0]]\nMot\n\nHai\n\n[[P2]]\nBa"

        assertNull(AiTranslationLayoutProtocol.decodeComplete(response, expectedParagraphs = 3))
        assertEquals("Mot\n\nHai\n\n\nBa", AiTranslationLayoutProtocol.stripMarkers(response))
    }

    @Test
    fun acceptsCommonMarkerVariantsInOrder() {
        assertEquals(
            "Mot\n\nHai",
            AiTranslationLayoutProtocol.decodeCompleteOrPlain(
                "[P0] Mot\n\n\u3010P1\u3011 Hai",
                expectedParagraphs = 2,
            ),
        )
    }

    @Test
    fun acceptsUnframedBlankLineOutputOnlyWhenParagraphCountIsExact() {
        assertEquals(
            "Mot\n\nHai",
            AiTranslationLayoutProtocol.decodeCompleteOrPlain(
                "Mot\n\nHai",
                expectedParagraphs = 2,
            ),
        )
        assertNull(
            AiTranslationLayoutProtocol.decodeCompleteOrPlain(
                "Mot\n\nHai\n\nBa",
                expectedParagraphs = 2,
            )
        )
    }

    @Test
    fun acceptsOneNonEmptyLinePerExpectedParagraph() {
        assertEquals(
            "Mot\n\nHai\n\nBa",
            AiTranslationLayoutProtocol.decodeCompleteOrPlain(
                "Mot\nHai\nBa",
                expectedParagraphs = 3,
            ),
        )
        assertNull(
            AiTranslationLayoutProtocol.decodeCompleteOrPlain(
                "Mot\nHai\nBa\nBon",
                expectedParagraphs = 3,
            )
        )
    }

    @Test
    fun reflowsMergedTranslationWithoutDroppingOrDuplicatingTokens() {
        assertEquals(
            "Mot hai ba bon\n\nnam sau bay tam",
            AiTranslationLayoutProtocol.decodeCompleteOrReflow(
                text = "Mot hai ba bon nam sau bay tam",
                sourceText = "1234\n\n5678",
            ),
        )
    }
}
