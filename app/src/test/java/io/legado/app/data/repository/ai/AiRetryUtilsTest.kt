package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiRequestAttemptsException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class AiRetryUtilsTest {

    @Test
    fun reportsAllRealAttemptsForRetryableFailures() = runBlocking {
        var calls = 0
        val error = captureAttemptsError {
            retryWithBackoff(
                maxAttempts = 3,
                baseDelayMs = 0,
                maxDelayMs = 0,
            ) {
                calls += 1
                throw Exception("HTTP 429: rate limited")
            }
        }

        assertEquals(3, calls)
        assertEquals(3, error.attempts)
        assertEquals("HTTP 429: rate limited", error.lastFailure.message)
    }

    @Test
    fun stopsPermanentAuthFailureAfterOneAttempt() = runBlocking {
        var calls = 0
        val error = captureAttemptsError {
            retryWithBackoff(
                maxAttempts = 3,
                baseDelayMs = 0,
                maxDelayMs = 0,
            ) {
                calls += 1
                throw Exception("HTTP 401: invalid API key")
            }
        }

        assertEquals(1, calls)
        assertEquals(1, error.attempts)
    }

    @Test
    fun rotatesAcrossMultilineKeysAfterAuthenticationFailure() = runBlocking {
        val keys = KeyRotator("bad-key\nsecond-key;third-key")
        val attemptedKeys = mutableListOf<String>()

        val result = retryWithBackoff(
            maxAttempts = keys.attemptsAtLeast(2),
            baseDelayMs = 0,
            maxDelayMs = 0,
            keyRotator = keys,
        ) {
            attemptedKeys += keys.currentKey
            if (keys.currentKey == "bad-key") throw Exception("HTTP 401: invalid API key")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf("bad-key", "second-key"), attemptedKeys)
        assertEquals(3, keys.keyCount)
    }

    @Test
    fun retriesNetworkTimeoutAndNeverWrapsCancellation() = runBlocking {
        var timeoutCalls = 0
        val timeout = captureAttemptsError {
            retryWithBackoff(
                maxAttempts = 2,
                baseDelayMs = 0,
                maxDelayMs = 0,
            ) {
                timeoutCalls += 1
                throw SocketTimeoutException("timed out")
            }
        }
        assertEquals(2, timeoutCalls)
        assertEquals(2, timeout.attempts)

        val cancellation = CancellationException("user cancelled")
        try {
            retryWithBackoff(maxAttempts = 3) { throw cancellation }
            fail("Cancellation must escape immediately")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }

    private suspend fun captureAttemptsError(block: suspend () -> Unit): AiRequestAttemptsException {
        try {
            block()
            fail("Expected AiRequestAttemptsException")
        } catch (error: AiRequestAttemptsException) {
            return error
        }
        throw AssertionError("unreachable")
    }
}
