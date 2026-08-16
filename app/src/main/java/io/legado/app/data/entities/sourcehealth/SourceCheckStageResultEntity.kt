package io.legado.app.data.entities.sourcehealth

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus

@Entity(
    tableName = "source_check_stage_results",
    primaryKeys = ["runId", "stageKey"],
    foreignKeys = [
        ForeignKey(
            entity = SourceCheckRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["runId"]),
        Index(value = ["status"]),
        Index(value = ["stageOrder"]),
    ],
)
data class SourceCheckStageResultEntity(
    val runId: String,
    val stageKey: String,
    val stageOrder: Int,
    val status: String = SourceCheckStageStatus.RUNNING.name,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val latencyMs: Long? = null,
    val httpStatus: Int? = null,
    val failureStep: String? = null,
    val messageRedacted: String? = null,
) {
    val statusValue: SourceCheckStageStatus
        get() = runCatching { SourceCheckStageStatus.valueOf(status) }
            .getOrDefault(SourceCheckStageStatus.RUNNING)
}
