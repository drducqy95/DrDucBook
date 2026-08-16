package io.legado.app.data.repository.ai

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.domain.gateway.LocalAiEmptyOutputException
import io.legado.app.domain.gateway.LocalAiUnsupportedAbiException
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import kotlinx.coroutines.flow.collect

class LocalGgufHandler(
    private val engine: LocalAiEngineGateway,
) : AiProtocolHandler {

    override val protocols: Set<String> = setOf(AiProtocol.LOCAL_GGUF)

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        runCatching {
            val text = StringBuilder()
            stream(request) { event ->
                if (event is AiStreamEvent.Content) text.append(event.text)
            }
            AiGenerateResponse(
                text = text.toString().takeIf(String::isNotBlank)
                    ?: throw LocalAiEmptyOutputException()
            )
        }

    override suspend fun stream(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit,
    ) {
        val modelPath = request.model.provider.localModelPath()
        engine.generateStream(modelPath, request).collect(emitEvent)
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        if (!engine.nativeRuntimeAvailable) {
            Result.failure(LocalAiUnsupportedAbiException())
        } else engine.inspectModel(provider.localModelPath()).map { metadata ->
            listOf(
                AiAvailableModel(
                    id = metadata.path,
                    name = metadata.name,
                    contextWindow = metadata.contextWindow,
                    maxOutputTokens = metadata.contextWindow,
                )
            )
        }
}

internal fun AiProviderConfig.localModelPath(): String {
    return baseUrl.removePrefix("file://").trim().ifBlank {
        throw IllegalArgumentException("Choose a local GGUF model file")
    }
}
