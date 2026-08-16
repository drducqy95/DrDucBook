package io.legado.app.ui.ai.agent.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.agenttools.CustomAgentToolLifecycleState
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun CustomAgentToolManagerRouteScreen(
    onBackClick: () -> Unit,
    showNavigationIcon: Boolean = true,
    viewModel: CustomAgentToolManagerViewModel = koinViewModel(),
) {
    CustomAgentToolManagerScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        showNavigationIcon = showNavigationIcon,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAgentToolManagerScreen(
    state: CustomAgentToolManagerUiState,
    effects: Flow<CustomAgentToolManagerEffect>,
    onIntent: (CustomAgentToolManagerIntent) -> Unit,
    onBackClick: () -> Unit,
    showNavigationIcon: Boolean = true,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is CustomAgentToolManagerEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    AppScaffold(
        appearanceTarget = AppearanceTarget.AGENT,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.ai_custom_tools_title),
                subtitle = stringResource(R.string.ai_custom_tools_summary, state.tools.size),
                scrollBehavior = scrollBehavior,
                navigationIcon = if (showNavigationIcon) {
                    { TopBarNavigationButton(onClick = onBackClick) }
                } else {
                    {}
                },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(CustomAgentToolManagerIntent.NewDraft) },
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.ai_custom_tool_new),
                    )
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
                SplicedColumnGroup(title = stringResource(R.string.ai_custom_tools_overview)) {
                    SettingItem(
                        title = stringResource(R.string.ai_custom_tools_registered, state.tools.size),
                        description = stringResource(R.string.ai_custom_tools_gate_summary),
                    )
                    if (state.loading) {
                        SettingItem(title = stringResource(R.string.ai_agent_loading))
                    }
                    state.errorMessage?.let { message ->
                        SettingItem(
                            title = stringResource(R.string.ai_custom_tool_load_failed),
                            description = message,
                        )
                    }
                }
            }

            if (state.editorVisible) {
                item {
                    CustomAgentToolEditor(state = state, onIntent = onIntent)
                }
            }

            if (state.tools.isEmpty()) {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.ai_custom_tools_title)) {
                        SettingItem(
                            title = stringResource(R.string.ai_custom_tools_empty),
                            description = stringResource(R.string.ai_custom_tools_empty_summary),
                        )
                    }
                }
            } else {
                items(
                    items = state.tools,
                    key = CustomAgentToolUi::id,
                ) { tool ->
                    CustomAgentToolCard(
                        tool = tool,
                        busy = tool.id in state.busyToolIds,
                        onIntent = onIntent,
                    )
                }
            }
        }
    }

    AppAlertDialog(
        show = state.pendingDelete != null,
        onDismissRequest = { onIntent(CustomAgentToolManagerIntent.DismissDelete) },
        title = stringResource(R.string.ai_custom_tool_delete_title),
        text = state.pendingDelete?.let {
            stringResource(R.string.ai_custom_tool_delete_message, it.name)
        },
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(android.R.string.cancel),
        onConfirm = { onIntent(CustomAgentToolManagerIntent.ConfirmDelete) },
        onDismiss = { onIntent(CustomAgentToolManagerIntent.DismissDelete) },
    )
}

