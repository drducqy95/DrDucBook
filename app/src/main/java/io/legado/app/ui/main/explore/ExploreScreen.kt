package io.legado.app.ui.main.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.drducbook.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.explore.ExploreBookGridItem
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.LoadMoreFooter
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.utils.startActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?) -> Unit,
    onOpenTranslationSettings: () -> Unit,
    onOpenAiRouter: () -> Unit,
    onOpenBrowser: (sourceUrl: String?, initialUrl: String?) -> Unit,
) {
    ExploreDiscoveryScreen(
        onOpenExploreShow = onOpenExploreShow,
        onOpenBookInfo = onOpenBookInfo,
        onOpenTranslationSettings = onOpenTranslationSettings,
        onOpenAiRouter = onOpenAiRouter,
        onOpenBrowser = onOpenBrowser,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ExploreDiscoveryScreen(
    viewModel: ExploreViewModel = koinViewModel(),
    previewViewModel: ExploreShowViewModel = koinViewModel(key = "explore-preview"),
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?) -> Unit,
    onOpenTranslationSettings: () -> Unit,
    onOpenAiRouter: () -> Unit,
    onOpenBrowser: (sourceUrl: String?, initialUrl: String?) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewState by previewViewModel.uiState.collectAsStateWithLifecycle()
    val selectedSource = uiState.items.firstOrNull {
        it.bookSourceUrl == uiState.expandedId
    }
    var showSourceMenu by remember { mutableStateOf(false) }
    var showTranslationShortcut by remember { mutableStateOf(false) }
    val exploreKindUseCase: ExploreKindUiUseCase = koinInject()
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)
    var selectedKindTitle by rememberSaveable(selectedSource?.bookSourceUrl) {
        mutableStateOf<String?>(null)
    }
    val urlKinds = remember(uiState.exploreKinds) {
        uiState.exploreKinds.filter { it.type == ExploreKind.Type.url }
    }
    val selectedKind = urlKinds.firstOrNull { it.title == selectedKindTitle }
        ?: urlKinds.firstOrNull()
    val browserRecoveryAvailable = shouldOfferSourceBrowser(
        errorMessage = previewState.errorMsg,
        sourceUrl = selectedSource?.bookSourceUrl,
        browserUrl = uiState.sourceBrowserUrl,
    )
    val previewGridState = rememberLazyGridState()
    val canLoadNextPreviewPage = previewState.books.isNotEmpty() &&
        !previewState.isLoading &&
        !previewState.isRefreshing &&
        previewState.errorMsg == null &&
        !previewState.isEnd

    ExplorePreviewLoadMoreDetector(
        gridState = previewGridState,
        sourceUrl = selectedSource?.bookSourceUrl,
        exploreUrl = selectedKind?.url,
        bookCount = previewState.books.size,
        enabled = canLoadNextPreviewPage,
        onLoadMore = { previewViewModel.onIntent(ExploreShowIntent.LoadMore) },
    )

    LaunchedEffect(selectedSource?.bookSourceUrl, selectedKind?.title, selectedKind?.url) {
        val source = selectedSource ?: return@LaunchedEffect
        val kind = selectedKind ?: return@LaunchedEffect
        selectedKindTitle = kind.title
        previewViewModel.onIntent(
            ExploreShowIntent.InitData(
                sourceUrl = source.bookSourceUrl,
                exploreUrl = kind.url,
                title = kind.title,
            )
        )
    }

    LaunchedEffect(viewModel, activity, exploreKindUseCase) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExploreEffect.ExecuteKindAction -> {
                    val infoMap = getExploreInfoMap(effect.sourceUrl)
                    exploreKindUseCase.executeAction(
                        action = effect.kind.action,
                        title = effect.kind.title,
                        sourceUrl = effect.sourceUrl,
                        infoMap = infoMap,
                        activity = activity,
                        onRefreshKinds = { viewModel.refreshExploreKinds(effect.sourceUrl) },
                    )
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel, previewViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val sourceUrl = viewModel.consumeBrowserRecovery() ?: return@LifecycleEventObserver
                viewModel.refreshExploreKinds(sourceUrl)
                previewViewModel.onIntent(ExploreShowIntent.Refresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ListScaffold(
        title = stringResource(R.string.discovery),
        subtitle = selectedSource?.let { source ->
            uiState.sourceDisplayNames[source.bookSourceUrl] ?: source.bookSourceName
        }
            ?: uiState.selectedGroup.ifEmpty { stringResource(R.string.select_source) },
        state = uiState,
        onSearchQueryChange = viewModel::search,
        onSearchToggle = viewModel::toggleSearchVisible,
        searchPlaceholder = stringResource(R.string.search_source),
        topBarActions = {
            TopBarActionButton(
                onClick = { onOpenBrowser(null, null) },
                imageVector = Icons.Default.Public,
                contentDescription = stringResource(R.string.browser),
            )
            TopBarActionButton(
                onClick = onOpenAiRouter,
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = stringResource(R.string.ai_router),
            )
            TopBarActionButton(
                onClick = { showTranslationShortcut = true },
                imageVector = Icons.Default.Translate,
                contentDescription = stringResource(R.string.translation_config),
            )
            Box {
                TopBarActionButton(
                    onClick = { showSourceMenu = true },
                    imageVector = Icons.Default.Language,
                    contentDescription = stringResource(R.string.select_source),
                )
                RoundDropdownMenu(
                    expanded = showSourceMenu,
                    onDismissRequest = { showSourceMenu = false },
                ) {
                    PillHeaderDivider(title = stringResource(R.string.select_source))
                    uiState.items.forEach { source ->
                        RoundDropdownMenuItem(
                            leadingIcon = {
                                MenuItemIcon(
                                    if (source.bookSourceUrl == selectedSource?.bookSourceUrl) {
                                        Icons.Default.LibraryBooks
                                    } else {
                                        Icons.Default.Language
                                    }
                                )
                            },
                            text = (uiState.sourceDisplayNames[source.bookSourceUrl]
                                ?: source.bookSourceName).let { translatedName ->
                                source.bookSourceGroup?.takeIf(String::isNotBlank)
                                    ?.let { "$translatedName ($it)" }
                                    ?: translatedName
                            },
                            onClick = {
                                viewModel.selectSource(source)
                                showSourceMenu = false
                            },
                        )
                    }
                    selectedSource?.let { source ->
                        PillHeaderDivider(
                            title = uiState.sourceDisplayNames[source.bookSourceUrl]
                                ?: source.bookSourceName
                        )
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
                            text = stringResource(R.string.edit_source),
                            onClick = {
                                showSourceMenu = false
                                context.startActivity<BookSourceEditActivity> {
                                    putExtra("sourceUrl", source.bookSourceUrl)
                                }
                            },
                        )
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.Default.Refresh) },
                            text = stringResource(R.string.refresh),
                            onClick = {
                                showSourceMenu = false
                                viewModel.refreshExploreKinds(source)
                            },
                        )
                    }
                    PillHeaderDivider(title = stringResource(R.string.book_source_manage))
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Settings) },
                        text = stringResource(R.string.book_source_manage),
                        onClick = {
                            showSourceMenu = false
                            context.startActivity<BookSourceActivity>()
                        },
                    )
                }
            }
        },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.Default.Group) },
                text = stringResource(R.string.all),
                onClick = { viewModel.setGroup(""); dismiss() },
            )
            uiState.groups.forEach { group ->
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Outlined.Label) },
                    text = group,
                    onClick = { viewModel.setGroup(group); dismiss() },
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        when {
            uiState.items.isEmpty() || selectedSource == null -> {
                EmptyMessage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding(),
                        ),
                    messageResId = R.string.explore_empty,
                )
            }

            uiState.loadingKinds -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppContainedLoadingIndicator()
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = previewGridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = adaptiveContentPadding(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = 120.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(
                        key = "source-header:${selectedSource.bookSourceUrl}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        ExploreSourceHeader(
                            name = uiState.sourceDisplayNames[selectedSource.bookSourceUrl]
                                ?: selectedSource.bookSourceName,
                            url = selectedSource.bookSourceUrl,
                        )
                    }
                    item(
                        key = "kind-chips:${selectedSource.bookSourceUrl}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            urlKinds.forEach { kind ->
                                FilterChip(
                                    selected = kind.title == selectedKind?.title,
                                    onClick = { selectedKindTitle = kind.title },
                                    label = {
                                        AppText(uiState.kindDisplayNames[kind.title] ?: kind.title)
                                    },
                                )
                            }
                            selectedKind?.let { kind ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        onOpenExploreShow(
                                            kind.title,
                                            selectedSource.bookSourceUrl,
                                            kind.url,
                                        )
                                    },
                                    label = { AppText("Xem thêm") },
                                    trailingIcon = {
                                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                    if (previewState.isLoading && previewState.books.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppContainedLoadingIndicator()
                            }
                        }
                    }
                    if (!previewState.isLoading && previewState.books.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyMessage(
                                message = previewState.errorMsg?.lineSequence()?.firstOrNull()
                                    ?: stringResource(R.string.explore_empty),
                                buttonText = stringResource(
                                    if (browserRecoveryAvailable) {
                                        R.string.source_health_open_browser
                                    } else {
                                        R.string.retry
                                    }
                                ),
                                buttonImageVector = if (browserRecoveryAvailable) {
                                    Icons.Default.Public
                                } else {
                                    Icons.Default.Refresh
                                },
                                onButtonClick = {
                                    if (browserRecoveryAvailable) {
                                        viewModel.markBrowserRecovery(selectedSource.bookSourceUrl)
                                        onOpenBrowser(
                                            selectedSource.bookSourceUrl,
                                            uiState.sourceBrowserUrl,
                                        )
                                    } else {
                                        previewViewModel.onIntent(ExploreShowIntent.Refresh)
                                    }
                                },
                                secondaryButtonText = if (browserRecoveryAvailable) {
                                    stringResource(R.string.retry)
                                } else null,
                                secondaryButtonImageVector = Icons.Default.Refresh,
                                onSecondaryButtonClick = if (browserRecoveryAvailable) {
                                    { previewViewModel.onIntent(ExploreShowIntent.Refresh) }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                            )
                        }
                    }
                    items(
                        items = previewState.books,
                        key = { it.book.bookUrl },
                    ) { item ->
                        ExploreBookGridItem(
                            book = item.displayBook,
                            shelfState = item.shelfState,
                            onClick = {
                                onOpenBookInfo(
                                    item.book.name,
                                    item.book.author,
                                    item.book.bookUrl,
                                    item.book.origin,
                                    item.book.coverUrl,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (previewState.books.isNotEmpty() &&
                        !previewState.isRefreshing &&
                        (previewState.isLoading || previewState.errorMsg != null || previewState.isEnd)
                    ) {
                        item(
                            key = "preview-load-more:${selectedSource.bookSourceUrl}:${selectedKind?.title}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            LoadMoreFooter(
                                isLoading = previewState.isLoading,
                                errorMsg = previewState.errorMsg,
                                isEnd = previewState.isEnd,
                                onRetry = {
                                    previewViewModel.onIntent(ExploreShowIntent.LoadMore)
                                },
                                onLoadMore = {
                                    previewViewModel.onIntent(ExploreShowIntent.ForceLoadNext)
                                },
                                autoLoad = false,
                            )
                        }
                    }
                    uiState.exploreKinds
                        .filter { it.type != ExploreKind.Type.url }
                        .forEachIndexed { index, kind ->
                            item(
                                key = "kind-action:$index:${kind.title}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                ExploreKindMultiTypeItem(
                                    kind = kind,
                                    sourceUrl = selectedSource.bookSourceUrl,
                                    activity = activity,
                                    onOpenUrl = { url ->
                                        onOpenExploreShow(
                                            kind.title,
                                            selectedSource.bookSourceUrl,
                                            url,
                                        )
                                    },
                                    onRefreshKinds = {
                                        viewModel.refreshExploreKinds(selectedSource)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    isMiuix = isMiuix,
                                    displayNameOverride = uiState.kindDisplayNames[kind.title],
                                    valueOverride = uiState.kindValues[kind.title],
                                    onValueChange = { value ->
                                        viewModel.updateKindValue(
                                            selectedSource.bookSourceUrl,
                                            kind,
                                            value,
                                        )
                                    },
                                    onRunAction = {
                                        viewModel.requestKindAction(
                                            selectedSource.bookSourceUrl,
                                            kind,
                                        )
                                    },
                                )
                            }
                    }
                }
            }
        }
    }

    if (showTranslationShortcut) {
        TranslationShortcutDialog(
            onDismiss = { showTranslationShortcut = false },
            onOpenSettings = {
                showTranslationShortcut = false
                onOpenTranslationSettings()
            },
        )
    }
}

