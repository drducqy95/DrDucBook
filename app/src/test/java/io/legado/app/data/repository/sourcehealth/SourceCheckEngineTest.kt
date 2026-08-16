package io.legado.app.data.repository.sourcehealth

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.domain.gateway.BookSourceHealthProbeGateway
import io.legado.app.domain.gateway.RssSourceHealthProbeGateway
import io.legado.app.domain.gateway.VbookSourceHealthProbeGateway
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.sourcehealth.SourceCheckProbeResult
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckRunStatus
import io.legado.app.domain.sourcehealth.SourceCheckStageEvidence
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SourceCheckEngineTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun checkBookSourcePersistsAdapterStagesAndSummary() = runBlocking {
        val source = source()
        val engine = engine(
            gateway = FixedBookGateway(
                SourceCheckProbeResult(
                    profile = SourceCheckProfile.STANDARD,
                    stages = listOf(
                        stage("reachability", 0, SourceCheckStageStatus.PASSED),
                        stage("search", 1, SourceCheckStageStatus.PASSED),
                        stage("detail", 2, SourceCheckStageStatus.PASSED),
                    ),
                )
            ),
            now = tickingClock(),
        )

        val run = engine.checkBookSource(source, SourceCheckProfile.STANDARD)

        assertEquals(SourceCheckRunStatus.COMPLETED, run.status)
        assertEquals(BookSourceHealthStatus.HEALTHY, run.healthStatus)
        assertEquals(3, database.sourceCheckDao.getStages(run.id).size)
        assertEquals(
            listOf("reachability", "search", "detail"),
            database.sourceCheckDao.getStages(run.id).map { it.stageKey },
        )
        assertEquals(
            BookSourceHealthStatus.HEALTHY.name,
            database.bookSourceHealthDao.getBySourceUrl(source.bookSourceUrl)?.status,
        )
    }

    @Test
    fun cancellationMarksRunCanceledAndLeavesSummaryUntouched() = runBlocking {
        val source = source("https://cancel.example/source")
        val engine = engine(
            gateway = object : BookSourceHealthProbeGateway {
                override suspend fun probe(
                    source: BookSource,
                    profile: SourceCheckProfile,
                ): SourceCheckProbeResult {
                    throw CancellationException("cancelled")
                }
            },
            now = tickingClock(),
        )

        try {
            engine.checkBookSource(source, SourceCheckProfile.FULL)
            fail("CancellationException expected")
        } catch (_: CancellationException) {
        }

        val run = database.sourceCheckDao.getLatestRunForSource(source.bookSourceUrl)!!
        assertEquals(SourceCheckRunStatus.CANCELED, run.statusValue)
        assertEquals(
            SourceCheckStageStatus.CANCELED,
            database.sourceCheckDao.getStages(run.id).single().statusValue,
        )
        assertNull(database.bookSourceHealthDao.getBySourceUrl(source.bookSourceUrl))
    }

    @Test
    fun timeoutMarksEngineFailureAndDoesNotCrash() = runBlocking {
        val source = source("https://timeout.example/source")
        val engine = engine(
            gateway = object : BookSourceHealthProbeGateway {
                override suspend fun probe(
                    source: BookSource,
                    profile: SourceCheckProfile,
                ): SourceCheckProbeResult {
                    delay(1_000)
                    return SourceCheckProbeResult(
                        profile = profile,
                        stages = listOf(stage("reachability", 0, SourceCheckStageStatus.PASSED)),
                    )
                }
            },
            now = tickingClock(),
        )

        val run = engine.checkBookSource(
            source = source,
            profile = SourceCheckProfile.QUICK,
            timeoutMs = 10L,
        )

        assertEquals(SourceCheckRunStatus.FAILED, run.status)
        assertEquals(BookSourceHealthStatus.NETWORK_ERROR, run.healthStatus)
        assertEquals("engine", run.failureStep)
        assertEquals(1, database.sourceCheckDao.getStages(run.id).size)
    }

    @Test
    fun checkRssSourceUsesSameEnginePersistencePath() = runBlocking {
        val source = RssSource(
            sourceUrl = "https://rss.example/feed",
            sourceName = "RSS",
            sourceGroup = "News",
        )
        val engine = SourceCheckEngine(
            sourceCheckRepository = SourceCheckRepository(
                database.sourceCheckDao,
                database.bookSourceHealthDao,
            ),
            bookSourceHealthProbeGateway = FixedBookGateway(
                SourceCheckProbeResult(SourceCheckProfile.QUICK, emptyList())
            ),
            rssSourceHealthProbeGateway = FixedRssGateway(
                SourceCheckProbeResult(
                    profile = SourceCheckProfile.FULL,
                    stages = listOf(
                        stage("feed", 0, SourceCheckStageStatus.PASSED),
                        stage("list", 1, SourceCheckStageStatus.PASSED),
                        stage("article", 2, SourceCheckStageStatus.PASSED),
                    ),
                )
            ),
            vbookSourceHealthProbeGateway = NoopVbookGateway,
        ).setClockForTest(tickingClock())

        val run = engine.checkRssSource(source, SourceCheckProfile.FULL)

        assertEquals(source.sourceUrl, run.sourceUrl)
        assertEquals(source.sourceGroup, run.sourceGroup)
        assertEquals(BookSourceHealthStatus.HEALTHY, run.healthStatus)
        assertEquals(3, database.sourceCheckDao.getStages(run.id).size)
        assertEquals(
            BookSourceHealthStatus.HEALTHY.name,
            database.bookSourceHealthDao.getBySourceUrl(source.sourceUrl)?.status,
        )
    }

    @Test
    fun checkBookSourceDoesNotMutateSourceEntity() = runBlocking {
        val source = source("https://immutable.example/source").copy(
            bookSourceGroup = "Original group",
            enabled = false,
            enabledExplore = false,
            searchUrl = "https://immutable.example/search?q={{key}}",
            bookSourceComment = "Original comment",
        )
        database.bookSourceDao.insert(source)
        val engine = engine(
            gateway = FixedBookGateway(
                SourceCheckProbeResult(
                    profile = SourceCheckProfile.QUICK,
                    stages = listOf(stage("reachability", 0, SourceCheckStageStatus.PASSED)),
                )
            ),
            now = tickingClock(),
        )

        engine.checkBookSource(source, SourceCheckProfile.QUICK)

        val stored = database.bookSourceDao.getBookSource(source.bookSourceUrl)!!
        assertEquals("Original group", stored.bookSourceGroup)
        assertEquals(false, stored.enabled)
        assertEquals(false, stored.enabledExplore)
        assertEquals("https://immutable.example/search?q={{key}}", stored.searchUrl)
        assertEquals("Original comment", stored.bookSourceComment)
    }

    @Test
    fun koinSingleOfCreatesEngineWithoutClockBinding() {
        val app = koinApplication {
            modules(
                module {
                    single {
                        SourceCheckRepository(
                            database.sourceCheckDao,
                            database.bookSourceHealthDao,
                        )
                    }
                    single<BookSourceHealthProbeGateway> {
                        FixedBookGateway(
                            SourceCheckProbeResult(SourceCheckProfile.QUICK, emptyList())
                        )
                    }
                    single<RssSourceHealthProbeGateway> {
                        FixedRssGateway(
                            SourceCheckProbeResult(SourceCheckProfile.QUICK, emptyList())
                        )
                    }
                    single<VbookSourceHealthProbeGateway> { NoopVbookGateway }
                    singleOf(::SourceCheckEngine)
                }
            )
        }

        try {
            assertNotNull(app.koin.get<SourceCheckEngine>())
        } finally {
            app.close()
        }
    }

    private fun engine(
        gateway: BookSourceHealthProbeGateway,
        now: () -> Long,
    ): SourceCheckEngine = SourceCheckEngine(
        sourceCheckRepository = SourceCheckRepository(
            database.sourceCheckDao,
            database.bookSourceHealthDao,
        ),
        bookSourceHealthProbeGateway = gateway,
        rssSourceHealthProbeGateway = FixedRssGateway(
            SourceCheckProbeResult(SourceCheckProfile.QUICK, emptyList())
        ),
        vbookSourceHealthProbeGateway = NoopVbookGateway,
    ).setClockForTest(now)

    private fun source(url: String = "https://source.example/source"): BookSource = BookSource(
        bookSourceUrl = url,
        bookSourceName = "Source",
    )

    private fun stage(
        key: String,
        order: Int,
        status: SourceCheckStageStatus,
        message: String? = null,
    ): SourceCheckStageEvidence = SourceCheckStageEvidence(
        stageKey = key,
        stageOrder = order,
        status = status,
        startedAt = 1_000L + order,
        finishedAt = 1_010L + order,
        latencyMs = 10L,
        messageRedacted = message,
    )

    private fun tickingClock(): () -> Long {
        var value = 1_000L
        return {
            value += 10L
            value
        }
    }
}

private class FixedBookGateway(
    private val result: SourceCheckProbeResult,
) : BookSourceHealthProbeGateway {
    override suspend fun probe(
        source: BookSource,
        profile: SourceCheckProfile,
    ): SourceCheckProbeResult = result.copy(profile = profile)
}

private class FixedRssGateway(
    private val result: SourceCheckProbeResult,
) : RssSourceHealthProbeGateway {
    override suspend fun probe(
        source: RssSource,
        profile: SourceCheckProfile,
    ): SourceCheckProbeResult = result.copy(profile = profile)
}

private object NoopVbookGateway : VbookSourceHealthProbeGateway {
    override fun supports(source: BookSource): Boolean = false

    override suspend fun probe(
        source: BookSource,
        profile: SourceCheckProfile,
    ): SourceCheckProbeResult = error("VBook probe should not run")
}
