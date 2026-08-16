package io.legado.app.domain.gateway

import io.legado.app.domain.model.AssetDeliveryArtifact

data class AssetDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
)

data class AssetDeliveryDownloadedFile(
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val mimeType: String,
)

interface AssetDeliveryImportGateway {
    /** Imports a verified package into the matching local subsystem. */
    suspend fun importArtifact(
        artifact: AssetDeliveryArtifact,
        downloadedFile: AssetDeliveryDownloadedFile,
    ): String
}

interface AssetDeliveryGateway {
    val configured: Boolean

    suspend fun downloadArtifact(
        artifact: AssetDeliveryArtifact,
        accessToken: String,
        onProgress: (AssetDownloadProgress) -> Unit,
    ): AssetDeliveryDownloadedFile
}