@Composable
private fun ExplorePreviewLoadMoreDetector(
    gridState: LazyGridState,
    sourceUrl: String?,
    exploreUrl: String?,
    bookCount: Int,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState, sourceUrl, exploreUrl, bookCount, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            shouldLoadNextExplorePreviewPage(
                lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo
                    .maxOfOrNull { it.index } ?: -1,
                bookCount = bookCount,
                leadingItemCount = EXPLORE_PREVIEW_LEADING_ITEM_COUNT,
                preloadDistance = EXPLORE_PREVIEW_PRELOAD_DISTANCE,
            )
        }.distinctUntilChanged().collect { shouldLoad ->
            if (shouldLoad) latestOnLoadMore()
        }
    }
}

internal fun shouldLoadNextExplorePreviewPage(
    lastVisibleIndex: Int,
    bookCount: Int,
    leadingItemCount: Int,
    preloadDistance: Int,
): Boolean = bookCount > 0 &&
    lastVisibleIndex >= leadingItemCount + bookCount - preloadDistance.coerceAtLeast(1)

private const val EXPLORE_PREVIEW_LEADING_ITEM_COUNT = 2
private const val EXPLORE_PREVIEW_PRELOAD_DISTANCE = 6

internal fun shouldOfferSourceBrowser(
    errorMessage: String?,
    sourceUrl: String?,
    browserUrl: String?,
): Boolean {
    if (errorMessage.isNullOrBlank() || browserUrl.isNullOrBlank()) return false
    if (sourceUrl?.startsWith("vbook://plugin/", ignoreCase = true) == true) return true
    val normalized = errorMessage.lowercase()
    return listOf(
        "cloudflare",
        "captcha",
        "cf-chl",
        "403",
        "forbidden",
        "access denied",
        "login",
        "\u0111\u0103ng nh\u1eadp",
        "trang ngu\u1ed3n",
    ).any(normalized::contains)
}

