package io.legado.app.data.repository.sourcehealth

import io.legado.app.data.dao.BookSourceHealthDao
import io.legado.app.data.dao.SourceCheckDao
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourceHealth
import io.legado.app.data.entities.sourcehealth.SourceCheckRunEntity
import io.legado.app.data.entities.sourcehealth.SourceCheckStageResultEntity
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.BookSourceProbeEvidence
import io.legado.app.domain.model.nextBookSourceFailureCount
import io.legado.app.domain.sourcehealth.SourceCheckCleanupResult
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckRetentionPolicy
import io.legado.app.domain.sourcehealth.SourceCheckRun
import io.legado.app.domain.sourcehealth.SourceCheckRunStatus
import io.legado.app.domain.sourcehealth.SourceCheckStageResult
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import io.legado.app.domain.sourcehealth.toSourceCheckStageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class SourceCheckRepository(
    private val sourceCheckDao: SourceCheckDao,
    private val bookSourceHealthDao: BookSourceHealthDao,
) {

    fun observeLatestRuns(limit: Int): Flow<List<SourceCheckRun>> =
        sourceCheckDao.observeLatestRuns(limit).map { rows -> rows.map { it.toDomain() } }

    fun observeRunsBySourceUrl(sourceUrl: String): Flow<List<SourceCheckRun>> =
        sourceCheckDao.observeRunsBySourceUrl(sourceUrl).map { rows -> rows.map { it.toDomain() } }

    fun observeRunsByStatus(status: SourceCheckRunStatus): Flow<List<SourceCheckRun>> =
        sourceCheckDao.observeRunsByStatus(status.name).map { rows -> rows.map { it.toDomain() } }

    fun observeRunsByProfile(profile: SourceCheckProfile): Flow<List<SourceCheckRun>> =
        sourceCheckDao.observeRunsByProfile(profile.name).map { rows -> rows.map { it.toDomain() } }

    fun observeStages(runId: String): Flow<List<SourceCheckStageResult>> =
        sourceCheckDao.observeStages(runId).map { rows -> rows.map { it.toDomain() } }

    suspend fun beginRun(
        source: BookSource,
        profile: SourceCheckProfile,
        startedAt: Long,
        stageKey: String = DEFAULT_STAGE_KEY,
        stageOrder: Int = DEFAULT_STAGE_ORDER,
        createInitialStage: Boolean = true,
    ): SourceCheckRun = beginRun(
        sourceUrl = source.bookSourceUrl,
        sourceName = source.bookSourceName,
        sourceGroup = source.bookSourceGroup,
        profile = profile,
        startedAt = startedAt,
        stageKey = stageKey,
        stageOrder = stageOrder,
        createInitialStage = createInitialStage,
    )

    suspend fun beginRun(
        sourceUrl: String,
        sourceName: String,
        sourceGroup: String?,
        profile: SourceCheckProfile,
        startedAt: Long,
        stageKey: String = DEFAULT_STAGE_KEY,
        stageOrder: Int = DEFAULT_STAGE_ORDER,
        createInitialStage: Boolean = true,
    ): SourceCheckRun {
        val run = SourceCheckRun(
            id = UUID.randomUUID().toString(),
            sourceUrl = sourceUrl,
            sourceName = sourceName,
            sourceGroup = sourceGroup,
            profile = profile,
            status = SourceCheckRunStatus.RUNNING,
            healthStatus = BookSourceHealthStatus.UNKNOWN_OFFLINE,
            startedAt = startedAt,
        )
        val stage = SourceCheckStageResult(
            runId = run.id,
            stageKey = stageKey,
            stageOrder = stageOrder,
            status = SourceCheckStageStatus.RUNNING,
            startedAt = startedAt,
        )
        sourceCheckDao.insertRunWithStages(
            run.toEntity(),
            if (createInitialStage) listOf(stage.toEntity()) else emptyList(),
        )
        return run
    }

    suspend fun completeRun(
        run: SourceCheckRun,
        finishedAt: Long,
        stages: List<SourceCheckStageResult>,
        evidence: BookSourceProbeEvidence? = null,
        runStatus: SourceCheckRunStatus = SourceCheckRunStatus.COMPLETED,
        persistSummary: Boolean = evidence != null,
    ): SourceCheckRun {
        val finalStages = if (stages.isEmpty()) {
            listOf(
                SourceCheckStageResult(
                    runId = run.id,
                    stageKey = DEFAULT_STAGE_KEY,
                    stageOrder = DEFAULT_STAGE_ORDER,
                    status = when (runStatus) {
                        SourceCheckRunStatus.CANCELED -> SourceCheckStageStatus.CANCELED
                        SourceCheckRunStatus.INTERRUPTED -> SourceCheckStageStatus.CANCELED
                        else -> evidence?.toSourceCheckStageStatus() ?: SourceCheckStageStatus.FAILED
                    },
                    startedAt = run.startedAt,
                    finishedAt = finishedAt,
                    latencyMs = evidence?.latencyMs,
                    httpStatus = evidence?.httpStatus,
                    failureStep = evidence?.failureStep,
                    messageRedacted = evidence?.messageRedacted ?: run.messageRedacted,
                )
            )
        } else {
            stages.map { stage ->
                if (stage.status == SourceCheckStageStatus.RUNNING) {
                    stage.copy(
                        status = when (runStatus) {
                            SourceCheckRunStatus.CANCELED -> SourceCheckStageStatus.CANCELED
                            SourceCheckRunStatus.INTERRUPTED -> SourceCheckStageStatus.CANCELED
                            else -> evidence?.toSourceCheckStageStatus() ?: SourceCheckStageStatus.FAILED
                        },
                        finishedAt = finishedAt,
                        latencyMs = evidence?.latencyMs,
                        httpStatus = evidence?.httpStatus,
                        failureStep = evidence?.failureStep,
                        messageRedacted = evidence?.messageRedacted ?: stage.messageRedacted,
                    )
                } else {
                    stage.copy(
                        finishedAt = stage.finishedAt ?: finishedAt,
                        latencyMs = stage.latencyMs ?: evidence?.latencyMs,
                        httpStatus = stage.httpStatus ?: evidence?.httpStatus,
                        failureStep = stage.failureStep ?: evidence?.failureStep,
                        messageRedacted = stage.messageRedacted ?: evidence?.messageRedacted,
                    )
                }
            }
        }
        val stageCount = finalStages.size
        val passedStageCount = finalStages.count { it.status == SourceCheckStageStatus.PASSED }
        val failedStageCount = finalStages.count { it.status == SourceCheckStageStatus.FAILED }
        val skippedStageCount = finalStages.count { it.status == SourceCheckStageStatus.SKIPPED }
        val finalRun = run.copy(
            status = runStatus,
            healthStatus = evidence?.status ?: run.healthStatus,
            finishedAt = finishedAt,
            latencyMs = if (runStatus == SourceCheckRunStatus.RUNNING) null else (finishedAt - run.startedAt).coerceAtLeast(0L),
            httpStatus = evidence?.httpStatus,
            failureStep = evidence?.failureStep,
            messageRedacted = evidence?.messageRedacted ?: run.messageRedacted,
            stageCount = stageCount,
            passedStageCount = passedStageCount,
            failedStageCount = failedStageCount,
            skippedStageCount = skippedStageCount,
        )
        sourceCheckDao.updateRunWithStages(
            runId = finalRun.id,
            status = finalRun.status.name,
            healthStatus = finalRun.healthStatus.name,
            finishedAt = finishedAt,
            latencyMs = finalRun.latencyMs,
            httpStatus = finalRun.httpStatus,
            failureStep = finalRun.failureStep,
            messageRedacted = finalRun.messageRedacted,
            stageCount = finalRun.stageCount,
            passedStageCount = finalRun.passedStageCount,
            failedStageCount = finalRun.failedStageCount,
            skippedStageCount = finalRun.skippedStageCount,
            stages = finalStages.map { it.toEntity() },
        )
        if (persistSummary && evidence != null) {
            val previous = bookSourceHealthDao.getBySourceUrl(finalRun.sourceUrl)
            bookSourceHealthDao.upsert(
                BookSourceHealth(
                    sourceUrl = finalRun.sourceUrl,
                    status = evidence.status.name,
                    lastChecked = finishedAt,
                    latencyMs = evidence.latencyMs,
                    httpStatus = evidence.httpStatus,
                    failureStep = evidence.failureStep,
                    messageRedacted = evidence.messageRedacted,
                    consecutiveFailures = nextBookSourceFailureCount(
                        previousFailures = previous?.consecutiveFailures ?: 0,
                        status = evidence.status,
                    ),
                )
            )
        }
        return finalRun
    }

    suspend fun markInterrupted(runId: String, finishedAt: Long) {
        sourceCheckDao.markInterruptedWithStages(runId, finishedAt)
    }

    suspend fun cleanup(
        policy: SourceCheckRetentionPolicy = SourceCheckRetentionPolicy(),
        now: Long = System.currentTimeMillis(),
    ): SourceCheckCleanupResult {
        val runs = sourceCheckDao.getRunsForCleanup()
        if (runs.isEmpty()) {
            return SourceCheckCleanupResult(
                inspectedRunCount = 0,
                deletedRunCount = 0,
                remainingRunCount = 0,
            )
        }
        val cutoff = now - policy.maxAgeMillis
        val deleteIds = runs
            .groupBy { it.sourceUrl }
            .values
            .flatMap { sourceRuns ->
                sourceRuns
                    .sortedByDescending { it.startedAt }
                    .idsToDelete(policy, cutoff)
            }
        val deletedCount = if (deleteIds.isEmpty()) {
            0
        } else {
            sourceCheckDao.deleteRunsByIds(deleteIds)
        }
        return SourceCheckCleanupResult(
            inspectedRunCount = runs.size,
            deletedRunCount = deletedCount,
            remainingRunCount = runs.size - deletedCount,
        )
    }

    private fun SourceCheckRunEntity.toDomain(): SourceCheckRun = SourceCheckRun(
        id = id,
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        sourceGroup = sourceGroup,
        profile = profileValue,
        status = statusValue,
        healthStatus = healthStatusValue,
        startedAt = startedAt,
        finishedAt = finishedAt,
        latencyMs = latencyMs,
        httpStatus = httpStatus,
        failureStep = failureStep,
        messageRedacted = messageRedacted,
        stageCount = stageCount,
        passedStageCount = passedStageCount,
        failedStageCount = failedStageCount,
        skippedStageCount = skippedStageCount,
    )

    private fun SourceCheckStageResultEntity.toDomain(): SourceCheckStageResult = SourceCheckStageResult(
        runId = runId,
        stageKey = stageKey,
        stageOrder = stageOrder,
        status = statusValue,
        startedAt = startedAt,
        finishedAt = finishedAt,
        latencyMs = latencyMs,
        httpStatus = httpStatus,
        failureStep = failureStep,
        messageRedacted = messageRedacted,
    )

    private fun SourceCheckRun.toEntity(): SourceCheckRunEntity = SourceCheckRunEntity(
        id = id,
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        sourceGroup = sourceGroup,
        profile = profile.name,
        status = status.name,
        healthStatus = healthStatus.name,
        startedAt = startedAt,
        finishedAt = finishedAt,
        latencyMs = latencyMs,
        httpStatus = httpStatus,
        failureStep = failureStep,
        messageRedacted = messageRedacted,
        stageCount = stageCount,
        passedStageCount = passedStageCount,
        failedStageCount = failedStageCount,
        skippedStageCount = skippedStageCount,
    )

    private fun SourceCheckStageResult.toEntity(): SourceCheckStageResultEntity =
        SourceCheckStageResultEntity(
            runId = runId,
            stageKey = stageKey,
            stageOrder = stageOrder,
            status = status.name,
            startedAt = startedAt,
            finishedAt = finishedAt,
            latencyMs = latencyMs,
            httpStatus = httpStatus,
            failureStep = failureStep,
            messageRedacted = messageRedacted,
        )

    private companion object {
        const val DEFAULT_STAGE_KEY = "probe"
        const val DEFAULT_STAGE_ORDER = 0
    }
}

private fun List<SourceCheckRunEntity>.idsToDelete(
    policy: SourceCheckRetentionPolicy,
    cutoff: Long,
): List<String> {
    var retainedFinishedRuns = 0
    return mapNotNull { run ->
        if (run.statusValue == SourceCheckRunStatus.RUNNING) {
            return@mapNotNull null
        }
        val keepLatestForSource = retainedFinishedRuns == 0
        val withinAge = run.startedAt >= cutoff
        val withinCount = retainedFinishedRuns < policy.maxRunsPerSource
        if (keepLatestForSource || (withinAge && withinCount)) {
            retainedFinishedRuns += 1
            null
        } else {
            run.id
        }
    }
}
