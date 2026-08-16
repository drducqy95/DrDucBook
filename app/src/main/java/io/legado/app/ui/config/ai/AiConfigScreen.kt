package io.legado.app.ui.config.ai

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
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
