package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.domain.model.RevisionStatus
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.TranslationOptionUi
import io.legado.app.ui.book.read.TranslationProgressUiState
import io.legado.app.ui.book.read.TranslationUiStatus
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun TranslationProgressSheet(
    show: Boolean,
    state: TranslationProgressUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val translationLogText = state.logs.joinToString("\n").ifBlank {
        stringResource(R.string.translation_log_empty)
    }
    val translationLogScrollState = rememberScrollState()
    LaunchedEffect(translationLogText) {
        translationLogScrollState.scrollTo(translationLogScrollState.maxValue)
    }
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.translation_sheet_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = state.chapterTitle,
                style = LegadoTheme.typography.titleMedium,
            )

            TranslationSelector(
                title = stringResource(R.string.translation_provider),
                selectedValue = state.provider,
                options = state.providerOptions,
                enabled = state.status != TranslationUiStatus.TRANSLATING,
                onSelected = { onIntent(ReadBookIntent.SelectTranslationProvider(it)) },
            )
            if (
                state.provider == TranslationConstants.PROVIDER_APP_AI &&
                state.aiSelectionLabel.isNotBlank()
            ) {
                AppText(
                    text = stringResource(
                        if (state.aiSelectionIsCombo) {
                            R.string.ai_prompt_editor_combo_entry
                        } else {
                            R.string.ai_prompt_editor_model_entry
                        },
                        state.aiSelectionLabel,
                    ),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            TranslationSelector(
                title = stringResource(R.string.ai_translation_target_language),
                selectedValue = state.targetLanguage,
                options = state.targetLanguageOptions,
                enabled = state.status != TranslationUiStatus.TRANSLATING,
                onSelected = { onIntent(ReadBookIntent.SelectTranslationTargetLanguage(it)) },
            )
            if (state.provider == TranslationConstants.PROVIDER_QUICK_TRANSLATOR) {
                TranslationSelector(
                    title = stringResource(R.string.quick_translation_book_pronoun_mode),
                    selectedValue = state.quickTranslationPronounMode,
                    options = state.quickTranslationPronounModeOptions,
                    enabled = state.status != TranslationUiStatus.TRANSLATING,
                    onSelected = { onIntent(ReadBookIntent.SelectQuickTranslationPronounMode(it)) },
                )
            }

            HorizontalDivider()
            TranslationSwitchRow(
                title = stringResource(R.string.translation_display_result),
                checked = state.displayTranslation,
                enabled = state.hasCachedTranslation,
                onCheckedChange = { onIntent(ReadBookIntent.ToggleTranslation) },
            )
            TranslationSwitchRow(
                title = stringResource(R.string.translation_auto_mode),
                checked = state.autoTranslateEnabled,
                onCheckedChange = { onIntent(ReadBookIntent.SetAutoTranslateEnabled(it)) },
            )
            TranslationSwitchRow(
                title = stringResource(R.string.translation_auto_wifi_only),
                checked = state.autoTranslateWifiOnly,
                enabled = state.autoTranslateEnabled,
                onCheckedChange = { onIntent(ReadBookIntent.SetAutoTranslateWifiOnly(it)) },
            )
            if (state.autoTranslateEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppText(stringResource(R.string.translation_auto_next_chapters))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                onIntent(
                                    ReadBookIntent.SetAutoTranslateNextChapters(
                                        (state.autoTranslateNextChapters - 1).coerceAtLeast(0)
                                    )
                                )
                            },
                        ) { AppText("-") }
                        AppText(state.autoTranslateNextChapters.toString())
                        TextButton(
                            onClick = {
                                onIntent(
                                    ReadBookIntent.SetAutoTranslateNextChapters(
                                        (state.autoTranslateNextChapters + 1).coerceAtMost(20)
                                    )
                                )
                            },
                        ) { AppText("+") }
                    }
                }
                if (state.autoTranslateTotalChapters > 0 || state.autoTranslateMessage != null) {
                    NormalCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AppText(
                                text = stringResource(
                                    R.string.translation_auto_progress,
                                    state.autoTranslateCompletedChapters,
                                    state.autoTranslateTotalChapters,
                                ),
                                style = LegadoTheme.typography.titleSmall,
                            )
                            if (state.autoTranslateTotalChapters > 0) {
                                LinearProgressIndicator(
                                    progress = {
                                        (state.autoTranslateCompletedChapters.toFloat() /
                                            state.autoTranslateTotalChapters.toFloat())
                                            .coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (state.autoTranslateCurrentChapter.isNotBlank()) {
                                AppText(
                                    text = stringResource(
                                        R.string.translation_auto_current_chapter,
                                        state.autoTranslateCurrentChapter,
                                    ),
                                    style = LegadoTheme.typography.bodySmall,
                                )
                            }
                            state.autoTranslateMessage?.let { message ->
                                AppText(
                                    text = message,
                                    style = LegadoTheme.typography.bodySmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            TranslationStatus(state)

            if (state.revisionStale) {
                AppText(
                    text = stringResource(R.string.translation_revision_stale_warning),
                    color = LegadoTheme.colorScheme.error,
                    style = LegadoTheme.typography.bodySmall,
                )
            } else if (state.revisionStatus == RevisionStatus.FINAL ||
                state.revisionStatus == RevisionStatus.USER_EDITED
            ) {
                AppText(
                    text = stringResource(R.string.translation_revision_protected_notice),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    style = LegadoTheme.typography.bodySmall,
                )
            }

            if (state.hasCachedTranslation) {
                TextButton(
                    onClick = { onIntent(ReadBookIntent.OpenTranslationRevision) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    AppText(stringResource(R.string.translation_revision_open))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                when (state.status) {
                    TranslationUiStatus.TRANSLATING -> OutlinedButton(
                        onClick = { onIntent(ReadBookIntent.CancelCurrentChapterTranslation) },
                    ) {
                        AppText(stringResource(R.string.stop))
                    }

                    TranslationUiStatus.COMPLETED -> OutlinedButton(
                        enabled = state.revisionStatus != RevisionStatus.FINAL &&
                            state.revisionStatus != RevisionStatus.USER_EDITED,
                        onClick = { onIntent(ReadBookIntent.RetranslateCurrentChapter) },
                    ) {
                        AppText(stringResource(R.string.retranslate_chapter))
                    }

                    else -> Button(
                        onClick = { onIntent(ReadBookIntent.StartCurrentChapterTranslation) },
                    ) {
                        AppText(stringResource(R.string.translate_chapter))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = stringResource(R.string.translation_log),
                    style = LegadoTheme.typography.titleSmall,
                )
                TextButton(
                    enabled = state.logs.isNotEmpty(),
                    onClick = { onIntent(ReadBookIntent.CopyTranslationLog) },
                ) {
                    AppText(stringResource(android.R.string.copy))
                }
            }
            NormalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 240.dp),
                containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
            ) {
                SelectionContainer {
                    AppText(
                        text = translationLogText,
                        modifier = Modifier
                            .verticalScroll(translationLogScrollState)
                            .padding(12.dp),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TranslationStatus(state: TranslationProgressUiState) {
    val statusText = when (state.status) {
        TranslationUiStatus.IDLE -> stringResource(R.string.translation_status_idle)
        TranslationUiStatus.TRANSLATING -> stringResource(R.string.translation_status_running)
        TranslationUiStatus.COMPLETED -> stringResource(R.string.translation_status_completed)
        TranslationUiStatus.FAILED -> stringResource(R.string.translation_status_failed)
        TranslationUiStatus.CANCELLED -> stringResource(R.string.translation_status_cancelled)
    }
    AppText(
        text = statusText,
        color = if (state.status == TranslationUiStatus.FAILED) {
            LegadoTheme.colorScheme.error
        } else {
            LegadoTheme.colorScheme.onSurface
        },
    )
    if (state.status == TranslationUiStatus.TRANSLATING) {
        val progress = if (state.totalChunks > 0) {
            state.currentChunk.toFloat() / state.totalChunks.toFloat()
        } else {
            0f
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        AppText(
            text = stringResource(
                R.string.translation_chunk_progress,
                state.currentChunk,
                state.totalChunks,
            ),
            style = LegadoTheme.typography.bodySmall,
        )
    }
    state.errorMessage?.let {
        Spacer(Modifier.height(8.dp))
        AppText(it, color = LegadoTheme.colorScheme.error)
    }
}

@Composable
private fun TranslationSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = checked,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppText(
            text = title,
            color = if (enabled) {
                LegadoTheme.colorScheme.onSurface
            } else {
                LegadoTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun TranslationSelector(
    title: String,
    selectedValue: String,
    options: List<TranslationOptionUi>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(title)
        Box {
            OutlinedButton(
                enabled = enabled,
                onClick = { expanded = true },
            ) {
                AppText(selectedLabel)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { AppText(option.label) },
                        onClick = {
                            expanded = false
                            onSelected(option.value)
                        },
                    )
                }
            }
        }
    }
}
