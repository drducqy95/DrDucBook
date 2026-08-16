package io.legado.app.ui.media.download

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.MediaDownloadState
import io.legado.app.service.MediaDownloadService
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.openFileUri
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileInputStream
import java.util.Locale

@Composable
fun MediaDownloadsRouteScreen(
    onBack: () -> Unit,
    onImportAudiobook: () -> Unit,
    viewModel: MediaDownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<MediaDownloadsEffect.ExportFile?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { target ->
        val export = pendingExport
        pendingExport = null
        if (target != null && export != null) {
            runCatching {
                context.contentResolver.openOutputStream(target)?.use { output ->
                    FileInputStream(export.path).use { input -> input.copyTo(output) }
                } ?: error("Cannot open export destination")
            }.onFailure { context.toastOnUi(it.localizedMessage.orEmpty()) }
        }
    }
    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                MediaDownloadsEffect.StartService -> MediaDownloadService.start(context)
                is MediaDownloadsEffect.OpenFile -> context.openFileUri(
                    Uri.fromFile(File(effect.path)),
                    effect.mimeType,
                )
                is MediaDownloadsEffect.ShareFile -> context.share(
                    File(effect.path),
                    effect.mimeType,
                )
                is MediaDownloadsEffect.ExportFile -> {
                    pendingExport = effect
                    exportLauncher.launch(effect.fileName)
                }
                is MediaDownloadsEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }
    MediaDownloadsScreen(state, viewModel::onIntent, onBack, onImportAudiobook)
}

@Composable
fun MediaDownloadsScreen(
    state: MediaDownloadsUiState,
    onIntent: (MediaDownloadsIntent) -> Unit,
    onBack: () -> Unit,
    onImportAudiobook: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.media_downloads_title),
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = stringResource(R.string.media_download_start_queue),
                        onClick = { onIntent(MediaDownloadsIntent.StartQueue) },
                    )
                    TopBarActionButton(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = stringResource(R.string.audiobook_import_title),
                        onClick = onImportAudiobook,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.loading -> androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { AppCircularProgressIndicator() }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            ) {
                DownloadManagementControls(state, onIntent)
                if (state.tasks.isEmpty()) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { AppText(stringResource(R.string.media_downloads_empty)) }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = adaptiveContentPadding(top = 0.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.tasks, key = MediaDownloadTaskUi::id) { task ->
                            DownloadTaskCard(task, onIntent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: MediaDownloadTaskUi,
    onIntent: (MediaDownloadsIntent) -> Unit,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(task.bookTitle, style = LegadoTheme.typography.titleMedium)
                    AppText(
                        text = task.state.label(),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    task.errorMessage?.takeIf(String::isNotBlank)?.let {
                        AppText(
                            text = it,
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.error,
                        )
                    }
                }
                when (task.state) {
                    MediaDownloadState.PENDING, MediaDownloadState.RUNNING -> IconButton(
                        onClick = { onIntent(MediaDownloadsIntent.Pause(task.id)) }
                    ) { Icon(Icons.Default.Pause, stringResource(R.string.pause)) }
                    MediaDownloadState.PAUSED -> IconButton(
                        onClick = { onIntent(MediaDownloadsIntent.Resume(task.id)) }
                    ) { Icon(Icons.Default.PlayArrow, stringResource(R.string.resume)) }
                    MediaDownloadState.FAILED -> IconButton(
                        onClick = { onIntent(MediaDownloadsIntent.Retry(task.id)) }
                    ) { Icon(Icons.Default.Refresh, stringResource(R.string.retry)) }
                    else -> Unit
                }
                if (task.state !in setOf(MediaDownloadState.COMPLETED, MediaDownloadState.CANCELED)) {
                    IconButton(onClick = { onIntent(MediaDownloadsIntent.Cancel(task.id)) }) {
                        Icon(Icons.Default.Cancel, stringResource(R.string.cancel))
                    }
                }
                IconButton(onClick = { onIntent(MediaDownloadsIntent.Delete(task.id)) }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete))
                }
            }
            task.items.forEach { item -> DownloadItemRow(item, onIntent) }
        }
    }
}

