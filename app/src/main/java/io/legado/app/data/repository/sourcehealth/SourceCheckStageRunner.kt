package io.legado.app.data.repository.sourcehealth

import io.legado.app.domain.model.redactBookSourceDiagnostic
import io.legado.app.domain.sourcehealth.SourceCheckStageEvidence
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import kotlinx.coroutines.CancellationException

internal class SourceCheckStageRunner(
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun <T> run(
        stageKey: String,
        stageOrder: Int,
        block: suspend () -> T,
    ): SourceCheckStageOutcome<T> {
        val startedAt = now()
        return try {
            val value = block()
            val finishedAt = now()
            SourceCheckStageOutcome(
                evidence = SourceCheckStageEvidence(
                    stageKey = stageKey,
                    stageOrder = stageOrder,
                    status = SourceCheckStageStatus.PASSED,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    latencyMs = (finishedAt - startedAt).coerceAtLeast(0L),
                ),
                value = value,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val finishedAt = now()
            SourceCheckStageOutcome(
                evidence = SourceCheckStageEvidence(
                    stageKey = stageKey,
                    stageOrder = stageOrder,
                    status = SourceCheckStageStatus.FAILED,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    latencyMs = (finishedAt - startedAt).coerceAtLeast(0L),
                    httpStatus = error.message.extractHttpStatus(),
                    failureStep = stageKey,
                    messageRedacted = error.toSafeDiagnostic(),
                ),
                value = null,
            )
        }
    }

    fun skipped(
        stageKey: String,
        stageOrder: Int,
        reason: String,
    ): SourceCheckStageEvidence {
        val timestamp = now()
        return SourceCheckStageEvidence(
            stageKey = stageKey,
            stageOrder = stageOrder,
            status = SourceCheckStageStatus.SKIPPED,
            startedAt = timestamp,
            finishedAt = timestamp,
            latencyMs = 0L,
            failureStep = "skipped",
            messageRedacted = redactBookSourceDiagnostic(reason),
        )
    }
}

internal data class SourceCheckStageOutcome<T>(
    val evidence: SourceCheckStageEvidence,
    val value: T?,
)

internal fun SourceCheckStageEvidence.passed(): Boolean =
    status == SourceCheckStageStatus.PASSED

internal fun SourceCheckProfile.includesStandard(): Boolean =
    this != SourceCheckProfile.QUICK

internal fun SourceCheckProfile.includesFull(): Boolean =
    this == SourceCheckProfile.FULL

private fun Throwable.toSafeDiagnostic(): String {
    val className = javaClass.simpleName.ifBlank { "Throwable" }
    val firstLine = message
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        .orEmpty()
    if (firstLine.isBlank()) return className
    val unsafe = firstLine.length > MAX_DIAGNOSTIC_CHARS ||
        firstLine.contains("<!doctype", ignoreCase = true) ||
        firstLine.contains("<html", ignoreCase = true) ||
        firstLine.contains("</")
    val detail = if (unsafe) className else "$className: $firstLine"
    return redactBookSourceDiagnostic(detail) ?: className
}

private fun String?.extractHttpStatus(): Int? {
    val value = this ?: return null
    return HTTP_STATUS_REGEX.find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

private val HTTP_STATUS_REGEX = Regex("(?i)(?:http|status)\\s*[:=]?\\s*(\\d{3})")
private const val MAX_DIAGNOSTIC_CHARS = 240
