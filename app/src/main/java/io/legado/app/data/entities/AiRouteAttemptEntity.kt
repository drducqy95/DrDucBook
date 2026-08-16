package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_route_attempts",
    indices = [
        Index(value = ["routeProfileId"]),
        Index(value = ["targetId"]),
        Index(value = ["createdAt"]),
    ],
)
data class AiRouteAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeProfileId: String,
    val targetId: String,
    val providerName: String,
    val modelName: String,
    val credentialLabel: String? = null,
    val success: Boolean,
    val failureKind: String? = null,
    val latencyMs: Long,
    val firstEventMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

