package io.legado.app.ui.download.center

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.DownloadCenterItem
import io.legado.app.domain.model.DownloadCenterAction
import io.legado.app.domain.model.DownloadItemKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class DownloadCenterUiState(
    val loading: Boolean = true,
    val items: ImmutableList<DownloadCenterItem> = persistentListOf(),
    val filter: DownloadCenterFilter = DownloadCenterFilter.ALL,
    val activeCount: Int = 0,
    val recoverableCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
)

enum class DownloadCenterFilter {
    ALL,
    BOOK,
    MEDIA,
    ACTIVE,
    COMPLETED,
    FAILED,
}

sealed interface DownloadCenterIntent {
    data object Refresh : DownloadCenterIntent
    data class SetFilter(val filter: DownloadCenterFilter) : DownloadCenterIntent
    data class PerformAction(val action: DownloadCenterAction) : DownloadCenterIntent
}
