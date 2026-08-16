package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiFailureKind

object AiRouterPolicy {

    fun affectsCredential(kind: AiFailureKind): Boolean = when (kind) {
        AiFailureKind.AUTHENTICATION,
        AiFailureKind.RATE_LIMIT,
        AiFailureKind.QUOTA -> true

        else -> false
    }

    fun mayFallback(kind: AiFailureKind, outputStarted: Boolean): Boolean {
        if (outputStarted) return false
        return when (kind) {
            AiFailureKind.AUTHENTICATION,
            AiFailureKind.RATE_LIMIT,
            AiFailureKind.QUOTA,
            AiFailureKind.TIMEOUT,
            AiFailureKind.NETWORK,
            AiFailureKind.EMPTY_OUTPUT,
            AiFailureKind.PARSE_ERROR,
            AiFailureKind.SERVER,
            AiFailureKind.ROUTE_UNAVAILABLE,
            // A combo can contain targets with different protocols/models. A 404 or schema
            // mismatch is therefore target-local and must advance to the next fallback.
            AiFailureKind.CONFIGURATION,
            AiFailureKind.PROTOCOL,
            // Some providers return new error shapes before we have classified them. If no
            // output has started, the next configured model/key is still safer than stopping.
            AiFailureKind.UNKNOWN -> true

            AiFailureKind.CANCELLED -> false
        }
    }

    fun cooldownMillis(kind: AiFailureKind, consecutiveFailures: Int): Long {
        val multiplier = consecutiveFailures.coerceIn(1, 6)
        return when (kind) {
            AiFailureKind.AUTHENTICATION,
            AiFailureKind.QUOTA -> 24L * 60L * 60L * 1_000L

            AiFailureKind.RATE_LIMIT -> multiplier * 60_000L
            AiFailureKind.SERVER -> multiplier * 30_000L
            AiFailureKind.TIMEOUT,
            AiFailureKind.NETWORK -> multiplier * 10_000L

            // Empty responses commonly arrive only after a full provider timeout. A short
            // cooldown lets the same broken target re-enter while other chapter chunks are
            // still running, multiplying both latency and failure count.
            AiFailureKind.EMPTY_OUTPUT -> multiplier * 5L * 60_000L
            AiFailureKind.PARSE_ERROR -> multiplier * 30_000L

            AiFailureKind.CONFIGURATION,
            AiFailureKind.PROTOCOL -> 60L * 60L * 1_000L

            AiFailureKind.ROUTE_UNAVAILABLE -> 0L

            else -> 0L
        }
    }
}
