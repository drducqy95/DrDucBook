package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AssetDeliveryDownloadedFile
import io.legado.app.domain.gateway.AssetDeliveryGateway
import io.legado.app.domain.gateway.AssetDeliveryImportGateway
import io.legado.app.domain.gateway.AssetDownloadProgress
import io.legado.app.domain.model.AssetDeliveryArtifact
import io.legado.app.domain.model.AssetDeliveryCatalogResolver
import io.legado.app.domain.model.AssetDeliveryResolution

class AssetDeliveryAuthRequiredException : IllegalStateException("Cần đăng nhập")

class AssetDeliveryUseCase(
    private val assetDeliveryGateway: AssetDeliveryGateway,
    private val assetDeliveryImportGateway: AssetDeliveryImportGateway,
    private val accountAuthUseCase: AccountAuthUseCase,
) {
    val configured: Boolean
        get() = assetDeliveryGateway.configured

    fun resolve(rawUri: String): AssetDeliveryResolution =
        AssetDeliveryCatalogResolver.resolve(rawUri)

    suspend fun hasSession(): Boolean =
        !accountAuthUseCase.currentAccessToken().isNullOrBlank()

    suspend fun downloadArtifact(
        artifact: AssetDeliveryArtifact,
        onProgress: (AssetDownloadProgress) -> Unit,
    ): AssetDeliveryDownloadedFile {
        val accessToken = accountAuthUseCase.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw AssetDeliveryAuthRequiredException()
        return assetDeliveryGateway.downloadArtifact(
            artifact = artifact,
            accessToken = accessToken,
            onProgress = onProgress,
        )
    }

    suspend fun importArtifact(
        artifact: AssetDeliveryArtifact,
        downloadedFile: AssetDeliveryDownloadedFile,
    ): String = assetDeliveryImportGateway.importArtifact(artifact, downloadedFile)
}
