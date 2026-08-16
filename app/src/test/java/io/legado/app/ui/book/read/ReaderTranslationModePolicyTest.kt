package io.legado.app.ui.book.read

import io.legado.app.domain.model.ReaderContentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTranslationModePolicyTest {

    @Test
    fun enablingProviderResult_switchesFromHanVietToProviderMode() {
        assertEquals(
            ReaderContentMode.AI,
            nextProviderTranslationDisplayMode(
                currentMode = ReaderContentMode.HAN_VIET,
                providerMode = ReaderContentMode.AI,
            ),
        )
    }

    @Test
    fun disablingProviderResult_returnsToRaw() {
        assertEquals(
            ReaderContentMode.RAW,
            nextProviderTranslationDisplayMode(
                currentMode = ReaderContentMode.NMT,
                providerMode = ReaderContentMode.NMT,
            ),
        )
    }

    @Test
    fun displayFlag_onlyMatchesCurrentProviderMode() {
        assertTrue(
            isProviderTranslationDisplayed(
                currentMode = ReaderContentMode.GOOGLE,
                providerMode = ReaderContentMode.GOOGLE,
            )
        )
        assertFalse(
            isProviderTranslationDisplayed(
                currentMode = ReaderContentMode.QUICK_TRANSLATOR,
                providerMode = ReaderContentMode.AI,
            )
        )
        assertTrue(
            isProviderTranslationDisplayed(
                currentMode = ReaderContentMode.TRANSLATION,
                providerMode = ReaderContentMode.AI,
            )
        )
    }
}
