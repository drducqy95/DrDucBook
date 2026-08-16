package io.legado.app.domain.gateway

import io.legado.app.domain.agenttools.CustomAgentToolDraft
import io.legado.app.domain.agenttools.CustomAgentToolFixtureResult
import io.legado.app.domain.agenttools.CustomAgentToolSnapshot
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult
import kotlinx.coroutines.flow.Flow

interface CustomAgentToolGateway {
    fun observeTools(): Flow<List<CustomAgentToolSnapshot>>
    fun registeredToolDefinitions(): List<AiToolDefinition>
    fun availableToolDefinitions(): List<AiToolDefinition>
    suspend fun createDraft(draft: CustomAgentToolDraft): CustomAgentToolSnapshot
    suspend fun validateLatestDraft(toolId: String): CustomAgentToolSnapshot
    suspend fun runFixture(toolId: String): CustomAgentToolFixtureResult
    suspend fun approveLatestVersion(toolId: String): CustomAgentToolSnapshot
    suspend fun setEnabled(toolId: String, enabled: Boolean): CustomAgentToolSnapshot
    suspend fun rollback(toolId: String): CustomAgentToolSnapshot
    suspend fun delete(toolId: String)
    suspend fun execute(call: AiToolCall): AiToolResult?
}
