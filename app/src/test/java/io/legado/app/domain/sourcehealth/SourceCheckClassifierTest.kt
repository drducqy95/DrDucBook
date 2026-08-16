package io.legado.app.domain.sourcehealth

import io.legado.app.domain.model.BookSourceHealthStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceCheckClassifierTest {

    @Test
    fun aggregateReturnsHealthyDegradedOrUnsupportedWithoutFailures() {
        assertEquals(
            BookSourceHealthStatus.HEALTHY,
            SourceCheckClassifier.classify(
                stages = listOf(stage("feed", 0, SourceCheckStageStatus.PASSED)),
                startedAt = 1_000L,
                finishedAt = 1_500L,
            ).status,
        )
        assertEquals(
            BookSourceHealthStatus.DEGRADED,
            SourceCheckClassifier.classify(
                stages = listOf(stage("feed", 0, SourceCheckStageStatus.PASSED)),
                startedAt = 1_000L,
                finishedAt = 10_000L,
            ).status,
        )
        assertEquals(
            BookSourceHealthStatus.UNSUPPORTED,
            SourceCheckClassifier.classify(
                stages = listOf(stage("media", 0, SourceCheckStageStatus.SKIPPED)),
                startedAt = 1_000L,
                finishedAt = 1_010L,
            ).status,
        )
    }

    @Test
    fun aggregateClassifiesFirstFailedStageDeterministically() {
        val evidence = SourceCheckClassifier.classify(
            stages = listOf(
                stage("content", 3, SourceCheckStageStatus.FAILED, "content empty"),
                stage("search", 1, SourceCheckStageStatus.FAILED, "HTTP 403 Forbidden"),
            ),
            startedAt = 1_000L,
            finishedAt = 2_000L,
        )

        assertEquals(BookSourceHealthStatus.AUTH_REQUIRED, evidence.status)
        assertEquals("search", evidence.failureStep)
        assertEquals(403, evidence.httpStatus)
    }

    @Test
    fun classifyFailureMessageCoversPhaseGateStatuses() {
        val cases = listOf(
            "offline airplane mode" to BookSourceHealthStatus.UNKNOWN_OFFLINE,
            "HTTP 401 Unauthorized" to BookSourceHealthStatus.AUTH_REQUIRED,
            "Cloudflare captcha challenge" to BookSourceHealthStatus.CAPTCHA_REQUIRED,
            "HTTP 429 too many requests" to BookSourceHealthStatus.RATE_LIMITED,
            "UnknownHost DNS failure" to BookSourceHealthStatus.NETWORK_ERROR,
            "TLS certificate handshake failed" to BookSourceHealthStatus.TLS_ERROR,
            "XPath selector parse rule failed" to BookSourceHealthStatus.BROKEN_RULE,
            "RSS feed returned no articles" to BookSourceHealthStatus.CONTENT_EMPTY,
            "media track returned no variants" to BookSourceHealthStatus.MEDIA_ERROR,
            "plugin capability unsupported" to BookSourceHealthStatus.UNSUPPORTED,
            "cached result is stale" to BookSourceHealthStatus.STALE,
            "HTTP 500 server error" to BookSourceHealthStatus.HTTP_ERROR,
        )

        cases.forEach { (message, expected) ->
            assertEquals(
                message,
                expected,
                SourceCheckClassifier.classifyFailureMessage(
                    message = message,
                    latencyMs = 42L,
                    stageKey = "gate",
                ).status,
            )
        }
    }

    private fun stage(
        key: String,
        order: Int,
        status: SourceCheckStageStatus,
        message: String? = null,
    ): SourceCheckStageEvidence = SourceCheckStageEvidence(
        stageKey = key,
        stageOrder = order,
        status = status,
        startedAt = 1_000L,
        finishedAt = 1_010L,
        latencyMs = 10L,
        httpStatus = message?.let {
            Regex("(\\d{3})").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
        },
        messageRedacted = message,
    )
}
