package io.legado.app.model.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.ceil
import kotlin.math.exp

/**
 * File-backed adaptation of Valtec's Android reference runtime.
 * Original runtime/model: Valtec Team, CC BY-NC 4.0. No Valtec model is bundled in the APK.
 */
class ValtecOnnxTtsEngine(private val model: LocalTtsModel) : LocalTtsSynthesisEngine {
    private val mutex = Mutex()
    private val g2p = VietnameseG2p()
    private var sessions: Sessions? = null
    private var idleUnloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private fun scheduleIdleUnload() {
        idleUnloadJob?.cancel()
        idleUnloadJob = scope.launch {
            delay(3 * 60 * 1000L) // 3 minutes
            mutex.withLock {
                closeSessions()
            }
        }
    }

    override suspend fun synthesize(text: String, voiceId: Int): FloatArray =
        synthesize(text, voiceId, noiseScale = 0.667f, lengthScale = 1f)

    private suspend fun synthesize(
        text: String,
        voiceId: Int,
        noiseScale: Float,
        lengthScale: Float,
    ): FloatArray = withContext(Dispatchers.Default) {
        mutex.withLock {
            require(text.length <= MAX_SYNTHESIS_CHARS) {
                "Văn bản TTS quá dài; hãy chia nhỏ trước khi tổng hợp"
            }
            idleUnloadJob?.cancel()
            val runtime = sessions ?: load().also { sessions = it }
            val result = runtime.synthesize(text, voiceId, noiseScale, lengthScale)
            scheduleIdleUnload()
            result
        }
    }

    private fun load(): Sessions {
        check(model.engine == LocalTtsModelRegistry.ENGINE_VALTEC_VITS) {
            "Engine model TTS chưa được hỗ trợ: ${model.engine}"
        }
        val directory = File(model.directoryPath)
        val config = JSONObject(File(directory, "tts_config.json").readText())
        val symbolsJson = config.getJSONObject("symbol_to_id")
        val symbols = symbolsJson.keys().asSequence().associateWith(symbolsJson::getInt)
        val languageId = config.optJSONObject("language_id_map")?.optInt("VI", 7) ?: 7
        g2p.initialize(symbols, languageId)
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        val opened = ArrayList<OrtSession>(4)
        return try {
            val textEncoder = environment.createSession(
                File(directory, "text_encoder.onnx").absolutePath,
                options,
            ).also(opened::add)
            val durationPredictor = environment.createSession(
                File(directory, "duration_predictor.onnx").absolutePath,
                options,
            ).also(opened::add)
            val flow = environment.createSession(
                File(directory, "flow.onnx").absolutePath,
                options,
            ).also(opened::add)
            val decoder = environment.createSession(
                File(directory, "decoder.onnx").absolutePath,
                options,
            ).also(opened::add)
            Sessions(
                environment = environment,
                textEncoder = textEncoder,
                durationPredictor = durationPredictor,
                flow = flow,
                decoder = decoder,
                g2p = g2p,
            )
        } catch (error: Throwable) {
            opened.asReversed().forEach { session -> runCatching { session.close() } }
            throw error
        } finally {
            options.close()
        }
    }

    private fun closeSessions() {
        sessions?.close()
        sessions = null
    }

    override fun close() {
        idleUnloadJob?.cancel()
        idleUnloadJob = null
        closeSessions()
    }

