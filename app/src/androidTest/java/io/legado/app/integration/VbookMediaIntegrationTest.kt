package io.legado.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.MediaProtocol
import io.legado.app.help.vbook.VbookMediaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VbookMediaIntegrationTest {

    @Test
    fun vbookHlsTrackIsPlayableAndDownloadable() {
        val parsed = VbookMediaParser.parseTrack(
            json = """{"data":"https://cdn.example/video/master.m3u8","type":"native"}""",
            candidate = VbookMediaParser.ServerCandidate(
                title = "1080p",
                data = "https://cdn.example/video/master.m3u8",
                headers = mapOf("Referer" to "https://source.example/"),
            ),
            defaultKind = MediaContentKind.VIDEO,
            idPrefix = "phase08",
        )

        val variant = parsed.variants.single()
        assertEquals(MediaProtocol.HLS, variant.protocol)
        assertEquals("https://source.example/", variant.referer)
        assertTrue(variant.downloadSupported)
    }
}
