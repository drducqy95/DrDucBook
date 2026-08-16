package io.legado.app.ui.ai.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.agent.AgentActionRisk
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AgentDashboardRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToCustomTools: () -> Unit,
    showNavigationIcon: Boolean = true,
    viewModel: AgentDashboardViewModel = koinViewModel(),
) {
    AgentDashboardScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToCustomTools = onNavigateToCustomTools,
        showNavigationIcon = showNavigationIcon,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDashboardScreen(
    state: AgentDashboardUiState,
    effects: Flow<AgentDashboardEffect>,
    onIntent: (AgentDashboardIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToCustomTools: () -> Unit,
    showNavigationIcon: Boolean = true,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AgentDashboardEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    AppScaffold(
        appearanceTarget = AppearanceTarget.AGENT,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.ai_agent_dashboard),
                scrollBehavior = scrollBehavior,
                navigationIcon = if (showNavigationIcon) {
                    { TopBarNavigationButton(onClick = onBackClick) }
                } else {
                    {}
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AgentDashboardSummary(state = state)
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_agent_controls)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.ai_chat_bubble),
                        description = stringResource(R.string.ai_chat_bubble_summary),
                        checked = state.chatBubbleEnabled,
                        onCheckedChange = {
                            onIntent(AgentDashboardIntent.SetChatBubbleEnabled(it))
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.refresh),
                        onClick = { onIntent(AgentDashboardIntent.Refresh) },
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_skills)) {
                    SettingItem(
                        title = stringResource(R.string.ai_agent_skills_registered, state.skillCount),
                        description = stringResource(
                            R.string.ai_agent_skills_summary,
                            state.enabledSkillCount,
                            state.invalidSkillCount,
                        ),
                    )
                    if (state.skills.isEmpty()) {
                        SettingItem(title = stringResource(R.string.ai_agent_no_skills))
                    } else {
                        state.skills.forEach { skill ->
                            ClickableSettingItem(
                                title = skill.name,
                                description = skill.description.takeIf(String::isNotBlank),
                                option = skill.activeVersion
                                    ?: stringResource(R.string.ai_agent_skill_no_active_version),
                                onClick = { onIntent(AgentDashboardIntent.OpenSkill(skill.id)) },
                            )
                        }
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_agent_context)) {
                    if (state.context.hasContext) {
                        SettingItem(
                            title = state.context.screen,
                            option = if (state.context.sensitive) {
                                stringResource(R.string.ai_agent_context_sensitive)
                            } else {
                                stringResource(R.string.ai_agent_context_available)
                            },
                        )
                        state.context.attributes.forEach { attribute ->
                            SettingItem(
                                title = attribute.key,
                                description = attribute.value,
                            )
                        }
                    } else {
                        SettingItem(
                            title = stringResource(R.string.ai_agent_no_context),
                            description = stringResource(R.string.ai_agent_no_context_summary),
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_agent_sessions)) {
                    if (state.loading) {
                        SettingItem(title = stringResource(R.string.ai_agent_loading))
                    }
                    state.errorMessage?.let { message ->
                        SettingItem(
                            title = stringResource(R.string.ai_agent_load_failed),
                            description = message,
                        )
                    }
                    SettingItem(
                        title = stringResource(R.string.ai_agent_pending_proposals),
                        option = state.pendingProposalCount.toString(),
                    )
                    if (state.recentProposals.isNotEmpty()) {
                        SettingItem(title = stringResource(R.string.ai_agent_recent_proposals))
                    }
                    state.recentProposals.forEach { proposal ->
                        SettingItem(
                            title = proposal.id,
                            description = proposal.conversationId,
                            option = "${proposal.status} · " + stringResource(
                                R.string.ai_agent_proposal_tool_count,
                                proposal.toolCount,
                            ),
                        )
                    }
                    SettingItem(
                        title = stringResource(R.string.ai_agent_audits),
                        option = state.auditCount.toString(),
                    )
                    if (state.recentAudits.isNotEmpty()) {
                        SettingItem(title = stringResource(R.string.ai_agent_recent_audits))
                    }
                    state.recentAudits.forEach { audit ->
                        SettingItem(
                            title = audit.toolName,
                            description = audit.errorMessage ?: audit.risk.riskLabel(),
                            option = "${audit.status} - " + stringResource(
                                R.string.ai_agent_audit_duration,
                                audit.durationMs,
                            ),
                        )
                    }
                    if (state.recentRuns.isEmpty()) {
                        SettingItem(
                            title = stringResource(R.string.ai_agent_no_recent_runs),
                        )
                    } else {
                        state.recentRuns.forEach { run ->
                            ClickableSettingItem(
                                title = run.id,
                                description = run.errorMessage
                                    ?: run.finalTextPreview.takeIf { it.isNotBlank() }
                                    ?: "${run.providerId} / ${run.modelId}",
                                option = run.status,
                                onClick = { onIntent(AgentDashboardIntent.OpenRun(run.id)) },
                            )
                        }
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_agent_memory)) {
                    SettingItem(
                        title = stringResource(R.string.ai_agent_memory_recent, state.memoryCount),
                        description = stringResource(R.string.ai_agent_memory_pinned, state.pinnedMemoryCount),
                    )
                    if (state.recentMemories.isEmpty()) {
                        SettingItem(
                            title = stringResource(R.string.ai_agent_no_memory),
                        )
                    } else {
                        state.recentMemories.forEach { memory ->
                            SettingItem(
                                title = memory.key,
                                description = memory.value,
                                option = memory.memoryLabel(),
                            )
                        }
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_agent_tools)) {
                    SettingItem(
                        title = stringResource(R.string.ai_agent_tools_registered, state.toolCount),
                        description = stringResource(
                            R.string.ai_agent_tools_summary,
                            state.enabledToolCount,
                            state.readToolCount,
                            state.approvalToolCount,
                        ),
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_custom_tools_title),
                        description = stringResource(R.string.ai_custom_tools_entry_summary),
                        onClick = onNavigateToCustomTools,
                    )
                    state.tools.forEach { tool ->
                        SettingItem(
                            title = tool.name,
                            description = tool.description,
                            option = if (tool.enabled) {
                                tool.risk.riskLabel()
                            } else {
                                stringResource(R.string.disabled)
                            },
                        )
                    }
                }
            }
        }
    }

    AgentSkillDialog(
        skill = state.selectedSkill,
        busy = state.selectedSkill?.id?.let { it in state.busySkillIds } == true,
        onIntent = onIntent,
    )
    AgentRunDialog(
        run = state.selectedRun,
        trace = state.selectedRunTrace,
        onDismiss = { onIntent(AgentDashboardIntent.DismissRun) },
    )
    AgentSkillActionDialog(
        action = state.pendingSkillAction,
        onConfirm = { onIntent(AgentDashboardIntent.ConfirmSkillAction) },
        onDismiss = { onIntent(AgentDashboardIntent.DismissSkillAction) },
    )
}

