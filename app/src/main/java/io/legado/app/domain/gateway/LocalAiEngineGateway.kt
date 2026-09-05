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

    suspend fun validateModel(modelPath: String): Result<LocalAiModelMetadata>

    suspend fun importModel(
        sourceUri: String,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<LocalAiModelMetadata>

    suspend fun unload()
}

class LocalAiEmptyOutputException : IllegalStateException(
    "Local GGUF model returned empty output. Check the model template and output token limit."
)

class LocalAiUnsupportedAbiException(
    message: String = "The local AI native runtime is not available for this device ABI (requires 64-bit arm64-v8a or x86_64)."
) : IllegalStateException(message)

class LocalAiOutOfMemoryException(
    message: String = "Không đủ bộ nhớ RAM khả dụng để nạp model Local AI. Vui lòng đóng bớt ứng dụng chạy ngầm và thử lại."
) : IllegalStateException(message)

class LocalAiCorruptedModelException(
    message: String = "Tệp model GGUF bị lỗi hoặc không đầy đủ. Vui lòng tải lại tệp model."
) : IllegalStateException(message)

data class LocalAiModelMetadata(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val contextWindow: Int,
    val runtimeProfile: LocalAiRuntimeProfile,
    val sha256: String = "",
    val primaryAbi: String = "",
    val totalMemoryMb: Long = 0,
    val ggufVersion: Int = 0,
)
