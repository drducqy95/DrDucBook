package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_route_targets",
    indices = [
        Index(value = ["routeProfileId"]),
        Index(value = ["modelProfileId"]),
        Index(value = ["credentialId"]),
    ],
)
data class AiRouteTargetEntity(
    @PrimaryKey
    val id: String,
    val routeProfileId: String,
    val modelProfileId: String,
    val credentialId: String? = null,
    val priority: Int = 0,
    val weight: Int = 1,
    val maxConcurrency: Int = 0,
    val enabled: Boolean = true,
    val sortNumber: Int = 0,
    val cooldownUntil: Long = 0,
    val consecutiveFailures: Int = 0,
    val lastFailureKind: String? = null,
    val lastUsedAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