@Composable
private fun AgentRunDialog(
    run: AgentRunUi?,
    trace: List<AgentTraceUi>,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        show = run != null,
        onDismissRequest = onDismiss,
        title = run?.id,
        text = run?.let { "${it.providerId} / ${it.modelId} - ${it.status}" },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = onDismiss,
        content = run?.let {
            {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trace.isEmpty()) {
                        SettingItem(title = stringResource(R.string.ai_agent_no_trace))
                    } else {
                        trace.forEach { step ->
                            SettingItem(
                                title = "${step.index + 1}. ${step.type}",
                                description = step.content.takeIf(String::isNotBlank),
                                option = step.toolName,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun AgentSkillActionDialog(
    action: AgentSkillActionUi?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actionLabel = when (action?.type) {
        AgentSkillActionType.ENABLE -> stringResource(R.string.ai_agent_skill_action_enable)
        AgentSkillActionType.DISABLE -> stringResource(R.string.ai_agent_skill_action_disable)
        AgentSkillActionType.ACTIVATE -> stringResource(R.string.ai_agent_skill_action_activate)
        AgentSkillActionType.ROLLBACK -> stringResource(R.string.ai_agent_skill_action_rollback)
        null -> ""
    }
    AppAlertDialog(
        show = action != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.ai_agent_skill_confirm_title),
        text = action?.let {
            stringResource(R.string.ai_agent_skill_confirm_message, actionLabel, it.skillName)
        },
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun AgentSkillDialog(
    skill: AgentSkillUi?,
    busy: Boolean,
    onIntent: (AgentDashboardIntent) -> Unit,
) {
    AppAlertDialog(
        show = skill != null,
        onDismissRequest = { onIntent(AgentDashboardIntent.DismissSkill) },
        title = skill?.name,
        text = skill?.description,
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onIntent(AgentDashboardIntent.DismissSkill) },
        content = skill?.let { selected ->
            {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingItem(
                        title = stringResource(R.string.ai_agent_skill_active_version),
                        option = selected.activeVersion
                            ?: stringResource(R.string.ai_agent_skill_no_active_version),
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_agent_skill_latest_version),
                        option = selected.latestVersion.orEmpty(),
                        description = if (selected.latestVersionValid) {
                            stringResource(R.string.ai_agent_skill_valid)
                        } else {
                            selected.validationMessage.ifBlank {
                                stringResource(R.string.ai_agent_skill_invalid)
                            }
                        },
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_agent_skill_allowed_tools),
                        description = selected.allowedTools.joinToString().ifBlank {
                            stringResource(R.string.ai_agent_skill_no_tools)
                        },
                    )
                    if (selected.requirements.isNotEmpty()) {
                        SettingItem(
                            title = stringResource(R.string.ai_agent_skill_requirements),
                            description = selected.requirements.joinToString("\n"),
                        )
                    }
                    SwitchSettingItem(
                        title = stringResource(R.string.ai_agent_skill_enabled_title),
                        description = stringResource(R.string.ai_agent_skill_enable_summary),
                        checked = selected.enabled,
                        enabled = !busy && selected.activeVersionId != null,
                        onCheckedChange = {
                            onIntent(AgentDashboardIntent.SetSkillEnabled(selected.id, it))
                        },
                    )
                    if (selected.canActivateLatest && selected.latestVersionId != null) {
                        PrimaryButton(
                            text = stringResource(R.string.ai_agent_skill_activate_latest),
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onIntent(
                                    AgentDashboardIntent.ActivateSkillVersion(
                                        selected.id,
                                        selected.latestVersionId,
                                    )
                                )
                            },
                        )
                    }
                    if (selected.canRollback) {
                        SecondaryButton(
                            text = stringResource(R.string.ai_agent_skill_rollback),
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onIntent(AgentDashboardIntent.RollbackSkill(selected.id)) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AgentDashboardSummary(
    state: AgentDashboardUiState,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AgentMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Build,
                title = stringResource(R.string.ai_agent_tools),
                value = state.toolCount.toString(),
                detail = stringResource(
                    R.string.ai_agent_tools_summary,
                    state.enabledToolCount,
                    state.readToolCount,
                    state.approvalToolCount,
                ),
            )
            AgentMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.ai_agent_pending_proposals),
                value = state.pendingProposalCount.toString(),
                detail = stringResource(
                    R.string.ai_agent_pending_summary,
                    state.pendingProposalCount,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AgentMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.History,
                title = stringResource(R.string.ai_agent_sessions),
                value = state.recentRuns.size.toString(),
                detail = state.recentRuns.firstOrNull()?.status
                    ?: stringResource(R.string.ai_agent_no_recent_runs),
            )
            AgentMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Memory,
                title = stringResource(R.string.ai_agent_memory),
                value = state.memoryCount.toString(),
                detail = stringResource(R.string.ai_agent_memory_pinned, state.pinnedMemoryCount),
            )
        }
        AgentMetricCard(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.AutoAwesome,
            title = stringResource(R.string.ai_skills),
            value = state.enabledSkillCount.toString(),
            detail = stringResource(R.string.ai_agent_skills_registered, state.skillCount),
        )
    }
}

@Composable
private fun AgentMetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    detail: String,
) {
    NormalCard(
        modifier = modifier.heightIn(min = 96.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                AppText(
                    text = title,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            AppText(
                text = value,
                style = LegadoTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            AppText(
                text = detail,
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AgentActionRisk.riskLabel(): String {
    return when (this) {
        AgentActionRisk.READ -> stringResource(R.string.ai_agent_risk_read)
        AgentActionRisk.WRITE -> stringResource(R.string.ai_agent_risk_write)
        AgentActionRisk.DELETE -> stringResource(R.string.ai_agent_risk_delete)
        AgentActionRisk.BULK -> stringResource(R.string.ai_agent_risk_bulk)
        AgentActionRisk.PLUGIN_INSTALL -> stringResource(R.string.ai_agent_risk_plugin_install)
    }
}

private fun AgentMemoryUi.memoryLabel(): String {
    val pin = if (pinned) " *" else ""
    return "$scope/$type$pin"
}
