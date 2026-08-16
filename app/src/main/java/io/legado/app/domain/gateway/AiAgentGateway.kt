package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiAgentProposal
import io.legado.app.data.entities.AiAgentRun
import io.legado.app.data.entities.AiAgentTrace
import io.legado.app.data.entities.AiAgentAudit
import io.legado.app.domain.agent.AgentAuditRecord
import io.legado.app.domain.agent.AgentActionProposal
import io.legado.app.domain.agent.AgentRunResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AiAgentGateway {
    fun observeRecentRuns(limit: Int): Flow<List<AiAgentRun>>
    fun observeTrace(runId: String): Flow<List<AiAgentTrace>>
    fun observePendingProposals(): Flow<List<AiAgentProposal>>
    fun observeRecentProposals(limit: Int): Flow<List<AiAgentProposal>> = observePendingProposals()
    fun observeRecentAudits(limit: Int): Flow<List<AiAgentAudit>> = flowOf(emptyList())
    suspend fun saveRunResult(
        runId: String,
        conversationId: String?,
        startedAt: Long,
        result: AgentRunResult,
    )
    suspend fun saveProposal(proposal: AgentActionProposal, runId: String? = null)
    suspend fun markProposalResolved(proposalId: String, status: String)
    suspend fun saveAudit(record: AgentAuditRecord) = Unit
}
