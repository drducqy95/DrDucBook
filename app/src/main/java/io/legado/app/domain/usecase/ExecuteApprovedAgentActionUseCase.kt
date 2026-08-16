package io.legado.app.domain.usecase

import io.legado.app.domain.agent.AgentActionAuditEntry
import io.legado.app.domain.agent.AgentApprovalScope
import io.legado.app.domain.agent.AgentAuditRecord
import io.legado.app.domain.agent.AgentAuditStatus
import io.legado.app.domain.agent.AgentActionProposal
import io.legado.app.domain.agent.AgentPermissionException
import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.agent.AgentProposalStatus
import io.legado.app.domain.agent.ApprovedAgentActionResult
import io.legado.app.domain.agent.sanitizeForAgentAudit
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolResult
import java.util.UUID

class ExecuteApprovedAgentActionUseCase(
    private val permissionBroker: AgentPermissionBroker,
    private val aiToolGateway: AiToolGateway,
    private val aiAgentGateway: AiAgentGateway,
) {

    suspend operator fun invoke(
        proposal: AgentActionProposal,
        toolCalls: List<AiToolCall>,
        approvalScope: AgentApprovalScope = AgentApprovalScope.ONE_TIME,
    ): ApprovedAgentActionResult {
        val approval = try {
            permissionBroker.approve(proposal, approvalScope)
        } catch (error: Throwable) {
            toolCalls.forEach { call ->
                val now = System.currentTimeMillis()
                saveAuditSafely(
                    call = call,
                    proposal = proposal,
                    scope = approvalScope,
                    status = AgentAuditStatus.DENIED,
                    result = null,
                    error = error,
                    startedAt = now,
                    finishedAt = now,
                )
            }
            runCatching {
                aiAgentGateway.markProposalResolved(proposal.id, AgentProposalStatus.FAILED)
            }
            throw error
        }
        val audit = mutableListOf<AgentActionAuditEntry>()
        val results = mutableListOf<AiToolResult>()
        try {
            toolCalls.forEach { call ->
                val startedAt = System.currentTimeMillis()
                val result = try {
                    aiToolGateway.execute(call, approval, proposal.conversationId)
                } catch (error: Throwable) {
                    val finishedAt = System.currentTimeMillis()
                    saveAuditSafely(
                        call = call,
                        proposal = proposal,
                        scope = approvalScope,
                        status = if (error is AgentPermissionException) {
                            AgentAuditStatus.DENIED
                        } else {
                            AgentAuditStatus.FAILED
                        },
                        result = null,
                        error = error,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                    )
                    throw error
                }
                val finishedAt = System.currentTimeMillis()
                saveAuditSafely(
                    call = call,
                    proposal = proposal,
                    scope = approvalScope,
                    status = AgentAuditStatus.APPROVED,
                    result = result,
                    error = null,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                )
                audit += AgentActionAuditEntry(
                    callId = call.id,
                    toolName = call.name,
                    risk = permissionBroker.riskFor(call.name),
                    before = call.arguments.sanitizeForAgentAudit(MAX_AUDIT_CHARS),
                    after = result.content.sanitizeForAgentAudit(MAX_AUDIT_CHARS),
                )
                results += result
            }
        } catch (error: Throwable) {
            runCatching {
                aiAgentGateway.markProposalResolved(
                    proposal.id,
                    if (audit.isEmpty()) AgentProposalStatus.FAILED else AgentProposalStatus.PARTIAL,
                )
            }
            throw error
        }
        // The mutation has already committed at this point. An audit storage failure must not
        // make the caller retry a one-time action and accidentally duplicate user data.
        runCatching {
            aiAgentGateway.markProposalResolved(proposal.id, AgentProposalStatus.APPROVED)
        }
        return ApprovedAgentActionResult(toolResults = results, audit = audit)
    }

    private suspend fun saveAuditSafely(
        call: AiToolCall,
        proposal: AgentActionProposal,
        scope: AgentApprovalScope,
        status: String,
        result: AiToolResult?,
        error: Throwable?,
        startedAt: Long,
        finishedAt: Long,
    ) {
        val duration = (finishedAt - startedAt).coerceAtLeast(0L)
        runCatching {
            aiAgentGateway.saveAudit(
                AgentAuditRecord(
                    id = "audit_${UUID.randomUUID().toString().replace("-", "")}",
                    runId = null,
                    proposalId = proposal.id,
                    conversationId = proposal.conversationId,
                    callId = call.id,
                    toolName = call.name,
                    risk = permissionBroker.riskFor(call.name),
                    capabilities = permissionBroker.capabilitiesFor(call.name),
                    approvalScope = scope,
                    status = status,
                    requestPreview = call.arguments.sanitizeForAgentAudit(MAX_AUDIT_CHARS),
                    resultPreview = result?.content?.sanitizeForAgentAudit(MAX_AUDIT_CHARS),
                    errorMessage = error?.message?.sanitizeForAgentAudit(MAX_AUDIT_CHARS),
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    durationMs = duration,
                )
            )
        }
    }

    companion object {
        private const val MAX_AUDIT_CHARS = 4_000
    }
}
