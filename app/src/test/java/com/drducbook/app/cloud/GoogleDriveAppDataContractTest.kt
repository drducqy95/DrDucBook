package com.drducbook.app.cloud

import io.legado.app.data.repository.GoogleDriveAppDataBackupRepository
import io.legado.app.data.repository.SupabaseCloudSyncRepository
import io.legado.app.domain.model.GoogleDriveCredentialSnapshot
import io.legado.app.domain.usecase.CloudSyncUseCase
import io.legado.app.domain.usecase.GoogleDriveBackupUseCase
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveAppDataContractTest {

    @Test
    fun driveBackupRequestsOnlyAppDataScope() {
        assertEquals(
            setOf(CloudConsentScopes.googleDriveAppData),
            GoogleDriveAppDataContract.requiredScopes,
        )
        assertTrue(
            GoogleDriveAppDataContract.validateConsentScopes(
                setOf(CloudConsentScopes.googleDriveAppData)
            ).isSuccess
        )
        assertTrue(
            GoogleDriveAppDataContract.validateConsentScopes(
                setOf("https://www.googleapis.com/auth/drive")
            ).isFailure
        )
        assertFalse(CloudConsentScopes.googleDriveAppData in CloudConsentScopes.googleSignIn)
    }

    @Test
    fun snapshotUploadUsesAppDataFolderAndRedactsAuthorizationInDebugString() {
        val snapshot = GoogleDriveAppDataContract.snapshotObject(snapshotDescriptor())
        val request = GoogleDriveAppDataContract.buildStartResumableUploadRequest(
            accessToken = "ya29.access-token-secret",
            snapshot = snapshot,
            supabaseUserHash = USER_HASH,
        )

        assertEquals("POST", request.method)
        assertTrue(request.url.contains("upload/drive/v3/files?uploadType=resumable"))
        assertTrue(request.body.orEmpty().contains("\"parents\": [\"appDataFolder\"]"))
        assertTrue(request.body.orEmpty().contains("\"drducbookNamespace\": \"drducbook\""))
        assertTrue(request.body.orEmpty().contains("\"supabaseUserHash\": \"$USER_HASH\""))
        assertFalse(request.toString().contains("ya29.access-token-secret"))
        assertFalse(GoogleDriveAppDataContract.containsClientSecret(request))
    }

    @Test
    fun listRequestIsScopedToAppDataFolderNamespace() {
        val request = GoogleDriveAppDataContract.buildListRequest(
            "ya29.access-token-secret",
            USER_HASH,
        )

        assertEquals("GET", request.method)
        assertTrue(request.url.contains("spaces=appDataFolder"))
        assertTrue(request.url.contains("drducbookNamespace"))
        assertTrue(request.url.contains(USER_HASH))
        assertFalse(request.toString().contains("ya29.access-token-secret"))
    }

    @Test
    fun downloadRequestUsesMediaEndpointAndRedactsToken() {
        val request = GoogleDriveAppDataContract.buildDownloadRequest(
            accessToken = "ya29.access-token-secret",
            fileId = "drive-file_123",
        )

        assertEquals("GET", request.method)
        assertTrue(request.url.endsWith("/drive/v3/files/drive-file_123?alt=media"))
        assertFalse(request.toString().contains("ya29.access-token-secret"))
    }

    @Test
    fun accountMismatchRequiresExplicitConfirmation() {
        val useCase = GoogleDriveBackupUseCase(
            GoogleDriveAppDataBackupRepository(client = OkHttpClient())
        )

        assertFalse(
            useCase.accountLink(USER_HASH, USER_HASH)
                .requiresAccountMismatchConfirmation
        )
        assertTrue(
            useCase.accountLink(USER_HASH, OTHER_USER_HASH)
                .requiresAccountMismatchConfirmation
        )
    }

    @Test
    fun driveCredentialSnapshotRedactsTokens() {
        val snapshot = GoogleDriveCredentialSnapshot(
            accessToken = "access-token-secret",
            refreshToken = "refresh-token-secret",
            accountHash = USER_HASH,
            expiresAtEpochMillis = 1L,
        )

        assertFalse(snapshot.toString().contains("access-token-secret"))
        assertFalse(snapshot.toString().contains("refresh-token-secret"))
        assertTrue(snapshot.toString().contains("<redacted>"))
    }

    private fun snapshotDescriptor() = CloudSyncUseCase(
        SupabaseCloudSyncRepository { configuredConfig() }
    ).createSnapshotDescriptor(
        userId = USER_ID,
        snapshotId = SNAPSHOT_ID,
        revision = "rev-drive-1",
        schemaVersion = 1,
        contentSha256 = "a".repeat(64),
        contentSizeBytes = 2048L,
    )

    private fun configuredConfig() = SupabasePublicConfig(
        url = "https://project-ref.supabase.co",
        publishableKey = "sb_publishable_phase10_test",
        googleAuthClientId = "google-auth-client",
        googleDriveClientId = "google-drive-client",
    )

    private companion object {
        const val USER_ID = "11111111-1111-1111-1111-111111111111"
        const val SNAPSHOT_ID = "33333333-3333-3333-3333-333333333333"
        val USER_HASH = "b".repeat(64)
        val OTHER_USER_HASH = "c".repeat(64)
    }
}
