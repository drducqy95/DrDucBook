package io.legado.app.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HfArtifactManifestTest {

    @Test
    fun androidCatalogUsesInternalDeliveryUrisInsteadOfDriveUrls() {
        val urls = buildList {
            add(ExternalAssetCatalog.hachimiOnnxZipUrl)
            add(ExternalAssetCatalog.quickTranslationCleanZipUrl)
            add(ExternalAssetCatalog.ttsPiperVoiceFolderUrl)
            add(ExternalAssetCatalog.ttsValtecModelZipUrl)
            add(ExternalAssetCatalog.ggufFolderUrl)
            addAll(ExternalAssetCatalog.externalPackageAssets.map { it.downloadUrl })
            addAll(ExternalAssetCatalog.releaseEligibleTtsVoiceCatalog.map { it.downloadUrl })
            addAll(LocalAiModelCatalog.all.map { it.downloadUrl })
        }

        assertTrue(urls.isNotEmpty())
        assertTrue(urls.all { it.startsWith("${AssetDeliveryCatalog.assetUriScheme}://") })
        assertFalse(urls.any { it.contains("drive.google.com", ignoreCase = true) })
    }

    @Test
    fun hfManifestCoversEveryPinnedCatalogAsset() {
        val manifest = readManifest()
        assertEquals(1, manifest.int("schemaVersion"))
        assertEquals(AssetDeliveryCatalog.manifestVersion, manifest.string("manifestVersion"))
        assertEquals(AssetDeliveryCatalog.hfRepository, manifest.string("repository"))
        assertEquals(AssetDeliveryCatalog.hfRevision, manifest.string("revision"))

        val artifacts = manifest.getValue("artifacts").jsonArray
        val byId = artifacts
            .map { it.jsonObject }
            .associateBy { it.string("id") }
        val expected = expectedCatalogAssets()

        assertTrue(byId.keys.containsAll(expected.keys))
        expected.forEach { (id, expectedAsset) ->
            val artifact = requireNotNull(byId[id]) { "Missing manifest entry for $id" }
            assertEquals(expectedAsset.fileName, artifact.string("fileName"))
            assertEquals(expectedAsset.sizeBytes, artifact.long("sizeBytes"))
            assertEquals(expectedAsset.sha256, artifact.string("sha256"))
            assertEquals(AssetDeliveryCatalog.hfRepository, artifact.string("hfRepo"))
            assertTrue(artifact.string("hfPath").isNotBlank())
            assertTrue(artifact.string("license").isNotBlank())
            assertTrue(artifact.string("provenance").isNotBlank())
            assertTrue(artifact.string("deliveryClass") in allowedDeliveryClasses)
            assertFalse(artifact.toString().contains("drive.google.com", ignoreCase = true))
        }
    }

    @Test
    fun licensePendingPiperVoicesStayOutOfPublicCatalog() {
        val manifest = readManifest()
        val artifactsById = manifest.getValue("artifacts").jsonArray
            .map { it.jsonObject }
            .associateBy { it.string("id") }
        val publicIds = ExternalAssetCatalog.releaseEligibleTtsVoiceCatalog
            .mapTo(mutableSetOf()) { it.artifactId }

        blockedPiperArtifactIds.forEach { artifactId ->
            val artifact = requireNotNull(artifactsById[artifactId]) {
                "Manifest should retain blocked artifact $artifactId for audit"
            }
            assertFalse(artifactId in publicIds)
            assertEquals("license-review-required", artifact.string("license"))
            assertEquals("local_verified_license_pending", artifact.string("inventoryState"))
        }
    }

    private fun expectedCatalogAssets(): Map<String, ExpectedAsset> = buildMap {
        ExternalAssetCatalog.externalPackageAssets.forEach { asset ->
            put(
                asset.id,
                ExpectedAsset(
                    fileName = asset.fileName,
                    sizeBytes = asset.sizeBytes,
                    sha256 = asset.sha256,
                )
            )
        }
        ExternalAssetCatalog.releaseEligibleTtsVoiceCatalog.forEach { asset ->
            put(
                asset.artifactId,
                ExpectedAsset(
                    fileName = asset.fileName,
                    sizeBytes = asset.sizeBytes,
                    sha256 = asset.sha256,
                )
            )
        }
        LocalAiModelCatalog.all.forEach { model ->
            put(
                model.artifactId,
                ExpectedAsset(
                    fileName = model.fileName,
                    sizeBytes = model.sizeBytes,
                    sha256 = model.sha256,
                )
            )
        }
    }

    private fun readManifest(): JsonObject {
        val path = "supabase/artifacts/hf-artifacts-manifest.json"
        val candidates = listOf(
            File(path),
            File("../$path"),
            File("../../$path"),
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Cannot find $path from ${File(".").absolutePath}")
        return Json.parseToJsonElement(file.readText().trimStart('\uFEFF')).jsonObject
    }

    private fun JsonObject.string(key: String): String =
        getValue(key).jsonPrimitive.content

    private fun JsonObject.int(key: String): Int =
        getValue(key).jsonPrimitive.int

    private fun JsonObject.long(key: String): Long =
        getValue(key).jsonPrimitive.content.toLong()

    private data class ExpectedAsset(
        val fileName: String,
        val sizeBytes: Long,
        val sha256: String,
    )

    private companion object {
        val allowedDeliveryClasses = setOf("hf_proxy", "storage_mirror_required")
        val blockedPiperArtifactIds = setOf(
            "tts-piper-indo_goreng",
            "tts-piper-john",
            "tts-piper-mattheo",
            "tts-piper-mattheo1",
        )
    }
}
