package io.legado.app.model.tts

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.absoluteValue

import io.legado.app.ui.config.readConfig.ReadConfig

/** Shared, serialized local-model synthesis for reader and WebService requests. */
object LocalTtsSynthesis {
    private const val WAV_HEADER_SIZE = 44
    private val mutex = Mutex()
    private val cachedEngines = java.util.concurrent.ConcurrentHashMap<String, LocalTtsSynthesisEngine>()

    private data class ResolvedTarget(
        val model: LocalTtsModel,
        val voiceId: Int,
        val cleanText: String,
        val targetFile: File,
    )

    private fun resolveTarget(
        context: Context,
        engineReference: String,
        text: String,
        speed: Float,
    ): ResolvedTarget? {
        val reference = parseLocalTtsEngine(engineReference) ?: return null
        val registry = LocalTtsModelRegistry(context)
        val model = registry.get(reference.modelId)
            ?: registry.list().firstOrNull { it.engine == LocalTtsModelRegistry.ENGINE_ZEROTTS }
            ?: registry.list().firstOrNull()
            ?: return null
        val voiceId = reference.voiceId.takeIf { id -> model.voices.any { it.id == id } }
            ?: model.defaultVoiceId
        val cleanText = text
            .replace('\u00A0', ' ')
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf(String::isNotBlank)
            ?: return null
        val root = File(context.externalCacheDir ?: context.cacheDir, "local_tts_audio").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${model.id}\u0000$voiceId\u0000$speed\u0000$cleanText".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val target = File(root, "$digest.wav")
        return ResolvedTarget(model, voiceId, cleanText, target)
    }

    fun getCachedWav(
        context: Context,
        engineReference: String,
        text: String,
        speed: Float = (ReadConfig.speechRatePlay + 5) / 10f,
    ): File? {
        val resolved = resolveTarget(context, engineReference, text, speed) ?: return null
        return if (resolved.targetFile.isFile && resolved.targetFile.length() > WAV_HEADER_SIZE.toLong()) {
            resolved.targetFile
        } else {
            null
        }
    }

    suspend fun synthesizeToWav(
        context: Context,
        engineReference: String,
        text: String,
        speed: Float = (ReadConfig.speechRatePlay + 5) / 10f,
    ): File {
        getCachedWav(context, engineReference, text, speed)?.let { return it }
        return mutex.withLock {
            val resolved = resolveTarget(context, engineReference, text, speed)
                ?: error("LOCAL_TTS_ENGINE_INVALID")
            if (!resolved.targetFile.isFile || resolved.targetFile.length() <= WAV_HEADER_SIZE.toLong()) {
                val engine = getOrCreateEngine(context, resolved.model)
                writeWaveAtomically(
                    resolved.targetFile,
                    engine.synthesize(resolved.cleanText, resolved.voiceId, speed),
                    resolved.model.sampleRate
                )
            }
            resolved.targetFile
        }
    }

    private fun getOrCreateEngine(context: Context, model: LocalTtsModel): LocalTtsSynthesisEngine {
        return cachedEngines.computeIfAbsent(model.id) {
            when (model.engine) {
                LocalTtsModelRegistry.ENGINE_VALTEC_VITS -> ValtecOnnxTtsEngine(model)
                LocalTtsModelRegistry.ENGINE_PIPER_VITS -> PiperOnnxTtsEngine(context.applicationContext, model)
                LocalTtsModelRegistry.ENGINE_ZEROTTS -> ZeroTtsOnnxEngine(model)
                else -> error("LOCAL_TTS_ENGINE_UNSUPPORTED")
            }
        }
    }

    fun clearCache() {
        cachedEngines.values.forEach { runCatching { it.close() } }
        cachedEngines.clear()
    }

    private fun writeWaveAtomically(target: File, samples: FloatArray, sampleRate: Int) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        val peak = samples.maxOfOrNull { it.absoluteValue }?.coerceAtLeast(1f) ?: 1f
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { sample ->
            pcm.putShort(((sample / peak).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
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
        check(temporary.renameTo(target) || temporary.copyTo(target, overwrite = true).let { temporary.delete() }) {
            "LOCAL_TTS_AUDIO_WRITE_FAILED"
        }
    }
}
