package io.legado.app.ui.book.source.health

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.SourceKeyType
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.image.sourceIcon.SourceIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun SourceHealthRouteScreen(
    sourceUrl: String?,
    onBackClick: () -> Unit,
    onOpenBrowser: (String, String?) -> Unit,
    onOpenEdit: (String, SourceKeyType) -> Unit,
    viewModel: SourceHealthViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnOpenBrowser by rememberUpdatedState(onOpenBrowser)
    val currentOnOpenEdit by rememberUpdatedState(onOpenEdit)

    LaunchedEffect(viewModel, sourceUrl) {
        viewModel.onIntent(SourceHealthIntent.Initialize(sourceUrl))
    }
    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SourceHealthEffect.ShowMessage -> context.toastOnUi(effect.message)
                is SourceHealthEffect.OpenBrowser -> currentOnOpenBrowser(
                    effect.sourceUrl,
                    effect.initialUrl,
                )
                is SourceHealthEffect.OpenEdit -> currentOnOpenEdit(
                    effect.sourceUrl,
                    effect.sourceType,
                )
            }
        }
    }
    SourceHealthScreen(state, viewModel::onIntent, onBackClick)
}

@Composable
fun SourceHealthScreen(
    state: SourceHealthUiState,
    onIntent: (SourceHealthIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.source_health),
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.Sync,
                        contentDescription = stringResource(R.string.source_health_check_now),
                        onClick = { onIntent(SourceHealthIntent.CheckNow) },
                    )
                    TopBarActionButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = { onIntent(SourceHealthIntent.Refresh) },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                SourceHealthSummaryRow(state.summary)
            }
            item(key = "search") {
                SearchBar(
                    query = state.query,
                    onQueryChange = { onIntent(SourceHealthIntent.ChangeQuery(it)) },
                    placeholder = stringResource(R.string.source_health_search),
                    autoFocus = false,
                )
            }
            item(key = "filters") {
                SourceHealthFilterRow(state.filter, onIntent)
            }
            if (state.recentRuns.isNotEmpty()) {
                item(key = "recent-title") {
                    SourceHealthSectionTitle(stringResource(R.string.source_health_recent_runs))
                }
                item(key = "recent-runs") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.recentRuns, key = SourceHealthRunUi::id) { run ->
                            RecentRunCard(
                                run = run,
                                onClick = {
                                    onIntent(SourceHealthIntent.SelectSource(run.sourceUrl))
                                },
                            )
                        }
                    }
                }
            }
            item(key = "sources-title") {
                SourceHealthSectionTitle(
                    stringResource(R.string.source_health_sources_count, state.items.size)
                )
            }
            when {
                state.loading && state.items.isEmpty() -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppCircularProgressIndicator()
                    }
                }
                state.items.isEmpty() -> item(key = "empty") {
                    SourceHealthEmptyText(stringResource(R.string.source_health_no_sources))
                }
                else -> items(
                    items = state.items,
                    key = SourceHealthItemUi::sourceUrl,
                    contentType = { "source-health-source" },
                ) { item ->
                    SourceHealthSourceCard(
                        item = item,
                        onClick = { onIntent(SourceHealthIntent.SelectSource(item.sourceUrl)) },
                    )
                }
            }
        }
    }

    SourceHealthDetailSheet(
        state = state,
        onIntent = onIntent,
    )
}

