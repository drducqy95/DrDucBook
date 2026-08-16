package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_agent_audits",
    foreignKeys = [
        ForeignKey(
            entity = AiAgentRun::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AiAgentProposal::class,
            parentColumns = ["id"],
            childColumns = ["proposalId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["runId"]),
        Index(value = ["proposalId"]),
        Index(value = ["conversationId"]),
        Index(value = ["toolName"]),
        Index(value = ["status"]),
        Index(value = ["startedAt"]),
    ],
)
data class AiAgentAudit(
    @PrimaryKey
    val id: String,
    val runId: String?,
    val proposalId: String?,
    val conversationId: String?,
    val callId: String,
    val toolName: String,
    val risk: String,
    val capabilitiesCsv: String,
    val approvalScope: String,
    val status: String,
    val requestPreview: String,
    val resultPreview: String?,
    val errorMessage: String?,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMs: Long,
)
