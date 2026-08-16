package io.legado.app.ui.authoring.ebook

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.constant.FeatureFlags
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.EbookLayoutMode
import io.legado.app.domain.model.resolveEbookDocument
import io.legado.app.domain.model.blockPlainText
import io.legado.app.domain.usecase.CloneContentVariant
import io.legado.app.service.export.EbookExportFormat
import io.legado.app.service.export.modernEbookExportFormats
import io.legado.app.ui.authoring.AuthoringProjectLibrary
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun EbookEditorRouteScreen(
    onBack: () -> Unit,
    onPreview: (String) -> Unit = {},
    showNavigationIcon: Boolean = true,
    handleSystemBack: Boolean = true,
    viewModel: EbookEditorViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
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
                viewModel.onIntent(EbookEditorIntent.ImagePicked(name, bytes))
            }.onFailure { error ->
                Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(viewModel, context, onBack) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                EbookEditorEffect.NavigateBack -> onBack()
                EbookEditorEffect.OpenImagePicker -> imagePicker.launch("image/*")
                is EbookEditorEffect.ShareFile -> context.share(
                    File(effect.path),
                    effect.mimeType,
                )
                is EbookEditorEffect.ShowMessage -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT,
                ).show()
                is EbookEditorEffect.NavigatePreview -> onPreview(effect.projectId)
            }
        }
    }
    EbookEditorScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        showNavigationIcon = showNavigationIcon,
        handleSystemBack = handleSystemBack,
    )
}