@Composable
private fun SourceHealthSummaryRow(summary: SourceHealthSummaryUi) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("total") {
            SourceHealthMetricCard(
                label = stringResource(R.string.source_health_total),
                value = summary.total,
                icon = Icons.Default.Language,
                color = LegadoTheme.colorScheme.primary,
            )
        }
        item("checked") {
            SourceHealthMetricCard(
                label = stringResource(R.string.source_health_checked),
                value = summary.checked,
                icon = Icons.Default.CheckCircle,
                color = LegadoTheme.colorScheme.primary,
            )
        }
        item("attention") {
            SourceHealthMetricCard(
                label = stringResource(R.string.source_health_attention),
                value = summary.needsAttention,
                icon = Icons.Default.WarningAmber,
                color = LegadoTheme.colorScheme.error,
            )
        }
        item("auth") {
            SourceHealthMetricCard(
                label = stringResource(R.string.source_health_auth_required),
                value = summary.authRequired,
                icon = Icons.AutoMirrored.Filled.Login,
                color = LegadoTheme.colorScheme.tertiary,
            )
        }
        item("captcha") {
            SourceHealthMetricCard(
                label = stringResource(R.string.source_health_captcha_required),
                value = summary.captchaRequired,
                icon = Icons.Default.WarningAmber,
                color = LegadoTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun SourceHealthMetricCard(
    label: String,
    value: Int,
    icon: ImageVector,
    color: Color,
) {
    NormalCard(modifier = Modifier.width(132.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
            AppText(
                text = value.toString(),
                style = LegadoTheme.typography.titleLarge,
                maxLines = 1,
            )
            AppText(
                text = label,
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SourceHealthFilterRow(
    selectedFilter: SourceHealthFilter,
    onIntent: (SourceHealthIntent) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = SourceHealthFilter.entries,
            key = SourceHealthFilter::name,
        ) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onIntent(SourceHealthIntent.SetFilter(filter)) },
                label = { AppText(filterLabel(filter)) },
            )
        }
    }
}

@Composable
private fun SourceHealthSourceCard(
    item: SourceHealthItemUi,
    onClick: () -> Unit,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SourceIcon(
                    path = item.iconPath,
                    sourceOrigin = item.sourceUrl,
                    modifier = Modifier.size(40.dp),
                    placeholderIcon = {
                        Icon(
                            sourceIcon(item),
                            contentDescription = null,
                            tint = LegadoTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxSize(0.68f),
                        )
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = item.sourceName,
                        style = LegadoTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = item.sourceUrl,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppText(
                    statusLabel(item.status),
                    style = LegadoTheme.typography.labelMedium,
                    color = statusColor(item.status),
                    maxLines = 1,
                )
            }
            AppText(
                text = item.sourceMetaLine(),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.message?.takeIf(String::isNotBlank)?.let { message ->
                AppText(
                    text = message,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecentRunCard(
    run: SourceHealthRunUi,
    onClick: () -> Unit,
) {
    NormalCard(
        modifier = Modifier.width(220.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = statusColor(run.healthStatus),
                    modifier = Modifier.size(20.dp),
                )
                AppText(
                    text = statusLabel(run.healthStatus),
                    style = LegadoTheme.typography.labelMedium,
                    color = statusColor(run.healthStatus),
                    maxLines = 1,
                )
            }
            AppText(
                text = run.sourceName,
                style = LegadoTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(40.dp),
            )
            AppText(
                text = run.runMetaLine(),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceHealthDetailSheet(
    state: SourceHealthUiState,
    onIntent: (SourceHealthIntent) -> Unit,
) {
    AppModalBottomSheet(
        data = state.selectedSource,
        onDismissRequest = { onIntent(SourceHealthIntent.SelectSource(null)) },
        title = state.selectedSource?.sourceName,
    ) { source ->
        val browserUrl = source.loginUrl ?: source.homeUrl
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SourceHealthSheetHeader(source)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        onIntent(SourceHealthIntent.CheckSource(source.sourceUrl))
                    },
                    enabled = !state.checking,
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.source_health_check_source))
                }
                OutlinedButton(
                    onClick = {
                        onIntent(
                            SourceHealthIntent.OpenBrowser(
                                sourceUrl = source.sourceUrl,
                                initialUrl = browserUrl,
                            )
                        )
                    },
                    enabled = browserUrl != null,
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.source_health_open_browser))
                }
                OutlinedButton(
                    onClick = {
                        onIntent(
                            SourceHealthIntent.OpenEdit(
                                sourceUrl = source.sourceUrl,
                                sourceType = source.sourceType,
                            )
                        )
                    },
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_source))
                }
            }
            SourceHealthRunHistory(state, onIntent)
        }
    }
}

