package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.domain.agent.AgentActionProposal
import io.legado.app.domain.agent.AgentActionRisk
import io.legado.app.domain.agent.AgentApprovalScope
import io.legado.app.domain.agent.AgentAuditRecord
import io.legado.app.domain.agent.AgentAuditStatus
import io.legado.app.domain.agent.AgentRunResult
import io.legado.app.domain.agent.AgentRunStatus
import io.legado.app.domain.agent.AgentToolCallPreview
import io.legado.app.domain.agent.AgentToolCapability
import io.legado.app.domain.agent.AgentTraceStep
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiAgentRepositoryAuditTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AiAgentRepository

    @Before
    fun setUp() {
        val application: Application = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AiAgentRepository(database.aiAgentDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveAuditRedactsSensitiveFieldsBeforePersisting() = runBlocking {
        repository.saveAudit(
            AgentAuditRecord(
                id = "audit_1",
                runId = null,
                proposalId = null,
                conversationId = "chat_1",
                callId = "call_1",
                toolName = "save_memory",
                risk = AgentActionRisk.WRITE,
                capabilities = setOf(AgentToolCapability.READ, AgentToolCapability.WRITE),
                approvalScope = AgentApprovalScope.ONE_TIME,
                status = AgentAuditStatus.APPROVED,
                requestPreview = """{"cookie":"raw-cookie","value":"safe"}""",
                resultPreview = """{"access_token":"raw-token"}""",
                errorMessage = "Bearer raw-bearer-token",
                startedAt = 10L,
                finishedAt = 15L,
                durationMs = 5L,
            )
        )

        val audit = repository.observeRecentAudits(10).first().single()

        assertEquals("audit_1", audit.id)
        assertEquals("READ,WRITE", audit.capabilitiesCsv)
        assertEquals(5L, audit.durationMs)
        assertFalse(audit.requestPreview.contains("raw-cookie"))
        assertFalse(audit.resultPreview.orEmpty().contains("raw-token"))
        assertFalse(audit.errorMessage.orEmpty().contains("raw-bearer-token"))
        assertTrue(audit.requestPreview.contains("[REDACTED]"))
    }

    @Test
    fun saveRunAndProposalRedactSensitivePayloadsBeforePersisting() = runBlocking {
        repository.saveRunResult(
            runId = "run_1",
            conversationId = "chat_1",
            startedAt = 10L,
            result = AgentRunResult(
                status = AgentRunStatus.ERROR,
                finalText = "failed with cookie=raw-cookie",
                request = agentRequest(),
                trace = listOf(
                    AgentTraceStep(
                        index = 0,
                        type = "tool_call",
                        content = """{"Authorization":"Bearer raw-token"}""",
                        toolName = "save_memory",
                        callId = "call_1",
                    )
                ),
                errorMessage = "apiKey=sk-1234567890abcdefghijkl",
            ),
        )
        repository.saveProposal(
            AgentActionProposal(
                id = "proposal_1",
                conversationId = "chat_1",
                toolCalls = listOf(
                    AgentToolCallPreview(
                        callId = "call_1",
                        toolName = "save_memory",
                        argumentsPreview = """{"cookie":"raw-cookie"}""",
                        risk = AgentActionRisk.WRITE,
                        callHash = "hash_1",
                    )
                ),
                proposalHash = "proposal_hash",
                argsHash = "args_hash",
                createdAt = 20L,
                expiresAt = 30L,
            ),
            runId = "run_1",
        )

        val run = repository.observeRecentRuns(10).first().single()
        val trace = repository.observeTrace("run_1").first().single()
        val proposal = repository.observePendingProposals().first().single()

        assertFalse(run.finalTextPreview.contains("raw-cookie"))
        assertFalse(run.errorMessage.orEmpty().contains("sk-1234567890abcdefghijkl"))
        assertFalse(trace.content.contains("raw-token"))
        assertFalse(proposal.toolCallsJson.contains("raw-cookie"))
        assertTrue(run.finalTextPreview.contains("[REDACTED]"))
        assertTrue(trace.content.contains("[REDACTED]"))
        assertTrue(proposal.toolCallsJson.contains("[REDACTED]"))
    }

    private fun agentRequest(): AiGenerateRequest =
        AiGenerateRequest(
            model = AiModelConfig(
                id = "model",
                provider = AiProviderConfig(
                    id = "provider",
                    name = "Provider",
                    protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://example.invalid",
                    apiKey = "sk-1234567890abcdefghijkl",
                ),
                displayName = "Model",
                modelId = "model",
            ),
            messages = listOf(AiMessage(AiMessageRole.USER, "hello")),
            taskType = AiTaskType.CHAT,
        )
}
