package io.legado.app.domain.gateway

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.LocalAiRuntimeProfile
import kotlinx.coroutines.flow.Flow

interface LocalAiEngineGateway {
    val nativeRuntimeAvailable: Boolean

    fun generateStream(
        modelPath: String,
        request: AiGenerateRequest,
    ): Flow<AiStreamEvent>

    suspend fun inspectModel(modelPath: String): Result<LocalAiModelMetadata>

    suspend fun importModel(sourceUri: String): Result<LocalAiModelMetadata>

    suspend fun unload()
}

class LocalAiEmptyOutputException : IllegalStateException(
    "Local GGUF model returned empty output. Check the model template and output token limit."
)

class LocalAiUnsupportedAbiException : IllegalStateException(
    "The local AI native runtime is not available for this device ABI."
)

data class LocalAiModelMetadata(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val contextWindow: Int,
    val runtimeProfile: LocalAiRuntimeProfile,
    val sha256: String = "",
    val primaryAbi: String = "",
    val totalMemoryMb: Long = 0,
)
