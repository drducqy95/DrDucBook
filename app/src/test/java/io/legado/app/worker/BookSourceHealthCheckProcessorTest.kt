package io.legado.app.worker

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.domain.gateway.BookSourceHealthProbeGateway
import io.legado.app.domain.gateway.RssSourceHealthProbeGateway
import io.legado.app.domain.gateway.VbookSourceHealthProbeGateway
import io.legado.app.data.repository.sourcehealth.SourceCheckEngine
import io.legado.app.data.repository.sourcehealth.SourceCheckRepository
import io.legado.app.domain.sourcehealth.SourceCheckProbeResult
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckStageEvidence
import io.legado.app.domain.sourcehealth.SourceCheckRunStatus
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
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
class BookSourceHealthCheckProcessorTest {

    private lateinit var database: AppDatabase
    private lateinit var probeGateway: RecordingBookSourceHealthProbeGateway
    private lateinit var processor: BookSourceHealthCheckProcessor

    @Before
    fun setUp() {
        val application: Application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        probeGateway = RecordingBookSourceHealthProbeGateway()
        val sourceCheckRepository = SourceCheckRepository(
            database.sourceCheckDao,
            database.bookSourceHealthDao,
        )
        processor = BookSourceHealthCheckProcessor(
            bookSourceDao = database.bookSourceDao,
            sourceCheckEngine = SourceCheckEngine(
                sourceCheckRepository = sourceCheckRepository,
                bookSourceHealthProbeGateway = probeGateway,
                rssSourceHealthProbeGateway = NoopRssSourceHealthProbeGateway,
                vbookSourceHealthProbeGateway = NoopVbookSourceHealthProbeGateway,
            ).setClockForTest { 1_000L },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun checkAllEnabledSkipsDisabledSources() = runBlocking {
        database.bookSourceDao.insert(
            source("https://enabled.example/source", enabled = true),
            source("https://disabled.example/source", enabled = false),
        )

        val result = processor.checkAllEnabled()

        assertEquals(listOf("https://enabled.example/source"), probeGateway.probedUrls)
        assertEquals(1, result.healthyCount)
        assertEquals(0, result.failedCount)
        assertEquals(
            "HEALTHY",
            database.bookSourceHealthDao.getBySourceUrl("https://enabled.example/source")?.status,
        )
        assertEquals(SourceCheckRunStatus.COMPLETED, database.sourceCheckDao.getLatestRunForSource("https://enabled.example/source")?.statusValue)
        assertEquals(
            SourceCheckStageStatus.PASSED,
            database.sourceCheckDao.getStages(
                database.sourceCheckDao.getLatestRunForSource("https://enabled.example/source")!!.id,
            ).single().statusValue,
        )
        assertNull(
            database.bookSourceHealthDao.getBySourceUrl("https://disabled.example/source")
        )
        assertNull(database.sourceCheckDao.getLatestRunForSource("https://disabled.example/source"))
    }

    @Test
    fun checkSourceTargetsExactSourceEvenWhenDisabled() = runBlocking {
        database.bookSourceDao.insert(
            source("https://enabled.example/source", enabled = true),
            source("https://disabled.example/source", enabled = false),
        )

        val result = processor.checkSource("https://disabled.example/source")

        assertEquals(listOf("https://disabled.example/source"), probeGateway.probedUrls)
        assertEquals(1, result.healthyCount)
        assertEquals(0, result.failedCount)
        assertEquals(
            "HEALTHY",
            database.bookSourceHealthDao.getBySourceUrl("https://disabled.example/source")?.status,
        )
        assertEquals(SourceCheckRunStatus.COMPLETED, database.sourceCheckDao.getLatestRunForSource("https://disabled.example/source")?.statusValue)
        assertTrue(
            database.sourceCheckDao.getStages(
                database.sourceCheckDao.getLatestRunForSource("https://disabled.example/source")!!.id,
            ).single().statusValue == SourceCheckStageStatus.PASSED
        )
        assertNull(
            database.bookSourceHealthDao.getBySourceUrl("https://enabled.example/source")
        )
        assertNull(database.sourceCheckDao.getLatestRunForSource("https://enabled.example/source"))
    }

    @Test
    fun missingTargetSourceDoesNotFallBackToAllSources() = runBlocking {
        database.bookSourceDao.insert(source("https://enabled.example/source", enabled = true))

        val result = processor.checkSource("https://missing.example/source")

        assertEquals(emptyList<String>(), probeGateway.probedUrls)
        assertEquals(0, result.healthyCount)
        assertEquals(0, result.failedCount)
        assertNull(
            database.bookSourceHealthDao.getBySourceUrl("https://enabled.example/source")
        )
        assertNull(database.sourceCheckDao.getLatestRunForSource("https://enabled.example/source"))
    }

    private fun source(
        url: String,
        enabled: Boolean,
    ): BookSource = BookSource(
        bookSourceUrl = url,
        bookSourceName = url.substringAfterLast('/'),
        enabled = enabled,
    )
}

private class RecordingBookSourceHealthProbeGateway : BookSourceHealthProbeGateway {
    val probedUrls = mutableListOf<String>()

    override suspend fun probe(
        source: BookSource,
        profile: SourceCheckProfile,
    ): SourceCheckProbeResult {
        probedUrls += source.bookSourceUrl
        return SourceCheckProbeResult(
            profile = profile,
            stages = listOf(
                SourceCheckStageEvidence(
                    stageKey = "reachability",
                    stageOrder = 0,
                    status = SourceCheckStageStatus.PASSED,
                    startedAt = 1_000L,
                    finishedAt = 1_000L,
                    latencyMs = 0L,
                )
            ),
        )
    }
}

private object NoopRssSourceHealthProbeGateway : RssSourceHealthProbeGateway {
    override suspend fun probe(
        source: RssSource,
        profile: SourceCheckProfile,
    ): SourceCheckProbeResult = error("RSS probe should not run in this test")
}

private object NoopVbookSourceHealthProbeGateway : VbookSourceHealthProbeGateway {
    override fun supports(source: BookSource): Boolean = false

    override suspend fun probe(
        source: BookSource,
        profile: SourceCheckProfile,
    ): SourceCheckProbeResult = error("VBook probe should not run in this test")
}
