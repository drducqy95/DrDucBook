package io.legado.app.data.entities.sourcehealth

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckRunStatus

@Entity(
    tableName = "source_check_runs",
    indices = [
        Index(value = ["sourceUrl", "startedAt"]),
        Index(value = ["status", "startedAt"]),
        Index(value = ["profile", "startedAt"]),
        Index(value = ["finishedAt"]),
    ],
)
data class SourceCheckRunEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    val sourceName: String,
    val sourceGroup: String?,
    val profile: String = SourceCheckProfile.QUICK.name,
    val status: String = SourceCheckRunStatus.RUNNING.name,
    val healthStatus: String = BookSourceHealthStatus.UNKNOWN_OFFLINE.name,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val latencyMs: Long? = null,
    val httpStatus: Int? = null,
    val failureStep: String? = null,
    val messageRedacted: String? = null,
    val stageCount: Int = 0,
    val passedStageCount: Int = 0,
    val failedStageCount: Int = 0,
    val skippedStageCount: Int = 0,
) {
    val profileValue: SourceCheckProfile
        get() = runCatching { SourceCheckProfile.valueOf(profile) }
            .getOrDefault(SourceCheckProfile.QUICK)

    val statusValue: SourceCheckRunStatus
        get() = runCatching { SourceCheckRunStatus.valueOf(status) }
            .getOrDefault(SourceCheckRunStatus.RUNNING)

    val healthStatusValue: BookSourceHealthStatus
        get() = runCatching { BookSourceHealthStatus.valueOf(healthStatus) }
            .getOrDefault(BookSourceHealthStatus.UNKNOWN_OFFLINE)
}
