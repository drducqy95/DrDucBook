package io.legado.app.domain.sourcehealth

import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.BookSourceProbeEvidence
import io.legado.app.domain.model.redactBookSourceDiagnostic

object SourceCheckClassifier {

    fun classify(
        stages: List<SourceCheckStageEvidence>,
        startedAt: Long,
        finishedAt: Long,
    ): BookSourceProbeEvidence {
        val failed = stages
            .filter { it.status == SourceCheckStageStatus.FAILED }
            .minWithOrNull(compareBy<SourceCheckStageEvidence> { it.stageOrder }.thenBy { it.stageKey })
        if (failed != null) {
            return failed.toFailureEvidence(startedAt, finishedAt)
        }

        val passedCount = stages.count { it.status == SourceCheckStageStatus.PASSED }
        val status = when {
            passedCount == 0 -> BookSourceHealthStatus.UNSUPPORTED
            (finishedAt - startedAt).coerceAtLeast(0L) >= DEGRADED_LATENCY_MS ->
                BookSourceHealthStatus.DEGRADED
            else -> BookSourceHealthStatus.HEALTHY
        }
        return BookSourceProbeEvidence(
            status = status,
            latencyMs = (finishedAt - startedAt).coerceAtLeast(0L),
            failureStep = if (status == BookSourceHealthStatus.UNSUPPORTED) "capability" else null,
            messageRedacted = if (status == BookSourceHealthStatus.UNSUPPORTED) {
                "No supported probe stages"
            } else {
                null
            },
        )
    }

    fun classifyFailureMessage(
        message: String?,
        latencyMs: Long,
        stageKey: String,
        httpStatus: Int? = null,
    ): BookSourceProbeEvidence {
        val value = message.orEmpty().lowercase()
        val statusCode = httpStatus ?: value.extractHttpStatus()
        val status = when {
            value.containsAny("captcha", "cloudflare", "challenge", "verify code", "verification code") ->
                BookSourceHealthStatus.CAPTCHA_REQUIRED
            statusCode in setOf(401, 403) ||
                value.containsAny("unauthorized", "forbidden", "login", "dang nhap", "signin", "sign in") ->
                BookSourceHealthStatus.AUTH_REQUIRED
            statusCode == 429 ||
                value.containsAny("rate limit", "too many requests", "quota exceeded", "throttle") ->
                BookSourceHealthStatus.RATE_LIMITED
            value.containsAny("ssl", "tls", "certificate", "certpath", "handshake", "trust anchor") ->
                BookSourceHealthStatus.TLS_ERROR
            value.containsAny("offline", "no network", "airplane mode") ->
                BookSourceHealthStatus.UNKNOWN_OFFLINE
            value.containsAny("unknownhost", "dns", "failed to connect", "connection reset", "timeout", "network") ->
                BookSourceHealthStatus.NETWORK_ERROR
            value.containsAny("rule", "parse", "selector", "xpath", "js failed", "script") ->
                BookSourceHealthStatus.BROKEN_RULE
            value.containsAny("empty", "no items", "no articles", "no chapters", "content") ->
                BookSourceHealthStatus.CONTENT_EMPTY
            value.containsAny("media", "track", "variant", "m3u8", "mpd") ->
                BookSourceHealthStatus.MEDIA_ERROR
            value.containsAny("unsupported", "not supported") ->
                BookSourceHealthStatus.UNSUPPORTED
            value.containsAny("stale", "outdated") ->
                BookSourceHealthStatus.STALE
            else -> BookSourceHealthStatus.HTTP_ERROR
        }
        return BookSourceProbeEvidence(
            status = status,
            latencyMs = latencyMs,
            httpStatus = statusCode,
            failureStep = stageKey,
            messageRedacted = redactBookSourceDiagnostic(message),
        )
    }

    private fun SourceCheckStageEvidence.toFailureEvidence(
        startedAt: Long,
        finishedAt: Long,
    ): BookSourceProbeEvidence {
        val latency = latencyMs ?: (finishedAt - startedAt).coerceAtLeast(0L)
        return classifyFailureMessage(
            message = messageRedacted ?: failureStep ?: stageKey,
            latencyMs = latency,
            stageKey = stageKey,
            httpStatus = httpStatus,
        )
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any(::contains)

    private fun String.extractHttpStatus(): Int? =
        HTTP_STATUS_REGEX.find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    const val DEGRADED_LATENCY_MS = 8_000L
    private val HTTP_STATUS_REGEX = Regex("(?i)(?:http|status)\\s*[:=]?\\s*(\\d{3})")
}
