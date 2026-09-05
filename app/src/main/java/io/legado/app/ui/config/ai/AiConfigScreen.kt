package io.legado.app.ui.config.ai

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiConfigRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAiSummary: () -> Unit,
    onNavigateToPromptEditor: () -> Unit,
    onNavigateToAgentDashboard: () -> Unit,
    viewModel: AiConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiConfigEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }

    AiConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToTranslation = onNavigateToTranslation,
        onNavigateToAiSummary = onNavigateToAiSummary,
        onNavigateToPromptEditor = onNavigateToPromptEditor,
        onNavigateToAgentDashboard = onNavigateToAgentDashboard,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(
    state: AiConfigUiState,
    onIntent: (AiConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAiSummary: () -> Unit,
    onNavigateToPromptEditor: () -> Unit,
    onNavigateToAgentDashboard: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.ai_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_providers)) {
                    DropdownListSettingItem(
                        title = stringResource(R.string.ai_model_discovery_frequency),
                        selectedValue = state.discoveryFrequencyHours.toString(),
                        displayEntries = arrayOf(
                            stringResource(R.string.ai_discovery_manual),
                            stringResource(R.string.ai_discovery_6h),
                            stringResource(R.string.ai_discovery_12h),
                            stringResource(R.string.ai_discovery_24h),
                        ),
                        entryValues = arrayOf("0", "6", "12", "24"),
                        onValueChange = { value ->
                            value.toLongOrNull()?.let { hours ->
                                onIntent(AiConfigIntent.SetDiscoveryFrequency(hours))
                            }
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_refresh_all_models),
                        description = if (state.isRefreshingModels) {
                            stringResource(R.string.loading)
                        } else {
                            stringResource(R.string.ai_refresh_all_models_summary)
                        },
                        onClick = {
                            onIntent(AiConfigIntent.RefreshAllModels)
                        }
                    )
                }
            }

            if (state.providerHealthList.isNotEmpty()) {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.ai_provider_health)) {
                        state.providerHealthList.forEach { health ->
                            val statusText = when (health.status) {
                                ProviderHealthStatus.HEALTHY -> stringResource(R.string.ai_health_healthy)
                                ProviderHealthStatus.DEGRADED -> stringResource(
                                    R.string.ai_health_consecutive_failures,
                                    health.consecutiveFailures,
                                )
                                ProviderHealthStatus.COOLDOWN -> {
                                    val remainingSec = ((health.cooldownUntil - System.currentTimeMillis()) / 1000)
                                        .coerceAtLeast(1)
                                    stringResource(R.string.ai_health_cooldown_remaining, remainingSec)
                                }
                                ProviderHealthStatus.DISABLED -> stringResource(R.string.ai_health_disabled)
                            }
                            val keyInfo = stringResource(
                                R.string.ai_health_keys_active,
                                health.activeCredentials,
                                health.totalCredentials,
                            )
                            val description = if (health.status == ProviderHealthStatus.DISABLED) {
                                statusText
                            } else {
                                "$statusText · $keyInfo"
                            }
                            val isActionable = health.status == ProviderHealthStatus.COOLDOWN ||
                                health.status == ProviderHealthStatus.DEGRADED

                            ClickableSettingItem(
                                title = health.providerName,
                                description = description,
                                option = if (isActionable) stringResource(R.string.ai_health_reset) else null,
                                onClick = {
                                    if (isActionable) {
                                        onIntent(AiConfigIntent.ResetProviderHealth(health.providerId))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_tasks)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.translation_config),
                        onClick = onNavigateToTranslation
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_chapter_summary),
                        onClick = onNavigateToAiSummary
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_title),
                        description = stringResource(R.string.ai_prompt_editor_summary),
                        onClick = onNavigateToPromptEditor,
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_skills)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.ai_chat_bubble),
                        description = stringResource(R.string.ai_chat_bubble_summary),
                        checked = state.chatBubbleEnabled,
                        onCheckedChange = {
                            onIntent(AiConfigIntent.SetChatBubbleEnabled(it))
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_agent_dashboard),
                        description = stringResource(R.string.ai_agent_dashboard_summary),
                        onClick = onNavigateToAgentDashboard,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_new_skill),
                        onClick = {}
                    )
                }
            }
        }
    }
}
