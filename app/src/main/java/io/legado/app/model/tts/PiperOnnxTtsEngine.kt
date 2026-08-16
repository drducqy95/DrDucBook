package io.legado.app.model.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class PiperOnnxTtsEngine(
    context: Context,
    private val model: LocalTtsModel,
) : LocalTtsSynthesisEngine {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default)
    private var runtime: OfflineTts? = null
    private var idleUnloadJob: Job? = null

    override suspend fun synthesize(text: String, voiceId: Int): FloatArray =
        withContext(Dispatchers.Default) {
            require(text.isNotBlank()) { "Văn bản TTS đang trống" }
            mutex.withLock {
                idleUnloadJob?.cancel()
                val tts = runtime ?: load().also { runtime = it }
                require(voiceId in 0 until tts.numSpeakers()) {
                    "Voice Piper không hợp lệ: $voiceId"
                }
                val audio = tts.generate(text.trim(), sid = voiceId, speed = 1f)
                check(audio.sampleRate == model.sampleRate) {
                    "Sample rate Piper không khớp: ${audio.sampleRate}"
                }
                scheduleIdleUnload()
                audio.samples
            }
        }

    private fun load(): OfflineTts {
        check(model.engine == LocalTtsModelRegistry.ENGINE_PIPER_VITS) {
            "Engine model TTS chưa được hỗ trợ: ${model.engine}"
        }
        val directory = File(model.directoryPath)
        val config = JSONObject(File(directory, LocalTtsModelRegistry.PIPER_CONFIG_FILE).readText())
        val inference = config.optJSONObject("inference") ?: JSONObject()
        val dataDirectory = PiperRuntimeAssets(appContext).ensureInstalled()
        val ttsConfig = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(directory, LocalTtsModelRegistry.PIPER_MODEL_FILE).absolutePath,
                    tokens = File(directory, LocalTtsModelRegistry.PIPER_TOKENS_FILE).absolutePath,
                    dataDir = dataDirectory.absolutePath,
                    noiseScale = inference.optDouble("noise_scale", 0.667).toFloat(),
                    noiseScaleW = inference.optDouble("noise_w", 0.8).toFloat(),
                    lengthScale = inference.optDouble("length_scale", 1.0).toFloat(),
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return OfflineTts(config = ttsConfig).also { tts ->
            check(tts.sampleRate() == model.sampleRate) {
                "Runtime Piper trả sample rate ${tts.sampleRate()}, cần ${model.sampleRate}"
            }
            check(tts.numSpeakers() == model.voices.size) {
                "Số voice Piper không khớp manifest"
            }
        }
    }

    private fun scheduleIdleUnload() {
        idleUnloadJob?.cancel()
        idleUnloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock { closeRuntime() }
        }
    }

    private fun closeRuntime() {
        runtime?.release()
        runtime = null
    }

    override fun close() {
        idleUnloadJob?.cancel()
        idleUnloadJob = null
        closeRuntime()
    }

    companion object {
        private const val IDLE_UNLOAD_MS = 3 * 60 * 1000L
    }
}
