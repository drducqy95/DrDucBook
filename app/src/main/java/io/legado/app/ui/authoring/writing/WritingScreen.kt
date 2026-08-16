package io.legado.app.ui.authoring.writing

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.WritingWorkflowPolicy
import io.legado.app.ui.authoring.AuthoringProjectLibrary
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@Composable
fun WritingRouteScreen(
    onBack: () -> Unit,
    showNavigationIcon: Boolean = true,
    handleSystemBack: Boolean = true,
    viewModel: WritingViewModel = koinViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentViewModel by rememberUpdatedState(viewModel)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                currentViewModel.onIntent(WritingIntent.FlushAutosave)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    WritingScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBack = onBack,
        showNavigationIcon = showNavigationIcon,
        handleSystemBack = handleSystemBack,
    )
}

@Composable
fun WritingScreen(
    state: WritingUiState,
    onIntent: (WritingIntent) -> Unit,
    effects: Flow<WritingEffect>,
    onBack: () -> Unit,
    showNavigationIcon: Boolean = true,
    handleSystemBack: Boolean = true,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val project = state.project
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error(resources.getString(R.string.ebook_editor_image_read_error))
                    (uri.lastPathSegment ?: "image") to bytes
                }
            }.onSuccess { (name, bytes) ->
                onIntent(WritingIntent.ImagePicked(name, bytes))
            }.onFailure { error ->
                Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(effects, context, onBack) {
        effects.collectLatest { effect ->
            when (effect) {
                WritingEffect.NavigateBack -> onBack()
                WritingEffect.OpenImagePicker -> imagePicker.launch("image/*")
                is WritingEffect.ShowMessage -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    BackHandler(enabled = handleSystemBack) { onIntent(WritingIntent.BackPressed) }

    AppScaffold(
        appearanceTarget = AppearanceTarget.AUTHORING,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = project?.title ?: stringResource(R.string.writing_title),
                subtitle = project?.let {
                    stringResource(
                        if (state.isDirty) R.string.authoring_unsaved else R.string.authoring_saved
                    )
                },
                navigationIcon = if (showNavigationIcon) {
                    { TopBarNavigationButton(onClick = { onIntent(WritingIntent.BackPressed) }) }
                } else {
                    {}
                },
                actions = {
                    if (project == null) {
                        TopBarActionButton(
                            onClick = { onIntent(WritingIntent.ShowCreateProject) },
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.writing_create_project),
                        )
                    } else {
                        TopBarActionButton(
                            onClick = { onIntent(WritingIntent.DuplicateProject) },
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.authoring_duplicate_project),
                        )
                        TopBarActionButton(
                            onClick = { onIntent(WritingIntent.Save) },
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.save),
                        )
                        TopBarActionButton(
                            onClick = { onIntent(WritingIntent.RequestDeleteProject) },
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (project != null) {
                AuthoringActionBar(
                    isBusy = state.isLoading,
                    isDirty = state.isDirty,
                    autosaveState = state.autosaveState,
                    onSave = { onIntent(WritingIntent.Save) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading || state.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (project == null) {
                AuthoringProjectLibrary(
                    projects = state.projects,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = {
                        onIntent(WritingIntent.UpdateSearchQuery(it))
                    },
                    onOpenProject = { onIntent(WritingIntent.OpenProject(it)) },
                    onCreateProject = { onIntent(WritingIntent.ShowCreateProject) },
                    title = stringResource(R.string.writing_library_title),
                    description = stringResource(R.string.writing_library_description),
                    createLabel = stringResource(R.string.writing_create_project),
                    searchLabel = stringResource(R.string.authoring_search_projects),
                    emptyTitle = stringResource(R.string.writing_empty_title),
                    emptyDescription = stringResource(R.string.writing_empty_description),
                    projectCountLabel = {
                        stringResource(R.string.authoring_project_count, it)
                    },
                    chapterCountLabel = {
                        stringResource(R.string.authoring_chapter_count, it)
                    },
                    wordCountLabel = {
                        stringResource(R.string.authoring_word_count, it)
                    },
                    updatedLabel = {
                        stringResource(R.string.authoring_updated_at, it)
                    },
                    icon = Icons.Default.EditNote,
                )
            } else {
                WritingWorkspaceModeBar(state, onIntent)
                WritingWorkspace(state = state, onIntent = onIntent)
            }
        }
    }

    WritingDialogs(dialog = state.dialog, onIntent = onIntent)
}

@Composable
private fun WritingWorkspace(
    state: WritingUiState,
    onIntent: (WritingIntent) -> Unit,
) {
    val project = state.project ?: return
    if (state.workspaceMode in setOf(
            WritingWorkspaceMode.PREWRITING,
            WritingWorkspaceMode.OUTLINE,
        )
    ) {
        PreWritingWorkspace(state, onIntent, Modifier.fillMaxSize())
        return
    }
    if (state.workspaceMode == WritingWorkspaceMode.VALIDATE) {
        WritingValidationSummary(project, Modifier.fillMaxSize())
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ChapterSidebar(
                    project = project,
                    selectedChapterId = state.selectedChapterId,
                    onSelect = { onIntent(WritingIntent.SelectChapter(it)) },
                    onAdd = { onIntent(WritingIntent.AddChapter) },
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                )
                WritingEditor(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                ChapterStrip(
                    project = project,
                    selectedChapterId = state.selectedChapterId,
                    onSelect = { onIntent(WritingIntent.SelectChapter(it)) },
                    onAdd = { onIntent(WritingIntent.AddChapter) },
                )
                WritingEditor(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WritingWorkspaceModeBar(
    state: WritingUiState,
    onIntent: (WritingIntent) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(WritingWorkspaceMode.entries.filterNot { it == WritingWorkspaceMode.OUTLINE }) { mode ->
            FilterChip(
                selected = mode == state.workspaceMode,
                onClick = { onIntent(WritingIntent.SelectWorkspaceMode(mode)) },
                enabled = mode != WritingWorkspaceMode.MANUSCRIPT ||
                    state.project?.let { WritingWorkflowPolicy.canWriteManuscript(it) } == true,
                label = {
                    Text(
                        stringResource(
                            when (mode) {
                                WritingWorkspaceMode.PREWRITING -> R.string.writing_mode_workflow
                                WritingWorkspaceMode.OUTLINE -> R.string.writing_mode_outline
                                WritingWorkspaceMode.MANUSCRIPT -> R.string.writing_mode_manuscript
                                WritingWorkspaceMode.VALIDATE -> R.string.writing_mode_validate
                            }
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun WritingValidationSummary(project: AuthoringProject, modifier: Modifier = Modifier) {
    val emptyChapters = project.chapters.count { it.content.isBlank() }
    val duplicateTitles = project.chapters.groupingBy { it.title.trim() }.eachCount().count { it.value > 1 }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NormalCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.writing_validation_title),
                        style = LegadoTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.writing_validation_chapters, project.chapters.size))
                    Text(stringResource(R.string.writing_validation_empty, emptyChapters))
                    Text(stringResource(R.string.writing_validation_duplicates, duplicateTitles))
                }
            }
        }
    }
}

@Composable
private fun ChapterSidebar(
    project: AuthoringProject,
    selectedChapterId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NormalCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.authoring_chapters),
                    style = LegadoTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAdd) { Text(stringResource(R.string.add)) }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(project.chapters, key = { it.id }) { chapter ->
                    Surface(
                        color = if (chapter.id == selectedChapterId) {
                            LegadoTheme.colorScheme.secondaryContainer
                        } else LegadoTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(chapter.id) },
                    ) {
                        Text(
                            text = chapter.title,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (chapter.id == selectedChapterId) {
                                FontWeight.SemiBold
                            } else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterStrip(
    project: AuthoringProject,
    selectedChapterId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(project.chapters, key = { it.id }) { chapter ->
            FilterChip(
                selected = chapter.id == selectedChapterId,
                onClick = { onSelect(chapter.id) },
                label = {
                    Text(chapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onAdd,
                label = { Text(stringResource(R.string.authoring_add_chapter)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun WritingEditor(
    state: WritingUiState,
    onIntent: (WritingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = state.project ?: return
    val words = state.chapterContent.trim().split(Regex("\\s+")).count(String::isNotBlank)
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            NormalCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.writing_project_information),
                        style = LegadoTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = project.title,
                        onValueChange = { onIntent(WritingIntent.UpdateProjectTitle(it)) },
                        label = { Text(stringResource(R.string.writing_project_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = project.author,
                        onValueChange = { onIntent(WritingIntent.UpdateProjectAuthor(it)) },
                        label = { Text(stringResource(R.string.authoring_author)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = project.description,
                        onValueChange = { onIntent(WritingIntent.UpdateProjectDescription(it)) },
                        label = { Text(stringResource(R.string.writing_outline)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            }
        }

        if (state.selectedChapterId == null) {
            item {
                NormalCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.authoring_no_chapter))
                        Button(onClick = { onIntent(WritingIntent.AddChapter) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(stringResource(R.string.authoring_add_chapter))
                        }
                    }
                }
            }
        } else {
            item {
                NormalCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.authoring_chapter_editor),
                                style = LegadoTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.authoring_word_count, words),
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                                style = LegadoTheme.typography.labelMedium,
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item {
                                TextButton(
                                    onClick = { onIntent(WritingIntent.Undo) },
                                    enabled = state.canUndo,
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                                    Text(stringResource(R.string.authoring_undo))
                                }
                            }
                            item {
                                TextButton(
                                    onClick = { onIntent(WritingIntent.Redo) },
                                    enabled = state.canRedo,
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null)
                                    Text(stringResource(R.string.authoring_redo))
                                }
                            }
                            item {
                                TextButton(onClick = { onIntent(WritingIntent.DuplicateChapter) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    Text(stringResource(R.string.authoring_duplicate_chapter))
                                }
                            }
                            item {
                                TextButton(onClick = { onIntent(WritingIntent.RequestImage) }) {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                    Text(stringResource(R.string.authoring_insert_image))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.chapterTitle,
                            onValueChange = { onIntent(WritingIntent.UpdateChapterTitle(it)) },
                            label = { Text(stringResource(R.string.authoring_chapter_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.chapterContent,
                            onValueChange = { onIntent(WritingIntent.UpdateChapterContent(it)) },
                            label = { Text(stringResource(R.string.authoring_content)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 18,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onIntent(WritingIntent.MoveChapter(-1)) }) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null)
                                Text(stringResource(R.string.authoring_move_up))
                            }
                            TextButton(onClick = { onIntent(WritingIntent.MoveChapter(1)) }) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null)
                                Text(stringResource(R.string.authoring_move_down))
                            }
                            TextButton(onClick = {
                                onIntent(WritingIntent.RequestDeleteChapter)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }

            item {
                NormalCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.replace),
                            style = LegadoTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = state.replaceQuery,
                            onValueChange = { onIntent(WritingIntent.UpdateReplaceQuery(it)) },
                            label = { Text(stringResource(R.string.authoring_find_in_chapter)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.replaceWith,
                            onValueChange = { onIntent(WritingIntent.UpdateReplaceWith(it)) },
                            label = { Text(stringResource(R.string.authoring_replace_with)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(
                                    R.string.authoring_search_result_count,
                                    state.searchResultCount,
                                ),
                                style = LegadoTheme.typography.labelMedium,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = { onIntent(WritingIntent.ReplaceNext) },
                                enabled = state.searchResultCount > 0,
                            ) {
                                Text(stringResource(R.string.authoring_replace_next))
                            }
                            Button(
                                onClick = { onIntent(WritingIntent.ReplaceAll) },
                                enabled = state.searchResultCount > 0,
                            ) {
                                Text(stringResource(R.string.authoring_replace_all))
                            }
                        }
                    }
                }
            }

            item {
                NormalCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = LegadoTheme.colorScheme.tertiaryContainer,
                    contentColor = LegadoTheme.colorScheme.onTertiaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.writing_ai_assistant),
                                style = LegadoTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            stringResource(R.string.writing_ai_description),
                            style = LegadoTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = state.aiInstruction,
                            onValueChange = { onIntent(WritingIntent.UpdateAiInstruction(it)) },
                            label = { Text(stringResource(R.string.writing_ai_instruction)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                        Button(
                            onClick = { onIntent(WritingIntent.GenerateWithAi) },
                            enabled = !state.isGenerating,
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Text(
                                stringResource(
                                    if (state.isGenerating) R.string.writing_ai_generating
                                    else R.string.writing_ai_generate
                                )
                            )
                        }
                    }
                }
            }

            if (state.aiSuggestion.isNotBlank()) {
                item {
                    NormalCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.writing_ai_suggestion),
                                style = LegadoTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(state.aiSuggestion, style = LegadoTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    onIntent(WritingIntent.ApplyAiSuggestion)
                                }) {
                                    Text(stringResource(R.string.writing_ai_apply))
                                }
                                OutlinedButton(onClick = {
                                    onIntent(WritingIntent.DismissAiSuggestion)
                                }) {
                                    Text(stringResource(R.string.writing_ai_discard))
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AuthoringActionBar(
    isBusy: Boolean,
    isDirty: Boolean,
    autosaveState: WritingAutosaveState,
    onSave: () -> Unit,
) {
    val statusText = when (autosaveState) {
        WritingAutosaveState.PENDING -> stringResource(R.string.authoring_autosave_pending)
        WritingAutosaveState.SAVING -> stringResource(R.string.authoring_autosave_saving)
        WritingAutosaveState.ERROR -> stringResource(R.string.authoring_autosave_failed)
        WritingAutosaveState.IDLE,
        WritingAutosaveState.SAVED -> stringResource(
            if (isDirty) R.string.authoring_unsaved else R.string.authoring_saved
        )
    }
    Surface(color = LegadoTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusText,
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = onSave, enabled = !isBusy && isDirty) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun WritingDialogs(
    dialog: WritingDialog?,
    onIntent: (WritingIntent) -> Unit,
) {
    when (dialog) {
        WritingDialog.CreateProject -> {
            var title by remember { mutableStateOf("") }
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(WritingIntent.DismissDialog) },
                title = stringResource(R.string.writing_create_project),
                content = {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.writing_project_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                },
                confirmText = stringResource(R.string.create),
                onConfirm = { onIntent(WritingIntent.CreateProject(title)) },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onIntent(WritingIntent.DismissDialog) },
            )
        }
        is WritingDialog.DeleteProject -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(WritingIntent.DismissDialog) },
            title = stringResource(R.string.writing_delete_project_title),
            text = stringResource(R.string.writing_delete_project_message, dialog.title),
            confirmText = stringResource(R.string.delete),
            onConfirm = { onIntent(WritingIntent.ConfirmDeleteProject) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(WritingIntent.DismissDialog) },
        )
        is WritingDialog.DeleteChapter -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(WritingIntent.DismissDialog) },
            title = stringResource(R.string.authoring_delete_chapter_title),
            text = stringResource(R.string.authoring_delete_chapter_message, dialog.title),
            confirmText = stringResource(R.string.delete),
            onConfirm = { onIntent(WritingIntent.ConfirmDeleteChapter) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(WritingIntent.DismissDialog) },
        )
        is WritingDialog.UnsavedChanges -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(WritingIntent.DismissDialog) },
            title = stringResource(R.string.authoring_unsaved_title),
            text = stringResource(R.string.authoring_unsaved_message),
            content = {
                OutlinedButton(
                    onClick = { onIntent(WritingIntent.DiscardAndContinue) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.authoring_discard_changes))
                }
            },
            confirmText = stringResource(R.string.authoring_save_and_continue),
            onConfirm = { onIntent(WritingIntent.SaveAndContinue) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(WritingIntent.DismissDialog) },
        )
        null -> Unit
    }
}
