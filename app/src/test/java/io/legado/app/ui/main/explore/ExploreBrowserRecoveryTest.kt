package io.legado.app.ui.main.explore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreBrowserRecoveryTest {

    @Test
    fun `vbook runtime errors offer source browser recovery`() {
        assertTrue(
            shouldOfferSourceBrowser(
                errorMessage = "VbookPluginException: Khong the chay script",
                sourceUrl = "vbook://plugin/59bd56b54548f60c6addd0c8",
                browserUrl = "https://www.piaotianapp.com",
            )
        )
    }

    @Test
    fun `cloudflare errors offer browser recovery for regular sources`() {
        assertTrue(
            shouldOfferSourceBrowser(
                errorMessage = "HTTP 403 Cloudflare challenge",
                sourceUrl = "https://reader.example.test",
                browserUrl = "https://reader.example.test/login",
            )
        )
    }

    @Test
    fun `unrelated errors without a browser url keep normal retry`() {
        assertFalse(
            shouldOfferSourceBrowser(
                errorMessage = "Unexpected parser result",
                sourceUrl = "https://reader.example.test",
                browserUrl = null,
            )
        )
    }
}
