package io.legado.app.ui.config.tts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.LocalTtsImportStage
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.FilteredOpenDocumentContract
import io.legado.app.utils.openUrl
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun TtsModelManagerRouteScreen(
    onBackClick: () -> Unit,
    viewModel: TtsModelManagerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val importPicker = rememberLauncherForActivityResult(
        contract = FilteredOpenDocumentContract(
            primaryMimeType = "application/zip",
            persistableAccess = false,
        ),
    ) { uri ->
        uri?.let { viewModel.onIntent(TtsModelManagerIntent.ImportFile(it)) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                TtsModelManagerEffect.PickImportFile -> importPicker.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                    )
                )
                is TtsModelManagerEffect.OpenUrl -> context.openUrl(effect.url)
                is TtsModelManagerEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }

    TtsModelManagerScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@Composable
fun TtsModelManagerScreen(
    state: TtsModelManagerUiState,
    onIntent: (TtsModelManagerIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showGuide by remember { mutableStateOf(false) }
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.tts_model_manager),
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = { onIntent(TtsModelManagerIntent.Refresh) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(
                        text = stringResource(R.string.tts_model_manager_summary),
                        style = LegadoTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onIntent(TtsModelManagerIntent.PickImportFile) },
                            enabled = !state.importing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            AppText(
                                stringResource(R.string.local_tts_import_short),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = { onIntent(TtsModelManagerIntent.OpenCatalog) },
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(stringResource(R.string.local_tts_catalog_short))
                        }
                    }
                    OutlinedButton(
                        onClick = { showGuide = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(R.string.local_tts_model_guide_short))
                    }
                    if (state.loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (state.importing) {
                        val fraction = state.importProgress?.fraction
                        if (fraction == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = importStageText(state.importProgress?.stage),
                                modifier = Modifier.weight(1f),
                                style = LegadoTheme.typography.bodySmall,
                            )
                            OutlinedButton(
                                onClick = { onIntent(TtsModelManagerIntent.CancelImport) },
                            ) {
                                AppText(stringResource(android.R.string.cancel))
                            }
                        }
                    }
                }
            }

            if (!state.loading && state.models.isEmpty()) {
                item(key = "empty") {
                    AppText(
                        text = stringResource(R.string.local_tts_no_models),
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(
                items = state.models,
                key = TtsModelItemUi::id,
                contentType = { "tts-model" },
            ) { model ->
                NormalCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(model.name, style = LegadoTheme.typography.titleMedium)
                                AppText(
                                    text = stringResource(
                                        R.string.local_tts_model_meta,
                                        model.language,
                                        model.sampleRate,
                                        model.voices.size,
                                        formatTtsSize(model.sizeBytes),
                                    ),
                                    style = LegadoTheme.typography.bodySmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                                AppText(
                                    text = if (model.runtimeReady) {
                                        stringResource(R.string.local_tts_runtime_ready)
                                    } else {
                                        stringResource(R.string.local_tts_runtime_unavailable)
                                    },
                                    style = LegadoTheme.typography.labelMedium,
                                    color = if (model.runtimeReady) {
                                        LegadoTheme.colorScheme.primary
                                    } else {
                                        LegadoTheme.colorScheme.error
                                    },
                                )
                            }
                            if (model.isDefault) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.default_version),
                                    tint = LegadoTheme.colorScheme.primary,
                                )
                            }
                        }
                        DropdownListSettingItem(
                            title = stringResource(R.string.speak_engine),
                            selectedValue = model.selectedVoiceId.toString(),
                            displayEntries = model.voices.map(TtsVoiceItemUi::name).toTypedArray(),
                            entryValues = model.voices.map { it.id.toString() }.toTypedArray(),
                            description = model.engine,
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { voiceId ->
                                    onIntent(TtsModelManagerIntent.SelectVoice(model.id, voiceId))
                                }
                            },
                        )
                        if (model.checksum.isNotBlank()) {
                            AppText(
                                text = stringResource(
                                    R.string.local_tts_model_checksum,
                                    model.checksum.take(16),
                                ),
                                style = LegadoTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { onIntent(TtsModelManagerIntent.TestModel(model.id)) },
                                enabled = model.runtimeReady && state.testingModelId == null,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                AppText(
                                    stringResource(
                                        if (state.testingModelId == model.id) {
                                            R.string.local_tts_testing
                                        } else {
                                            R.string.local_tts_test
                                        }
                                    ),
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                            OutlinedButton(
                                onClick = { onIntent(TtsModelManagerIntent.SetDefault(model.id)) },
                                enabled = model.runtimeReady,
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(stringResource(R.string.local_tts_set_default))
                            }
                            IconButton(
                                onClick = { onIntent(TtsModelManagerIntent.RequestDelete(model.id)) },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    AppAlertDialog(
        show = state.deletingModelId != null,
        onDismissRequest = { onIntent(TtsModelManagerIntent.DismissDelete) },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.local_tts_delete_confirm),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onIntent(TtsModelManagerIntent.ConfirmDelete) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { onIntent(TtsModelManagerIntent.DismissDelete) },
    )

    AppAlertDialog(
        show = showGuide,
        onDismissRequest = { showGuide = false },
        title = stringResource(R.string.local_tts_model_guide),
        text = stringResource(R.string.local_tts_model_guide_text),
        dismissText = stringResource(android.R.string.ok),
        onDismiss = { showGuide = false },
    )
}

private fun formatTtsSize(bytes: Long): String =
    String.format(Locale.US, "%.1f MiB", bytes / 1024f / 1024f)

@Composable
private fun importStageText(stage: LocalTtsImportStage?): String = stringResource(
    when (stage) {
        LocalTtsImportStage.PREPARING -> R.string.local_tts_import_preparing
        LocalTtsImportStage.EXTRACTING -> R.string.local_tts_import_extracting
        LocalTtsImportStage.VALIDATING -> R.string.local_tts_import_validating
        LocalTtsImportStage.PROBING -> R.string.local_tts_import_probing
        LocalTtsImportStage.INSTALLING -> R.string.local_tts_import_installing
        null -> R.string.importing_local_tts_model
    }
)
