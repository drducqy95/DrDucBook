package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import io.legado.app.domain.agent.AgentApprovalScope
import io.legado.app.domain.agent.AgentActionProposal
import io.legado.app.domain.agent.AgentAuditRecord
import io.legado.app.domain.agent.AgentAuditStatus
import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.agent.AgentProposalStatus
import io.legado.app.domain.agent.AgentRunResult
import io.legado.app.domain.agent.AgentRunStatus
import io.legado.app.domain.agent.AgentToolNameNormalizer
import io.legado.app.domain.agent.AgentTraceStep
import io.legado.app.domain.agent.sanitizeForAgentAudit
import io.legado.app.domain.agent.withCanonicalToolName
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolApprovalState
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolResult
import io.legado.app.domain.model.toolParts
import io.legado.app.ui.ai.chat.AiChatBookResultUi
import io.legado.app.ui.ai.chat.AiChatMessageUi
import io.legado.app.utils.GSON
import java.util.UUID

/**
 * Encapsulates chat generation logic: request building, streaming, tool execution loop.
 * ViewModel only handles UI state updates by collecting [GenerationEvent]s.
 */
class AiChatGenerationUseCase(
    private val aiTextGateway: AiTextGateway,
    private val aiToolGateway: AiToolGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiChatGateway: AiChatGateway,
    private val aiMemoryGateway: AiMemoryGateway,
    private val agentPermissionBroker: AgentPermissionBroker,
    private val aiAgentGateway: AiAgentGateway,
    private val aiSkillGateway: AiSkillGateway,
    private val executeApprovedAgentAction: ExecuteApprovedAgentActionUseCase,
) {

    suspend fun buildRequest(
        userContent: String,
        history: List<AiChatMessageUi>,
        reasoningLevel: AiReasoningLevel,
        conversationId: String? = null
    ): AiGenerateRequest {
        val preset = resolvePreset()
            ?: error("Please configure a default AI model first")
        return AiGenerateRequest(
            model = preset.model,
            messages = buildRequestMessages(userContent, history, conversationId),
            params = preset.params.copy(reasoningLevel = reasoningLevel),
            tools = aiToolGateway.availableTools(),
            taskType = AiTaskType.CHAT,
            routeProfileId = preset.runtimeOptions.routeProfileId,
            routeSessionKey = conversationId,
        )
    }

    fun startAgentRun(
        request: AiGenerateRequest,
        conversationId: String?,
    ): AiChatAgentRun = AiChatAgentRun(
        id = "run_${UUID.randomUUID().toString().replace("-", "")}",
        conversationId = conversationId,
        startedAt = System.currentTimeMillis(),
        request = request,
    )

    suspend fun saveAgentRun(
        run: AiChatAgentRun,
        status: AgentRunStatus,
        finalText: String,
        reasoning: String,
        toolTrace: ToolTraceBuilder,
        pendingProposal: AgentActionProposal? = null,
        errorMessage: String? = null,
    ) {
        aiAgentGateway.saveRunResult(
            runId = run.id,
            conversationId = run.conversationId,
            startedAt = run.startedAt,
            result = AgentRunResult(
                status = status,
                finalText = finalText,
                request = run.request,
                trace = toolTrace.toAgentTrace(
                    reasoning = reasoning,
                    response = finalText,
                    pendingProposal = pendingProposal,
                ),
                pendingProposal = pendingProposal,
                pendingToolCalls = pendingProposal?.let { toolTrace.pendingToolCalls() }.orEmpty(),
                toolResults = toolTrace.toAgentToolResults(),
                errorMessage = errorMessage,
            ),
        )
    }

    suspend fun collectStream(
        request: AiGenerateRequest,
        toolTrace: ToolTraceBuilder,
        onContent: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit,
        onToolTraceUpdate: suspend () -> Unit
    ) {
        toolTrace.beginResponse()
        aiTextGateway.generateStream(request).collect { event ->
            when (event) {
                is AiStreamEvent.Content -> onContent(event.text)
                is AiStreamEvent.Reasoning -> onReasoning(event.text)
                is AiStreamEvent.ToolCallDelta -> {
                    toolTrace.append(event)
                    onToolTraceUpdate()
                }
            }
        }
    }

    suspend fun executeToolCalls(
        request: AiGenerateRequest,
        assistantContent: String,
        toolTrace: ToolTraceBuilder,
        toolCalls: List<AiToolCall>,
        onToolTraceUpdate: suspend () -> Unit
    ): AiGenerateRequest {
        val canonicalToolCalls = toolCalls.map { it.withCanonicalToolName() }
        val toolResultMessages = canonicalToolCalls.map { toolCall ->
            val result = aiToolGateway.execute(
                call = toolCall,
                conversationId = request.routeSessionKey,
            )
            val truncated = result.content.truncateToolOutput()
            toolTrace.appendResult(result.callId, truncated)
            onToolTraceUpdate()
            AiMessage(
                role = AiMessageRole.TOOL,
                content = truncated,
                toolCallId = result.callId,
                name = result.name
            )
        }
        return request.copy(
            messages = request.messages +
                AiMessage(
                    role = AiMessageRole.ASSISTANT,
                    content = assistantContent,
                    toolCalls = canonicalToolCalls
                ) +
                toolResultMessages
        )
    }

    suspend fun executeApprovedToolCalls(
        request: AiGenerateRequest,
        assistantContent: String,
        toolTrace: ToolTraceBuilder,
        toolCalls: List<AiToolCall>,
        proposal: AgentActionProposal,
        approvalScope: AgentApprovalScope = AgentApprovalScope.ONE_TIME,
        onToolTraceUpdate: suspend () -> Unit,
    ): AiGenerateRequest {
        val canonicalToolCalls = toolCalls.map { it.withCanonicalToolName() }
        val approved = executeApprovedAgentAction(proposal, canonicalToolCalls, approvalScope)
        val toolResultMessages = approved.toolResults.map { result ->
            val truncated = result.content.truncateToolOutput()
            toolTrace.appendResult(result.callId, truncated)
            onToolTraceUpdate()
            AiMessage(
                role = AiMessageRole.TOOL,
                content = truncated,
                toolCallId = result.callId,
                name = result.name,
            )
        }
        return request.copy(
            messages = request.messages +
                AiMessage(
                    role = AiMessageRole.ASSISTANT,
                    content = assistantContent,
                    toolCalls = canonicalToolCalls,
                ) +
                toolResultMessages
        )
    }

    fun requiresConfirmation(toolName: String): Boolean {
        return requiresConfirmation(toolName, conversationId = null)
    }

    fun requiresConfirmation(toolName: String, conversationId: String?): Boolean {
        return aiToolGateway.requiresConfirmation(toolName) &&
            !agentPermissionBroker.hasReusableGrant(toolName, conversationId)
    }

    suspend fun createToolProposal(
        conversationId: String?,
        toolCalls: List<AiToolCall>,
    ): AgentActionProposal {
        return agentPermissionBroker.createProposal(conversationId, toolCalls).also { proposal ->
            runCatching { aiAgentGateway.saveProposal(proposal) }
        }
    }

    suspend fun rejectToolProposal(proposal: AgentActionProposal) {
        agentPermissionBroker.reject(proposal.id)
        saveRejectedAudit(proposal)
        runCatching {
            aiAgentGateway.markProposalResolved(
                proposalId = proposal.id,
                status = AgentProposalStatus.REJECTED,
            )
        }
    }

    private suspend fun saveRejectedAudit(proposal: AgentActionProposal) {
        val now = System.currentTimeMillis()
        proposal.toolCalls.forEach { call ->
            runCatching {
                aiAgentGateway.saveAudit(
                    AgentAuditRecord(
                        id = "audit_${UUID.randomUUID().toString().replace("-", "")}",
                        runId = null,
                        proposalId = proposal.id,
                        conversationId = proposal.conversationId,
                        callId = call.callId,
                        toolName = call.toolName,
                        risk = call.risk,
                        capabilities = agentPermissionBroker.capabilitiesFor(call.toolName),
                        approvalScope = AgentApprovalScope.ONE_TIME,
                        status = AgentAuditStatus.REJECTED,
                        requestPreview = call.argumentsPreview.sanitizeForAgentAudit(),
                        resultPreview = null,
                        errorMessage = "User rejected agent tool proposal",
                        startedAt = now,
                        finishedAt = now,
                        durationMs = 0L,
                    )
                )
            }
        }
    }

    fun buildAssistantParts(
        text: String,
        reasoning: String,
        toolTrace: ToolTraceBuilder
    ): List<AiMessagePart> {
        return buildList {
            reasoning.takeIf { it.isNotBlank() }?.let { add(AiMessagePart.Reasoning(it)) }
            text.takeIf { it.isNotBlank() }?.let { add(AiMessagePart.Text(it)) }
            addAll(toolTrace.toParts())
            addAll(toolTrace.bookResults())
        }
    }

    suspend fun generateTitle(
        userContent: String,
        assistantContent: String,
        reasoningLevel: AiReasoningLevel
    ): String {
        val preset = resolvePreset()
            ?: error("No AI model configured")
        val prompt = """
            Đặt một tiêu đề tiếng Việt ngắn, tự nhiên cho cuộc trò chuyện sau (tối đa 12 từ).
            Chỉ trả tiêu đề, không thêm giải thích, dấu ngoặc hoặc tiền tố.

            Người dùng: ${userContent.take(500)}
            Trợ lý: ${assistantContent.take(500)}
        """.trimIndent()
        val request = AiGenerateRequest(
            model = preset.model,
            messages = listOf(AiMessage(AiMessageRole.USER, prompt)),
            params = preset.params.copy(reasoningLevel = reasoningLevel),
            taskType = AiTaskType.CHAT,
            routeProfileId = preset.runtimeOptions.routeProfileId,
        )
        val result = aiTextGateway.generate(request)
        return result.getOrNull()?.text?.trim()?.take(30) ?: userContent.take(20)
    }

    private suspend fun buildRequestMessages(
        newContent: String,
        history: List<AiChatMessageUi>,
        conversationId: String? = null
    ): List<AiMessage> {
        val system = buildSystemPrompt(conversationId)
        val trimmedHistory = history.trimForRequest(MAX_HISTORY_MESSAGES)
        val messages = trimmedHistory.flatMap {
            when (it.role) {
                AiMessageRole.USER -> listOf(AiMessage(AiMessageRole.USER, it.content))
                AiMessageRole.ASSISTANT -> it.toRequestMessages()
                else -> null
            }.orEmpty()
        }
        return listOf(AiMessage(AiMessageRole.SYSTEM, system)) +
            messages +
            AiMessage(AiMessageRole.USER, newContent)
    }

    /**
     * Tool-aware history trimming. Ensures tool_call and tool_result pairs
     * are never split — if a tool_result would be kept without its tool_call,
     * the message is dropped.
     */
    private fun List<AiChatMessageUi>.trimForRequest(maxMessages: Int): List<AiChatMessageUi> {
        if (size <= maxMessages) return this
        val trimmed = takeLast(maxMessages)
        // Find the first message where all Tool parts with output have their
        // corresponding ToolCall present in the trimmed set
        val firstSafe = trimmed.indexOfFirst { msg ->
            msg.parts.toolParts().filter { it.output.isNotBlank() }.all { tool ->
                trimmed.any { other ->
                    other.parts.toolParts().any { it.toolCallId == tool.toolCallId && it.output.isBlank() }
                }
            }
        }
        return if (firstSafe > 0) trimmed.drop(firstSafe) else trimmed
    }

    private fun AiChatMessageUi.toRequestMessages(): List<AiMessage> {
        val tools = parts.toolParts()
        val toolCalls = tools.filter {
            it.output.isNotBlank() || it.approvalState == AiToolApprovalState.AUTO
        }.map {
            AiToolCall(
                id = it.toolCallId,
                name = it.toolName,
                arguments = it.input,
                metadata = it.metadata,
            )
        }
        val toolResults = tools.filter { it.output.isNotBlank() }.map {
            AiMessage(
                role = AiMessageRole.TOOL,
                content = it.output,
                toolCallId = it.toolCallId,
                name = it.toolName
            )
        }
        return listOf(
            AiMessage(role = AiMessageRole.ASSISTANT, content = content, toolCalls = toolCalls)
        ) + toolResults
    }

    private suspend fun buildSystemPrompt(conversationId: String? = null): String {
        val base = """
            You are a helpful AI assistant inside a reading app.
            Reply in the user's language; use Vietnamese by default.
            Render answers in complete Markdown when structure helps.
            Use local reading tools when the user asks about bookshelf books, current reading progress, chapters, bookmarks, reading statistics, or existing AI notes.
            For requests like summarizing, explaining, or continuing from the current chapter, use the local book and chapter tools before answering.
            You can search the public web, inspect installed book sources, search online books through those sources, and add a selected result to the bookshelf.
            You can create local VBook plugin drafts or standard Legado BookSource JSON drafts and install them only after the user confirms.
            You can create versioned Agent skill drafts, but activation, enable/disable, and rollback always require user confirmation.
            Chatbot-installed VBook plugins and Legado sources stay disabled by default; set enableAfterInstall only when the user explicitly asks to enable the new source immediately.
            Prefer these exact tool names when relevant: search_internet, fetch_internet_page, search_book_sources, search_online_books, add_book_to_bookshelf, get_ai_runtime_status, get_ai_quota_status, diagnose_book_source, repair_book_source, create_vbook_plugin_draft, install_vbook_plugin, create_legado_book_source_draft, install_legado_book_source.
            You can inspect user Quick Translation dictionary entries, refresh a bookshelf book, queue offline chapter downloads, and configure the app's non-AI scheduled bookshelf updater through tools.
            When asked which model is being used, call get_ai_runtime_status. When asked about remaining quota or limits, call get_ai_quota_status and never guess exact remaining quota if the tool says it is unknown.
            When asked to fix a book source, first call search_book_sources if needed, then diagnose_book_source. Explain the failing stage before proposing repair_book_source. Use create_vbook_plugin_draft only for packaged VBook src/*.js plugins. Use create_legado_book_source_draft for a standard rule-based Legado BookSource JSON object. Install either format only after explicit user confirmation.
            VBook draft scripts run synchronously in the app sandbox: every role script must expose a global execute(...) function and should use fetch, Http, Html, Response, localStorage, or the network-only java.connect/java.ajax compatibility facade. Promise.resolve/new Promise/.then are supported only when they settle synchronously; async/await, CommonJS module.exports, and Java/Android platform packages are unavailable.
            A Legado source draft must be one complete JSON object with bookSourceName, a public http/https bookSourceUrl, and the relevant searchUrl/ruleSearch/ruleBookInfo/ruleToc/ruleContent fields. Put the complete serialized object in sourceJson; do not wrap it in a JSON array.
            Treat save, edit, delete, add-book, plugin, refresh, download, and scheduler changes as write actions. Call the appropriate tool only after the user has clearly requested the action; the app will show a final confirmation before execution.
            Scheduled bookshelf updates and downloads are native app jobs and never require an AI provider after configuration.
            If a tool says content is missing or unavailable, state that limitation clearly and do not invent book content.
            Save notes or summaries only when the user explicitly asks to save them.
            Do not reveal hidden chain-of-thought. If reasoning is useful, provide a brief reasoning summary.
        """.trimIndent()

        val sections = mutableListOf(base)
        val enabledSkills = aiSkillGateway.getEnabledSkills()
            .take(MAX_ENABLED_SKILLS)
        if (enabledSkills.isNotEmpty()) {
            val skillBlock = enabledSkills.joinToString("\n\n") { skill ->
                val version = requireNotNull(skill.activeVersion)
                buildString {
                    append("### ").append(skill.name).append(" (").append(version.version).append(")\n")
                    append("Allowed tools: ")
                    append(version.allowedTools.ifEmpty { listOf("none") }.joinToString())
                    append("\n")
                    append(version.instructions.take(MAX_SKILL_INSTRUCTION_CHARS))
                }
            }
            sections += """
                ## Enabled Agent Skills
                Follow these user-approved skills only within their declared allowed tools. Skills never override permission checks, safety policy, or the user's current request.
                $skillBlock
            """.trimIndent()
        }
        if (conversationId != null) {
            val memories = aiMemoryGateway.getForPrompt(conversationId)
            if (memories.isNotEmpty()) {
                val memoryBlock = memories.joinToString("\n") { "- ${it.key}: ${it.value}" }
                sections += "## User Memory\nThe following facts about the user have been remembered from prior conversations:\n$memoryBlock"
            }
        }
        return sections.joinToString("\n\n")
    }

    private suspend fun resolvePreset() =
        aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)

    companion object {
        const val MAX_HISTORY_MESSAGES = 12
        const val MAX_TOOL_OUTPUT_CHARS = 8_000
        const val MAX_TOOL_ROUNDS = 12
        private const val MAX_ENABLED_SKILLS = 8
        private const val MAX_SKILL_INSTRUCTION_CHARS = 8_000
    }
}

