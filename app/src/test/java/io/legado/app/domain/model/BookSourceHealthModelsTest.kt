package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BookSourceHealthModelsTest {

    @Test
    fun classifierSeparatesCaptchaAuthRateNetworkTlsRuleEmptyMediaAndHttpFailures() {
        assertEquals(
            BookSourceHealthStatus.CAPTCHA_REQUIRED,
            classifyBookSourceProbeFailure("Cloudflare challenge captcha", 10).status,
        )
        assertEquals(
            BookSourceHealthStatus.AUTH_REQUIRED,
            classifyBookSourceProbeFailure("HTTP 403 Forbidden", 10).status,
        )
        assertEquals(
            BookSourceHealthStatus.RATE_LIMITED,
            classifyBookSourceProbeFailure("HTTP 429 Too Many Requests", 10).status,
        )
        assertEquals(
            BookSourceHealthStatus.NETWORK_ERROR,
            classifyBookSourceProbeFailure("UnknownHost DNS failure", 10).status,
        )
        assertEquals(
            BookSourceHealthStatus.TLS_ERROR,
            classifyBookSourceProbeFailure("SSL handshake certificate failed", 10).status,
        )
        assertEquals(
            BookSourceHealthStatus.BROKEN_RULE,
            classifyBookSourceProbeFailure("XPath parse rule failed", 10).status,
        )
        assertEquals(
            BookSourceHealthStatus.CONTENT_EMPTY,
            classifyBookSourceProbeFailure("content empty", 10).status,
        )
        assertEquals(
            BookSourceHealthStatus.MEDIA_ERROR,
            classifyBookSourceProbeFailure("media track returned no variants", 10).status,
        )
        assertEquals(
            BookSourceHealthStatus.STALE,
            classifyBookSourceProbeFailure("cached result is stale", 10).status,
        )
        assertEquals(
            BookSourceHealthStatus.HTTP_ERROR,
            classifyBookSourceProbeFailure("HTTP 500 server error", 10).status,
        )
    }

    @Test
    fun diagnosticsRedactCredentialsAndFailureCountResetsOnHealthy() {
        val diagnostic = redactBookSourceDiagnostic(
            "HTTP 401 Authorization: Bearer secret-token Cookie=session-secret"
        ).orEmpty()

        assertFalse("secret-token" in diagnostic)
        assertFalse("session-secret" in diagnostic)
        assertEquals(3, nextBookSourceFailureCount(2, BookSourceHealthStatus.HTTP_ERROR))
        assertEquals(0, nextBookSourceFailureCount(8, BookSourceHealthStatus.HEALTHY))
        assertEquals(0, nextBookSourceFailureCount(8, BookSourceHealthStatus.DEGRADED))
        assertEquals(0, nextBookSourceFailureCount(8, BookSourceHealthStatus.UNKNOWN_OFFLINE))
        assertEquals(0, nextBookSourceFailureCount(8, BookSourceHealthStatus.STALE))
    }
}
