package io.legado.app.ui.config.translation.prompt

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.TranslationPromptStage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class TranslationPromptItemUi(
    val id: String,
    val stage: TranslationPromptStage,
    val name: String,
    val instruction: String,
    val enabled: Boolean,
    val sortNumber: Int,
)

@Stable
data class TranslationPromptEditorUi(
    val id: String? = null,
    val stage: TranslationPromptStage = TranslationPromptStage.PREPARE,
    val name: String = "",
    val instruction: String = "",
    val errorMessage: String? = null,
)

@Stable
data class TranslationPromptConfigUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val items: ImmutableList<TranslationPromptItemUi> = persistentListOf(),
    val editor: TranslationPromptEditorUi? = null,
    val deleteItem: TranslationPromptItemUi? = null,
)

sealed interface TranslationPromptConfigIntent {
    data class Add(val stage: TranslationPromptStage = TranslationPromptStage.PREPARE) : TranslationPromptConfigIntent
    data class Edit(val item: TranslationPromptItemUi) : TranslationPromptConfigIntent
    data class Toggle(val item: TranslationPromptItemUi, val enabled: Boolean) : TranslationPromptConfigIntent
    data class UpdateStage(val stage: TranslationPromptStage) : TranslationPromptConfigIntent
    data class UpdateName(val value: String) : TranslationPromptConfigIntent
    data class UpdateInstruction(val value: String) : TranslationPromptConfigIntent
    data object SaveEditor : TranslationPromptConfigIntent
    data object CloseEditor : TranslationPromptConfigIntent
    data class RequestDelete(val item: TranslationPromptItemUi) : TranslationPromptConfigIntent
    data object ConfirmDelete : TranslationPromptConfigIntent
    data object CancelDelete : TranslationPromptConfigIntent
}

sealed interface TranslationPromptConfigEffect {
    data class ShowMessage(val message: String) : TranslationPromptConfigEffect
}
