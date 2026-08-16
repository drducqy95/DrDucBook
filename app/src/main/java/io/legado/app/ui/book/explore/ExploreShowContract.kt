package io.legado.app.ui.book.explore

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.BookShelfState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Stable
data class ExploreShowUiState(
    val sourceUrl: String? = null,
    val title: String? = null,
    val books: ImmutableList<ExploreBookItemUi> = persistentListOf(),
    val kinds: ImmutableList<ExploreKind> = persistentListOf(),
    val kindDisplayNames: ImmutableMap<String, String> = persistentMapOf(),
    val selectedKindTitle: String? = null,
    val layoutState: Int = 0,
    val gridCount: Int = 3,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEnd: Boolean = false,
    val errorMsg: String? = null,
    val sheet: ExploreShowSheet = ExploreShowSheet.None,
)

@Stable
data class ExploreBookItemUi(
    val book: SearchBook,
    val displayBook: SearchBook = book,
    val shelfState: BookShelfState = BookShelfState.NOT_IN_SHELF,
    // SearchBook.equals() compares only bookUrl. Keep display content in equality so StateFlow
    // emits when asynchronous QT metadata arrives for the same source book.
    val displayContentKey: Int = listOf(
        displayBook.name,
        displayBook.author,
        displayBook.originName,
        displayBook.kind,
        displayBook.intro,
        displayBook.latestChapterTitle,
        displayBook.wordCount,
        displayBook.chapterWordCountText,
    ).hashCode(),
)

sealed interface ExploreShowSheet {
    data object None : ExploreShowSheet
    data object KindSelect : ExploreShowSheet
    data object GridCount : ExploreShowSheet
}

sealed interface ExploreShowIntent {
    data class InitData(
        val sourceUrl: String,
        val exploreUrl: String?,
        val title: String? = null,
    ) : ExploreShowIntent

    data object LoadMore : ExploreShowIntent
    data object ForceLoadNext : ExploreShowIntent
    data object Refresh : ExploreShowIntent
    data class SwitchKind(val kind: ExploreKind) : ExploreShowIntent
    data object ToggleLayout : ExploreShowIntent
    data class SaveGridCount(val count: Int) : ExploreShowIntent
    data class ShowSheet(val sheet: ExploreShowSheet) : ExploreShowIntent
    data object DismissSheet : ExploreShowIntent
    data class OpenBook(val book: SearchBook, val sharedCoverKey: String?) : ExploreShowIntent
    data class OpenBookInfo(val book: SearchBook, val sharedCoverKey: String?) : ExploreShowIntent
    data class SelectBookText(val book: SearchBook, val displayBook: SearchBook) : ExploreShowIntent
    data class AddToShelf(val book: SearchBook) : ExploreShowIntent
}

sealed interface ExploreShowEffect {
    data class OpenReader(val book: SearchBook) : ExploreShowEffect

    data class OpenBookInfo(
        val name: String,
        val author: String,
        val bookUrl: String,
        val origin: String?,
        val coverPath: String?,
        val sharedCoverKey: String?,
    ) : ExploreShowEffect

    data class ShowMessage(val message: String) : ExploreShowEffect

    data class OpenQuickDictionary(
        val projectKey: String,
        val initialText: String,
    ) : ExploreShowEffect
}