@Composable
fun EbookEditorScreen(
    state: EbookEditorUiState,
    onIntent: (EbookEditorIntent) -> Unit,
    showNavigationIcon: Boolean = true,
    handleSystemBack: Boolean = true,
) {
    val project = state.project
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val compactLayout = LocalConfiguration.current.screenWidthDp < 600
    var showCompactActions by remember { mutableStateOf(false) }
    BackHandler(enabled = handleSystemBack) { onIntent(EbookEditorIntent.BackPressed) }

    AppScaffold(
        appearanceTarget = AppearanceTarget.EBOOK,
        topBar = {
            val title = project?.title ?: stringResource(R.string.ebook_editor_title)
            val navigation: @Composable () -> Unit = if (showNavigationIcon) {
                { TopBarNavigationButton(onClick = { onIntent(EbookEditorIntent.BackPressed) }) }
            } else {
                {}
            }
            if (compactLayout) {
                GlassTopAppBar(
                    title = title,
                    navigationIcon = navigation,
                    actions = {
                        if (project == null) {
                            TopBarActionButton(
                                onClick = { onIntent(EbookEditorIntent.ShowCreateProject) },
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.ebook_editor_create_project),
                            )
                        } else {
                            TopBarActionButton(
                                onClick = { onIntent(EbookEditorIntent.Undo) },
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Undo",
                            )
                            TopBarActionButton(
                                onClick = { onIntent(EbookEditorIntent.Redo) },
                                imageVector = Icons.Default.Redo,
                                contentDescription = "Redo",
                            )
                            Box {
                                IconButton(onClick = { showCompactActions = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Editor tools")
                                }
                                DropdownMenu(
                                    expanded = showCompactActions,
                                    onDismissRequest = { showCompactActions = false },
                                ) {
                                    CompactEditorAction("Layers", Icons.Default.Layers) {
                                        showCompactActions = false
                                        onIntent(EbookEditorIntent.ShowLayers)
                                    }
                                    CompactEditorAction("Chapter manager", Icons.Default.MenuBook) {
                                        showCompactActions = false
                                        onIntent(EbookEditorIntent.ShowChapterManager)
                                    }
                                    CompactEditorAction("Validate", Icons.Default.FactCheck) {
                                        showCompactActions = false
                                        onIntent(EbookEditorIntent.Validate)
                                    }
                                    CompactEditorAction("Preview", Icons.Default.Preview) {
                                        showCompactActions = false
                                        onIntent(EbookEditorIntent.Preview)
                                    }
                                    CompactEditorAction(stringResource(R.string.save), Icons.Default.Save) {
                                        showCompactActions = false
                                        onIntent(EbookEditorIntent.Save)
                                    }
                                    CompactEditorAction(stringResource(R.string.delete), Icons.Default.Delete) {
                                        showCompactActions = false
                                        onIntent(EbookEditorIntent.RequestDeleteProject)
                                    }
                                }
                            }
                        }
                    },
                )
            } else {
                GlassMediumFlexibleTopAppBar(
                    title = title,
                    subtitle = project?.let {
                        stringResource(
                            if (state.isDirty) R.string.authoring_unsaved else R.string.authoring_saved
                        )
                    },
                    navigationIcon = navigation,
                    actions = {
                        if (project == null) {
                            TopBarActionButton(
                                onClick = { onIntent(EbookEditorIntent.ShowCreateProject) },
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.ebook_editor_create_project),
                            )
                        } else {
                            WideEditorActions(onIntent)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            if (project != null) {
                EbookActionBar(
                    state = state,
                    onSave = { onIntent(EbookEditorIntent.Save) },
                    onExport = { onIntent(EbookEditorIntent.Export) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (project == null) {
                AuthoringProjectLibrary(
                    projects = state.projects,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = {
                        onIntent(EbookEditorIntent.UpdateSearchQuery(it))
                    },
                    onOpenProject = { onIntent(EbookEditorIntent.OpenProject(it)) },
                    onCreateProject = { onIntent(EbookEditorIntent.ShowCreateProject) },
                    title = stringResource(R.string.ebook_editor_library_title),
                    description = stringResource(R.string.ebook_editor_library_description),
                    createLabel = stringResource(R.string.ebook_editor_create_project),
                    searchLabel = stringResource(R.string.authoring_search_projects),
                    emptyTitle = stringResource(R.string.ebook_editor_empty_title),
                    emptyDescription = stringResource(R.string.ebook_editor_empty_description),
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
                    icon = Icons.Default.AutoStories,
                    secondaryActionLabel = stringResource(R.string.ebook_editor_clone_downloaded),
                    onSecondaryAction = {
                        onIntent(EbookEditorIntent.ShowCloneDownloadedBook)
                    },
                )
            } else {
                EbookWorkspace(state = state, onIntent = onIntent)
            }
        }
    }

    EbookDialogs(dialog = state.dialog, onIntent = onIntent)
    CloneDownloadedBookSheet(state = state, onIntent = onIntent)
    EbookLayerPanel(state = state, onIntent = onIntent)
    EbookChapterManagerSheet(state = state, onIntent = onIntent)
}

@Composable
private fun RowScope.WideEditorActions(onIntent: (EbookEditorIntent) -> Unit) {
    TopBarActionButton(
        onClick = { onIntent(EbookEditorIntent.Undo) },
        imageVector = Icons.Default.Undo,
        contentDescription = "Undo",
    )
    TopBarActionButton(
        onClick = { onIntent(EbookEditorIntent.Redo) },
        imageVector = Icons.Default.Redo,
        contentDescription = "Redo",
    )
    TopBarActionButton(
        onClick = { onIntent(EbookEditorIntent.ShowLayers) },
        imageVector = Icons.Default.Layers,
        contentDescription = "Layers",
    )
    TopBarActionButton(
        onClick = { onIntent(EbookEditorIntent.ShowChapterManager) },
        imageVector = Icons.Default.MenuBook,
        contentDescription = "Chapter manager",
    )
    TopBarActionButton(
        onClick = { onIntent(EbookEditorIntent.Validate) },
        imageVector = Icons.Default.FactCheck,
        contentDescription = "Validate",
    )
    TopBarActionButton(
        onClick = { onIntent(EbookEditorIntent.Preview) },
        imageVector = Icons.Default.Preview,
        contentDescription = "Preview",
    )
    TopBarActionButton(
        onClick = { onIntent(EbookEditorIntent.Save) },
        imageVector = Icons.Default.Save,
        contentDescription = stringResource(R.string.save),
    )
    TopBarActionButton(
        onClick = { onIntent(EbookEditorIntent.RequestDeleteProject) },
        imageVector = Icons.Default.Delete,
        contentDescription = stringResource(R.string.delete),
    )
}

@Composable
private fun CompactEditorAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun EbookWorkspace(
    state: EbookEditorUiState,
    onIntent: (EbookEditorIntent) -> Unit,
) {
    val project = state.project ?: return
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EbookChapterSidebar(
                    project = project,
                    selectedChapterId = state.selectedChapterId,
                    onSelect = { onIntent(EbookEditorIntent.SelectChapter(it)) },
                    onAdd = { onIntent(EbookEditorIntent.AddChapter) },
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                )
                EbookEditorContent(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                EbookChapterStrip(
                    project = project,
                    selectedChapterId = state.selectedChapterId,
                    onSelect = { onIntent(EbookEditorIntent.SelectChapter(it)) },
                    onAdd = { onIntent(EbookEditorIntent.AddChapter) },
                )
                EbookEditorContent(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EbookChapterSidebar(
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
                            chapter.title,
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
private fun EbookChapterStrip(
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
                label = { Text(chapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
private fun EbookEditorContent(
    state: EbookEditorUiState,
    onIntent: (EbookEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = state.project ?: return
    val document = project.resolveEbookDocument()
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                        stringResource(R.string.ebook_editor_metadata),
                        style = LegadoTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = project.title,
                        onValueChange = { onIntent(EbookEditorIntent.UpdateTitle(it)) },
                        label = { Text(stringResource(R.string.ebook_editor_book_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = project.author,
                            onValueChange = { onIntent(EbookEditorIntent.UpdateAuthor(it)) },
                            label = { Text(stringResource(R.string.authoring_author)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = project.language,
                            onValueChange = { onIntent(EbookEditorIntent.UpdateLanguage(it)) },
                            label = { Text(stringResource(R.string.ebook_editor_language)) },
                            modifier = Modifier.width(110.dp),
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = project.description,
                        onValueChange = { onIntent(EbookEditorIntent.UpdateDescription(it)) },
                        label = { Text(stringResource(R.string.ebook_editor_description)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
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
                        stringResource(R.string.ebook_editor_layout),
                        style = LegadoTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = project.style.fontFamily,
                            onValueChange = { onIntent(EbookEditorIntent.UpdateFontFamily(it)) },
                            label = { Text(stringResource(R.string.ebook_editor_font)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = project.style.fontSizeSp.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let {
                                    onIntent(EbookEditorIntent.UpdateFontSize(it))
                                }
                            },
                            label = { Text(stringResource(R.string.ebook_editor_font_size)) },
                            keyboardOptions = numberKeyboard,
                            modifier = Modifier.width(104.dp),
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = project.style.lineHeightPercent.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let {
                                onIntent(EbookEditorIntent.UpdateLineHeight(it))
                            }
                        },
                        label = { Text(stringResource(R.string.ebook_editor_line_height)) },
                        keyboardOptions = numberKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.ebook_editor_drop_cap))
                            Text(
                                stringResource(R.string.ebook_editor_drop_cap_description),
                                style = LegadoTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = project.style.dropCap,
                            onCheckedChange = { onIntent(EbookEditorIntent.UpdateDropCap(it)) },
                        )
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
                    Text("Document mode", style = LegadoTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EbookLayoutMode.entries
                            .filter { mode ->
                                mode != EbookLayoutMode.FIXED_PAGE ||
                                    FeatureFlags.ebookFixedLayout ||
                                    document.layoutMode == EbookLayoutMode.FIXED_PAGE
                            }
                            .forEach { mode ->
                            FilterChip(
                                selected = document.layoutMode == mode,
                                onClick = { onIntent(EbookEditorIntent.SetLayoutMode(mode)) },
                                label = { Text(mode.name) },
                            )
                            }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EbookBlockKind.entries) { kind ->
                            FilterChip(
                                selected = false,
                                onClick = { onIntent(EbookEditorIntent.InsertBlock(kind)) },
                                label = { Text(kind.name.replace('_', ' ')) },
                            )
                        }
                    }
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
                        Button(onClick = { onIntent(EbookEditorIntent.AddChapter) }) {
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
                                style = LegadoTheme.typography.labelMedium,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = state.chapterTitle,
                            onValueChange = { onIntent(EbookEditorIntent.UpdateChapterTitle(it)) },
                            label = { Text(stringResource(R.string.authoring_chapter_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        if (document.layoutMode == EbookLayoutMode.FIXED_PAGE) {
                            EbookBlockCanvas(
                                document = document,
                                chapterId = state.selectedChapterId,
                                selectedBlockId = state.selectedBlockId,
                                selectedBlockIds = state.selectedBlockIds,
                                onIntent = onIntent,
                                modifier = Modifier.fillMaxWidth().height(560.dp),
                            )
                            SelectedBlockEditor(state, onIntent)
                        } else {
                            OutlinedTextField(
                                value = state.chapterContent,
                                onValueChange = { onIntent(EbookEditorIntent.UpdateChapterContent(it)) },
                                label = { Text(stringResource(R.string.authoring_content)) },
                                supportingText = {
                                    Text(stringResource(R.string.ebook_editor_image_marker_hint))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 18,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            OutlinedButton(onClick = {
                                onIntent(EbookEditorIntent.RequestImage)
                            }) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Text(stringResource(R.string.ebook_editor_insert_image))
                            }
                            TextButton(onClick = { onIntent(EbookEditorIntent.MoveChapter(-1)) }) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null)
                            }
                            TextButton(onClick = { onIntent(EbookEditorIntent.MoveChapter(1)) }) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null)
                            }
                            TextButton(onClick = {
                                onIntent(EbookEditorIntent.RequestDeleteChapter)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
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
                        stringResource(R.string.ebook_editor_export_title),
                        style = LegadoTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.ebook_editor_export_description),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(modernEbookExportFormats) { format ->
                            FilterChip(
                                selected = format == state.exportFormat,
                                onClick = {
                                    onIntent(EbookEditorIntent.SelectExportFormat(format))
                                },
                                label = { Text(format.name) },
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SelectedBlockEditor(
    state: EbookEditorUiState,
    onIntent: (EbookEditorIntent) -> Unit,
) {
    val block = state.project?.resolveEbookDocument()?.chapters
        ?.firstOrNull { it.id == state.selectedChapterId }
        ?.blocks
        ?.firstOrNull { it.id == state.selectedBlockId }
        ?: return
    if (state.selectedBlockIds.size > 1) {
        Text("${state.selectedBlockIds.size} blocks selected")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(EbookBlockAlignment.entries) { alignment ->
                TextButton(onClick = { onIntent(EbookEditorIntent.AlignSelectedBlocks(alignment)) }) {
                    Text(alignment.name.replace('_', ' '))
                }
            }
            items(EbookDistributionAxis.entries) { axis ->
                TextButton(onClick = { onIntent(EbookEditorIntent.DistributeSelectedBlocks(axis)) }) {
                    Text("Distribute ${axis.name.lowercase()}")
                }
            }
        }
    }
    OutlinedTextField(
        value = blockPlainText(block),
        onValueChange = { onIntent(EbookEditorIntent.UpdateSelectedBlockText(it)) },
        label = { Text(block.name) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextButton(onClick = { onIntent(EbookEditorIntent.DuplicateSelectedBlock) }) {
            Text("Duplicate")
        }
        TextButton(onClick = { onIntent(EbookEditorIntent.ToggleSelectedBlockLock) }) {
            Text(if (block.geometry?.isLocked == true) "Unlock" else "Lock")
        }
        TextButton(onClick = { onIntent(EbookEditorIntent.DeleteSelectedBlock) }) {
            Text("Delete")
        }
    }
    OutlinedTextField(
        value = state.aiInstruction,
        onValueChange = { onIntent(EbookEditorIntent.UpdateAiInstruction(it)) },
        label = { Text("AI suggestion for selected block") },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onIntent(EbookEditorIntent.GenerateBlockWithAi) },
        enabled = !state.isGenerating && state.aiInstruction.isNotBlank(),
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null)
        Text(if (state.isGenerating) "Generating" else "Generate suggestion")
    }
    if (state.aiSuggestion.isNotBlank()) {
        NormalCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.aiSuggestion)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onIntent(EbookEditorIntent.ApplyAiSuggestion) }) {
                        Text("Apply")
                    }
                    OutlinedButton(onClick = { onIntent(EbookEditorIntent.DismissAiSuggestion) }) {
                        Text("Discard")
                    }
                }
            }
        }
    }
}

@Composable
private fun EbookActionBar(
    state: EbookEditorUiState,
    onSave: () -> Unit,
    onExport: () -> Unit,
) {
    Surface(color = LegadoTheme.colorScheme.surfaceContainerHigh) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 600.dp
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        if (state.isDirty) R.string.authoring_unsaved else R.string.authoring_saved
                    ),
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (compact) {
                    IconButton(
                        onClick = onSave,
                        enabled = !state.isLoading && state.isDirty,
                    ) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                    }
                    IconButton(onClick = onExport, enabled = !state.isLoading) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = stringResource(
                                R.string.ebook_editor_export_format,
                                state.exportFormat.name,
                            ),
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onSave,
                        enabled = !state.isLoading && state.isDirty,
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text(stringResource(R.string.save))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onExport, enabled = !state.isLoading) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Text(stringResource(R.string.ebook_editor_export_format, state.exportFormat.name))
                    }
                }
            }
        }
    }
}

@Composable
private fun EbookDialogs(
    dialog: EbookEditorDialog?,
    onIntent: (EbookEditorIntent) -> Unit,
) {
    when (dialog) {
        EbookEditorDialog.CreateProject -> {
            var title by remember { mutableStateOf("") }
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(EbookEditorIntent.DismissDialog) },
                title = stringResource(R.string.ebook_editor_create_project),
                content = {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.ebook_editor_book_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                },
                confirmText = stringResource(R.string.create),
                onConfirm = { onIntent(EbookEditorIntent.CreateProject(title)) },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onIntent(EbookEditorIntent.DismissDialog) },
            )
        }
        is EbookEditorDialog.DeleteProject -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(EbookEditorIntent.DismissDialog) },
            title = stringResource(R.string.ebook_editor_delete_project_title),
            text = stringResource(R.string.ebook_editor_delete_project_message, dialog.title),
            confirmText = stringResource(R.string.delete),
            onConfirm = { onIntent(EbookEditorIntent.ConfirmDeleteProject) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(EbookEditorIntent.DismissDialog) },
        )
        is EbookEditorDialog.DeleteChapter -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(EbookEditorIntent.DismissDialog) },
            title = stringResource(R.string.authoring_delete_chapter_title),
            text = stringResource(R.string.authoring_delete_chapter_message, dialog.title),
            confirmText = stringResource(R.string.delete),
            onConfirm = { onIntent(EbookEditorIntent.ConfirmDeleteChapter) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(EbookEditorIntent.DismissDialog) },
        )
        is EbookEditorDialog.DeleteChapters -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(EbookEditorIntent.DismissDialog) },
            title = "Delete selected chapters",
            text = "Xóa ${dialog.label}? Bạn vẫn có thể hoàn tác trước khi lưu.",
            confirmText = stringResource(R.string.delete),
            onConfirm = { onIntent(EbookEditorIntent.ConfirmDeleteChapter) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(EbookEditorIntent.DismissDialog) },
        )
        is EbookEditorDialog.RenameBlock -> {
            var name by remember(dialog.blockId) { mutableStateOf(dialog.currentName) }
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(EbookEditorIntent.DismissDialog) },
                title = "Rename block",
                content = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Block name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                },
                confirmText = stringResource(R.string.save),
                onConfirm = { onIntent(EbookEditorIntent.RenameBlock(dialog.blockId, name)) },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onIntent(EbookEditorIntent.DismissDialog) },
            )
        }
        EbookEditorDialog.ConfirmLossyTextExport -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(EbookEditorIntent.DismissDialog) },
            title = "Export as plain text",
            text = "TXT cannot preserve images, fixed-page geometry, rotation, or visual layers. Continue with text content only?",
            confirmText = "Continue",
            onConfirm = { onIntent(EbookEditorIntent.ConfirmExport) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(EbookEditorIntent.DismissDialog) },
        )
        is EbookEditorDialog.UnsavedChanges -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(EbookEditorIntent.DismissDialog) },
            title = stringResource(R.string.authoring_unsaved_title),
            text = stringResource(R.string.authoring_unsaved_message),
            content = {
                OutlinedButton(
                    onClick = { onIntent(EbookEditorIntent.DiscardAndContinue) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.authoring_discard_changes))
                }
            },
            confirmText = stringResource(R.string.authoring_save_and_continue),
            onConfirm = { onIntent(EbookEditorIntent.SaveAndContinue) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(EbookEditorIntent.DismissDialog) },
        )
        null -> Unit
    }
}

