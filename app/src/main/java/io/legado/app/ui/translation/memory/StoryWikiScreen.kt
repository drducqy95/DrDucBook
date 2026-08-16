package io.legado.app.ui.translation.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import coil.compose.AsyncImage
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun StoryWikiRouteScreen(
    onBack: () -> Unit,
    onOpenBook: (bookUrl: String, bookName: String) -> Unit,
    viewModel: StoryWikiViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is StoryWikiEffect.OpenBook -> onOpenBook(effect.bookUrl, effect.bookName)
            }
        }
    }
    StoryWikiScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryWikiScreen(
    state: StoryWikiUiState,
    onIntent: (StoryWikiIntent) -> Unit,
    onBack: () -> Unit,
) {
    AppScaffold(
        topBar = {
            GlassTopAppBar(
                title = stringResource(R.string.story_wiki_title),
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onIntent(StoryWikiIntent.ChangeQuery(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.story_memory_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StoryKindChip(null, state.selectedKind, onIntent)
                    AiTranslationStoryMemoryKind.entries.forEach { kind ->
                        StoryKindChip(kind, state.selectedKind, onIntent)
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
                state.errorMessage != null -> item {
                    Text(state.errorMessage, modifier = Modifier.padding(16.dp))
                }
                state.records.isEmpty() -> item {
                    Text(
                        stringResource(R.string.story_memory_empty),
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> items(state.records, key = AiTranslationStoryWikiRecord::id) { record ->
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onIntent(StoryWikiIntent.SelectRecord(record))
                        },
                        headlineContent = {
                            Text(record.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    record.bookName,
                                    record.chapterIndex?.let { stringResource(R.string.story_memory_chapter, it + 1) },
                                    record.subtitle,
                                ).joinToString(" · "),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = record.imagePath?.let { imagePath ->
                            {
                                AsyncImage(
                                    model = File(imagePath),
                                    contentDescription = record.title,
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

    state.selectedRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { onIntent(StoryWikiIntent.DismissRecord) },
            title = { Text(record.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    record.imagePath?.let { imagePath ->
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = record.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 320.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Text(record.bookName)
                    record.chapterIndex?.let {
                        Text(stringResource(R.string.story_memory_chapter, it + 1))
                    }
                    Text(record.subtitle)
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(StoryWikiIntent.OpenSelectedBook) }) {
                    Text(stringResource(R.string.story_memory_open_book))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(StoryWikiIntent.DismissRecord) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun StoryKindChip(
    kind: AiTranslationStoryMemoryKind?,
    selectedKind: AiTranslationStoryMemoryKind?,
    onIntent: (StoryWikiIntent) -> Unit,
) {
    FilterChip(
        selected = selectedKind == kind,
        onClick = { onIntent(StoryWikiIntent.SelectKind(kind)) },
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
