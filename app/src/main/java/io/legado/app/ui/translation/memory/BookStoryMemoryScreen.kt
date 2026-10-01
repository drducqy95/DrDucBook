package io.legado.app.ui.translation.memory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import coil.compose.AsyncImage
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.CharacterProfileDetails
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.utils.GSON
import io.legado.app.ui.translation.TranslationCaseControls
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

@Composable
fun BookStoryMemoryRouteScreen(
    bookUrl: String,
    onBack: () -> Unit,
    viewModel: BookStoryMemoryViewModel = koinViewModel(
        key = bookUrl,
        parameters = { parametersOf(bookUrl) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(content)
                } ?: error("Cannot open export document")
            }.onSuccess {
                context.toastOnUi(R.string.story_memory_exported)
            }.onFailure { context.toastOnUi(it.localizedMessage) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot read import document")
            }.onSuccess { content ->
                viewModel.onIntent(BookStoryMemoryIntent.ImportJson(content))
            }.onFailure { context.toastOnUi(it.localizedMessage) }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                BookStoryMemoryEffect.OpenImportDocument ->
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                is BookStoryMemoryEffect.ExportDocument -> {
                    pendingExport = effect.content
                    exportLauncher.launch(effect.suggestedName)
                }
                is BookStoryMemoryEffect.ShowMessage -> context.toastOnUi(effect.messageRes)
                is BookStoryMemoryEffect.ShowMessageText -> context.toastOnUi(effect.message)
                is BookStoryMemoryEffect.ShowError -> context.toastOnUi(effect.message)
            }
        }
    }
    BookStoryMemoryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookStoryMemoryScreen(
    state: BookStoryMemoryUiState,
    onIntent: (BookStoryMemoryIntent) -> Unit,
    onBack: () -> Unit,
) {
    AppScaffold(
        topBar = {
            GlassTopAppBar(
                title = stringResource(R.string.story_memory_title),
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.Public,
                        contentDescription = stringResource(R.string.story_memory_generate_map),
                        onClick = { onIntent(BookStoryMemoryIntent.GenerateWorldMap) },
                    )
                    TopBarActionButton(
                        imageVector = Icons.Default.Upload,
                        contentDescription = stringResource(R.string.story_memory_import),
                        onClick = { onIntent(BookStoryMemoryIntent.RequestImport) },
                    )
                    TopBarActionButton(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(R.string.story_memory_export),
                        onClick = { onIntent(BookStoryMemoryIntent.RequestExport) },
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    onIntent(
                        BookStoryMemoryIntent.Add(
                            state.selectedKind ?: AiTranslationStoryMemoryKind.ENTITY
                        )
                    )
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.story_memory_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "AI memory: ${state.counts.values.sum()} bản ghi · " +
                        "${state.analyzedChapterCount} chương phân tích" +
                    (if (state.pendingChapterCount > 0) {
                            " · ${state.pendingChapterCount} chương chờ ghi lại"
                        } else ""),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BookMemoryKindChip(null, state.selectedKind, onIntent)
                    AiTranslationStoryMemoryKind.entries.forEach { kind ->
                        BookMemoryKindChip(kind, state.selectedKind, onIntent)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onIntent(BookStoryMemoryIntent.OpenAiBuilderDialog) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.story_memory_btn_builder),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(
                        onClick = { onIntent(BookStoryMemoryIntent.OpenRetrofitDialog) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.story_memory_btn_retrofit),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (state.pendingChapterCount > 0) {
                    TextButton(onClick = { onIntent(BookStoryMemoryIntent.RetryPending) }) {
                        Text("Ghi lại ${state.pendingChapterCount} memory đang chờ")
                    }
                }
            }
            when {
                state.loading -> item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { CircularProgressIndicator() }
                }
                state.errorMessage != null -> item { Text(state.errorMessage) }
                state.items.isEmpty() -> item {
                    Text(stringResource(R.string.story_memory_empty), modifier = Modifier.padding(16.dp))
                }
                else -> items(state.items, key = StoryMemoryItemUi::id) { item ->
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onIntent(BookStoryMemoryIntent.Edit(item))
                        },
                        headlineContent = {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    item.chapterIndex?.let {
                                        stringResource(R.string.story_memory_chapter, it + 1)
                                    },
                                    item.subtitle,
                                ).joinToString(" · "),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = item.imagePath?.let { imagePath ->
                            {
                                AsyncImage(
                                    model = File(imagePath),
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
    state.editor?.let { draft ->
        StoryMemoryEditorDialog(
            draft = draft,
            saving = state.saving,
            onChange = { onIntent(BookStoryMemoryIntent.UpdateEditor(it)) },
            onSave = { onIntent(BookStoryMemoryIntent.SaveEditor) },
            onDelete = { onIntent(BookStoryMemoryIntent.DeleteEditor) },
            onGenerateImage = { onIntent(BookStoryMemoryIntent.GenerateEditorImage) },
            onRequestSuggestion = { provider -> onIntent(BookStoryMemoryIntent.RequestSuggestion(provider)) },
            onApplySuggestion = { value -> onIntent(BookStoryMemoryIntent.ApplySuggestion(value)) },
            onDismiss = { onIntent(BookStoryMemoryIntent.DismissEditor) },
        )
    }
    if (state.builderDialog.isOpen) {
        AiStoryMemoryBuilderDialog(
            state = state.builderDialog,
            onUpdate = { onIntent(BookStoryMemoryIntent.UpdateAiBuilderDialog(it)) },
            onExecute = { onIntent(BookStoryMemoryIntent.ExecuteAiBuilder) },
            onDismiss = { onIntent(BookStoryMemoryIntent.DismissAiBuilderDialog) },
        )
    }
    if (state.retrofitDialog.isOpen) {
        RetrofitCacheDialog(
            state = state.retrofitDialog,
            onUpdate = { onIntent(BookStoryMemoryIntent.UpdateRetrofitDialog(it)) },
            onExecute = { onIntent(BookStoryMemoryIntent.ExecuteRetrofit) },
            onDismiss = { onIntent(BookStoryMemoryIntent.DismissRetrofitDialog) },
        )
    }
}

@Composable
private fun StoryMemoryEditorDialog(
    draft: StoryMemoryEditorDraft,
    saving: Boolean,
    onChange: (StoryMemoryEditorDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onGenerateImage: () -> Unit,
    onRequestSuggestion: (String) -> Unit,
    onApplySuggestion: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.kind.label()) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draft.imagePath.takeIf(String::isNotBlank)?.let { imagePath ->
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = draft.secondary.ifBlank { draft.primary },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 260.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                when (draft.kind) {
                    AiTranslationStoryMemoryKind.RELATIONSHIP -> {
                        EditorField(
                            value = draft.primary,
                            onValueChange = { onChange(draft.copy(primary = it)) },
                            label = stringResource(R.string.story_memory_rel_entity_source),
                        )
                        EditorField(
                            value = draft.secondary,
                            onValueChange = { onChange(draft.copy(secondary = it)) },
                            label = stringResource(R.string.story_memory_rel_entity_target),
                        )
                        EditorField(
                            value = draft.type,
                            onValueChange = { onChange(draft.copy(type = it)) },
                            label = stringResource(R.string.story_memory_rel_type),
                        )
                        val relationshipPresets = listOf(
                            "Sư đồ", "Huynh đệ", "Tỷ muội", "Đạo lữ", "Đồng minh", "Kẻ thù", "Chủ tớ", "Môn đồ", "Bằng hữu"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            relationshipPresets.forEach { preset ->
                                FilterChip(
                                    selected = draft.type == preset,
                                    onClick = { onChange(draft.copy(type = preset)) },
                                    label = { Text(preset) },
                                )
                            }
                        }
                        EditorField(
                            value = draft.description,
                            onValueChange = { onChange(draft.copy(description = it)) },
                            label = stringResource(R.string.story_memory_rel_description),
                            singleLine = false,
                        )
                        EditorField(
                            value = draft.chapterIndexText,
                            onValueChange = { onChange(draft.copy(chapterIndexText = it)) },
                            label = stringResource(R.string.story_memory_chapter_index),
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    AiTranslationStoryMemoryKind.TIMELINE -> {
                        EditorField(
                            value = draft.primary,
                            onValueChange = { onChange(draft.copy(primary = it)) },
                            label = stringResource(R.string.story_memory_chapter_title),
                        )
                        EditorField(
                            value = draft.description,
                            onValueChange = { onChange(draft.copy(description = it)) },
                            label = stringResource(R.string.story_memory_description),
                            singleLine = false,
                        )
                        EditorField(
                            value = draft.chapterIndexText,
                            onValueChange = { onChange(draft.copy(chapterIndexText = it)) },
                            label = stringResource(R.string.story_memory_chapter_index),
                            keyboardType = KeyboardType.Number,
                        )
                        EditorField(
                            value = draft.eventsText,
                            onValueChange = { onChange(draft.copy(eventsText = it)) },
                            label = stringResource(R.string.story_memory_events),
                            singleLine = false,
                        )
                        EditorField(
                            value = draft.charactersText,
                            onValueChange = { onChange(draft.copy(charactersText = it)) },
                            label = stringResource(R.string.story_memory_characters_hint),
                            singleLine = false,
                        )
                        EditorField(
                            value = draft.discoveriesText,
                            onValueChange = { onChange(draft.copy(discoveriesText = it)) },
                            label = stringResource(R.string.story_memory_discoveries_hint),
                            singleLine = false,
                        )
                    }
                    AiTranslationStoryMemoryKind.ENTITY,
                    AiTranslationStoryMemoryKind.WORLD_BUILDING -> {
                        EditorField(
                            value = draft.primary,
                            onValueChange = { onChange(draft.copy(primary = it)) },
                            label = stringResource(R.string.story_memory_raw),
                        )
                        EditorField(
                            value = draft.secondary,
                            onValueChange = { onChange(draft.copy(secondary = it)) },
                            label = stringResource(R.string.story_memory_target),
                        )
                        EditorField(
                            value = draft.senseKey,
                            onValueChange = { onChange(draft.copy(senseKey = it)) },
                            label = stringResource(R.string.story_memory_sense_key),
                        )
                        TranslationCaseControls(
                            value = draft.secondary,
                            onValueChange = { onChange(draft.copy(secondary = it)) },
                        )
                        Text(
                            text = stringResource(R.string.quick_dictionary_translation_provider),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TranslationConstants.providerValues
                                .zip(TranslationConstants.providerDisplayNames)
                                .forEach { (provider, label) ->
                                    FilterChip(
                                        selected = draft.selectedProvider == provider,
                                        enabled = !draft.isSuggesting,
                                        onClick = { onRequestSuggestion(provider) },
                                        label = { Text(label) },
                                    )
                                }
                        }
                        if (draft.isSuggesting) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        if (draft.suggestions.isNotEmpty()) {
                            Text(stringResource(R.string.quick_dictionary_suggestions))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                draft.suggestions.forEach { suggestion ->
                                    TextButton(
                                        onClick = { onApplySuggestion(suggestion.text) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("${suggestion.providerLabel}: ${suggestion.text}")
                                    }
                                }
                            }
                        }
                        EditorField(
                            value = draft.type,
                            onValueChange = { onChange(draft.copy(type = it)) },
                            label = stringResource(R.string.story_memory_type),
                        )
                        EditorField(
                            value = draft.description,
                            onValueChange = { onChange(draft.copy(description = it)) },
                            label = stringResource(R.string.story_memory_description),
                            singleLine = false,
                        )
                        EditorField(
                            value = draft.chapterIndexText,
                            onValueChange = { onChange(draft.copy(chapterIndexText = it)) },
                            label = stringResource(R.string.story_memory_chapter_index),
                            keyboardType = KeyboardType.Number,
                        )
                        if (draft.kind == AiTranslationStoryMemoryKind.ENTITY) {
                            EditorField(
                                value = draft.aliasesOrRefsText,
                                onValueChange = { onChange(draft.copy(aliasesOrRefsText = it)) },
                                label = stringResource(R.string.story_memory_aliases_refs),
                                singleLine = false,
                            )
                            EditorField(
                                value = draft.gender,
                                onValueChange = { onChange(draft.copy(gender = it)) },
                                label = stringResource(R.string.story_memory_gender),
                            )
                            EditorField(
                                value = draft.rank,
                                onValueChange = { onChange(draft.copy(rank = it)) },
                                label = stringResource(R.string.story_memory_rank),
                            )

                            var showProfileFields by remember { mutableStateOf(false) }
                            val profile = remember(draft.metadata) {
                                draft.metadata.takeIf { it.startsWith("{") }?.let {
                                    try { GSON.fromJson(it, CharacterProfileDetails::class.java) } catch (_: Throwable) { null }
                                } ?: CharacterProfileDetails()
                            }

                            TextButton(
                                onClick = { showProfileFields = !showProfileFields },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (showProfileFields) {
                                        "▲ " + stringResource(R.string.character_dossier_profile_section)
                                    } else {
                                        "▼ " + stringResource(R.string.character_dossier_profile_section)
                                    }
                                )
                            }

                            if (showProfileFields) {
                                EditorField(
                                    value = profile.realm,
                                    onValueChange = {
                                        val updated = profile.copy(realm = it)
                                        onChange(draft.copy(metadata = GSON.toJson(updated)))
                                    },
                                    label = stringResource(R.string.character_dossier_realm),
                                )
                                EditorField(
                                    value = profile.sect,
                                    onValueChange = {
                                        val updated = profile.copy(sect = it)
                                        onChange(draft.copy(metadata = GSON.toJson(updated)))
                                    },
                                    label = stringResource(R.string.character_dossier_sect),
                                )
                                EditorField(
                                    value = profile.titles.joinToString(", "),
                                    onValueChange = {
                                        val updated = profile.copy(titles = it.split(",").map(String::trim).filter(String::isNotBlank))
                                        onChange(draft.copy(metadata = GSON.toJson(updated)))
                                    },
                                    label = stringResource(R.string.character_dossier_titles),
                                )
                                EditorField(
                                    value = profile.artifacts.joinToString(", "),
                                    onValueChange = {
                                        val updated = profile.copy(artifacts = it.split(",").map(String::trim).filter(String::isNotBlank))
                                        onChange(draft.copy(metadata = GSON.toJson(updated)))
                                    },
                                    label = stringResource(R.string.character_dossier_artifacts),
                                )
                                EditorField(
                                    value = profile.techniques.joinToString(", "),
                                    onValueChange = {
                                        val updated = profile.copy(techniques = it.split(",").map(String::trim).filter(String::isNotBlank))
                                        onChange(draft.copy(metadata = GSON.toJson(updated)))
                                    },
                                    label = stringResource(R.string.character_dossier_techniques),
                                )
                                EditorField(
                                    value = profile.aptitude,
                                    onValueChange = {
                                        val updated = profile.copy(aptitude = it)
                                        onChange(draft.copy(metadata = GSON.toJson(updated)))
                                    },
                                    label = stringResource(R.string.character_dossier_aptitude),
                                )
                                EditorField(
                                    value = profile.personality,
                                    onValueChange = {
                                        val updated = profile.copy(personality = it)
                                        onChange(draft.copy(metadata = GSON.toJson(updated)))
                                    },
                                    label = stringResource(R.string.character_dossier_personality),
                                )
                                EditorField(
                                    value = profile.appearance,
                                    onValueChange = {
                                        val updated = profile.copy(appearance = it)
                                        onChange(draft.copy(metadata = GSON.toJson(updated)))
                                    },
                                    label = stringResource(R.string.character_dossier_appearance),
                                )
                            }
                        } else {
                            EditorField(
                                value = draft.aliasesOrRefsText,
                                onValueChange = { onChange(draft.copy(aliasesOrRefsText = it)) },
                                label = stringResource(R.string.story_memory_aliases_refs),
                                singleLine = false,
                            )
                        }
                    }
                }
                if (
                    draft.originalId != null &&
                    draft.kind in setOf(
                        AiTranslationStoryMemoryKind.ENTITY,
                        AiTranslationStoryMemoryKind.WORLD_BUILDING,
                    )
                ) {
                    TextButton(
                        onClick = onGenerateImage,
                        enabled = !saving,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.story_memory_generate_image))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !saving) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            if (draft.originalId != null) {
                TextButton(onClick = onDelete, enabled = !saving) {
                    Text(stringResource(R.string.delete))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun BookMemoryKindChip(
    kind: AiTranslationStoryMemoryKind?,
    selectedKind: AiTranslationStoryMemoryKind?,
    onIntent: (BookStoryMemoryIntent) -> Unit,
) {
    FilterChip(
        selected = selectedKind == kind,
        onClick = { onIntent(BookStoryMemoryIntent.SelectKind(kind)) },
        label = { Text(kind.label()) },
    )
}

@Composable
private fun AiTranslationStoryMemoryKind?.label(): String = stringResource(
    when (this) {
        null -> R.string.all
        AiTranslationStoryMemoryKind.ENTITY -> R.string.story_memory_entities
        AiTranslationStoryMemoryKind.RELATIONSHIP -> R.string.story_memory_relationships
        AiTranslationStoryMemoryKind.WORLD_BUILDING -> R.string.story_memory_world_building
        AiTranslationStoryMemoryKind.TIMELINE -> R.string.story_memory_timeline
    }
)

@Composable
private fun AiStoryMemoryBuilderDialog(
    state: AiStoryMemoryBuilderDialogState,
    onUpdate: (AiStoryMemoryBuilderDialogState) -> Unit,
    onExecute: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.story_memory_ai_builder_title))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.story_memory_ai_builder_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.mode == AiStoryMemoryBuilderDialogState.MODE_CHAPTERS,
                        onClick = { onUpdate(state.copy(mode = AiStoryMemoryBuilderDialogState.MODE_CHAPTERS)) },
                        label = { Text(stringResource(R.string.story_memory_builder_mode_chapters)) },
                        enabled = !state.isRunning,
                    )
                    FilterChip(
                        selected = state.mode == AiStoryMemoryBuilderDialogState.MODE_CUSTOM_TEXT,
                        onClick = { onUpdate(state.copy(mode = AiStoryMemoryBuilderDialogState.MODE_CUSTOM_TEXT)) },
                        label = { Text(stringResource(R.string.story_memory_builder_mode_text)) },
                        enabled = !state.isRunning,
                    )
                }

                if (state.mode == AiStoryMemoryBuilderDialogState.MODE_CHAPTERS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.startChapter.toString(),
                            onValueChange = { str ->
                                str.toIntOrNull()?.let { onUpdate(state.copy(startChapter = it)) }
                            },
                            label = { Text(stringResource(R.string.story_memory_start_chapter)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            enabled = !state.isRunning,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.endChapter.toString(),
                            onValueChange = { str ->
                                str.toIntOrNull()?.let { onUpdate(state.copy(endChapter = it)) }
                            },
                            label = { Text(stringResource(R.string.story_memory_end_chapter)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            enabled = !state.isRunning,
                            singleLine = true,
                        )
                    }
                    if (state.maxChapters > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SuggestionChip(
                                onClick = { onUpdate(state.copy(startChapter = 0, endChapter = minOf(10, state.maxChapters))) },
                                label = { Text("10 chương đầu") },
                                enabled = !state.isRunning,
                            )
                            SuggestionChip(
                                onClick = { onUpdate(state.copy(startChapter = 0, endChapter = state.maxChapters)) },
                                label = { Text("Tất cả (${state.maxChapters} ch)") },
                                enabled = !state.isRunning,
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = state.directText,
                        onValueChange = { onUpdate(state.copy(directText = it)) },
                        placeholder = { Text(stringResource(R.string.story_memory_custom_text_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 200.dp),
                        enabled = !state.isRunning,
                        minLines = 4,
                        maxLines = 8,
                    )
                }

                if (state.isRunning) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                if (state.progressTotal > 0) {
                                    state.progressCurrent.toFloat() / state.progressTotal.toFloat()
                                } else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = state.progressMessage.ifBlank { stringResource(R.string.story_memory_builder_running) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExecute,
                enabled = !state.isRunning && (state.mode == AiStoryMemoryBuilderDialogState.MODE_CHAPTERS || state.directText.isNotBlank()),
            ) {
                Text(stringResource(R.string.story_memory_execute_builder))
            }
        },
        dismissButton = {
            if (!state.isRunning) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun RetrofitCacheDialog(
    state: RetrofitCacheDialogState,
    onUpdate: (RetrofitCacheDialogState) -> Unit,
    onExecute: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.story_memory_retrofit_title))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.story_memory_retrofit_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.startChapter.toString(),
                        onValueChange = { str ->
                            str.toIntOrNull()?.let { onUpdate(state.copy(startChapter = it)) }
                        },
                        label = { Text(stringResource(R.string.story_memory_start_chapter)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        enabled = !state.isRunning,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.endChapter.toString(),
                        onValueChange = { str ->
                            str.toIntOrNull()?.let { onUpdate(state.copy(endChapter = it)) }
                        },
                        label = { Text(stringResource(R.string.story_memory_end_chapter)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        enabled = !state.isRunning,
                        singleLine = true,
                    )
                }
                if (state.maxChapters > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SuggestionChip(
                            onClick = { onUpdate(state.copy(startChapter = 0, endChapter = minOf(10, state.maxChapters))) },
                            label = { Text("10 chương đầu") },
                            enabled = !state.isRunning,
                        )
                        SuggestionChip(
                            onClick = { onUpdate(state.copy(startChapter = 0, endChapter = state.maxChapters)) },
                            label = { Text("Tất cả (${state.maxChapters} ch)") },
                            enabled = !state.isRunning,
                        )
                    }
                }
                if (state.isRunning) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                if (state.progressTotal > 0) {
                                    state.progressCurrent.toFloat() / state.progressTotal.toFloat()
                                } else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = state.progressMessage.ifBlank { stringResource(R.string.story_memory_retrofit_running) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExecute,
                enabled = !state.isRunning && (state.endChapter > state.startChapter),
            ) {
                Text(stringResource(R.string.story_memory_execute_retrofit))
            }
        },
        dismissButton = {
            if (!state.isRunning) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
