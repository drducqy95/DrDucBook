package io.legado.app.ui.book.entity

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.legado.app.domain.model.QuickDictionaryType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class EntityCandidateUi(
    val raw: String,
    val hanViet: String,
    val target: String,
    val type: QuickDictionaryType,
    val occurrences: Int,
    val chapterCount: Int,
    val firstChapterTitle: String,
    val context: String,
    val selected: Boolean = false,
)

sealed interface EntityAnalyzerDialog {
    data class Edit(
        val raw: String,
        val hanViet: String,
        val target: String,
        val type: QuickDictionaryType,
    ) : EntityAnalyzerDialog

    data class ConfirmImport(val count: Int) : EntityAnalyzerDialog
}

@Stable
data class EntityAnalyzerUiState(
    val bookName: String = "",
    val analyzing: Boolean = true,
    val importing: Boolean = false,
    val scannedChapters: Int = 0,
    val totalChapters: Int = 0,
    val downloadedChapters: Int = 0,
    val trackedCandidates: Int = 0,
    val candidateCount: Int = 0,
    val selectedCount: Int = 0,
    val searchQuery: String = "",
    val candidates: ImmutableList<EntityCandidateUi> = persistentListOf(),
    @StringRes val errorRes: Int? = null,
    val dialog: EntityAnalyzerDialog? = null,
)

sealed interface EntityAnalyzerIntent {
    data object Analyze : EntityAnalyzerIntent
    data object CancelAnalysis : EntityAnalyzerIntent
    data class Search(val query: String) : EntityAnalyzerIntent
    data class ToggleCandidate(val raw: String) : EntityAnalyzerIntent
    data object SelectVisible : EntityAnalyzerIntent
    data object ClearSelection : EntityAnalyzerIntent
    data class EditCandidate(val raw: String) : EntityAnalyzerIntent
    data class UpdateEditHanViet(val value: String) : EntityAnalyzerIntent
    data class UpdateEditTarget(val value: String) : EntityAnalyzerIntent
    data class UpdateEditType(val value: QuickDictionaryType) : EntityAnalyzerIntent
    data object SaveEdit : EntityAnalyzerIntent
    data object RequestImport : EntityAnalyzerIntent
    data object ConfirmImport : EntityAnalyzerIntent
    data object DismissDialog : EntityAnalyzerIntent
}

sealed interface EntityAnalyzerEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
        val count: Int? = null,
    ) : EntityAnalyzerEffect
}