/**
 * Accumulates streaming tool call deltas into complete [AiMessagePart.Tool] parts.
 * Thread-safe for use within a single coroutine context.
 */
class ToolTraceBuilder {
    private val calls = linkedMapOf<String, ToolCallTrace>()
    private val indexKeys = mutableMapOf<Int, String>()

    fun beginResponse() {
        indexKeys.clear()
    }

    fun append(event: AiStreamEvent.ToolCallDelta): String {
        val eventId = event.id?.takeIf { it.isNotBlank() }
        if (eventId != null && event.index != null) {
            indexKeys[event.index] = eventId
        }
        val baseId = eventId
            ?: event.index?.let { indexKeys[it] ?: "tool_index_$it" }
            ?: "tool_${calls.size + 1}"
        val id = if (eventId == null && calls[baseId]?.result != null) {
            "${baseId}_${calls.size + 1}"
        } else {
            baseId
        }
        val call = calls.getOrPut(id) { ToolCallTrace(id = id, rawType = event.rawType) }
        event.name?.takeIf { it.isNotBlank() }?.let { call.name = it }
        event.argumentsDelta?.takeIf { it.isNotEmpty() }?.let { call.arguments.append(it) }
        event.metadata?.takeIf(String::isNotBlank)?.let { call.metadata = it }
        if (call.rawType.isBlank()) call.rawType = event.rawType
        return toString()
    }

