package io.legado.app.ui.translation.memory

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.ui.translation.TranslationCaseTransform
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Stable
data class StoryMemoryItemUi(
    val id: String,
    val kind: AiTranslationStoryMemoryKind,
    val title: String,
    val subtitle: String,
    val chapterIndex: Int? = null,
    val imagePath: String? = null,
)

@Stable
data class StoryMemoryEditorDraft(
    val originalId: String? = null,
    val kind: AiTranslationStoryMemoryKind = AiTranslationStoryMemoryKind.ENTITY,
    val primary: String = "",
    val secondary: String = "",
    val senseKey: String = "",
    val type: String = "",
    val description: String = "",
    val chapterIndexText: String = "",
    val lastChapterIndex: Int = -1,
    val aliasesOrRefsText: String = "",
    val gender: String = "",
    val rank: String = "",
    val eventsText: String = "",
    val charactersText: String = "",
    val discoveriesText: String = "",
    val imagePath: String = "",
    val imagePrompt: String = "",
    val imageUpdatedAt: Long = 0L,
    val metadata: String = "",
    val selectedProvider: String = TranslationConstants.PROVIDER_QUICK_TRANSLATOR,
    val suggestions: ImmutableList<TranslationSuggestionUi> = persistentListOf(),
    val isSuggesting: Boolean = false,
)

@Stable
data class TranslationSuggestionUi(
    val provider: String,
    val providerLabel: String,
    val text: String,
)

@Stable
data class AiStoryMemoryBuilderDialogState(
    val isOpen: Boolean = false,
    val mode: Int = MODE_CHAPTERS,
    val startChapter: Int = 0,
    val endChapter: Int = 10,
    val maxChapters: Int = 0,
    val directText: String = "",
    val isRunning: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressMessage: String = "",
) {
    companion object {
        const val MODE_CHAPTERS = 0
        const val MODE_CUSTOM_TEXT = 1
    }
}

@Stable
data class RetrofitCacheDialogState(
    val isOpen: Boolean = false,
    val startChapter: Int = 0,
    val endChapter: Int = 10,
    val maxChapters: Int = 0,
    val isRunning: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressMessage: String = "",
)

@Stable
data class BookStoryMemoryUiState(
    val loading: Boolean = true,
    val selectedKind: AiTranslationStoryMemoryKind? = null,
    val items: ImmutableList<StoryMemoryItemUi> = persistentListOf(),
    val editor: StoryMemoryEditorDraft? = null,
    val builderDialog: AiStoryMemoryBuilderDialogState = AiStoryMemoryBuilderDialogState(),
    val retrofitDialog: RetrofitCacheDialogState = RetrofitCacheDialogState(),
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val counts: ImmutableMap<AiTranslationStoryMemoryKind, Int> = persistentMapOf(),
    val analyzedChapterCount: Int = 0,
    val pendingChapterCount: Int = 0,
)

sealed interface BookStoryMemoryIntent {
    data class SelectKind(val value: AiTranslationStoryMemoryKind?) : BookStoryMemoryIntent
    data class Edit(val item: StoryMemoryItemUi) : BookStoryMemoryIntent
    data class Add(val kind: AiTranslationStoryMemoryKind) : BookStoryMemoryIntent
    data class UpdateEditor(val value: StoryMemoryEditorDraft) : BookStoryMemoryIntent
    data class RequestSuggestion(val provider: String) : BookStoryMemoryIntent
    data class ApplySuggestion(val value: String) : BookStoryMemoryIntent
    data class ApplyCaseTransform(val transform: TranslationCaseTransform) : BookStoryMemoryIntent
    data object DismissEditor : BookStoryMemoryIntent
    data object SaveEditor : BookStoryMemoryIntent
    data object DeleteEditor : BookStoryMemoryIntent
    data object GenerateEditorImage : BookStoryMemoryIntent
    data object GenerateWorldMap : BookStoryMemoryIntent
    data object RequestImport : BookStoryMemoryIntent
    data object RequestExport : BookStoryMemoryIntent
    data object RetryPending : BookStoryMemoryIntent
    data object BackfillCachedChapters : BookStoryMemoryIntent
    data class ImportJson(val content: String) : BookStoryMemoryIntent

    data object OpenAiBuilderDialog : BookStoryMemoryIntent
    data class UpdateAiBuilderDialog(val value: AiStoryMemoryBuilderDialogState) : BookStoryMemoryIntent
    data object DismissAiBuilderDialog : BookStoryMemoryIntent
    data object ExecuteAiBuilder : BookStoryMemoryIntent

    data object OpenRetrofitDialog : BookStoryMemoryIntent
    data class UpdateRetrofitDialog(val value: RetrofitCacheDialogState) : BookStoryMemoryIntent
    data object DismissRetrofitDialog : BookStoryMemoryIntent
    data object ExecuteRetrofit : BookStoryMemoryIntent
}

sealed interface BookStoryMemoryEffect {
    data object OpenImportDocument : BookStoryMemoryEffect
    data class ExportDocument(val content: String, val suggestedName: String) : BookStoryMemoryEffect
    data class ShowMessage(@StringRes val messageRes: Int) : BookStoryMemoryEffect
    data class ShowError(val message: String) : BookStoryMemoryEffect
    data class ShowMessageText(val message: String) : BookStoryMemoryEffect
}
