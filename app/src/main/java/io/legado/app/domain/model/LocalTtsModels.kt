package io.legado.app.domain.model

import kotlinx.collections.immutable.ImmutableList

data class LocalTtsVoiceInfo(
    val id: Int,
    val name: String,
)

data class LocalTtsModelInfo(
    val id: String,
    val name: String,
    val engine: String,
    val language: String,
    val sampleRate: Int,
    val voices: ImmutableList<LocalTtsVoiceInfo>,
    val defaultVoiceId: Int,
    val selectedVoiceId: Int?,
    val attribution: String,
    val license: String,
    val checksum: String,
    val sizeBytes: Long,
    val runtimeReady: Boolean,
)

data class LocalTtsModelTestResult(
    val success: Boolean,
    val sampleRate: Int = 0,
    val frameCount: Int = 0,
    val message: String = "",
)
