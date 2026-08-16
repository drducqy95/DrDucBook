package io.legado.app.data.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "ai_custom_tool_versions",
    foreignKeys = [
        ForeignKey(
            entity = AiCustomTool::class,
            parentColumns = ["id"],
            childColumns = ["toolId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["toolId"]),
        Index(value = ["toolId", "version"], unique = true),
        Index(value = ["toolName"]),
        Index(value = ["lifecycleState"]),
        Index(value = ["createdAt"]),
    ],
)
data class AiCustomToolVersion(
    @PrimaryKey val id: String,
    val toolId: String,
    val toolName: String,
    val version: String,
    val name: String,
    val description: String,
    val manifestJson: String,
    val checksum: String,
    val capabilitiesCsv: String,
    val allowedDomainsJson: String,
    val lifecycleState: String,
    val validationStatus: String,
    val validationMessage: String,
    val testStatus: String,
    val testMessage: String,
    val testOutputJson: String?,
    val fixtureArgumentsJson: String,
    val createdAt: Long,
    val validatedAt: Long?,
    val approvedAt: Long?,
    val testedAt: Long?,
)
