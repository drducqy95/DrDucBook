package io.legado.app.ui.config.ai.prompt

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiPromptEditorRouteScreen(
    onBackClick: () -> Unit,
    viewModel: AiPromptEditorViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effects = viewModel.effects
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.readPromptTransfer(uri) }.getOrNull()
            }
            viewModel.onIntent(AiPromptEditorIntent.ImportJson(content.orEmpty()))
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri == null || content == null) {
            viewModel.onIntent(AiPromptEditorIntent.TransferCancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.writer(Charsets.UTF_8).use { it.write(content) }
                    } ?: error("Cannot open export destination")
                }.isSuccess
            }
            viewModel.onIntent(AiPromptEditorIntent.ExportFinished(succeeded))
        }
    }
    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                AiPromptEditorEffect.OpenImportFile -> {
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                }
                is AiPromptEditorEffect.CreateExportFile -> {
                    pendingExport = effect.content
                    exportLauncher.launch(effect.fileName)
                }
                is AiPromptEditorEffect.ShowMessage -> Unit
            }
        }
    }
    AiPromptEditorScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPromptEditorScreen(
    state: AiPromptEditorUiState,
    effects: Flow<AiPromptEditorEffect>,
    onIntent: (AiPromptEditorIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiPromptEditorEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                AiPromptEditorEffect.OpenImportFile,
                is AiPromptEditorEffect.CreateExportFile -> Unit
            }
        }
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.ai_prompt_editor_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(AiPromptEditorIntent.RequestImport) },
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = stringResource(R.string.ai_prompt_editor_import),
                    )
                    TopBarActionButton(
                        onClick = { onIntent(AiPromptEditorIntent.RequestExport) },
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = stringResource(R.string.ai_prompt_editor_export),
                    )
                },
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(AiPromptEditorIntent.AddPreset) },
                icon = Icons.Default.Add,
                tooltipText = stringResource(R.string.ai_prompt_editor_add),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "task_filter") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(R.string.ai_prompt_editor_task_filter))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.supportedTaskTypes.forEach { taskType ->
                            FilterChip(
                                selected = taskType == state.selectedTaskType,
                                onClick = {
                                    onIntent(AiPromptEditorIntent.SelectTask(taskType))
                                },
                                label = { AppText(taskTypeLabel(taskType)) },
                            )
                        }
                    }
                }
            }

            item(key = "catalog") {
                SplicedColumnGroup(title = stringResource(R.string.ai_prompt_editor_library)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_open_library),
                        description = stringResource(R.string.ai_prompt_editor_library_summary),
                        imageVector = Icons.Default.LibraryBooks,
                        onClick = { onIntent(AiPromptEditorIntent.OpenCatalog) },
                    )
                }
            }

            if (!state.loading && state.models.isEmpty()) {
                item(key = "missing_model") {
                    SplicedColumnGroup(title = stringResource(R.string.ai_prompt_editor_model)) {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_prompt_editor_no_model),
                            description = stringResource(R.string.ai_prompt_editor_no_model_summary),
                            onClick = onBackClick,
                        )
                    }
                }
            }

            if (!state.loading && state.presets.isEmpty()) {
                item(key = "empty") {
                    SplicedColumnGroup(title = taskTypeLabel(state.selectedTaskType)) {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_prompt_editor_empty),
                            description = stringResource(R.string.ai_prompt_editor_empty_summary),
                            onClick = { onIntent(AiPromptEditorIntent.OpenCatalog) },
                        )
                    }
                }
            } else {
                items(state.presets, key = AiPromptPresetItemUi::id) { item ->
                    SplicedColumnGroup(
                        title = if (item.isDefault) {
                            stringResource(R.string.ai_prompt_editor_default)
                        } else {
                            taskTypeLabel(item.taskType)
                        }
                    ) {
                        ClickableSettingItem(
                            title = item.name,
                            description = listOf(
                                item.description,
                                item.modelLabel,
                                item.routeLabel,
                            )
                                .filter(String::isNotBlank)
                                .joinToString("\n"),
                            option = if (item.enabled) {
                                stringResource(R.string.ai_prompt_editor_enabled)
                            } else {
                                stringResource(R.string.ai_prompt_editor_disabled)
                            },
                            trailingContent = if (item.isDefault) {
                                { TextCard(text = stringResource(R.string.ai_prompt_editor_active)) }
                            } else {
                                {
                                    OutlinedButton(
                                        onClick = {
                                            onIntent(AiPromptEditorIntent.SetDefault(item.id))
                                        }
                                    ) {
                                        AppText(stringResource(R.string.ai_prompt_editor_activate))
                                    }
                                }
                            },
                            onClick = { onIntent(AiPromptEditorIntent.EditPreset(item)) },
                        )
                    }
                }
            }
        }
    }

    PromptCatalogSheet(
        state = state,
        onIntent = onIntent,
    )
    PromptPresetEditorSheet(
        state = state,
        onIntent = onIntent,
    )
    PromptEditorDialogs(
        state = state,
        onIntent = onIntent,
    )
}

