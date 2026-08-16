package io.legado.app.ui.translation.memory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
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
                if (state.pendingChapterCount > 0) {
                    TextButton(onClick = { onIntent(BookStoryMemoryIntent.RetryPending) }) {
                        Text("Ghi lại memory đang chờ")
                    }
                }
                TextButton(onClick = { onIntent(BookStoryMemoryIntent.BackfillCachedChapters) }) {
                    Text("Phân tích các chương đã lưu")
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
            onDismiss = { onIntent(BookStoryMemoryIntent.DismissEditor) },
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
                EditorField(draft.primary, { onChange(draft.copy(primary = it)) },
                    if (draft.kind == AiTranslationStoryMemoryKind.TIMELINE) {
                        stringResource(R.string.story_memory_chapter_title)
                    } else stringResource(R.string.story_memory_raw))
                if (draft.kind != AiTranslationStoryMemoryKind.TIMELINE) {
                    EditorField(draft.secondary, { onChange(draft.copy(secondary = it)) },
                        stringResource(R.string.story_memory_target))
                    EditorField(draft.type, { onChange(draft.copy(type = it)) },
                        stringResource(R.string.story_memory_type))
                }
                EditorField(
                    draft.description,
                    { onChange(draft.copy(description = it)) },
                    stringResource(R.string.story_memory_description),
                    singleLine = false,
                )
                EditorField(
                    draft.chapterIndexText,
                    { onChange(draft.copy(chapterIndexText = it)) },
                    stringResource(R.string.story_memory_chapter_index),
                    keyboardType = KeyboardType.Number,
                )
                when (draft.kind) {
                    AiTranslationStoryMemoryKind.ENTITY -> {
                        EditorField(draft.aliasesOrRefsText,
                            { onChange(draft.copy(aliasesOrRefsText = it)) },
                            stringResource(R.string.story_memory_aliases_refs), false)
                        EditorField(draft.gender, { onChange(draft.copy(gender = it)) },
                            stringResource(R.string.story_memory_gender))
                        EditorField(draft.rank, { onChange(draft.copy(rank = it)) },
                            stringResource(R.string.story_memory_rank))
                    }
                    AiTranslationStoryMemoryKind.WORLD_BUILDING -> EditorField(
                        draft.aliasesOrRefsText,
                        { onChange(draft.copy(aliasesOrRefsText = it)) },
                        stringResource(R.string.story_memory_aliases_refs),
                        false,
                    )
                    AiTranslationStoryMemoryKind.TIMELINE -> {
                        EditorField(draft.eventsText, { onChange(draft.copy(eventsText = it)) },
                            stringResource(R.string.story_memory_events), false)
                        EditorField(draft.charactersText,
                            { onChange(draft.copy(charactersText = it)) },
                            stringResource(R.string.story_memory_characters_hint), false)
                        EditorField(draft.discoveriesText,
                            { onChange(draft.copy(discoveriesText = it)) },
                            stringResource(R.string.story_memory_discoveries_hint), false)
                    }
                    AiTranslationStoryMemoryKind.RELATIONSHIP -> Unit
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
