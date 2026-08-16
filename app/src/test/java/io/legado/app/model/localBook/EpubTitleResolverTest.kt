package io.legado.app.model.localBook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTitleResolverTest {

    @Test
    fun replacesUnknownPlaceholderWithVisibleHeading() {
        val result = EpubTitleResolver.resolve(
            resourceTitle = "Unknown",
            html = "<html><head><title>Unknown</title></head><body><h3>BÀN VỀ HAM MUỐN</h3></body></html>",
            fallback = "--卷首--",
        )

        assertEquals("BÀN VỀ HAM MUỐN", result.value)
        assertTrue(result.derivedFromBody)
    }

    @Test
    fun keepsMeaningfulNavigationTitle() {
        val result = EpubTitleResolver.resolve(
            resourceTitle = "Lời cảm ơn",
            html = "<h1>Duplicate body heading</h1>",
            fallback = "--卷首--",
        )

        assertEquals("Lời cảm ơn", result.value)
        assertFalse(result.derivedFromBody)
    }
}