    fun appendResult(id: String, result: String): String {
        calls[id]?.result = result
        return toString()
    }

    fun pendingToolCalls(): List<AiToolCall> {
        return calls.values.filter { it.result == null }.mapNotNull { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiToolCall(
                id = call.id,
                name = name,
                arguments = call.arguments.toString().ifBlank { "{}" },
                metadata = call.metadata,
            ).withCanonicalToolName()
        }
    }

    fun toParts(): List<AiMessagePart> {
        val parts = mutableListOf<AiMessagePart>()
        calls.values.forEach { call ->
            val name = call.name.takeIf { it.isNotBlank() }
                ?.let(AgentToolNameNormalizer::canonicalize)
                ?: return@forEach
            parts += AiMessagePart.Tool(
                toolCallId = call.id,
                toolName = name,
                input = call.arguments.toString().ifBlank { "{}" },
                output = call.result ?: "",
                rawType = call.rawType.ifBlank { "tool_call" },
                metadata = call.metadata,
                approvalState = if (call.result == null) {
                    AiToolApprovalState.PENDING
                } else {
                    AiToolApprovalState.AUTO
                }
            )
        }
        return parts
    }

    fun bookResults(): List<AiMessagePart.BookResult> {
        val books = linkedMapOf<String, AiMessagePart.BookResult>()
        calls.values.mapNotNull { it.result }.forEach { result ->
            val root = runCatching {
                GSON.fromJson(result, JsonObject::class.java)
            }.getOrNull() ?: return@forEach
            root.getAsJsonArrayOrNull("books")?.forEach { element ->
                element.asJsonObjectOrNull()?.toBookResultPart()?.let {
                    books.putIfAbsent(it.bookUrl, it)
                }
            }
            root.getAsJsonObjectOrNull("book")?.toBookResultPart()?.let {
                books.putIfAbsent(it.bookUrl, it)
            }
        }
        return books.values.toList()
    }