@Composable
private fun ExploreSourceHeader(
    name: String,
    url: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppText(
            text = name,
            style = LegadoTheme.typography.headlineSmall,
        )
        AppText(
            text = url,
            style = LegadoTheme.typography.bodyMedium,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslationShortcutDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var provider by remember { mutableStateOf(TranslationConfig.llmProvider) }
    var targetLanguage by remember { mutableStateOf(TranslationConfig.llmTargetLanguage) }
    var translateDynamicUi by remember { mutableStateOf(TranslationConfig.dynamicUiTranslationEnabled) }
    val languages = remember(provider) {
        io.legado.app.domain.model.TranslationConstants.targetLanguagesForProvider(provider)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(R.string.translation_config)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(stringResource(R.string.translation_provider))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TranslationConfig.providerValues.zip(TranslationConfig.providerDisplayNames)
                        .forEach { (value, label) ->
                            FilterChip(
                                selected = provider == value,
                                onClick = {
                                    provider = value
                                    targetLanguage = io.legado.app.domain.model.TranslationConstants
                                        .targetLanguagesForProvider(value)
                                        .firstOrNull()?.first
                                        ?: targetLanguage
                                },
                                label = { AppText(label) },
                            )
                        }
                }
                AppText(stringResource(R.string.llm_target_language))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    languages.forEach { (value, label) ->
                        FilterChip(
                            selected = targetLanguage == value,
                            onClick = { targetLanguage = value },
                            label = { AppText(label) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppText(stringResource(R.string.dynamic_ui_translation))
                    Switch(
                        checked = translateDynamicUi,
                        onCheckedChange = { translateDynamicUi = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                TranslationConfig.llmProvider = provider
                TranslationConfig.llmTargetLanguage = targetLanguage
                TranslationConfig.dynamicUiTranslationEnabled = translateDynamicUi
                onDismiss()
            }) {
                AppText(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenSettings) {
                AppText(stringResource(R.string.translation_config))
            }
        },
    )
}

@Composable
private fun ExploreCategoryTile(
    title: String,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onClick,
            ),
        cornerRadius = 18.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            AppText(
                text = title,
                style = LegadoTheme.typography.titleSmall,
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
private fun ExploreLegacyDiscoveryScreen(
    viewModel: ExploreViewModel = koinViewModel(),
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listItems by remember(uiState.items, uiState.expandedId, uiState.exploreKinds) {
        derivedStateOf { viewModel.buildExploreListItems(uiState) }
    }
    var sourceToDeleteUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceToDelete = remember(sourceToDeleteUrl, uiState.items) {
        uiState.items.firstOrNull { it.bookSourceUrl == sourceToDeleteUrl }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val exploreKindUseCase: ExploreKindUiUseCase = koinInject()

    LaunchedEffect(viewModel, activity, exploreKindUseCase) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExploreEffect.ExecuteKindAction -> {
                    val infoMap = getExploreInfoMap(effect.sourceUrl)
                    exploreKindUseCase.executeAction(
                        action = effect.kind.action,
                        title = effect.kind.title,
                        sourceUrl = effect.sourceUrl,
                        infoMap = infoMap,
                        activity = activity,
                        onRefreshKinds = { viewModel.refreshExploreKinds(effect.sourceUrl) }
                    )
                }
            }
        }
    }

    val stickyHeaderSource by remember(listItems, uiState.items) {
        derivedStateOf {
            val firstIndex = listState.firstVisibleItemIndex
            val item = listItems.getOrNull(firstIndex)
            if (item is ExploreListItem.KindRow) {
                uiState.items.find { it.bookSourceUrl == item.sourceUrl }
            } else {
                null
            }
        }
    }

    val composeEngine = ThemeResolver.isMiuixEngine(composeEngine)

    ListScaffold(
        title = stringResource(R.string.discovery),
        state = uiState,
        subtitle = uiState.selectedGroup.ifEmpty { stringResource(R.string.all) },
        onSearchQueryChange = { viewModel.search(it) },
        onSearchToggle = { viewModel.toggleSearchVisible(it) },
        searchPlaceholder = stringResource(R.string.search),
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.Default.Group) },
                text = stringResource(R.string.all),
                onClick = { viewModel.setGroup(""); dismiss() }
            )
            uiState.groups.forEach { group ->
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Outlined.Label) },
                    text = group,
                    onClick = { viewModel.setGroup(group); dismiss() }
                )
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.items.isEmpty()) {
                EmptyMessage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding()
                        ),
                    messageResId = R.string.explore_empty
                )
                return@Box
            }

            FastScrollLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 120.dp
                )
            ) {
                items(
                    items = listItems,
                    key = { it.key }
                ) { listItem ->
                    when (listItem) {
                        is ExploreListItem.Header -> {
                            val item = listItem.source
                            val isExpanded = uiState.expandedId == item.bookSourceUrl
                        ExploreSourceHeader(
                            modifier = Modifier.animateItem(),
                            item = item,
                            displayName = uiState.sourceDisplayNames[item.bookSourceUrl]
                                ?: item.bookSourceName,
                            isExpanded = isExpanded,
                            loadingKinds = if (isExpanded) uiState.loadingKinds else false,
                            onClick = { viewModel.toggleExpand(item) },
                            onTop = { viewModel.topSource(item) },
                            onEdit = {
                                context.startActivity<BookSourceEditActivity> {
                                    putExtra("sourceUrl", item.bookSourceUrl)
                                }
                            },
                            onSearch = {
                                context.startActivity<SearchActivity> {
                                    putExtra("searchScope", SearchScope(item).toString())
                                }
                            },
                            onLogin = {
                                context.startActivity<SourceLoginActivity> {
                                    putExtra("type", "bookSource")
                                    putExtra("key", item.bookSourceUrl)
                                }
                            },
                            onRefresh = { viewModel.refreshExploreKinds(item) },
                            onDelete = { sourceToDeleteUrl = item.bookSourceUrl },
                            isMiuix = composeEngine
                        )
                        }

                        is ExploreListItem.KindRow -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listItem.rowItems.forEach { (kind, span) ->
                                    ExploreKindMultiTypeItem(
                                        kind = kind,
                                        sourceUrl = listItem.sourceUrl,
                                        onOpenUrl = { url ->
                                            onOpenExploreShow(
                                                uiState.kindDisplayNames[kind.title] ?: kind.title,
                                                listItem.sourceUrl,
                                                url,
                                            )
                                        },
                                        modifier = Modifier.weight(span.toFloat()),
                                        isMiuix = composeEngine,
                                        displayNameOverride = uiState.kindDisplayNames[kind.title],
                                        valueOverride = uiState.kindValues[kind.title],
                                        onValueChange = { value ->
                                            viewModel.updateKindValue(listItem.sourceUrl, kind, value)
                                        },
                                        onRunAction = {
                                            viewModel.requestKindAction(listItem.sourceUrl, kind)
                                        }
                                    )
                                }

                                val totalSpan = listItem.rowItems.sumOf { it.second }
                                if (totalSpan < 6) {
                                    Spacer(
                                        modifier = Modifier.weight((6 - totalSpan).toFloat())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TopFloatingStickyItem(
                item = stickyHeaderSource,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = paddingValues.calculateTopPadding() + 4.dp, start = 8.dp)
            ) { item ->
                TextCard(
                    text = uiState.sourceDisplayNames[item.bookSourceUrl]
                        ?: item.bookSourceName,
                    textStyle = LegadoTheme.typography.labelMediumEmphasized,
                    cornerRadius = 12.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 8.dp,
                    modifier = Modifier.semantics {
                        contentDescription = uiState.sourceDisplayNames[item.bookSourceUrl]
                            ?: item.bookSourceName
                        role = Role.Button
                    },
                    onClick = {
                        scope.launch {
                            val index = listItems.indexOfFirst {
                                it is ExploreListItem.Header && it.source.bookSourceUrl == item.bookSourceUrl
                            }
                            if (index >= 0) listState.animateScrollToItem(index)
                        }
                    }
                )
            }
        }
    }


    AppAlertDialog(
        data = sourceToDelete,
        onDismissRequest = { sourceToDeleteUrl = null },
        title = stringResource(R.string.sure_del),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { source ->
            viewModel.deleteSource(source)
            sourceToDeleteUrl = null
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { sourceToDeleteUrl = null },
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreSourceHeader(
    modifier: Modifier = Modifier,
    item: BookSourcePart,
    displayName: String = item.bookSourceName,
    isExpanded: Boolean,
    loadingKinds: Boolean,
    onClick: () -> Unit,
    onTop: () -> Unit,
    onEdit: () -> Unit,
    onSearch: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    isMiuix: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (isExpanded) 90f else 0f, label = "rotation")
    val expandActionLabel = stringResource(if (isExpanded) R.string.collapse else R.string.expand)
    val loadingLabel = stringResource(R.string.loading)
    val moreMenuLabel = stringResource(R.string.more_menu)

    val containerColor by animateColorAsState(
        targetValue = if (isExpanded)
            if (isMiuix) MiuixTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer
        else
            if (isMiuix) MiuixTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isExpanded)
            if (isMiuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
        else
            if (isMiuix) MiuixTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        cornerRadius = 12.dp,
        containerColor = containerColor,
    ) {
        ListItem(
            modifier = Modifier
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = expandActionLabel,
                    onLongClickLabel = moreMenuLabel,
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = displayName
                    role = Role.Button
                    if (loadingKinds) {
                        stateDescription = loadingLabel
                    }
                }
                .fillMaxWidth(),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            headlineContent = {
                AppText(
                    text = displayName,
                    style = LegadoTheme.typography.titleMedium,
                    color = contentColor
                )
            },
            trailingContent = {
                AnimatedContent(
                    targetState = loadingKinds,
                    label = "LoadingSwitch"
                ) { loading ->
                    if (loading) {
                        AppContainedLoadingIndicator(
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier
                                .rotate(rotation)
                                .size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    PillHeaderDivider(title = displayName)
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.VerticalAlignTop) },
                        text = stringResource(R.string.to_top),
                        onClick = { onTop(); showMenu = false }
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
                        text = stringResource(R.string.edit),
                        onClick = { onEdit(); showMenu = false }
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Search) },
                        text = stringResource(R.string.search),
                        onClick = { onSearch(); showMenu = false }
                    )
                    if (item.hasLoginUrl) {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                            text = stringResource(R.string.login),
                            onClick = { onLogin(); showMenu = false }
                        )
                    }
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Refresh) },
                        text = stringResource(R.string.refresh),
                        onClick = { onRefresh(); showMenu = false }
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = {
                            MenuItemIcon(
                                Icons.Default.Delete,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        text = stringResource(R.string.delete),
                        color = LegadoTheme.colorScheme.error,
                        onClick = { onDelete(); showMenu = false }
                    )
                }
            }
        )
    }
}
