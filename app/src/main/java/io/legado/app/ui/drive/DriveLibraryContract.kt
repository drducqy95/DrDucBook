package io.legado.app.ui.drive

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.domain.model.ManagedDriveSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class DriveCatalogItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isImported: Boolean = false,
    val isDownloading: Boolean = false,
    val title: String = name.substringBeforeLast("."),
    val author: String? = null,
    val intro: String? = null,
    val coverUrl: String? = null,
    val format: String = "",
    val isLoadingMetadata: Boolean = false
)

enum class DriveViewMode {
    CATALOG,
    GRID,
    LIST
}

sealed interface DriveLibrarySheet {
    data object AddSource : DriveLibrarySheet
    data class BookPreview(val item: DriveCatalogItem) : DriveLibrarySheet
}

sealed interface DriveLibraryDialog {
    data class ConfirmDelete(val source: ManagedDriveSource) : DriveLibraryDialog
}

@Stable
data class DriveLibraryUiState(
    val sources: ImmutableList<ManagedDriveSource> = persistentListOf(),
    val activeSource: ManagedDriveSource? = null,
    val connectedServerId: Long? = null,
    val currentPath: String = "/",
    val breadcrumbs: ImmutableList<String> = persistentListOf("/"),
    val items: ImmutableList<DriveCatalogItem> = persistentListOf(),
    val loading: Boolean = false,
    val viewMode: DriveViewMode = DriveViewMode.CATALOG,
    val previewBook: DriveCatalogItem? = null,
    val downloadingPaths: ImmutableSet<String> = persistentSetOf(),
    val importedPaths: ImmutableSet<String> = persistentSetOf(),
    val activeSheet: DriveLibrarySheet? = null,
    val activeDialog: DriveLibraryDialog? = null,
    val errorMessage: String? = null
)

sealed interface DriveLibraryIntent {
    data class SelectSource(val sourceId: String) : DriveLibraryIntent
    data class ConnectSource(val source: ManagedDriveSource) : DriveLibraryIntent
    data object Disconnect : DriveLibraryIntent
    data class OpenFolder(val path: String) : DriveLibraryIntent
    data class NavigateBreadcrumb(val index: Int) : DriveLibraryIntent
    data class DownloadAndImport(val item: DriveCatalogItem) : DriveLibraryIntent
    data class OpenBook(val item: DriveCatalogItem) : DriveLibraryIntent
    data class SetViewMode(val mode: DriveViewMode) : DriveLibraryIntent
    data class ShowBookPreview(val item: DriveCatalogItem) : DriveLibraryIntent
    data object DismissBookPreview : DriveLibraryIntent
    data class PrefetchMetadata(val item: DriveCatalogItem) : DriveLibraryIntent
    data object ShowAddSourceSheet : DriveLibraryIntent
    data object DismissSheet : DriveLibraryIntent
    data class ShowDeleteDialog(val source: ManagedDriveSource) : DriveLibraryIntent
    data object DismissDialog : DriveLibraryIntent
    data class ConfirmDeleteSource(val source: ManagedDriveSource) : DriveLibraryIntent
    data object RequestGoogleAuth : DriveLibraryIntent
    data class AddPublicLink(val url: String, val name: String) : DriveLibraryIntent
    data class AddGoogleAccount(val email: String, val rootFolderId: String, val name: String) : DriveLibraryIntent
}

sealed interface DriveLibraryEffect {
    data class ShowToast(val message: String) : DriveLibraryEffect
    data class OpenBookInfo(val bookUrl: String) : DriveLibraryEffect
    data object RequestGoogleAuth : DriveLibraryEffect
}