@Composable
private fun SourceHealthSheetHeader(source: SourceHealthItemUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceIcon(
            path = source.iconPath,
            sourceOrigin = source.sourceUrl,
            modifier = Modifier.size(44.dp),
            placeholderIcon = {
                Icon(
                    sourceIcon(source),
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.68f),
                )
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = source.sourceUrl,
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = source.sourceMetaLine(),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppText(
            text = statusLabel(source.status),
            style = LegadoTheme.typography.labelMedium,
            color = statusColor(source.status),
            maxLines = 1,
        )
    }
    source.message?.takeIf(String::isNotBlank)?.let { message ->
        AppText(
            text = message,
            style = LegadoTheme.typography.bodySmall,
            color = LegadoTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SourceHealthRunHistory(
    state: SourceHealthUiState,
    onIntent: (SourceHealthIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceHealthSectionTitle(stringResource(R.string.source_health_run_history))
        if (state.selectedRuns.isEmpty()) {
            SourceHealthEmptyText(stringResource(R.string.source_health_no_runs))
        } else {
            state.selectedRuns.forEach { run ->
                SourceHealthRunRow(
                    run = run,
                    selected = state.selectedRunId == run.id,
                    onClick = { onIntent(SourceHealthIntent.SelectRun(run.id)) },
                )
            }
        }
        SourceHealthSectionTitle(stringResource(R.string.source_health_stages))
        if (state.selectedStages.isEmpty()) {
            SourceHealthEmptyText(stringResource(R.string.source_health_no_stages))
        } else {
            state.selectedStages.forEach { stage ->
                SourceHealthStageRow(stage)
            }
        }
    }
}

@Composable
private fun SourceHealthRunRow(
    run: SourceHealthRunUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = if (selected) {
            LegadoTheme.colorScheme.secondaryContainer
        } else {
            LegadoTheme.colorScheme.surfaceContainer
        },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = run.profile.name,
                    style = LegadoTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                AppText(
                    text = statusLabel(run.healthStatus),
                    style = LegadoTheme.typography.labelMedium,
                    color = statusColor(run.healthStatus),
                    maxLines = 1,
                )
            }
            AppText(
                text = run.runMetaLine(),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            run.message?.takeIf(String::isNotBlank)?.let { message ->
                AppText(
                    text = message,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SourceHealthStageRow(stage: SourceHealthStageUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (stage.status == SourceCheckStageStatus.PASSED) {
                Icons.Default.CheckCircle
            } else {
                Icons.Default.WarningAmber
            },
            contentDescription = null,
            tint = stageStatusColor(stage.status),
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = stage.stageKey,
                style = LegadoTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = stage.stageMetaLine(),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            stage.message?.takeIf(String::isNotBlank)?.let { message ->
                AppText(
                    text = message,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SourceHealthSectionTitle(text: String) {
    AppText(
        text = text,
        style = LegadoTheme.typography.titleSmall,
        color = LegadoTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SourceHealthEmptyText(text: String) {
    AppText(
        text = text,
        style = LegadoTheme.typography.bodyMedium,
        color = LegadoTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun SourceHealthItemUi.sourceMetaLine(): String = listOfNotNull(
    sourceTypeLabel(this),
    sourceGroup?.takeIf(String::isNotBlank),
    if (enabled) stringResource(R.string.source_health_enabled) else stringResource(R.string.source_health_disabled),
    if (hasLoginUrl) stringResource(R.string.source_health_has_login) else null,
    lastCheckedLabel(lastChecked),
    latencyMs?.let { stringResource(R.string.source_health_latency_ms, it) },
).joinToString(" | ")

@Composable
private fun SourceHealthRunUi.runMetaLine(): String = listOfNotNull(
    runStatus.name.toReadableEnumName(),
    stringResource(R.string.source_health_started, lastCheckedLabel(startedAt)),
    latencyMs?.let { stringResource(R.string.source_health_latency_ms, it) },
    stringResource(
        R.string.source_health_stage_counts,
        passedStageCount,
        failedStageCount,
        skippedStageCount,
        stageCount,
    ),
).joinToString(" | ")

@Composable
private fun SourceHealthStageUi.stageMetaLine(): String = listOfNotNull(
    status.name.toReadableEnumName(),
    latencyMs?.let { stringResource(R.string.source_health_latency_ms, it) },
    httpStatus?.let { stringResource(R.string.source_health_http_status, it) },
    failureStep?.takeIf(String::isNotBlank),
).joinToString(" | ")

@Composable
private fun lastCheckedLabel(timeMillis: Long): String = if (timeMillis > 0L) {
    DateUtils.getRelativeTimeSpanString(timeMillis).toString()
} else {
    stringResource(R.string.source_health_not_checked)
}

@Composable
private fun sourceTypeLabel(item: SourceHealthItemUi): String = when {
    item.isVbook -> stringResource(R.string.source_health_type_vbook)
    item.sourceType == SourceKeyType.RSS -> stringResource(R.string.source_health_type_rss)
    else -> stringResource(R.string.source_health_type_book)
}

private fun sourceIcon(item: SourceHealthItemUi): ImageVector = when {
    item.isVbook -> Icons.Default.AutoStories
    item.sourceType == SourceKeyType.RSS -> Icons.Default.RssFeed
    else -> Icons.Default.Language
}

@Composable
private fun filterLabel(filter: SourceHealthFilter): String = stringResource(
    when (filter) {
        SourceHealthFilter.ALL -> R.string.all
        SourceHealthFilter.HEALTHY -> R.string.source_health_healthy
        SourceHealthFilter.ERROR -> R.string.source_health_error
        SourceHealthFilter.AUTH_REQUIRED -> R.string.source_health_auth_required
        SourceHealthFilter.CAPTCHA_REQUIRED -> R.string.source_health_captcha_required
    }
)

@Composable
private fun statusLabel(status: BookSourceHealthStatus): String = stringResource(
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
private fun statusColor(status: BookSourceHealthStatus): Color = when (status) {
    BookSourceHealthStatus.HEALTHY -> LegadoTheme.colorScheme.primary
    BookSourceHealthStatus.DEGRADED,
    BookSourceHealthStatus.STALE,
    BookSourceHealthStatus.UNKNOWN_OFFLINE,
    BookSourceHealthStatus.UNSUPPORTED -> LegadoTheme.colorScheme.tertiary
    else -> LegadoTheme.colorScheme.error
}

@Composable
private fun stageStatusColor(status: SourceCheckStageStatus): Color = when (status) {
    SourceCheckStageStatus.PASSED -> LegadoTheme.colorScheme.primary
    SourceCheckStageStatus.RUNNING,
    SourceCheckStageStatus.SKIPPED -> LegadoTheme.colorScheme.tertiary
    SourceCheckStageStatus.FAILED,
    SourceCheckStageStatus.CANCELED -> LegadoTheme.colorScheme.error
}

private fun String.toReadableEnumName(): String = lowercase(Locale.ROOT)
    .replace('_', ' ')
    .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
