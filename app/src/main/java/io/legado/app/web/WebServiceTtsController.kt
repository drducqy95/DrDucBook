package io.legado.app.web

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.appDb
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.ReadAloud
import io.legado.app.model.tts.LocalTtsModelRegistry
import io.legado.app.model.tts.parseLocalTtsEngine
import io.legado.app.model.tts.LocalTtsSynthesis
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.lib.dialogs.SelectItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import io.legado.app.domain.webservice.WebServiceTtsCatalogItemResponse
import io.legado.app.domain.webservice.WebServiceTtsModelResponse
import io.legado.app.domain.webservice.WebServiceTtsModelSelectRequest
import io.legado.app.domain.webservice.WebServiceTtsModelsResponse
import io.legado.app.domain.webservice.WebServiceTtsVoiceResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object WebServiceTtsController {
    private const val TTL_MILLIS = 30 * 60 * 1000L
    private val files = ConcurrentHashMap<String, TtsFile>()

    fun models(): WebServiceTtsModelsResponse {
        val selected = ReadConfig.ttsEngine.orEmpty()
        val localSelected = parseLocalTtsEngine(selected)

        val localModels = LocalTtsModelRegistry(appCtx).list().map { model ->
            WebServiceTtsModelResponse(
                id = model.id,
                name = model.name,
                engine = "local",
                language = model.language,
                sampleRate = model.sampleRate,
                voices = model.voices.map { WebServiceTtsVoiceResponse(it.id, it.name) },
                defaultVoiceId = model.defaultVoiceId,
                selectedVoiceId = if (localSelected?.modelId == model.id) localSelected.voiceId else null,
                isDefault = localSelected?.modelId == model.id,
                runtimeReady = true,
                sizeBytes = model.sizeBytes,
                checksum = model.checksum,
            )
        }

        val httpModels = appDb.httpTTSDao.all.map { http ->
            WebServiceTtsModelResponse(
                id = "http:${http.id}",
                name = http.name,
                engine = "http",
                language = "vi",
                sampleRate = 22050,
                voices = emptyList(),
                defaultVoiceId = 0,
                selectedVoiceId = null,
                isDefault = selected == http.id.toString(),
                runtimeReady = true,
                sizeBytes = 0L,
                checksum = "",
            )
        }

        val systemModels = mutableListOf<WebServiceTtsModelResponse>()
        systemModels.add(
            WebServiceTtsModelResponse(
                id = "system:default",
                name = "Android System TTS (Mặc định)",
                engine = "system",
                language = Locale.getDefault().toLanguageTag(),
                sampleRate = 22050,
                voices = emptyList(),
                defaultVoiceId = 0,
                selectedVoiceId = null,
                isDefault = selected.isBlank() || selected == "system:default",
                runtimeReady = true,
                sizeBytes = 0L,
                checksum = "",
            )
        )

        val catalog = emptyList<WebServiceTtsCatalogItemResponse>()
        val selectedEngineStr = when {
            selected.isBlank() -> "system:default"
            localSelected != null -> localSelected.modelId
            selected.toLongOrNull() != null -> "http:$selected"
            else -> selected
        }

        return WebServiceTtsModelsResponse(
            models = systemModels + localModels + httpModels,
            catalog = catalog,
            selectedEngine = selectedEngineStr,
            speechRate = ReadConfig.ttsSpeechRate,
            ttsFollowSys = ReadConfig.ttsFollowSys,
        )
    }

    suspend fun selectModel(request: WebServiceTtsModelSelectRequest) {
        if (request.speechRate != null) {
            ReadConfig.ttsSpeechRate = request.speechRate
        }
        if (request.ttsFollowSys != null) {
            ReadConfig.ttsFollowSys = request.ttsFollowSys
        }
        val modelId = request.modelId.trim()
        if (modelId.isNotBlank()) {
            when {
                modelId == "system:default" || modelId == "system" -> {
                    ReadConfig.ttsEngine = null
                }
                modelId.startsWith("http:") -> {
                    val httpId = modelId.removePrefix("http:").trim()
                    ReadConfig.ttsEngine = httpId
                }
                modelId.startsWith("system:") -> {
                    val pkgName = modelId.removePrefix("system:").trim()
                    ReadConfig.ttsEngine = pkgName
                }
                else -> {
                    val registry = LocalTtsModelRegistry(appCtx)
                    val model = registry.get(modelId)
                        ?: throw IllegalArgumentException("TTS_MODEL_NOT_FOUND: $modelId")
                    val voiceId = request.voiceId ?: model.defaultVoiceId
                    ReadConfig.ttsEngine = model.engineValue(voiceId)
                }
            }
        }
        ReadAloud.upReadAloudClass()
    }

    fun capabilities(bookUrl: String? = null): Pair<String, String> {
        val configured = normalizedEngine(resolveEngine(bookUrl))
        val engine = when {
            configured.isBlank() -> "system"
            parseLocalTtsEngine(configured) != null -> "local"
            configured.toLongOrNull() != null -> "http"
            else -> configured
        }
        val language = if (engine == "local") {
            localModelLanguage(configured) ?: Locale.getDefault().toLanguageTag()
        } else {
            Locale.getDefault().toLanguageTag()
        }
        return engine to language
    }

    suspend fun synthesize(text: String, language: String?, bookUrl: String? = null): TtsFile {
        val cleanText = normalizeSpeechText(text).take(20_000).takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("TTS_TEXT_REQUIRED")
        val requestedLanguage = language?.trim()?.takeIf(String::isNotBlank)
        val requestedLocale = requestedLanguage
            ?.let(Locale::forLanguageTag)
            ?.takeIf { it.language.isNotBlank() }
            ?: inferLocale(cleanText)
        val id = UUID.randomUUID().toString()
        val file = File(appCtx.cacheDir, "web_tts_$id.wav")
        val configuredEngine = normalizedEngine(resolveEngine(bookUrl))
        val localEngine = parseLocalTtsEngine(configuredEngine)
        if (localEngine != null) {
            val localText = localSpeechText(cleanText, configuredEngine)
                ?: throw IllegalArgumentException("TTS_TEXT_UNSUPPORTED_LOCAL")
            val speed = (ReadConfig.speechRatePlay + 5) / 10f
            val localFile = LocalTtsSynthesis.synthesizeToWav(appCtx, configuredEngine, localText, speed)
            if (hasUsableDuration(localFile, localText)) {
                val expiresAt = System.currentTimeMillis() + TTL_MILLIS
                files[id] = TtsFile(id, localFile, requestedLocale.toLanguageTag(), expiresAt, "audio/wav")
                trimExpired()
                return files[id]!!
            }
            throw IllegalStateException("TTS_LOCAL_AUDIO_INVALID")
        }
        val engineForSystemTts = configuredEngine.takeUnless { parseLocalTtsEngine(it) != null }
        val httpTts = configuredEngine.toLongOrNull()?.let(appDb.httpTTSDao::get)
        if (httpTts != null) {
            val contentType = synthesizeHttp(httpTts, cleanText, file)
            val expiresAt = System.currentTimeMillis() + TTL_MILLIS
            files[id] = TtsFile(id, file, requestedLocale.toLanguageTag(), expiresAt, contentType)
            trimExpired()
            return files[id]!!
        }
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
                    val languageResult = tts.setLanguage(requestedLocale)
                    if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                        languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        finish(Result.failure(IllegalStateException("TTS_LANGUAGE_UNAVAILABLE")))
                        return@TextToSpeech
                    }
                    tts.setSpeechRate((ReadConfig.speechRatePlay + 5) / 10f)
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) = finish(Result.success(Unit))
                        override fun onError(id: String?) = finish(Result.failure(IllegalStateException("TTS_SYNTHESIS_FAILED")))
                    })
                    val result = tts.synthesizeToFile(cleanText, Bundle(), file, utteranceId)
                    if (result == TextToSpeech.ERROR) {
                        finish(Result.failure(IllegalStateException("TTS_SYNTHESIS_FAILED")))
                    }
                }, engineForSystemTts?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) })
                continuation.invokeOnCancellation { handler.post { tts.runCatching { shutdown() } } }
            }
        }
        val expiresAt = System.currentTimeMillis() + TTL_MILLIS
        if (!file.isFile || file.length() <= 44L) {
            file.delete()
            throw IllegalStateException("TTS_AUDIO_EMPTY")
        }
        files[id] = TtsFile(id, file, requestedLocale.toLanguageTag(), expiresAt, "audio/wav")
        trimExpired()
        return files[id]!!
    }

    fun get(id: String): TtsFile? = files[id]?.takeIf { it.expiresAt > System.currentTimeMillis() }

    private fun resolveEngine(bookUrl: String?): String {
        val bookEngine = bookUrl
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { url -> appDb.bookDao.getBook(url)?.getTtsEngine() }
            ?.takeIf(String::isNotBlank)
        val configured = bookEngine ?: ReadAloud.ttsEngine.orEmpty()
        if (configured.isNotBlank()) return configured

        // A release device may not ship with Android's system TTS provider. If
        // the app already has an imported ONNX model, use that model as the
        // deterministic WebService fallback instead of returning
        // TTS_INIT_FAILED from TextToSpeech.
        return runCatching {
            LocalTtsModelRegistry(appCtx).list().firstOrNull()?.engineValue()
        }.getOrNull().orEmpty()
    }

    private fun normalizeSpeechText(value: String): String = value
        .replace('\u00A0', ' ')
        .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizedEngine(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        return GSON.fromJsonObject<SelectItem<String>>(value).getOrNull()?.value?.trim()
            ?: value
    }

    private fun localModelLanguage(engineReference: String): String? {
        val reference = parseLocalTtsEngine(engineReference) ?: return null
        return LocalTtsModelRegistry(appCtx).get(reference.modelId)?.language?.takeIf(String::isNotBlank)
    }

    private fun localSpeechText(text: String, engineReference: String): String? {
        val language = localModelLanguage(engineReference)?.lowercase(Locale.ROOT).orEmpty()
        val sanitized = if (language.startsWith("zh") ||
            language.startsWith("ja") ||
            language.startsWith("ko") ||
            language.startsWith("ru") ||
            language.startsWith("ar")
        ) {
            text
        } else {
            stripUnsupportedLocalCharacters(text)
        }
        return normalizeSpeechText(sanitized).takeIf(String::isNotBlank)
    }

    private suspend fun synthesizeHttp(
        httpTts: HttpTTS,
        text: String,
        target: File,
    ): String {
        val analyzeUrl = AnalyzeUrl(
            mUrl = httpTts.url,
            speakText = text,
            speakSpeed = ReadConfig.speechRatePlay + 5,
            source = httpTts,
            readTimeout = 300_000L,
            coroutineContext = currentCoroutineContext(),
        )
        var response = analyzeUrl.getResponseAwait()
        val loginCheckJs = httpTts.loginCheckJs?.trim().orEmpty()
        if (loginCheckJs.isNotBlank()) {
            currentCoroutineContext().ensureActive()
            val checkedResponse = analyzeUrl.evalJS(loginCheckJs, response) as? okhttp3.Response
                ?: run {
                    response.close()
                    throw IllegalStateException("HTTP_TTS_LOGIN_CHECK_FAILED")
                }
            if (checkedResponse !== response) response.close()
            response = checkedResponse
        }
        var contentType = ""
        response.use { result ->
            if (!result.isSuccessful) {
                throw IllegalStateException("HTTP_TTS_FAILED_${result.code}")
            }
            contentType = result.header("Content-Type").orEmpty().substringBefore(';').trim()
            if (contentType.startsWith("text/", true) || contentType.contains("json", true)) {
                throw NoStackTraceException(result.body.string().take(512).ifBlank { "HTTP_TTS_INVALID_AUDIO" })
            }
            val expectedContentType = httpTts.contentType?.trim().orEmpty()
            if (expectedContentType.isNotBlank() &&
                (contentType.isBlank() || !Regex(expectedContentType).matches(contentType))
            ) {
                throw NoStackTraceException(
                    "HTTP_TTS_CONTENT_TYPE_INVALID:" + result.body.string().take(512)
                )
            }
            result.body.byteStream().use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        }
        if (!target.isFile || target.length() == 0L) {
            target.delete()
            throw IllegalStateException("TTS_AUDIO_EMPTY")
        }
        return contentType.ifBlank { "audio/mpeg" }
    }

    private fun trimExpired() {
        val now = System.currentTimeMillis()
        files.entries.removeIf { entry ->
            if (entry.value.expiresAt <= now) {
                entry.value.file.delete()
                true
            } else false
        }
    }

    private fun stripUnsupportedLocalCharacters(text: String): String =
        buildString(text.length) {
            text.forEach { character ->
                append(
                    if (isUnsupportedLocalCharacter(character)) ' ' else character
                )
            }
        }

    private fun isUnsupportedLocalCharacter(character: Char): Boolean =
        character in '\u2E80'..'\u9FFF' ||
            character in '\uAC00'..'\uD7AF' ||
            character in '\u3040'..'\u30FF' ||
            character in '\u0400'..'\u04FF' ||
            character in '\u0600'..'\u06FF'

    private fun hasUsableDuration(file: File, text: String): Boolean {
        if (!file.isFile || file.length() <= 44L) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(44)
                if (input.read(header) != header.size) return false
                val sampleRate = littleEndianInt(header, 24)
                val channels = littleEndianShort(header, 22)
                val bits = littleEndianShort(header, 34)
                val dataSize = littleEndianInt(header, 40)
                if (sampleRate <= 0 || channels <= 0 || bits <= 0 || dataSize <= 0) return false
                val seconds = dataSize.toDouble() / (sampleRate * channels * (bits / 8.0))
                val expected = (text.count { !it.isWhitespace() } * 0.018).coerceIn(0.35, 45.0)
                seconds >= expected * 0.35
            }
        }.getOrDefault(false)
    }

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun inferLocale(text: String): Locale = when {
        text.any { it in '\u3040'..'\u30FF' } -> Locale.JAPAN
        text.any { it in '\uAC00'..'\uD7AF' } -> Locale.KOREA
        text.any { it in '\u2E80'..'\u9FFF' } -> Locale.SIMPLIFIED_CHINESE
        text.any { it in '\u0400'..'\u04FF' } -> Locale("ru")
        text.any { it in '\u0600'..'\u06FF' } -> Locale("ar")
        else -> Locale.getDefault()
    }

    data class TtsFile(
        val id: String,
        val file: File,
        val language: String,
        val expiresAt: Long,
        val contentType: String,
    )
}
