package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiFailureKind
import io.legado.app.domain.model.AiProviderFailureClassifier
import io.legado.app.domain.model.AiRequestAttemptsException
import kotlinx.coroutines.delay
import java.util.concurrent.CancellationException
import kotlin.math.min
import kotlin.random.Random

/**
 * Round-robin key rotator for providers with multiple API keys.
 * Keys may be separated by newlines, commas, or semicolons in the provider's apiKey field.
 */
internal class KeyRotator(rawKey: String) {

    private val keys: List<String> = rawKey
        .split(Regex("[,;\\r\\n]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf("") }

    private var index = 0

    val currentKey: String
        get() = keys[index % keys.size]

    val hasMultipleKeys: Boolean
        get() = keys.size > 1

    val keyCount: Int
        get() = keys.size

    fun attemptsAtLeast(defaultAttempts: Int): Int = maxOf(defaultAttempts, keyCount)

    /** Advance to the next key. Returns the new current key. */
    fun rotate(): String {
        if (keys.size > 1) {
            index = (index + 1) % keys.size
        }
        return currentKey
    }
}

/**
 * Retry a block with exponential backoff + jitter.
 * Retries on [retryableStatusCodes] (default: 429, 502, 503).
 * If [keyRotator] is provided and has multiple keys, rotates key on each retry.
 *
 * @param maxAttempts Total attempts (1 = no retry, 2 = one retry, etc.)
 * @param baseDelayMs Base delay in milliseconds
 * @param maxDelayMs Maximum delay cap
 * @param onRetry Called before each retry with (attempt, delayMs, exception)
 */
internal suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    baseDelayMs: Long = 1_000,
    maxDelayMs: Long = 30_000,
    retryableStatusCodes: Set<Int> = setOf(408, 425, 429, 500, 502, 503, 504),
    keyRotator: KeyRotator? = null,
    onRetry: (suspend (attempt: Int, delayMs: Long, error: Exception) -> Unit)? = null,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    var attemptsMade = 0
    for (attempt in 1..maxAttempts.coerceAtLeast(1)) {
        attemptsMade = attempt
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastException = e
            if (attempt >= maxAttempts.coerceAtLeast(1)) break
            val retryable = isRetryable(e, retryableStatusCodes)
            val canTryAnotherKey = keyRotator?.hasMultipleKeys == true && isCredentialFailure(e)
            if (!retryable && !canTryAnotherKey) break

            // Rotate key if available
            if (keyRotator != null && keyRotator.hasMultipleKeys) {
                keyRotator.rotate()
            }

            // Exponential backoff with jitter
            val totalDelay = if (canTryAnotherKey && !retryable) {
                0L
            } else {
                val exponentialDelay = baseDelayMs * (1L shl (attempt - 1))
                val cappedDelay = min(exponentialDelay, maxDelayMs)
                val jitter = Random.nextLong(0, cappedDelay / 4 + 1)
                cappedDelay + jitter
            }

            onRetry?.invoke(attempt, totalDelay, e)
            if (totalDelay > 0) delay(totalDelay)
        }
    }
    val failure = lastException ?: Exception("Retry failed before the first attempt")
    throw AiRequestAttemptsException(attemptsMade.coerceAtLeast(1), failure)
}

private fun isRetryable(e: Exception, retryableStatusCodes: Set<Int>): Boolean {
    val failure = AiProviderFailureClassifier.classify(
        error = e,
        provider = "",
        model = "",
    ).failure
    return when (failure.kind) {
        AiFailureKind.RATE_LIMIT,
        AiFailureKind.SERVER,
        AiFailureKind.TIMEOUT -> failure.statusCode?.let(retryableStatusCodes::contains)
            ?: failure.retryable
        else -> failure.retryable
    }
}

private fun isCredentialFailure(e: Exception): Boolean {
    return when (
        AiProviderFailureClassifier.classify(
            error = e,
            provider = "",
            model = "",
        ).failure.kind
    ) {
        AiFailureKind.AUTHENTICATION,
        AiFailureKind.QUOTA,
        AiFailureKind.RATE_LIMIT -> true
        else -> false
    }
}
