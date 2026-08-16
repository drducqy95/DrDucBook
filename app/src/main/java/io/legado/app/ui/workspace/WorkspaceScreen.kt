package io.legado.app.ui.workspace

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.PersonalizedIcon
import io.legado.app.domain.model.IconSlot
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun WorkspaceRouteScreen(
    onOpenWriting: () -> Unit,
    onOpenEbookEditor: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenRss: () -> Unit,
    onOpenStoryWiki: () -> Unit,
    viewModel: WorkspaceViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOpenWriting by rememberUpdatedState(onOpenWriting)
    val currentOpenEbookEditor by rememberUpdatedState(onOpenEbookEditor)
    val currentOpenAgent by rememberUpdatedState(onOpenAgent)
    val currentOpenRss by rememberUpdatedState(onOpenRss)
    val currentOpenStoryWiki by rememberUpdatedState(onOpenStoryWiki)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is WorkspaceEffect.OpenModule -> when (effect.module) {
                    WorkspaceModule.WRITING -> currentOpenWriting()
                    WorkspaceModule.EBOOK_EDITOR -> currentOpenEbookEditor()
                    WorkspaceModule.AGENT -> currentOpenAgent()
                    WorkspaceModule.RSS -> currentOpenRss()
                    WorkspaceModule.STORY_WIKI -> currentOpenStoryWiki()
                }
            }
        }
    }

    WorkspaceScreen(state = state, onIntent = viewModel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
) {
    AppScaffold(
        appearanceTarget = AppearanceTarget.WORKSPACE,
        topBar = {
            GlassTopAppBar(
                title = stringResource(R.string.workspace_title),
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        personalizationSlot = IconSlot.TOOLBAR_REFRESH,
                        onClick = { onIntent(WorkspaceIntent.Refresh) },
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = paddingValues.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                WorkspaceSectionTitle(stringResource(R.string.workspace_tools))
            }
            items(state.modules, key = { it.module.name }) { module ->
                WorkspaceModuleRow(module = module, onIntent = onIntent)
            }
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.hasError) {
                item {
                    WorkspaceError(onRetry = { onIntent(WorkspaceIntent.Refresh) })
                }
            }
            item {
                WorkspaceSectionTitle(
                    text = stringResource(R.string.workspace_recent),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (!state.isLoading && !state.hasError && state.recentItems.isEmpty()) {
                item {
                    AppText(
                        text = stringResource(R.string.workspace_recent_empty),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.recentItems, key = WorkspaceRecentUi::id) { item ->
                    WorkspaceRecentRow(item = item, onIntent = onIntent)
                    HorizontalDivider(color = LegadoTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSectionTitle(text: String, modifier: Modifier = Modifier) {
    AppText(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        style = LegadoTheme.typography.titleMedium,
    )
}

@Composable
private fun WorkspaceModuleRow(
    module: WorkspaceModuleUi,
    onIntent: (WorkspaceIntent) -> Unit,
) {
    val label = module.module.label()
    val badgeDescription = module.badgeCount?.let {
        stringResource(R.string.workspace_item_count, label, it)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = badgeDescription ?: label
                role = Role.Button
            }
            .clickable(enabled = module.available) {
                onIntent(WorkspaceIntent.OpenModule(module.module))
            },
        shape = RoundedCornerShape(8.dp),
        color = LegadoTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        ListItem(
            supportingContent = if (module.available) null else {
                { AppText(stringResource(R.string.workspace_unavailable)) }
            },
            leadingContent = {
                PersonalizedIcon(
                    slot = module.module.iconSlot(),
                    fallback = module.module.icon(),
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                )
            },
            trailingContent = module.badgeCount?.let { count ->
                {
                    Badge { AppText(count.toString()) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            AppText(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WorkspaceRecentRow(
    item: WorkspaceRecentUi,
    onIntent: (WorkspaceIntent) -> Unit,
) {
    val moduleLabel = item.module.label()
    val title = item.title.ifBlank { moduleLabel }
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        item.updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable { onIntent(WorkspaceIntent.OpenModule(item.module)) },
        supportingContent = {
            AppText(
                text = stringResource(R.string.workspace_recent_detail, moduleLabel, relativeTime),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            PersonalizedIcon(
                slot = item.module.iconSlot(),
                fallback = item.module.icon(),
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        AppText(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WorkspaceError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppText(
            text = stringResource(R.string.workspace_load_failed),
            color = LegadoTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            AppText(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun WorkspaceModule.label(): String = stringResource(
    when (this) {
        WorkspaceModule.WRITING -> R.string.writing_title
        WorkspaceModule.EBOOK_EDITOR -> R.string.ebook_editor_title
        WorkspaceModule.AGENT -> R.string.ai_agent_nav
        WorkspaceModule.RSS -> R.string.workspace_rss_sources
        WorkspaceModule.STORY_WIKI -> R.string.story_wiki_title
    }
)

private fun WorkspaceModule.icon(): ImageVector = when (this) {
    WorkspaceModule.WRITING -> Icons.Default.EditNote
    WorkspaceModule.EBOOK_EDITOR -> Icons.Default.AutoStories
    WorkspaceModule.AGENT -> Icons.Default.AutoAwesome
    WorkspaceModule.RSS -> Icons.Default.RssFeed
    WorkspaceModule.STORY_WIKI -> Icons.Default.MenuBook
}

private fun WorkspaceModule.iconSlot(): IconSlot = when (this) {
    WorkspaceModule.WRITING -> IconSlot.WORKSPACE_WRITING
    WorkspaceModule.EBOOK_EDITOR -> IconSlot.WORKSPACE_EBOOK
    WorkspaceModule.AGENT -> IconSlot.WORKSPACE_AGENT
    WorkspaceModule.RSS -> IconSlot.WORKSPACE_RSS
    WorkspaceModule.STORY_WIKI -> IconSlot.WORKSPACE_EBOOK
}
