package io.legado.app.domain.webservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareTunnelModelsTest {

    @Test
    fun `quick tunnel points to the loopback web service`() {
        val command = CloudflareTunnelCommand.quick("/native/cloudflared", 1124)

        assertEquals("http://127.0.0.1:1124", command.last())
        assertTrue(command.contains("--no-autoupdate"))
        assertTrue(command.contains("http2"))
    }

    @Test
    fun `named public URL must use https`() {
        assertEquals(
            "https://reader.example.com",
            CloudflareTunnelCommand.normalizePublicUrl(" https://reader.example.com/ "),
        )
        assertNull(CloudflareTunnelCommand.normalizePublicUrl("http://reader.example.com"))
    }

    @Test
    fun `named tunnel reads its credential from a private token file`() {
        val command = CloudflareTunnelCommand.named(
            binaryPath = "/native/cloudflared",
            tokenFilePath = "/private/cloudflare-token",
        )

        assertTrue(command.contains("--token-file"))
        assertTrue(command.contains("/private/cloudflare-token"))
        assertTrue(!command.contains("--token"))
    }
}
