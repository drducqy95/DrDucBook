package com.drducbook.app.cloud

import io.legado.app.domain.model.AssetDeliveryCatalog
import io.legado.app.domain.model.ExternalAssetCatalog
import io.legado.app.domain.model.LocalAiModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetDeliveryClientContractTest {

    @Test
    fun parsesInternalDownloadAndCatalogUris() {
        assertEquals(
            AssetDeliveryReference(
                kind = AssetDeliveryKind.DOWNLOAD,
                id = ExternalAssetCatalog.quickTranslationCleanAssetId,
            ),
            AssetDeliveryClientContract.parseInternalUri(ExternalAssetCatalog.quickTranslationCleanZipUrl),
        )
        assertEquals(
            AssetDeliveryReference(
                kind = AssetDeliveryKind.CATALOG,
                id = ExternalAssetCatalog.ttsPiperVoiceCatalogId,
            ),
            AssetDeliveryClientContract.parseInternalUri(ExternalAssetCatalog.ttsPiperVoiceFolderUrl),
        )
        assertNull(AssetDeliveryClientContract.parseInternalUri("https://example.com/file.zip"))
    }

    @Test
    fun everyPinnedCatalogUrlIsAnInternalAssetUri() {
        val urls = buildList {
            addAll(ExternalAssetCatalog.externalPackageAssets.map { it.downloadUrl })
            addAll(ExternalAssetCatalog.releaseEligibleTtsVoiceCatalog.map { it.downloadUrl })
            addAll(LocalAiModelCatalog.all.map { it.downloadUrl })
        }

        assertTrue(urls.isNotEmpty())
        urls.forEach { url ->
            assertTrue(url.startsWith("${AssetDeliveryCatalog.assetUriScheme}://"))
            assertNotNull(AssetDeliveryClientContract.parseInternalUri(url))
        }
    }

    @Test
    fun ticketRequestUsesSupabaseJwtButNoServerSecrets() {
        val request = AssetDeliveryClientContract.buildTicketRequest(
            config = configured(),
            artifactId = ExternalAssetCatalog.quickTranslationCleanAssetId,
            supabaseAccessToken = "jwt-access-token",
        )

        assertEquals("POST", request.method)
        assertEquals("https://project-ref.supabase.co/functions/v1/asset-ticket", request.url)
        assertEquals("Bearer jwt-access-token", request.headers["authorization"])
        assertEquals("""{"artifactId":"translation-quick-clean"}""", request.body)
        assertFalse(AssetDeliveryClientContract.containsClientSecret(request))
    }

    @Test
    fun downloadRequestUsesOpaqueTicketAndOptionalRange() {
        val request = AssetDeliveryClientContract.buildDownloadRequest(
            config = configured(),
            artifactId = ExternalAssetCatalog.hachimiOnnxAssetId,
            ticket = "opaque-ticket",
            rangeHeader = "bytes=0-1023",
        )

        assertEquals("GET", request.method)
        assertEquals(
            "https://project-ref.supabase.co/functions/v1/asset-download?artifactId=translation-hachimi-onnx-arm64",
            request.url,
        )
        assertEquals("opaque-ticket", request.headers["x-drducbook-asset-ticket"])
        assertEquals("bytes=0-1023", request.headers["range"])
        assertFalse("authorization" in request.headers)
        assertFalse(AssetDeliveryClientContract.containsClientSecret(request))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankSupabaseConfigCannotBuildTicketRequest() {
        AssetDeliveryClientContract.buildTicketRequest(
            config = SupabasePublicConfig("", "", "", ""),
            artifactId = ExternalAssetCatalog.quickTranslationCleanAssetId,
            supabaseAccessToken = "jwt-access-token",
        )
    }

    private fun configured() = SupabasePublicConfig(
        url = "https://project-ref.supabase.co",
        publishableKey = "sb_publishable_test",
        googleAuthClientId = "",
        googleDriveClientId = "",
    )
}
