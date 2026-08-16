package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_credentials",
    indices = [Index(value = ["providerId"])],
)
data class AiCredentialEntity(
    @PrimaryKey
    val id: String,
    val providerId: String,
    val label: String,
    val kind: String,
    val secretRef: String,
    val enabled: Boolean = true,
    val sortNumber: Int = 0,
    val cooldownUntil: Long = 0,
    val consecutiveFailures: Int = 0,
    val lastFailureKind: String? = null,
    val lastUsedAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val oauthProvider: String? = null,
    val refreshTokenRef: String? = null,
    val idTokenRef: String? = null,
    val accountId: String? = null,
    val accountLabel: String? = null,
    val expiresAt: Long? = null,
    val scopes: String? = null,
    val status: String = "active",
    val providerDataJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
