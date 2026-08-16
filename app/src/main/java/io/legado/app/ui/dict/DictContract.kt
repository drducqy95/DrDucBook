package io.legado.app.ui.dict

import androidx.compose.runtime.Stable
import androidx.annotation.StringRes
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class DictUiState(
    val word: String = "",
    val rules: ImmutableList<DictRuleUi> = persistentListOf(),
    val selectedIndex: Int = 0,
    val pages: ImmutableList<DictPageUiState> = persistentListOf(),
    val emptyReason: DictEmptyReason? = null,
)

@Stable
data class DictPageUiState(
    val isLoading: Boolean = false,
    val htmlContent: String = "",
    val quickLookup: QuickLookupUiState? = null,
    val emptyReason: DictEmptyReason? = null,
)

@Stable
data class DictRuleUi(
    val name: String = "",
    @StringRes val nameRes: Int? = null,
)

@Stable
data class QuickLookupUiState(
    val hanViet: String,
    val translation: String,
    val scopedEntries: ImmutableList<QuickLookupEntryUi> = persistentListOf(),
)

@Stable
data class QuickLookupEntryUi(
    val raw: String,
    val value: String,
    val type: QuickDictionaryType,
    val scope: QuickDictionaryScope,
)

sealed interface DictEmptyReason {
    data object BlankWord : DictEmptyReason
    data object NoRules : DictEmptyReason
    data object NoResult : DictEmptyReason
}

sealed interface DictIntent {
    data class Load(val word: String, val bookUrl: String? = null) : DictIntent
    data class SelectRule(val index: Int) : DictIntent
}

sealed interface DictEffect {
    data class ShowToast(val message: String) : DictEffect
}
