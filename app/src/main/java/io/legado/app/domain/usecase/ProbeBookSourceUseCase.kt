package io.legado.app.domain.usecase

import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.BookSourceProbeGateway
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.BookSourceProbeEvidence
import io.legado.app.domain.model.classifyBookSourceProbeFailure
import io.legado.app.domain.model.redactBookSourceDiagnostic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class ProbeBookSourceUseCase(
    private val gateway: BookSourceProbeGateway,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(source: BookSource): BookSourceProbeEvidence {
        val startedAt = now()
        return try {
            withTimeout(PROBE_TIMEOUT_MS) {
                gateway.probe(source)
            }
            val latency = (now() - startedAt).coerceAtLeast(0L)
            BookSourceProbeEvidence(
                status = if (latency >= DEGRADED_LATENCY_MS) {
                    BookSourceHealthStatus.DEGRADED
                } else {
                    BookSourceHealthStatus.HEALTHY
                },
                latencyMs = latency,
            )
        } catch (error: TimeoutCancellationException) {
            BookSourceProbeEvidence(
                status = BookSourceHealthStatus.NETWORK_ERROR,
                latencyMs = (now() - startedAt).coerceAtLeast(PROBE_TIMEOUT_MS),
                failureStep = "timeout",
                messageRedacted = redactBookSourceDiagnostic("Source probe timed out"),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            classifyBookSourceProbeFailure(
                message = error.message ?: error.javaClass.simpleName,
                latencyMs = (now() - startedAt).coerceAtLeast(0L),
            )
        }
    }

    companion object {
        internal const val PROBE_TIMEOUT_MS = 20_000L
        internal const val DEGRADED_LATENCY_MS = 8_000L
    }
}
