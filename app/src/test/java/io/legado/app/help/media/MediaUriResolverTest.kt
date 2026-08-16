package io.legado.app.help.media

import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.MediaProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MediaUriResolverTest {

    @Test
    fun `resolves local file and content audio formats`() {
        val mp3 = MediaUriResolver.resolve(
            sourceId = "local",
            contentId = "chapter-1",
            title = "Chương 1",
            uri = "file:///storage/emulated/0/Books/chapter.mp3",
            defaultKind = MediaContentKind.UNKNOWN,
        ).variants.single()
        val m4a = MediaUriResolver.resolve(
            sourceId = "local",
            contentId = "chapter-2",
            title = "Chương 2",
            uri = "content://io.legato.kazusa.media/chapter.m4a",
            defaultKind = MediaContentKind.UNKNOWN,
        ).variants.single()

        assertEquals(MediaContentKind.AUDIO, mp3.contentKind)
        assertEquals("audio/mpeg", mp3.mimeType)
        assertEquals(MediaContentKind.AUDIO, m4a.contentKind)
        assertEquals("audio/mp4", m4a.mimeType)
        assertTrue(mp3.downloadSupported)
        assertNotEquals(mp3.id, m4a.id)
    }

    @Test
    fun `keeps hls headers referer and expiry`() {
        val variant = MediaUriResolver.resolve(
            sourceId = "video-source",
            contentId = "episode-1",
            title = "Tập 1",
            uri = "https://cdn.test/master.m3u8?expires=2000000000",
            defaultKind = MediaContentKind.VIDEO,
            headers = mapOf(
                "User-Agent" to "Legado test",
                "Referer" to "https://video.test/",
            ),
            resolvedAt = 123L,
        ).variants.single()

        assertEquals(MediaProtocol.HLS, variant.protocol)
        assertEquals(MediaContentKind.VIDEO, variant.contentKind)
        assertEquals("application/x-mpegURL", variant.mimeType)
        assertEquals("https://video.test/", variant.referer)
        assertEquals(2_000_000_000_000L, variant.expiresAt)
        assertTrue(variant.downloadSupported)
        assertFalse(variant.externalPlayerRequired)
    }

    @Test
    fun `rejects unsupported schemes`() {
        try {
            MediaUriResolver.resolve(
                sourceId = "source",
                contentId = "episode",
                title = "Tập",
                uri = "javascript:playVideo()",
                defaultKind = MediaContentKind.VIDEO,
            )
            fail("Expected unsupported media URI to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
