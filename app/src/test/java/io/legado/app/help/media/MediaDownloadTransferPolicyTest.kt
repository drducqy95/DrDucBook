package io.legado.app.help.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadTransferPolicyTest {

    @Test
    fun appendsOnlyWhenServerAcceptsRange() {
        val resumed = MediaDownloadTransferPolicy.directTransferPlan(1024L, 206)
        val restarted = MediaDownloadTransferPolicy.directTransferPlan(1024L, 200)

        assertTrue(resumed.append)
        assertEquals(1024L, resumed.initialBytes)
        assertFalse(restarted.append)
        assertEquals(0L, restarted.initialBytes)
    }

    @Test
    fun parsesRangeTotalFor416Validation() {
        assertEquals(4096L, MediaDownloadTransferPolicy.contentRangeTotal("bytes */4096"))
        assertEquals(null, MediaDownloadTransferPolicy.contentRangeTotal(null))
        assertEquals(null, MediaDownloadTransferPolicy.contentRangeTotal("invalid"))
    }

    @Test
    fun restartsResumeWhenRemoteIdentityChanges() {
        assertTrue(
            MediaDownloadTransferPolicy.resumeIdentityChanged(
                existingBytes = 1_024L,
                storedEtag = "v1",
                responseEtag = "v2",
                storedLastModified = null,
                responseLastModified = null,
                storedContentLength = 4_096L,
                responseContentLength = 4_096L,
            )
        )
        assertFalse(
            MediaDownloadTransferPolicy.resumeIdentityChanged(
                existingBytes = 1_024L,
                storedEtag = "v1",
                responseEtag = "v1",
                storedLastModified = "today",
                responseLastModified = "today",
                storedContentLength = 4_096L,
                responseContentLength = 4_096L,
            )
        )
    }

    @Test
    fun resolvesHlsInitAndRelativeSegments() {
        val segments = MediaDownloadTransferPolicy.parseHlsSegments(
            "https://example.test/media/playlist.m3u8",
            listOf(
                "#EXTM3U",
                "#EXT-X-MAP:URI=\"init.mp4\"",
                "segment-1.m4s",
                "../shared/segment-2.m4s",
            ),
        )

        assertEquals(
            listOf(
                "https://example.test/media/init.mp4",
                "https://example.test/media/segment-1.m4s",
                "https://example.test/shared/segment-2.m4s",
            ),
            segments,
        )
    }

    @Test
    fun selectsHighestBandwidthHlsVariantAndResolvesUrl() {
        val variant = MediaDownloadTransferPolicy.selectBestHlsVariant(
            "https://cdn.example.test/master/index.m3u8",
            listOf(
                "#EXTM3U",
                "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480",
                "480p/main.m3u8",
                "#EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1920x1080",
                "https://video.example.test/1080p/main.m3u8",
                "#EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720",
                "../720p/main.m3u8",
            ),
        )

        assertEquals("https://video.example.test/1080p/main.m3u8", variant?.url)
        assertEquals(2_400_000L, variant?.bandwidth)
        assertEquals(1920, variant?.width)
        assertEquals(1080, variant?.height)
    }

    @Test
    fun parsesHlsAesKeyExplicitIvAndSequenceIv() {
        val explicit = MediaDownloadTransferPolicy.parseHlsDownloadSegments(
            "https://example.test/hls/playlist.m3u8",
            listOf(
                "#EXTM3U",
                "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0x0000000000000000000000000000002a",
                "seg-1.ts",
            ),
        ).single()

        assertEquals("https://example.test/hls/key.bin", explicit.keyUrl)
        assertArrayEquals(
            ByteArray(16).also { it[15] = 42 },
            explicit.iv,
        )

        val sequenceDerived = MediaDownloadTransferPolicy.parseHlsDownloadSegments(
            "https://example.test/hls/playlist.m3u8",
            listOf(
                "#EXTM3U",
                "#EXT-X-MEDIA-SEQUENCE:7",
                "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"",
                "seg-7.ts",
                "#EXT-X-KEY:METHOD=NONE",
                "clear.ts",
            ),
        )

        assertArrayEquals(ByteArray(16).also { it[15] = 7 }, sequenceDerived[0].iv)
        assertNull(sequenceDerived[1].keyUrl)
        assertNull(sequenceDerived[1].iv)
    }

    @Test
    fun parsesHlsInitMapAndMediaByteRanges() {
        val segments = MediaDownloadTransferPolicy.parseHlsDownloadSegments(
            "https://example.test/media/playlist.m3u8",
            listOf(
                "#EXTM3U",
                "#EXT-X-MAP:URI=\"movie.mp4\",BYTERANGE=\"720@0\"",
                "#EXT-X-BYTERANGE:100@720",
                "movie.mp4",
                "#EXT-X-BYTERANGE:200",
                "movie.mp4",
            ),
        )

        assertEquals(3, segments.size)
        assertTrue(segments[0].initMap)
        assertEquals(MediaDownloadTransferPolicy.HlsByteRange(0, 720), segments[0].byteRange)
        assertEquals("bytes=0-719", segments[0].byteRange?.toHttpRangeHeader())
        assertEquals(MediaDownloadTransferPolicy.HlsByteRange(720, 100), segments[1].byteRange)
        assertEquals("bytes=720-819", segments[1].byteRange?.toHttpRangeHeader())
        assertEquals(MediaDownloadTransferPolicy.HlsByteRange(820, 200), segments[2].byteRange)
        assertEquals("bytes=820-1019", segments[2].byteRange?.toHttpRangeHeader())
    }

    @Test
    fun marksHlsDiscontinuityWithoutChangingMediaSequenceIv() {
        val segments = MediaDownloadTransferPolicy.parseHlsDownloadSegments(
            "https://example.test/hls/playlist.m3u8",
            listOf(
                "#EXTM3U",
                "#EXT-X-MEDIA-SEQUENCE:10",
                "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"",
                "seg-10.ts",
                "#EXT-X-DISCONTINUITY",
                "seg-11.ts",
            ),
        )

        assertFalse(segments[0].discontinuityBefore)
        assertTrue(segments[1].discontinuityBefore)
        assertArrayEquals(ByteArray(16).also { it[15] = 10 }, segments[0].iv)
        assertArrayEquals(ByteArray(16).also { it[15] = 11 }, segments[1].iv)
    }

    @Test
    fun parsesDashSegmentListAndChoosesBestRepresentation() {
        val plan = MediaDownloadTransferPolicy.parseDashDownloadPlan(
            "https://example.test/path/manifest.mpd",
            """
                <MPD mediaPresentationDuration="PT6S">
                  <BaseURL>https://cdn.example.test/video/</BaseURL>
                  <Period>
                    <AdaptationSet mimeType="video/mp4">
                      <Representation id="low" bandwidth="400000" width="640" height="360">
                        <BaseURL>low/</BaseURL>
                        <SegmentList>
                          <Initialization sourceURL="init.mp4" range="0-719" />
                          <SegmentURL media="seg-1.m4s" mediaRange="720-1719" />
                        </SegmentList>
                      </Representation>
                      <Representation id="high" bandwidth="2400000" width="1920" height="1080">
                        <BaseURL>high/</BaseURL>
                        <SegmentList>
                          <Initialization sourceURL="init.mp4" range="0-719" />
                          <SegmentURL media="seg-1.m4s" mediaRange="720-1719" />
                          <SegmentURL media="seg-2.m4s" mediaRange="1720-2719" />
                        </SegmentList>
                      </Representation>
                    </AdaptationSet>
                  </Period>
                </MPD>
            """.trimIndent(),
        )

        assertEquals("mp4", plan.extension)
        assertEquals(3, plan.segments.size)
        assertTrue(plan.segments[0].initSegment)
        assertEquals("https://cdn.example.test/video/high/init.mp4", plan.segments[0].url)
        assertEquals(MediaDownloadTransferPolicy.HlsByteRange(0, 720), plan.segments[0].byteRange)
        assertEquals("bytes=720-1719", plan.segments[1].byteRange?.toHttpRangeHeader())
        assertEquals("https://cdn.example.test/video/high/seg-2.m4s", plan.segments[2].url)
    }

    @Test
    fun parsesDashSegmentTemplateDurationAndTimeline() {
        val durationPlan = MediaDownloadTransferPolicy.parseDashDownloadPlan(
            "https://example.test/dash/manifest.mpd",
            """
                <MPD mediaPresentationDuration="PT6S">
                  <Period>
                    <AdaptationSet mimeType="video/mp4">
                      <SegmentTemplate timescale="1" duration="2" startNumber="3"
                        initialization="init-${'$'}RepresentationID${'$'}.mp4"
                        media="chunk-${'$'}Number%05d${'$'}.m4s" />
                      <Representation id="v1" bandwidth="900000" />
                    </AdaptationSet>
                  </Period>
                </MPD>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "https://example.test/dash/init-v1.mp4",
                "https://example.test/dash/chunk-00003.m4s",
                "https://example.test/dash/chunk-00004.m4s",
                "https://example.test/dash/chunk-00005.m4s",
            ),
            durationPlan.segments.map { it.url },
        )

        val timelinePlan = MediaDownloadTransferPolicy.parseDashDownloadPlan(
            "https://example.test/dash/manifest.mpd",
            """
                <MPD>
                  <Period>
                    <AdaptationSet mimeType="video/mp4">
                      <SegmentTemplate media="time-${'$'}Time${'$'}.m4s">
                        <SegmentTimeline>
                          <S t="100" d="2" r="1" />
                          <S d="2" />
                        </SegmentTimeline>
                      </SegmentTemplate>
                      <Representation id="v1" bandwidth="900000" />
                    </AdaptationSet>
                  </Period>
                </MPD>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "https://example.test/dash/time-100.m4s",
                "https://example.test/dash/time-102.m4s",
                "https://example.test/dash/time-104.m4s",
            ),
            timelinePlan.segments.map { it.url },
        )
    }
}