@Composable
private fun PromptCatalogSheet(
    state: AiPromptEditorUiState,
    onIntent: (AiPromptEditorIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = state.showCatalog,
        onDismissRequest = { onIntent(AiPromptEditorIntent.CloseCatalog) },
        title = stringResource(R.string.ai_prompt_editor_library),
    ) {
        val templates = state.catalog.filter {
            it.taskType == state.selectedTaskType
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
        ) {
            if (templates.isEmpty()) {
                item {
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_library_empty),
                        onClick = { onIntent(AiPromptEditorIntent.CloseCatalog) },
                    )
                }
            } else {
                items(templates, key = AiPromptCatalogItemUi::id) { template ->
                    ClickableSettingItem(
                        title = template.name,
                        description = template.description,
                        onClick = {
                            onIntent(AiPromptEditorIntent.ApplyCatalog(template))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptPresetEditorSheet(
    state: AiPromptEditorUiState,
    onIntent: (AiPromptEditorIntent) -> Unit,
) {
    val editor = state.editor
    AppModalBottomSheet(
        show = editor != null,
        onDismissRequest = { onIntent(AiPromptEditorIntent.CloseEditor) },
        title = stringResource(
            if (editor?.presetId == null) {
                R.string.ai_prompt_editor_add
            } else {
                R.string.ai_prompt_editor_edit
            }
        ),
        animateContentSize = false,
    ) {
        if (editor == null) return@AppModalBottomSheet
        val taskEntries = state.supportedTaskTypes.map { taskType ->
            taskTypeLabel(taskType)
        }.toTypedArray()
        val taskValues = state.supportedTaskTypes.toTypedArray()
        val reasonEntries = AiReasoningLevel.entries
            .map { reasoningLabel(it) }
            .toTypedArray()
        val reasonValues = AiReasoningLevel.entries.map(AiReasoningLevel::effort).toTypedArray()
        val routeOptions = state.routes.filter { it.taskType == editor.taskType }
        val modelOptions = state.models
        val selectedRoute = routeOptions.firstOrNull { it.id == editor.routeProfileId }
        val selectedModel = modelOptions.firstOrNull { it.id == editor.modelProfileId }
        val selectionEntries = routeOptions.map {
            stringResource(R.string.ai_prompt_editor_combo_entry, it.displayLabel)
        } + modelOptions.map {
            stringResource(R.string.ai_prompt_editor_model_entry, it.displayLabel)
        }
        val selectionValues = routeOptions.map { AI_PROMPT_SELECTION_ROUTE_PREFIX + it.id } +
            modelOptions.map { AI_PROMPT_SELECTION_MODEL_PREFIX + it.id }
        val selectedSelection = when {
            editor.routeProfileId.isNotBlank() -> AI_PROMPT_SELECTION_ROUTE_PREFIX + editor.routeProfileId
            editor.modelProfileId.isNotBlank() -> AI_PROMPT_SELECTION_MODEL_PREFIX + editor.modelProfileId
            else -> AI_PROMPT_SELECTION_DEFAULT
        }
        val currentItem = editor.presetId?.let { id ->
            state.presets.firstOrNull { it.id == id }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DropdownListSettingItem(
                    title = stringResource(R.string.ai_prompt_editor_task),
                    selectedValue = editor.taskType,
                    displayEntries = taskEntries,
                    entryValues = taskValues,
                    onValueChange = { onIntent(AiPromptEditorIntent.UpdateTask(it)) },
                )
                if (selectionEntries.isNotEmpty()) {
                    DropdownListSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_model_or_combo),
                        selectedValue = selectedSelection,
                        displayEntries = selectionEntries.toTypedArray(),
                        entryValues = selectionValues.toTypedArray(),
                        description = selectedRoute?.let { route ->
                            stringResource(
                                R.string.ai_prompt_editor_fallback_combo_summary,
                                route.targetCount,
                                route.maxAttempts,
                            )
                        } ?: selectedModel?.displayLabel
                            ?: stringResource(R.string.ai_prompt_editor_combo_required),
                        onValueChange = { onIntent(AiPromptEditorIntent.SelectModelOrRoute(it)) },
                    )
                } else {
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_model_or_combo),
                        description = stringResource(R.string.ai_prompt_editor_combo_required),
                        onClick = {},
                    )
                }
            }
            item {
                AppTextField(
                    value = editor.name,
                    onValueChange = {
                        onIntent(AiPromptEditorIntent.UpdateName(it))
                    },
                    label = stringResource(R.string.ai_prompt_editor_name),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                AppTextField(
                    value = editor.description,
                    onValueChange = {
                        onIntent(AiPromptEditorIntent.UpdateDescription(it))
                    },
                    label = stringResource(R.string.ai_prompt_editor_description),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                )
            }
            item {
                AppTextField(
                    value = editor.promptTemplate,
                    onValueChange = {
                        onIntent(AiPromptEditorIntent.UpdatePrompt(it))
                    },
                    label = stringResource(R.string.ai_prompt_editor_system_prompt),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 420.dp),
                    singleLine = false,
                    maxLines = 20,
                )
                AppText(
                    stringResource(
                        R.string.ai_prompt_editor_prompt_metrics,
                        editor.promptTemplate.length,
                        estimatePromptTokens(editor.promptTemplate),
                    )
                )
            }
            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_prompt_editor_generation)) {
                    InputSettingItem(
                        title = stringResource(R.string.ai_temperature),
                        value = editor.temperature,
                        description = stringResource(R.string.ai_prompt_editor_temperature_summary),
                        onConfirm = {
                            onIntent(AiPromptEditorIntent.UpdateTemperature(it))
                        },
                    )
                    InputSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_top_p),
                        value = editor.topP,
                        description = stringResource(R.string.ai_prompt_editor_top_p_summary),
                        onConfirm = { onIntent(AiPromptEditorIntent.UpdateTopP(it)) },
                    )
                    InputSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_top_k),
                        value = editor.topK,
                        description = stringResource(R.string.ai_prompt_editor_top_k_summary),
                        onConfirm = { onIntent(AiPromptEditorIntent.UpdateTopK(it)) },
                    )
                    InputSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_repetition_penalty),
                        value = editor.repetitionPenalty,
                        description = stringResource(
                            R.string.ai_prompt_editor_repetition_penalty_summary
                        ),
                        onConfirm = {
                            onIntent(AiPromptEditorIntent.UpdateRepetitionPenalty(it))
                        },
                    )
                    InputSettingItem(
                        title = stringResource(R.string.ai_max_output_tokens),
                        value = editor.maxOutputTokens,
                        description = stringResource(R.string.ai_prompt_editor_optional),
                        onConfirm = {
                            onIntent(AiPromptEditorIntent.UpdateMaxOutputTokens(it))
                        },
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_reasoning),
                        selectedValue = editor.reasoningLevel.effort,
                        displayEntries = reasonEntries,
                        entryValues = reasonValues,
                        onValueChange = { effort ->
                            onIntent(
                                AiPromptEditorIntent.UpdateReasoning(
                                    AiReasoningLevel.fromEffort(effort)
                                )
                            )
                        },
                    )
                }
            }
            if (editor.taskType == AiTaskType.TRANSLATE_CHAPTER) {
                item {
                    val languages = TranslationConstants.targetLanguages
                    SplicedColumnGroup(
                        title = stringResource(R.string.ai_prompt_editor_translation_runtime)
                    ) {
                        DropdownListSettingItem(
                            title = stringResource(R.string.llm_target_language),
                            selectedValue = editor.targetLanguage,
                            displayEntries = languages.map { it.second }.toTypedArray(),
                            entryValues = languages.map { it.first }.toTypedArray(),
                            onValueChange = {
                                onIntent(AiPromptEditorIntent.UpdateTargetLanguage(it))
                            },
                        )
                        InputSettingItem(
                            title = stringResource(R.string.ai_prompt_editor_max_input),
                            value = editor.maxInputChars,
                            onConfirm = {
                                onIntent(AiPromptEditorIntent.UpdateMaxInputChars(it))
                            },
                        )
                        InputSettingItem(
                            title = stringResource(R.string.ai_prompt_editor_concurrency),
                            value = editor.concurrentRequests,
                            onConfirm = {
                                onIntent(AiPromptEditorIntent.UpdateConcurrentRequests(it))
                            },
                        )
                        InputSettingItem(
                            title = stringResource(R.string.ai_prompt_editor_retries),
                            value = editor.retryCount,
                            onConfirm = {
                                onIntent(AiPromptEditorIntent.UpdateRetryCount(it))
                            },
                        )
                    }
                }
            }
            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_prompt_editor_status)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_enabled),
                        checked = editor.enabled,
                        onCheckedChange = {
                            onIntent(AiPromptEditorIntent.UpdateEnabled(it))
                        },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.ai_prompt_editor_make_default),
                        description = stringResource(R.string.ai_prompt_editor_make_default_summary),
                        checked = editor.makeDefault,
                        onCheckedChange = {
                            onIntent(AiPromptEditorIntent.UpdateMakeDefault(it))
                        },
                    )
                }
            }
            editor.errorMessage?.let { message ->
                item {
                    AppText(message)
                }
            }
            item {
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onIntent(AiPromptEditorIntent.ResetEditor) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(R.string.ai_prompt_editor_reset))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                onIntent(AiPromptEditorIntent.PreviewEffectivePrompt)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(stringResource(R.string.ai_prompt_editor_preview))
                        }
                        Button(
                            onClick = { onIntent(AiPromptEditorIntent.SavePreset) },
                            enabled = !state.saving,
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(stringResource(R.string.action_save))
                        }
                    }
                    if (currentItem != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onIntent(AiPromptEditorIntent.DuplicateCurrentEditor)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(stringResource(R.string.ai_prompt_editor_duplicate))
                            }
                            OutlinedButton(
                                onClick = {
                                    onIntent(AiPromptEditorIntent.RequestDelete(currentItem))
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ContentResolver.readPromptTransfer(uri: Uri): String {
    return openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
        val output = StringBuilder()
        val buffer = CharArray(8_192)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            require(output.length + read <= MAX_PROMPT_TRANSFER_CHARS) {
                "Prompt transfer file is too large"
            }
            output.append(buffer, 0, read)
        }
        output.toString()
    } ?: error("Cannot open prompt transfer file")
}

