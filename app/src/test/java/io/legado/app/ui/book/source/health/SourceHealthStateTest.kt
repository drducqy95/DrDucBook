package io.legado.app.ui.book.source.health

import io.legado.app.data.entities.BookSourceHealth
import io.legado.app.domain.model.BookSourceHealthRow
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.SourceKeyType
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckRun
import io.legado.app.domain.sourcehealth.SourceCheckRunStatus
import io.legado.app.domain.sourcehealth.SourceCheckStageResult
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHealthStateTest {

    @Test
    fun mapsSummaryFiltersAndSelectedSourceAcrossAllSourceTypes() {
        val rows = listOf(
            row(
                sourceUrl = "https://book.example/source",
                sourceName = "Book Source",
                sourceType = SourceKeyType.BOOK,
                status = BookSourceHealthStatus.HEALTHY,
            ),
            row(
                sourceUrl = "https://rss.example/feed",
                sourceName = "RSS Source",
                sourceType = SourceKeyType.RSS,
                loginUrl = "https://rss.example/login",
                status = BookSourceHealthStatus.AUTH_REQUIRED,
            ),
            row(
                sourceUrl = "vbook://plugin/drduc",
                sourceName = "VBook Plugin",
                sourceType = SourceKeyType.BOOK,
                isVbook = true,
                status = null,
            ),
        )
        val run = SourceCheckRun(
            id = "run-1",
            sourceUrl = "https://rss.example/feed",
            sourceName = "RSS Source",
            sourceGroup = "News",
            profile = SourceCheckProfile.STANDARD,
            status = SourceCheckRunStatus.COMPLETED,
            healthStatus = BookSourceHealthStatus.AUTH_REQUIRED,
            startedAt = 1_000L,
            finishedAt = 1_500L,
            latencyMs = 500L,
            stageCount = 1,
            failedStageCount = 1,
            messageRedacted = "Login required",
        )
        val stage = SourceCheckStageResult(
            runId = "run-1",
            stageKey = "feed",
            stageOrder = 0,
            status = SourceCheckStageStatus.FAILED,
            startedAt = 1_000L,
            finishedAt = 1_500L,
            latencyMs = 500L,
            httpStatus = 403,
            failureStep = "feed",
            messageRedacted = "Login required",
        )

        val state = buildSourceHealthUiState(
            rows = rows,
            recentRuns = listOf(run),
            selectedRuns = listOf(run),
            selectedStages = listOf(stage),
            current = SourceHealthUiState(
                query = "rss",
                selectedSourceUrl = "vbook://plugin/drduc",
            ),
        )

        assertEquals(3, state.summary.total)
        assertEquals(2, state.summary.checked)
        assertEquals(1, state.summary.healthy)
        assertEquals(1, state.summary.authRequired)
        assertEquals(listOf("https://rss.example/feed"), state.items.map { it.sourceUrl })
        assertEquals(SourceKeyType.RSS, state.items.single().sourceType)
        assertTrue(state.items.single().hasLoginUrl)
        assertEquals("vbook://plugin/drduc", state.selectedSource?.sourceUrl)
        assertTrue(state.selectedSource?.isVbook == true)
        assertEquals("run-1", state.recentRuns.single().id)
        assertEquals("feed", state.selectedStages.single().stageKey)
    }

    @Test
    fun errorFilterKeepsOnlySourcesNeedingAttention() {
        val rows = listOf(
            row(
                sourceUrl = "https://healthy.example",
                sourceName = "Healthy",
                status = BookSourceHealthStatus.HEALTHY,
            ),
            row(
                sourceUrl = "https://captcha.example",
                sourceName = "Captcha",
                status = BookSourceHealthStatus.CAPTCHA_REQUIRED,
            ),
        )

        val state = buildSourceHealthUiState(
            rows = rows,
            recentRuns = emptyList(),
            selectedRuns = emptyList(),
            selectedStages = emptyList(),
            current = SourceHealthUiState(filter = SourceHealthFilter.ERROR),
        )

        assertEquals(listOf("https://captcha.example"), state.items.map { it.sourceUrl })
        assertEquals(1, state.summary.needsAttention)
        assertEquals(1, state.summary.captchaRequired)
    }

    private fun row(
        sourceUrl: String,
        sourceName: String,
        sourceType: SourceKeyType = SourceKeyType.BOOK,
        loginUrl: String? = null,
        isVbook: Boolean = false,
        status: BookSourceHealthStatus?,
    ): BookSourceHealthRow = BookSourceHealthRow(
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        sourceGroup = null,
        sourceType = sourceType,
        homeUrl = if (sourceUrl.startsWith("http")) sourceUrl else null,
        loginUrl = loginUrl,
        iconPath = null,
        isVbook = isVbook,
        enabled = true,
        enabledExplore = true,
        health = status?.let {
            BookSourceHealth(
                sourceUrl = sourceUrl,
                status = it.name,
                lastChecked = 1_000L,
                latencyMs = 250L,
            )
        },
    )
}
