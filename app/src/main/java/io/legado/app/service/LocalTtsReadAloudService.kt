package io.legado.app.service

import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.tts.LocalTtsModelRegistry
import io.legado.app.model.tts.LocalTtsSynthesisEngine
import io.legado.app.model.tts.PiperOnnxTtsEngine
import io.legado.app.model.tts.ValtecOnnxTtsEngine
import io.legado.app.model.tts.parseLocalTtsEngine
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.absoluteValue

/** On-device, file-backed TTS playback for imported ONNX voice models. */
class LocalTtsReadAloudService : BaseReadAloudService(), Player.Listener {
    private val player by lazy { ExoPlayer.Builder(this).build() }
    private var generationJob: Job? = null
    private var engine: LocalTtsSynthesisEngine? = null
    private var loadedModelId: String? = null
    private var generatedFile: File? = null

    override fun onCreate() {
        super.onCreate()
        player.addListener(this)
        applySpeechRate()
    }

    override fun onDestroy() {
        generationJob?.cancel()
        player.release()
        engine?.close()
        engine = null
        super.onDestroy()
    }

    override fun play() {
        pageChanged = false
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("Danh sách đọc bằng model local đang trống")
            ReadBook.readAloud()
            return
        }
        super.play()
        synthesizeCurrentParagraph()
    }

    private fun synthesizeCurrentParagraph() {
        generationJob?.cancel()
        player.stop()
        val reference = parseLocalTtsEngine(ReadAloud.ttsEngine)
        if (reference == null) {
            failPlayback("Cấu hình model TTS local không hợp lệ")
            return
        }
        val model = LocalTtsModelRegistry(this).get(reference.modelId)
        if (model == null) {
            failPlayback("Không tìm thấy model TTS đã chọn. Hãy nhập lại model.")
            return
        }
        val voiceId = reference.voiceId.takeIf { id -> model.voices.any { it.id == id } }
            ?: model.defaultVoiceId
        var text = contentList.getOrNull(nowSpeak).orEmpty()
        if (paragraphStartPos in 1 until text.length) text = text.substring(paragraphStartPos)
        text = text.replace(AppPattern.notReadAloudRegex, "").trim()
        if (text.isEmpty()) {
            lifecycleScope.launch { advanceAndPlay() }
            return
        }
        generationJob = lifecycleScope.launch {
            try {
                val wav = withContext(Dispatchers.Default) {
                    val runtime = obtainEngine(model.id) {
                        when (model.engine) {
                            LocalTtsModelRegistry.ENGINE_VALTEC_VITS -> ValtecOnnxTtsEngine(model)
                            LocalTtsModelRegistry.ENGINE_PIPER_VITS -> PiperOnnxTtsEngine(this@LocalTtsReadAloudService, model)
                            else -> error("Engine model TTS chưa được hỗ trợ: ${model.engine}")
                        }
                    }
                    val target = cacheFile(model.id, voiceId, text)
                    if (!target.isFile || target.length() <= WAV_HEADER_SIZE.toLong()) {
                        val samples = runtime.synthesize(text, voiceId)
                        writeWaveAtomically(target, samples, model.sampleRate)
                    }
                    target
                }
                generatedFile = wav
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(wav)))
                applySpeechRate()
                player.prepare()
                upTtsProgress(readAloudNumber + 1)
                upMediaMetadata(showContent = true)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) return@launch
                AppLog.put("Lỗi tổng hợp giọng đọc local\n${error.localizedMessage}", error, true)
                failPlayback(error.localizedMessage ?: "Không thể tổng hợp giọng đọc local")
            }
        }
    }

    @Synchronized
    private fun obtainEngine(modelId: String, create: () -> LocalTtsSynthesisEngine): LocalTtsSynthesisEngine {
        if (loadedModelId != modelId) {
            engine?.close()
            engine = null
            loadedModelId = modelId
        }
        return engine ?: create().also { engine = it }
    }

    private suspend fun advanceAndPlay() {
        readAloudNumber += contentList[nowSpeak].length + 1 - paragraphStartPos
        paragraphStartPos = 0
        if (nowSpeak < contentList.lastIndex) {
            nowSpeak++
            upTtsProgress(readAloudNumber + 1)
            upMediaMetadata(showContent = true)
            val interval = ReadConfig.ttsParagraphInterval.toLong().coerceAtLeast(0)
            if (interval > 0) delay(interval)
            if (!pause) synthesizeCurrentParagraph()
        } else {
            nextChapter()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> if (!pause) player.play()
            Player.STATE_ENDED -> lifecycleScope.launch { advanceAndPlay() }
        }
    }

    override fun playStop() {
        generationJob?.cancel()
        player.stop()
    }

    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        player.pause()
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        if (pageChanged || player.playbackState == Player.STATE_IDLE) {
            play()
        } else {
            player.play()
        }
    }

    override fun upSpeechRate(reset: Boolean) {
        applySpeechRate()
        if (reset && !pause && player.playbackState == Player.STATE_IDLE) play()
    }

    private fun applySpeechRate() {
        val speed = if (ReadConfig.ttsFollowSys) 1f else (ReadConfig.ttsSpeechRate + 5) / 10f
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))
    }

    private fun failPlayback(message: String) {
        toastOnUi(message)
        if (!pause) pauseReadAloud()
    }

    private fun cacheFile(modelId: String, voiceId: Int, text: String): File {
        val root = File(externalCacheDir ?: cacheDir, "local_tts_audio").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$modelId\u0000$voiceId\u0000$text".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(root, "$digest.wav")
    }

    private fun writeWaveAtomically(target: File, samples: FloatArray, sampleRate: Int) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        val peak = samples.maxOfOrNull { it.absoluteValue }?.coerceAtLeast(1f) ?: 1f
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { sample ->
            val normalized = (sample / peak).coerceIn(-1f, 1f)
            pcm.putShort((normalized * Short.MAX_VALUE).toInt().toShort())
        }
        val data = pcm.array()
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + data.size)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray())
            putInt(data.size)
        }.array()
        temporary.outputStream().buffered().use { output ->
            output.write(header)
            output.write(data)
        }
        if (target.exists() && !target.delete()) {
            temporary.delete()
            error("Không thể thay cache âm thanh TTS")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? =
        servicePendingIntent<LocalTtsReadAloudService>(actionStr)

    companion object {
        private const val WAV_HEADER_SIZE = 44
    }
}
