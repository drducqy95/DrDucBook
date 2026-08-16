package io.legado.app.ui.workspace

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class WorkspaceModule {
    WRITING,
    EBOOK_EDITOR,
    AGENT,
    RSS,
    STORY_WIKI,
}

@Stable
data class WorkspaceModuleUi(
    val module: WorkspaceModule,
    val available: Boolean = true,
    val badgeCount: Int? = null,
)

@Stable
data class WorkspaceRecentUi(
    val id: String,
    val module: WorkspaceModule,
    val title: String,
    val updatedAt: Long,
)

@Stable
data class WorkspaceUiState(
    val isLoading: Boolean = true,
    val modules: ImmutableList<WorkspaceModuleUi> = persistentListOf(),
    val recentItems: ImmutableList<WorkspaceRecentUi> = persistentListOf(),
    val hasError: Boolean = false,
)

sealed interface WorkspaceIntent {
    data object Refresh : WorkspaceIntent
    data class OpenModule(val module: WorkspaceModule) : WorkspaceIntent
}

sealed interface WorkspaceEffect {
    data class OpenModule(val module: WorkspaceModule) : WorkspaceEffect
}
