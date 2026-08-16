package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetDeliveryCatalogResolverTest {

    @Test
    fun resolvesPinnedDownloadUriToArtifactMetadata() {
        val resolution = AssetDeliveryCatalogResolver.resolve(
            ExternalAssetCatalog.quickTranslationCleanZipUrl,
        )

        val single = resolution as AssetDeliveryResolution.Single
        assertEquals(ExternalAssetCatalog.quickTranslationCleanAssetId, single.artifact.id)
        assertEquals("legado-qt-clean-20260721.zip", single.artifact.fileName)
        assertEquals(AssetDeliveryArtifactKind.TRANSLATION, single.artifact.kind)
    }

    @Test
    fun resolvesPiperCatalogToDownloadableArtifacts() {
        val resolution = AssetDeliveryCatalogResolver.resolve(
            ExternalAssetCatalog.ttsPiperVoiceFolderUrl,
        )

        val catalog = resolution as AssetDeliveryResolution.Catalog
        assertEquals(ExternalAssetCatalog.ttsPiperVoiceCatalogId, catalog.id)
        assertEquals(25, catalog.artifacts.size)
        assertTrue(catalog.artifacts.all { it.id.startsWith("tts-piper-") })
        assertTrue(catalog.artifacts.all { it.mimeType == "application/zip" })
        assertFalse(catalog.artifacts.any { it.id in blockedPiperArtifactIds })
    }

    @Test
    fun rejectsLicensePendingPiperDirectDownloads() {
        blockedPiperArtifactIds.forEach { artifactId ->
            val resolution = AssetDeliveryCatalogResolver.resolve(
                AssetDeliveryCatalog.downloadUri(artifactId),
            )

            assertTrue(resolution is AssetDeliveryResolution.Invalid)
        }
    }

    @Test
    fun resolvesLocalAiCatalogToGgufArtifacts() {
        val resolution = AssetDeliveryCatalogResolver.resolve(
            ExternalAssetCatalog.ggufFolderUrl,
        )

        val catalog = resolution as AssetDeliveryResolution.Catalog
        assertEquals(LocalAiModelCatalog.all.size, catalog.artifacts.size)
        assertTrue(catalog.artifacts.all { it.kind == AssetDeliveryArtifactKind.LOCAL_AI })
        assertTrue(catalog.artifacts.all { it.fileName.endsWith(".gguf") })
    }

    @Test
    fun rejectsUnknownInternalAssetUri() {
        val resolution = AssetDeliveryCatalogResolver.resolve(
            "${AssetDeliveryCatalog.assetUriScheme}://download/missing-package",
        )

        assertTrue(resolution is AssetDeliveryResolution.Invalid)
    }

    private companion object {
        val blockedPiperArtifactIds = setOf(
            "tts-piper-indo_goreng",
            "tts-piper-john",
            "tts-piper-mattheo",
            "tts-piper-mattheo1",
        )
    }
}
