package io.legado.app.ui.quickdict

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.ui.translation.TranslationCaseControls
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun QuickDictionaryForm(
    state: QuickDictionaryUiState,
    onRawChange: (String) -> Unit,
    onHanVietChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onRequestSuggestion: (String) -> Unit,
    onApplySuggestion: (String) -> Unit,
    onTypeChange: (QuickDictionaryType) -> Unit,
    onScopeChange: (QuickDictionaryScope) -> Unit,
    onAdjustSelection: (QuickDictionarySelectionAction) -> Unit,
    onSelectUniverse: (String) -> Unit,
    onUniverseNameChange: (String) -> Unit,
    onContextMarkersChange: (String) -> Unit,
    onSaveToTranslationMemoryChange: (Boolean) -> Unit,
    onMemoryCategoryChange: (StoryMemoryCategory) -> Unit = {},
    onMemoryDescriptionChange: (String) -> Unit = {},
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.contextBefore.isNotBlank() || state.contextAfter.isNotBlank()) {
            val preview = quickDictionaryContextPreview(
                contextBefore = state.contextBefore,
                raw = state.raw,
                contextAfter = state.contextAfter,
            )
            AppText(
                text = stringResource(R.string.quick_dictionary_raw),
                style = LegadoTheme.typography.titleSmall,
            )
            NormalCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = LegadoTheme.colorScheme.surfaceContainer,
            ) {
                AppText(
                    text = buildAnnotatedString {
                        if (preview.omittedBefore) append('…')
                        append(preview.before)
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = LegadoTheme.colorScheme.primary,
                            )
                        ) {
                            append(preview.raw)
                        }
                        append(preview.after)
                        if (preview.omittedAfter) append('…')
                    },
                    modifier = Modifier.padding(12.dp),
                    style = LegadoTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        OutlinedTextField(
            value = state.raw,
            onValueChange = onRawChange,
            modifier = Modifier.fillMaxWidth(),
            label = { AppText(stringResource(R.string.quick_dictionary_raw)) },
            singleLine = true,
        )

        if (state.sourceLocation.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = stringResource(R.string.quick_dictionary_source_location),
                    modifier = Modifier.size(18.dp),
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = state.sourceLocation,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.sourceUrl.isNotBlank()) {
                        AppText(
                            text = state.sourceUrl,
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (state.hasSelectionControls) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SelectionIconButton(
                    enabled = state.canExpandSelectionLeft,
                    description = stringResource(R.string.quick_dictionary_expand_left),
                    onClick = { onAdjustSelection(QuickDictionarySelectionAction.EXPAND_LEFT) },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
                SelectionIconButton(
                    enabled = state.canShrinkSelectionLeft,
                    description = stringResource(R.string.quick_dictionary_shrink_left),
                    onClick = { onAdjustSelection(QuickDictionarySelectionAction.SHRINK_LEFT) },
                ) {
                    Icon(Icons.Default.ChevronRight, null)
                }
                SelectionIconButton(
                    enabled = state.canShrinkSelectionRight,
                    description = stringResource(R.string.quick_dictionary_shrink_right),
                    onClick = { onAdjustSelection(QuickDictionarySelectionAction.SHRINK_RIGHT) },
                ) {
                    Icon(Icons.Default.ChevronLeft, null)
                }
                SelectionIconButton(
                    enabled = state.canExpandSelectionRight,
                    description = stringResource(R.string.quick_dictionary_expand_right),
                    onClick = { onAdjustSelection(QuickDictionarySelectionAction.EXPAND_RIGHT) },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }

        OutlinedTextField(
            value = state.hanViet,
            onValueChange = onHanVietChange,
            modifier = Modifier.fillMaxWidth(),
            label = { AppText(stringResource(R.string.quick_dictionary_han_viet)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.target,
            onValueChange = onTargetChange,
            modifier = Modifier.fillMaxWidth(),
            label = { AppText(stringResource(R.string.quick_dictionary_target)) },
            singleLine = true,
        )
        TranslationCaseControls(
            value = state.target,
            onValueChange = onTargetChange,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactDropdownField(
                value = stringResource(state.type.labelResource()),
                label = stringResource(R.string.quick_dictionary_type),
                items = quickDictionaryVisibleTypes,
                itemLabel = { stringResource(it.labelResource()) },
                onItemSelected = onTypeChange,
                modifier = Modifier.weight(1f),
            )
            CompactDropdownField(
                value = stringResource(state.scope.labelResource()),
                label = stringResource(R.string.quick_dictionary_scope),
                items = QuickDictionaryScope.entries,
                itemLabel = { stringResource(it.labelResource()) },
                onItemSelected = onScopeChange,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(visible = state.scope == QuickDictionaryScope.UNIVERSE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val universeOptions = listOf("" to stringResource(R.string.quick_dictionary_universe_new)) +
                    state.availableUniverses.map { it.key to it.name }
                val currentUniverseLabel = universeOptions.firstOrNull { it.first == state.universeKey }?.second
                    ?: stringResource(R.string.quick_dictionary_universe_new)
                CompactDropdownField(
                    value = currentUniverseLabel,
                    label = stringResource(R.string.quick_dictionary_universe_name),
                    items = universeOptions,
                    itemLabel = { it.second },
                    onItemSelected = { onSelectUniverse(it.first) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.universeName,
                    onValueChange = onUniverseNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { AppText(stringResource(R.string.quick_dictionary_universe_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.contextMarkers,
                    onValueChange = onContextMarkersChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { AppText(stringResource(R.string.quick_dictionary_context_markers)) },
                    supportingText = {
                        AppText(stringResource(R.string.quick_dictionary_context_markers_summary))
                    },
                    minLines = 2,
                )
            }
        }

        if (state.providerOptions.isNotEmpty()) {
            val currentProviderLabel = state.providerOptions.firstOrNull { it.value == state.selectedProvider }?.label
                ?: state.selectedProvider
            CompactDropdownField(
                value = currentProviderLabel,
                label = stringResource(R.string.quick_dictionary_translation_provider),
                items = state.providerOptions,
                itemLabel = { it.label },
                onItemSelected = { onRequestSuggestion(it.value) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.isSuggesting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.suggestions.forEach { suggestion ->
                    OutlinedButton(
                        onClick = { onApplySuggestion(suggestion.text) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText("${suggestion.providerLabel}: ${suggestion.text}")
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSaveToTranslationMemoryChange(!state.saveToTranslationMemory) },
        ) {
            Checkbox(
                checked = state.saveToTranslationMemory,
                onCheckedChange = onSaveToTranslationMemoryChange,
            )
            AppText(stringResource(R.string.quick_dictionary_save_to_translation_memory))
        }
        AnimatedVisibility(visible = state.saveToTranslationMemory) {
            Column(
                modifier = Modifier.padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactDropdownField(
                    value = stringResource(state.memoryCategory.labelResource()),
                    label = stringResource(R.string.quick_dictionary_memory_category),
                    items = StoryMemoryCategory.entries,
                    itemLabel = { stringResource(it.labelResource()) },
                    onItemSelected = onMemoryCategoryChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.memoryDescription,
                    onValueChange = onMemoryDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { AppText(stringResource(R.string.quick_dictionary_memory_description)) },
                    singleLine = false,
                    minLines = 2,
                )
            }
        }

        OutlinedButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
            AppText(
                text = stringResource(if (showAdvanced) R.string.collapse else R.string.expand),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(
                    text = stringResource(R.string.quick_dictionary_priority_summary),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.errorMessage?.let {
            AppText(it, color = LegadoTheme.colorScheme.error)
        }
        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppText(
                stringResource(
                    if (state.isSaving) R.string.quick_dictionary_saving
                    else R.string.quick_dictionary_save
                )
            )
        }
    }
}

@Composable
private fun SelectionIconButton(
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (enabled) {
                LegadoTheme.colorScheme.primary
            } else {
                LegadoTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier,
            ) {
                icon()
            }
        }
    }
}

private val QuickDictionaryUiState.hasSelectionControls: Boolean
    get() = canExpandSelectionLeft || canExpandSelectionRight ||
        canShrinkSelectionLeft || canShrinkSelectionRight

private val quickDictionaryVisibleTypes = listOf(
    QuickDictionaryType.NAME,
    QuickDictionaryType.VIETPHRASE,
    QuickDictionaryType.PHONETIC,
    QuickDictionaryType.PRONOUN,
    QuickDictionaryType.LUAT_NHAN,
    QuickDictionaryType.IGNORE,
)

private fun QuickDictionaryType.labelResource(): Int = when (this) {
    QuickDictionaryType.TERM -> R.string.quick_dictionary_type_term
    QuickDictionaryType.NAME -> R.string.quick_dictionary_type_name
    QuickDictionaryType.VIETPHRASE -> R.string.quick_dictionary_type_vietphrase
    QuickDictionaryType.PRONOUN -> R.string.quick_dictionary_type_pronoun
    QuickDictionaryType.PHONETIC -> R.string.quick_dictionary_type_phonetic
    QuickDictionaryType.LUAT_NHAN -> R.string.quick_dictionary_type_luat_nhan
    QuickDictionaryType.IGNORE -> R.string.quick_dictionary_type_ignore
}

private fun QuickDictionaryScope.labelResource(): Int = when (this) {
    QuickDictionaryScope.GLOBAL -> R.string.quick_dictionary_scope_global
    QuickDictionaryScope.UNIVERSE -> R.string.quick_dictionary_scope_universe
    QuickDictionaryScope.PROJECT -> R.string.quick_dictionary_scope_project
}

internal data class QuickDictionaryContextPreview(
    val before: String,
    val raw: String,
    val after: String,
    val omittedBefore: Boolean,
    val omittedAfter: Boolean,
) {
    val text: String
        get() = buildString {
            if (omittedBefore) append('…')
            append(before)
            append(raw)
            append(after)
            if (omittedAfter) append('…')
        }
}

internal fun quickDictionaryContextPreview(
    contextBefore: String,
    raw: String,
    contextAfter: String,
    contextChars: Int = SOURCE_CONTEXT_CHARS,
): QuickDictionaryContextPreview {
    val safeLimit = contextChars.coerceAtLeast(0)
    return QuickDictionaryContextPreview(
        before = contextBefore.takeLast(safeLimit),
        raw = raw,
        after = contextAfter.take(safeLimit),
        omittedBefore = contextBefore.length > safeLimit,
        omittedAfter = contextAfter.length > safeLimit,
    )
}

@Composable
private fun <T> CompactDropdownField(
    value: String,
    label: String,
    items: List<T>,
    itemLabel: @Composable (T) -> String,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { AppText(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            singleLine = true,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { AppText(itemLabel(item)) },
                    onClick = {
                        expanded = false
                        onItemSelected(item)
                    },
                )
            }
        }
    }
}

private const val SOURCE_CONTEXT_CHARS = 14
