package io.legado.app.ui.translation.memory

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class StoryWikiUiState(
    val loading: Boolean = true,
    val query: String = "",
    val selectedKind: AiTranslationStoryMemoryKind? = null,
    val records: ImmutableList<AiTranslationStoryWikiRecord> = persistentListOf(),
    val selectedRecord: AiTranslationStoryWikiRecord? = null,
    val errorMessage: String? = null,
)

sealed interface StoryWikiIntent {
    data class ChangeQuery(val value: String) : StoryWikiIntent
    data class SelectKind(val value: AiTranslationStoryMemoryKind?) : StoryWikiIntent
    data class SelectRecord(val value: AiTranslationStoryWikiRecord) : StoryWikiIntent
    data object DismissRecord : StoryWikiIntent
    data object OpenSelectedBook : StoryWikiIntent
}

sealed interface StoryWikiEffect {
    data class OpenBook(val bookUrl: String, val bookName: String) : StoryWikiEffect
}
