package io.legado.app.ui.config.translation.dictionary

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.openUrl
import io.legado.app.utils.FilteredOpenDocumentContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun QuickDictionaryManagerRouteScreen(
    projectKey: String?,
    initialText: String?,
    requestImportFile: Boolean,
    onBackClick: () -> Unit,
    viewModel: QuickDictionaryManagerViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val routeScope = rememberCoroutineScope()
    var dictImportJob by remember { mutableStateOf<Job?>(null) }
    val launcher = rememberLauncherForActivityResult(
        FilteredOpenDocumentContract(
            primaryMimeType = "application/zip",
            persistableAccess = false,
        )
    ) { uri ->
        viewModel.onIntent(
            QuickDictionaryManagerIntent.Initialize(projectKey, initialText)
        )
        uri ?: return@rememberLauncherForActivityResult
        dictImportJob?.cancel()
        dictImportJob = routeScope.launch {
            val copied = withContext(Dispatchers.IO) {
                cleanOrphanedDictionaryStaging(context.cacheDir)
                runCatching {
                    val fileName = context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment
                    val localFile = File.createTempFile(
                        "quick-dictionary-import-",
                        ".source",
                        context.cacheDir,
                    )
                    try {
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "Không thể mở tệp từ điển đã chọn" }
                            localFile.outputStream().buffered().use(input::copyTo)
                        }
                        fileName to localFile.absolutePath
                    } catch (error: Throwable) {
                        localFile.delete()
                        throw error
                    }
                }
            }
            copied.onSuccess { (fileName, localPath) ->
                viewModel.onIntent(QuickDictionaryManagerIntent.ImportFile(fileName, localPath))
            }.onFailure {
                viewModel.onIntent(QuickDictionaryManagerIntent.ImportFileFailed)
            }
            dictImportJob = null
        }
    }
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                QuickDictionaryManagerEffect.OpenImportFile -> {
                    launcher.launch(quickDictionaryImportMimeTypes)
                }
                is QuickDictionaryManagerEffect.OpenUrl -> {
                    context.openUrl(effect.url)
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(projectKey, initialText, requestImportFile) {
        if (requestImportFile) {
            launcher.launch(quickDictionaryImportMimeTypes)
        } else {
            viewModel.onIntent(
                QuickDictionaryManagerIntent.Initialize(projectKey, initialText)
            )
        }
    }
    QuickDictionaryManagerScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

private val quickDictionaryImportMimeTypes = arrayOf(
    "text/plain",
    "text/csv",
    "text/tab-separated-values",
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDictionaryManagerScreen(
    state: QuickDictionaryManagerUiState,
    effects: Flow<QuickDictionaryManagerEffect>,
    onIntent: (QuickDictionaryManagerIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            if (effect is QuickDictionaryManagerEffect.ShowMessage) {
                val message = when {
                    effect.count != null && effect.secondaryCount != null -> resources.getString(
                        effect.messageRes,
                        effect.count,
                        effect.secondaryCount,
                    )
                    effect.count != null -> resources.getString(effect.messageRes, effect.count)
                    else -> resources.getString(effect.messageRes)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.quick_dictionary_manager),
                subtitle = stringResource(
                    R.string.quick_dictionary_custom_count,
                    state.totalCustomEntries,
                ),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(QuickDictionaryManagerIntent.OpenDownloadCatalog) },
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(R.string.quick_dictionary_download_catalog),
                    )
                    TopBarActionButton(
                        onClick = {
                            if (!state.importing) {
                                onIntent(QuickDictionaryManagerIntent.RequestImportFile)
                            }
                        },
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = stringResource(R.string.quick_dictionary_import_file),
                    )
                },
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(QuickDictionaryManagerIntent.Add) },
                icon = Icons.Default.Add,
                tooltipText = stringResource(R.string.quick_dictionary_add),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AppText(
                    text = stringResource(R.string.quick_dictionary_bundled),
                    style = LegadoTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 3,
                ) {
                    state.catalogs.forEach { catalog ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.selectedCatalogId == catalog.id) {
                                LegadoTheme.colorScheme.secondaryContainer
                            } else {
                                LegadoTheme.colorScheme.surfaceContainer
                            },
                            onClick = {
                                onIntent(
                                    QuickDictionaryManagerIntent.SelectCatalog(
                                        id = catalog.id,
                                        type = catalog.type,
                                    )
                                )
                            },
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                AppText(catalog.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                AppText(
                                    stringResource(R.string.quick_dictionary_entry_count, catalog.entryCount),
                                    style = LegadoTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }

            if (state.importing) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = LegadoTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppText(
                                stringResource(
                                    R.string.quick_dictionary_import_progress,
                                    state.importProcessed,
                                    state.importSucceeded,
                                    state.importDuplicates,
                                )
                            )
                            val byteProgress = if (state.importTotalBytes > 0) {
                                state.importProcessedBytes.toFloat() / state.importTotalBytes.toFloat()
                            } else {
                                0f
                            }
                            LinearProgressIndicator(
                                progress = { byteProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (state.packs.isNotEmpty()) {
                item {
                    AppText(
                        text = stringResource(R.string.quick_dictionary_large_packs),
                        style = LegadoTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(
                    items = state.packs,
                    key = QuickDictionaryPackUi::id,
                ) { pack ->
                    QuickDictionaryPackRow(pack = pack, onIntent = onIntent)
                }
            }

            item {
                AppText(
                    text = stringResource(R.string.quick_dictionary_filters),
                    style = LegadoTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickDictionaryType.entries.forEach { type ->
                        FilterChip(
                            selected = state.selectedType == type,
                            onClick = { onIntent(QuickDictionaryManagerIntent.SelectType(type)) },
                            label = { AppText(stringResource(type.labelRes())) },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickDictionaryScope.entries.forEach { scope ->
                        FilterChip(
                            selected = state.selectedScope == scope,
                            onClick = { onIntent(QuickDictionaryManagerIntent.SelectScope(scope)) },
                            label = { AppText(stringResource(scope.labelRes())) },
                        )
                    }
                }
                ScopeKeyMenu(state = state, onIntent = onIntent)
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onIntent(QuickDictionaryManagerIntent.Search(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, null) },
                    placeholder = { AppText(stringResource(R.string.quick_dictionary_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                )
            }

            if (state.rows.isEmpty() && !state.loading) {
                item {
                    AppText(
                        stringResource(R.string.quick_dictionary_no_entries),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                items(
                    items = state.rows,
                    key = { row -> "${row.catalogId}:${row.bundled}:${row.id}:${row.type}:${row.raw}" },
                ) { row ->
                    QuickDictionaryRow(row = row, onIntent = onIntent)
                }
            }
        }
    }

    DictionaryEditorSheet(state = state, onIntent = onIntent)
    SelectionDialog(state = state, onIntent = onIntent)
}

@Composable
private fun QuickDictionaryPackRow(
    pack: QuickDictionaryPackUi,
    onIntent: (QuickDictionaryManagerIntent) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(8.dp),
        color = LegadoTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            headlineContent = {
                AppText(pack.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                AppText(
                    stringResource(
                        R.string.quick_dictionary_pack_summary,
                        stringResource(pack.type.labelRes()),
                        stringResource(pack.scope.labelRes()),
                        pack.entryCount,
                        formatFileSize(pack.indexBytes),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                IconButton(
                    onClick = { onIntent(QuickDictionaryManagerIntent.DeletePack(pack.id)) }
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Delete,
                        stringResource(R.string.delete),
                    )
                }
            },
        )
    }
}

@Composable
private fun ScopeKeyMenu(
    state: QuickDictionaryManagerUiState,
    onIntent: (QuickDictionaryManagerIntent) -> Unit,
) {
    if (state.selectedScope == QuickDictionaryScope.GLOBAL) return
    var expanded by remember { mutableStateOf(false) }
    val options = if (state.selectedScope == QuickDictionaryScope.PROJECT) {
        state.projects.map { it.key to listOf(it.name, it.author).filter(String::isNotBlank).joinToString(" — ") }
    } else {
        state.universes.map { it.key to it.name }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            AppText(
                options.firstOrNull { it.first == state.selectedScopeKey }?.second
                    ?: stringResource(R.string.quick_dictionary_select_scope),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { AppText(label) },
                    onClick = {
                        expanded = false
                        onIntent(QuickDictionaryManagerIntent.SelectScopeKey(key))
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickDictionaryRow(
    row: QuickDictionaryRowUi,
    onIntent: (QuickDictionaryManagerIntent) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .combinedClickable(
                onClick = { onIntent(QuickDictionaryManagerIntent.Edit(row)) },
                onLongClick = { onIntent(QuickDictionaryManagerIntent.Edit(row)) },
            ),
        shape = RoundedCornerShape(8.dp),
        color = LegadoTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            headlineContent = { AppText(row.raw, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                val value = row.target.ifBlank { row.hanViet }
                if (value.isNotBlank()) AppText(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = {
                Row {
                    IconButton(onClick = { onIntent(QuickDictionaryManagerIntent.Edit(row)) }) {
                        androidx.compose.material3.Icon(Icons.Default.Edit, stringResource(R.string.edit))
                    }
                    if (!row.bundled && row.id != null) {
                        IconButton(onClick = { onIntent(QuickDictionaryManagerIntent.Delete(row.id)) }) {
                            androidx.compose.material3.Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun DictionaryEditorSheet(
    state: QuickDictionaryManagerUiState,
    onIntent: (QuickDictionaryManagerIntent) -> Unit,
) {
    val editor = state.editor
    AppModalBottomSheet(
        show = editor != null,
        onDismissRequest = { onIntent(QuickDictionaryManagerIntent.CloseEditor) },
        title = stringResource(if (editor?.id == null) R.string.quick_dictionary_add else R.string.edit),
    ) {
        if (editor != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppTextField(
                    value = editor.raw,
                    onValueChange = { onIntent(QuickDictionaryManagerIntent.UpdateRaw(it)) },
                    label = stringResource(R.string.quick_dictionary_raw),
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = editor.hanViet,
                    onValueChange = { onIntent(QuickDictionaryManagerIntent.UpdateHanViet(it)) },
                    label = stringResource(R.string.quick_dictionary_han_viet),
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = editor.target,
                    onValueChange = { onIntent(QuickDictionaryManagerIntent.UpdateTarget(it)) },
                    label = stringResource(R.string.quick_dictionary_target),
                    modifier = Modifier.fillMaxWidth(),
                )
                AppText(stringResource(R.string.quick_dictionary_type))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickDictionaryType.entries.forEach { type ->
                        FilterChip(
                            selected = editor.type == type,
                            onClick = { onIntent(QuickDictionaryManagerIntent.UpdateEditorType(type)) },
                            label = { AppText(stringResource(type.labelRes())) },
                        )
                    }
                }
                AppText(stringResource(R.string.quick_dictionary_scope))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDictionaryScope.entries.forEach { scope ->
                        FilterChip(
                            selected = editor.scope == scope,
                            onClick = { onIntent(QuickDictionaryManagerIntent.UpdateEditorScope(scope)) },
                            label = { AppText(stringResource(scope.labelRes())) },
                        )
                    }
                }
                EditorScopeKeyMenu(state = state, onIntent = onIntent)
                editor.errorRes?.let { AppText(stringResource(it)) }
                Button(
                    onClick = { onIntent(QuickDictionaryManagerIntent.Save) },
                    enabled = !editor.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText(stringResource(if (editor.saving) R.string.quick_dictionary_saving else R.string.quick_dictionary_save))
                }
            }
        }
    }
}

@Composable
private fun EditorScopeKeyMenu(
    state: QuickDictionaryManagerUiState,
    onIntent: (QuickDictionaryManagerIntent) -> Unit,
) {
    val editor = state.editor ?: return
    if (editor.scope == QuickDictionaryScope.GLOBAL) return
    var expanded by remember(editor.scope) { mutableStateOf(false) }
    val options = if (editor.scope == QuickDictionaryScope.PROJECT) {
        state.projects.map { it.key to listOf(it.name, it.author).filter(String::isNotBlank).joinToString(" — ") }
    } else {
        state.universes.map { it.key to it.name }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            AppText(
                options.firstOrNull { it.first == editor.scopeKey }?.second
                    ?: stringResource(R.string.quick_dictionary_select_scope),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { AppText(label) },
                    onClick = {
                        expanded = false
                        onIntent(QuickDictionaryManagerIntent.UpdateEditorScopeKey(key))
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectionDialog(
    state: QuickDictionaryManagerUiState,
    onIntent: (QuickDictionaryManagerIntent) -> Unit,
) {
    val text = state.selectionText ?: return
    AlertDialog(
        onDismissRequest = { onIntent(QuickDictionaryManagerIntent.CloseSelection) },
        title = { AppText(stringResource(R.string.quick_dictionary_select_phrase)) },
        text = {
            OutlinedTextField(
                value = TextFieldValue(
                    text = text,
                    selection = TextRange(state.selectionStart, state.selectionEnd),
                ),
                onValueChange = {
                    onIntent(
                        QuickDictionaryManagerIntent.UpdateSelection(
                            it.selection.start,
                            it.selection.end,
                        )
                    )
                },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 360.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onIntent(QuickDictionaryManagerIntent.AddSelection) }) {
                AppText(stringResource(R.string.quick_dictionary_add_selection))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(QuickDictionaryManagerIntent.CloseSelection) }) {
                AppText(stringResource(R.string.cancel))
            }
        },
    )
}

private fun QuickDictionaryType.labelRes(): Int = when (this) {
    QuickDictionaryType.NAME -> R.string.quick_dictionary_type_name
    QuickDictionaryType.VIETPHRASE -> R.string.quick_dictionary_type_vietphrase
    QuickDictionaryType.PHONETIC -> R.string.quick_dictionary_type_phonetic
    QuickDictionaryType.PRONOUN -> R.string.quick_dictionary_type_pronoun
    QuickDictionaryType.LUAT_NHAN -> R.string.quick_dictionary_type_luat_nhan
    QuickDictionaryType.IGNORE -> R.string.quick_dictionary_type_ignore
    QuickDictionaryType.TERM -> R.string.quick_dictionary_type_term
}

private fun QuickDictionaryScope.labelRes(): Int = when (this) {
    QuickDictionaryScope.GLOBAL -> R.string.quick_dictionary_scope_global
    QuickDictionaryScope.UNIVERSE -> R.string.quick_dictionary_scope_universe
    QuickDictionaryScope.PROJECT -> R.string.quick_dictionary_scope_project
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun cleanOrphanedDictionaryStaging(cacheDir: File) {
    runCatching {
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("quick-dictionary-import-") && file.name.endsWith(".source")) {
                file.delete()
            }
        }
    }
}
