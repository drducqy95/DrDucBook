package io.legado.app.ui.vbook.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.drducbook.app.R
import io.legado.app.domain.model.VbookImportAction
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun VbookImportRouteScreen(
    onBackClick: () -> Unit,
    viewModel: VbookImportViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onIntent(VbookImportIntent.FileSelected(it.toString())) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                VbookImportEffect.OpenJsonFilePicker -> filePicker.launch(
                    arrayOf("application/json", "text/json", "text/plain")
                )
                is VbookImportEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }

    VbookImportScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@Composable
fun VbookImportScreen(
    state: VbookImportUiState,
    onIntent: (VbookImportIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val filteredItems = remember(state.items, state.searchQuery) {
        val query = state.searchQuery.trim()
        if (query.isEmpty()) state.items else state.items.filter { item ->
            item.name.contains(query, ignoreCase = true) ||
                item.author.contains(query, ignoreCase = true) ||
                item.declaredKind.name.contains(query, ignoreCase = true)
        }
    }
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.vbook_registry_import),
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = stringResource(R.string.vbook_registry_choose_json),
                        onClick = { onIntent(VbookImportIntent.PickJsonFile) },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "input") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = { onIntent(VbookImportIntent.ChangeInput(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { AppText(stringResource(R.string.vbook_registry_url_or_file)) },
                        enabled = !state.loading && !state.installing,
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onIntent(VbookImportIntent.PickJsonFile) },
                            enabled = !state.loading && !state.installing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            AppText(
                                stringResource(R.string.vbook_registry_choose_json),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Button(
                            onClick = { onIntent(VbookImportIntent.Preview) },
                            enabled = state.input.isNotBlank() && !state.loading && !state.installing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            AppText(
                                stringResource(R.string.vbook_registry_preview),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (state.loading || state.installing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (state.installing) {
                        AppText(
                            text = stringResource(
                                R.string.vbook_registry_progress,
                                state.progressCompleted,
                                state.progressTotal,
                                state.progressName,
                            ),
                            style = LegadoTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (state.items.isNotEmpty()) {
                item(key = "search-selection") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { onIntent(VbookImportIntent.ChangeSearch(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { AppText(stringResource(R.string.search)) },
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onIntent(VbookImportIntent.SelectAllInstallable) },
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(stringResource(R.string.select_all))
                            }
                            OutlinedButton(
                                onClick = { onIntent(VbookImportIntent.ClearSelection) },
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(stringResource(R.string.clear))
                            }
                        }
                        AppText(
                            text = stringResource(
                                R.string.vbook_registry_preview_count,
                                state.items.size,
                                state.rejectedItemCount,
                            ),
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(
                items = filteredItems,
                key = { it.pluginId },
                contentType = { "vbook-plugin" },
            ) { item ->
                val selectable = item.compatible &&
                    item.action in setOf(VbookImportAction.INSTALL, VbookImportAction.UPDATE)
                NormalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = selectable && !state.installing) {
                            onIntent(VbookImportIntent.TogglePlugin(item.pluginId))
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = item.iconUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 12.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(
                                item.name,
                                style = LegadoTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AppText(
                                stringResource(
                                    R.string.vbook_registry_item_meta,
                                    item.author,
                                    item.version,
                                    item.declaredKind.name,
                                ),
                                style = LegadoTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                            AppText(
                                actionLabel(item.action),
                                style = LegadoTheme.typography.labelMedium,
                                color = if (selectable) {
                                    LegadoTheme.colorScheme.primary
                                } else {
                                    LegadoTheme.colorScheme.error
                                },
                            )
                        }
                        Checkbox(
                            checked = item.pluginId in state.selectedPluginIds,
                            onCheckedChange = if (selectable && !state.installing) {
                                { onIntent(VbookImportIntent.TogglePlugin(item.pluginId)) }
                            } else null,
                            enabled = selectable && !state.installing,
                        )
                    }
                    HorizontalDivider()
                }
            }

            if (state.items.isNotEmpty()) {
                item(key = "install") {
                    Button(
                        onClick = { onIntent(VbookImportIntent.InstallSelected) },
                        enabled = state.selectedPluginIds.isNotEmpty() && !state.installing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(
                            stringResource(
                                R.string.vbook_registry_install_selected,
                                state.selectedPluginIds.size,
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun actionLabel(action: VbookImportAction): String = stringResource(
    when (action) {
        VbookImportAction.INSTALL -> R.string.vbook_registry_action_install
        VbookImportAction.UPDATE -> R.string.vbook_registry_action_update
        VbookImportAction.SKIP_SAME -> R.string.vbook_registry_action_same
        VbookImportAction.DOWNGRADE_WARNING -> R.string.vbook_registry_action_downgrade
        VbookImportAction.DUPLICATE_URL_WARNING -> R.string.vbook_registry_action_conflict
    }
)
