package io.legado.app.domain.gateway

import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiTranslationChunkContext
import io.legado.app.domain.model.DictPair

data class LocalAiTranslationResult(
    val text: String,
    val generatedTokens: Int = 0,
    val promptTokens: Int = 0,
    val modelName: String = "",
)

interface LocalAiTranslationGateway {
    val isAvailable: Boolean
    val loadedModelName: String?

    suspend fun translate(
        text: String,
        targetLanguage: String,
        context: AiTranslationChunkContext = AiTranslationChunkContext(),
        dictionary: List<DictPair> = emptyList(),
        configuredPrompt: String = "",
        retryInstruction: String = "",
        params: AiGenerationParams? = null,
        onToken: ((String) -> Unit)? = null,
    ): LocalAiTranslationResult

    suspend fun rewrite(
        text: String,
        instruction: String,
        params: AiGenerationParams? = null,
        onToken: ((String) -> Unit)? = null,
    ): LocalAiTranslationResult

    suspend fun unload()
}