@Composable
private fun CustomAgentToolEditor(
    state: CustomAgentToolManagerUiState,
    onIntent: (CustomAgentToolManagerIntent) -> Unit,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppText(
                text = stringResource(R.string.ai_custom_tool_editor),
                style = LegadoTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = state.manifestJson,
                onValueChange = { onIntent(CustomAgentToolManagerIntent.UpdateManifest(it)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                label = { AppText(stringResource(R.string.ai_custom_tool_manifest_json)) },
                textStyle = LegadoTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 10,
            )
            OutlinedTextField(
                value = state.fixtureArgumentsJson,
                onValueChange = { onIntent(CustomAgentToolManagerIntent.UpdateFixtureArguments(it)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                label = { AppText(stringResource(R.string.ai_custom_tool_fixture_json)) },
                textStyle = LegadoTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryButton(
                    text = stringResource(R.string.ai_custom_tool_save_draft),
                    modifier = Modifier.weight(1f),
                    onClick = { onIntent(CustomAgentToolManagerIntent.SaveDraft) },
                )
                SecondaryButton(
                    text = stringResource(android.R.string.cancel),
                    modifier = Modifier.weight(1f),
                    onClick = { onIntent(CustomAgentToolManagerIntent.DismissEditor) },
                )
            }
        }
    }
}

@Composable
private fun CustomAgentToolCard(
    tool: CustomAgentToolUi,
    busy: Boolean,
    onIntent: (CustomAgentToolManagerIntent) -> Unit,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = tool.name,
                        style = LegadoTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = tool.toolName,
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppText(
                    text = if (tool.enabled) {
                        stringResource(R.string.enabled)
                    } else {
                        stringResource(R.string.disabled)
                    },
                    style = LegadoTheme.typography.labelLarge,
                    color = if (tool.enabled) {
                        LegadoTheme.colorScheme.primary
                    } else {
                        LegadoTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (tool.description.isNotBlank()) {
                AppText(
                    text = tool.description,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingItem(
                title = stringResource(R.string.ai_custom_tool_versions, tool.versionCount),
                description = stringResource(
                    R.string.ai_custom_tool_version_summary,
                    tool.activeVersion ?: "-",
                    tool.latestVersion ?: "-",
                ),
                option = tool.latestLifecycle.label(),
            )
            SettingItem(
                title = stringResource(R.string.ai_custom_tool_validation),
                description = tool.latestValidationMessage.takeIf(String::isNotBlank),
                option = tool.latestValidationStatus.ifBlank { "-" },
            )
            SettingItem(
                title = stringResource(R.string.ai_custom_tool_fixture),
                description = tool.latestTestMessage.takeIf(String::isNotBlank)
                    ?: tool.latestTestOutputJson?.take(160),
                option = tool.latestTestStatus.ifBlank { "-" },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.edit),
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { onIntent(CustomAgentToolManagerIntent.EditLatest(tool.id)) },
                    )
                    SecondaryButton(
                        text = stringResource(R.string.ai_custom_tool_validate),
                        enabled = !busy && tool.canValidate,
                        modifier = Modifier.weight(1f),
                        onClick = { onIntent(CustomAgentToolManagerIntent.ValidateLatest(tool.id)) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.ai_custom_tool_run_fixture),
                        enabled = !busy && tool.canRunFixture,
                        modifier = Modifier.weight(1f),
                        onClick = { onIntent(CustomAgentToolManagerIntent.RunFixture(tool.id)) },
                    )
                    PrimaryButton(
                        text = stringResource(R.string.ai_custom_tool_approve),
                        enabled = !busy && tool.canApprove,
                        modifier = Modifier.weight(1f),
                        onClick = { onIntent(CustomAgentToolManagerIntent.ApproveLatest(tool.id)) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryButton(
                        text = if (tool.enabled) {
                            stringResource(R.string.ai_custom_tool_disable)
                        } else {
                            stringResource(R.string.ai_custom_tool_enable)
                        },
                        enabled = !busy && (tool.canEnable || tool.canDisable),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onIntent(CustomAgentToolManagerIntent.SetEnabled(tool.id, !tool.enabled))
                        },
                    )
                    SecondaryButton(
                        text = stringResource(R.string.ai_custom_tool_rollback),
                        enabled = !busy && tool.canRollback,
                        modifier = Modifier.weight(1f),
                        onClick = { onIntent(CustomAgentToolManagerIntent.Rollback(tool.id)) },
                    )
                }
                SecondaryButton(
                    text = stringResource(R.string.delete),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onIntent(CustomAgentToolManagerIntent.RequestDelete(tool.id)) },
                )
            }
        }
    }
}

@Composable
private fun CustomAgentToolLifecycleState.label(): String {
    return when (this) {
        CustomAgentToolLifecycleState.DRAFT -> stringResource(R.string.ai_custom_tool_state_draft)
        CustomAgentToolLifecycleState.VALIDATED -> stringResource(R.string.ai_custom_tool_state_validated)
        CustomAgentToolLifecycleState.APPROVED -> stringResource(R.string.ai_custom_tool_state_approved)
        CustomAgentToolLifecycleState.ENABLED -> stringResource(R.string.enabled)
        CustomAgentToolLifecycleState.DISABLED -> stringResource(R.string.disabled)
        CustomAgentToolLifecycleState.REVOKED -> stringResource(R.string.ai_custom_tool_state_revoked)
        CustomAgentToolLifecycleState.QUARANTINED -> stringResource(R.string.ai_custom_tool_state_quarantined)
    }
}
