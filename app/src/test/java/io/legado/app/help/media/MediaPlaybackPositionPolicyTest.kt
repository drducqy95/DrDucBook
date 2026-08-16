package io.legado.app.help.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPlaybackPositionPolicyTest {

    @Test
    fun persistedAbsolutePositionInsideClipWinsOverRequestedRelativeStart() {
        val start = MediaPlaybackPositionPolicy.prepareStartPosition(
            persistedAbsoluteMs = 12_000L,
            clipStartMs = 10_000L,
            clipEndMs = 20_000L,
            requestedRelativeMs = 5_000L,
        )

        assertEquals(12_000L, start)
    }

    @Test
    fun stalePersistedPositionFallsBackToClipStartPlusRequestedRelativeStart() {
        val start = MediaPlaybackPositionPolicy.prepareStartPosition(
            persistedAbsoluteMs = 25_000L,
            clipStartMs = 10_000L,
            clipEndMs = 20_000L,
            requestedRelativeMs = 3_000L,
        )

        assertEquals(13_000L, start)
    }

    @Test
    fun seekAndSnapshotPositionsStayRelativeToClip() {
        assertEquals(
            18_000L,
            MediaPlaybackPositionPolicy.seekAbsolute(
                relativeMs = 8_000L,
                clipStartMs = 10_000L,
                clipEndMs = 20_000L,
            )
        )
        assertEquals(
            10_000L,
            MediaPlaybackPositionPolicy.relativePosition(
                absoluteMs = 20_000L,
                clipStartMs = 10_000L,
            )
        )
        assertEquals(
            10_000L,
            MediaPlaybackPositionPolicy.relativeDuration(
                rawDurationMs = 60_000L,
                clipStartMs = 10_000L,
                clipEndMs = 20_000L,
            )
        )
    }

    @Test
    fun seekIsBoundedByClipEnd() {
        val absolute = MediaPlaybackPositionPolicy.seekAbsolute(
            relativeMs = 30_000L,
            clipStartMs = 10_000L,
            clipEndMs = 20_000L,
        )

        assertEquals(20_000L, absolute)
    }
}
