package io.legado.app.ui.config.translation.mlkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import io.legado.app.ui.widget.components.alert.AppAlertDialog
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
fun MlKitModelsRouteScreen(
    onBackClick: () -> Unit,
    viewModel: MlKitModelsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MlKitModelsEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }
    MlKitModelsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@Composable
fun MlKitModelsScreen(
    state: MlKitModelsUiState,
    onIntent: (MlKitModelsIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.mlkit_language_models),
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = { onIntent(MlKitModelsIntent.Refresh) },
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
                        text = stringResource(R.string.mlkit_language_models_summary),
                        style = LegadoTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onIntent(MlKitModelsIntent.RequestDownloadAll) },
                            enabled = !state.batchRunning,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Download, null)
                            AppText(
                                stringResource(R.string.mlkit_download_all),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = { onIntent(MlKitModelsIntent.RequestDeleteAll) },
                            enabled = !state.batchRunning,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Delete, null)
                            AppText(
                                stringResource(R.string.mlkit_delete_all),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (state.loading || state.batchRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            items(
                items = state.models,
                key = MlKitLanguageModelUi::languageTag,
                contentType = { "language-model" },
            ) { model ->
                NormalCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(model.displayName, style = LegadoTheme.typography.titleMedium)
                            AppText(
                                model.languageTag,
                                style = LegadoTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            enabled = !model.busy && !state.batchRunning,
                            onClick = {
                                onIntent(
                                    if (model.downloaded) MlKitModelsIntent.Delete(model.languageTag)
                                    else MlKitModelsIntent.Download(model.languageTag)
                                )
                            },
                        ) {
                            Icon(
                                imageVector = if (model.downloaded) {
                                    Icons.Default.Delete
                                } else {
                                    Icons.Default.Download
                                },
                                contentDescription = stringResource(
                                    if (model.downloaded) R.string.delete else R.string.download
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    val dialog = state.dialog
    AppAlertDialog(
        show = dialog != null,
        onDismissRequest = { onIntent(MlKitModelsIntent.DismissDialog) },
        title = stringResource(R.string.draw),
        text = stringResource(
            if (dialog == MlKitModelsDialog.DeleteAll) {
                R.string.mlkit_confirm_delete_all
            } else {
                R.string.mlkit_confirm_download_all
            }
        ),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onIntent(MlKitModelsIntent.ConfirmDialog) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { onIntent(MlKitModelsIntent.DismissDialog) },
    )
}
