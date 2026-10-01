package io.legado.app.ui.translation.memory

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import io.legado.app.domain.model.StoryWikiGraphNode
import io.legado.app.domain.model.StoryWikiRelationshipTag
import io.legado.app.domain.model.StoryWikiSnapshot
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class StoryWikiTab {
    ENTITY,
    WORLD_BUILDING,
    TIMELINE,
    CHARACTER_GRAPH,
}

@Stable
data class StoryWikiUiState(
    val loading: Boolean = true,
    val query: String = "",
    val books: ImmutableList<StoryWikiSnapshot> = persistentListOf(),
    val selectedBookUrl: String? = null,
    val selectedTab: StoryWikiTab = StoryWikiTab.ENTITY,
    val glossaryRecords: ImmutableList<AiTranslationStoryWikiRecord> = persistentListOf(),
    val timelineRecords: ImmutableList<AiTranslationStoryWikiRecord> = persistentListOf(),
    val relationshipTags: ImmutableList<StoryWikiRelationshipTag> = persistentListOf(),
    val characterGraph: io.legado.app.domain.model.StoryWikiCharacterGraph =
        io.legado.app.domain.model.StoryWikiCharacterGraph(),
    val selectedRecord: AiTranslationStoryWikiRecord? = null,
    val selectedGraphNode: StoryWikiGraphNode? = null,
    val errorMessage: String? = null,
)

sealed interface StoryWikiIntent {
    data class ChangeQuery(val value: String) : StoryWikiIntent
    data class SelectBook(val value: String?) : StoryWikiIntent
    data object BackToBookList : StoryWikiIntent
    data class SelectTab(val value: StoryWikiTab) : StoryWikiIntent
    data class SelectRecord(val value: AiTranslationStoryWikiRecord) : StoryWikiIntent
    data class SelectGraphNode(val value: String) : StoryWikiIntent
    data object DismissRecord : StoryWikiIntent
    data object DismissGraphNode : StoryWikiIntent
    data object Refresh : StoryWikiIntent
    data object OpenSelectedBook : StoryWikiIntent
}

sealed interface StoryWikiEffect {
    data class OpenBook(val bookUrl: String, val bookName: String) : StoryWikiEffect
}
