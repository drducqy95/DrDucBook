package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickDictionaryUniverseMatcherTest {

    private val cultivation = QuickDictionaryUniverse(
        key = "cultivation",
        name = "Cultivation",
        contextMarkers = listOf("Tu tiên giới", "regex:phi thăng\\s+thượng giới"),
    )
    private val academy = QuickDictionaryUniverse(
        key = "academy",
        name = "Academy",
        contextMarkers = listOf("Học viện ma pháp"),
    )

    @Test
    fun noMarker_doesNotActivateUniverseScope() {
        assertNull(
            QuickDictionaryUniverseMatcher.activeUniverseKey(
                listOf(cultivation, academy),
                "Một chương không có marker.",
            )
        )
    }

    @Test
    fun latestMarker_selectsCurrentWorld() {
        assertEquals(
            "academy",
            QuickDictionaryUniverseMatcher.activeUniverseKey(
                listOf(cultivation, academy),
                "Tu tiên giới\n...\nHọc viện ma pháp\nChương mới",
            )
        )
    }

    @Test
    fun regexMarker_isSupported() {
        assertEquals(
            "cultivation",
            QuickDictionaryUniverseMatcher.activeUniverseKey(
                listOf(cultivation),
                "Nhân vật phi thăng   thượng giới.",
            )
        )
    }
}
