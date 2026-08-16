package io.legado.app.ui.quickdict

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
            AppText(
                text = stringResource(R.string.quick_dictionary_selection_tools),
                style = LegadoTheme.typography.titleSmall,
            )
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
        TargetCaseControls(
            value = state.target,
            onValueChange = onTargetChange,
        )

        AppText(
            text = stringResource(R.string.quick_dictionary_translation_provider),
            style = LegadoTheme.typography.titleSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.providerOptions.forEach { provider ->
                FilterChip(
                    selected = provider.value == state.selectedProvider,
                    enabled = !state.isSuggesting,
                    onClick = { onRequestSuggestion(provider.value) },
                    label = { AppText(provider.label) },
                )
            }
        }
        if (state.isSuggesting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.suggestions.isNotEmpty()) {
            AppText(
                text = stringResource(R.string.quick_dictionary_suggestions),
                style = LegadoTheme.typography.titleSmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(
                    text = stringResource(R.string.quick_dictionary_type),
                    style = LegadoTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickDictionaryVisibleTypes.forEach { type ->
                        FilterChip(
                            selected = type == state.type,
                            onClick = { onTypeChange(type) },
                            label = { AppText(stringResource(type.labelResource())) },
                        )
                    }
                }

                AppText(
                    text = stringResource(R.string.quick_dictionary_scope),
                    style = LegadoTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDictionaryScope.entries.forEach { scope ->
                        FilterChip(
                            selected = scope == state.scope,
                            onClick = { onScopeChange(scope) },
                            label = { AppText(stringResource(scope.labelResource())) },
                        )
                    }
                }
                if (state.scope == QuickDictionaryScope.UNIVERSE) {
                    AppText(
                        text = stringResource(R.string.quick_dictionary_universe_explanation),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.universeKey.isBlank(),
                            onClick = { onSelectUniverse("") },
                            label = { AppText(stringResource(R.string.quick_dictionary_universe_new)) },
                        )
                        state.availableUniverses.forEach { universe ->
                            FilterChip(
                                selected = state.universeKey == universe.key,
                                onClick = { onSelectUniverse(universe.key) },
                                label = { AppText(universe.name) },
                            )
                        }
                    }
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
                        minLines = 3,
                    )
                }
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
private fun TargetCaseControls(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        QuickDictionaryCaseTransform.entries.forEach { transform ->
            val description = stringResource(transform.descriptionResource())
            TextButton(
                onClick = {
                    onValueChange(applyQuickDictionaryCaseTransform(value, transform))
                },
                enabled = value.any(Char::isLetter),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 0.dp, minHeight = 40.dp)
                    .semantics { contentDescription = description },
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            ) {
                AppText(
                    text = transform.label,
                    style = LegadoTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
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

private fun QuickDictionaryCaseTransform.descriptionResource(): Int = when (this) {
    QuickDictionaryCaseTransform.LOWERCASE -> R.string.quick_dictionary_case_lowercase
    QuickDictionaryCaseTransform.CAPITALIZE_ONE -> R.string.quick_dictionary_case_capitalize_one
    QuickDictionaryCaseTransform.CAPITALIZE_TWO -> R.string.quick_dictionary_case_capitalize_two
    QuickDictionaryCaseTransform.CAPITALIZE_THREE -> R.string.quick_dictionary_case_capitalize_three
    QuickDictionaryCaseTransform.CAPITALIZE_ALL -> R.string.quick_dictionary_case_capitalize_all
    QuickDictionaryCaseTransform.UPPERCASE -> R.string.quick_dictionary_case_uppercase
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

private const val SOURCE_CONTEXT_CHARS = 14
