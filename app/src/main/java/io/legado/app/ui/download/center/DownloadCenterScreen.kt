package io.legado.app.ui.download.center

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.DownloadCenterItem
import io.legado.app.domain.model.DownloadCenterAction
import io.legado.app.domain.model.DownloadDisplayState
import io.legado.app.domain.model.DownloadItemKind
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText
import org.koin.androidx.compose.koinViewModel

@Composable
fun DownloadCenterScreen(
    onOpenDownloadSettings: () -> Unit,
    onImportAudiobook: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: DownloadCenterViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        NormalCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = stringResource(
                        R.string.download_center_summary,
                        state.activeCount,
                        state.recoverableCount,
                        state.completedCount,
                    ),
                )
                AppText(
                    text = stringResource(
                        R.string.download_center_item_count,
                        state.items.size,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DownloadCenterFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = {
                        viewModel.onIntent(DownloadCenterIntent.SetFilter(filter))
                    },
                    label = { AppText(filter.label()) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 16.dp,
            ),
        ) {
            if (state.items.isEmpty()) {
                item {
                    AppText(
                        text = stringResource(R.string.download_center_empty),
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(state.items, key = DownloadCenterItem::id) { item ->
                    DownloadCenterItemCard(
                        item = item,
                        onAction = { action ->
                            viewModel.onIntent(DownloadCenterIntent.PerformAction(action))
                        },
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenDownloadSettings,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(R.string.download_cache_config))
                    }
                    Button(
                        onClick = onImportAudiobook,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(R.string.audiobook_import_title))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCenterItemCard(
    item: DownloadCenterItem,
    onAction: (DownloadCenterAction) -> Unit,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = item.title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                AppText(text = item.state.label())
            }
            AppText(
                text = buildString {
                    append(
                        if (item.kind == DownloadItemKind.BOOK) {
                            stringResource(R.string.offline_cache)
                        } else {
                            stringResource(R.string.media_downloads_title)
                        }
                    )
                    item.subtitle?.takeIf(String::isNotBlank)?.let { append(" · $it") }
                },
            )
            item.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }
            if (item.downloadedBytes != null) {
                AppText(
                    text = buildString {
                        append(formatDownloadBytes(item.downloadedBytes))
                        item.totalBytes?.takeIf { it > 0L }?.let {
                            append(" / ")
                            append(formatDownloadBytes(it))
                        }
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                AppText(text = error, modifier = Modifier.padding(top = 6.dp))
            }
            if (item.kind == DownloadItemKind.MEDIA) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (item.state) {
                        DownloadDisplayState.WAITING,
                        DownloadDisplayState.RUNNING -> OutlinedButton(
                            onClick = {
                                onAction(DownloadCenterAction.PauseMedia(item.id.removePrefix("media:")))
                            },
                        ) { AppText(stringResource(R.string.pause)) }
                        DownloadDisplayState.PAUSED -> Button(
                            onClick = {
                                onAction(DownloadCenterAction.ResumeMedia(item.id.removePrefix("media:")))
                            },
                        ) { AppText(stringResource(R.string.resume)) }
                        DownloadDisplayState.FAILED -> Button(
                            onClick = {
                                onAction(DownloadCenterAction.RetryMedia(item.id.removePrefix("media:")))
                            },
                        ) { AppText(stringResource(R.string.retry)) }
                        DownloadDisplayState.COMPLETED,
                        DownloadDisplayState.CANCELED -> OutlinedButton(
                            onClick = {
                                onAction(DownloadCenterAction.DeleteMedia(item.id.removePrefix("media:")))
                            },
                        ) { AppText(stringResource(R.string.delete)) }
                    }
                    if (item.state == DownloadDisplayState.WAITING ||
                        item.state == DownloadDisplayState.RUNNING ||
                        item.state == DownloadDisplayState.PAUSED ||
                        item.state == DownloadDisplayState.FAILED
                    ) {
                        OutlinedButton(
                            onClick = {
                                onAction(DownloadCenterAction.CancelMedia(item.id.removePrefix("media:")))
                            },
                        ) { AppText(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
    }
}

private fun DownloadCenterFilter.label(): String = when (this) {
    DownloadCenterFilter.ALL -> "Tất cả"
    DownloadCenterFilter.BOOK -> "Sách"
    DownloadCenterFilter.MEDIA -> "Media"
    DownloadCenterFilter.ACTIVE -> "Đang xử lý"
    DownloadCenterFilter.COMPLETED -> "Hoàn tất"
    DownloadCenterFilter.FAILED -> "Lỗi"
}

@Composable
private fun DownloadDisplayState.label(): String = when (this) {
    DownloadDisplayState.WAITING -> stringResource(R.string.media_download_state_pending)
    DownloadDisplayState.RUNNING -> stringResource(R.string.media_download_state_running)
    DownloadDisplayState.PAUSED -> stringResource(R.string.media_download_state_paused)
    DownloadDisplayState.FAILED -> stringResource(R.string.media_download_state_failed)
    DownloadDisplayState.COMPLETED -> stringResource(R.string.media_download_state_completed)
    DownloadDisplayState.CANCELED -> stringResource(R.string.media_download_state_canceled)
}

private fun formatDownloadBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
