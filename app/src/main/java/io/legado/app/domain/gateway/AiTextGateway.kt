package io.legado.app.domain.gateway

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiProviderConfig
import kotlinx.coroutines.flow.Flow

sealed interface AiStreamEvent {
    data class Content(val text: String) : AiStreamEvent
    data class Reasoning(val text: String) : AiStreamEvent
    data class ToolCallDelta(
        val id: String?,
        val index: Int?,
        val name: String?,
        val argumentsDelta: String?,
        val rawType: String,
        val metadata: String? = null,
    ) : AiStreamEvent
    data class Citation(
        val startIndex: Int? = null,
        val endIndex: Int? = null,
        val uri: String,
        val title: String = "",
        val snippet: String = ""
    ) : AiStreamEvent
    data class Usage(
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val totalTokens: Int = 0,
        val reasoningTokens: Int = 0
    ) : AiStreamEvent
}

interface AiTextGateway {
    suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse>
    fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent>
    suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>>
}
