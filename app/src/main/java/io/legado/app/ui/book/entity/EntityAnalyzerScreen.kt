package io.legado.app.ui.book.entity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun EntityAnalyzerRouteScreen(
    bookUrl: String,
    onBack: () -> Unit,
    viewModel: EntityAnalyzerViewModel = koinViewModel(
        key = "EntityAnalyzer:$bookUrl",
        parameters = { parametersOf(bookUrl) },
    ),
) {
    EntityAnalyzerScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityAnalyzerScreen(
    state: EntityAnalyzerUiState,
    effects: Flow<EntityAnalyzerEffect>,
    onIntent: (EntityAnalyzerIntent) -> Unit,
    onBack: () -> Unit,
) {
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is EntityAnalyzerEffect.ShowMessage -> {
                    val message = effect.count?.let {
                        resources.getString(effect.messageRes, it)
                    } ?: resources.getString(effect.messageRes)
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.entity_analyzer_title),
                subtitle = state.bookName.ifBlank {
                    stringResource(R.string.entity_analyzer_downloaded_only)
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(EntityAnalyzerIntent.Analyze) },
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.entity_analyzer_analyze_again),
                    )
                },
            )
        },
        bottomBar = {
            Surface(
                color = LegadoTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onIntent(EntityAnalyzerIntent.ClearSelection) },
                        enabled = state.selectedCount > 0 && !state.importing,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(R.string.entity_analyzer_clear_selection))
                    }
                    Button(
                        onClick = { onIntent(EntityAnalyzerIntent.RequestImport) },
                        enabled = state.selectedCount > 0 &&
                            !state.analyzing &&
                            !state.importing,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(
                            if (state.importing) {
                                stringResource(R.string.entity_analyzer_importing)
                            } else {
                                stringResource(
                                    R.string.entity_analyzer_import_selected,
                                    state.selectedCount,
                                )
                            }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(contentType = "status") {
                AnalysisStatus(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item(contentType = "filters") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { onIntent(EntityAnalyzerIntent.Search(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        label = { AppText(stringResource(R.string.entity_analyzer_search)) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AppText(
                            text = stringResource(
                                R.string.entity_analyzer_candidate_summary,
                                state.candidateCount,
                                state.selectedCount,
                            ),
                            style = LegadoTheme.typography.bodyMedium,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { onIntent(EntityAnalyzerIntent.SelectVisible) },
                            enabled = state.candidates.isNotEmpty() && !state.importing,
                        ) {
                            AppText(stringResource(R.string.entity_analyzer_select_visible))
                        }
                    }
                }
            }

            if (!state.analyzing && state.candidates.isEmpty()) {
                item(contentType = "empty") {
                    AppText(
                        text = state.errorRes?.let { stringResource(it) }
                            ?: stringResource(R.string.entity_analyzer_no_candidates),
                        modifier = Modifier.padding(24.dp),
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(
                items = state.candidates,
                key = EntityCandidateUi::raw,
                contentType = { "candidate" },
            ) { candidate ->
                CandidateRow(
                    candidate = candidate,
                    onToggle = {
                        onIntent(EntityAnalyzerIntent.ToggleCandidate(candidate.raw))
                    },
                    onEdit = {
                        onIntent(EntityAnalyzerIntent.EditCandidate(candidate.raw))
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }

    EntityAnalyzerDialogs(state.dialog, onIntent)
}

@Composable
private fun AnalysisStatus(
    state: EntityAnalyzerUiState,
    onIntent: (EntityAnalyzerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LegadoTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText(
                text = stringResource(R.string.entity_analyzer_downloaded_only),
                style = LegadoTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            AppText(
                text = stringResource(
                    R.string.entity_analyzer_progress,
                    state.scannedChapters,
                    state.totalChapters,
                    state.downloadedChapters,
                    state.trackedCandidates,
                ),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            if (state.analyzing) {
                val progress = if (state.totalChapters > 0) {
                    state.scannedChapters.toFloat() / state.totalChapters.toFloat()
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onIntent(EntityAnalyzerIntent.CancelAnalysis) }) {
                    AppText(stringResource(R.string.entity_analyzer_cancel))
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: EntityCandidateUi,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (candidate.selected) {
            LegadoTheme.colorScheme.secondaryContainer
        } else {
            LegadoTheme.colorScheme.surfaceContainerLow
        },
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            headlineContent = {
                AppText(
                    text = candidate.raw,
                    style = LegadoTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    AppText(
                        text = candidate.hanViet,
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.primary,
                    )
                    AppText(
                        text = candidate.target,
                        style = LegadoTheme.typography.bodyMedium,
                    )
                    AppText(
                        text = stringResource(
                            R.string.entity_analyzer_stats,
                            candidate.occurrences,
                            candidate.chapterCount,
                            candidate.firstChapterTitle,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    AppText(
                        text = candidate.context,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = {
                Checkbox(checked = candidate.selected, onCheckedChange = { onToggle() })
            },
            trailingContent = {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.entity_analyzer_edit),
                    )
                }
            },
        )
    }
}

@Composable
private fun EntityAnalyzerDialogs(
    dialog: EntityAnalyzerDialog?,
    onIntent: (EntityAnalyzerIntent) -> Unit,
) {
    when (dialog) {
        is EntityAnalyzerDialog.Edit -> {
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(EntityAnalyzerIntent.DismissDialog) },
                title = stringResource(R.string.entity_analyzer_edit_title, dialog.raw),
                confirmText = stringResource(R.string.entity_analyzer_save),
                onConfirm = { onIntent(EntityAnalyzerIntent.SaveEdit) },
                dismissText = stringResource(R.string.entity_analyzer_cancel),
                onDismiss = { onIntent(EntityAnalyzerIntent.DismissDialog) },
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = dialog.hanViet,
                            onValueChange = {
                                onIntent(EntityAnalyzerIntent.UpdateEditHanViet(it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { AppText(stringResource(R.string.quick_dictionary_han_viet)) },
                        )
                        OutlinedTextField(
                            value = dialog.target,
                            onValueChange = {
                                onIntent(EntityAnalyzerIntent.UpdateEditTarget(it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { AppText(stringResource(R.string.quick_dictionary_target)) },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                QuickDictionaryType.NAME to R.string.entity_analyzer_type_name,
                                QuickDictionaryType.TERM to R.string.entity_analyzer_type_term,
                            ).forEach { (type, labelRes) ->
                                FilterChip(
                                    selected = dialog.type == type,
                                    onClick = {
                                        onIntent(EntityAnalyzerIntent.UpdateEditType(type))
                                    },
                                    label = { AppText(stringResource(labelRes)) },
                                )
                            }
                        }
                    }
                },
            )
        }

        is EntityAnalyzerDialog.ConfirmImport -> {
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(EntityAnalyzerIntent.DismissDialog) },
                title = stringResource(R.string.entity_analyzer_confirm_title),
                text = stringResource(R.string.entity_analyzer_confirm_text, dialog.count),
                confirmText = stringResource(R.string.entity_analyzer_confirm),
                onConfirm = { onIntent(EntityAnalyzerIntent.ConfirmImport) },
                dismissText = stringResource(R.string.entity_analyzer_cancel),
                onDismiss = { onIntent(EntityAnalyzerIntent.DismissDialog) },
            )
        }

        null -> Unit
    }
}
