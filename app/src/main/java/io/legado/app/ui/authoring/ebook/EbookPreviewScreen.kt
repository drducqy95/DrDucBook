package io.legado.app.ui.authoring.ebook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.domain.model.EbookLayoutMode
import io.legado.app.domain.model.blockPlainText
import io.legado.app.domain.model.resolveEbookDocument
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel

@Composable
fun EbookPreviewRouteScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: EbookPreviewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(projectId) { viewModel.onIntent(EbookPreviewIntent.Load(projectId)) }
    EbookPreviewScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun EbookPreviewScreen(
    state: EbookPreviewUiState,
    onIntent: (EbookPreviewIntent) -> Unit,
    onBack: () -> Unit,
) {
    val background = if (state.darkMode) Color(0xFF151515) else Color(0xFFF7F7F7)
    val foreground = if (state.darkMode) Color(0xFFF4F4F4) else Color(0xFF171717)
    AppScaffold(
        appearanceTarget = AppearanceTarget.EBOOK,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = state.project?.title ?: "Ebook preview",
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppCircularProgressIndicator()
            }
            state.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.errorMessage, color = LegadoTheme.colorScheme.error)
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).background(background),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(EbookPreviewViewport.entries) { viewport ->
                        FilterChip(
                            selected = viewport == state.viewport,
                            onClick = { onIntent(EbookPreviewIntent.SetViewport(viewport)) },
                            label = { Text(viewport.name) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.darkMode,
                            onClick = { onIntent(EbookPreviewIntent.ToggleDarkMode) },
                            label = { Text(if (state.darkMode) "Dark" else "Light") },
                        )
                    }
                }
                AppSlider(
                    value = state.fontScale,
                    onValueChange = { onIntent(EbookPreviewIntent.SetFontScale(it)) },
                    valueRange = 0.75f..1.75f,
                    accessibilityLabel = "Font scale",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                val project = state.project ?: return@Column
                val document = project.resolveEbookDocument()
                val rendered = state.rendered
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(document.chapters, key = { it.id }) { chapter ->
                        FilterChip(
                            selected = chapter.id == state.selectedChapterId,
                            onClick = { onIntent(EbookPreviewIntent.SelectChapter(chapter.id)) },
                            label = { Text(chapter.title) },
                        )
                    }
                }
                val maxWidth = when (state.viewport) {
                    EbookPreviewViewport.PHONE -> 420.dp
                    EbookPreviewViewport.TABLET -> 760.dp
                    EbookPreviewViewport.PAGE -> 900.dp
                }
                val chapter = document.chapters.firstOrNull { it.id == state.selectedChapterId }
                val renderedChapter = rendered?.chapters?.firstOrNull { it.id == state.selectedChapterId }
                if (document.layoutMode == EbookLayoutMode.FIXED_PAGE) {
                    val previewDocument = document.copy(
                        chapters = document.chapters.map { source ->
                            if (source.id == renderedChapter?.id) source.copy(blocks = renderedChapter.blocks)
                            else source
                        }
                    )
                    EbookBlockCanvas(
                        document = previewDocument,
                        chapterId = chapter?.id,
                        selectedBlockId = null,
                        onIntent = {},
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxWidth).weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxWidth).weight(1f)
                            .background(if (state.darkMode) Color(0xFF202020) else Color.White)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(renderedChapter?.blocks ?: chapter?.blocks.orEmpty(), key = { it.id }) { block ->
                            if (block is EbookImageBlock) {
                                AsyncImage(block.uri, block.alt, Modifier.fillMaxWidth())
                            } else {
                                Text(
                                    blockPlainText(block),
                                    color = foreground,
                                    style = TextStyle(fontSize = (project.style.fontSizeSp * state.fontScale).sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
