package io.legado.app.ui.ai.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiAgentAudit
import io.legado.app.data.entities.AiAgentRun
import io.legado.app.data.entities.AiAgentProposal
import io.legado.app.data.entities.AiAgentTrace
import com.drducbook.app.R
import io.legado.app.domain.agent.AgentActionRisk
import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.agent.AgentSkillSnapshot
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.help.config.AiChatBubbleConfig
import io.legado.app.ui.ai.context.AiScreenContextRegistry
import io.legado.app.ui.ai.context.AiScreenContextSnapshot
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.init.appCtx

class AgentDashboardViewModel(
    private val aiToolGateway: AiToolGateway,
    private val aiAgentGateway: AiAgentGateway,
    private val aiMemoryGateway: AiMemoryGateway,
    private val aiSkillGateway: AiSkillGateway,
    private val permissionBroker: AgentPermissionBroker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(createUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AgentDashboardEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private var recentRuns: List<AiAgentRun> = emptyList()
    private var recentMemories: List<AiMemory> = emptyList()
    private var pendingProposalCount: Int = 0
    private var recentProposals: List<AiAgentProposal> = emptyList()
    private var recentAudits: List<AiAgentAudit> = emptyList()
    private var skills: List<AgentSkillSnapshot> = emptyList()
    private var traceJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                combine(
                    AiScreenContextRegistry.current,
                    aiAgentGateway.observeRecentRuns(RECENT_RUN_LIMIT),
                    aiAgentGateway.observeRecentProposals(RECENT_PROPOSAL_LIMIT),
                    aiAgentGateway.observeRecentAudits(RECENT_AUDIT_LIMIT),
                    aiMemoryGateway.observeRecent(RECENT_MEMORY_LIMIT),
                ) { context, runs, proposals, audits, memories ->
                    AgentDashboardAuditSnapshot(
                        context = context,
                        recentRuns = runs,
                        recentMemories = memories,
                        pendingProposalCount = proposals.count { it.status == "PENDING" },
                        recentProposals = proposals,
                        recentAudits = audits,
                    )
                },
                aiSkillGateway.observeSkills(),
            ) { snapshot, observedSkills -> snapshot.copy(skills = observedSkills) }
                .onEach { snapshot ->
                recentRuns = snapshot.recentRuns
                recentMemories = snapshot.recentMemories
                pendingProposalCount = snapshot.pendingProposalCount
                recentProposals = snapshot.recentProposals
                recentAudits = snapshot.recentAudits
                skills = snapshot.skills
                val current = _uiState.value
                _uiState.value = createUiState(
                    context = snapshot.context,
                    recentRuns = snapshot.recentRuns,
                    recentMemories = snapshot.recentMemories,
                    pendingProposalCount = snapshot.pendingProposalCount,
                    recentProposals = snapshot.recentProposals,
                    recentAudits = snapshot.recentAudits,
                    skills = snapshot.skills,
                    selectedSkillId = current.selectedSkill?.id,
                    selectedRunId = current.selectedRun?.id,
                    selectedRunTrace = current.selectedRunTrace,
                    pendingSkillAction = current.pendingSkillAction,
                    busySkillIds = current.busySkillIds,
                    loading = false,
                )
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = error.message ?: appCtx.getString(R.string.ai_agent_load_failed),
                    )
                }
            }.collect { }
        }
    }

    fun onIntent(intent: AgentDashboardIntent) {
        when (intent) {
            AgentDashboardIntent.Refresh -> refresh()
            is AgentDashboardIntent.SetChatBubbleEnabled -> setChatBubbleEnabled(intent.enabled)
            is AgentDashboardIntent.OpenSkill -> openSkill(intent.skillId)
            AgentDashboardIntent.DismissSkill -> _uiState.update { it.copy(selectedSkill = null) }
            is AgentDashboardIntent.OpenRun -> openRun(intent.runId)
            AgentDashboardIntent.DismissRun -> dismissRun()
            is AgentDashboardIntent.SetSkillEnabled -> requestSetSkillEnabled(intent.skillId, intent.enabled)
            is AgentDashboardIntent.ActivateSkillVersion -> requestActivateSkillVersion(
                intent.skillId,
                intent.versionId,
            )
            is AgentDashboardIntent.RollbackSkill -> requestRollbackSkill(intent.skillId)
            AgentDashboardIntent.ConfirmSkillAction -> confirmSkillAction()
            AgentDashboardIntent.DismissSkillAction -> _uiState.update {
                it.copy(pendingSkillAction = null)
            }
        }
    }

    private fun refresh() {
        _uiState.value = createUiState(
            context = AiScreenContextRegistry.current.value,
            recentRuns = recentRuns,
            recentMemories = recentMemories,
            pendingProposalCount = pendingProposalCount,
            recentProposals = recentProposals,
            recentAudits = recentAudits,
            skills = skills,
            selectedSkillId = _uiState.value.selectedSkill?.id,
            selectedRunId = _uiState.value.selectedRun?.id,
            selectedRunTrace = _uiState.value.selectedRunTrace,
            pendingSkillAction = _uiState.value.pendingSkillAction,
            busySkillIds = _uiState.value.busySkillIds,
            loading = false,
        )
        _effects.tryEmit(AgentDashboardEffect.ShowMessage(appCtx.getString(R.string.refresh)))
    }

    private fun setChatBubbleEnabled(enabled: Boolean) {
        AiChatBubbleConfig.enabled = enabled
        _uiState.update { it.copy(chatBubbleEnabled = enabled) }
    }

    private fun openSkill(skillId: String) {
        _uiState.update { state ->
            state.copy(selectedSkill = state.skills.firstOrNull { it.id == skillId })
        }
    }

    private fun requestSetSkillEnabled(skillId: String, enabled: Boolean) {
        val skill = skills.firstOrNull { it.id == skillId } ?: return
        _uiState.update {
            it.copy(
                pendingSkillAction = AgentSkillActionUi(
                    skillId = skillId,
                    skillName = skill.name,
                    type = if (enabled) AgentSkillActionType.ENABLE else AgentSkillActionType.DISABLE,
                )
            )
        }
    }

    private fun applySetSkillEnabled(skillId: String, enabled: Boolean) {
        runSkillMutation(skillId) {
            aiSkillGateway.setEnabled(skillId, enabled)
            appCtx.getString(
                if (enabled) R.string.ai_agent_skill_enabled else R.string.ai_agent_skill_disabled
            )
        }
    }

    private fun requestActivateSkillVersion(skillId: String, versionId: String) {
        val skill = skills.firstOrNull { it.id == skillId } ?: return
        _uiState.update {
            it.copy(
                pendingSkillAction = AgentSkillActionUi(
                    skillId = skillId,
                    skillName = skill.name,
                    type = AgentSkillActionType.ACTIVATE,
                    versionId = versionId,
                )
            )
        }
    }

    private fun applyActivateSkillVersion(skillId: String, versionId: String) {
        runSkillMutation(skillId) {
            aiSkillGateway.activateVersion(skillId, versionId)
            appCtx.getString(R.string.ai_agent_skill_activated)
        }
    }

    private fun requestRollbackSkill(skillId: String) {
        val skill = skills.firstOrNull { it.id == skillId } ?: return
        _uiState.update {
            it.copy(
                pendingSkillAction = AgentSkillActionUi(
                    skillId = skillId,
                    skillName = skill.name,
                    type = AgentSkillActionType.ROLLBACK,
                )
            )
        }
    }

    private fun applyRollbackSkill(skillId: String) {
        runSkillMutation(skillId) {
            aiSkillGateway.rollback(skillId)
            appCtx.getString(R.string.ai_agent_skill_rolled_back)
        }
    }

    private fun confirmSkillAction() {
        val action = _uiState.value.pendingSkillAction ?: return
        _uiState.update { it.copy(pendingSkillAction = null) }
        when (action.type) {
            AgentSkillActionType.ENABLE -> applySetSkillEnabled(action.skillId, true)
            AgentSkillActionType.DISABLE -> applySetSkillEnabled(action.skillId, false)
            AgentSkillActionType.ACTIVATE -> action.versionId?.let {
                applyActivateSkillVersion(action.skillId, it)
            }
            AgentSkillActionType.ROLLBACK -> applyRollbackSkill(action.skillId)
        }
    }

    private fun openRun(runId: String) {
        traceJob?.cancel()
        _uiState.update { state ->
            state.copy(
                selectedRun = state.recentRuns.firstOrNull { it.id == runId },
                selectedRunTrace = emptyList<AgentTraceUi>().toImmutableList(),
            )
        }
        traceJob = viewModelScope.launch {
            aiAgentGateway.observeTrace(runId)
                .catch { error ->
                    _effects.tryEmit(
                        AgentDashboardEffect.ShowMessage(
                            error.message ?: appCtx.getString(R.string.ai_agent_trace_load_failed)
                        )
                    )
                }
                .collect { trace ->
                    _uiState.update { state ->
                        if (state.selectedRun?.id == runId) {
                            state.copy(selectedRunTrace = trace.map(AiAgentTrace::toUi).toImmutableList())
                        } else state
                    }
                }
        }
    }

    private fun dismissRun() {
        traceJob?.cancel()
        traceJob = null
        _uiState.update { it.copy(selectedRun = null, selectedRunTrace = emptyList<AgentTraceUi>().toImmutableList()) }
    }

    private fun runSkillMutation(
        skillId: String,
        action: suspend () -> String,
    ) {
        if (skillId in _uiState.value.busySkillIds) return
        _uiState.update { state ->
            state.copy(busySkillIds = (state.busySkillIds + skillId).toImmutableList())
        }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { message -> _effects.tryEmit(AgentDashboardEffect.ShowMessage(message)) }
                .onFailure { error ->
                    _effects.tryEmit(
                        AgentDashboardEffect.ShowMessage(
                            error.message ?: appCtx.getString(R.string.ai_agent_skill_update_failed)
                        )
                    )
                }
            _uiState.update { state ->
                state.copy(
                    busySkillIds = state.busySkillIds.filterNot { it == skillId }.toImmutableList()
                )
            }
        }
    }

    private fun createUiState(
        context: AiScreenContextSnapshot? = AiScreenContextRegistry.current.value,
        recentRuns: List<AiAgentRun> = emptyList(),
        recentMemories: List<AiMemory> = emptyList(),
        pendingProposalCount: Int = 0,
        recentProposals: List<AiAgentProposal> = emptyList(),
        recentAudits: List<AiAgentAudit> = emptyList(),
        skills: List<AgentSkillSnapshot> = emptyList(),
        selectedSkillId: String? = null,
        selectedRunId: String? = null,
        selectedRunTrace: List<AgentTraceUi> = emptyList(),
        pendingSkillAction: AgentSkillActionUi? = null,
        busySkillIds: List<String> = emptyList(),
        loading: Boolean = true,
    ): AgentDashboardUiState {
        return buildAgentDashboardUiState(
            chatBubbleEnabled = AiChatBubbleConfig.enabled,
            context = context,
            tools = aiToolGateway.registeredTools(),
            enabledToolNames = aiToolGateway.availableTools().mapTo(mutableSetOf()) { it.name },
            recentRuns = recentRuns,
            recentMemories = recentMemories,
            pendingProposalCount = pendingProposalCount,
            recentProposals = recentProposals,
            recentAudits = recentAudits,
            skills = skills,
            selectedSkillId = selectedSkillId,
            selectedRunId = selectedRunId,
            selectedRunTrace = selectedRunTrace,
            pendingSkillAction = pendingSkillAction,
            busySkillIds = busySkillIds,
            loading = loading,
            riskFor = permissionBroker::riskFor,
            requiresApproval = permissionBroker::requiresApproval,
        )
    }

    private data class AgentDashboardAuditSnapshot(
        val context: AiScreenContextSnapshot?,
        val recentRuns: List<AiAgentRun>,
        val recentMemories: List<AiMemory>,
        val pendingProposalCount: Int,
        val recentProposals: List<AiAgentProposal>,
        val recentAudits: List<AiAgentAudit>,
        val skills: List<AgentSkillSnapshot> = emptyList(),
    )

    companion object {
        private const val RECENT_RUN_LIMIT = 5
        private const val RECENT_MEMORY_LIMIT = 8
        private const val RECENT_PROPOSAL_LIMIT = 10
        private const val RECENT_AUDIT_LIMIT = 10
    }
}

