package io.legado.app.domain.sourcehealth

import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.BookSourceProbeEvidence

enum class SourceCheckProfile {
    QUICK,
    STANDARD,
    FULL,
}

enum class SourceCheckRunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED,
    INTERRUPTED,
}

enum class SourceCheckStageStatus {
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED,
    CANCELED,
}

data class SourceCheckRun(
    val id: String,
    val sourceUrl: String,
    val sourceName: String,
    val sourceGroup: String?,
    val profile: SourceCheckProfile,
    val status: SourceCheckRunStatus,
    val healthStatus: BookSourceHealthStatus,
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
)

data class SourceCheckStageResult(
    val runId: String,
    val stageKey: String,
    val stageOrder: Int,
    val status: SourceCheckStageStatus,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val latencyMs: Long? = null,
    val httpStatus: Int? = null,
    val failureStep: String? = null,
    val messageRedacted: String? = null,
)

data class SourceCheckProbeResult(
    val profile: SourceCheckProfile,
    val stages: List<SourceCheckStageEvidence>,
)

data class SourceCheckStageEvidence(
    val stageKey: String,
    val stageOrder: Int,
    val status: SourceCheckStageStatus,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val latencyMs: Long? = null,
    val httpStatus: Int? = null,
    val failureStep: String? = null,
    val messageRedacted: String? = null,
)

fun BookSourceProbeEvidence.toSourceCheckStageStatus(): SourceCheckStageStatus =
    if (status in setOf(BookSourceHealthStatus.HEALTHY, BookSourceHealthStatus.DEGRADED)) {
        SourceCheckStageStatus.PASSED
    } else {
        SourceCheckStageStatus.FAILED
    }

fun SourceCheckStageEvidence.toStageResult(runId: String): SourceCheckStageResult =
    SourceCheckStageResult(
        runId = runId,
        stageKey = stageKey,
        stageOrder = stageOrder,
        status = status,
        startedAt = startedAt,
        finishedAt = finishedAt,
        latencyMs = latencyMs,
        httpStatus = httpStatus,
        failureStep = failureStep,
        messageRedacted = messageRedacted,
    )

fun SourceCheckProbeResult.toStageResults(runId: String): List<SourceCheckStageResult> =
    stages.map { it.toStageResult(runId) }
