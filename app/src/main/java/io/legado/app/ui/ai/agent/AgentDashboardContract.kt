package io.legado.app.ui.ai.agent

import androidx.compose.runtime.Stable
import io.legado.app.domain.agent.AgentActionRisk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AgentDashboardUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val chatBubbleEnabled: Boolean = false,
    val context: AgentScreenContextUi = AgentScreenContextUi(),
    val toolCount: Int = 0,
    val enabledToolCount: Int = 0,
    val readToolCount: Int = 0,
    val approvalToolCount: Int = 0,
    val pendingProposalCount: Int = 0,
    val auditCount: Int = 0,
    val memoryCount: Int = 0,
    val pinnedMemoryCount: Int = 0,
    val skillCount: Int = 0,
    val enabledSkillCount: Int = 0,
    val invalidSkillCount: Int = 0,
    val recentRuns: ImmutableList<AgentRunUi> = persistentListOf(),
    val recentProposals: ImmutableList<AgentProposalUi> = persistentListOf(),
    val recentAudits: ImmutableList<AgentAuditUi> = persistentListOf(),
    val recentMemories: ImmutableList<AgentMemoryUi> = persistentListOf(),
    val tools: ImmutableList<AgentToolUi> = persistentListOf(),
    val skills: ImmutableList<AgentSkillUi> = persistentListOf(),
    val selectedSkill: AgentSkillUi? = null,
    val selectedRun: AgentRunUi? = null,
    val selectedRunTrace: ImmutableList<AgentTraceUi> = persistentListOf(),
    val pendingSkillAction: AgentSkillActionUi? = null,
    val busySkillIds: ImmutableList<String> = persistentListOf(),
)

@Stable
data class AgentScreenContextUi(
    val hasContext: Boolean = false,
    val screen: String = "",
    val sensitive: Boolean = false,
    val attributes: ImmutableList<AgentContextAttributeUi> = persistentListOf(),
)

@Stable
data class AgentContextAttributeUi(
    val key: String,
    val value: String,
)

@Stable
data class AgentToolUi(
    val name: String,
    val description: String,
    val risk: AgentActionRisk,
    val requiresApproval: Boolean,
    val enabled: Boolean,
)

@Stable
data class AgentRunUi(
    val id: String,
    val status: String,
    val finalTextPreview: String,
    val errorMessage: String?,
    val traceCount: Int,
    val toolResultCount: Int,
    val providerId: String,
    val modelId: String,
    val startedAt: Long,
    val updatedAt: Long,
)

@Stable
data class AgentProposalUi(
    val id: String,
    val conversationId: String?,
    val status: String,
    val toolCount: Int,
    val createdAt: Long,
    val expiresAt: Long,
)

@Stable
data class AgentAuditUi(
    val id: String,
    val toolName: String,
    val status: String,
    val risk: AgentActionRisk,
    val approvalScope: String,
    val durationMs: Long,
    val errorMessage: String?,
    val startedAt: Long,
)

@Stable
data class AgentTraceUi(
    val index: Int,
    val type: String,
    val content: String,
    val toolName: String?,
    val callId: String?,
)

enum class AgentSkillActionType {
    ENABLE,
    DISABLE,
    ACTIVATE,
    ROLLBACK,
}

@Stable
data class AgentSkillActionUi(
    val skillId: String,
    val skillName: String,
    val type: AgentSkillActionType,
    val versionId: String? = null,
)

@Stable
data class AgentMemoryUi(
    val conversationId: String,
    val key: String,
    val value: String,
    val scope: String,
    val scopeId: String,
    val type: String,
    val pinned: Boolean,
    val confidence: Double,
)

@Stable
data class AgentSkillUi(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val activeVersionId: String?,
    val activeVersion: String?,
    val latestVersionId: String?,
    val latestVersion: String?,
    val latestVersionValid: Boolean,
    val validationMessage: String,
    val allowedTools: ImmutableList<String>,
    val requirements: ImmutableList<String>,
    val versionCount: Int,
    val canActivateLatest: Boolean,
    val canRollback: Boolean,
)

sealed interface AgentDashboardIntent {
    data object Refresh : AgentDashboardIntent
    data class SetChatBubbleEnabled(val enabled: Boolean) : AgentDashboardIntent
    data class OpenSkill(val skillId: String) : AgentDashboardIntent
    data object DismissSkill : AgentDashboardIntent
    data class OpenRun(val runId: String) : AgentDashboardIntent
    data object DismissRun : AgentDashboardIntent
    data class SetSkillEnabled(val skillId: String, val enabled: Boolean) : AgentDashboardIntent
    data class ActivateSkillVersion(val skillId: String, val versionId: String) : AgentDashboardIntent
    data class RollbackSkill(val skillId: String) : AgentDashboardIntent
    data object ConfirmSkillAction : AgentDashboardIntent
    data object DismissSkillAction : AgentDashboardIntent
}

sealed interface AgentDashboardEffect {
    data class ShowMessage(val message: String) : AgentDashboardEffect
}
