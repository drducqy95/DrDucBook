package io.legado.app.help.media

import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.MediaProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MediaSourceRuleResultParserTest {

    @Test
    fun parsesLabeledHlsLineWithoutTreatingItAsText() {
        val media = MediaSourceRuleResultParser.parse(
            sourceId = "source-video",
            contentId = "episode-1",
            title = "Episode 1",
            raw = "HD 1920x800 + https://cdn.test/video/master.m3u8?expires=2000000000",
            defaultKind = MediaContentKind.VIDEO,
            fallbackHeaders = mapOf(
                "User-Agent" to "DrDucBook Test",
                "Referer" to "https://video.test/watch/1",
            ),
            resolvedAt = 42L,
        )

        val variant = media.variants.single()
        assertEquals("HD 1920x800", variant.title)
        assertEquals("https://cdn.test/video/master.m3u8?expires=2000000000", variant.uri)
        assertEquals(MediaProtocol.HLS, variant.protocol)
        assertEquals(MediaContentKind.VIDEO, variant.contentKind)
        assertEquals("https://video.test/watch/1", variant.referer)
        assertEquals(2_000_000_000_000L, variant.expiresAt)
        assertTrue(variant.downloadSupported)
        assertEquals(42L, media.resolvedAt)
    }

    @Test
    fun parsesJsonTrackResultWithHeadersAndSubtitles() {
        val media = MediaSourceRuleResultParser.parse(
            sourceId = "source-video",
            contentId = "episode-json",
            title = "Episode JSON",
            raw = """
                {
                  "title": "1080p",
                  "data": "https://cdn.test/video/master.m3u8",
                  "headers": {
                    "Referer": "https://video.test/"
                  },
                  "subtitles": [
                    {"label": "VI", "lang": "vi", "url": "https://cdn.test/subs/vi.vtt"}
                  ]
                }
            """.trimIndent(),
            defaultKind = MediaContentKind.VIDEO,
            fallbackHeaders = mapOf("User-Agent" to "DrDucBook Test"),
        )

        val variant = media.variants.single()
        assertEquals("1080p", variant.title)
        assertEquals(MediaProtocol.HLS, variant.protocol)
        assertEquals("https://video.test/", variant.referer)
        assertEquals("DrDucBook Test", variant.headers["User-Agent"])
        assertEquals("VI", media.subtitles.single().label)
    }

    @Test
    fun rejectsNormalChapterTextWithoutMediaUrl() {
        try {
            MediaSourceRuleResultParser.parse(
                sourceId = "text-source",
                contentId = "chapter-1",
                title = "Chapter 1",
                raw = "This is a normal chapter paragraph with no playable media.",
                defaultKind = MediaContentKind.VIDEO,
            )
            fail("Expected normal text to be rejected as non-media")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun normalizesRelativeSingleMediaUrlAgainstChapterBase() {
        val media = MediaSourceRuleResultParser.parse(
            sourceId = "source-video",
            contentId = "episode-relative",
            title = "Episode Relative",
            raw = "../hls/master.m3u8",
            defaultKind = MediaContentKind.VIDEO,
            baseUrl = "https://video.test/series/episode/1",
        )

        val variant = media.variants.single()
        assertEquals("https://video.test/series/hls/master.m3u8", variant.uri)
        assertEquals(MediaProtocol.HLS, variant.protocol)
    }
}
