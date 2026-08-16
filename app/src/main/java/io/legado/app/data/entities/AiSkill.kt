package io.legado.app.data.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "ai_skills",
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["enabled"]),
        Index(value = ["updatedAt"]),
    ],
)
data class AiSkill(
    @PrimaryKey val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val enabled: Boolean = false,
    val activeVersionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
