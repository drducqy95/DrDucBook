package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_agent_proposals",
    foreignKeys = [
        ForeignKey(
            entity = AiAgentRun::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["runId"]),
        Index(value = ["conversationId"]),
        Index(value = ["status"]),
        Index(value = ["expiresAt"]),
    ],
)
data class AiAgentProposal(
    @PrimaryKey
    val id: String,
    val runId: String?,
    val conversationId: String?,
    val status: String,
    val toolCount: Int,
    val toolCallsJson: String,
    val proposalHash: String,
    val argsHash: String,
    val createdAt: Long,
    val expiresAt: Long,
    val resolvedAt: Long?,
)
