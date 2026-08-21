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
                _uiState.update { it.copy(editor = TranslationPromptEditorUi(stage = intent.stage)) }
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
            is TranslationPromptConfigIntent.UpdateStage -> updateEditor { copy(stage = intent.stage) }
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
            if (!TranslationConfig.promptPipelineInitialized) {
                gateway.savePresets(defaultPresets())
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
        val defaults = listOf(
            TranslationPromptStage.PREPARE to "Read the complete supplied excerpt before translating. Preserve paragraph order, dialogue boundaries, names, numbers, and markup tokens.",
            TranslationPromptStage.FILTER to "Treat navigation labels, advertisements, duplicated headers, and unrelated boilerplate as noise; never invent replacements for removed noise.",
            TranslationPromptStage.DICTIONARY to "Use the supplied terminology exactly. Extract only recurring names, places, titles, or setting terms that are useful in later chunks.",
            TranslationPromptStage.TRANSLATE to "Produce complete literary prose in the target language without summaries, commentary, censorship, or omitted sentences. When translating from machine/convert text, convert awkward Sino-Vietnamese sentence patterns into fluent, natural Vietnamese.",
            TranslationPromptStage.RETRANSLATE to "Correct the specific failure reported for the previous attempt while retaining all valid terminology and paragraph structure. Restructure awkward convert patterns, stiff repeated pronouns, and mechanical idioms into smooth Vietnamese prose.",
        )
        return defaults.mapIndexed { index, (stage, instruction) ->
            AiPromptPreset(
                id = "translation-${stage.storageKey}",
                taskType = stage.taskType,
                name = stage.storageKey.replaceFirstChar(Char::uppercase),
                instruction = instruction,
                enabled = true,
                builtIn = false,
                sortNumber = index,
            )
        }
    }
}
