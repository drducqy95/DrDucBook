package io.legado.app.ui.config.translation.prompt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.TranslationPromptStage
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun TranslationPromptConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: TranslationPromptConfigViewModel = koinViewModel(),
) {
    TranslationPromptConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationPromptConfigScreen(
    state: TranslationPromptConfigUiState,
    effects: Flow<TranslationPromptConfigEffect>,
    onIntent: (TranslationPromptConfigIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is TranslationPromptConfigEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.translation_prompt_pipeline),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(TranslationPromptConfigIntent.Add()) },
                icon = Icons.Default.Add,
                tooltipText = stringResource(R.string.translation_prompt_add),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
        ) {
            TranslationPromptStage.entries.forEach { stage ->
                val stageItems = state.items.filter { it.stage == stage }
                item(key = stage.name) {
                    SplicedColumnGroup(title = stringResource(stage.labelResource())) {
                        if (stageItems.isEmpty()) {
                            ClickableSettingItem(
                                title = stringResource(R.string.translation_prompt_stage_empty),
                                description = stringResource(R.string.translation_prompt_add),
                                onClick = { onIntent(TranslationPromptConfigIntent.Add(stage)) },
                            )
                        } else {
                            stageItems.forEach { item ->
                                ClickableSettingItem(
                                    title = item.name,
                                    description = item.instruction,
                                    trailingContent = {
                                        Switch(
                                            checked = item.enabled,
                                            onCheckedChange = {
                                                onIntent(TranslationPromptConfigIntent.Toggle(item, it))
                                            },
                                        )
                                    },
                                    onClick = { onIntent(TranslationPromptConfigIntent.Edit(item)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val editor = state.editor
    AppModalBottomSheet(
        show = editor != null,
        onDismissRequest = { onIntent(TranslationPromptConfigIntent.CloseEditor) },
        title = stringResource(
            if (editor?.id == null) R.string.translation_prompt_add
            else R.string.translation_prompt_edit
        ),
    ) {
        if (editor != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(stringResource(R.string.translation_prompt_stage))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TranslationPromptStage.entries.forEach { stage ->
                        FilterChip(
                            selected = stage == editor.stage,
                            onClick = {
                                onIntent(TranslationPromptConfigIntent.UpdateStage(stage))
                            },
                            label = { AppText(stringResource(stage.labelResource())) },
                        )
                    }
                }
                AppTextField(
                    value = editor.name,
                    onValueChange = { onIntent(TranslationPromptConfigIntent.UpdateName(it)) },
                    label = stringResource(R.string.translation_prompt_name),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                AppTextField(
                    value = editor.instruction,
                    onValueChange = {
                        onIntent(TranslationPromptConfigIntent.UpdateInstruction(it))
                    },
                    label = stringResource(R.string.translation_prompt_instruction),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp),
                    singleLine = false,
                    maxLines = 12,
                )
                editor.errorMessage?.let { AppText(it) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (editor.id != null) {
                        val item = state.items.firstOrNull { it.id == editor.id }
                        OutlinedButton(
                            onClick = {
                                if (item != null) {
                                    onIntent(TranslationPromptConfigIntent.CloseEditor)
                                    onIntent(TranslationPromptConfigIntent.RequestDelete(item))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(stringResource(R.string.delete))
                        }
                    }
                    Button(
                        onClick = { onIntent(TranslationPromptConfigIntent.SaveEditor) },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }

    val deleteItem = state.deleteItem
    AppAlertDialog(
        show = deleteItem != null,
        onDismissRequest = { onIntent(TranslationPromptConfigIntent.CancelDelete) },
        title = stringResource(R.string.translation_prompt_delete),
        confirmText = stringResource(R.string.delete),
        onConfirm = { onIntent(TranslationPromptConfigIntent.ConfirmDelete) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(TranslationPromptConfigIntent.CancelDelete) },
        content = {
            AppText(deleteItem?.name.orEmpty())
        },
    )
}

private fun TranslationPromptStage.labelResource(): Int = when (this) {
    TranslationPromptStage.PREPARE -> R.string.translation_prompt_stage_prepare
    TranslationPromptStage.FILTER -> R.string.translation_prompt_stage_filter
    TranslationPromptStage.DICTIONARY -> R.string.translation_prompt_stage_dictionary
    TranslationPromptStage.TRANSLATE -> R.string.translation_prompt_stage_translate
    TranslationPromptStage.RETRANSLATE -> R.string.translation_prompt_stage_retranslate
}
