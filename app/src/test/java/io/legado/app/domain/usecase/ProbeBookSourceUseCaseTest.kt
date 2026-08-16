package io.legado.app.domain.usecase

import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.BookSourceProbeGateway
import io.legado.app.domain.model.BookSourceHealthStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProbeBookSourceUseCaseTest {

    @Test
    fun successBecomesHealthyAndSlowSuccessBecomesDegraded() = runBlocking {
        var clock = 100L
        var delay = 0L
        val useCase = ProbeBookSourceUseCase(
            gateway = object : BookSourceProbeGateway {
                override suspend fun probe(source: BookSource) {
                    clock += delay
                }
            },
            now = { clock },
        )

        assertEquals(BookSourceHealthStatus.HEALTHY, useCase(BookSource()).status)
        delay = ProbeBookSourceUseCase.DEGRADED_LATENCY_MS
        assertEquals(BookSourceHealthStatus.DEGRADED, useCase(BookSource()).status)
    }

    @Test
    fun providerFailureIsClassifiedWithoutLeakingMessage() = runBlocking {
        val useCase = ProbeBookSourceUseCase(
            gateway = object : BookSourceProbeGateway {
                override suspend fun probe(source: BookSource) {
                    error("HTTP 403 Authorization: Bearer secret-token")
                }
            },
        )

        val result = useCase(BookSource())

        assertEquals(BookSourceHealthStatus.AUTH_REQUIRED, result.status)
        check("secret-token" !in result.messageRedacted.orEmpty())
    }
}
