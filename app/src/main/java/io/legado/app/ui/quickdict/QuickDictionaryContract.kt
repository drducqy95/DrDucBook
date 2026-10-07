package io.legado.app.ui.quickdict

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryUniverse
import io.legado.app.domain.model.MappedDisplayText
import io.legado.app.domain.model.TranslationConstants
import com.drducbook.app.R
import io.legado.app.ui.translation.TranslationCaseTransform
import io.legado.app.ui.translation.applyTranslationCaseTransform
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

enum class StoryMemoryCategory(
    val entityType: String?,
    val worldCategory: String?,
) {
    CHARACTER("character", null),
    FACTION(null, "faction"),
    LOCATION(null, "location"),
    ARTIFACT(null, "weapon"),
    TECHNIQUE(null, "technique"),
    REALM(null, "rank"),
    TERM(null, "term"),
}

fun StoryMemoryCategory.labelResource(): Int = when (this) {
    StoryMemoryCategory.CHARACTER -> R.string.story_memory_cat_character
    StoryMemoryCategory.FACTION -> R.string.story_memory_cat_faction
    StoryMemoryCategory.LOCATION -> R.string.story_memory_cat_location
    StoryMemoryCategory.ARTIFACT -> R.string.story_memory_cat_artifact
    StoryMemoryCategory.TECHNIQUE -> R.string.story_memory_cat_technique
    StoryMemoryCategory.REALM -> R.string.story_memory_cat_realm
    StoryMemoryCategory.TERM -> R.string.story_memory_cat_term
}

@Stable
data class QuickDictionaryProviderUi(
    val value: String,
    val label: String,
)

@Stable
data class QuickDictionarySuggestionUi(
    val provider: String,
    val providerLabel: String,
    val text: String,
)

@Stable
data class QuickDictionarySelectionAlternativeUi(
    val raw: String,
    val contextBefore: String,
    val contextAfter: String,
)

fun withHanVietSuggestion(
    suggestions: List<QuickDictionarySuggestionUi>,
    hanViet: String,
): ImmutableList<QuickDictionarySuggestionUi> {
    val cleanHanViet = hanViet.trim()
    val otherSuggestions = suggestions.filterNot { it.provider == "han_viet" }
    return if (cleanHanViet.isNotBlank()) {
        (listOf(
            QuickDictionarySuggestionUi(
                provider = "han_viet",
                providerLabel = "Hán Việt",
                text = cleanHanViet,
            )
        ) + otherSuggestions).distinctBy { it.provider to it.text.trim().lowercase() }.toImmutableList()
    } else {
        otherSuggestions.toImmutableList()
    }
}

@Stable
data class QuickDictionaryUiState(
    val raw: String = "",
    val hanViet: String = "",
    val target: String = "",
    val contextBefore: String = "",
    val contextAfter: String = "",
    val sourceLocation: String = "",
    val sourceUrl: String = "",
    val selectedProvider: String = TranslationConstants.PROVIDER_QUICK_TRANSLATOR,
    val providerOptions: ImmutableList<QuickDictionaryProviderUi> = persistentListOf(),
    val suggestions: ImmutableList<QuickDictionarySuggestionUi> = persistentListOf(),
    val isSuggesting: Boolean = false,
    val type: QuickDictionaryType = QuickDictionaryType.VIETPHRASE,
    val scope: QuickDictionaryScope = QuickDictionaryScope.PROJECT,
    val universeKey: String = "",
    val universeName: String = "",
    val contextMarkers: String = "",
    val availableUniverses: ImmutableList<QuickDictionaryUniverse> = persistentListOf(),
    val canExpandSelectionLeft: Boolean = false,
    val canExpandSelectionRight: Boolean = false,
    val canShrinkSelectionLeft: Boolean = false,
    val canShrinkSelectionRight: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val selectionAlternatives: ImmutableList<QuickDictionarySelectionAlternativeUi> = persistentListOf(),
    val showSelectionChooser: Boolean = false,
    val saveToTranslationMemory: Boolean = false,
    val memoryCategory: StoryMemoryCategory = StoryMemoryCategory.TERM,
    val memoryDescription: String = "",
)

enum class QuickDictionarySelectionAction {
    EXPAND_LEFT,
    EXPAND_RIGHT,
    SHRINK_LEFT,
    SHRINK_RIGHT,
}

typealias QuickDictionaryCaseTransform = TranslationCaseTransform

internal fun applyQuickDictionaryCaseTransform(
    value: String,
    transform: QuickDictionaryCaseTransform,
): String = when (transform) {
    else -> applyTranslationCaseTransform(value, transform)
}

@Stable
data class QuickDictionaryRequest(
    val bookUrl: String,
    val selectedText: String,
    val sourceText: String,
    val displayText: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val sourceLocation: String,
    val sourceUrl: String = "",
    val mappedDisplayText: MappedDisplayText? = null,
)

sealed interface QuickDictionaryEditorIntent {
    data class Load(val request: QuickDictionaryRequest) : QuickDictionaryEditorIntent
    data class SetRaw(val value: String) : QuickDictionaryEditorIntent
    data class SetHanViet(val value: String) : QuickDictionaryEditorIntent
    data class SetTarget(val value: String) : QuickDictionaryEditorIntent
    data class RequestSuggestion(val provider: String) : QuickDictionaryEditorIntent
    data class RequestMlKitPairSuggestion(val pair: io.legado.app.domain.model.MlKitLanguagePair) : QuickDictionaryEditorIntent
    data class ApplySuggestion(val value: String) : QuickDictionaryEditorIntent
    data class SetType(val value: QuickDictionaryType) : QuickDictionaryEditorIntent
    data class SetScope(val value: QuickDictionaryScope) : QuickDictionaryEditorIntent
    data class AdjustSelection(
        val action: QuickDictionarySelectionAction,
    ) : QuickDictionaryEditorIntent
    data class SelectUniverse(val key: String) : QuickDictionaryEditorIntent
    data class SetUniverseName(val value: String) : QuickDictionaryEditorIntent
    data class SetContextMarkers(val value: String) : QuickDictionaryEditorIntent
    data class SetSaveToTranslationMemory(val value: Boolean) : QuickDictionaryEditorIntent
    data class SetMemoryCategory(val value: StoryMemoryCategory) : QuickDictionaryEditorIntent
    data class SetMemoryDescription(val value: String) : QuickDictionaryEditorIntent
    data class SelectMappingAlternative(val index: Int) : QuickDictionaryEditorIntent
    data object DismissMappingAlternatives : QuickDictionaryEditorIntent
    data object Save : QuickDictionaryEditorIntent
}

sealed interface QuickDictionaryEditorEffect {
    data object Saved : QuickDictionaryEditorEffect
    data class ShowMessage(val message: String) : QuickDictionaryEditorEffect
}
