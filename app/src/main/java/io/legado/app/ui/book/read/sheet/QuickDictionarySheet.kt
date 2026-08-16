package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.quickdict.QuickDictionaryForm
import io.legado.app.ui.quickdict.QuickDictionaryUiState
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun QuickDictionarySheet(
    show: Boolean,
    state: QuickDictionaryUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.quick_dictionary_add),
    ) {
        QuickDictionaryForm(
            state = state,
            onRawChange = { onIntent(ReadBookIntent.SetQuickDictionaryRaw(it)) },
            onHanVietChange = { onIntent(ReadBookIntent.SetQuickDictionaryHanViet(it)) },
            onTargetChange = { onIntent(ReadBookIntent.SetQuickDictionaryTarget(it)) },
            onRequestSuggestion = {
                onIntent(ReadBookIntent.RequestQuickDictionarySuggestion(it))
            },
            onApplySuggestion = {
                onIntent(ReadBookIntent.ApplyQuickDictionarySuggestion(it))
            },
            onTypeChange = { onIntent(ReadBookIntent.SetQuickDictionaryType(it)) },
            onScopeChange = { onIntent(ReadBookIntent.SetQuickDictionaryScope(it)) },
            onAdjustSelection = {
                onIntent(ReadBookIntent.AdjustQuickDictionarySelection(it))
            },
            onSelectUniverse = {
                onIntent(ReadBookIntent.SelectQuickDictionaryUniverse(it))
            },
            onUniverseNameChange = {
                onIntent(ReadBookIntent.SetQuickDictionaryUniverseName(it))
            },
            onContextMarkersChange = {
                onIntent(ReadBookIntent.SetQuickDictionaryContextMarkers(it))
            },
            onSave = { onIntent(ReadBookIntent.SaveQuickDictionary) },
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        )
    }

    AppAlertDialog(
        show = state.showSelectionChooser,
        onDismissRequest = {
            onIntent(ReadBookIntent.DismissQuickDictionaryMappingAlternatives)
        },
        title = stringResource(R.string.quick_dictionary_mapping_title),
        text = stringResource(R.string.quick_dictionary_mapping_summary),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.selectionAlternatives.forEachIndexed { index, alternative ->
                    TextButton(
                        onClick = {
                            onIntent(ReadBookIntent.SelectQuickDictionaryMappingAlternative(index))
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
            onIntent(ReadBookIntent.DismissQuickDictionaryMappingAlternatives)
        },
    )
}
