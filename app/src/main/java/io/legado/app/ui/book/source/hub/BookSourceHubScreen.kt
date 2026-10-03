package io.legado.app.ui.book.source.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.OnlineBookSourceItem
import io.legado.app.domain.model.OnlineSourceCollectionItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun BookSourceHubRouteScreen(
    onBackClick: () -> Unit,
    viewModel: BookSourceHubViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BookSourceHubEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    BookSourceHubScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourceHubScreen(
    state: BookSourceHubUiState,
    onIntent: (BookSourceHubIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.book_source_hub_title),
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = { onIntent(BookSourceHubIntent.LoadInitialData) },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            SecondaryTabRow(
                selectedTabIndex = state.currentTab.ordinal,
                containerColor = LegadoTheme.colorScheme.surface,
                contentColor = LegadoTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = state.currentTab == BookSourceHubTab.YCKCEO,
                    onClick = { onIntent(BookSourceHubIntent.SwitchTab(BookSourceHubTab.YCKCEO)) },
                    text = { Text(stringResource(R.string.source_hub_tab_yckceo)) },
                )
                Tab(
                    selected = state.currentTab == BookSourceHubTab.MIAOGONGZI,
                    onClick = { onIntent(BookSourceHubIntent.SwitchTab(BookSourceHubTab.MIAOGONGZI)) },
                    text = { Text(stringResource(R.string.source_hub_tab_miaogongzi)) },
                )
            }

            when (state.currentTab) {
                BookSourceHubTab.YCKCEO -> YckceoTabContent(state, onIntent)
                BookSourceHubTab.MIAOGONGZI -> MiaoGongZiTabContent(state, onIntent)
            }
        }
    }
}

@Composable
private fun YckceoTabContent(
    state: BookSourceHubUiState,
    onIntent: (BookSourceHubIntent) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = adaptiveContentPadding(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search_bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SearchBar(
                        query = state.searchQuery,
                        onQueryChange = { onIntent(BookSourceHubIntent.ChangeSearchQuery(it)) },
                        onSearch = {
                            keyboardController?.hide()
                            onIntent(BookSourceHubIntent.Search)
                        },
                        placeholder = stringResource(R.string.source_hub_search_hint),
                        autoFocus = false,
                    )
                }
                SmallTonalButton(
                    onClick = {
                        keyboardController?.hide()
                        onIntent(BookSourceHubIntent.Search)
                    },
                    icon = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search),
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        item(key = "category_filters") {
            CategoryFilterRow(
                selected = state.selectedCategory,
                onSelected = { onIntent(BookSourceHubIntent.ChangeCategory(it)) },
            )
        }

        if (state.isLoading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }
        } else if (state.yckceoSources.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = stringResource(R.string.source_health_no_sources),
                        style = LegadoTheme.typography.bodyLarge,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                items = state.yckceoSources,
                key = OnlineBookSourceItem::id,
            ) { item ->
                val isImporting = state.importingIds.contains(item.id)
                YckceoSourceCard(
                    item = item,
                    isImporting = isImporting,
                    onImport = { onIntent(BookSourceHubIntent.ImportYckceoSource(item)) },
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: String,
    onSelected: (String) -> Unit,
) {
    val categories = listOf(
        "ALL" to stringResource(R.string.source_hub_all_sources),
        "EXPLORE" to stringResource(R.string.source_hub_category_explore),
        "SEARCH" to stringResource(R.string.source_hub_category_search),
        "COMIC" to stringResource(R.string.source_hub_category_comic),
        "AUDIO" to stringResource(R.string.source_hub_category_audio),
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelected(key) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YckceoSourceCard(
    item: OnlineBookSourceItem,
    isImporting: Boolean,
    onImport: () -> Unit,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(LegadoTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = item.name,
                        style = LegadoTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.originUrl.isNotBlank()) {
                        AppText(
                            text = item.originUrl,
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                } else {
                    SmallTonalButton(
                        onClick = onImport,
                        icon = Icons.Default.CloudDownload,
                        contentDescription = stringResource(R.string.source_hub_download),
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.isVersion3) SourceTag("3.X", LegadoTheme.colorScheme.primary)
                if (item.hasExplore) SourceTag("Khám phá", LegadoTheme.colorScheme.tertiary)
                if (item.hasSearch) SourceTag("Tìm kiếm", LegadoTheme.colorScheme.secondary)
                if (item.hasImage) SourceTag("Ảnh", LegadoTheme.colorScheme.primary)
                if (item.hasAudio) SourceTag("Audio", LegadoTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                item.author?.let {
                    AppText(
                        text = "${stringResource(R.string.author)}: $it",
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.downloadCount?.let {
                    AppText(
                        text = stringResource(R.string.source_hub_downloads_count, it),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.updateTime?.let {
                    AppText(
                        text = it,
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        AppText(
            text = text,
            style = LegadoTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun MiaoGongZiTabContent(
    state: BookSourceHubUiState,
    onIntent: (BookSourceHubIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = adaptiveContentPadding(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "curated_header") {
            AppText(
                text = stringResource(R.string.source_hub_bundles_title),
                style = LegadoTheme.typography.titleMedium,
                color = LegadoTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        items(
            items = state.miaogongziBundles,
            key = OnlineSourceCollectionItem::downloadUrl,
        ) { bundle ->
            val isImporting = state.importingIds.contains(bundle.downloadUrl)
            MiaoGongZiBundleCard(
                bundle = bundle,
                isImporting = isImporting,
                onImport = { onIntent(BookSourceHubIntent.ImportBundle(bundle)) },
            )
        }
    }
}

@Composable
private fun MiaoGongZiBundleCard(
    bundle: OnlineSourceCollectionItem,
    isImporting: Boolean,
    onImport: () -> Unit,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(LegadoTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = bundle.title,
                        style = LegadoTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = stringResource(R.string.source_hub_bundle_sources_count, bundle.sourceCount) + " · " + bundle.author,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }

                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                } else {
                    SmallTonalButton(
                        onClick = onImport,
                        icon = Icons.Default.CloudDownload,
                        contentDescription = stringResource(R.string.source_hub_download),
                    )
                }
            }

            AppText(
                text = bundle.description,
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = stringResource(R.string.authoring_updated_at, bundle.updateTime),
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
