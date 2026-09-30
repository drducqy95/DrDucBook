package io.legado.app.ui.translation.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import coil.compose.AsyncImage
import com.drducbook.app.R
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import io.legado.app.domain.model.StoryWikiCharacterGraph
import io.legado.app.domain.model.StoryWikiGraphNode
import io.legado.app.domain.model.StoryWikiRelationshipTag
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
    StoryWikiScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

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
            if (state.books.size > 1) {
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.books.forEach { book ->
                            FilterChip(
                                selected = state.selectedBookUrl == book.bookUrl,
                                onClick = { onIntent(StoryWikiIntent.SelectBook(book.bookUrl)) },
                                label = { Text(book.bookName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
            item {
                ScrollableTabRow(selectedTabIndex = state.selectedTab.ordinal) {
                    StoryWikiTab.entries.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { onIntent(StoryWikiIntent.SelectTab(tab)) },
                            text = { Text(tab.label()) },
                        )
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
                state.errorMessage != null -> item { Text(state.errorMessage, modifier = Modifier.padding(16.dp)) }
                state.selectedTab == StoryWikiTab.CHARACTER_GRAPH -> {
                    if (state.characterGraph.nodes.isEmpty()) {
                        item { Text(stringResource(R.string.story_memory_empty), modifier = Modifier.padding(16.dp)) }
                    } else {
                        items(
                            graphRows(state.characterGraph),
                            key = { it.first.id },
                        ) { (node, depth) ->
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (depth * 20).dp)
                                    .clickable { onIntent(StoryWikiIntent.SelectGraphNode(node.id)) },
                                headlineContent = {
                                    Text(node.target.ifBlank { node.raw }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = { Text("${node.raw} · ${node.category}") },
                            )
                        }
                    }
                }
                state.selectedTab == StoryWikiTab.TIMELINE -> {
                    if (state.timelineRecords.isEmpty()) {
                        item { Text(stringResource(R.string.story_memory_empty), modifier = Modifier.padding(16.dp)) }
                    } else {
                        items(state.timelineRecords, key = AiTranslationStoryWikiRecord::id) { record ->
                            WikiRecordItem(record, onIntent)
                        }
                    }
                }
                else -> {
                    if (state.glossaryRecords.isEmpty()) {
                        item { Text(stringResource(R.string.story_memory_empty), modifier = Modifier.padding(16.dp)) }
                    } else {
                        items(
                            state.glossaryRecords.filter {
                                (state.selectedTab == StoryWikiTab.ENTITY && it.kind == AiTranslationStoryMemoryKind.ENTITY) ||
                                    (state.selectedTab == StoryWikiTab.WORLD_BUILDING && it.kind == AiTranslationStoryMemoryKind.WORLD_BUILDING)
                            },
                            key = AiTranslationStoryWikiRecord::id,
                        ) { record -> WikiRecordItem(record, onIntent) }
                    }
                }
            }
        }
    }

    state.selectedRecord?.let { record ->
        WikiRecordDialog(record, state.relationshipTags, onIntent)
    }
    state.selectedGraphNode?.let { node ->
        GraphNodeDialog(node, state.relationshipTags, onIntent)
    }
}

@Composable
private fun WikiRecordItem(
    record: AiTranslationStoryWikiRecord,
    onIntent: (StoryWikiIntent) -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onIntent(StoryWikiIntent.SelectRecord(record)) },
        headlineContent = { Text(record.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(record.bookName, record.chapterIndex?.let { "Chương ${it + 1}" }, record.subtitle)
                    .joinToString(" · "),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = record.imagePath?.let { path ->
            {
                AsyncImage(
                    model = File(path),
                    contentDescription = record.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
        },
    )
}

@Composable
private fun WikiRecordDialog(
    record: AiTranslationStoryWikiRecord,
    tags: List<StoryWikiRelationshipTag>,
    onIntent: (StoryWikiIntent) -> Unit,
) {
    val related = tags.filter {
        it.sourceRaw.equals(record.raw, true) || it.targetRaw.equals(record.raw, true)
    }
    AlertDialog(
        onDismissRequest = { onIntent(StoryWikiIntent.DismissRecord) },
        title = { Text(record.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Raw: ${record.raw}")
                record.senseKey.takeIf(String::isNotBlank)?.let { Text("Sense: $it") }
                record.category.takeIf(String::isNotBlank)?.let { Text("Category: $it") }
                record.description.takeIf(String::isNotBlank)?.let { Text(it) }
                related.forEach { tag ->
                    Text(
                        "${tag.relation}: ${tag.targetTarget.ifBlank { tag.targetRaw }}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

@Composable
private fun GraphNodeDialog(
    node: StoryWikiGraphNode,
    tags: List<StoryWikiRelationshipTag>,
    onIntent: (StoryWikiIntent) -> Unit,
) {
    val related = tags.filter { it.sourceRaw.equals(node.raw, true) || it.targetRaw.equals(node.raw, true) }
    AlertDialog(
        onDismissRequest = { onIntent(StoryWikiIntent.DismissGraphNode) },
        title = { Text(node.target.ifBlank { node.raw }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Raw: ${node.raw}")
                Text("Category: ${node.category}")
                related.forEach { tag ->
                    Text("${tag.relation}: ${tag.targetTarget.ifBlank { tag.targetRaw }}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(StoryWikiIntent.DismissGraphNode) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

private fun graphRows(graph: StoryWikiCharacterGraph): List<Pair<StoryWikiGraphNode, Int>> {
    val incoming = graph.edges.map { it.targetId }.toSet()
    val outgoing = graph.edges.groupBy { it.sourceId }
    val nodes = graph.nodes.associateBy { it.id }
    val visited = mutableSetOf<String>()
    val result = mutableListOf<Pair<StoryWikiGraphNode, Int>>()

    fun visit(id: String, depth: Int) {
        if (!visited.add(id)) return
        nodes[id]?.let { result += it to depth }
        outgoing[id].orEmpty().forEach { edge -> visit(edge.targetId, depth + 1) }
    }
    graph.nodes.filter { it.id !in incoming }.forEach { visit(it.id, 0) }
    graph.nodes.forEach { visit(it.id, 0) }
    return result
}

private fun StoryWikiTab.label(): String = when (this) {
    StoryWikiTab.ENTITY -> "Entity"
    StoryWikiTab.WORLD_BUILDING -> "World-building"
    StoryWikiTab.TIMELINE -> "Timeline"
    StoryWikiTab.CHARACTER_GRAPH -> "Character graph"
}
