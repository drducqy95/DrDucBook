package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.sourcehealth.SourceCheckRunEntity
import io.legado.app.data.entities.sourcehealth.SourceCheckStageResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceCheckDao {

    @Query("SELECT * FROM source_check_runs ORDER BY startedAt DESC LIMIT :limit")
    fun observeLatestRuns(limit: Int): Flow<List<SourceCheckRunEntity>>

    @Query("SELECT * FROM source_check_runs WHERE sourceUrl = :sourceUrl ORDER BY startedAt DESC")
    fun observeRunsBySourceUrl(sourceUrl: String): Flow<List<SourceCheckRunEntity>>

    @Query("SELECT * FROM source_check_runs WHERE status = :status ORDER BY startedAt DESC")
    fun observeRunsByStatus(status: String): Flow<List<SourceCheckRunEntity>>

    @Query("SELECT * FROM source_check_runs WHERE profile = :profile ORDER BY startedAt DESC")
    fun observeRunsByProfile(profile: String): Flow<List<SourceCheckRunEntity>>

    @Query("SELECT * FROM source_check_runs WHERE id = :runId LIMIT 1")
    suspend fun getRun(runId: String): SourceCheckRunEntity?

    @Query("SELECT * FROM source_check_runs WHERE sourceUrl = :sourceUrl ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestRunForSource(sourceUrl: String): SourceCheckRunEntity?

    @Query("SELECT * FROM source_check_runs ORDER BY sourceUrl ASC, startedAt DESC")
    suspend fun getRunsForCleanup(): List<SourceCheckRunEntity>

    @Query("SELECT * FROM source_check_stage_results WHERE runId = :runId ORDER BY stageOrder ASC, stageKey ASC")
    fun observeStages(runId: String): Flow<List<SourceCheckStageResultEntity>>

    @Query("SELECT * FROM source_check_stage_results WHERE runId = :runId ORDER BY stageOrder ASC, stageKey ASC")
    suspend fun getStages(runId: String): List<SourceCheckStageResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: SourceCheckRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStages(stages: List<SourceCheckStageResultEntity>)

    @Query(
        """
        UPDATE source_check_runs
        SET
            status = :status,
            healthStatus = :healthStatus,
            finishedAt = :finishedAt,
            latencyMs = :latencyMs,
            httpStatus = :httpStatus,
            failureStep = :failureStep,
            messageRedacted = :messageRedacted,
            stageCount = :stageCount,
            passedStageCount = :passedStageCount,
            failedStageCount = :failedStageCount,
            skippedStageCount = :skippedStageCount
        WHERE id = :runId
        """
    )
    suspend fun finishRun(
        runId: String,
        status: String,
        healthStatus: String,
        finishedAt: Long,
        latencyMs: Long?,
        httpStatus: Int?,
        failureStep: String?,
        messageRedacted: String?,
        stageCount: Int,
        passedStageCount: Int,
        failedStageCount: Int,
        skippedStageCount: Int,
    )

    @Query(
        """
        UPDATE source_check_stage_results
        SET
            status = :status,
            finishedAt = :finishedAt,
            latencyMs = :latencyMs,
            httpStatus = :httpStatus,
            failureStep = :failureStep,
            messageRedacted = :messageRedacted
        WHERE runId = :runId AND stageKey = :stageKey
        """
    )
    suspend fun finishStage(
        runId: String,
        stageKey: String,
        status: String,
        finishedAt: Long,
        latencyMs: Long?,
        httpStatus: Int?,
        failureStep: String?,
        messageRedacted: String?,
    )

    @Query("UPDATE source_check_runs SET status = 'INTERRUPTED', finishedAt = :finishedAt WHERE id = :runId AND status = 'RUNNING'")
    suspend fun markInterrupted(runId: String, finishedAt: Long)

    @Query("UPDATE source_check_stage_results SET status = 'CANCELED', finishedAt = :finishedAt WHERE runId = :runId AND status = 'RUNNING'")
    suspend fun cancelRunningStages(runId: String, finishedAt: Long)

    @Query("DELETE FROM source_check_stage_results WHERE runId = :runId")
    suspend fun deleteStagesForRun(runId: String)

    @Query("DELETE FROM source_check_runs WHERE id = :runId")
    suspend fun deleteRun(runId: String)

    @Query("DELETE FROM source_check_runs WHERE id IN (:runIds)")
    suspend fun deleteRunsByIds(runIds: List<String>): Int

    @Query("DELETE FROM source_check_runs WHERE sourceUrl = :sourceUrl")
    fun deleteRunsBySourceUrlBlocking(sourceUrl: String): Int

    @Query("SELECT COUNT(*) FROM source_check_runs")
    suspend fun getRunCount(): Int

    @Query("SELECT COUNT(*) FROM source_check_stage_results")
    suspend fun getStageCount(): Int

    @Transaction
    suspend fun insertRunWithStages(
        run: SourceCheckRunEntity,
        stages: List<SourceCheckStageResultEntity>,
    ) {
        upsertRun(run)
        if (stages.isNotEmpty()) {
            upsertStages(stages)
        }
    }

    @Transaction
    suspend fun updateRunWithStages(
        runId: String,
        status: String,
        healthStatus: String,
        finishedAt: Long,
        latencyMs: Long?,
        httpStatus: Int?,
        failureStep: String?,
        messageRedacted: String?,
        stageCount: Int,
        passedStageCount: Int,
        failedStageCount: Int,
        skippedStageCount: Int,
        stages: List<SourceCheckStageResultEntity>,
    ) {
        finishRun(
            runId = runId,
            status = status,
            healthStatus = healthStatus,
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
        if (stages.isNotEmpty()) {
            upsertStages(stages)
        }
    }

    @Transaction
    suspend fun markInterruptedWithStages(runId: String, finishedAt: Long) {
        markInterrupted(runId, finishedAt)
        cancelRunningStages(runId, finishedAt)
    }
}
