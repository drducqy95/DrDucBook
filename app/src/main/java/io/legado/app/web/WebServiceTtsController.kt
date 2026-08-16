package io.legado.app.web

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.legado.app.model.ReadAloud
import io.legado.app.model.tts.parseLocalTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object WebServiceTtsController {
    private const val TTL_MILLIS = 30 * 60 * 1000L
    private val files = ConcurrentHashMap<String, TtsFile>()

    fun capabilities(): Pair<String, String> {
        val configured = ReadAloud.ttsEngine.orEmpty()
        val engine = when {
            configured.isBlank() || configured.all(Char::isDigit) || parseLocalTtsEngine(configured) != null -> "system"
            else -> configured
        }
        return engine to Locale.getDefault().toLanguageTag()
    }

    suspend fun synthesize(text: String, language: String?): TtsFile {
        val cleanText = text.trim().take(20_000).takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("TTS_TEXT_REQUIRED")
        val requestedLocale = language?.trim()?.takeIf(String::isNotBlank)
            ?.let(Locale::forLanguageTag)
            ?: Locale.getDefault()
        val id = UUID.randomUUID().toString()
        val file = File(appCtx.cacheDir, "web_tts_$id.wav")
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { continuation ->
                lateinit var tts: TextToSpeech
                val handler = Handler(Looper.getMainLooper())
                val utteranceId = "web-$id"
                val finish: (Result<Unit>) -> Unit = { result ->
                    handler.post {
                        tts.runCatching { shutdown() }
                        if (continuation.isActive) continuation.resumeWith(result)
                    }
                }
                tts = TextToSpeech(appCtx, { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        finish(Result.failure(IllegalStateException("TTS_INIT_FAILED")))
                        return@TextToSpeech
                    }
                    tts.language = requestedLocale
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) = finish(Result.success(Unit))
                        override fun onError(id: String?) = finish(Result.failure(IllegalStateException("TTS_SYNTHESIS_FAILED")))
                    })
                    val result = tts.synthesizeToFile(cleanText, Bundle(), file, utteranceId)
                    if (result == TextToSpeech.ERROR) {
                        finish(Result.failure(IllegalStateException("TTS_SYNTHESIS_FAILED")))
                    }
                }, ReadAloud.ttsEngine?.takeIf {
                    it.isNotBlank() && !it.all(Char::isDigit) && parseLocalTtsEngine(it) == null
                })
                continuation.invokeOnCancellation { handler.post { tts.runCatching { shutdown() } } }
            }
        }
        val expiresAt = System.currentTimeMillis() + TTL_MILLIS
        files[id] = TtsFile(id, file, requestedLocale.toLanguageTag(), expiresAt)
        trimExpired()
        return files[id]!!
    }

    fun get(id: String): TtsFile? = files[id]?.takeIf { it.expiresAt > System.currentTimeMillis() }

    private fun trimExpired() {
        val now = System.currentTimeMillis()
        files.entries.removeIf { entry ->
            if (entry.value.expiresAt <= now) {
                entry.value.file.delete()
                true
            } else false
        }
    }

    data class TtsFile(
        val id: String,
        val file: File,
        val language: String,
        val expiresAt: Long,
    )
}