@Composable
private fun DownloadItemRow(
    item: MediaDownloadItemUi,
    onIntent: (MediaDownloadsIntent) -> Unit,
) {
    val fraction = item.totalBytes.takeIf { it > 0L }
        ?.let { (item.bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
    val progressDescription = if (fraction == null) {
        stringResource(R.string.media_download_progress_indeterminate_a11y, item.title)
    } else {
        stringResource(R.string.media_download_progress_percent_a11y, item.title, (fraction * 100).toInt())
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(item.title, style = LegadoTheme.typography.bodyMedium)
                AppText(
                    text = buildString {
                        append(item.state.label())
                        append(" - ")
                        append(formatBytes(item.bytesDownloaded))
                        if (item.totalBytes > 0L) append(" / ${formatBytes(item.totalBytes)}")
                    },
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                item.errorMessage?.takeIf(String::isNotBlank)?.let {
                    AppText(it, style = LegadoTheme.typography.bodySmall, color = LegadoTheme.colorScheme.error)
                }
            }
            if (item.state == MediaDownloadState.COMPLETED) {
                IconButton(onClick = { onIntent(MediaDownloadsIntent.Open(item.id)) }) {
                    Icon(Icons.Default.FolderOpen, stringResource(R.string.open))
                }
                IconButton(onClick = { onIntent(MediaDownloadsIntent.Share(item.id)) }) {
                    Icon(Icons.Default.Share, stringResource(R.string.share))
                }
                IconButton(onClick = { onIntent(MediaDownloadsIntent.Export(item.id)) }) {
                    Icon(Icons.Default.SaveAlt, stringResource(R.string.export))
                }
                IconButton(onClick = { onIntent(MediaDownloadsIntent.DeleteItem(item.id)) }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete))
                }
            }
            if (item.state in setOf(MediaDownloadState.PENDING, MediaDownloadState.PAUSED)) {
                IconButton(onClick = { onIntent(MediaDownloadsIntent.MoveEarlier(item.id)) }) {
                    Icon(Icons.Default.ArrowUpward, stringResource(R.string.a11y_move_up))
                }
                IconButton(onClick = { onIntent(MediaDownloadsIntent.MoveLater(item.id)) }) {
                    Icon(Icons.Default.ArrowDownward, stringResource(R.string.a11y_move_down))
                }
            }
        }
        if (item.state in setOf(MediaDownloadState.PENDING, MediaDownloadState.RUNNING)) {
            val progressModifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = progressDescription }
            if (fraction == null) {
                LinearProgressIndicator(modifier = progressModifier)
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = progressModifier)
            }
        }
    }
}

@Composable
private fun DownloadManagementControls(
    state: MediaDownloadsUiState,
    onIntent: (MediaDownloadsIntent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppText(
            text = stringResource(R.string.media_download_storage_used, formatBytes(state.storageUsedBytes)),
            style = LegadoTheme.typography.labelMedium,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
        AppText(
            text = stringResource(
                R.string.media_download_task_summary,
                state.totalTaskCount,
                state.activeTaskCount,
                state.completedTaskCount,
                state.failedTaskCount,
            ),
            style = LegadoTheme.typography.bodySmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
        if (state.recoverableTaskCount > 0) {
            AppText(
                text = stringResource(R.string.media_download_recoverable_summary, state.recoverableTaskCount),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.error,
            )
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MediaDownloadFilter.entries) { filter ->
                androidx.compose.material3.FilterChip(
                    selected = filter == state.filter,
                    onClick = { onIntent(MediaDownloadsIntent.SetFilter(filter)) },
                    label = { AppText(filter.label()) },
                )
            }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MediaDownloadSort.entries) { sort ->
                androidx.compose.material3.FilterChip(
                    selected = sort == state.sort,
                    onClick = { onIntent(MediaDownloadsIntent.SetSort(sort)) },
                    label = { AppText(sort.label()) },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(
                onClick = { onIntent(MediaDownloadsIntent.PauseAll) },
                enabled = state.activeTaskCount > 0,
            ) {
                Icon(Icons.Default.PauseCircle, stringResource(R.string.media_download_pause_all))
            }
            IconButton(
                onClick = { onIntent(MediaDownloadsIntent.ResumeAll) },
                enabled = state.recoverableTaskCount > 0,
            ) {
                Icon(Icons.Default.PlayCircle, stringResource(R.string.media_download_resume_all))
            }
            IconButton(
                onClick = { onIntent(MediaDownloadsIntent.CancelAll) },
                enabled = state.activeTaskCount > 0 || state.recoverableTaskCount > 0,
            ) {
                Icon(Icons.Default.Cancel, stringResource(R.string.media_download_cancel_all))
            }
            IconButton(
                onClick = { onIntent(MediaDownloadsIntent.DeleteCompleted) },
                enabled = state.completedTaskCount > 0,
            ) {
                Icon(Icons.Default.DeleteSweep, stringResource(R.string.media_download_delete_completed))
            }
        }
    }
}

@Composable
private fun MediaDownloadState.label(): String = when (this) {
    MediaDownloadState.PENDING -> stringResource(R.string.media_download_state_pending)
    MediaDownloadState.RUNNING -> stringResource(R.string.media_download_state_running)
    MediaDownloadState.PAUSED -> stringResource(R.string.media_download_state_paused)
    MediaDownloadState.FAILED -> stringResource(R.string.media_download_state_failed)
    MediaDownloadState.COMPLETED -> stringResource(R.string.media_download_state_completed)
    MediaDownloadState.CANCELED -> stringResource(R.string.media_download_state_canceled)
}

@Composable
private fun MediaDownloadFilter.label(): String = when (this) {
    MediaDownloadFilter.ALL -> stringResource(R.string.media_download_filter_all)
    MediaDownloadFilter.ACTIVE -> stringResource(R.string.media_download_filter_active)
    MediaDownloadFilter.COMPLETED -> stringResource(R.string.media_download_filter_completed)
    MediaDownloadFilter.FAILED -> stringResource(R.string.media_download_filter_failed)
}

@Composable
private fun MediaDownloadSort.label(): String = when (this) {
    MediaDownloadSort.DATE -> stringResource(R.string.media_download_sort_date)
    MediaDownloadSort.NAME -> stringResource(R.string.media_download_sort_name)
    MediaDownloadSort.STATUS -> stringResource(R.string.media_download_sort_status)
    MediaDownloadSort.SIZE -> stringResource(R.string.media_download_sort_size)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GiB", bytes / 1024f / 1024f / 1024f)
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MiB", bytes / 1024f / 1024f)
    else -> String.format(Locale.US, "%.1f KiB", bytes / 1024f)
}
