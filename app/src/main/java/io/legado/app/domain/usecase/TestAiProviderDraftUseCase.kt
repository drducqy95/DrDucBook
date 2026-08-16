package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiConnectionStatus
import io.legado.app.domain.model.AiConnectionTestResult
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderConnectionDraft
import io.legado.app.domain.model.AiModelRegistry
import java.time.Clock

class TestAiProviderDraftUseCase(
    private val aiTextGateway: AiTextGateway,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend operator fun invoke(draft: AiProviderConnectionDraft): AiConnectionTestResult {
        validate(draft)?.let { return it }

        val provider = draft.toProviderConfig()
        val discoveredModels = if (draft.modelsUrl.isNullOrBlank()) {
            emptyList()
        } else {
            aiTextGateway.fetchModels(provider).getOrElse { error ->
                return AiConnectionTestResult(
                    status = AiConnectionStatus.ERROR,
                    message = error.message ?: "Không thể lấy danh sách model từ provider",
                )
            }
        }
        val selectedModel = selectModel(draft, discoveredModels)
            ?: return AiConnectionTestResult(
                status = AiConnectionStatus.ERROR,
                message = "Cần chọn model",
                discoveredModels = discoveredModels,
            )
        val inferredCapabilities = AiModelRegistry.inferCapabilities(
            "${selectedModel.id} ${selectedModel.name}"
        )
        val requestedProbeTokens = when {
            AiCapability.REASONING in inferredCapabilities -> REASONING_PROBE_OUTPUT_TOKENS
            selectedModel.id.equals(PREFERRED_GATEWAY_PROBE_MODEL, ignoreCase = true) ->
                GATEWAY_PROBE_OUTPUT_TOKENS
            else -> STANDARD_PROBE_OUTPUT_TOKENS
        }
        val probeOutputTokens = selectedModel.maxOutputTokens
            .takeIf { it > 0 }
            ?.let(requestedProbeTokens::coerceAtMost)
            ?: requestedProbeTokens

        val request = AiGenerateRequest(
            model = AiModelConfig(
                id = "draft:${draft.catalogId}:${selectedModel.id}",
                provider = provider,
                displayName = selectedModel.name,
                modelId = selectedModel.id,
                contextWindow = selectedModel.contextWindow,
                maxOutputTokens = selectedModel.maxOutputTokens,
                capabilities = inferredCapabilities,
            ),
            messages = listOf(
                AiMessage(
                    role = "user",
                    content = "Reply with OK.",
                )
            ),
            params = AiGenerationParams(
                temperature = 0f,
                maxOutputTokens = probeOutputTokens,
            ),
        )

        val startedAt = clock.millis()
        return aiTextGateway.generate(request).fold(
            onSuccess = { response ->
                val latencyMs = (clock.millis() - startedAt).coerceAtLeast(0)
                if (response.text.isBlank()) {
                    AiConnectionTestResult(
                        status = AiConnectionStatus.ERROR,
                        message = "Provider trả về nội dung trống",
                        discoveredModels = discoveredModels,
                        selectedModel = selectedModel,
                        latencyMs = latencyMs,
                    )
                } else {
                    AiConnectionTestResult(
                        status = AiConnectionStatus.READY,
                        message = "Kết nối thành công",
                        discoveredModels = discoveredModels,
                        selectedModel = selectedModel,
                        latencyMs = latencyMs,
                    )
                }
            },
            onFailure = { error ->
                AiConnectionTestResult(
                    status = AiConnectionStatus.ERROR,
                    message = error.message ?: "Kiểm tra kết nối thất bại",
                    discoveredModels = discoveredModels,
                    selectedModel = selectedModel,
                    latencyMs = (clock.millis() - startedAt).coerceAtLeast(0),
                )
            },
        )
    }

    private fun validate(draft: AiProviderConnectionDraft): AiConnectionTestResult? {
        if (draft.providerName.isBlank()) {
            return AiConnectionTestResult(
                status = AiConnectionStatus.ERROR,
                message = "Cần nhập tên provider",
            )
        }
        if (draft.baseUrl.isBlank()) {
            return AiConnectionTestResult(
                status = AiConnectionStatus.ERROR,
                message = "Cần nhập Base URL",
            )
        }
        val requiresSecret = draft.authType != AiProviderAuthType.NONE
        if (requiresSecret && draft.apiKey.isBlank() && !draft.hasStoredSecret) {
            return AiConnectionTestResult(
                status = AiConnectionStatus.ERROR,
                message = "Cần nhập API key hoặc token",
            )
        }
        return null
    }

    private fun selectModel(
        draft: AiProviderConnectionDraft,
        discoveredModels: List<AiAvailableModel>,
    ): AiAvailableModel? {
        val modelId = draft.modelId.takeIf(String::isNotBlank)
        val discovered = discoveredModels.firstOrNull { it.id == modelId }
            ?: preferredProbeModel(discoveredModels)
        return when {
            modelId != null -> discovered ?: AiAvailableModel(
                id = modelId,
                name = draft.modelName.ifBlank { modelId },
                contextWindow = draft.contextWindow,
                maxOutputTokens = draft.maxOutputTokens,
            )
            else -> discovered
        }
    }

    private fun preferredProbeModel(
        discoveredModels: List<AiAvailableModel>,
    ): AiAvailableModel? =
        discoveredModels.firstOrNull { model ->
            model.id.equals(PREFERRED_GATEWAY_PROBE_MODEL, ignoreCase = true)
        } ?: discoveredModels.firstOrNull { model ->
            model.id.startsWith("oc/", ignoreCase = true) && model.isFreeModel()
        } ?: discoveredModels.firstOrNull { model -> model.isFreeModel() }
            ?: discoveredModels.firstOrNull()

    private fun AiAvailableModel.isFreeModel(): Boolean =
        id.contains("free", ignoreCase = true) || name.contains("free", ignoreCase = true)

    private fun AiProviderConnectionDraft.toProviderConfig(): AiProviderConfig =
        AiProviderConfig(
            id = providerProfileId ?: "draft:$catalogId",
            name = providerName,
            protocol = protocol,
            baseUrl = baseUrl,
            apiKey = apiKey,
            authType = authType,
            modelsUrl = modelsUrl,
            headers = headers,
            chatPath = chatPath,
            responsesPath = responsesPath,
            messagesPath = messagesPath,
            modelsPath = modelsPath,
            customHeaders = customHeaders,
        )

    private companion object {
        const val STANDARD_PROBE_OUTPUT_TOKENS = 64
        const val GATEWAY_PROBE_OUTPUT_TOKENS = 256
        const val REASONING_PROBE_OUTPUT_TOKENS = 1_024
        const val PREFERRED_GATEWAY_PROBE_MODEL = "oc/mimo-v2.5-free"
    }
}
