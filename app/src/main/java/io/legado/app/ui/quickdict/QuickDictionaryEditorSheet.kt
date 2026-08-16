package io.legado.app.ui.quickdict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun QuickDictionaryEditorSheet(
    request: QuickDictionaryRequest?,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit = {},
    viewModel: QuickDictionaryEditorViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(request) {
        request?.let { viewModel.onIntent(QuickDictionaryEditorIntent.Load(it)) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                QuickDictionaryEditorEffect.Saved -> {
                    onSaved()
                    onDismissRequest()
                }
                is QuickDictionaryEditorEffect.ShowMessage -> Unit
            }
        }
    }

    AppModalBottomSheet(
        show = request != null,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.quick_dictionary_add),
    ) {
        QuickDictionaryForm(
            state = state,
            onRawChange = { viewModel.onIntent(QuickDictionaryEditorIntent.SetRaw(it)) },
            onHanVietChange = {
                viewModel.onIntent(QuickDictionaryEditorIntent.SetHanViet(it))
            },
            onTargetChange = {
                viewModel.onIntent(QuickDictionaryEditorIntent.SetTarget(it))
            },
            onRequestSuggestion = {
                viewModel.onIntent(QuickDictionaryEditorIntent.RequestSuggestion(it))
            },
            onApplySuggestion = {
                viewModel.onIntent(QuickDictionaryEditorIntent.ApplySuggestion(it))
            },
            onTypeChange = { viewModel.onIntent(QuickDictionaryEditorIntent.SetType(it)) },
            onScopeChange = { viewModel.onIntent(QuickDictionaryEditorIntent.SetScope(it)) },
            onAdjustSelection = {
                viewModel.onIntent(QuickDictionaryEditorIntent.AdjustSelection(it))
            },
            onSelectUniverse = {
                viewModel.onIntent(QuickDictionaryEditorIntent.SelectUniverse(it))
            },
            onUniverseNameChange = {
                viewModel.onIntent(QuickDictionaryEditorIntent.SetUniverseName(it))
            },
            onContextMarkersChange = {
                viewModel.onIntent(QuickDictionaryEditorIntent.SetContextMarkers(it))
            },
            onSave = { viewModel.onIntent(QuickDictionaryEditorIntent.Save) },
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        )
    }

    AppAlertDialog(
        show = state.showSelectionChooser,
        onDismissRequest = {
            viewModel.onIntent(QuickDictionaryEditorIntent.DismissMappingAlternatives)
        },
        title = stringResource(R.string.quick_dictionary_mapping_title),
        text = stringResource(R.string.quick_dictionary_mapping_summary),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.selectionAlternatives.forEachIndexed { index, alternative ->
                    TextButton(
                        onClick = {
                            viewModel.onIntent(
                                QuickDictionaryEditorIntent.SelectMappingAlternative(index)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(alternative.raw)
                    }
                }
            }
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = {
            viewModel.onIntent(QuickDictionaryEditorIntent.DismissMappingAlternatives)
        },
    )
}
