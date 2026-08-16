package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiAgentAudit
import io.legado.app.data.entities.AiAgentProposal
import io.legado.app.data.entities.AiAgentRun
import io.legado.app.data.entities.AiAgentTrace
import io.legado.app.domain.agent.AgentAuditRecord
import io.legado.app.domain.agent.AgentAuditStatus
import io.legado.app.domain.agent.AgentActionProposal
import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.agent.AgentPermissionException
import io.legado.app.domain.agent.AgentProposalStatus
import io.legado.app.domain.agent.AgentToolCapability
import io.legado.app.domain.agent.AgentRunResult
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteApprovedAgentActionUseCaseTest {

    @Test
    fun approvedActionExecutesThenMarksProposalApproved() = runBlocking {
        val broker = AgentPermissionBroker()
        val agentGateway = RecordingAgentGateway()
        val toolGateway = GuardedToolGateway(broker)
        val call = mutationCall("book-a")
        val proposal = broker.createProposal("chat-1", listOf(call))

        val result = ExecuteApprovedAgentActionUseCase(
            broker,
            toolGateway,
            agentGateway,
        )(proposal, listOf(call))

        assertEquals(1, toolGateway.mutationCount)
        assertEquals(listOf(AgentProposalStatus.APPROVED), agentGateway.statuses)
        assertEquals("save_memory", result.audit.single().toolName)
        assertEquals("saved", result.audit.single().after)
        assertEquals(AgentAuditStatus.APPROVED, agentGateway.audits.single().status)
    }

    @Test
    fun changedArgumentsAreRejectedWithoutMutationAndMarkedFailed() {
        val broker = AgentPermissionBroker()
        val agentGateway = RecordingAgentGateway()
        val toolGateway = GuardedToolGateway(broker)
        val approvedCall = mutationCall("book-a")
        val changedCall = mutationCall("book-b")
        val proposal = broker.createProposal("chat-1", listOf(approvedCall))

        assertThrows(AgentPermissionException::class.java) {
            runBlocking {
                ExecuteApprovedAgentActionUseCase(
                    broker,
                    toolGateway,
                    agentGateway,
                )(proposal, listOf(changedCall))
            }
        }

        assertEquals(0, toolGateway.mutationCount)
        assertEquals(listOf(AgentProposalStatus.FAILED), agentGateway.statuses)
        assertEquals(AgentAuditStatus.DENIED, agentGateway.audits.single().status)
    }

    @Test
    fun approvedActionAuditRedactsSensitiveRequestAndResult() = runBlocking {
        val broker = AgentPermissionBroker()
        val agentGateway = RecordingAgentGateway()
        val toolGateway = GuardedToolGateway(
            broker = broker,
            resultContent = """{"ok":true,"access_token":"result-secret"}""",
        )
        val call = AiToolCall(
            id = "call-1",
            name = "save_memory",
            arguments = """{"key":"book","value":"safe","cookie":"raw-cookie","apiKey":"sk-1234567890abcdefghijkl"}""",
        )
        val proposal = broker.createProposal("chat-1", listOf(call))

        ExecuteApprovedAgentActionUseCase(
            broker,
            toolGateway,
            agentGateway,
        )(proposal, listOf(call))

        val audit = agentGateway.audits.single()
        assertEquals(AgentAuditStatus.APPROVED, audit.status)
        assertEquals(setOf(AgentToolCapability.READ, AgentToolCapability.WRITE), audit.capabilities)
        assertEquals("chat-1", audit.conversationId)
        assertTrue(audit.proposalId.orEmpty().startsWith("proposal_"))
        assertFalse(audit.requestPreview.contains("raw-cookie"))
        assertFalse(audit.requestPreview.contains("sk-1234567890abcdefghijkl"))
        assertFalse(audit.resultPreview.orEmpty().contains("result-secret"))
        assertTrue(audit.requestPreview.contains("[REDACTED]"))
        assertTrue(audit.durationMs >= 0L)
    }

    @Test
    fun deniedActionAuditRedactsChangedArguments() {
        val broker = AgentPermissionBroker()
        val agentGateway = RecordingAgentGateway()
        val toolGateway = GuardedToolGateway(broker)
        val approvedCall = mutationCall("book-a")
        val changedCall = AiToolCall(
            id = "call-1",
            name = "save_memory",
            arguments = """{"key":"book","value":"book-b","cookie":"changed-cookie"}""",
        )
        val proposal = broker.createProposal("chat-1", listOf(approvedCall))

        assertThrows(AgentPermissionException::class.java) {
            runBlocking {
                ExecuteApprovedAgentActionUseCase(
                    broker,
                    toolGateway,
                    agentGateway,
                )(proposal, listOf(changedCall))
            }
        }

        val audit = agentGateway.audits.single()
        assertEquals(AgentAuditStatus.DENIED, audit.status)
        assertFalse(audit.requestPreview.contains("changed-cookie"))
        assertTrue(audit.requestPreview.contains("[REDACTED]"))
    }

    private fun mutationCall(value: String) = AiToolCall(
        id = "call-1",
        name = "save_memory",
        arguments = "{\"key\":\"book\",\"value\":\"$value\"}",
    )
}

private class GuardedToolGateway(
    private val broker: AgentPermissionBroker,
    private val resultContent: String = "saved",
) : AiToolGateway {
    var mutationCount = 0

    override fun availableTools(): List<AiToolDefinition> = emptyList()

    override fun requiresConfirmation(toolName: String): Boolean = broker.requiresApproval(toolName)

    override suspend fun execute(
        call: AiToolCall,
        approval: io.legado.app.domain.agent.AgentActionApproval?,
        conversationId: String?,
    ): AiToolResult {
        broker.requireCanExecute(call, approval, conversationId)
        mutationCount++
        return AiToolResult(callId = call.id, name = call.name, content = resultContent)
    }
}

private class RecordingAgentGateway : AiAgentGateway {
    val statuses = mutableListOf<String>()
    val audits = mutableListOf<AgentAuditRecord>()

    override fun observeRecentRuns(limit: Int): Flow<List<AiAgentRun>> = flowOf(emptyList())
    override fun observeTrace(runId: String): Flow<List<AiAgentTrace>> = flowOf(emptyList())
    override fun observePendingProposals(): Flow<List<AiAgentProposal>> = flowOf(emptyList())
    override fun observeRecentAudits(limit: Int): Flow<List<AiAgentAudit>> = flowOf(emptyList())

    override suspend fun saveRunResult(
        runId: String,
        conversationId: String?,
        startedAt: Long,
        result: AgentRunResult,
    ) = Unit

    override suspend fun saveProposal(proposal: AgentActionProposal, runId: String?) = Unit

    override suspend fun markProposalResolved(proposalId: String, status: String) {
        statuses += status
    }

    override suspend fun saveAudit(record: AgentAuditRecord) {
        audits += record
    }
}
