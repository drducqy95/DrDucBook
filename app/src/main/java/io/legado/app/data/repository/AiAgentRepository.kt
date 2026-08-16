package io.legado.app.data.repository

import io.legado.app.data.dao.AiAgentDao
import io.legado.app.data.entities.AiAgentAudit
import io.legado.app.data.entities.AiAgentProposal
import io.legado.app.data.entities.AiAgentRun
import io.legado.app.data.entities.AiAgentTrace
import io.legado.app.domain.agent.AgentAuditRecord
import io.legado.app.domain.agent.AgentActionProposal
import io.legado.app.domain.agent.AgentProposalStatus
import io.legado.app.domain.agent.AgentRunResult
import io.legado.app.domain.agent.sanitizeForAgentAudit
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiAgentRepository(
    private val aiAgentDao: AiAgentDao,
) : AiAgentGateway {

    override fun observeRecentRuns(limit: Int): Flow<List<AiAgentRun>> =
        aiAgentDao.observeRecentRuns(limit)

    override fun observeTrace(runId: String): Flow<List<AiAgentTrace>> =
        aiAgentDao.observeTrace(runId)

    override fun observePendingProposals(): Flow<List<AiAgentProposal>> =
        aiAgentDao.observePendingProposals()

    override fun observeRecentProposals(limit: Int): Flow<List<AiAgentProposal>> =
        aiAgentDao.observeRecentProposals(limit)

    override fun observeRecentAudits(limit: Int): Flow<List<AiAgentAudit>> =
        aiAgentDao.observeRecentAudits(limit)

    override suspend fun saveRunResult(
        runId: String,
        conversationId: String?,
        startedAt: Long,
        result: AgentRunResult,
    ) {
        withContext(Dispatchers.IO) {
            val updatedAt = System.currentTimeMillis()
            aiAgentDao.upsertRun(
                AiAgentRun(
                    id = runId,
                    conversationId = conversationId,
                    status = result.status.name,
                    taskType = result.request.taskType,
                    providerId = result.request.model.provider.id,
                    modelId = result.request.model.modelId,
                    finalTextPreview = result.finalText.sanitizeForAgentAudit(MAX_PREVIEW_CHARS),
                    errorMessage = result.errorMessage?.sanitizeForAgentAudit(MAX_PREVIEW_CHARS),
                    pendingProposalId = result.pendingProposal?.id,
                    startedAt = startedAt,
                    updatedAt = updatedAt,
                    traceCount = result.trace.size,
                    toolResultCount = result.toolResults.size,
                )
            )
            aiAgentDao.deleteTraceForRun(runId)
            aiAgentDao.insertTrace(
                result.trace.map { step ->
                    AiAgentTrace(
                        runId = runId,
                        stepIndex = step.index,
                        type = step.type,
                        content = step.content.sanitizeForAgentAudit(),
                        toolName = step.toolName,
                        callId = step.callId,
                    )
                }
            )
            result.pendingProposal?.let { proposal -> saveProposalInternal(proposal, runId) }
        }
    }

    override suspend fun saveProposal(proposal: AgentActionProposal, runId: String?) =
        withContext(Dispatchers.IO) {
            saveProposalInternal(proposal, runId)
        }

    override suspend fun markProposalResolved(proposalId: String, status: String) =
        withContext(Dispatchers.IO) {
            aiAgentDao.updateProposalStatus(
                proposalId = proposalId,
                status = status,
                resolvedAt = System.currentTimeMillis(),
            )
        }

    override suspend fun saveAudit(record: AgentAuditRecord) =
        withContext(Dispatchers.IO) {
            aiAgentDao.insertAudit(record.toEntity())
        }

    private suspend fun saveProposalInternal(proposal: AgentActionProposal, runId: String?) {
        aiAgentDao.upsertProposal(proposal.toEntity(runId))
    }

    private fun AgentActionProposal.toEntity(runId: String?): AiAgentProposal {
        val sanitizedToolCalls = toolCalls.map { preview ->
            preview.copy(argumentsPreview = preview.argumentsPreview.sanitizeForAgentAudit())
        }
        return AiAgentProposal(
            id = id,
            runId = runId,
            conversationId = conversationId,
            status = AgentProposalStatus.PENDING,
            toolCount = toolCalls.size,
            toolCallsJson = GSON.toJson(sanitizedToolCalls).sanitizeForAgentAudit(),
            proposalHash = proposalHash,
            argsHash = argsHash,
            createdAt = createdAt,
            expiresAt = expiresAt,
            resolvedAt = null,
        )
    }

    private fun AgentAuditRecord.toEntity(): AiAgentAudit {
        return AiAgentAudit(
            id = id,
            runId = runId,
            proposalId = proposalId,
            conversationId = conversationId,
            callId = callId,
            toolName = toolName,
            risk = risk.name,
            capabilitiesCsv = capabilities
                .map { it.name }
                .sorted()
                .joinToString(","),
            approvalScope = approvalScope.name,
            status = status,
            requestPreview = requestPreview.sanitizeForAgentAudit(),
            resultPreview = resultPreview?.sanitizeForAgentAudit(),
            errorMessage = errorMessage?.sanitizeForAgentAudit(MAX_PREVIEW_CHARS),
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMs = durationMs,
        )
    }

    companion object {
        private const val MAX_PREVIEW_CHARS = 4_000
    }
}
