package io.legado.app.ui.ai.chat

import io.legado.app.domain.agent.sanitizeForAgentAudit
import io.legado.app.domain.model.AiToolCall

internal fun buildAiToolConfirmation(
    toolCalls: List<AiToolCall>,
    proposalId: String,
): AiToolConfirmationUi {
    val description = buildString {
        append("Proposal: ")
        append(proposalId)
        toolCalls.forEachIndexed { index, call ->
            append("\n\n")
            append(index + 1)
            append(". ")
            append(call.name)
            append('\n')
            append(call.arguments.sanitizeForAgentAudit(Int.MAX_VALUE))
        }
    }
    return AiToolConfirmationUi(
        title = toolCalls.joinToString { call -> call.name },
        description = description,
        requestCount = toolCalls.size,
    )
}
