package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessage
import io.legado.app.data.entities.AiAgentAudit
import io.legado.app.data.entities.AiAgentProposal
import io.legado.app.data.entities.AiAgentRun
import io.legado.app.data.entities.AiAgentTrace
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.domain.agent.AgentActionApproval
import io.legado.app.domain.agent.AgentActionProposal
import io.legado.app.domain.agent.AgentApprovalScope
import io.legado.app.domain.agent.AgentAuditRecord
import io.legado.app.domain.agent.AgentAuditStatus
import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.agent.AgentProposalStatus
import io.legado.app.domain.agent.AgentRunResult
import io.legado.app.domain.agent.AgentRunStatus
import io.legado.app.domain.agent.AgentSkillDraft
import io.legado.app.domain.agent.AgentSkillSnapshot
import io.legado.app.domain.agent.AgentSkillVersionSnapshot
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult
import io.legado.app.ui.ai.chat.AiChatMessageUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatGenerationUseCaseTest {

    @Test
    fun buildRequestExposesInternetBookAndPluginToolsToChatbot() = runBlocking {
        val useCase = useCaseWith()

        val request = useCase.buildRequest(
            userContent = "Tim truyen va tao plugin neu can",
            history = emptyList(),
            reasoningLevel = AiReasoningLevel.AUTO,
            conversationId = "chat_1",
        )

        val toolNames = request.tools.mapTo(mutableSetOf()) { it.name }
        assertTrue("search_internet" in toolNames)
        assertTrue("fetch_internet_page" in toolNames)
        assertTrue("search_book_sources" in toolNames)
        assertTrue("search_online_books" in toolNames)
        assertTrue("add_book_to_bookshelf" in toolNames)
        assertTrue("get_ai_runtime_status" in toolNames)
        assertTrue("get_ai_quota_status" in toolNames)
        assertTrue("diagnose_book_source" in toolNames)
        assertTrue("repair_book_source" in toolNames)
        assertTrue("create_vbook_plugin_draft" in toolNames)
        assertTrue("install_vbook_plugin" in toolNames)
        assertTrue("create_legado_book_source_draft" in toolNames)
        assertTrue("install_legado_book_source" in toolNames)
        assertEquals(AiTaskType.CHAT, request.taskType)
        assertEquals("chat_1", request.routeSessionKey)

        val systemPrompt = request.messages.first().content
        assertTrue(systemPrompt.contains("search_internet"))
        assertTrue(systemPrompt.contains("fetch_internet_page"))
        assertTrue(systemPrompt.contains("get_ai_runtime_status"))
        assertTrue(systemPrompt.contains("get_ai_quota_status"))
        assertTrue(systemPrompt.contains("diagnose_book_source"))
        assertTrue(systemPrompt.contains("repair_book_source"))
        assertTrue(systemPrompt.contains("create_vbook_plugin_draft"))
        assertTrue(systemPrompt.contains("install_vbook_plugin"))
        assertTrue(systemPrompt.contains("create_legado_book_source_draft"))
        assertTrue(systemPrompt.contains("install_legado_book_source"))
        assertTrue(systemPrompt.contains("sourceJson"))
        assertTrue(systemPrompt.contains("enableAfterInstall"))
    }

    @Test
    fun addBookAndPluginToolsRequireApprovalButCanResumeAfterApproval() = runBlocking {
        val broker = AgentPermissionBroker()
        val toolGateway = PermissionedToolGateway(broker)
        val useCase = useCaseWith(
            broker = broker,
            toolGateway = toolGateway,
        )
        val calls = listOf(
            AiToolCall(
                id = "call_add",
                name = "add_book_to_bookshelf",
                arguments = """{"bookUrl":"https://books.test/1"}""",
            ),
            AiToolCall(
                id = "call_draft",
                name = "create_vbook_plugin_draft",
                arguments = """{"name":"Demo Source"}""",
            ),
        )

        assertTrue(useCase.requiresConfirmation("add_book_to_bookshelf"))
        assertTrue(useCase.requiresConfirmation("create_vbook_plugin_draft"))
        assertFalse(useCase.requiresConfirmation("search_online_books"))

        val proposal = useCase.createToolProposal("chat_1", calls)
        useCase.executeApprovedToolCalls(
            request = request(),
            assistantContent = "",
            toolTrace = ToolTraceBuilder(),
            toolCalls = calls,
            proposal = proposal,
            onToolTraceUpdate = {},
        )

        assertEquals(
            listOf("add_book_to_bookshelf", "create_vbook_plugin_draft"),
            toolGateway.executed.map { it.name },
        )
    }

    @Test
    fun rejectedToolProposalWritesRejectedAudit() = runBlocking {
        val agentGateway = FakeAgentGateway()
        val useCase = useCaseWith(agentGateway = agentGateway)
        val calls = listOf(
            AiToolCall(
                id = "call_reject",
                name = "save_memory",
                arguments = """{"key":"token","value":"secret","cookie":"raw-cookie"}""",
            )
        )
        val proposal = useCase.createToolProposal("chat_1", calls)

        useCase.rejectToolProposal(proposal)

        assertEquals(listOf(AgentProposalStatus.REJECTED), agentGateway.proposalStatuses)
        val audit = agentGateway.audits.single()
        assertEquals(AgentAuditStatus.REJECTED, audit.status)
        assertEquals("save_memory", audit.toolName)
        assertFalse(audit.requestPreview.contains("raw-cookie"))
        assertTrue(audit.requestPreview.contains("[REDACTED]"))
    }

    @Test
    fun sessionApprovalSkipsConfirmationOnlyForSameConversation() = runBlocking {
        val broker = AgentPermissionBroker()
        val toolGateway = PermissionedToolGateway(broker)
        val useCase = useCaseWith(
            broker = broker,
            toolGateway = toolGateway,
        )
        val call = AiToolCall(
            id = "call_session",
            name = "save_memory",
            arguments = """{"key":"scope","value":"session"}""",
        )
        val proposal = useCase.createToolProposal("chat_1", listOf(call))

        useCase.executeApprovedToolCalls(
            request = request(conversationId = "chat_1"),
            assistantContent = "",
            toolTrace = ToolTraceBuilder(),
            toolCalls = listOf(call),
            proposal = proposal,
            approvalScope = AgentApprovalScope.SESSION,
            onToolTraceUpdate = {},
        )

        assertFalse(useCase.requiresConfirmation("save_memory", "chat_1"))
        assertTrue(useCase.requiresConfirmation("save_memory", "chat_2"))
    }

    @Test
    fun chatRunPersistenceIncludesReasoningToolAndResponseTrace() = runBlocking {
        val agentGateway = FakeAgentGateway()
        val useCase = useCaseWith(agentGateway = agentGateway)
        val request = request()
        val run = useCase.startAgentRun(request, "chat_1")
        val trace = ToolTraceBuilder().apply {
            append(
                AiStreamEvent.ToolCallDelta(
                    id = "call_1",
                    index = 0,
                    name = "search_books",
                    argumentsDelta = "{}",
                    rawType = "tool_call",
                )
            )
            appendResult("call_1", """{"books":[]}""")
        }

        useCase.saveAgentRun(
            run = run,
            status = AgentRunStatus.FINAL,
            finalText = "Done",
            reasoning = "Brief reasoning",
            toolTrace = trace,
        )

        assertEquals(run.id, agentGateway.lastRunId)
        assertEquals(AgentRunStatus.FINAL, agentGateway.lastResult?.status)
        assertEquals(1, agentGateway.lastResult?.toolResults?.size)
        assertEquals(
            listOf("reasoning", "tool_call", "tool_result", "response"),
            agentGateway.lastResult?.trace?.map { it.type },
        )
    }

    @Test
    fun toolTraceBuilderCanonicalizesProviderToolAliases() {
        val trace = ToolTraceBuilder().apply {
            append(
                AiStreamEvent.ToolCallDelta(
                    id = "call_alias",
                    index = 0,
                    name = "create_vbook_plugin.draft",
                    argumentsDelta = """{"name":"Demo"}""",
                    rawType = "tool_call",
                )
            )
        }

        val pending = trace.pendingToolCalls().single()
        val part = trace.toParts().single() as AiMessagePart.Tool

        assertEquals("create_vbook_plugin_draft", pending.name)
        assertEquals("create_vbook_plugin_draft", part.toolName)
        assertTrue(trace.toString().contains("create_vbook_plugin_draft"))
    }

    @Test
    fun buildRequestLoadsOnlyEnabledValidatedSkillInstructions() = runBlocking {
        val enabledSkill = AgentSkillSnapshot(
            id = "skill_1",
            slug = "chapter_summary",
            name = "Chapter Summary",
            description = "Summarize cached chapters",
            enabled = true,
            activeVersionId = "version_1",
            versions = listOf(
                AgentSkillVersionSnapshot(
                    id = "version_1",
                    version = "1.0.0",
                    name = "Chapter Summary",
                    description = "Summarize cached chapters",
                    instructions = "Always read the cached chapter before summarizing.",
                    allowedTools = listOf("list_book_chapters"),
                    requirements = emptyList(),
                    valid = true,
                    validationMessage = "",
                    createdAt = 1L,
                )
            ),
            createdAt = 1L,
            updatedAt = 1L,
        )
        val request = useCaseWith(skillGateway = FakeSkillGateway(listOf(enabledSkill)))
            .buildRequest(
                userContent = "Summarize",
                history = emptyList(),
                reasoningLevel = AiReasoningLevel.AUTO,
                conversationId = "chat_1",
            )

        val systemPrompt = request.messages.first().content
        assertTrue(systemPrompt.contains("## Enabled Agent Skills"))
        assertTrue(systemPrompt.contains("Always read the cached chapter"))
        assertTrue(systemPrompt.contains("Allowed tools: list_book_chapters"))
    }

    private fun useCaseWith(
        broker: AgentPermissionBroker = AgentPermissionBroker(),
        toolGateway: PermissionedToolGateway = PermissionedToolGateway(broker),
        agentGateway: FakeAgentGateway = FakeAgentGateway(),
        skillGateway: AiSkillGateway = FakeSkillGateway(),
    ): AiChatGenerationUseCase =
        AiChatGenerationUseCase(
            aiTextGateway = FakeTextGateway(),
            aiToolGateway = toolGateway,
            aiProfileGateway = FakeProfileGateway(),
            aiChatGateway = FakeChatGateway(),
            aiMemoryGateway = FakeMemoryGateway(),
            agentPermissionBroker = broker,
            aiAgentGateway = agentGateway,
            aiSkillGateway = skillGateway,
            executeApprovedAgentAction = ExecuteApprovedAgentActionUseCase(
                permissionBroker = broker,
                aiToolGateway = toolGateway,
                aiAgentGateway = agentGateway,
            ),
        )

    private fun request(conversationId: String = "chat_1"): AiGenerateRequest =
        AiGenerateRequest(
            model = modelConfig(),
            messages = emptyList(),
            taskType = AiTaskType.CHAT,
            routeSessionKey = conversationId,
        )

    private companion object {
        fun modelConfig(): AiModelConfig =
            AiModelConfig(
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
            )

        val toolNames = listOf(
            "search_internet",
            "fetch_internet_page",
            "search_books",
            "search_book_sources",
            "search_online_books",
            "add_book_to_bookshelf",
            "get_ai_runtime_status",
            "get_ai_quota_status",
            "diagnose_book_source",
            "repair_book_source",
            "create_vbook_plugin_draft",
            "install_vbook_plugin",
            "create_legado_book_source_draft",
            "install_legado_book_source",
            "list_agent_skills",
            "create_agent_skill_draft",
            "set_agent_skill_enabled",
            "activate_agent_skill_version",
            "rollback_agent_skill",
        )
    }

    private class PermissionedToolGateway(
        private val broker: AgentPermissionBroker,
    ) : AiToolGateway {
        val executed = mutableListOf<AiToolCall>()

        override fun availableTools(): List<AiToolDefinition> =
            toolNames.map { name ->
                AiToolDefinition(name = name, description = name, inputSchema = emptyMap())
            }

        override fun requiresConfirmation(toolName: String): Boolean =
            broker.requiresApproval(toolName)

        override suspend fun execute(
            call: AiToolCall,
            approval: AgentActionApproval?,
            conversationId: String?,
        ): AiToolResult {
            broker.requireCanExecute(call, approval, conversationId)
            executed += call
            return AiToolResult(
                callId = call.id,
                name = call.name,
                content = """{"ok":true}""",
            )
        }
    }

    private class FakeTextGateway : AiTextGateway {
        override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
            Result.success(AiGenerateResponse("OK"))

        override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> =
            emptyFlow()

        override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
            Result.success(emptyList())
    }

    private class FakeProfileGateway : AiProfileGateway {
        override fun observeProviders(): Flow<List<AiProviderProfile>> = flowOf(emptyList())
        override fun observeModels(): Flow<List<AiModelProfile>> = flowOf(emptyList())
        override fun observePresets(): Flow<List<AiTaskPreset>> = flowOf(emptyList())
        override suspend fun getProvider(id: String): AiProviderProfile? = null
        override suspend fun getModel(id: String): AiModelProfile? = null
        override suspend fun getModelConfig(id: String): AiModelConfig? = modelConfig()

        override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? =
            if (taskType == AiTaskType.CHAT) {
                AiTaskPresetConfig(
                    id = "preset",
                    taskType = AiTaskType.CHAT,
                    name = "Chat",
                    model = modelConfig(),
                    promptTemplate = "",
                    params = AiGenerationParams(),
                )
            } else {
                null
            }

        override suspend fun getProviderApiKey(providerId: String): String = ""
        override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile = error("unused")
        override suspend fun saveModel(draft: AiModelDraft): AiModelProfile = error("unused")
        override suspend fun importProviderModels(providerId: String, models: List<AiAvailableModel>): List<AiModelProfile> =
            error("unused")

        override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig = error("unused")
        override suspend fun saveDefaultChatProfile(draft: AiProfileDraft): AiTaskPresetConfig = error("unused")
        override suspend fun saveTaskPreset(
            taskType: String,
            promptTemplate: String,
            temperature: Float,
            maxOutputTokens: Int,
        ): AiTaskPresetConfig = error("unused")

        override suspend fun saveTaskPreset(draft: AiTaskPresetDraft): AiTaskPresetConfig = error("unused")
        override suspend fun setDefaultTaskPreset(presetId: String): AiTaskPresetConfig = error("unused")
        override suspend fun deleteTaskPreset(presetId: String) = Unit
        override suspend fun deleteProvider(providerId: String) = Unit
        override suspend fun deleteModel(modelId: String) = Unit
    }

    private class FakeChatGateway : AiChatGateway {
        override fun observeConversations(): Flow<List<AiChatConversation>> = flowOf(emptyList())
        override fun observeMessages(conversationId: String): Flow<List<AiChatMessage>> = flowOf(emptyList())
        override fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessage>> = flowOf(emptyList())
        override suspend fun getConversation(id: String): AiChatConversation? = null
        override suspend fun createConversation(title: String): AiChatConversation = error("unused")
        override suspend fun saveMessage(
            conversationId: String,
            role: String,
            parts: List<AiMessagePart>,
            parentMessageId: String?,
            thinkingDuration: Int,
        ): AiChatMessage = error("unused")

        override suspend fun saveRegeneratedMessage(
            conversationId: String,
            role: String,
            parts: List<AiMessagePart>,
            parentMessageId: String,
            thinkingDuration: Int,
        ): AiChatMessage = error("unused")

        override suspend fun selectBranch(messageId: String) = Unit
        override suspend fun getBranches(parentMessageId: String): List<AiChatMessage> = emptyList()
        override suspend fun getBranchCounts(conversationId: String): Map<String, Int> = emptyMap()
        override suspend fun updateConversationTitle(conversationId: String, title: String) = Unit
        override suspend fun updateReasoningLevel(conversationId: String, reasoningLevel: String) = Unit
        override suspend fun deleteConversation(conversationId: String) = Unit
    }

    private class FakeMemoryGateway : AiMemoryGateway {
        override fun observeByConversation(conversationId: String): Flow<List<AiMemory>> = flowOf(emptyList())
        override fun observeGlobal(): Flow<List<AiMemory>> = flowOf(emptyList())
        override fun observeRecent(limit: Int): Flow<List<AiMemory>> = flowOf(emptyList())
        override fun observeByScope(scope: String, scopeId: String): Flow<List<AiMemory>> = flowOf(emptyList())
        override suspend fun getByConversation(conversationId: String): List<AiMemory> = emptyList()
        override suspend fun getGlobal(): List<AiMemory> = emptyList()
        override suspend fun getByScope(scope: String, scopeId: String): List<AiMemory> = emptyList()
        override suspend fun getForPrompt(conversationId: String): List<AiMemory> = emptyList()
        override suspend fun search(
            query: String,
            scope: String?,
            scopeId: String?,
            limit: Int,
        ): List<AiMemory> = emptyList()
        override suspend fun searchForPrompt(
            query: String,
            conversationId: String,
            limit: Int,
        ): List<AiMemory> = emptyList()
        override suspend fun upsert(memory: AiMemory) = Unit
        override suspend fun updatePinned(conversationId: String, key: String, pinned: Boolean) = Unit
        override suspend fun delete(conversationId: String, key: String) = Unit
        override suspend fun deleteAllForConversation(conversationId: String) = Unit
    }

    private class FakeSkillGateway(
        private val enabledSkills: List<AgentSkillSnapshot> = emptyList(),
    ) : AiSkillGateway {
        override fun observeSkills(): Flow<List<AgentSkillSnapshot>> = flowOf(enabledSkills)
        override suspend fun getEnabledSkills(): List<AgentSkillSnapshot> = enabledSkills
        override suspend fun createDraft(
            draft: AgentSkillDraft,
            availableTools: Set<String>,
        ): AgentSkillSnapshot = error("unused")

        override suspend fun setEnabled(skillId: String, enabled: Boolean): AgentSkillSnapshot =
            error("unused")

        override suspend fun activateVersion(skillId: String, versionId: String): AgentSkillSnapshot =
            error("unused")

        override suspend fun rollback(skillId: String): AgentSkillSnapshot = error("unused")
    }

    private class FakeAgentGateway : AiAgentGateway {
        var lastRunId: String? = null
        var lastResult: AgentRunResult? = null
        val proposalStatuses = mutableListOf<String>()
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
        ) {
            lastRunId = runId
            lastResult = result
        }

        override suspend fun saveProposal(proposal: AgentActionProposal, runId: String?) = Unit
        override suspend fun markProposalResolved(proposalId: String, status: String) {
            proposalStatuses += status
        }

        override suspend fun saveAudit(record: AgentAuditRecord) {
            audits += record
        }
    }
}
