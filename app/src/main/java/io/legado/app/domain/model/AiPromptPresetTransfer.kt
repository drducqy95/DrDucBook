package io.legado.app.domain.model

import androidx.annotation.Keep

/** Portable, secret-free JSON format for AI prompt presets. */
@Keep
data class AiPromptPresetTransferFile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val presets: List<AiPromptPresetTransfer>? = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Keep
data class AiPromptPresetTransfer(
    val taskType: String? = null,
    val name: String? = null,
    val description: String? = null,
    val providerName: String? = null,
    val modelId: String? = null,
    val modelDisplayName: String? = null,
    val promptTemplate: String? = null,
    val params: AiGenerationParams? = null,
    val runtimeOptions: AiTaskRuntimeOptions? = null,
    val enabled: Boolean? = true,
    val makeDefault: Boolean? = false,
    val sortNumber: Int? = 0,
)
