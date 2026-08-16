package io.legado.app.integration

import android.media.MediaExtractor
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MediaDevicePlaybackSmokeTest {

    @Test
    fun localAudioCanBePreparedPlayedPausedAndReleasedByMedia3() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val mediaFile = writeSineWave(File(context.cacheDir, "media-device-smoke.wav"))
        val ready = CountDownLatch(1)
        lateinit var player: ExoPlayer

        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) ready.countDown()
                    }
                })
                setMediaItem(MediaItem.fromUri(Uri.fromFile(mediaFile)))
                prepare()
            }
        }

        assertTrue("Media3 player did not become ready", ready.await(10, TimeUnit.SECONDS))

        instrumentation.runOnMainSync {
            player.play()
            player.pause()
            player.release()
        }
    }

    @Test
    fun generatedOfflineAudioOutputIsProbeableOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mediaFile = writeSineWave(File(context.cacheDir, "media-offline-output-smoke.wav"))
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(mediaFile.absolutePath)
            assertTrue("Offline media output has no tracks", extractor.trackCount > 0)
        } finally {
            extractor.release()
        }
    }

    private fun writeSineWave(file: File): File {
        val sampleRate = 44_100
        val durationSeconds = 1
        val samples = sampleRate * durationSeconds
        val dataSize = samples * Short.SIZE_BYTES
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * Short.SIZE_BYTES)
        buffer.putShort(Short.SIZE_BYTES.toShort())
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        repeat(samples) { index ->
            val sample = (sin(2.0 * PI * 440.0 * index / sampleRate) * Short.MAX_VALUE * 0.2).toInt()
            buffer.putShort(sample.toShort())
        }
        file.parentFile?.mkdirs()
        file.writeBytes(buffer.array())
        return file
    }
}
