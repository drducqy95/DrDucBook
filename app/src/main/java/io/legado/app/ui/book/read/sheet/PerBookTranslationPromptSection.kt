package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.ui.book.read.PromptPresetOptionUi
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons

@Composable
fun PerBookCustomPromptSection(
    title: String,
    disabledHint: String,
    editLabel: String,
    editHint: String,
    enabled: Boolean,
    promptText: String,
    sourcePresetName: String,
    isModified: Boolean,
    presets: ImmutableList<PromptPresetOptionUi>,
    isBusy: Boolean,
    onToggle: (Boolean) -> Unit,
    onLoadPreset: (String) -> Unit,
    onUpdateText: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    var isEditing by remember(enabled, promptText.isBlank()) {
        mutableStateOf(enabled && promptText.isBlank())
    }

    TranslationSwitchRow(
        title = title,
        checked = enabled,
        enabled = !isBusy,
        onCheckedChange = {
            onToggle(it)
            if (it && promptText.isBlank()) {
                isEditing = true
            }
        },
    )

    if (enabled) {
        NormalCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isEditing && promptText.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val basedOnText = if (sourcePresetName.isNotBlank()) {
                                stringResource(R.string.per_book_prompt_based_on, sourcePresetName)
                            } else {
                                stringResource(R.string.custom)
                            }
                            val label = if (isModified) {
                                "$basedOnText · ${stringResource(R.string.per_book_prompt_modified)}"
                            } else {
                                basedOnText
                            }
                            AppText(
                                text = label,
                                style = LegadoTheme.typography.titleSmall,
                                color = if (isModified) {
                                    LegadoTheme.colorScheme.primary
                                } else {
                                    LegadoTheme.colorScheme.onSurface
                                },
                            )
                        }

                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = { isEditing = true },
                        ) {
                            AppIcon(
                                imageVector = AppIcons.Edit,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            AppText(stringResource(R.string.edit))
                        }
                    }

                    AppText(
                        text = promptText,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AppText(
                            text = stringResource(R.string.per_book_prompt_load_preset),
                            style = LegadoTheme.typography.titleSmall,
                        )
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                enabled = !isBusy,
                                onClick = { dropdownExpanded = true },
                            ) {
                                AppText(stringResource(R.string.per_book_prompt_choose_preset))
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                            ) {
                                presets.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { AppText(preset.name) },
                                        onClick = {
                                            dropdownExpanded = false
                                            onLoadPreset(preset.id)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (sourcePresetName.isNotBlank()) {
                        val basedOnText = stringResource(R.string.per_book_prompt_based_on, sourcePresetName)
                        val label = if (isModified) {
                            "$basedOnText · ${stringResource(R.string.per_book_prompt_modified)}"
                        } else {
                            basedOnText
                        }
                        AppText(
                            text = label,
                            style = LegadoTheme.typography.bodySmall,
                            color = if (isModified) {
                                LegadoTheme.colorScheme.primary
                            } else {
                                LegadoTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    AppTextField(
                        value = promptText,
                        onValueChange = onUpdateText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 280.dp),
                        enabled = !isBusy,
                        label = editLabel,
                        placeholder = {
                            AppText(editHint)
                        },
                        minLines = 4,
                        maxLines = 12,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        if (promptText.isNotBlank()) {
                            TextButton(
                                onClick = { isEditing = false },
                                enabled = !isBusy,
                            ) {
                                AppText(stringResource(R.string.collapse))
                            }
                        }
                        TextButton(
                            onClick = onClear,
                            enabled = !isBusy,
                        ) {
                            AppText(stringResource(R.string.per_book_prompt_clear))
                        }
                        Button(
                            onClick = {
                                onSave()
                                isEditing = false
                            },
                            enabled = !isBusy && promptText.isNotBlank(),
                        ) {
                            AppText(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    } else {
        AppText(
            text = disabledHint,
            style = LegadoTheme.typography.bodySmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PerBookTranslationPromptSection(
    enabled: Boolean,
    promptText: String,
    sourcePresetName: String,
    isModified: Boolean,
    presets: ImmutableList<PromptPresetOptionUi>,
    globalPromptName: String,
    isTranslating: Boolean,
    onIntent: (ReadBookIntent) -> Unit,
) {
    PerBookCustomPromptSection(
        title = stringResource(R.string.per_book_prompt_enabled),
        disabledHint = stringResource(R.string.per_book_prompt_disabled_hint, globalPromptName),
        editLabel = stringResource(R.string.per_book_prompt_edit_label),
        editHint = stringResource(R.string.per_book_prompt_edit_hint),
        enabled = enabled,
        promptText = promptText,
        sourcePresetName = sourcePresetName,
        isModified = isModified,
        presets = presets,
        isBusy = isTranslating,
        onToggle = { onIntent(ReadBookIntent.TogglePerBookTranslationPrompt(it)) },
        onLoadPreset = { onIntent(ReadBookIntent.LoadPresetIntoPerBookPrompt(it)) },
        onUpdateText = { onIntent(ReadBookIntent.UpdatePerBookPromptText(it)) },
        onSave = { onIntent(ReadBookIntent.SavePerBookPrompt) },
        onClear = { onIntent(ReadBookIntent.ClearPerBookTranslationPrompt) },
    )
}

@Composable
fun PerBookRewritePromptSection(
    enabled: Boolean,
    promptText: String,
    sourcePresetName: String,
    isModified: Boolean,
    presets: ImmutableList<PromptPresetOptionUi>,
    globalPromptName: String,
    isBusy: Boolean,
    onIntent: (ReadBookIntent) -> Unit,
) {
    PerBookCustomPromptSection(
        title = stringResource(R.string.per_book_rewrite_prompt_enabled),
        disabledHint = stringResource(R.string.per_book_rewrite_prompt_disabled_hint, globalPromptName),
        editLabel = stringResource(R.string.per_book_rewrite_prompt_edit_label),
        editHint = stringResource(R.string.per_book_rewrite_prompt_edit_hint),
        enabled = enabled,
        promptText = promptText,
        sourcePresetName = sourcePresetName,
        isModified = isModified,
        presets = presets,
        isBusy = isBusy,
        onToggle = { onIntent(ReadBookIntent.TogglePerBookRewritePrompt(it)) },
        onLoadPreset = { onIntent(ReadBookIntent.LoadPresetIntoPerBookRewritePrompt(it)) },
        onUpdateText = { onIntent(ReadBookIntent.UpdatePerBookRewritePromptText(it)) },
        onSave = { onIntent(ReadBookIntent.SavePerBookRewritePrompt) },
        onClear = { onIntent(ReadBookIntent.ClearPerBookRewritePrompt) },
    )
}
