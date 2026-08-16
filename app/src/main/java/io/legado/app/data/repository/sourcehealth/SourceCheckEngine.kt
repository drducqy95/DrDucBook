package io.legado.app.data.repository.sourcehealth

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.domain.gateway.BookSourceHealthProbeGateway
import io.legado.app.domain.gateway.RssSourceHealthProbeGateway
import io.legado.app.domain.gateway.VbookSourceHealthProbeGateway
import io.legado.app.domain.sourcehealth.SourceCheckClassifier
import io.legado.app.domain.sourcehealth.SourceCheckProbeResult
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckRun
import io.legado.app.domain.sourcehealth.SourceCheckRunStatus
import io.legado.app.domain.sourcehealth.SourceCheckStageResult
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import io.legado.app.domain.sourcehealth.toStageResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class SourceCheckEngine(
    private val sourceCheckRepository: SourceCheckRepository,
    private val bookSourceHealthProbeGateway: BookSourceHealthProbeGateway,
    private val rssSourceHealthProbeGateway: RssSourceHealthProbeGateway,
    private val vbookSourceHealthProbeGateway: VbookSourceHealthProbeGateway,
) {
    private var now: () -> Long = System::currentTimeMillis

    internal fun setClockForTest(now: () -> Long): SourceCheckEngine = apply {
        this.now = now
    }

    suspend fun checkBookSource(
        source: BookSource,
        profile: SourceCheckProfile,
        persistSummary: Boolean = true,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): SourceCheckRun {
        val startedAt = now()
        val run = sourceCheckRepository.beginRun(
            source = source,
            profile = profile,
            startedAt = startedAt,
            createInitialStage = false,
        )
        return runAdapterProbe(
            run = run,
            startedAt = startedAt,
            persistSummary = persistSummary,
            timeoutMs = timeoutMs,
        ) {
            if (vbookSourceHealthProbeGateway.supports(source)) {
                vbookSourceHealthProbeGateway.probe(source, profile)
            } else {
                bookSourceHealthProbeGateway.probe(source, profile)
            }
        }
    }

    suspend fun checkRssSource(
        source: RssSource,
        profile: SourceCheckProfile,
        persistSummary: Boolean = true,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): SourceCheckRun {
        val startedAt = now()
        val run = sourceCheckRepository.beginRun(
            sourceUrl = source.sourceUrl,
            sourceName = source.sourceName,
            sourceGroup = source.sourceGroup,
            profile = profile,
            startedAt = startedAt,
            createInitialStage = false,
        )
        return runAdapterProbe(
            run = run,
            startedAt = startedAt,
            persistSummary = persistSummary,
            timeoutMs = timeoutMs,
        ) {
            rssSourceHealthProbeGateway.probe(source, profile)
        }
    }

    private suspend fun runAdapterProbe(
        run: SourceCheckRun,
        startedAt: Long,
        persistSummary: Boolean,
        timeoutMs: Long,
        probe: suspend () -> SourceCheckProbeResult,
    ): SourceCheckRun {
        return try {
            val result = if (timeoutMs > 0L) {
                withTimeout(timeoutMs) { probe() }
            } else {
                probe()
            }
            val finishedAt = now()
            val evidence = SourceCheckClassifier.classify(
                stages = result.stages,
                startedAt = startedAt,
                finishedAt = finishedAt,
            )
            sourceCheckRepository.completeRun(
                run = run,
                finishedAt = finishedAt,
                stages = result.toStageResults(run.id),
                evidence = evidence,
                runStatus = SourceCheckRunStatus.COMPLETED,
                persistSummary = persistSummary,
            )
        } catch (timeout: TimeoutCancellationException) {
            val finishedAt = now()
            val evidence = SourceCheckClassifier.classifyFailureMessage(
                message = "Source check timeout after ${timeoutMs}ms",
                latencyMs = (finishedAt - startedAt).coerceAtLeast(timeoutMs.coerceAtLeast(0L)),
                stageKey = STAGE_ENGINE,
            )
            sourceCheckRepository.completeRun(
                run = run,
                finishedAt = finishedAt,
                stages = listOf(
                    SourceCheckStageResult(
                        runId = run.id,
                        stageKey = STAGE_ENGINE,
                        stageOrder = ORDER_ENGINE,
                        status = SourceCheckStageStatus.FAILED,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        latencyMs = evidence.latencyMs,
                        httpStatus = evidence.httpStatus,
                        failureStep = evidence.failureStep,
                        messageRedacted = evidence.messageRedacted,
                    )
                ),
                evidence = evidence,
                runStatus = SourceCheckRunStatus.FAILED,
                persistSummary = persistSummary,
            )
        } catch (cancellation: CancellationException) {
            val finishedAt = now()
            runCatching {
                sourceCheckRepository.completeRun(
                    run = run,
                    finishedAt = finishedAt,
                    stages = listOf(
                        SourceCheckStageResult(
                            runId = run.id,
                            stageKey = STAGE_ENGINE,
                            stageOrder = ORDER_ENGINE,
                            status = SourceCheckStageStatus.CANCELED,
                            startedAt = startedAt,
                            finishedAt = finishedAt,
                        )
                    ),
                    evidence = null,
                    runStatus = SourceCheckRunStatus.CANCELED,
                    persistSummary = false,
                )
            }
            throw cancellation
        } catch (error: Throwable) {
            val finishedAt = now()
            val evidence = SourceCheckClassifier.classifyFailureMessage(
                message = error.message ?: error.javaClass.simpleName,
                latencyMs = (finishedAt - startedAt).coerceAtLeast(0L),
                stageKey = STAGE_ENGINE,
            )
            sourceCheckRepository.completeRun(
                run = run,
                finishedAt = finishedAt,
                stages = listOf(
                    SourceCheckStageResult(
                        runId = run.id,
                        stageKey = STAGE_ENGINE,
                        stageOrder = ORDER_ENGINE,
                        status = SourceCheckStageStatus.FAILED,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        latencyMs = evidence.latencyMs,
                        httpStatus = evidence.httpStatus,
                        failureStep = evidence.failureStep,
                        messageRedacted = evidence.messageRedacted,
                    )
                ),
                evidence = evidence,
                runStatus = SourceCheckRunStatus.FAILED,
                persistSummary = persistSummary,
            )
        }
    }

    private companion object {
        const val STAGE_ENGINE = "engine"
        const val ORDER_ENGINE = -1
        const val DEFAULT_TIMEOUT_MS = 180_000L
    }
}
