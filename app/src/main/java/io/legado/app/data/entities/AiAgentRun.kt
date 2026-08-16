package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_agent_runs",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["status"]),
        Index(value = ["startedAt"]),
    ]
)
data class AiAgentRun(
    @PrimaryKey
    val id: String,
    val conversationId: String?,
    val status: String,
    val taskType: String?,
    val providerId: String,
    val modelId: String,
    val finalTextPreview: String,
    val errorMessage: String?,
    val pendingProposalId: String?,
    val startedAt: Long,
    val updatedAt: Long,
    val traceCount: Int,
    val toolResultCount: Int,
)
