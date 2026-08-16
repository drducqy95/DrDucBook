package io.legado.app.model.tts

interface LocalTtsSynthesisEngine : AutoCloseable {
    suspend fun synthesize(text: String, voiceId: Int): FloatArray
}
