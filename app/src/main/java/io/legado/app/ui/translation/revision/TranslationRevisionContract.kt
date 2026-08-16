package io.legado.app.ui.translation.revision

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.RevisionStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class TranslationRevisionItemUi(
    val revisionId: String,
    val content: String,
    val status: RevisionStatus,
    val actor: String,
    val updatedAt: Long,
    val stale: Boolean,
)

@Stable
data class TranslationRevisionUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val rawContent: String = "",
    val editedContent: String = "",
    val status: RevisionStatus? = null,
    val history: ImmutableList<TranslationRevisionItemUi> = persistentListOf(),
    val dialog: TranslationRevisionDialog? = null,
    val errorMessage: String? = null,
)

sealed interface TranslationRevisionDialog {
    data object Finalize : TranslationRevisionDialog
    data object Unlock : TranslationRevisionDialog
    data class Restore(val revisionId: String) : TranslationRevisionDialog
}

sealed interface TranslationRevisionIntent {
    data class Load(
        val bookUrl: String,
        val chapterIndex: Int,
        val targetLanguage: String,
        val provider: String,
    ) : TranslationRevisionIntent

    data class Edit(val content: String) : TranslationRevisionIntent
    data object Save : TranslationRevisionIntent
    data object RequestFinalize : TranslationRevisionIntent
    data object RequestUnlock : TranslationRevisionIntent
    data class RequestRestore(val revisionId: String) : TranslationRevisionIntent
    data object ConfirmDialog : TranslationRevisionIntent
    data object DismissDialog : TranslationRevisionIntent
    data object Refresh : TranslationRevisionIntent
}

sealed interface TranslationRevisionEffect {
    data class ShowMessage(val message: String) : TranslationRevisionEffect
}
