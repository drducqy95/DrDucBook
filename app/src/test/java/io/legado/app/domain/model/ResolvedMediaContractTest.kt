package io.legado.app.domain.model

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedMediaContractTest {

    @Test
    fun roundTripsVersionedMediaContractWithVariantsAndTracks() {
        val media = fixtureMedia()

        val contract = media.toResolvedMediaContract()
        val json = GSON.toJson(contract)
        val restored = GSON.fromJson(json, ResolvedMediaContract::class.java).toResolvedMedia()

        assertEquals(RESOLVED_MEDIA_CONTRACT_VERSION, contract.schemaVersion)
        assertEquals(media, restored)
        assertTrue(json.contains("schemaVersion"))
        assertEquals("HLS", contract.variants[0].protocol)
        assertEquals("DASH", contract.variants[1].protocol)
        assertTrue(contract.variants[1].drmUnsupported)
        assertEquals("episode-1.m3u8", contract.variants[0].downloadFileName)
    }

    @Test
    fun redactedContractDoesNotLeakCredentialHeadersOrUriQuerySecrets() {
        val json = GSON.toJson(fixtureMedia().toResolvedMediaContract(redactSecrets = true))

        assertFalse(json, json.contains("Bearer media-secret"))
        assertFalse(json, json.contains("session=raw-cookie"))
        assertFalse(json, json.contains("access_token=raw-token"))
        assertFalse(json, json.contains("signature=raw-signature"))
        assertTrue(json.contains("[REDACTED]"))
        assertTrue(json.contains("https://video.test/watch/1"))
    }

    @Test
    fun persistentHeadersDropSecretsButKeepPlaybackContext() {
        val persistent = mapOf(
            "Cookie" to "session=raw-cookie",
            "Authorization" to "Bearer media-secret",
            "X-Api-Key" to "raw-key",
            "CookieJar" to "1",
            "Referer" to "https://video.test/",
            "User-Agent" to "DrDucBook",
        ).toPersistentMediaHeaders()

        assertFalse("Cookie" in persistent)
        assertFalse("Authorization" in persistent)
        assertFalse("X-Api-Key" in persistent)
        assertEquals("1", persistent["CookieJar"])
        assertEquals("https://video.test/", persistent["Referer"])
        assertEquals("DrDucBook", persistent["User-Agent"])
    }

    @Test
    fun rejectsUnsupportedContractVersion() {
        val contract = fixtureMedia().toResolvedMediaContract().copy(schemaVersion = 999)

        try {
            contract.toResolvedMedia()
            throw AssertionError("Expected unsupported contract version to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("Unsupported ResolvedMedia contract version"))
        }
    }

    private fun fixtureMedia(): ResolvedMedia = ResolvedMedia(
        sourceId = "source-video",
        contentId = "episode-1",
        title = "Episode 1",
        variants = listOf(
            ResolvedMediaVariant(
                id = "hls-1080",
                title = "1080p",
                uri = "https://cdn.test/master.m3u8?access_token=raw-token&signature=raw-signature",
                contentKind = MediaContentKind.VIDEO,
                protocol = MediaProtocol.HLS,
                mimeType = "application/x-mpegURL",
                headers = mapOf(
                    "Authorization" to "Bearer media-secret",
                    "Cookie" to "session=raw-cookie",
                    "Referer" to "https://video.test/watch/1",
                    "User-Agent" to "DrDucBook",
                ),
                referer = "https://video.test/watch/1",
                expiresAt = 2_000_000_000_000L,
                downloadSupported = true,
                externalPlayerRequired = false,
                durationMs = 90_000L,
                drmUnsupported = false,
                downloadFileName = "episode-1.m3u8",
            ),
            ResolvedMediaVariant(
                id = "dash-720",
                title = "720p DASH",
                uri = "https://cdn.test/manifest.mpd",
                contentKind = MediaContentKind.VIDEO,
                protocol = MediaProtocol.DASH,
                mimeType = "application/dash+xml",
                headers = emptyMap(),
                referer = "",
                expiresAt = null,
                downloadSupported = true,
                externalPlayerRequired = false,
                drmUnsupported = true,
            ),
            ResolvedMediaVariant(
                id = "local-file",
                title = "Offline",
                uri = "file:///storage/emulated/0/Movies/episode.mp4",
                contentKind = MediaContentKind.VIDEO,
                protocol = MediaProtocol.DIRECT,
                mimeType = "video/mp4",
                headers = emptyMap(),
                referer = "",
                expiresAt = null,
                downloadSupported = false,
                externalPlayerRequired = false,
            ),
        ),
        subtitles = listOf(
            ResolvedSubtitleTrack(
                id = "sub-vi",
                label = "VI",
                language = "vi",
                uri = "https://cdn.test/sub.vtt?token=subtitle-secret",
                mimeType = "text/vtt",
                headers = mapOf("Cookie" to "subtitle-cookie"),
                isDefault = true,
            )
        ),
        audioTracks = listOf(
            ResolvedAudioTrack(
                id = "audio-vi",
                label = "Thuyet minh",
                language = "vi",
                uri = "https://cdn.test/audio.m4a",
                mimeType = "audio/mp4",
                headers = mapOf("Referer" to "https://video.test/watch/1"),
                isDefault = false,
            )
        ),
        resolvedAt = 123L,
    )
}
