package io.legado.app.ui.book.source.hub

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.OnlineBookSourceItem
import io.legado.app.domain.model.OnlineSourceCollectionItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

enum class BookSourceHubTab {
    YCKCEO,
    MIAOGONGZI,
}

@Stable
data class BookSourceHubUiState(
    val currentTab: BookSourceHubTab = BookSourceHubTab.YCKCEO,
    val searchQuery: String = "",
    val selectedCategory: String = "ALL",
    val isLoading: Boolean = false,
    val yckceoSources: ImmutableList<OnlineBookSourceItem> = persistentListOf(),
    val miaogongziBundles: ImmutableList<OnlineSourceCollectionItem> = persistentListOf(),
    val importingIds: ImmutableList<String> = persistentListOf(),
    val installedUrls: ImmutableSet<String> = persistentSetOf(),
    val installedNames: ImmutableSet<String> = persistentSetOf(),
    val selectedSourceIds: ImmutableSet<String> = persistentSetOf(),
    val isBatchImporting: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null,
    val showOnlyUninstalled: Boolean = false,
    val error: String? = null,
)

sealed interface BookSourceHubIntent {
    data class SwitchTab(val tab: BookSourceHubTab) : BookSourceHubIntent
    data class ChangeSearchQuery(val query: String) : BookSourceHubIntent
    data class ChangeCategory(val category: String) : BookSourceHubIntent
    data object Search : BookSourceHubIntent
    data object LoadInitialData : BookSourceHubIntent
    data class ImportYckceoSource(val item: OnlineBookSourceItem) : BookSourceHubIntent
    data class ImportBundle(val item: OnlineSourceCollectionItem) : BookSourceHubIntent
    data class ToggleSelectSource(val id: String) : BookSourceHubIntent
    data object SelectAllSources : BookSourceHubIntent
    data object ClearSourceSelection : BookSourceHubIntent
    data object ImportSelectedSources : BookSourceHubIntent
    data object ToggleFilterUninstalled : BookSourceHubIntent
}

sealed interface BookSourceHubEffect {
    data class ShowToast(val message: String) : BookSourceHubEffect
}