internal fun buildAgentDashboardUiState(
    chatBubbleEnabled: Boolean,
    context: AiScreenContextSnapshot?,
    tools: List<AiToolDefinition>,
    enabledToolNames: Set<String> = tools.mapTo(mutableSetOf()) { it.name },
    recentRuns: List<AiAgentRun> = emptyList(),
    recentMemories: List<AiMemory> = emptyList(),
    pendingProposalCount: Int = 0,
    recentProposals: List<AiAgentProposal> = emptyList(),
    recentAudits: List<AiAgentAudit> = emptyList(),
    skills: List<AgentSkillSnapshot> = emptyList(),
    selectedSkillId: String? = null,
    selectedRunId: String? = null,
    selectedRunTrace: List<AgentTraceUi> = emptyList(),
    pendingSkillAction: AgentSkillActionUi? = null,
    busySkillIds: List<String> = emptyList(),
    loading: Boolean = false,
    riskFor: (String) -> AgentActionRisk,
    requiresApproval: (String) -> Boolean,
): AgentDashboardUiState {
    val toolItems = tools
        .sortedBy { it.name }
        .map { tool ->
            val risk = riskFor(tool.name)
            AgentToolUi(
                name = tool.name,
                description = tool.description,
                risk = risk,
                requiresApproval = requiresApproval(tool.name),
                enabled = tool.name in enabledToolNames,
            )
        }
        .toImmutableList()

    val skillItems = skills.map(AgentSkillSnapshot::toUi).toImmutableList()
    return AgentDashboardUiState(
        loading = loading,
        chatBubbleEnabled = chatBubbleEnabled,
        context = context.toUi(),
        toolCount = toolItems.size,
        enabledToolCount = toolItems.count { it.enabled },
        readToolCount = toolItems.count { it.risk == AgentActionRisk.READ },
        approvalToolCount = toolItems.count { it.requiresApproval },
        pendingProposalCount = pendingProposalCount,
        auditCount = recentAudits.size,
        memoryCount = recentMemories.size,
        pinnedMemoryCount = recentMemories.count { it.pinned },
        skillCount = skillItems.size,
        enabledSkillCount = skillItems.count { it.enabled },
        invalidSkillCount = skillItems.count { !it.latestVersionValid },
        recentRuns = recentRuns.map { it.toUi() }.toImmutableList(),
        recentProposals = recentProposals.map { it.toUi() }.toImmutableList(),
        recentAudits = recentAudits.map { it.toUi() }.toImmutableList(),
        recentMemories = recentMemories.map { it.toUi() }.toImmutableList(),
        tools = toolItems,
        skills = skillItems,
        selectedSkill = skillItems.firstOrNull { it.id == selectedSkillId },
        selectedRun = recentRuns.firstOrNull { it.id == selectedRunId }?.toUi(),
        selectedRunTrace = selectedRunTrace.toImmutableList(),
        pendingSkillAction = pendingSkillAction,
        busySkillIds = busySkillIds.toImmutableList(),
    )
}

