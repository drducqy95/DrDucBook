package io.legado.app.data.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "ai_skill_versions",
    foreignKeys = [
        ForeignKey(
            entity = AiSkill::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["skillId"]),
        Index(value = ["skillId", "version"], unique = true),
        Index(value = ["createdAt"]),
    ],
)
data class AiSkillVersion(
    @PrimaryKey val id: String,
    val skillId: String,
    val version: String,
    val name: String,
    val description: String,
    val manifestJson: String,
    val skillMarkdown: String,
    val allowedToolsJson: String,
    val requirementsJson: String,
    val validationStatus: String,
    val validationMessage: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_VALID = "VALID"
        const val STATUS_INVALID = "INVALID"
    }
}