private const val MAX_PROMPT_TRANSFER_CHARS = 2_000_000

@Composable
private fun PromptEditorDialogs(
    state: AiPromptEditorUiState,
    onIntent: (AiPromptEditorIntent) -> Unit,
) {
    val delete = state.activeDialog as? AiPromptEditorDialog.Delete
    AppAlertDialog(
        show = delete != null,
        onDismissRequest = { onIntent(AiPromptEditorIntent.CloseDialog) },
        title = stringResource(R.string.ai_prompt_editor_delete_title),
        text = delete?.item?.name.orEmpty(),
        confirmText = stringResource(R.string.delete),
        onConfirm = { onIntent(AiPromptEditorIntent.ConfirmDelete) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(AiPromptEditorIntent.CloseDialog) },
    )

    val preview = state.activeDialog as? AiPromptEditorDialog.Preview
    AppAlertDialog(
        show = preview != null,
        onDismissRequest = { onIntent(AiPromptEditorIntent.CloseDialog) },
        title = preview?.title ?: stringResource(R.string.ai_prompt_editor_preview),
        confirmText = stringResource(R.string.close),
        onConfirm = { onIntent(AiPromptEditorIntent.CloseDialog) },
        content = {
            AppText(
                text = preview?.content.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
    )

    AppAlertDialog(
        show = state.activeDialog == AiPromptEditorDialog.DiscardEditor,
        onDismissRequest = { onIntent(AiPromptEditorIntent.CloseDialog) },
        title = stringResource(R.string.ai_prompt_editor_discard_title),
        text = stringResource(R.string.ai_prompt_editor_discard_summary),
        confirmText = stringResource(R.string.ai_prompt_editor_discard),
        onConfirm = { onIntent(AiPromptEditorIntent.ConfirmDiscardEditor) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(AiPromptEditorIntent.CloseDialog) },
    )
}

private fun estimatePromptTokens(prompt: String): Int =
    if (prompt.isEmpty()) 0 else kotlin.math.ceil(prompt.length / 3.0).toInt()

private fun Int.displayTokenLimit(): String =
    if (this > 0) toString() else "—"

@Composable
private fun taskTypeLabel(taskType: String): String = stringResource(
    when (taskType) {
        AiTaskType.TRANSLATE_CHAPTER -> R.string.ai_prompt_task_translate
        AiTaskType.CHAT -> R.string.ai_prompt_task_chat
        AiTaskType.SUMMARIZE_CHAPTER -> R.string.ai_prompt_task_summary_chapter
        AiTaskType.SUMMARIZE_BOOK -> R.string.ai_prompt_task_summary_book
        AiTaskType.EXPLAIN_SELECTION -> R.string.ai_prompt_task_explain
        AiTaskType.CLEAN_SELECTION -> R.string.ai_prompt_task_clean
        AiTaskType.TEXT_FACTORY -> R.string.ai_prompt_task_text_factory
        AiTaskType.REWRITE_TEXT -> R.string.ai_prompt_task_rewrite
        AiTaskType.AUTHORING_DIRECTOR -> R.string.ai_prompt_task_authoring_director
        AiTaskType.AUTHORING_WRITER -> R.string.ai_prompt_task_authoring_writer
        AiTaskType.GENERATE_STORY_IMAGE -> R.string.ai_prompt_task_story_image
        else -> R.string.ai_prompt_task_other
    }
)

@Composable
private fun reasoningLabel(level: AiReasoningLevel): String = stringResource(
    when (level) {
        AiReasoningLevel.OFF -> R.string.ai_reasoning_off
        AiReasoningLevel.AUTO -> R.string.ai_reasoning_auto
        AiReasoningLevel.LOW -> R.string.ai_reasoning_low
        AiReasoningLevel.MEDIUM -> R.string.ai_reasoning_medium
        AiReasoningLevel.HIGH -> R.string.ai_reasoning_high
        AiReasoningLevel.XHIGH -> R.string.ai_reasoning_xhigh
    }
)

private val AiPromptModelOptionUi.displayLabel: String
    get() = "$providerName · $modelName ($modelId)"

private val AiPromptRouteOptionUi.displayLabel: String
    get() = if (targetCount > 0) "$name ($targetCount)" else name
