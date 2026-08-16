package io.legado.app.ui.browser

import io.legado.app.domain.model.BrowserPageTextTranslation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPageTranslationBridgeTest {

    @Test
    fun decodesWebViewQuotedSnapshot() {
        val payload = """{"nodes":[{"id":"n1","text":"第一章","contentHash":"abc"}]}"""
        val callbackValue = Json.encodeToString(payload)

        val nodes = BrowserPageTranslationBridge.decodeSnapshot(callbackValue)

        assertEquals(1, nodes.size)
        assertEquals("n1", nodes.single().id)
        assertEquals("第一章", nodes.single().text)
    }

    @Test
    fun scriptsExcludeEditableContentAndRestoreOriginalNodes() {
        val extraction = BrowserPageTranslationBridge.extractionScript()
        val restore = BrowserPageTranslationBridge.restoreOriginalScript()

        assertTrue(extraction.contains("INPUT"))
        assertTrue(extraction.contains("TEXTAREA"))
        assertTrue(extraction.contains("contenteditable"))
        assertTrue(restore.contains("entry.original"))
    }

    @Test
    fun applyScriptCarriesIdentityAndContentHash() {
        val script = BrowserPageTranslationBridge.applyTranslationsScript(
            listOf(
                BrowserPageTextTranslation(
                    id = "n9",
                    originalText = "原文",
                    translatedText = "Bản dịch",
                    contentHash = "f00d",
                )
            )
        )

        assertTrue(script.contains("n9"))
        assertTrue(script.contains("f00d"))
        assertTrue(script.contains("entry.original !== update.originalText"))
    }
}
