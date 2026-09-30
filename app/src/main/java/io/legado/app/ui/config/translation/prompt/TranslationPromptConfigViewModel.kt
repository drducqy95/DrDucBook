package io.legado.app.ui.config.translation.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.data.entities.AiPromptPreset
import io.legado.app.domain.gateway.AiPromptPresetGateway
import io.legado.app.domain.model.TranslationPromptStage
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.util.UUID

class TranslationPromptConfigViewModel(
    private val gateway: AiPromptPresetGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranslationPromptConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TranslationPromptConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        load()
    }

    fun onIntent(intent: TranslationPromptConfigIntent) {
        when (intent) {
            is TranslationPromptConfigIntent.Add -> {
                _uiState.update { it.copy(editor = newEditor(intent.stage)) }
            }
            is TranslationPromptConfigIntent.Edit -> {
                _uiState.update {
                    it.copy(
                        editor = TranslationPromptEditorUi(
                            id = intent.item.id,
                            stage = intent.item.stage,
                            name = intent.item.name,
                            instruction = intent.item.instruction,
                        )
                    )
                }
            }
            is TranslationPromptConfigIntent.Toggle -> toggle(intent.item, intent.enabled)
            is TranslationPromptConfigIntent.UpdateStage -> selectStage(intent.stage)
            is TranslationPromptConfigIntent.UpdateName -> updateEditor { copy(name = intent.value) }
            is TranslationPromptConfigIntent.UpdateInstruction -> updateEditor { copy(instruction = intent.value) }
            TranslationPromptConfigIntent.SaveEditor -> saveEditor()
            TranslationPromptConfigIntent.CloseEditor -> _uiState.update { it.copy(editor = null) }
            is TranslationPromptConfigIntent.RequestDelete -> _uiState.update { it.copy(deleteItem = intent.item) }
            TranslationPromptConfigIntent.ConfirmDelete -> deleteSelected()
            TranslationPromptConfigIntent.CancelDelete -> _uiState.update { it.copy(deleteItem = null) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val existing = gateway.getByTaskTypePrefix(TranslationPromptStage.TASK_TYPE_PREFIX)
            val defaults = defaultPresets()
            val existingById = existing.associateBy { it.id }
            val presetsToInstall = defaults.mapNotNull { default ->
                val current = existingById[default.id]
                when {
                    current == null -> default
                    current.instruction.trim() == TranslationPromptStage.legacyInstruction(
                        TranslationPromptStage.fromTaskType(current.taskType) ?: return@mapNotNull null,
                    ).trim() -> default.copy(
                        createdAt = current.createdAt,
                        updatedAt = System.currentTimeMillis(),
                    )
                    else -> null
                }
            }
            if (presetsToInstall.isNotEmpty()) {
                gateway.savePresets(presetsToInstall)
                TranslationConfig.promptPipelineInitialized = true
            }
            refresh()
        }
    }

    private suspend fun refresh() {
        val order = TranslationPromptStage.entries.withIndex().associate { it.value to it.index }
        val items = gateway.getByTaskTypePrefix(TranslationPromptStage.TASK_TYPE_PREFIX)
            .mapNotNull { preset ->
                val stage = TranslationPromptStage.fromTaskType(preset.taskType) ?: return@mapNotNull null
                TranslationPromptItemUi(
                    id = preset.id,
                    stage = stage,
                    name = preset.name,
                    instruction = preset.instruction,
                    enabled = preset.enabled,
                    sortNumber = preset.sortNumber,
                )
            }
            .sortedWith(compareBy({ order[it.stage] }, { it.sortNumber }, { it.name }))
            .toImmutableList()
        _uiState.update { it.copy(loading = false, saving = false, items = items) }
    }

    private fun updateEditor(update: TranslationPromptEditorUi.() -> TranslationPromptEditorUi) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.update()?.copy(errorMessage = null))
        }
    }

    private fun selectStage(stage: TranslationPromptStage) {
        val current = _uiState.value.editor ?: return
        if (current.stage == stage) return

        val nextEditor = translationPromptEditorForStage(
            stage = stage,
            currentId = current.id,
            items = _uiState.value.items,
        )
        _uiState.update { it.copy(editor = nextEditor) }
    }

    private fun newEditor(stage: TranslationPromptStage): TranslationPromptEditorUi =
        translationPromptEditorForStage(stage = stage, currentId = null, items = emptyList())

    private fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        if (editor.name.isBlank() || editor.instruction.isBlank()) {
            updateEditor { copy(errorMessage = appCtx.getString(R.string.translation_prompt_required)) }
            return
        }
        val previous = _uiState.value.items.firstOrNull { it.id == editor.id }
        val nextOrder = _uiState.value.items.count { it.stage == editor.stage }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            gateway.savePreset(
                AiPromptPreset(
                    id = editor.id ?: UUID.randomUUID().toString(),
                    taskType = editor.stage.taskType,
                    name = editor.name.trim(),
                    instruction = editor.instruction.trim(),
                    enabled = previous?.enabled ?: true,
                    builtIn = false,
                    sortNumber = previous?.sortNumber ?: nextOrder,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            _uiState.update { it.copy(editor = null) }
            refresh()
            _effects.tryEmit(
                TranslationPromptConfigEffect.ShowMessage(
                    appCtx.getString(R.string.translation_prompt_saved)
                )
            )
        }
    }

    private fun toggle(item: TranslationPromptItemUi, enabled: Boolean) {
        viewModelScope.launch {
            gateway.savePreset(
                AiPromptPreset(
                    id = item.id,
                    taskType = item.stage.taskType,
                    name = item.name,
                    instruction = item.instruction,
                    enabled = enabled,
                    sortNumber = item.sortNumber,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            refresh()
        }
    }

    private fun deleteSelected() {
        val item = _uiState.value.deleteItem ?: return
        viewModelScope.launch {
            gateway.deletePreset(item.id)
            _uiState.update { it.copy(deleteItem = null) }
            refresh()
        }
    }

    private fun defaultPresets(): List<AiPromptPreset> {
        return TranslationPromptStage.entries.mapIndexed { index, stage ->
            AiPromptPreset(
                id = "translation-${stage.storageKey}",
                taskType = stage.taskType,
                name = stage.storageKey.replaceFirstChar(Char::uppercase),
                instruction = TranslationPromptStage.defaultInstruction(stage),
                enabled = true,
                builtIn = false,
                sortNumber = index,
            )
        }
    }
}

internal fun translationPromptEditorForStage(
    stage: TranslationPromptStage,
    currentId: String?,
    items: Iterable<TranslationPromptItemUi>,
): TranslationPromptEditorUi {
    if (currentId == null) {
        return TranslationPromptEditorUi(
            stage = stage,
            name = stage.storageKey.replaceFirstChar(Char::uppercase),
            instruction = TranslationPromptStage.defaultInstruction(stage),
        )
    }

    val item = items
        .asSequence()
        .filter { it.stage == stage && it.id != currentId }
        .minWithOrNull(compareBy({ it.sortNumber }, { it.name }))
    return item?.let {
        TranslationPromptEditorUi(
            id = it.id,
            stage = it.stage,
            name = it.name,
            instruction = it.instruction,
        )
    } ?: TranslationPromptEditorUi(
        stage = stage,
        name = stage.storageKey.replaceFirstChar(Char::uppercase),
        instruction = TranslationPromptStage.defaultInstruction(stage),
    )
}
