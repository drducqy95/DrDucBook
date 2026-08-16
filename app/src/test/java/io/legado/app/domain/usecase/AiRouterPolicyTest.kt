package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiFailureKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AiRouterPolicyTest {

    @Test
    fun `stream never falls back after output starts`() {
        AiFailureKind.entries.forEach { kind ->
            assertFalse(AiRouterPolicy.mayFallback(kind, outputStarted = true))
        }
    }

    @Test
    fun `provider availability failures may fall back before output`() {
        listOf(
            AiFailureKind.AUTHENTICATION,
            AiFailureKind.QUOTA,
            AiFailureKind.RATE_LIMIT,
            AiFailureKind.SERVER,
            AiFailureKind.NETWORK,
            AiFailureKind.TIMEOUT,
            AiFailureKind.ROUTE_UNAVAILABLE,
        ).forEach { kind ->
            assertTrue(kind.name, AiRouterPolicy.mayFallback(kind, outputStarted = false))
        }
    }

    @Test
    fun `cancellation does not fall back`() {
        assertFalse(
            AiFailureKind.CANCELLED.name,
            AiRouterPolicy.mayFallback(AiFailureKind.CANCELLED, outputStarted = false),
        )
    }

    @Test
    fun `target-local configuration protocol and unknown errors fall back`() {
        listOf(AiFailureKind.CONFIGURATION, AiFailureKind.PROTOCOL, AiFailureKind.UNKNOWN).forEach { kind ->
            assertTrue(kind.name, AiRouterPolicy.mayFallback(kind, outputStarted = false))
        }
    }

    @Test
    fun `empty output cooldown keeps a failed target out of the current chapter wave`() {
        assertEquals(
            5L * 60_000L,
            AiRouterPolicy.cooldownMillis(AiFailureKind.EMPTY_OUTPUT, consecutiveFailures = 1),
        )
        assertEquals(
            30L * 60_000L,
            AiRouterPolicy.cooldownMillis(AiFailureKind.EMPTY_OUTPUT, consecutiveFailures = 8),
        )
    }

    @Test
    fun `only account scoped failures quarantine a credential`() {
        listOf(
            AiFailureKind.AUTHENTICATION,
            AiFailureKind.RATE_LIMIT,
            AiFailureKind.QUOTA,
        ).forEach { kind ->
            assertTrue(kind.name, AiRouterPolicy.affectsCredential(kind))
        }
        listOf(
            AiFailureKind.EMPTY_OUTPUT,
            AiFailureKind.PARSE_ERROR,
            AiFailureKind.PROTOCOL,
            AiFailureKind.CONFIGURATION,
            AiFailureKind.TIMEOUT,
            AiFailureKind.NETWORK,
            AiFailureKind.ROUTE_UNAVAILABLE,
        ).forEach { kind ->
            assertFalse(kind.name, AiRouterPolicy.affectsCredential(kind))
        }
    }

    @Test
    fun `route unavailable does not quarantine targets again`() {
        assertEquals(
            0L,
            AiRouterPolicy.cooldownMillis(
                AiFailureKind.ROUTE_UNAVAILABLE,
                consecutiveFailures = 5,
            ),
        )
    }
}
