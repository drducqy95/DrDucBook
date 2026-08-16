package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_route_profiles",
    indices = [Index(value = ["taskType"])],
)
data class AiRouteProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val taskType: String,
    val strategy: String,
    val maxAttempts: Int = 3,
    val stickySession: Boolean = true,
    val enabled: Boolean = true,
    val isDefault: Boolean = true,
    val sortNumber: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