private fun AiScreenContextSnapshot?.toUi(): AgentScreenContextUi {
    if (this == null) return AgentScreenContextUi()
    return AgentScreenContextUi(
        hasContext = true,
        screen = screen,
        sensitive = sensitive,
        attributes = attributes
            .toSortedMap()
            .map { (key, value) -> AgentContextAttributeUi(key = key, value = value) }
            .toImmutableList(),
    )
}

private fun AiAgentRun.toUi(): AgentRunUi {
    return AgentRunUi(
        id = id,
        status = status,
        finalTextPreview = finalTextPreview,
        errorMessage = errorMessage,
        traceCount = traceCount,
        toolResultCount = toolResultCount,
        providerId = providerId,
        modelId = modelId,
        startedAt = startedAt,
        updatedAt = updatedAt,
    )
}

private fun AiAgentProposal.toUi() = AgentProposalUi(
    id = id,
    conversationId = conversationId,
    status = status,
    toolCount = toolCount,
    createdAt = createdAt,
    expiresAt = expiresAt,
)

private fun AiAgentAudit.toUi() = AgentAuditUi(
    id = id,
    toolName = toolName,
    status = status,
    risk = runCatching { AgentActionRisk.valueOf(risk) }.getOrDefault(AgentActionRisk.READ),
    approvalScope = approvalScope,
    durationMs = durationMs,
    errorMessage = errorMessage,
    startedAt = startedAt,
)