    fun toAgentToolResults(): List<AiToolResult> = calls.values.mapNotNull { call ->
        val name = call.name.takeIf(String::isNotBlank)
            ?.let(AgentToolNameNormalizer::canonicalize)
            ?: return@mapNotNull null
        val result = call.result ?: return@mapNotNull null
        AiToolResult(
            callId = call.id,
            name = name,
            content = result,
        )
    }

    fun toAgentTrace(
        reasoning: String,
        response: String,
        pendingProposal: AgentActionProposal?,
    ): List<AgentTraceStep> = buildList {
        reasoning.takeIf(String::isNotBlank)?.let { content ->
            add(AgentTraceStep(size, RunAiAgentUseCase.TRACE_REASONING, content))
        }
        calls.values.forEach { call ->
            val name = call.name.takeIf(String::isNotBlank)
                ?.let(AgentToolNameNormalizer::canonicalize)
                ?: return@forEach
            add(
                AgentTraceStep(
                    index = size,
                    type = RunAiAgentUseCase.TRACE_TOOL_CALL,
                    content = call.arguments.toString().ifBlank { "{}" },
                    toolName = name,
                    callId = call.id,
                )
            )
            call.result?.let { result ->
                add(
                    AgentTraceStep(
                        index = size,
                        type = RunAiAgentUseCase.TRACE_TOOL_RESULT,
                        content = result,
                        toolName = name,
                        callId = call.id,
                    )
                )
            }
        }
        pendingProposal?.let { proposal ->
            add(
                AgentTraceStep(
                    index = size,
                    type = RunAiAgentUseCase.TRACE_PROPOSAL,
                    content = proposal.id,
                )
            )
        }
        response.takeIf(String::isNotBlank)?.let { content ->
            add(AgentTraceStep(size, RunAiAgentUseCase.TRACE_RESPONSE, content))
        }
    }

