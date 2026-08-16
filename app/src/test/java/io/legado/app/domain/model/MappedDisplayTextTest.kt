package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MappedDisplayTextTest {

    @Test
    fun rebasesRepeatedQtTermsAfterReaderContentProcessing() {
        val source = "甲惠桂英乙惠桂英丙"
        val generated = "A Hội Quế Anh quảng cáo B Hội Quế Anh C"
        val actual = "A Hội Quế Anh B Hội Quế Anh C"
        val firstSource = source.indexOf("惠桂英")
        val secondSource = source.indexOf("惠桂英", firstSource + 1)
        val firstDisplay = generated.indexOf("Hội Quế Anh")
        val secondDisplay = generated.indexOf("Hội Quế Anh", firstDisplay + 1)
        val mapping = MappedDisplayText(
            sourceText = source,
            displayText = generated,
            engine = "quick_translator_exact",
            segments = listOf(
                DisplaySourceSegment(
                    sourceStart = firstSource,
                    sourceEnd = firstSource + 3,
                    displayStart = firstDisplay,
                    displayEnd = firstDisplay + "Hội Quế Anh".length,
                ),
                DisplaySourceSegment(
                    sourceStart = secondSource,
                    sourceEnd = secondSource + 3,
                    displayStart = secondDisplay,
                    displayEnd = secondDisplay + "Hội Quế Anh".length,
                ),
            ),
        ).rebaseDisplayText(actual)
        val selectedStart = actual.lastIndexOf("Hội Quế Anh")

        val selected = mapping.mapSelection(
            displayStart = selectedStart,
            displayEnd = selectedStart + "Hội Quế Anh".length,
        )

        assertTrue(selected.isMapped)
        assertEquals(secondSource, selected.sourceStart)
        assertEquals(secondSource + 3, selected.sourceEnd)
        assertEquals(1f, selected.confidence)
    }
}
