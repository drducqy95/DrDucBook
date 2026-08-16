package io.legado.app.domain.gateway

import io.legado.app.domain.model.AiImageGenerateRequest
import io.legado.app.domain.model.AiImageGenerateResult

interface AiImageGateway {
    suspend fun generate(request: AiImageGenerateRequest): AiImageGenerateResult
}