    override fun toString(): String {
        return calls.values.joinToString("\n\n") { call ->
            buildString {
                append("Tool: ")
                val displayName = call.name.takeIf(String::isNotBlank)
                    ?.let(AgentToolNameNormalizer::canonicalize)
                    ?: call.rawType.ifBlank { call.id }
                append(displayName)
                append('\n')
                append("ID: ")
                append(call.id)
                if (call.arguments.isNotBlank()) {
                    append('\n')
                    append(call.arguments)
                }
                call.result?.takeIf { it.isNotBlank() }?.let {
                    append('\n')
                    append("Result: ")
                    append(it.take(2000))
                }
            }
        }
    }
}

internal data class ToolCallTrace(
    val id: String,
    var rawType: String,
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
    var metadata: String? = null,
    var result: String? = null
)

data class PendingToolRun(
    val conversationId: String?,
    val request: AiGenerateRequest,
    val fullText: String,
    val fullReasoning: String,
    val toolTrace: ToolTraceBuilder,
    val toolCalls: List<AiToolCall>,
    val proposal: AgentActionProposal?,
    val assistantTextStart: Int,
    val round: Int,
    val parentMessageId: String? = null,
    val agentRun: AiChatAgentRun,
)

data class AiChatAgentRun(
    val id: String,
    val conversationId: String?,
    val startedAt: Long,
    val request: AiGenerateRequest,
)

