package io.legado.app.domain.usecase

import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.agent.AgentActionProposal
import io.legado.app.domain.agent.AgentRunStatus
import io.legado.app.domain.agent.AgentRunResult
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.data.entities.AiAgentProposal
import io.legado.app.data.entities.AiAgentRun
import io.legado.app.data.entities.AiAgentTrace
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RunAiAgentUseCaseTest {

    @Test
    fun finalAnswerStopsWithoutToolCalls() = runBlocking {
        val agentGateway = RecordingAgentGateway()
        val useCase = useCaseWith(
            streams = listOf(listOf(AiStreamEvent.Content("Xin chao"))),
            agentGateway = agentGateway,
        )

        val result = useCase(request())

        assertEquals(AgentRunStatus.FINAL, result.status)
        assertEquals("Xin chao", result.finalText)
        assertEquals(1, result.trace.size)
        assertEquals(listOf(AgentRunStatus.FINAL), agentGateway.saved.map { it.status })
    }

    @Test
    fun readToolExecutesAndContinuesToFinalAnswer() = runBlocking {
        val textGateway = RecordingTextGateway(
            streams = listOf(
                listOf(toolDelta("call_1", "search_books", """{"query":"book"}""")),
                listOf(AiStreamEvent.Content("Found one")),
            )
        )
        val toolGateway = RecordingToolGateway()
        val useCase = RunAiAgentUseCase(
            aiTextGateway = textGateway,
            aiToolGateway = toolGateway,
            agentPermissionBroker = AgentPermissionBroker(),
            aiAgentGateway = RecordingAgentGateway(),
        )

        val result = useCase(request())

        assertEquals(AgentRunStatus.FINAL, result.status)
        assertEquals("Found one", result.finalText)
        assertEquals(listOf("search_books"), toolGateway.executed.map { it.name })
        assertEquals(2, textGateway.requests.size)
        assertEquals(AiMessageRole.TOOL, textGateway.requests.last().messages.last().role)
        assertEquals(
            listOf(
                RunAiAgentUseCase.TRACE_TOOL_CALL,
                RunAiAgentUseCase.TRACE_TOOL_RESULT,
                RunAiAgentUseCase.TRACE_RESPONSE,
            ),
            result.trace.map { it.type },
        )
    }

    @Test
    fun mutationToolStopsAtApprovalProposal() = runBlocking {
        val toolGateway = RecordingToolGateway()
        val useCase = useCaseWith(
            streams = listOf(
                listOf(toolDelta("call_write", "save_memory", """{"key":"genre","value":"xianxia"}""")),
            ),
            toolGateway = toolGateway,
        )

        val result = useCase(request(conversationId = "chat_1"))

        assertEquals(AgentRunStatus.WAITING_FOR_APPROVAL, result.status)
        assertNotNull(result.pendingProposal)
        assertEquals(listOf("save_memory"), result.pendingToolCalls.map { it.name })
        assertEquals(emptyList<AiToolCall>(), toolGateway.executed)
    }

    @Test
    fun repeatedSameToolAndArgsStopsAsLoop() = runBlocking {
        val toolGateway = RecordingToolGateway()
        val useCase = useCaseWith(
            streams = listOf(
                listOf(toolDelta("call_1", "search_books", """{"query":"same"}""")),
                listOf(toolDelta("call_2", "search_books", """{"query":"same"}""")),
                listOf(toolDelta("call_3", "search_books", """{"query":"same"}""")),
            ),
            toolGateway = toolGateway,
        )

        val result = useCase(request())

        assertEquals(AgentRunStatus.LOOP_DETECTED, result.status)
        assertEquals(2, toolGateway.executed.size)
        assertEquals(RunAiAgentUseCase.TRACE_LOOP_DETECTED, result.trace.last().type)
        assertEquals("call_3", result.trace.last().callId)
    }

    @Test
    fun maxStepStopsLongToolRuns() = runBlocking {
        val toolGateway = RecordingToolGateway()
        val useCase = useCaseWith(
            streams = listOf(
                listOf(toolDelta("call_1", "search_books", """{"query":"one"}""")),
                listOf(toolDelta("call_2", "search_books", """{"query":"two"}""")),
            ),
            toolGateway = toolGateway,
        )

        val result = useCase(request(), maxSteps = 2)

        assertEquals(AgentRunStatus.MAX_STEPS, result.status)
        assertEquals(2, toolGateway.executed.size)
        assertEquals(RunAiAgentUseCase.TRACE_MAX_STEPS, result.trace.last().type)
    }

    @Test
    fun cancellationStopsAndPersistsCancelledRun() = runBlocking {
        val agentGateway = RecordingAgentGateway()
        val useCase = RunAiAgentUseCase(
            aiTextGateway = CancellingTextGateway(),
            aiToolGateway = RecordingToolGateway(),
            agentPermissionBroker = AgentPermissionBroker(),
            aiAgentGateway = agentGateway,
        )

        val result = useCase(request())

        assertEquals(AgentRunStatus.CANCELLED, result.status)
        assertEquals(listOf(AgentRunStatus.CANCELLED), agentGateway.saved.map { it.status })
    }

    private fun useCaseWith(
        streams: List<List<AiStreamEvent>>,
        toolGateway: RecordingToolGateway = RecordingToolGateway(),
        agentGateway: RecordingAgentGateway = RecordingAgentGateway(),
    ): RunAiAgentUseCase =
        RunAiAgentUseCase(
            aiTextGateway = RecordingTextGateway(streams),
            aiToolGateway = toolGateway,
            agentPermissionBroker = AgentPermissionBroker(),
            aiAgentGateway = agentGateway,
        )

    private fun request(conversationId: String? = null): AiGenerateRequest =
        AiGenerateRequest(
            model = AiModelConfig(
                id = "model",
                provider = AiProviderConfig(
                    id = "provider",
                    name = "Provider",
                    protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                    baseUrl = "https://example.invalid",
                    apiKey = "key",
                ),
                displayName = "Model",
                modelId = "model",
            ),
            messages = listOf(AiMessage(AiMessageRole.USER, "hello")),
            taskType = AiTaskType.CHAT,
            routeSessionKey = conversationId,
        )

    private fun toolDelta(id: String, name: String, arguments: String): AiStreamEvent.ToolCallDelta =
        AiStreamEvent.ToolCallDelta(
            id = id,
            index = 0,
            name = name,
            argumentsDelta = arguments,
            rawType = "tool_call",
        )

    private class RecordingTextGateway(
        streams: List<List<AiStreamEvent>>,
    ) : AiTextGateway {
        private val streams = ArrayDeque(streams)
        val requests = mutableListOf<AiGenerateRequest>()

        override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> {
            requests += request
            return Result.success(AiGenerateResponse("OK"))
        }

        override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
            requests += request
            streams.removeFirstOrNull().orEmpty().forEach { emit(it) }
        }

        override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> {
            return Result.success(emptyList())
        }
    }

    private class CancellingTextGateway : AiTextGateway {
        override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> {
            throw CancellationException("cancelled")
        }

        override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
            throw CancellationException("cancelled")
        }

        override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> {
            return Result.success(emptyList())
        }
    }

    private class RecordingToolGateway : AiToolGateway {
        val executed = mutableListOf<AiToolCall>()

        override fun availableTools(): List<AiToolDefinition> {
            return listOf(
                AiToolDefinition("search_books", "Search books", emptyMap()),
                AiToolDefinition("save_memory", "Save memory", emptyMap()),
            )
        }

        override fun requiresConfirmation(toolName: String): Boolean {
            return toolName == "save_memory"
        }

        override suspend fun execute(
            call: AiToolCall,
            approval: io.legado.app.domain.agent.AgentActionApproval?,
            conversationId: String?,
        ): AiToolResult {
            executed += call
            return AiToolResult(
                callId = call.id,
                name = call.name,
                content = """{"ok":true}""",
            )
        }
    }

    private class RecordingAgentGateway : AiAgentGateway {
        val saved = mutableListOf<AgentRunResult>()

        override fun observeRecentRuns(limit: Int): Flow<List<AiAgentRun>> = flowOf(emptyList())

        override fun observeTrace(runId: String): Flow<List<AiAgentTrace>> = flowOf(emptyList())

        override fun observePendingProposals(): Flow<List<AiAgentProposal>> = flowOf(emptyList())

        override suspend fun saveRunResult(
            runId: String,
            conversationId: String?,
            startedAt: Long,
            result: AgentRunResult,
        ) {
            saved += result
        }

        override suspend fun saveProposal(proposal: AgentActionProposal, runId: String?) = Unit

        override suspend fun markProposalResolved(proposalId: String, status: String) = Unit
    }
}
