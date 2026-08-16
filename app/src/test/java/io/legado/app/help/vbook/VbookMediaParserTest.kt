package io.legado.app.help.vbook

import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.MediaProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VbookMediaParserTest {

    @Test
    fun `parses video server and track with headers subtitles and audio tracks`() {
        val servers = VbookMediaParser.parseServers(
            json = """
                [
                  {"name": "Vietsub", "type": "section"},
                  {"title": "1080p", "data": "https://cdn.test/master.m3u8"}
                ]
            """.trimIndent(),
            fallbackUrl = "https://video.test/episode/1",
        )

        assertEquals(1, servers.size)
        val parsed = VbookMediaParser.parseTrack(
            json = """
                {
                  "data": "https://cdn.test/master.m3u8?expires=2000000000",
                  "type": "native",
                  "headers": {
                    "User-Agent": "VBook Test",
                    "Referer": "https://video.test/"
                  },
                  "subtitles": [
                    {
                      "label": "Tiếng Việt",
                      "lang": "vi",
                      "url": "https://cdn.test/subtitle.vtt",
                      "default": true
                    }
                  ],
                  "audioTracks": [
                    {
                      "label": "Thuyết minh",
                      "language": "vi",
                      "url": "https://cdn.test/audio.m4a"
                    }
                  ]
                }
            """.trimIndent(),
            candidate = servers.single(),
            defaultKind = MediaContentKind.VIDEO,
            idPrefix = "episode-1",
        )

        val variant = parsed.variants.single()
        assertEquals(MediaProtocol.HLS, variant.protocol)
        assertEquals(MediaContentKind.VIDEO, variant.contentKind)
        assertEquals("application/x-mpegURL", variant.mimeType)
        assertEquals("https://video.test/", variant.referer)
        assertEquals(2_000_000_000_000L, variant.expiresAt)
        assertTrue(variant.downloadSupported)
        assertFalse(variant.externalPlayerRequired)
        assertEquals("text/vtt", parsed.subtitles.single().mimeType)
        assertTrue(parsed.subtitles.single().isDefault)
        assertEquals("audio/mp4", parsed.audioTracks.single().mimeType)
    }

    @Test
    fun `marks iframe as external and not downloadable`() {
        val candidate = VbookMediaParser.ServerCandidate(
            title = "Embed",
            data = "https://player.test/embed/123",
            headers = emptyMap(),
        )

        val parsed = VbookMediaParser.parseTrack(
            json = """
                {
                  "data": "https://player.test/embed/123",
                  "type": "iframe"
                }
            """.trimIndent(),
            candidate = candidate,
            defaultKind = MediaContentKind.VIDEO,
            idPrefix = "episode-2",
        )

        assertEquals(MediaProtocol.IFRAME, parsed.variants.single().protocol)
        assertTrue(parsed.variants.single().externalPlayerRequired)
        assertFalse(parsed.variants.single().downloadSupported)
    }

    @Test
    fun `discovers direct audiobook URL from track result`() {
        val candidate = VbookMediaParser.ServerCandidate(
            title = "64 kbps",
            data = "chapter-1",
            headers = mapOf("Referer" to "https://audio.test/"),
        )

        val parsed = VbookMediaParser.parseTrack(
            json = """
                {
                  "sources": [
                    {
                      "quality": "64 kbps",
                      "url": "https://cdn.test/book/chapter-1.m4a"
                    },
                    {
                      "quality": "128 kbps",
                      "url": "https://cdn.test/book/chapter-1.mp3"
                    }
                  ]
                }
            """.trimIndent(),
            candidate = candidate,
            defaultKind = MediaContentKind.UNKNOWN,
            idPrefix = "audio-1",
        )

        assertEquals(2, parsed.variants.size)
        assertTrue(parsed.variants.all { it.contentKind == MediaContentKind.AUDIO })
        assertTrue(parsed.variants.all { it.protocol == MediaProtocol.DIRECT })
        assertTrue(parsed.variants.all { it.downloadSupported })
        assertEquals(setOf("audio/mp4", "audio/mpeg"), parsed.variants.map { it.mimeType }.toSet())
    }

    @Test
    fun `uses direct chapter URL when chap response has no usable server`() {
        val servers = VbookMediaParser.parseServers(
            json = """[{"name":"Server","type":"section"}]""",
            fallbackUrl = "https://cdn.test/video.mp4",
        )
        val parsed = VbookMediaParser.parseTrack(
            json = "null",
            candidate = servers.single(),
            defaultKind = MediaContentKind.VIDEO,
            idPrefix = "fallback",
        )

        assertEquals("https://cdn.test/video.mp4", parsed.variants.single().uri)
        assertEquals(MediaProtocol.DIRECT, parsed.variants.single().protocol)
    }
}
