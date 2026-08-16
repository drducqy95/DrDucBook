package io.legado.app.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.domain.model.IconSlot
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.BrowserSourceContext
import io.legado.app.constant.FeatureFlags
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.image.sourceIcon.SourceIcon
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

@Composable
fun BrowserScreen(
    state: BrowserUiState,
    onIntent: (BrowserIntent) -> Unit,
    onBackClick: () -> Unit,
    webContent: @Composable () -> Unit,
) {
    val activeTab = state.tabs.firstOrNull { tab -> tab.id == state.activeTabId }
    val activeTitle = when {
        state.isHomeMode || activeTab?.isHome == true -> stringResource(R.string.browser_home)
        else -> activeTab?.title?.ifBlank { stringResource(R.string.browser) }
            ?: stringResource(R.string.browser)
    }
    AppScaffold(
        topBar = {
            GlassTopAppBar(
                title = activeTitle,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.browser_exit),
                        personalizationSlot = IconSlot.TOOLBAR_BROWSER_EXIT,
                        onClick = { onIntent(BrowserIntent.ExitBrowser) },
                    )
                    if (!state.isHomeMode && FeatureFlags.browserPageTranslation) {
                        TopBarActionButton(
                            imageVector = Icons.Default.Translate,
                            contentDescription = stringResource(
                                when (state.translationState) {
                                    BrowserPageTranslationState.TRANSLATED -> R.string.browser_show_original
                                    else -> R.string.browser_translate_page
                                }
                            ),
                            onClick = { onIntent(BrowserIntent.TogglePageTranslation) },
                        )
                    }
                    TopBarActionButton(
                        imageVector = Icons.Default.Tab,
                        contentDescription = stringResource(R.string.browser_tabs_count, state.tabs.size),
                        onClick = { onIntent(BrowserIntent.ShowTabs) },
                    )
                    Box {
                        TopBarActionButton(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_menu),
                            personalizationSlot = IconSlot.TOOLBAR_MORE,
                            onClick = { onIntent(BrowserIntent.ShowMenu) },
                        )
                        BrowserMoreMenu(state, onIntent)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            AppTextField(
                value = state.addressBarText,
                onValueChange = { onIntent(BrowserIntent.ChangeAddress(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                label = stringResource(R.string.browser_address),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onIntent(BrowserIntent.NavigateAddress) }),
            )
            state.activeSourceContext?.let { sourceContext ->
                BrowserSourceIndicator(sourceContext, onClick = { onIntent(BrowserIntent.ShowMenu) })
            }
            if (!state.isHomeMode &&
                (state.isLoading || state.translationState == BrowserPageTranslationState.TRANSLATING)
            ) {
                LinearProgressIndicator(
                    progress = { (state.progress.coerceAtLeast(5)) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (state.isHomeMode) {
                    BrowserHomeContent(state, onIntent)
                } else {
                    webContent()
                }
                state.errorMessage?.let { message ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        color = LegadoTheme.colorScheme.errorContainer,
                    ) {
                        AppText(
                            text = message,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = LegadoTheme.colorScheme.onErrorContainer,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            BrowserNavigationBar(state, onIntent)
        }
    }

    if (state.showTabs) {
        BrowserTabsSheet(state, onIntent)
    }
    state.bookmarkEditor?.let { editor ->
        BrowserBookmarkEditorSheet(editor, onIntent)
    }
}

@Composable
private fun BrowserSourceIndicator(
    sourceContext: BrowserSourceContext,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = LegadoTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceIcon(
                path = sourceContext.iconPath,
                sourceOrigin = sourceContext.sourceUrl,
                modifier = Modifier.size(22.dp),
                placeholderIcon = {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxSize(0.72f),
                    )
                },
            )
            AppText(
                text = sourceContext.name,
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AppText(
                text = stringResource(R.string.source_health),
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BrowserHomeContent(
    state: BrowserUiState,
    onIntent: (BrowserIntent) -> Unit,
) {
    val recentTabs = state.tabs.filter { tab -> !tab.isHome && isSafeBrowserUrl(tab.url) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "health-summary") {
            BrowserHealthSummaryCard(state.home.healthSummary, onIntent)
        }
        item(key = "home-search") {
            AppTextField(
                value = state.home.query,
                onValueChange = { onIntent(BrowserIntent.ChangeHomeQuery(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.browser_home_search),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
            )
        }
        item(key = "bookmarks-title") {
            BrowserHomeSectionTitle(stringResource(R.string.browser_bookmarks))
        }
        if (state.home.manualBookmarks.isEmpty()) {
            item(key = "bookmarks-empty") {
                BrowserHomeEmptyText(stringResource(R.string.browser_no_bookmarks))
            }
        } else {
            items(
                items = state.home.manualBookmarks,
                key = { bookmark -> bookmark.id },
            ) { bookmark ->
                BrowserBookmarkItem(bookmark, onIntent)
            }
        }
        item(key = "source-shortcuts-title") {
            BrowserHomeSectionTitle(stringResource(R.string.browser_source_shortcuts))
        }
        if (state.home.sourceShortcuts.isEmpty()) {
            item(key = "source-shortcuts-empty") {
                BrowserHomeEmptyText(stringResource(R.string.browser_no_source_shortcuts))
            }
        } else {
            item(key = "source-shortcuts") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = state.home.sourceShortcuts,
                        key = { source -> source.sourceUrl },
                    ) { source ->
                        BrowserSourceShortcutCard(source, onIntent)
                    }
                }
            }
        }
        item(key = "recent-tabs-title") {
            BrowserHomeSectionTitle(stringResource(R.string.browser_recent_tabs))
        }
        if (recentTabs.isEmpty()) {
            item(key = "recent-tabs-empty") {
                BrowserHomeEmptyText(stringResource(R.string.browser_no_recent_tabs))
            }
        } else {
            items(recentTabs.take(6), key = { tab -> tab.id }) { tab ->
                BrowserRecentTabItem(tab, onIntent)
            }
        }
    }
}

@Composable
private fun BrowserBookmarkItem(
    bookmark: BrowserBookmarkUi,
    onIntent: (BrowserIntent) -> Unit,
) {
    ListItem(
        headlineContent = {
            AppText(
                text = bookmark.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                AppText(
                    text = bookmark.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                AppText(
                    text = bookmark.folder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Icon(Icons.Default.Bookmark, contentDescription = null)
        },
        trailingContent = {
            IconButton(onClick = { onIntent(BrowserIntent.EditBookmark(bookmark.id)) }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onIntent(BrowserIntent.OpenShortcut(bookmark.url)) },
    )
}

@Composable
private fun BrowserHealthSummaryCard(
    summary: BrowserSourceHealthSummaryUi,
    onIntent: (BrowserIntent) -> Unit,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onIntent(BrowserIntent.OpenSourceHealth) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                )
                AppText(
                    text = stringResource(R.string.source_health),
                    style = LegadoTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                AppText(
                    text = stringResource(R.string.source_health_check_now),
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrowserHealthMetric(stringResource(R.string.all), summary.total, Modifier.weight(1f))
                BrowserHealthMetric(
                    stringResource(R.string.source_health_healthy),
                    summary.healthy,
                    Modifier.weight(1f),
                )
                BrowserHealthMetric(
                    stringResource(R.string.source_health_error),
                    summary.needsAttention,
                    Modifier.weight(1f),
                )
                BrowserHealthMetric(
                    stringResource(R.string.source_health_auth_required),
                    summary.authRequired,
                    Modifier.weight(1f),
                )
                BrowserHealthMetric(
                    stringResource(R.string.source_health_captcha_required),
                    summary.captchaRequired,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BrowserHealthMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(
            text = value.toString(),
            style = LegadoTheme.typography.titleMedium,
            maxLines = 1,
        )
        AppText(
            text = label,
            style = LegadoTheme.typography.labelSmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BrowserSourceShortcutCard(
    source: BrowserSourceShortcutUi,
    onIntent: (BrowserIntent) -> Unit,
) {
    NormalCard(
        modifier = Modifier.width(156.dp),
        onClick = { onIntent(BrowserIntent.OpenShortcut(source.homeUrl)) },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SourceIcon(
                    path = source.iconPath,
                    sourceOrigin = source.sourceUrl,
                    modifier = Modifier.size(36.dp),
                    placeholderIcon = {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = LegadoTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxSize(0.68f),
                        )
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onIntent(BrowserIntent.ToggleSourcePinned(source.sourceKey)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(
                            if (source.pinned) R.string.browser_source_unpin else R.string.browser_source_pin
                        ),
                        tint = if (source.pinned) {
                            LegadoTheme.colorScheme.primary
                        } else {
                            LegadoTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = { onIntent(BrowserIntent.ToggleSourceHidden(source.sourceKey)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = stringResource(R.string.browser_source_hide),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppText(
                text = source.name,
                style = LegadoTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(42.dp),
            )
            source.group?.takeIf(String::isNotBlank)?.let { group ->
                AppText(
                    text = group,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            source.healthStatus?.let { status ->
                AppText(
                    text = browserHealthStatusLabel(status),
                    style = LegadoTheme.typography.labelSmall,
                    color = browserHealthStatusColor(status),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BrowserRecentTabItem(
    tab: BrowserTabUi,
    onIntent: (BrowserIntent) -> Unit,
) {
    ListItem(
        headlineContent = {
            AppText(
                text = tab.title.ifBlank { tab.url },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            AppText(
                text = tab.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(Icons.Default.Language, contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onIntent(BrowserIntent.SwitchTab(tab.id)) },
    )
}

@Composable
private fun BrowserHomeSectionTitle(text: String) {
    AppText(
        text = text,
        style = LegadoTheme.typography.titleSmall,
        color = LegadoTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BrowserHomeEmptyText(text: String) {
    AppText(
        text = text,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        style = LegadoTheme.typography.bodyMedium,
        color = LegadoTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BrowserNavigationBar(
    state: BrowserUiState,
    onIntent: (BrowserIntent) -> Unit,
) {
    val activeTab = state.tabs.firstOrNull { tab -> tab.id == state.activeTabId }
    val hasActivePage = activeTab?.url?.takeIf(::isSafeBrowserUrl) != null && !state.isHomeMode
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrowserNavigationButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = stringResource(R.string.browser_back),
                enabled = !state.isHomeMode && state.canGoBack,
                onClick = { onIntent(BrowserIntent.GoBack) },
            )
            BrowserNavigationButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                description = stringResource(R.string.browser_forward),
                enabled = !state.isHomeMode && state.canGoForward,
                onClick = { onIntent(BrowserIntent.GoForward) },
            )
            BrowserNavigationButton(
                icon = if (state.isLoading) Icons.Default.Stop else Icons.Default.Refresh,
                description = stringResource(if (state.isLoading) R.string.stop else R.string.refresh),
                enabled = hasActivePage,
                onClick = { onIntent(BrowserIntent.ReloadOrStop) },
            )
            BrowserNavigationButton(
                icon = Icons.Default.Home,
                description = stringResource(R.string.home),
                onClick = { onIntent(BrowserIntent.GoHome) },
            )
            BrowserNavigationButton(
                icon = Icons.Default.Add,
                description = stringResource(R.string.browser_new_tab),
                onClick = { onIntent(BrowserIntent.AddTab) },
            )
        }
    }
}

@Composable
private fun BrowserNavigationButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun BrowserMoreMenu(
    state: BrowserUiState,
    onIntent: (BrowserIntent) -> Unit,
) {
    val activeUrl = state.tabs
        .firstOrNull { tab -> tab.id == state.activeTabId }
        ?.url
        ?.takeIf(::isSafeBrowserUrl)
    val activeSource = state.activeSourceContext
    DropdownMenu(
        expanded = state.showMenu,
        onDismissRequest = { onIntent(BrowserIntent.DismissOverlays) },
    ) {
        if (activeUrl != null) {
            BrowserMenuItem(
                icon = Icons.Default.BookmarkAdd,
                text = stringResource(R.string.browser_add_bookmark),
                onClick = { onIntent(BrowserIntent.ShowAddBookmark) },
            )
            BrowserMenuItem(
                icon = Icons.Default.OpenInBrowser,
                text = stringResource(R.string.browser_open_external),
                onClick = { onIntent(BrowserIntent.OpenExternal) },
            )
            BrowserMenuItem(
                icon = Icons.Default.Share,
                text = stringResource(R.string.share),
                onClick = { onIntent(BrowserIntent.SharePage) },
            )
            BrowserMenuItem(
                icon = Icons.Default.ContentCopy,
                text = stringResource(R.string.copy_link),
                onClick = { onIntent(BrowserIntent.CopyLink) },
            )
            if (!state.isHomeMode) {
                BrowserMenuItem(
                    icon = Icons.Default.Language,
                    text = stringResource(R.string.browser_desktop_mode),
                    trailing = { Checkbox(checked = state.isDesktopMode, onCheckedChange = null) },
                    onClick = { onIntent(BrowserIntent.ToggleDesktopMode) },
                )
            }
        }
        BrowserMenuItem(
            icon = Icons.Default.History,
            text = stringResource(R.string.browser_history),
            onClick = { onIntent(BrowserIntent.ShowTabs) },
        )
        BrowserMenuItem(
            icon = Icons.Default.CheckCircle,
            text = stringResource(R.string.source_health),
            onClick = { onIntent(BrowserIntent.OpenSourceHealth) },
        )
        if (activeSource != null) {
            HorizontalDivider()
            if (!activeSource.loginUrl.isNullOrBlank()) {
                BrowserMenuItem(
                    icon = Icons.AutoMirrored.Filled.Login,
                    text = stringResource(R.string.login),
                    onClick = { onIntent(BrowserIntent.OpenSourceLogin) },
                )
            }
            BrowserMenuItem(
                icon = Icons.Default.CheckCircle,
                text = stringResource(R.string.browser_login_complete),
                onClick = { onIntent(BrowserIntent.ConfirmLoginAndProbe) },
            )
            BrowserMenuItem(
                icon = Icons.Default.Edit,
                text = stringResource(R.string.edit),
                onClick = { onIntent(BrowserIntent.OpenSourceEdit) },
            )
            BrowserMenuItem(
                icon = Icons.Default.PushPin,
                text = stringResource(
                    if (state.activeSourcePreference.pinned) {
                        R.string.browser_source_unpin
                    } else {
                        R.string.browser_source_pin
                    }
                ),
                onClick = { onIntent(BrowserIntent.ToggleActiveSourcePinned) },
            )
            BrowserMenuItem(
                icon = Icons.Default.VisibilityOff,
                text = stringResource(
                    if (state.activeSourcePreference.hidden) {
                        R.string.browser_source_show
                    } else {
                        R.string.browser_source_hide
                    }
                ),
                onClick = { onIntent(BrowserIntent.ToggleActiveSourceHidden) },
            )
            BrowserMenuItem(
                icon = Icons.Default.Delete,
                text = stringResource(R.string.cookie),
                onClick = { onIntent(BrowserIntent.ClearSourceCookie) },
            )
        }
        HorizontalDivider()
        BrowserMenuItem(
            icon = Icons.Default.Home,
            text = stringResource(R.string.browser_open_app),
            onClick = { onIntent(BrowserIntent.ExitBrowser) },
        )
    }
}

@Composable
private fun BrowserMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { AppText(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = trailing,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserBookmarkEditorSheet(
    editor: BrowserBookmarkEditorUi,
    onIntent: (BrowserIntent) -> Unit,
) {
    var title by remember(editor.id, editor.url) { mutableStateOf(editor.title) }
    var url by remember(editor.id, editor.url) { mutableStateOf(editor.url) }
    var folder by remember(editor.id, editor.url) { mutableStateOf(editor.folder) }
    ModalBottomSheet(onDismissRequest = { onIntent(BrowserIntent.DismissBookmarkEditor) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = stringResource(R.string.browser_edit_bookmark),
                style = LegadoTheme.typography.titleMedium,
            )
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.browser_bookmark_title),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            AppTextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.browser_bookmark_url),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            AppTextField(
                value = folder,
                onValueChange = { folder = it },
                label = stringResource(R.string.browser_bookmark_folder),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                editor.id?.let { id ->
                    TextButton(onClick = { onIntent(BrowserIntent.DeleteBookmark(id)) }) {
                        AppText(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = { onIntent(BrowserIntent.DismissBookmarkEditor) }) {
                    AppText(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onIntent(
                            BrowserIntent.SaveBookmark(
                                id = editor.id,
                                title = title,
                                url = url,
                                folder = folder,
                            )
                        )
                    }
                ) {
                    AppText(stringResource(R.string.save))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTabsSheet(
    state: BrowserUiState,
    onIntent: (BrowserIntent) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { onIntent(BrowserIntent.DismissOverlays) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.browser_tabs_count, state.tabs.size),
                style = LegadoTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onIntent(BrowserIntent.AddTab) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.browser_new_tab))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.tabs, key = { tab -> tab.id }) { tab ->
                ListItem(
                    headlineContent = {
                        AppText(
                            text = browserTabTitle(tab),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        AppText(
                            if (tab.isHome) stringResource(R.string.browser_home) else tab.url,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onIntent(BrowserIntent.CloseTab(tab.id)) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.browser_close_tab),
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (tab.id == state.activeTabId) {
                            LegadoTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIntent(BrowserIntent.SwitchTab(tab.id)) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun browserTabTitle(tab: BrowserTabUi): String =
    if (tab.isHome) stringResource(R.string.browser_home) else tab.title.ifBlank { tab.url }

@Composable
private fun browserHealthStatusLabel(status: BookSourceHealthStatus): String = stringResource(
    when (status) {
        BookSourceHealthStatus.HEALTHY -> R.string.source_health_healthy
        BookSourceHealthStatus.DEGRADED -> R.string.source_health_degraded
        BookSourceHealthStatus.AUTH_REQUIRED -> R.string.source_health_auth_required
        BookSourceHealthStatus.CAPTCHA_REQUIRED -> R.string.source_health_captcha_required
        BookSourceHealthStatus.RATE_LIMITED -> R.string.source_health_rate_limited
        BookSourceHealthStatus.BROKEN_RULE -> R.string.source_health_broken_rule
        BookSourceHealthStatus.NETWORK_ERROR -> R.string.source_health_network_error
        BookSourceHealthStatus.TLS_ERROR -> R.string.source_health_tls_error
        BookSourceHealthStatus.CONTENT_EMPTY -> R.string.source_health_content_empty
        BookSourceHealthStatus.MEDIA_ERROR -> R.string.source_health_media_error
        BookSourceHealthStatus.UNSUPPORTED -> R.string.source_health_unsupported
        BookSourceHealthStatus.STALE -> R.string.source_health_stale
        BookSourceHealthStatus.HTTP_ERROR -> R.string.source_health_http_error
        BookSourceHealthStatus.UNKNOWN_OFFLINE -> R.string.source_health_offline
    }
)

@Composable
private fun browserHealthStatusColor(status: BookSourceHealthStatus) = when (status) {
    BookSourceHealthStatus.HEALTHY -> LegadoTheme.colorScheme.primary
    BookSourceHealthStatus.DEGRADED,
    BookSourceHealthStatus.STALE,
    BookSourceHealthStatus.UNSUPPORTED -> LegadoTheme.colorScheme.tertiary
    else -> LegadoTheme.colorScheme.error
}
