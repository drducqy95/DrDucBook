package io.legado.app.ui.assetdelivery

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.openFileUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

@Composable
fun AssetDeliveryRouteScreen(
    rawUri: String,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: AssetDeliveryViewModel = koinViewModel(
        key = "AssetDelivery:$rawUri",
        parameters = { parametersOf(rawUri) },
    ),
) {
    val context = LocalContext.current
    val latestOnOpenAccount = rememberUpdatedState(onOpenAccount)
    AssetDeliveryScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onOpenFile = { path, mimeType ->
            context.openFileUri(Uri.fromFile(File(path)), mimeType)
        },
        onOpenAccount = { latestOnOpenAccount.value() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDeliveryScreen(
    state: AssetDeliveryUiState,
    effects: Flow<AssetDeliveryEffect>,
    onIntent: (AssetDeliveryIntent) -> Unit,
    onBack: () -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onOpenAccount: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AssetDeliveryEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is AssetDeliveryEffect.OpenFile -> onOpenFile(effect.path, effect.mimeType)
                AssetDeliveryEffect.OpenAccount -> onOpenAccount()
            }
        }
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = state.title.ifBlank { stringResource(R.string.asset_delivery_title) },
                subtitle = state.selectedItem?.fileName,
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(AssetDeliveryIntent.Refresh) },
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.retry),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AssetDeliveryBottomBar(
                state = state,
                onIntent = onIntent,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AssetDeliveryStatusSection(
                    state = state,
                    onIntent = onIntent,
                )
            }
            if (state.items.isNotEmpty()) {
                item {
                    AppText(
                        text = stringResource(R.string.asset_delivery_packages),
                        style = LegadoTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(
                    items = state.items,
                    key = AssetDeliveryItemUi::id,
                ) { item ->
                    AssetDeliveryItemRow(
                        item = item,
                        selected = item.id == state.selectedArtifactId,
                        enabled = !state.downloading,
                        onClick = {
                            onIntent(AssetDeliveryIntent.SelectArtifact(item.id))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetDeliveryStatusSection(
    state: AssetDeliveryUiState,
    onIntent: (AssetDeliveryIntent) -> Unit,
) {
    SplicedColumnGroup(title = stringResource(R.string.asset_delivery_status)) {
        if (state.loading || state.downloading || state.importing) {
            val progress = state.progressFraction()
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when {
            !state.configured -> SettingItem(
                title = stringResource(R.string.asset_delivery_not_configured),
                description = stringResource(R.string.asset_delivery_not_configured_summary),
            )

            state.needsSignIn -> SettingItem(
                title = stringResource(R.string.asset_delivery_sign_in_required),
                description = stringResource(R.string.asset_delivery_sign_in_required_summary),
            )

            state.importing -> SettingItem(
                title = stringResource(R.string.asset_delivery_importing),
                description = state.selectedItem?.displayName.orEmpty(),
            )

            state.imported -> SettingItem(
                title = stringResource(R.string.asset_delivery_imported),
                description = state.downloadedPath.orEmpty(),
            )

            state.verified -> SettingItem(
                title = stringResource(R.string.asset_delivery_verified),
                description = state.downloadedPath.orEmpty(),
            )

            state.status == AssetDeliveryStatus.IMPORT_FAILED -> SettingItem(
                title = stringResource(R.string.asset_delivery_import_failed),
                description = state.errorMessage.orEmpty(),
            )

            state.downloading -> SettingItem(
                title = stringResource(R.string.asset_delivery_downloading),
                description = state.progressText(),
            )

            state.errorMessage != null -> SettingItem(
                title = stringResource(R.string.download_error),
                description = state.errorMessage,
            )

            state.items.isEmpty() && !state.loading -> SettingItem(
                title = stringResource(R.string.asset_delivery_no_packages),
                description = state.rawUri,
            )

            else -> SettingItem(
                title = stringResource(R.string.asset_delivery_ready),
                description = state.selectedItem?.fileName.orEmpty(),
            )
        }

        if (state.needsSignIn) {
            TextButton(
                onClick = { onIntent(AssetDeliveryIntent.OpenAccount) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AppText(stringResource(R.string.asset_delivery_open_account))
            }
        }
    }
}

@Composable
private fun AssetDeliveryItemRow(
    item: AssetDeliveryItemUi,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        headlineContent = {
            AppText(
                text = item.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppText(
                    text = "${item.detail} · ${item.sizeText}",
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                AppText(
                    text = "${item.fileName} · SHA ${item.sha256Short}",
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            RadioButton(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                LegadoTheme.colorScheme.surfaceContainerHigh
            } else {
                LegadoTheme.colorScheme.surface
            },
        ),
    )
}

@Composable
private fun AssetDeliveryBottomBar(
    state: AssetDeliveryUiState,
    onIntent: (AssetDeliveryIntent) -> Unit,
) {
    Surface(
        color = LegadoTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.downloading -> {
                    OutlinedButton(
                        onClick = { onIntent(AssetDeliveryIntent.CancelDownload) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(R.string.cancel))
                    }
                }

                state.status == AssetDeliveryStatus.IMPORT_FAILED -> {
                    Button(
                        onClick = { onIntent(AssetDeliveryIntent.RetryImport) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(R.string.retry))
                    }
                }

                state.downloaded -> {
                    OutlinedButton(
                        onClick = { onIntent(AssetDeliveryIntent.DownloadSelected) },
                        enabled = state.selectedArtifactId != null && state.configured,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(R.string.asset_delivery_download_again))
                    }
                    Button(
                        onClick = { onIntent(AssetDeliveryIntent.OpenDownloaded) },
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(R.string.asset_delivery_open_file))
                    }
                }

                state.needsSignIn -> {
                    Button(
                        onClick = { onIntent(AssetDeliveryIntent.OpenAccount) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(R.string.asset_delivery_open_account))
                    }
                }

                else -> {
                    Button(
                        onClick = { onIntent(AssetDeliveryIntent.DownloadSelected) },
                        enabled = state.selectedArtifactId != null &&
                            !state.loading &&
                            state.configured,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(R.string.download))
                    }
                }
            }
        }
    }
}

private fun AssetDeliveryUiState.progressFraction(): Float? =
    totalBytes
        .takeIf { it > 0L }
        ?.let { (progressBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f) }

private fun AssetDeliveryUiState.progressText(): String =
    if (totalBytes > 0L) {
        "${progressBytes.coerceAtMost(totalBytes)} / $totalBytes"
    } else {
        "$progressBytes"
    }
