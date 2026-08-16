package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.LocalTtsModelGateway
import io.legado.app.domain.model.LocalTtsModelTestResult

class TestLocalTtsModelUseCase(
    private val gateway: LocalTtsModelGateway,
) {
    suspend operator fun invoke(
        modelId: String,
        voiceId: Int,
        testPhrase: String = "Xin chào, đây là bản kiểm tra giọng đọc trên thiết bị.",
    ): LocalTtsModelTestResult {
        if (testPhrase.isBlank()) {
            return LocalTtsModelTestResult(false, message = "Câu kiểm tra đang trống")
        }
        return gateway.testModel(modelId, voiceId, testPhrase)
    }
}