private fun AiAgentTrace.toUi() = AgentTraceUi(
    index = stepIndex,
    type = type,
    content = content.take(MAX_TRACE_CONTENT_CHARS),
    toolName = toolName,
    callId = callId,
)

private const val MAX_TRACE_CONTENT_CHARS = 4_000

private fun AiMemory.toUi(): AgentMemoryUi {
    return AgentMemoryUi(
        conversationId = conversationId,
        key = key,
        value = value,
        scope = scope,
        scopeId = scopeId,
        type = type,
        pinned = pinned,
        confidence = confidence,
    )
}

private fun AgentSkillSnapshot.toUi(): AgentSkillUi {
    val latest = latestVersion
    val active = activeVersion
    val ordered = versions.sortedByDescending { it.createdAt }
    val activeIndex = ordered.indexOfFirst { it.id == activeVersionId }
    return AgentSkillUi(
        id = id,
        name = name,
        description = description,
        enabled = enabled,
        activeVersionId = activeVersionId,
        activeVersion = active?.version,
        latestVersionId = latest?.id,
        latestVersion = latest?.version,
        latestVersionValid = latest?.valid == true,
        validationMessage = latest?.validationMessage.orEmpty(),
        allowedTools = latest?.allowedTools.orEmpty().toImmutableList(),
        requirements = latest?.requirements.orEmpty().toImmutableList(),
        versionCount = versions.size,
        canActivateLatest = latest?.valid == true && latest.id != activeVersionId,
        canRollback = activeIndex >= 0 && ordered.drop(activeIndex + 1).any { it.valid },
    )
}
