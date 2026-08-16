package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "ai_agent_trace",
    primaryKeys = ["runId", "stepIndex"],
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
        Index(value = ["type"]),
    ],
)
data class AiAgentTrace(
    val runId: String,
    val stepIndex: Int,
    val type: String,
    val content: String,
    val toolName: String?,
    val callId: String?,
)
