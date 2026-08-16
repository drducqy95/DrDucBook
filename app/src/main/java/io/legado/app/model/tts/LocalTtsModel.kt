package io.legado.app.model.tts

import androidx.compose.runtime.Immutable

const val LOCAL_TTS_ENGINE_PREFIX = "local-tts:"

@Immutable
data class LocalTtsVoice(
    val id: Int,
    val name: String,
)

@Immutable
data class LocalTtsModel(
    val id: String,
    val name: String,
    val engine: String,
    val language: String,
    val sampleRate: Int,
    val voices: List<LocalTtsVoice>,
    val defaultVoiceId: Int,
    val attribution: String,
    val license: String,
    val directoryPath: String,
    val checksum: String = "",
    val sizeBytes: Long = 0L,
) {
    fun engineValue(voiceId: Int = defaultVoiceId): String =
        "$LOCAL_TTS_ENGINE_PREFIX$id:$voiceId"
}

data class LocalTtsEngineRef(
    val modelId: String,
    val voiceId: Int,
)

fun parseLocalTtsEngine(value: String?): LocalTtsEngineRef? {
    if (value == null || !value.startsWith(LOCAL_TTS_ENGINE_PREFIX)) return null
    val body = value.removePrefix(LOCAL_TTS_ENGINE_PREFIX)
    val separator = body.lastIndexOf(':')
    if (separator <= 0 || separator == body.lastIndex) return null
    val modelId = body.substring(0, separator)
    val voiceId = body.substring(separator + 1).toIntOrNull() ?: return null
    return LocalTtsEngineRef(modelId, voiceId)
}
