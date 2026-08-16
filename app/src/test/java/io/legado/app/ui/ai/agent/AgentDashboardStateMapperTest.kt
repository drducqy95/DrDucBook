package io.legado.app.ui.ai.agent

import io.legado.app.domain.agent.AgentActionRisk
import io.legado.app.domain.agent.AgentSkillSnapshot
import io.legado.app.domain.agent.AgentSkillVersionSnapshot
import io.legado.app.data.entities.AiAgentAudit
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiAgentProposal
import io.legado.app.data.entities.AiAgentRun
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.ui.ai.context.AiScreenContextSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentDashboardStateMapperTest {

    @Test
    fun buildStateSummarizesContextAndToolRisks() {
        val state = buildAgentDashboardUiState(
            chatBubbleEnabled = true,
            context = AiScreenContextSnapshot(
                ownerId = "main",
                screen = "MainRouteBookInfo",
                attributes = mapOf(
                    "bookUrl" to "book://1",
                    "origin" to "source",
                ),
                sensitive = true,
            ),
            tools = listOf(
                AiToolDefinition("save_memory", "Save memory", emptyMap()),
                AiToolDefinition("search_bookshelf", "Search bookshelf", emptyMap()),
                AiToolDefinition("delete_memory", "Delete memory", emptyMap()),
            ),
            recentMemories = listOf(
                AiMemory(
                    conversationId = "",
                    key = "genre",
                    value = "xianxia",
                    scope = AiMemory.SCOPE_GLOBAL,
                    scopeId = "",
                    type = AiMemory.TYPE_PREFERENCE,
                    pinned = true,
                )
            ),
            skills = listOf(
                AgentSkillSnapshot(
                    id = "skill_1",
                    slug = "chapter_reader",
                    name = "Chapter reader",
                    description = "Read cached chapters",
                    enabled = true,
                    activeVersionId = "version_1",
                    versions = listOf(
                        AgentSkillVersionSnapshot(
                            id = "version_1",
                            version = "1.0.0",
                            name = "Chapter reader",
                            description = "Read cached chapters",
                            instructions = "Read first",
                            allowedTools = listOf("get_chapter_content"),
                            requirements = emptyList(),
                            valid = true,
                            validationMessage = "",
                            createdAt = 1L,
                        )
                    ),
                    createdAt = 1L,
                    updatedAt = 1L,
                )
            ),
            selectedSkillId = "skill_1",
            recentRuns = listOf(
                AiAgentRun(
                    id = "run_1",
                    conversationId = "chat_1",
                    status = "FINAL",
                    taskType = "chat",
                    providerId = "provider_1",
                    modelId = "model_1",
                    finalTextPreview = "done",
                    errorMessage = null,
                    pendingProposalId = null,
                    startedAt = 10L,
                    updatedAt = 20L,
                    traceCount = 2,
                    toolResultCount = 1,
                )
            ),
            recentProposals = listOf(
                AiAgentProposal(
                    id = "proposal_1",
                    runId = "run_1",
                    conversationId = "chat_1",
                    status = "APPROVED",
                    toolCount = 1,
                    toolCallsJson = "[]",
                    proposalHash = "redacted",
                    argsHash = "redacted",
                    createdAt = 10L,
                    expiresAt = 100L,
                    resolvedAt = 20L,
                )
            ),
            recentAudits = listOf(
                AiAgentAudit(
                    id = "audit_1",
                    runId = "run_1",
                    proposalId = "proposal_1",
                    conversationId = "chat_1",
                    callId = "call_1",
                    toolName = "save_memory",
                    risk = "WRITE",
                    capabilitiesCsv = "READ,WRITE",
                    approvalScope = "ONE_TIME",
                    status = "APPROVED",
                    requestPreview = "{}",
                    resultPreview = """{"ok":true}""",
                    errorMessage = null,
                    startedAt = 10L,
                    finishedAt = 12L,
                    durationMs = 2L,
                )
            ),
            riskFor = { name ->
                when (name) {
                    "delete_memory" -> AgentActionRisk.DELETE
                    "save_memory" -> AgentActionRisk.WRITE
                    else -> AgentActionRisk.READ
                }
            },
            requiresApproval = { name -> name != "search_bookshelf" },
        )

        assertTrue(state.chatBubbleEnabled)
        assertTrue(state.context.hasContext)
        assertTrue(state.context.sensitive)
        assertEquals("MainRouteBookInfo", state.context.screen)
        assertEquals("bookUrl", state.context.attributes.first().key)
        assertEquals(3, state.toolCount)
        assertEquals(3, state.enabledToolCount)
        assertEquals(1, state.readToolCount)
        assertEquals(2, state.approvalToolCount)
        assertEquals("delete_memory", state.tools.first().name)
        assertEquals(AgentActionRisk.DELETE, state.tools.first().risk)
        assertEquals(1, state.memoryCount)
        assertEquals(1, state.pinnedMemoryCount)
        assertEquals("genre", state.recentMemories.first().key)
        assertEquals(AiMemory.TYPE_PREFERENCE, state.recentMemories.first().type)
        assertEquals(1, state.skillCount)
        assertEquals(1, state.enabledSkillCount)
        assertEquals("1.0.0", state.selectedSkill?.activeVersion)
        assertEquals(listOf("get_chapter_content"), state.skills.first().allowedTools)
        assertEquals("provider_1", state.recentRuns.single().providerId)
        assertEquals("APPROVED", state.recentProposals.single().status)
        assertEquals(1, state.auditCount)
        assertEquals("save_memory", state.recentAudits.single().toolName)
        assertEquals(2L, state.recentAudits.single().durationMs)
    }

    @Test
    fun buildStateUsesEmptyContextWhenRegistryHasNoCurrentScreen() {
        val state = buildAgentDashboardUiState(
            chatBubbleEnabled = false,
            context = null,
            tools = emptyList(),
            riskFor = { AgentActionRisk.READ },
            requiresApproval = { false },
        )

        assertFalse(state.chatBubbleEnabled)
        assertFalse(state.context.hasContext)
        assertEquals(0, state.toolCount)
        assertEquals(0, state.enabledToolCount)
        assertEquals(0, state.readToolCount)
        assertEquals(0, state.approvalToolCount)
    }

    @Test
    fun buildStateSeparatesRegisteredAndEnabledTools() {
        val state = buildAgentDashboardUiState(
            chatBubbleEnabled = false,
            context = null,
            tools = listOf(
                AiToolDefinition("list_agent_skills", "List skills", emptyMap()),
                AiToolDefinition("search_books", "Search books", emptyMap()),
                AiToolDefinition("save_memory", "Save memory", emptyMap()),
            ),
            enabledToolNames = setOf("search_books"),
            riskFor = { name ->
                if (name == "save_memory") AgentActionRisk.WRITE else AgentActionRisk.READ
            },
            requiresApproval = { name -> name == "save_memory" },
        )

        assertEquals(3, state.toolCount)
        assertEquals(1, state.enabledToolCount)
        assertEquals(2, state.readToolCount)
        assertEquals(1, state.approvalToolCount)
        assertTrue(state.tools.single { it.name == "search_books" }.enabled)
        assertFalse(state.tools.single { it.name == "list_agent_skills" }.enabled)
        assertFalse(state.tools.single { it.name == "save_memory" }.enabled)
    }
}
