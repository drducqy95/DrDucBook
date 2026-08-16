package io.legado.app.domain.usecase

import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.agent.AgentRunResult
import io.legado.app.domain.agent.AgentRunStatus
import io.legado.app.domain.agent.AgentToolLoopGuard
import io.legado.app.domain.agent.AgentTraceStep
import io.legado.app.domain.agent.withCanonicalToolName
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolResult
import kotlinx.coroutines.CancellationException
import java.util.UUID

class RunAiAgentUseCase(
    private val aiTextGateway: AiTextGateway,
    private val aiToolGateway: AiToolGateway,
    private val agentPermissionBroker: AgentPermissionBroker,
    private val aiAgentGateway: AiAgentGateway,
) {

    suspend operator fun invoke(
        request: AiGenerateRequest,
        conversationId: String? = request.routeSessionKey,
        maxSteps: Int = DEFAULT_MAX_STEPS,
    ): AgentRunResult {
        val trace = mutableListOf<AgentTraceStep>()
        val toolResults = mutableListOf<AiToolResult>()
        val finalText = StringBuilder()
        val loopGuard = AgentToolLoopGuard(LOOP_DETECTION_COUNT)
        var currentRequest = request.withAgentTools()
        val runId = "run_${UUID.randomUUID().toString().replace("-", "")}"
        val startedAt = System.currentTimeMillis()

        suspend fun finish(result: AgentRunResult): AgentRunResult {
            runCatching {
                aiAgentGateway.saveRunResult(
                    runId = runId,
                    conversationId = conversationId,
                    startedAt = startedAt,
                    result = result,
                )
            }
            return result
        }

        return try {
            var step = 0
            while (step < maxSteps) {
                val toolTrace = ToolTraceBuilder()
                val roundText = StringBuilder()
                toolTrace.beginResponse()

                aiTextGateway.generateStream(currentRequest).collect { event ->
                    when (event) {
                        is AiStreamEvent.Content -> {
                            roundText.append(event.text)
                            finalText.append(event.text)
                        }

                        is AiStreamEvent.Reasoning -> trace += traceStep(
                            trace,
                            type = TRACE_REASONING,
                            content = event.text,
                        )

                        is AiStreamEvent.ToolCallDelta -> toolTrace.append(event)
                    }
                }

                roundText.toString().takeIf { it.isNotBlank() }?.let {
                    trace += traceStep(trace, TRACE_RESPONSE, it)
                }

                val toolCalls = toolTrace.pendingToolCalls().map { it.withCanonicalToolName() }
                if (toolCalls.isEmpty()) {
                    return finish(AgentRunResult(
                        status = AgentRunStatus.FINAL,
                        finalText = finalText.toString(),
                        request = currentRequest,
                        trace = trace,
                        toolResults = toolResults,
                    ))
                }

                val mutationCalls = toolCalls.filter { agentPermissionBroker.requiresApproval(it.name) }
                if (mutationCalls.isNotEmpty()) {
                    val proposal = agentPermissionBroker.createProposal(
                        conversationId = conversationId,
                        toolCalls = mutationCalls,
                    )
                    mutationCalls.forEach { call ->
                        trace += traceStep(
                            trace = trace,
                            type = TRACE_PROPOSAL,
                            content = call.arguments,
                            toolName = call.name,
                            callId = call.id,
                        )
                    }
                    return finish(AgentRunResult(
                        status = AgentRunStatus.WAITING_FOR_APPROVAL,
                        finalText = finalText.toString(),
                        request = currentRequest,
                        trace = trace,
                        pendingProposal = proposal,
                        pendingToolCalls = mutationCalls,
                        toolResults = toolResults,
                    ))
                }

                val resultMessages = toolCalls.map { call ->
                    if (loopGuard.recordAndIsLoop(call)) {
                        val message = "Repeated tool call without progress: ${call.name}"
                        trace += traceStep(
                            trace = trace,
                            type = TRACE_LOOP_DETECTED,
                            content = message,
                            toolName = call.name,
                            callId = call.id,
                        )
                        return finish(AgentRunResult(
                            status = AgentRunStatus.LOOP_DETECTED,
                            finalText = finalText.toString(),
                            request = currentRequest,
                            trace = trace,
                            toolResults = toolResults,
                            errorMessage = message,
                        ))
                    }

                    trace += traceStep(
                        trace = trace,
                        type = TRACE_TOOL_CALL,
                        content = call.arguments,
                        toolName = call.name,
                        callId = call.id,
                    )
                    val result = aiToolGateway.execute(
                        call = call,
                        conversationId = conversationId,
                    )
                    toolResults += result
                    val truncated = result.content.truncateToolOutput()
                    trace += traceStep(
                        trace = trace,
                        type = TRACE_TOOL_RESULT,
                        content = truncated,
                        toolName = result.name,
                        callId = result.callId,
                    )
                    AiMessage(
                        role = AiMessageRole.TOOL,
                        content = truncated,
                        toolCallId = result.callId,
                        name = result.name,
                    )
                }

                currentRequest = currentRequest.copy(
                    messages = currentRequest.messages +
                        AiMessage(
                            role = AiMessageRole.ASSISTANT,
                            content = roundText.toString(),
                            toolCalls = toolCalls,
                        ) +
                        resultMessages,
                )
                step += 1
            }

            trace += traceStep(
                trace = trace,
                type = TRACE_MAX_STEPS,
                content = "Agent reached the max-step limit",
            )
            finish(AgentRunResult(
                status = AgentRunStatus.MAX_STEPS,
                finalText = finalText.toString(),
                request = currentRequest,
                trace = trace,
                toolResults = toolResults,
                errorMessage = "Agent reached the max-step limit",
            ))
        } catch (_: CancellationException) {
            finish(AgentRunResult(
                status = AgentRunStatus.CANCELLED,
                finalText = finalText.toString(),
                request = currentRequest,
                trace = trace,
                toolResults = toolResults,
            ))
        } catch (error: Throwable) {
            finish(AgentRunResult(
                status = AgentRunStatus.ERROR,
                finalText = finalText.toString(),
                request = currentRequest,
                trace = trace,
                toolResults = toolResults,
                errorMessage = error.message ?: error::class.java.simpleName,
            ))
        }
    }

    private fun AiGenerateRequest.withAgentTools(): AiGenerateRequest {
        if (tools.isNotEmpty()) return this
        return copy(tools = aiToolGateway.availableTools())
    }

    private fun traceStep(
        trace: List<AgentTraceStep>,
        type: String,
        content: String,
        toolName: String? = null,
        callId: String? = null,
    ): AgentTraceStep {
        return AgentTraceStep(
            index = trace.size,
            type = type,
            content = content,
            toolName = toolName,
            callId = callId,
        )
    }

    companion object {
        const val DEFAULT_MAX_STEPS = 12
        const val LOOP_DETECTION_COUNT = 3

        const val TRACE_REASONING = "reasoning"
        const val TRACE_RESPONSE = "response"
        const val TRACE_TOOL_CALL = "tool_call"
        const val TRACE_TOOL_RESULT = "tool_result"
        const val TRACE_PROPOSAL = "proposal"
        const val TRACE_LOOP_DETECTED = "loop_detected"
        const val TRACE_MAX_STEPS = "max_steps"
    }
}