@Composable
private fun CloneDownloadedBookSheet(
    state: EbookEditorUiState,
    onIntent: (EbookEditorIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = state.sheet is EbookEditorSheet.CloneDownloadedBook,
        onDismissRequest = { onIntent(EbookEditorIntent.DismissSheet) },
        title = stringResource(R.string.ebook_editor_clone_downloaded),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.ebook_editor_clone_description),
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.cloneQuery,
                onValueChange = { onIntent(EbookEditorIntent.UpdateCloneQuery(it)) },
                label = { Text("Search downloaded books") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                items(state.cloneCandidates, key = { it.bookUrl }) { candidate ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onIntent(EbookEditorIntent.SelectCloneCandidate(candidate.bookUrl))
                        },
                        color = if (candidate.bookUrl == state.sourceBookUrl) {
                            LegadoTheme.colorScheme.secondaryContainer
                        } else LegadoTheme.colorScheme.surface,
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(candidate.title, style = LegadoTheme.typography.titleSmall)
                            Text(
                                listOf(
                                    candidate.author.takeIf(String::isNotBlank),
                                    stringResource(R.string.authoring_chapter_count, candidate.chapterCount),
                                ).filterNotNull().joinToString(" - "),
                                style = LegadoTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.sourceBookUrl,
                onValueChange = { onIntent(EbookEditorIntent.UpdateSourceBookUrl(it)) },
                label = { Text(stringResource(R.string.ebook_editor_source_book_url)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = state.cloneChapterScope,
                onValueChange = { onIntent(EbookEditorIntent.UpdateCloneChapterScope(it)) },
                label = { Text("Chapter scope: all, 1-10,15") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CloneContentVariant.entries) { variant ->
                    FilterChip(
                        selected = variant == state.cloneVariant,
                        onClick = { onIntent(EbookEditorIntent.UpdateCloneVariant(variant)) },
                        label = { Text(variant.name) },
                    )
                }
            }
            if (state.cloneVariant != CloneContentVariant.RAW) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.cloneProvider,
                        onValueChange = { onIntent(EbookEditorIntent.UpdateCloneProvider(it)) },
                        label = { Text("Translation provider") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.cloneTargetLanguage,
                        onValueChange = { onIntent(EbookEditorIntent.UpdateCloneTargetLanguage(it)) },
                        label = { Text("Language") },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                    )
                }
            }
            Button(
                onClick = { onIntent(EbookEditorIntent.CloneDownloadedBook) },
                enabled = !state.isLoading && state.sourceBookUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AutoStories, contentDescription = null)
                Text(stringResource(R.string.ebook_editor_clone_action))
            }
        }
    }
}
