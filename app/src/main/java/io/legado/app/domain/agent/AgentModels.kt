package io.legado.app.domain.agent

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolResult

enum class AgentActionRisk {
    READ,
    WRITE,
    DELETE,
    BULK,
    PLUGIN_INSTALL;

    val requiresApproval: Boolean
        get() = this != READ
}

enum class AgentToolCapability {
    READ,
    WRITE,
    NETWORK,
    FILE,
    SOURCE,
    AUTHORING,
}

enum class AgentApprovalScope {
    ONE_TIME,
    SESSION,
    ALWAYS,
}

data class AgentToolCallPreview(
    val callId: String,
    val toolName: String,
    val argumentsPreview: String,
    val risk: AgentActionRisk,
    val callHash: String,
)

data class AgentActionProposal(
    val id: String,
    val conversationId: String?,
    val toolCalls: List<AgentToolCallPreview>,
    val proposalHash: String,
    val argsHash: String,
    val createdAt: Long,
    val expiresAt: Long,
)

data class AgentActionApproval(
    val proposalId: String,
    val token: String,
    val conversationId: String?,
    val proposalHash: String,
    val argsHash: String,
    val callHashes: Map<String, String>,
    val expiresAt: Long,
    val scope: AgentApprovalScope = AgentApprovalScope.ONE_TIME,
)

object AgentProposalStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
    const val FAILED = "FAILED"
    const val PARTIAL = "PARTIAL"
}

object AgentAuditStatus {
    const val APPROVED = "APPROVED"
    const val DENIED = "DENIED"
    const val FAILED = "FAILED"
    const val REJECTED = "REJECTED"
}

data class AgentActionAuditEntry(
    val callId: String,
    val toolName: String,
    val risk: AgentActionRisk,
    val before: String,
    val after: String,
)

data class AgentAuditRecord(
    val id: String,
    val runId: String?,
    val proposalId: String?,
    val conversationId: String?,
    val callId: String,
    val toolName: String,
    val risk: AgentActionRisk,
    val capabilities: Set<AgentToolCapability>,
    val approvalScope: AgentApprovalScope,
    val status: String,
    val requestPreview: String,
    val resultPreview: String?,
    val errorMessage: String?,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMs: Long,
)

data class ApprovedAgentActionResult(
    val toolResults: List<AiToolResult>,
    val audit: List<AgentActionAuditEntry>,
)

class AgentPermissionException(message: String) : IllegalStateException(message)

enum class AgentRunStatus {
    FINAL,
    WAITING_FOR_APPROVAL,
    CANCELLED,
    MAX_STEPS,
    LOOP_DETECTED,
    ERROR,
}

data class AgentTraceStep(
    val index: Int,
    val type: String,
    val content: String,
    val toolName: String? = null,
    val callId: String? = null,
)

data class AgentRunResult(
    val status: AgentRunStatus,
    val finalText: String,
    val request: AiGenerateRequest,
    val trace: List<AgentTraceStep>,
    val pendingProposal: AgentActionProposal? = null,
    val pendingToolCalls: List<AiToolCall> = emptyList(),
    val toolResults: List<AiToolResult> = emptyList(),
    val errorMessage: String? = null,
)
