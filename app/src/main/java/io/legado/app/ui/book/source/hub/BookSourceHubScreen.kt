package io.legado.app.ui.book.source.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun BookSourceHubRouteScreen(
    onBackClick: () -> Unit,
    viewModel: BookSourceHubViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
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
        Box(modifier = Modifier.fillMaxSize()) {
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

            // Bottom Batch Action Bar
            if (state.currentTab == BookSourceHubTab.YCKCEO && state.selectedSourceIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = LegadoTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.isBatchImporting && state.batchProgress != null) {
                            val (current, total) = state.batchProgress
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppText(
                                    text = stringResource(R.string.source_hub_batch_importing, current, total),
                                    style = LegadoTheme.typography.bodyMedium,
                                    color = LegadoTheme.colorScheme.primary,
                                )
                                AppText(
                                    text = "$current / $total",
                                    style = LegadoTheme.typography.labelMedium,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { if (total > 0) current.toFloat() / total.toFloat() else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onIntent(BookSourceHubIntent.ClearSourceSelection) },
                                enabled = !state.isBatchImporting,
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(stringResource(R.string.source_hub_deselect_all))
                            }
                            OutlinedButton(
                                onClick = { onIntent(BookSourceHubIntent.SelectAllSources) },
                                enabled = !state.isBatchImporting,
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(stringResource(R.string.source_hub_select_all))
                            }
                            Button(
                                onClick = { onIntent(BookSourceHubIntent.ImportSelectedSources) },
                                enabled = !state.isBatchImporting && state.selectedSourceIds.isNotEmpty(),
                                modifier = Modifier.weight(1.5f),
                            ) {
                                AppText(
                                    stringResource(
                                        R.string.source_hub_batch_import,
                                        state.selectedSourceIds.size,
                                    )
                                )
                            }
                        }
                    }
                }
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

    val displayedSources = remember(
        state.yckceoSources,
        state.installedUrls,
        state.installedNames,
        state.showOnlyUninstalled,
    ) {
        if (!state.showOnlyUninstalled) {
            state.yckceoSources
        } else {
            state.yckceoSources.filter { item ->
                val normUrl = (NetworkUtils.getBaseUrl(item.originUrl) ?: item.originUrl).trimEnd('/').lowercase()
                val installed = (normUrl.isNotBlank() && state.installedUrls.contains(normUrl)) ||
                    state.installedNames.contains(item.name.trim().lowercase())
                !installed
            }.toImmutableList()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = adaptiveContentPadding(
            top = 12.dp,
            bottom = if (state.selectedSourceIds.isNotEmpty()) 140.dp else 96.dp,
        ),
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
                showOnlyUninstalled = state.showOnlyUninstalled,
                onToggleUninstalled = { onIntent(BookSourceHubIntent.ToggleFilterUninstalled) },
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
        } else if (displayedSources.isEmpty()) {
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
                items = displayedSources,
                key = OnlineBookSourceItem::id,
            ) { item ->
                val isImporting = state.importingIds.contains(item.id)
                val normalizedOriginUrl = remember(item.originUrl) {
                    (NetworkUtils.getBaseUrl(item.originUrl) ?: item.originUrl).trimEnd('/').lowercase()
                }
                val isInstalled = (normalizedOriginUrl.isNotBlank() && state.installedUrls.contains(normalizedOriginUrl)) ||
                    state.installedNames.contains(item.name.trim().lowercase())
                val isSelected = state.selectedSourceIds.contains(item.id)

                YckceoSourceCard(
                    item = item,
                    isImporting = isImporting,
                    isInstalled = isInstalled,
                    isSelected = isSelected,
                    onToggleSelect = { onIntent(BookSourceHubIntent.ToggleSelectSource(item.id)) },
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
    showOnlyUninstalled: Boolean,
    onToggleUninstalled: () -> Unit,
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "filter_uninstalled") {
            FilterChip(
                selected = showOnlyUninstalled,
                onClick = onToggleUninstalled,
                label = { Text(stringResource(R.string.source_hub_filter_uninstalled)) },
                leadingIcon = if (showOnlyUninstalled) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
            )
        }

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
    isInstalled: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onImport: () -> Unit,
) {
    NormalCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                )

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isInstalled) LegadoTheme.colorScheme.tertiaryContainer
                            else LegadoTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isInstalled) Icons.Default.Check else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (isInstalled) LegadoTheme.colorScheme.onTertiaryContainer
                        else LegadoTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppText(
                            text = item.name,
                            style = LegadoTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isInstalled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LegadoTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                AppText(
                                    text = "✓ " + stringResource(R.string.source_hub_installed),
                                    style = LegadoTheme.typography.labelSmall,
                                    color = LegadoTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
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
                        icon = if (isInstalled) Icons.Default.Done else Icons.Default.CloudDownload,
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
                        text = "${bundle.author} • ${stringResource(R.string.source_hub_bundle_sources_count, bundle.sourceCount)}",
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
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

            if (bundle.description.isNotBlank()) {
                AppText(
                    text = bundle.description,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppText(
                    text = bundle.updateTime,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
