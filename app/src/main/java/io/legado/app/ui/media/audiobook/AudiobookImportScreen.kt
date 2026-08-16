package io.legado.app.ui.media.audiobook

import android.content.Intent
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AudiobookImportRouteScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: AudiobookImportViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (uris.isNotEmpty()) viewModel.onIntent(AudiobookImportIntent.ScanFiles(uris))
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.onIntent(AudiobookImportIntent.ScanFolder(it))
        }
    }
    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                AudiobookImportEffect.PickFiles -> filesPicker.launch(
                    arrayOf("audio/*", "audio/x-mpegurl", "application/x-cue")
                )
                AudiobookImportEffect.PickFolder -> folderPicker.launch(null)
                is AudiobookImportEffect.Created -> onCreated(effect.bookUrl)
                is AudiobookImportEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }
    AudiobookImportScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun AudiobookImportScreen(
    state: AudiobookImportUiState,
    onIntent: (AudiobookImportIntent) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.audiobook_import_title),
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = padding.calculateTopPadding(),
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("pickers") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onIntent(AudiobookImportIntent.PickFiles) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        AppText(stringResource(R.string.audiobook_pick_files), Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = { onIntent(AudiobookImportIntent.PickFolder) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        AppText(stringResource(R.string.audiobook_pick_folder), Modifier.padding(start = 6.dp))
                    }
                }
                if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.tracks.isNotEmpty()) {
                item("metadata") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = { onIntent(AudiobookImportIntent.UpdateTitle(it)) },
                            label = { AppText(stringResource(R.string.book_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.author,
                            onValueChange = { onIntent(AudiobookImportIntent.UpdateAuthor(it)) },
                            label = { AppText(stringResource(R.string.author)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
                items(state.tracks, key = AudiobookTrackUi::id) { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = track.selected,
                            onCheckedChange = { onIntent(AudiobookImportIntent.ToggleTrack(track.id)) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = track.title,
                                onValueChange = { onIntent(AudiobookImportIntent.UpdateTrackTitle(track.id, it)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            AppText(
                                text = "#${track.trackNumber} · ${formatDuration(track.durationMs)}",
                                style = LegadoTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item("create") {
                    Button(
                        onClick = { onIntent(AudiobookImportIntent.Create) },
                        enabled = !state.creating && state.title.isNotBlank() && state.tracks.any { it.selected },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(R.string.audiobook_create_book))
                    }
                }
            }
        }
    }
}

private fun formatDuration(valueMs: Long): String {
    val minutes = valueMs.coerceAtLeast(0L) / 60_000L
    val seconds = valueMs.coerceAtLeast(0L) / 1_000L % 60L
    return "%02d:%02d".format(minutes, seconds)
}
