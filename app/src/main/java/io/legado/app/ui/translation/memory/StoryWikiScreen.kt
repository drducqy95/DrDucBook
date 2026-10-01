package io.legado.app.ui.translation.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.drducbook.app.R
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import io.legado.app.domain.model.StoryWikiCharacterGraph
import io.legado.app.domain.model.StoryWikiGraphNode
import io.legado.app.domain.model.StoryWikiRelationshipTag
import io.legado.app.domain.model.StoryWikiSnapshot
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun StoryWikiRouteScreen(
    initialBookUrl: String? = null,
    onBack: () -> Unit,
    onOpenBook: (bookUrl: String, bookName: String) -> Unit,
    viewModel: StoryWikiViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(initialBookUrl) {
        if (!initialBookUrl.isNullOrBlank()) {
            viewModel.onIntent(StoryWikiIntent.SelectBook(initialBookUrl))
        }
    }
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
    BackHandler(enabled = state.selectedBookUrl != null) {
        onIntent(StoryWikiIntent.BackToBookList)
    }

    val selectedBook = state.books.firstOrNull { it.bookUrl == state.selectedBookUrl }

    AppScaffold(
        topBar = {
            GlassTopAppBar(
                title = if (selectedBook != null) {
                    selectedBook.bookName
                } else {
                    stringResource(R.string.story_wiki_title)
                },
                navigationIcon = {
                    TopBarNavigationButton(
                        onClick = {
                            if (state.selectedBookUrl != null) {
                                onIntent(StoryWikiIntent.BackToBookList)
                            } else {
                                onBack()
                            }
                        }
                    )
                },
            )
        },
    ) { padding ->
        if (state.selectedBookUrl == null) {
            StoryWikiBookListContent(
                state = state,
                onIntent = onIntent,
                contentPadding = padding,
            )
        } else {
            StoryWikiBookDetailContent(
                state = state,
                onIntent = onIntent,
                contentPadding = padding,
            )
        }
    }

    state.selectedRecord?.let { record ->
        if (record.kind == AiTranslationStoryMemoryKind.ENTITY) {
            CharacterDossierSheet(
                record = record,
                allRecords = state.glossaryRecords,
                tags = state.relationshipTags,
                timeline = state.timelineRecords,
                onDismissRequest = { onIntent(StoryWikiIntent.DismissRecord) },
                onOpenBook = { onIntent(StoryWikiIntent.OpenSelectedBook) },
                onSelectRelatedRecord = { relatedRecord -> onIntent(StoryWikiIntent.SelectRecord(relatedRecord)) },
            )
        } else {
            WikiRecordDialog(record, state.relationshipTags, onIntent)
        }
    }
    state.selectedGraphNode?.let { node ->
        GraphNodeDialog(node, state.relationshipTags, onIntent)
    }
}

@Composable
private fun StoryWikiBookListContent(
    state: StoryWikiUiState,
    onIntent: (StoryWikiIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    val query = state.query.trim()
    val filteredBooks = remember(state.books, query) {
        if (query.isBlank()) {
            state.books
        } else {
            state.books.filter { book ->
                book.bookName.contains(query, ignoreCase = true) ||
                    book.bookAuthor.contains(query, ignoreCase = true) ||
                    book.glossaryRecords.any { it.title.contains(query, ignoreCase = true) || it.raw.contains(query, ignoreCase = true) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
            filteredBooks.isEmpty() -> item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.story_wiki_no_books),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                item {
                    Text(
                        text = stringResource(R.string.story_wiki_book_count, filteredBooks.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                items(filteredBooks, key = StoryWikiSnapshot::bookUrl) { book ->
                    StoryWikiBookCard(book = book, onIntent = onIntent)
                }
            }
        }
    }
}

@Composable
private fun StoryWikiBookCard(
    book: StoryWikiSnapshot,
    onIntent: (StoryWikiIntent) -> Unit,
) {
    val entityCount = remember(book.glossaryRecords) {
        book.glossaryRecords.count { it.kind == AiTranslationStoryMemoryKind.ENTITY }
    }
    val worldCount = remember(book.glossaryRecords) {
        book.glossaryRecords.count { it.kind == AiTranslationStoryMemoryKind.WORLD_BUILDING }
    }
    val relationCount = book.relationshipTags.size
    val timelineCount = book.timelineRecords.size

    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onIntent(StoryWikiIntent.SelectBook(book.bookUrl)) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoilBookCover(
                name = book.bookName,
                author = book.bookAuthor,
                path = book.bookCoverUrl,
                modifier = Modifier.width(54.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = book.bookName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.bookAuthor.isNotBlank()) {
                    Text(
                        text = book.bookAuthor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatBadge(icon = "👥", count = entityCount)
                    StatBadge(icon = "🏰", count = worldCount)
                    if (relationCount > 0) {
                        StatBadge(icon = "🔗", count = relationCount)
                    }
                    if (timelineCount > 0) {
                        StatBadge(icon = "📖", count = timelineCount)
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.story_wiki_enter_book),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun StatBadge(
    icon: String,
    count: Int,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(icon, fontSize = 11.sp)
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun StoryWikiBookDetailContent(
    state: StoryWikiUiState,
    onIntent: (StoryWikiIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
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
                val filteredRecords = state.glossaryRecords.filter {
                    (state.selectedTab == StoryWikiTab.ENTITY && it.kind == AiTranslationStoryMemoryKind.ENTITY) ||
                        (state.selectedTab == StoryWikiTab.WORLD_BUILDING && it.kind == AiTranslationStoryMemoryKind.WORLD_BUILDING)
                }
                if (filteredRecords.isEmpty()) {
                    item { Text(stringResource(R.string.story_memory_empty), modifier = Modifier.padding(16.dp)) }
                } else {
                    items(
                        filteredRecords,
                        key = AiTranslationStoryWikiRecord::id,
                    ) { record -> WikiRecordItem(record, onIntent) }
                }
            }
        }
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

@Composable
private fun StoryWikiTab.label(): String = when (this) {
    StoryWikiTab.ENTITY -> stringResource(R.string.story_wiki_tab_entity)
    StoryWikiTab.WORLD_BUILDING -> stringResource(R.string.story_wiki_tab_world)
    StoryWikiTab.TIMELINE -> stringResource(R.string.story_wiki_tab_timeline)
    StoryWikiTab.CHARACTER_GRAPH -> stringResource(R.string.story_wiki_tab_graph)
}
