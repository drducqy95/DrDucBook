package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Runtime capability of one credential for one model and one output contract.
 * The model catalog is provider-wide; this table is intentionally credential-scoped.
 */
@Entity(
    tableName = "ai_credential_model_capabilities",
    primaryKeys = ["credentialId", "modelProfileId", "taskType", "outputContract"],
    indices = [
        Index(value = ["credentialId"]),
        Index(value = ["modelProfileId"]),
        Index(value = ["taskType", "outputContract", "status"]),
        Index(value = ["cooldownUntil"]),
    ],
)
data class AiCredentialModelCapabilityEntity(
    val credentialId: String,
    val modelProfileId: String,
    val taskType: String,
    val outputContract: String,
    val status: String = "unknown",
    val lastProbeAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val cooldownUntil: Long = 0L,
    val failureKind: String? = null,
    val failureMessage: String? = null,
    val latencyMs: Long? = null,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val capabilitiesJson: String? = null,
    val providerFingerprint: String? = null,
    val probeRevision: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
