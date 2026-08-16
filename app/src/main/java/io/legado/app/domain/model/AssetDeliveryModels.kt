package io.legado.app.domain.model

import java.net.URI

enum class AssetDeliveryArtifactKind {
    TRANSLATION,
    TTS,
    LOCAL_AI,
}

data class AssetDeliveryArtifact(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val kind: AssetDeliveryArtifactKind,
    val detail: String,
) {
    val mimeType: String
        get() = when (fileName.substringAfterLast('.', "").lowercase()) {
            "zip" -> "application/zip"
            "gguf" -> "application/octet-stream"
            else -> "application/octet-stream"
        }
}

sealed interface AssetDeliveryResolution {
    data class Single(val artifact: AssetDeliveryArtifact) : AssetDeliveryResolution
    data class Catalog(
        val id: String,
        val title: String,
        val artifacts: List<AssetDeliveryArtifact>,
    ) : AssetDeliveryResolution

    data class Invalid(val rawUri: String) : AssetDeliveryResolution
}

object AssetDeliveryCatalogResolver {

    fun resolve(rawUri: String): AssetDeliveryResolution {
        val reference = parseReference(rawUri) ?: return AssetDeliveryResolution.Invalid(rawUri)
        return when (reference.kind) {
            ParsedAssetKind.DOWNLOAD -> allArtifactsById[reference.id]
                ?.let(AssetDeliveryResolution::Single)
                ?: AssetDeliveryResolution.Invalid(rawUri)

            ParsedAssetKind.CATALOG -> {
                val artifacts = catalogArtifacts(reference.id)
                if (artifacts.isEmpty()) {
                    AssetDeliveryResolution.Invalid(rawUri)
                } else {
                    AssetDeliveryResolution.Catalog(
                        id = reference.id,
                        title = catalogTitle(reference.id),
                        artifacts = artifacts,
                    )
                }
            }
        }
    }

    fun allArtifacts(): List<AssetDeliveryArtifact> = allArtifactsById.values.toList()

    private fun catalogArtifacts(catalogId: String): List<AssetDeliveryArtifact> = when (catalogId) {
        ExternalAssetCatalog.ttsPiperVoiceCatalogId -> ExternalAssetCatalog.ttsPiperVoiceAssets
            .filter { it.releaseEligible }
            .map { it.toArtifact() }
            .sortedBy { it.displayName.lowercase() }

        ExternalAssetCatalog.ggufCatalogId -> LocalAiModelCatalog.all
            .map { it.toArtifact() }
            .sortedBy { it.displayName.lowercase() }

        else -> emptyList()
    }

    private fun catalogTitle(catalogId: String): String = when (catalogId) {
        ExternalAssetCatalog.ttsPiperVoiceCatalogId -> "Piper TTS voices"
        ExternalAssetCatalog.ggufCatalogId -> "Hy-MT2 GGUF models"
        else -> catalogId
    }

    private fun parseReference(rawUri: String): ParsedAssetReference? {
        val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
        if (uri.scheme != AssetDeliveryCatalog.assetUriScheme) return null
        val kind = when (uri.host) {
            "download" -> ParsedAssetKind.DOWNLOAD
            "catalog" -> ParsedAssetKind.CATALOG
            else -> return null
        }
        val id = uri.path?.trim('/')?.takeIf(String::isNotBlank) ?: return null
        if (!assetIdRegex.matches(id)) return null
        return ParsedAssetReference(kind = kind, id = id)
    }

    private val allArtifactsById: Map<String, AssetDeliveryArtifact> by lazy {
        buildList {
            addAll(ExternalAssetCatalog.externalPackageAssets.map { it.toArtifact() })
            addAll(ExternalAssetCatalog.releaseEligibleTtsVoiceCatalog.map { it.toArtifact() })
            addAll(LocalAiModelCatalog.all.map { it.toArtifact() })
        }.associateBy { it.id }
    }

    private fun ExternalPackageAsset.toArtifact(): AssetDeliveryArtifact =
        AssetDeliveryArtifact(
            id = id,
            displayName = displayName,
            fileName = fileName,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            kind = AssetDeliveryArtifactKind.TRANSLATION,
            detail = category,
        )

    private fun ExternalTtsVoiceAsset.toArtifact(): AssetDeliveryArtifact =
        AssetDeliveryArtifact(
            id = artifactId,
            displayName = displayName,
            fileName = fileName,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            kind = AssetDeliveryArtifactKind.TTS,
            detail = engine,
        )

    private fun LocalAiCatalogModel.toArtifact(): AssetDeliveryArtifact =
        AssetDeliveryArtifact(
            id = artifactId,
            displayName = fileName.substringBeforeLast('.'),
            fileName = fileName,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            kind = AssetDeliveryArtifactKind.LOCAL_AI,
            detail = "$repository@$revision",
        )

    private data class ParsedAssetReference(
        val kind: ParsedAssetKind,
        val id: String,
    )

    private enum class ParsedAssetKind {
        DOWNLOAD,
        CATALOG,
    }

    private val assetIdRegex = Regex("^[a-z0-9][a-z0-9._-]{1,127}$")
}
