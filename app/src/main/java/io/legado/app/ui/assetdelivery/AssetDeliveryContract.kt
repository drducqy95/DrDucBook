package io.legado.app.ui.assetdelivery

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AssetDeliveryUiState(
    val rawUri: String,
    val loading: Boolean = true,
    val configured: Boolean = true,
    val needsSignIn: Boolean = false,
    val title: String = "",
    val items: ImmutableList<AssetDeliveryItemUi> = persistentListOf(),
    val selectedArtifactId: String? = null,
    val status: AssetDeliveryStatus = AssetDeliveryStatus.IDLE,
    val progressBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedPath: String? = null,
    val downloadedMimeType: String? = null,
    val errorMessage: String? = null,
) {
    val selectedItem: AssetDeliveryItemUi?
        get() = items.firstOrNull { it.id == selectedArtifactId }

    val downloading: Boolean
        get() = status == AssetDeliveryStatus.DOWNLOADING

    val verified: Boolean
        get() = status == AssetDeliveryStatus.VERIFIED && downloadedPath != null

    val importing: Boolean
        get() = status == AssetDeliveryStatus.IMPORTING

    val imported: Boolean
        get() = status == AssetDeliveryStatus.IMPORTED && downloadedPath != null

    val downloaded: Boolean
        get() = downloadedPath != null && status in setOf(
            AssetDeliveryStatus.VERIFIED,
            AssetDeliveryStatus.IMPORTING,
            AssetDeliveryStatus.IMPORTED,
            AssetDeliveryStatus.IMPORT_FAILED,
        )
}

@Stable
data class AssetDeliveryItemUi(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeText: String,
    val detail: String,
    val sha256Short: String,
)

enum class AssetDeliveryStatus {
    IDLE,
    DOWNLOADING,
    VERIFIED,
    IMPORTING,
    IMPORTED,
    IMPORT_FAILED,
    ERROR,
}

sealed interface AssetDeliveryIntent {
    data object Refresh : AssetDeliveryIntent
    data class SelectArtifact(val artifactId: String) : AssetDeliveryIntent
    data object DownloadSelected : AssetDeliveryIntent
    data object RetryImport : AssetDeliveryIntent
    data object OpenDownloaded : AssetDeliveryIntent
    data object OpenAccount : AssetDeliveryIntent
    data object CancelDownload : AssetDeliveryIntent
}

sealed interface AssetDeliveryEffect {
    data class ShowMessage(val message: String) : AssetDeliveryEffect
    data class OpenFile(val path: String, val mimeType: String?) : AssetDeliveryEffect
    data object OpenAccount : AssetDeliveryEffect
}
