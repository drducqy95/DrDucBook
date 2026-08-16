package io.legado.app.ui.translation.revision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import io.legado.app.domain.model.RevisionStatus
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
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun TranslationRevisionRouteScreen(
    bookUrl: String,
    chapterIndex: Int,
    targetLanguage: String,
    provider: String,
    onBackClick: () -> Unit,
    viewModel: TranslationRevisionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(bookUrl, chapterIndex, targetLanguage, provider) {
        viewModel.onIntent(
            TranslationRevisionIntent.Load(bookUrl, chapterIndex, targetLanguage, provider)
        )
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TranslationRevisionEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }
    TranslationRevisionScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@Composable
fun TranslationRevisionScreen(
    state: TranslationRevisionUiState,
    onIntent: (TranslationRevisionIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.translation_revision_title),
                subtitle = state.chapterTitle.takeIf(String::isNotBlank),
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = { onIntent(TranslationRevisionIntent.Refresh) },
                    )
                    if (!state.saving && state.status != RevisionStatus.FINAL) {
                        TopBarActionButton(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.save),
                            onClick = { onIntent(TranslationRevisionIntent.Save) },
                        )
                    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading || state.saving) {
                item(key = "progress") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            item(key = "identity") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText(state.bookTitle, style = LegadoTheme.typography.titleMedium)
                    AppText(
                        text = state.status?.displayLabel().orEmpty(),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "raw") {
                OutlinedTextField(
                    value = state.rawContent,
                    onValueChange = {},
                    readOnly = true,
                    label = { AppText(stringResource(R.string.translation_revision_raw)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp),
                )
            }
            item(key = "translation") {
                OutlinedTextField(
                    value = state.editedContent,
                    onValueChange = { onIntent(TranslationRevisionIntent.Edit(it)) },
                    readOnly = state.status == RevisionStatus.FINAL,
                    label = { AppText(stringResource(R.string.translation_revision_text)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                )
            }
            item(key = "revision-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.status == RevisionStatus.FINAL) {
                        IconButton(onClick = { onIntent(TranslationRevisionIntent.RequestUnlock) }) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = stringResource(R.string.translation_revision_unlock),
                            )
                        }
                    } else if (state.status != null) {
                        IconButton(onClick = { onIntent(TranslationRevisionIntent.RequestFinalize) }) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(R.string.translation_revision_finalize),
                            )
                        }
                    }
                }
            }
            item(key = "history-title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null)
                    AppText(
                        stringResource(R.string.translation_revision_history),
                        modifier = Modifier.padding(start = 8.dp),
                        style = LegadoTheme.typography.titleMedium,
                    )
                }
            }
            items(
                items = state.history,
                key = TranslationRevisionItemUi::revisionId,
                contentType = { "revision" },
            ) { revision ->
                RevisionHistoryItem(
                    revision = revision,
                    restoreEnabled = revision.revisionId != state.history.firstOrNull()?.revisionId,
                    onRestore = {
                        onIntent(TranslationRevisionIntent.RequestRestore(revision.revisionId))
                    },
                )
            }
        }
    }

    val dialog = state.dialog
    AppAlertDialog(
        show = dialog != null,
        onDismissRequest = { onIntent(TranslationRevisionIntent.DismissDialog) },
        title = stringResource(R.string.translation_revision_title),
        text = stringResource(
            when (dialog) {
                TranslationRevisionDialog.Finalize -> R.string.translation_revision_confirm_finalize
                TranslationRevisionDialog.Unlock -> R.string.translation_revision_confirm_unlock
                is TranslationRevisionDialog.Restore -> R.string.translation_revision_confirm_restore
                null -> R.string.translation_revision_confirm_restore
            }
        ),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onIntent(TranslationRevisionIntent.ConfirmDialog) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { onIntent(TranslationRevisionIntent.DismissDialog) },
    )
}

@Composable
private fun RevisionHistoryItem(
    revision: TranslationRevisionItemUi,
    restoreEnabled: Boolean,
    onRestore: () -> Unit,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                AppText(revision.status.displayLabel(), style = LegadoTheme.typography.labelLarge)
                AppText(
                    text = revision.content.lineSequence().firstOrNull().orEmpty().take(120),
                    style = LegadoTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                AppText(
                    text = "${revision.actor} | ${DateFormat.getDateTimeInstance().format(Date(revision.updatedAt))}",
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(enabled = restoreEnabled, onClick = onRestore) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = stringResource(R.string.translation_revision_restore),
                )
            }
        }
    }
}

private fun RevisionStatus.displayLabel(): String = name.replace('_', ' ')