    private class Sessions(
        private val environment: OrtEnvironment,
        private val textEncoder: OrtSession,
        private val durationPredictor: OrtSession,
        private val flow: OrtSession,
        private val decoder: OrtSession,
        private val g2p: VietnameseG2p,
    ) : AutoCloseable {

        fun synthesize(text: String, voiceId: Int, noiseScale: Float, lengthScale: Float): FloatArray {
            val (phones, tones, languages) = g2p.encode(text)
            val sequenceLength = phones.size
            require(sequenceLength > 2) { "Văn bản không tạo được chuỗi âm vị" }
            val phoneIds = longTensor(phones, longArrayOf(1, sequenceLength.toLong()))
            val phoneLengths = longTensor(listOf(sequenceLength), longArrayOf(1))
            val toneIds = longTensor(tones, longArrayOf(1, sequenceLength.toLong()))
            val languageIds = longTensor(languages, longArrayOf(1, sequenceLength.toLong()))
            val bert = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(FloatArray(1024 * sequenceLength)),
                longArrayOf(1, 1024, sequenceLength.toLong()),
            )
            val jaBert = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(FloatArray(768 * sequenceLength)),
                longArrayOf(1, 768, sequenceLength.toLong()),
            )
            val speaker = longTensor(listOf(voiceId), longArrayOf(1))
            try {
                textEncoder.run(
                    mapOf(
                        "phone_ids" to phoneIds,
                        "phone_lengths" to phoneLengths,
                        "tone_ids" to toneIds,
                        "language_ids" to languageIds,
                        "bert" to bert,
                        "ja_bert" to jaBert,
                        "speaker_id" to speaker,
                    )
                ).use { encoded ->
                    val mean = encoded[1] as OnnxTensor
                    val logScale = encoded[2] as OnnxTensor
                    val xMask = encoded[3] as OnnxTensor
                    val channels = mean.info.shape[1].toInt()
                    durationPredictor.run(
                        tensorInputs(
                            "x" to encoded[0],
                            "x_mask" to encoded[3],
                            "g" to encoded[4],
                        )
                    ).use { durationResult ->
                        val logDuration = (durationResult[0] as OnnxTensor).floatBuffer
                        val mask = xMask.floatBuffer
                        var totalFrames = 0
                        val durations = IntArray(sequenceLength) { index ->
                            ceil(
                                exp(logDuration.get(index).toDouble()) *
                                    mask.get(index) * lengthScale.coerceIn(0.4f, 2.5f)
                            ).toInt().coerceAtLeast(0).also { totalFrames += it }
                        }
                        totalFrames = totalFrames.coerceAtLeast(1)
                        val expandedMean = FloatArray(channels * totalFrames)
                        val expandedLogScale = FloatArray(channels * totalFrames)
                        val meanData = mean.floatBuffer
                        val scaleData = logScale.floatBuffer
                        var frame = 0
                        durations.forEachIndexed { token, duration ->
                            repeat(duration) {
                                if (frame < totalFrames) {
                                    repeat(channels) { channel ->
                                        expandedMean[channel * totalFrames + frame] =
                                            meanData.get(channel * sequenceLength + token)
                                        expandedLogScale[channel * totalFrames + frame] =
                                            scaleData.get(channel * sequenceLength + token)
                                    }
                                    frame++
                                }
                            }
                        }
                        val random = java.util.Random()
                        val latent = FloatArray(channels * totalFrames) { index ->
                            expandedMean[index] +
                                exp(expandedLogScale[index].toDouble()).toFloat() *
                                random.nextGaussian().toFloat() * noiseScale.coerceIn(0f, 1.5f)
                        }
                        OnnxTensor.createTensor(
                            environment,
                            FloatBuffer.wrap(latent),
                            longArrayOf(1, channels.toLong(), totalFrames.toLong()),
                        ).use { latentTensor ->
                            OnnxTensor.createTensor(
                                environment,
                                FloatBuffer.wrap(FloatArray(totalFrames) { 1f }),
                                longArrayOf(1, 1, totalFrames.toLong()),
                            ).use { yMask ->
                                flow.run(
                                    tensorInputs(
                                        "z_p" to latentTensor,
                                        "y_mask" to yMask,
                                        "g" to encoded[4],
                                    )
                                ).use { flowed ->
                                    decoder.run(
                                        tensorInputs("z" to flowed[0], "g" to encoded[4])
                                    ).use { decoded ->
                                        val buffer = (decoded[0] as OnnxTensor).floatBuffer
                                        return FloatArray(buffer.remaining()).also(buffer::get)
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                phoneIds.close()
                phoneLengths.close()
                toneIds.close()
                languageIds.close()
                bert.close()
                jaBert.close()
                speaker.close()
            }
        }

        private fun longTensor(values: List<Int>, shape: LongArray): OnnxTensor =
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(values.map(Int::toLong).toLongArray()),
                shape,
            )

        private fun tensorInputs(vararg pairs: Pair<String, OnnxValue>): Map<String, OnnxTensorLike> =
            pairs.associate { (name, value) ->
                name to (value as? OnnxTensorLike
                    ?: error("ONNX value $name không phải tensor"))
            }

        override fun close() {
            runCatching { textEncoder.close() }
            runCatching { durationPredictor.close() }
            runCatching { flow.close() }
            runCatching { decoder.close() }
        }
    }

    private companion object {
        const val MAX_SYNTHESIS_CHARS = 420
    }
}