// ---- Book result extraction helpers ----

internal fun JsonObject.toBookResultPart(): AiMessagePart.BookResult? {
    val bookUrl = string("bookUrl")?.takeIf { it.isNotBlank() } ?: return null
    return AiMessagePart.BookResult(
        bookUrl = bookUrl,
        name = string("name").orEmpty(),
        author = string("author").orEmpty(),
        origin = string("origin") ?: string("originName"),
        coverPath = string("coverPath") ?: string("coverUrl"),
        latestChapterTitle = string("latestChapterTitle"),
        currentChapterTitle = string("currentChapterTitle"),
        intro = string("intro")
    )
}

internal fun JsonObject.string(name: String): String? {
    return get(name)?.takeIf { !it.isJsonNull }?.asString
}

internal fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? {
    return get(name)?.let { if (it.isJsonObject) it.asJsonObject else null }
}

internal fun JsonObject.getAsJsonArrayOrNull(name: String) = runCatching {
    get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray
}.getOrNull()

internal fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
    return takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
}

/** Truncate tool output to [AiChatGenerationUseCase.MAX_TOOL_OUTPUT_CHARS] to avoid overflowing context. */
internal fun String.truncateToolOutput(): String {
    if (length <= AiChatGenerationUseCase.MAX_TOOL_OUTPUT_CHARS) return this
    return take(AiChatGenerationUseCase.MAX_TOOL_OUTPUT_CHARS) +
        "\n\n[...truncated from ${length} chars]"
}
