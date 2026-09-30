package io.legado.app.domain.model

import androidx.annotation.Keep

object AiCapabilityStatus {
    const val UNKNOWN = "unknown"
    const val PROBING = "probing"
    const val AVAILABLE = "available"
    const val DEGRADED = "degraded"
    const val COOLDOWN = "cooldown"
    const val AUTH_FAILED = "auth_failed"
    const val MODEL_UNAVAILABLE = "model_unavailable"
    const val CONTRACT_UNSUPPORTED = "contract_unsupported"
    const val QUOTA_EXHAUSTED = "quota_exhausted"
    const val STALE = "stale"

    fun isUsable(status: String, now: Long, cooldownUntil: Long): Boolean =
        cooldownUntil <= now && (status == AVAILABLE || status == DEGRADED)
}

object AiOutputContract {
    const val CHAT_TEXT = "chat_text"
    const val TRANSLATION_JSON = "translation_json"
    const val REWRITE_TEXT = "rewrite_text"
    const val AGENT_TOOL_CALL = "agent_tool_call"
    const val SUMMARY_TEXT = "summary_text"
    const val PLAIN_TEXT = "plain_text"

    fun forTask(taskType: String?, hasTools: Boolean = false): String = when {
        hasTools -> AGENT_TOOL_CALL
        taskType == AiTaskType.TRANSLATE_CHAPTER -> TRANSLATION_JSON
        taskType == AiTaskType.REWRITE_TEXT -> REWRITE_TEXT
        taskType == AiTaskType.SUMMARIZE_CHAPTER || taskType == AiTaskType.SUMMARIZE_BOOK -> SUMMARY_TEXT
        taskType == AiTaskType.CHAT -> CHAT_TEXT
        else -> PLAIN_TEXT
    }

    /**
     * Some authoring/text-factory callers intentionally provide a stable task-specific schema
     * instruction instead of one of the built-in labels. Keep the strict contracts for chat,
     * translation and rewrite, while allowing those explicit custom contracts for text tasks.
     */
    fun isCompatibleWithTask(
        taskType: String,
        outputContract: String,
        hasTools: Boolean,
    ): Boolean {
        if (hasTools) return outputContract == AGENT_TOOL_CALL
        return when (taskType) {
            AiTaskType.CHAT -> outputContract == CHAT_TEXT
            AiTaskType.TRANSLATE_CHAPTER -> outputContract == TRANSLATION_JSON
            AiTaskType.REWRITE_TEXT -> outputContract == REWRITE_TEXT
            else -> outputContract == forTask(taskType) || outputContract.isNotBlank()
        }
    }
}

@Keep
data class AiCredentialModelCapabilityConfig(
    val credentialId: String,
    val modelProfileId: String,
    val taskType: String,
    val outputContract: String,
    val status: String,
    val lastProbeAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val cooldownUntil: Long = 0L,
    val failureKind: String? = null,
    val failureMessage: String? = null,
    val latencyMs: Long? = null,
    val probeRevision: Long = 0L,
)

fun io.legado.app.data.entities.AiCredentialModelCapabilityEntity.toConfig() =
    AiCredentialModelCapabilityConfig(
        credentialId = credentialId,
        modelProfileId = modelProfileId,
        taskType = taskType,
        outputContract = outputContract,
        status = status,
        lastProbeAt = lastProbeAt,
        lastSuccessAt = lastSuccessAt,
        lastFailureAt = lastFailureAt,
        cooldownUntil = cooldownUntil,
        failureKind = failureKind,
        failureMessage = failureMessage,
        latencyMs = latencyMs,
        probeRevision = probeRevision,
    )
