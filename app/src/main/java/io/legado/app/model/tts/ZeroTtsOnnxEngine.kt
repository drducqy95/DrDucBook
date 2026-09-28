package io.legado.app.model.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.Random

/**
 * On-device ONNX inference engine for ZeroTTS (Vietnamese zero-shot neural TTS).
 * Executes text_encoder, prefix_step autoregressive loop, local_frame_decode,
 * and MOSS audio codec decoder graphs.
 */
class ZeroTtsOnnxEngine(private val model: LocalTtsModel) : LocalTtsSynthesisEngine {
    private val mutex = Mutex()
    private var sessions: Sessions? = null
    private var idleUnloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private fun scheduleIdleUnload() {
        idleUnloadJob?.cancel()
        idleUnloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock {
                closeSessions()
            }
        }
    }

    override suspend fun synthesize(text: String, voiceId: Int, speed: Float): FloatArray =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                require(text.isNotBlank()) { "Văn bản TTS đang trống" }
                idleUnloadJob?.cancel()
                val runtime = sessions ?: load().also { sessions = it }
                val result = runtime.synthesize(text, voiceId) {
                    coroutineContext.ensureActive()
                }
                scheduleIdleUnload()
                result
            }
        }

    private fun load(): Sessions {
        check(model.engine == LocalTtsModelRegistry.ENGINE_ZEROTTS) {
            "Engine model TTS không khớp: ${model.engine}"
        }
        val directory = File(model.directoryPath)
        val configFile = File(directory, "config.json")
        if (!configFile.isFile) throw IOException("Thiếu tệp config.json trong thư mục model")
        val config = JSONObject(configFile.readText())

        val tokenizerFile = File(directory, "tokenizer.json")
        if (!tokenizerFile.isFile) throw IOException("Thiếu tệp tokenizer.json trong thư mục model")
        val tokenizer = ZeroTtsBpeTokenizer.fromFile(tokenizerFile)

        val nullVoiceFile = File(directory, "null_voice_emb.npy")
        if (!nullVoiceFile.isFile) throw IOException("Thiếu tệp null_voice_emb.npy trong thư mục model")
        val nullVoiceEmb = NpyReader.readFloatArray(nullVoiceFile)

        val environment = OrtEnvironment.getEnvironment()
        val numThreads = optimalThreadCount()
        val arThreads = optimalArThreadCount()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(numThreads)
            setMemoryPatternOptimization(true)
            setCPUArenaAllocator(true)
            addConfigEntry("session.intra_op.allow_spinning", "0")
            addConfigEntry("session.arena_extend_strategy", "kNextPowerOfTwo")
            try {
                addXnnpack(mapOf("intra_op_num_threads" to numThreads.toString()))
                android.util.Log.i("ZeroTtsOnnx", "XNNPACK initialized with $numThreads threads")
            } catch (t: Throwable) {
                android.util.Log.w("ZeroTtsOnnx", "XNNPACK not available, using default CPU: ${t.message}")
            }
        }
        val arOptions = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(arThreads)
            setMemoryPatternOptimization(true)
            setCPUArenaAllocator(true)
            addConfigEntry("session.intra_op.allow_spinning", "0")
            addConfigEntry("session.arena_extend_strategy", "kNextPowerOfTwo")
            try {
                addXnnpack(mapOf("intra_op_num_threads" to arThreads.toString()))
                android.util.Log.i("ZeroTtsOnnx", "XNNPACK AR initialized with $arThreads threads")
            } catch (t: Throwable) {
                android.util.Log.w("ZeroTtsOnnx", "XNNPACK AR not available, using default CPU: ${t.message}")
            }
        }
        val opened = ArrayList<OrtSession>(4)
        return try {
            val textEncoderPath = resolveModelPath(directory, "text_encoder.onnx")
            android.util.Log.i("ZeroTtsOnnx", "Creating textEncoder session: $textEncoderPath")
            val textEncoder = environment.createSession(textEncoderPath, options).also(opened::add)

            val prefixStepPath = resolveModelPath(directory, "prefix_step.onnx")
            android.util.Log.i("ZeroTtsOnnx", "Creating prefixStep session: $prefixStepPath")
            val prefixStep = environment.createSession(prefixStepPath, options).also(opened::add)

            val localFrameDecodePath = resolveModelPath(directory, "local_frame_decode.onnx")
            android.util.Log.i("ZeroTtsOnnx", "Creating localFrameDecode session: $localFrameDecodePath")
            val localFrameDecode = environment.createSession(localFrameDecodePath, arOptions).also(opened::add)

            val codecPath = resolveModelPath(directory, "moss_audio_tokenizer_decode_full.onnx")
            android.util.Log.i("ZeroTtsOnnx", "Creating codecDecodeFull session: $codecPath")
            val codecDecodeFull = environment.createSession(codecPath, options).also(opened::add)

            android.util.Log.i("ZeroTtsOnnx", "All 4 ONNX sessions created successfully (general=$numThreads, ar=$arThreads threads)!")

            Sessions(
                directory = directory,
                environment = environment,
                config = config,
                tokenizer = tokenizer,
                nullVoiceEmb = nullVoiceEmb,
                textEncoder = textEncoder,
                prefixStep = prefixStep,
                localFrameDecode = localFrameDecode,
                codecDecodeFull = codecDecodeFull,
            )
        } catch (error: Throwable) {
            android.util.Log.e("ZeroTtsOnnx", "Failed to load ONNX sessions: ${error.javaClass.name}: ${error.message}", error)
            opened.asReversed().forEach { runCatching { it.close() } }
            throw error
        } finally {
            options.close()
            arOptions.close()
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
        private val directory: File,
        private val environment: OrtEnvironment,
        private val config: JSONObject,
        private val tokenizer: ZeroTtsBpeTokenizer,
        private val nullVoiceEmb: FloatArray,
        private val textEncoder: OrtSession,
        private val prefixStep: OrtSession,
        private val localFrameDecode: OrtSession,
        private val codecDecodeFull: OrtSession,
    ) : AutoCloseable {
        private val numCodebooks = config.optInt("num_codebooks", 16)
        private val codebookSize = config.optInt("codebook_size", 1024)
        private val dModel = config.optInt("d_model", 768)
        private val nVoiceQueries = config.optInt("n_voice_queries", 10)
        private val specialTokens = config.optJSONObject("special_tokens")
        private val eoaId = specialTokens?.optInt("<eoa>", 5) ?: 5
        private val random = Random()

        // Hoisted constant sampling parameters tensors
        private val textTempTensor = OnnxTensor.createTensor(environment, floatArrayOf(1.0f))
        private val textTopkTensor = OnnxTensor.createTensor(environment, longArrayOf(50L))
        private val audioTempTensor = OnnxTensor.createTensor(environment, floatArrayOf(0.8f))
        private val audioTopkTensor = OnnxTensor.createTensor(environment, longArrayOf(25L))
        private val audioToppTensor = OnnxTensor.createTensor(environment, floatArrayOf(0.95f))
        private val audioRepPenTensor = OnnxTensor.createTensor(environment, floatArrayOf(1.2f))
        private val cfgScaleTensor = OnnxTensor.createTensor(environment, floatArrayOf(1.0f))
        private val forbidEoaTrueTensor = OnnxTensor.createTensor(
            environment,
            ByteBuffer.allocateDirect(1).apply { put(1.toByte()); flip() },
            longArrayOf(1),
            ai.onnxruntime.OnnxJavaType.BOOL,
        )
        private val forbidEoaFalseTensor = OnnxTensor.createTensor(
            environment,
            ByteBuffer.allocateDirect(1).apply { put(0.toByte()); flip() },
            longArrayOf(1),
            ai.onnxruntime.OnnxJavaType.BOOL,
        )

        // Hoisted constant prefix-step tensors
        private val zeroExternalEmbed = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(FloatArray(dModel)),
            longArrayOf(1, 1, dModel.toLong()),
        )
        private val zeroUseExternalEmbed = OnnxTensor.createTensor(
            environment,
            ByteBuffer.allocateDirect(1).apply { put(0.toByte()); flip() },
            longArrayOf(1, 1),
            ai.onnxruntime.OnnxJavaType.BOOL,
        )
        private val stepNewValid = OnnxTensor.createTensor(
            environment,
            ByteBuffer.allocateDirect(1).apply { put(1.toByte()); flip() },
            longArrayOf(1, 1),
            ai.onnxruntime.OnnxJavaType.BOOL,
        )
        private val stepNewBidir = OnnxTensor.createTensor(
            environment,
            ByteBuffer.allocateDirect(1).apply { put(0.toByte()); flip() },
            longArrayOf(1, 1),
            ai.onnxruntime.OnnxJavaType.BOOL,
        )

        // Reusable direct buffers with isolated allocations to prevent cross-tensor buffer overwrite
        private val seenBytesDirect = ByteBuffer.allocateDirect(numCodebooks * codebookSize)
        private val globalHiddenDirect = ByteBuffer.allocateDirect(dModel * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        private val audioRandomDirect = ByteBuffer.allocateDirect(numCodebooks * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        private val ctrlRandomDirect = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        private val stepFrameCodesDirect = ByteBuffer.allocateDirect(numCodebooks * 8).order(ByteOrder.nativeOrder()).asLongBuffer()
        private val stepPosDirect = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asLongBuffer()
        private var kvDirectBuffer = ByteBuffer.allocateDirect(4 * 1024 * 1024).order(ByteOrder.nativeOrder())
        private var validDirectBuffer = ByteBuffer.allocateDirect(16 * 1024).order(ByteOrder.nativeOrder())
        private var auxDirectBuffer = ByteBuffer.allocateDirect(1024 * 1024).order(ByteOrder.nativeOrder())

        private fun ensureKvCapacity(bytes: Int): ByteBuffer {
            if (kvDirectBuffer.capacity() < bytes) {
                var newCap = kvDirectBuffer.capacity() * 2
                while (newCap < bytes) newCap *= 2
                kvDirectBuffer = ByteBuffer.allocateDirect(newCap).order(ByteOrder.nativeOrder())
            }
            kvDirectBuffer.clear()
            return kvDirectBuffer
        }

        private fun ensureValidCapacity(bytes: Int): ByteBuffer {
            if (validDirectBuffer.capacity() < bytes) {
                var newCap = validDirectBuffer.capacity() * 2
                while (newCap < bytes) newCap *= 2
                validDirectBuffer = ByteBuffer.allocateDirect(newCap).order(ByteOrder.nativeOrder())
            }
            validDirectBuffer.clear()
            return validDirectBuffer
        }

        private fun ensureAuxCapacity(bytes: Int): ByteBuffer {
            if (auxDirectBuffer.capacity() < bytes) {
                var newCap = auxDirectBuffer.capacity() * 2
                while (newCap < bytes) newCap *= 2
                auxDirectBuffer = ByteBuffer.allocateDirect(newCap).order(ByteOrder.nativeOrder())
            }
            auxDirectBuffer.clear()
            return auxDirectBuffer
        }

        fun synthesize(
            text: String,
            voiceId: Int,
            checkCancelled: (() -> Unit)? = null,
        ): FloatArray {
            val chunks = splitIntoChunks(text, MAX_CHUNK_CHARS)
            if (chunks.isEmpty()) return FloatArray(0)
            if (chunks.size == 1) return synthesizeChunk(chunks[0], voiceId, checkCancelled)

            android.util.Log.i(
                "ZeroTtsOnnx",
                "Synthesizing text (${text.length} chars) split into ${chunks.size} chunks (maxChars=$MAX_CHUNK_CHARS)"
            )

            val audioParts = ArrayList<FloatArray>(chunks.size * 2)
            var totalSamples = 0
            val sampleRate = config.optInt("sample_rate", 48000)
            val silenceSamples = (sampleRate * 80) / 1000
            val silence = FloatArray(silenceSamples)

            for (i in chunks.indices) {
                checkCancelled?.invoke()
                val chunk = chunks[i]
                android.util.Log.i(
                    "ZeroTtsOnnx",
                    "Synthesizing chunk ${i + 1}/${chunks.size} (${chunk.length} chars): ${chunk.take(40)}..."
                )
                val chunkAudio = synthesizeChunk(chunk, voiceId, checkCancelled)
                if (chunkAudio.isNotEmpty()) {
                    if (audioParts.isNotEmpty()) {
                        audioParts.add(silence)
                        totalSamples += silence.size
                    }
                    audioParts.add(chunkAudio)
                    totalSamples += chunkAudio.size
                }
            }

            val combined = FloatArray(totalSamples)
            var offset = 0
            for (part in audioParts) {
                System.arraycopy(part, 0, combined, offset, part.size)
                offset += part.size
            }
            return combined
        }

        private fun synthesizeChunk(
            text: String,
            voiceId: Int,
            checkCancelled: (() -> Unit)? = null,
        ): FloatArray {
            val textIds = tokenizer.encode(text)
            val textLen = textIds.size
            val voiceEmb = resolveVoiceEmb(voiceId)

            val textIdsTensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(textIds),
                longArrayOf(1, textLen.toLong()),
            )
            val txtLengthsTensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(longArrayOf(textLen.toLong())),
                longArrayOf(1),
            )

            try {
                textEncoder.run(
                    mapOf(
                        "text_ids" to textIdsTensor,
                        "txt_lengths" to txtLengthsTensor,
                    )
                ).use { encoded ->
                    val textValid = encoded[1] as OnnxTensor
                    val soaEmbed = encoded[2] as OnnxTensor
                    val crossKv = encoded[3] as OnnxTensor

                    val soaBuffer = soaEmbed.floatBuffer
                    val soaFloats = FloatArray(soaBuffer.remaining()).also(soaBuffer::get)

                    // external_embed = concat(voice_emb (1, 10, 768), soa_embed (1, 1, 768)) -> (1, 11, 768)
                    val prefixLen = nVoiceQueries + 1
                    val externalEmbedFloats = FloatArray(prefixLen * dModel)
                    System.arraycopy(voiceEmb, 0, externalEmbedFloats, 0, voiceEmb.size)
                    System.arraycopy(soaFloats, 0, externalEmbedFloats, voiceEmb.size, soaFloats.size)

                    val generatedCodes = runAutoregressiveLoop(
                        externalEmbedFloats = externalEmbedFloats,
                        prefixLen = prefixLen,
                        crossKv = crossKv,
                        textValid = textValid,
                        textLen = textLen,
                        checkCancelled = checkCancelled,
                    )

                    return decodeAudioFrames(generatedCodes)
                }
            } finally {
                textIdsTensor.close()
                txtLengthsTensor.close()
            }
        }

        private fun runAutoregressiveLoop(
            externalEmbedFloats: FloatArray,
            prefixLen: Int,
            crossKv: OnnxTensor,
            textValid: OnnxTensor,
            textLen: Int,
            checkCancelled: (() -> Unit)? = null,
        ): List<LongArray> {
            val externalEmbedTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(externalEmbedFloats),
                longArrayOf(1, prefixLen.toLong(), dModel.toLong()),
            )
            val useExternalEmbedTensor = OnnxTensor.createTensor(
                environment,
                ByteBuffer.allocateDirect(prefixLen).apply { repeat(prefixLen) { put(1.toByte()) }; flip() },
                longArrayOf(1, prefixLen.toLong()),
                ai.onnxruntime.OnnxJavaType.BOOL,
            )
            val frameCodesInit = LongArray(prefixLen * numCodebooks)
            val frameCodesInitTensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(frameCodesInit),
                longArrayOf(1, prefixLen.toLong(), numCodebooks.toLong()),
            )
            val newPosInit = LongArray(prefixLen) { it.toLong() }
            val newPosInitTensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(newPosInit),
                longArrayOf(1, prefixLen.toLong()),
            )
            val newValidInitTensor = OnnxTensor.createTensor(
                environment,
                ByteBuffer.allocateDirect(prefixLen).apply { repeat(prefixLen) { put(1.toByte()) }; flip() },
                longArrayOf(1, prefixLen.toLong()),
                ai.onnxruntime.OnnxJavaType.BOOL,
            )
            val packedKvInitTensor = OnnxTensor.createTensor(
                environment,
                ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()).asFloatBuffer(),
                longArrayOf(9, 2, 1, 12, 0, 64),
            )
            val newBidirInitTensor = OnnxTensor.createTensor(
                environment,
                ByteBuffer.allocateDirect(prefixLen).apply {
                    repeat(nVoiceQueries) { put(1.toByte()) }
                    put(0.toByte()) // SOA is causal (0)
                    flip()
                },
                longArrayOf(1, prefixLen.toLong()),
                ai.onnxruntime.OnnxJavaType.BOOL,
            )
            val pastValidInitTensor = OnnxTensor.createTensor(
                environment,
                ByteBuffer.allocateDirect(0),
                longArrayOf(1, 0),
                ai.onnxruntime.OnnxJavaType.BOOL,
            )

            var currentHidden: FloatArray
            var currentPackedKv: OnnxTensor
            var currentPastValid: OnnxTensor

            try {
                prefixStep.run(
                    mapOf(
                        "external_embed" to externalEmbedTensor,
                        "use_external_embed" to useExternalEmbedTensor,
                        "frame_codes" to frameCodesInitTensor,
                        "new_pos" to newPosInitTensor,
                        "new_valid" to newValidInitTensor,
                        "packed_kv" to packedKvInitTensor,
                        "new_bidirectional" to newBidirInitTensor,
                        "past_valid" to pastValidInitTensor,
                        "cross_kv" to crossKv,
                        "text_valid" to textValid,
                    )
                ).use { initResult ->
                    val hiddenTensor = initResult[0] as OnnxTensor
                    val hiddenBuf = hiddenTensor.floatBuffer
                    val allHidden = FloatArray(hiddenBuf.remaining()).also(hiddenBuf::get)
                    currentHidden = FloatArray(dModel)
                    System.arraycopy(allHidden, (prefixLen - 1) * dModel, currentHidden, 0, dModel)

                    // Keep packed_kv and full_valid for iterative steps
                    currentPackedKv = copyTensor(initResult[1] as OnnxTensor)
                    currentPastValid = copyTensor(initResult[2] as OnnxTensor)
                }
            } finally {
                externalEmbedTensor.close()
                useExternalEmbedTensor.close()
                frameCodesInitTensor.close()
                newPosInitTensor.close()
                newValidInitTensor.close()
                packedKvInitTensor.close()
                newBidirInitTensor.close()
                pastValidInitTensor.close()
            }

            val frames = ArrayList<LongArray>()
            seenBytesDirect.clear()
            val totalMaskBytes = numCodebooks * codebookSize
            repeat(totalMaskBytes) { seenBytesDirect.put(0.toByte()) }
            seenBytesDirect.flip()

            val minFrames = 4
            val maxFrames = (textLen * 6).coerceIn(30, DEFAULT_MAX_FRAMES)
            var tailLeft: Int? = null
            var t = 0

            try {
                while (t < maxFrames) {
                    if (t % 2 == 0) checkCancelled?.invoke()
                    val forbidEoa = t < minFrames || tailLeft != null
                    val (ctrlId, codes) = decodeSingleFrame(currentHidden, forbidEoa)

                    if (tailLeft == null && ctrlId == eoaId) {
                        tailLeft = 1 // 1 extra frame after EOA to avoid clipping audio
                    }
                    if ((tailLeft != null && tailLeft <= 0) || t >= maxFrames) {
                        break
                    }

                    frames.add(codes)

                    if (tailLeft != null) {
                        tailLeft -= 1
                        if (tailLeft <= 0) break
                    }

                    // Advance prefix_step for one frame using direct buffers
                    stepFrameCodesDirect.clear()
                    for (c in 0 until numCodebooks) {
                        stepFrameCodesDirect.put(codes[c])
                    }
                    stepFrameCodesDirect.flip()
                    val stepFrameCodes = OnnxTensor.createTensor(
                        environment,
                        stepFrameCodesDirect,
                        longArrayOf(1, 1, numCodebooks.toLong()),
                    )

                    stepPosDirect.clear()
                    stepPosDirect.put((prefixLen + t).toLong())
                    stepPosDirect.flip()
                    val stepPos = OnnxTensor.createTensor(
                        environment,
                        stepPosDirect,
                        longArrayOf(1, 1),
                    )

                    val oldPacked = currentPackedKv
                    val oldPastValid = currentPastValid

                    try {
                        prefixStep.run(
                            mapOf(
                                "external_embed" to zeroExternalEmbed,
                                "use_external_embed" to zeroUseExternalEmbed,
                                "frame_codes" to stepFrameCodes,
                                "new_pos" to stepPos,
                                "new_valid" to stepNewValid,
                                "packed_kv" to oldPacked,
                                "past_valid" to oldPastValid,
                                "cross_kv" to crossKv,
                                "new_bidirectional" to stepNewBidir,
                                "text_valid" to textValid,
                            )
                        ).use { stepResult ->
                            val hiddenTensor = stepResult[0] as OnnxTensor
                            val hiddenBuf = hiddenTensor.floatBuffer
                            currentHidden = FloatArray(dModel).also(hiddenBuf::get)

                            currentPackedKv = copyTensor(stepResult[1] as OnnxTensor)
                            currentPastValid = copyTensor(stepResult[2] as OnnxTensor)
                        }
                    } finally {
                        stepFrameCodes.close()
                        stepPos.close()
                        oldPacked.close()
                        oldPastValid.close()
                    }

                    t++
                }
            } finally {
                currentPackedKv.close()
                currentPastValid.close()
            }

            return frames
        }

        private fun decodeSingleFrame(
            hidden: FloatArray,
            forbidEoa: Boolean,
        ): Pair<Int, LongArray> {
            globalHiddenDirect.clear()
            globalHiddenDirect.put(hidden)
            globalHiddenDirect.flip()
            val globalHiddenTensor = OnnxTensor.createTensor(
                environment,
                globalHiddenDirect,
                longArrayOf(1, dModel.toLong()),
            )
            val forbidEoaTensor = if (forbidEoa) forbidEoaTrueTensor else forbidEoaFalseTensor

            seenBytesDirect.position(0)
            val seenMaskTensor = OnnxTensor.createTensor(
                environment,
                seenBytesDirect,
                longArrayOf(1, numCodebooks.toLong(), codebookSize.toLong()),
                ai.onnxruntime.OnnxJavaType.BOOL,
            )

            ctrlRandomDirect.clear()
            ctrlRandomDirect.put(random.nextFloat())
            ctrlRandomDirect.flip()
            val ctrlRandomTensor = OnnxTensor.createTensor(
                environment,
                ctrlRandomDirect,
                longArrayOf(1),
            )
            audioRandomDirect.clear()
            for (c in 0 until numCodebooks) {
                audioRandomDirect.put(random.nextFloat())
            }
            audioRandomDirect.flip()
            val audioRandomTensor = OnnxTensor.createTensor(
                environment,
                audioRandomDirect,
                longArrayOf(1, numCodebooks.toLong()),
            )

            try {
                localFrameDecode.run(
                    mapOf(
                        "global_hidden" to globalHiddenTensor,
                        "forbid_eoa" to forbidEoaTensor,
                        "text_temperature" to textTempTensor,
                        "text_topk" to textTopkTensor,
                        "audio_temperature" to audioTempTensor,
                        "audio_topk" to audioTopkTensor,
                        "audio_topp" to audioToppTensor,
                        "audio_repetition_penalty" to audioRepPenTensor,
                        "seen_mask" to seenMaskTensor,
                        "ctrl_random_u" to ctrlRandomTensor,
                        "audio_random_u" to audioRandomTensor,
                        "cfg_scale" to cfgScaleTensor,
                    )
                ).use { result ->
                    val isEoaTensor = result[0] as OnnxTensor
                    val isEoa = when (isEoaTensor.info.type) {
                        ai.onnxruntime.OnnxJavaType.INT32 -> isEoaTensor.intBuffer.get(0) != 0
                        ai.onnxruntime.OnnxJavaType.INT64 -> isEoaTensor.longBuffer.get(0) != 0L
                        ai.onnxruntime.OnnxJavaType.BOOL,
                        ai.onnxruntime.OnnxJavaType.INT8,
                        ai.onnxruntime.OnnxJavaType.UINT8 -> isEoaTensor.byteBuffer.get(0).toInt() != 0
                        else -> isEoaTensor.intBuffer?.get(0) != 0
                    }

                    val codesTensor = result[1] as OnnxTensor
                    val codes = LongArray(numCodebooks)
                    when (codesTensor.info.type) {
                        ai.onnxruntime.OnnxJavaType.INT32 -> {
                            val buf = codesTensor.intBuffer
                            for (c in 0 until numCodebooks) {
                                codes[c] = buf.get().toLong()
                            }
                        }
                        ai.onnxruntime.OnnxJavaType.INT64 -> {
                            val buf = codesTensor.longBuffer
                            for (c in 0 until numCodebooks) {
                                codes[c] = buf.get()
                            }
                        }
                        else -> {
                            val buf = codesTensor.intBuffer
                                ?: throw IllegalStateException("Unexpected codes tensor type: ${codesTensor.info.type}")
                            for (c in 0 until numCodebooks) {
                                codes[c] = buf.get().toLong()
                            }
                        }
                    }

                    for (c in 0 until numCodebooks) {
                        val code = codes[c].toInt()
                        if (code in 0 until codebookSize) {
                            seenBytesDirect.put(c * codebookSize + code, 1.toByte())
                        }
                    }

                    val ctrlId = if (isEoa) eoaId else 4
                    return Pair(ctrlId, codes)
                }
            } finally {
                globalHiddenTensor.close()
                seenMaskTensor.close()
                ctrlRandomTensor.close()
                audioRandomTensor.close()
            }
        }

        private fun decodeAudioFrames(frames: List<LongArray>): FloatArray {
            if (frames.isEmpty()) return FloatArray(0)
            val numFrames = frames.size

            // MOSS audio codec decode expects (1, numFrames, 16) int32
            val audioCodes = IntArray(numFrames * numCodebooks)
            for (t in 0 until numFrames) {
                val frame = frames[t]
                for (c in 0 until numCodebooks) {
                    audioCodes[t * numCodebooks + c] = frame[c].toInt()
                }
            }

            val audioCodesTensor = OnnxTensor.createTensor(
                environment,
                IntBuffer.wrap(audioCodes),
                longArrayOf(1, numFrames.toLong(), numCodebooks.toLong()),
            )
            val audioLengthsTensor = OnnxTensor.createTensor(
                environment,
                IntBuffer.wrap(intArrayOf(numFrames)),
                longArrayOf(1),
            )

            try {
                codecDecodeFull.run(
                    mapOf(
                        "audio_codes" to audioCodesTensor,
                        "audio_code_lengths" to audioLengthsTensor,
                    )
                ).use { result ->
                    val audioTensor = result[0] as OnnxTensor
                    val lengthsTensor = result[1] as OnnxTensor
                    val totalSamples = when (lengthsTensor.info.type) {
                        ai.onnxruntime.OnnxJavaType.INT32 -> lengthsTensor.intBuffer.get(0)
                        ai.onnxruntime.OnnxJavaType.INT64 -> lengthsTensor.longBuffer.get(0).toInt()
                        else -> lengthsTensor.intBuffer?.get(0) ?: lengthsTensor.longBuffer?.get(0)?.toInt() ?: 0
                    }

                    // Audio output shape is (1, 2, totalSamples) stereo -> average to mono
                    val audioBuffer = audioTensor.floatBuffer
                    val samples = FloatArray(totalSamples)
                    val channel0 = FloatArray(totalSamples)
                    val channel1 = FloatArray(totalSamples)

                    audioBuffer.get(channel0)
                    audioBuffer.get(channel1)

                    for (i in 0 until totalSamples) {
                        samples[i] = (channel0[i] + channel1[i]) * 0.5f
                    }
                    return samples
                }
            } finally {
                audioCodesTensor.close()
                audioLengthsTensor.close()
            }
        }

        private fun resolveVoiceEmb(voiceId: Int): FloatArray {
            val voicesDir = File(directory, "voices")
            if (voicesDir.isDirectory) {
                val matched = voicesDir.listFiles()?.firstOrNull { file ->
                    file.name.startsWith("${voiceId}_") && (file.name.endsWith(".bin") || file.name.endsWith(".npy"))
                }
                if (matched != null) {
                    return if (matched.name.endsWith(".npy")) {
                        NpyReader.readFloatArray(matched)
                    } else {
                        readRawFloatArray(matched)
                    }
                }
            }
            return nullVoiceEmb
        }

        private fun readRawFloatArray(file: File): FloatArray {
            val bytes = file.readBytes()
            val floats = FloatArray(bytes.size / 4)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
            return floats
        }

        private fun copyTensor(tensor: OnnxTensor): OnnxTensor {
            val info = tensor.info
            val shape = info.shape
            return when (info.type) {
                ai.onnxruntime.OnnxJavaType.FLOAT -> {
                    val fb = tensor.floatBuffer
                    val remaining = fb.remaining()
                    val buf = ensureKvCapacity(remaining * 4)
                    val targetFloatBuffer = buf.asFloatBuffer()
                    fb.mark()
                    targetFloatBuffer.put(fb)
                    fb.reset()
                    targetFloatBuffer.flip()
                    OnnxTensor.createTensor(environment, targetFloatBuffer, shape)
                }
                ai.onnxruntime.OnnxJavaType.BOOL -> {
                    val bb = tensor.byteBuffer
                    val remaining = bb.remaining()
                    val buf = ensureValidCapacity(remaining)
                    bb.mark()
                    buf.put(bb)
                    bb.reset()
                    buf.flip()
                    OnnxTensor.createTensor(environment, buf, shape, ai.onnxruntime.OnnxJavaType.BOOL)
                }
                ai.onnxruntime.OnnxJavaType.INT32 -> {
                    val ib = tensor.intBuffer
                    val remaining = ib.remaining()
                    val buf = ensureAuxCapacity(remaining * 4)
                    val targetIntBuffer = buf.asIntBuffer()
                    ib.mark()
                    targetIntBuffer.put(ib)
                    ib.reset()
                    targetIntBuffer.flip()
                    OnnxTensor.createTensor(environment, targetIntBuffer, shape)
                }
                ai.onnxruntime.OnnxJavaType.INT64 -> {
                    val lb = tensor.longBuffer
                    val remaining = lb.remaining()
                    val buf = ensureAuxCapacity(remaining * 8)
                    val targetLongBuffer = buf.asLongBuffer()
                    lb.mark()
                    targetLongBuffer.put(lb)
                    lb.reset()
                    targetLongBuffer.flip()
                    OnnxTensor.createTensor(environment, targetLongBuffer, shape)
                }
                else -> throw UnsupportedOperationException("Unsupported tensor copy type: ${info.type}")
            }
        }

        override fun close() {
            runCatching { textEncoder.close() }
            runCatching { prefixStep.close() }
            runCatching { localFrameDecode.close() }
            runCatching { codecDecodeFull.close() }
            runCatching { textTempTensor.close() }
            runCatching { textTopkTensor.close() }
            runCatching { audioTempTensor.close() }
            runCatching { audioTopkTensor.close() }
            runCatching { audioToppTensor.close() }
            runCatching { audioRepPenTensor.close() }
            runCatching { cfgScaleTensor.close() }
            runCatching { forbidEoaTrueTensor.close() }
            runCatching { forbidEoaFalseTensor.close() }
            runCatching { zeroExternalEmbed.close() }
            runCatching { zeroUseExternalEmbed.close() }
            runCatching { stepNewValid.close() }
            runCatching { stepNewBidir.close() }
        }
    }

    companion object {
        private const val IDLE_UNLOAD_MS = 60 * 1000L
        private const val DEFAULT_MAX_FRAMES = 250
        const val MAX_CHUNK_CHARS = 85

        internal fun splitIntoChunks(text: String, maxChars: Int = MAX_CHUNK_CHARS): List<String> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return emptyList()
            if (trimmed.length <= maxChars) return listOf(trimmed)

            // Phase 1: Split text into sentence pieces (preserving trailing sentence punctuation)
            val sentenceRegex = Regex("""(?<=[.!?;\n…。！？；])(?![.!?;\n…。！？；])\s*""")
            val rawSentences = trimmed.split(sentenceRegex).map { it.trim() }.filter { it.isNotEmpty() }

            // Phase 2: For any sentence > maxChars, split by clause punctuation (preserving clause punctuation)
            val clauseRegex = Regex("""(?<=[,:\—\–\"“”«»\(\)（），：])(?![,\—\–\"“”«»\(\)（），：])\s*""")
            val refinedPieces = mutableListOf<String>()

            for (sentence in rawSentences) {
                if (sentence.length <= maxChars) {
                    refinedPieces.add(sentence)
                } else {
                    val clauses = sentence.split(clauseRegex).map { it.trim() }.filter { it.isNotEmpty() }
                    for (clause in clauses) {
                        if (clause.length <= maxChars) {
                            refinedPieces.add(clause)
                        } else {
                            // Phase 3: Split long clause by whitespace
                            val words = clause.split(Regex("""\s+""")).filter { it.isNotEmpty() }
                            val wordBuf = StringBuilder()
                            for (word in words) {
                                if (wordBuf.isNotEmpty() && wordBuf.length + 1 + word.length > maxChars) {
                                    refinedPieces.add(wordBuf.toString())
                                    wordBuf.clear()
                                }
                                if (word.length > maxChars) {
                                    // Phase 4: Hard split if a single token exceeds maxChars
                                    var start = 0
                                    while (start < word.length) {
                                        val end = (start + maxChars).coerceAtMost(word.length)
                                        refinedPieces.add(word.substring(start, end))
                                        start = end
                                    }
                                } else {
                                    if (wordBuf.isNotEmpty()) wordBuf.append(' ')
                                    wordBuf.append(word)
                                }
                            }
                            if (wordBuf.isNotEmpty()) {
                                refinedPieces.add(wordBuf.toString())
                            }
                        }
                    }
                }
            }

            // Phase 5: Pack consecutive small pieces up to maxChars
            val chunks = mutableListOf<String>()
            val currentChunk = StringBuilder()

            for (piece in refinedPieces) {
                if (currentChunk.isEmpty()) {
                    currentChunk.append(piece)
                } else if (currentChunk.length + 1 + piece.length <= maxChars) {
                    currentChunk.append(' ').append(piece)
                } else {
                    chunks.add(currentChunk.toString())
                    currentChunk.clear()
                    currentChunk.append(piece)
                }
            }
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
            }

            return chunks
        }

        internal fun optimalThreadCount(): Int {
            val available = Runtime.getRuntime().availableProcessors()
            return when {
                available <= 2 -> available
                available in 3..4 -> available
                // On ARM big.LITTLE mobile CPUs (typically 4 big + 4 little), using > 4 threads
                // forces intra-op kernels onto little cores, causing barrier latency bottlenecks.
                else -> 4
            }
        }

        internal fun optimalArThreadCount(): Int {
            val available = Runtime.getRuntime().availableProcessors()
            return when {
                available <= 2 -> available
                else -> 2
            }
        }

        internal fun resolveModelPath(directory: File, baseName: String): String {
            val ortFile = File(directory, baseName.replace(".onnx", ".ort"))
            if (ortFile.isFile) return ortFile.absolutePath
            val int8File = File(directory, baseName.replace(".onnx", ".int8.onnx"))
            if (int8File.isFile) return int8File.absolutePath
            val onnxFile = File(directory, baseName)
            if (onnxFile.isFile) return onnxFile.absolutePath
            throw IOException("Không tìm thấy tệp model $baseName trong ${directory.absolutePath}")
        }
    }
}
