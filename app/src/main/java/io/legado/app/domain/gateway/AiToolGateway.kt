package io.legado.app.domain.gateway

import io.legado.app.domain.agent.AgentActionApproval
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult

interface AiToolGateway {
    fun registeredTools(): List<AiToolDefinition> = availableTools()
    fun availableTools(): List<AiToolDefinition>
    fun requiresConfirmation(toolName: String): Boolean
    suspend fun execute(
        call: AiToolCall,
        approval: AgentActionApproval? = null,
        conversationId: String? = approval?.conversationId,
    ): AiToolResult
}
