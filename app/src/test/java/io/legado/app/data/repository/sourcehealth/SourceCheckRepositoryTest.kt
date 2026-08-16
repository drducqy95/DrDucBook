package io.legado.app.data.repository.sourcehealth

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.BookSourceProbeEvidence
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckRetentionPolicy
import io.legado.app.domain.sourcehealth.SourceCheckRun
import io.legado.app.domain.sourcehealth.SourceCheckRunStatus
import io.legado.app.domain.sourcehealth.SourceCheckStageResult
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SourceCheckRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: SourceCheckRepository

    @Before
    fun setUp() {
        val application: Application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SourceCheckRepository(database.sourceCheckDao, database.bookSourceHealthDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun beginAndCompleteRunPersistRunStageAndSummary() = runBlocking {
        val source = source("https://example.com/source", "Example source")

        val begun = repository.beginRun(
            source = source,
            profile = SourceCheckProfile.QUICK,
            startedAt = 1_000L,
        )

        assertEquals(SourceCheckRunStatus.RUNNING, database.sourceCheckDao.getRun(begun.id)?.statusValue)
        assertEquals(
            SourceCheckStageStatus.RUNNING,
            database.sourceCheckDao.getStages(begun.id).single().statusValue,
        )
        assertEquals(listOf(begun.id), repository.observeRunsBySourceUrl(source.bookSourceUrl).first().map { it.id })

        val completed = repository.completeRun(
            run = begun,
            finishedAt = 1_500L,
            stages = listOf(
                SourceCheckStageResult(
                    runId = begun.id,
                    stageKey = "probe",
                    stageOrder = 0,
                    status = SourceCheckStageStatus.PASSED,
                    startedAt = 1_000L,
                    finishedAt = 1_500L,
                    latencyMs = 500L,
                ),
            ),
            evidence = BookSourceProbeEvidence(
                status = BookSourceHealthStatus.HEALTHY,
                latencyMs = 500L,
            ),
        )

        assertEquals(SourceCheckRunStatus.COMPLETED, completed.status)
        assertEquals(SourceCheckRunStatus.COMPLETED, database.sourceCheckDao.getRun(begun.id)?.statusValue)
        assertEquals(
            SourceCheckStageStatus.PASSED,
            database.sourceCheckDao.getStages(begun.id).single().statusValue,
        )
        assertEquals(
            BookSourceHealthStatus.HEALTHY.name,
            database.bookSourceHealthDao.getBySourceUrl(source.bookSourceUrl)?.status,
        )
        assertEquals(
            begun.id,
            repository.observeRunsByStatus(SourceCheckRunStatus.COMPLETED).first().single().id,
        )
        assertTrue(repository.observeRunsByProfile(SourceCheckProfile.QUICK).first().isNotEmpty())
    }

    @Test
    fun markInterruptedLeavesSummaryUntouched() = runBlocking {
        val source = source("https://example.com/interrupted", "Interrupted source")
        val begun = repository.beginRun(
            source = source,
            profile = SourceCheckProfile.QUICK,
            startedAt = 2_000L,
        )

        repository.markInterrupted(begun.id, finishedAt = 2_250L)

        assertEquals(SourceCheckRunStatus.INTERRUPTED, database.sourceCheckDao.getRun(begun.id)?.statusValue)
        assertEquals(
            SourceCheckStageStatus.CANCELED,
            database.sourceCheckDao.getStages(begun.id).single().statusValue,
        )
        assertEquals(SourceCheckRunStatus.INTERRUPTED, repository.observeRunsByStatus(SourceCheckRunStatus.INTERRUPTED).first().single().status)
        assertNull(database.bookSourceHealthDao.getBySourceUrl(source.bookSourceUrl))
    }

    @Test
    fun cleanupKeepsActiveAndLatestRunsWhileApplyingAgeAndCountLimits() = runBlocking {
        val source = source("https://example.com/history", "History source")
        val firstOld = completedRun(source, startedAt = 1_000L)
        completedRun(source, startedAt = 2_000L)
        val recentA = completedRun(source, startedAt = 9_000L)
        val recentB = completedRun(source, startedAt = 9_500L)
        val runningOld = repository.beginRun(
            source = source("https://example.com/running", "Running source"),
            profile = SourceCheckProfile.FULL,
            startedAt = 500L,
        )
        val oldOnly = completedRun(
            source = source("https://example.com/old-only", "Old only"),
            startedAt = 750L,
        )

        val result = repository.cleanup(
            policy = SourceCheckRetentionPolicy(
                maxAgeMillis = 2_000L,
                maxRunsPerSource = 2,
            ),
            now = 10_000L,
        )
        val secondResult = repository.cleanup(
            policy = SourceCheckRetentionPolicy(
                maxAgeMillis = 2_000L,
                maxRunsPerSource = 2,
            ),
            now = 10_000L,
        )

        assertEquals(6, result.inspectedRunCount)
        assertEquals(2, result.deletedRunCount)
        assertEquals(4, result.remainingRunCount)
        assertEquals(0, secondResult.deletedRunCount)
        assertEquals(
            listOf(recentB.id, recentA.id),
            repository.observeRunsBySourceUrl(source.bookSourceUrl).first().map { it.id },
        )
        assertNull(database.sourceCheckDao.getRun(firstOld.id))
        assertEquals(SourceCheckRunStatus.RUNNING, database.sourceCheckDao.getRun(runningOld.id)?.statusValue)
        assertEquals(oldOnly.id, repository.observeRunsBySourceUrl(oldOnly.sourceUrl).first().single().id)
        assertEquals(4, database.sourceCheckDao.getStageCount())
    }

    @Test
    fun sourceDeletionCleanupRemovesRunsStagesAndSummaryForThatSource() = runBlocking {
        val source = source("https://example.com/delete-me", "Delete me")
        val survivor = source("https://example.com/keep-me", "Keep me")
        completedRun(source, startedAt = 1_000L, status = BookSourceHealthStatus.AUTH_REQUIRED)
        completedRun(survivor, startedAt = 1_000L)

        database.bookSourceHealthDao.deleteBySourceUrlBlocking(source.bookSourceUrl)
        database.sourceCheckDao.deleteRunsBySourceUrlBlocking(source.bookSourceUrl)

        assertTrue(repository.observeRunsBySourceUrl(source.bookSourceUrl).first().isEmpty())
        assertNull(database.bookSourceHealthDao.getBySourceUrl(source.bookSourceUrl))
        assertEquals(1, repository.observeRunsBySourceUrl(survivor.bookSourceUrl).first().size)
        assertEquals(1, database.sourceCheckDao.getStageCount())
    }

    private fun source(url: String, name: String): BookSource = BookSource(
        bookSourceUrl = url,
        bookSourceName = name,
        enabled = true,
    )

    private suspend fun completedRun(
        source: BookSource,
        startedAt: Long,
        status: BookSourceHealthStatus = BookSourceHealthStatus.HEALTHY,
    ): SourceCheckRun {
        val begun = repository.beginRun(
            source = source,
            profile = SourceCheckProfile.QUICK,
            startedAt = startedAt,
        )
        return repository.completeRun(
            run = begun,
            finishedAt = startedAt + 100L,
            stages = listOf(
                SourceCheckStageResult(
                    runId = begun.id,
                    stageKey = "probe",
                    stageOrder = 0,
                    status = if (status == BookSourceHealthStatus.HEALTHY) {
                        SourceCheckStageStatus.PASSED
                    } else {
                        SourceCheckStageStatus.FAILED
                    },
                    startedAt = startedAt,
                    finishedAt = startedAt + 100L,
                    latencyMs = 100L,
                ),
            ),
            evidence = BookSourceProbeEvidence(
                status = status,
                latencyMs = 100L,
            ),
        )
    }
}
