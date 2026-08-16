package com.drducbook.app.cloud

import io.legado.app.data.repository.SupabaseCloudSyncRepository
import io.legado.app.domain.model.CloudSyncTarget
import io.legado.app.domain.usecase.CloudSyncUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncClientContractTest {

    @Test
    fun snapshotPathIsNamespacedByUserAndRevision() {
        val path = CloudSyncClientContract.snapshotObjectPath(
            userId = USER_ID.uppercase(),
            snapshotId = SNAPSHOT_ID.uppercase(),
            revision = "rev_20260731",
        )

        assertEquals(
            "$USER_ID/snapshots/rev_20260731/$SNAPSHOT_ID.drducsnapshot",
            path,
        )
        assertTrue(CloudSyncClientContract.ownsObjectPath(USER_ID, path))
        assertFalse(CloudSyncClientContract.ownsObjectPath(OTHER_USER_ID, path))
    }

    @Test(expected = IllegalArgumentException::class)
    fun snapshotPathRejectsTraversalRevision() {
        CloudSyncClientContract.snapshotObjectPath(USER_ID, SNAPSHOT_ID, "../bad")
    }

    @Test(expected = IllegalArgumentException::class)
    fun userAssetPathRejectsTraversal() {
        CloudSyncClientContract.userAssetObjectPath(USER_ID, "../theme.png")
    }

    @Test
    fun snapshotDescriptorRequiresHashSizeAndOwnershipPath() {
        val useCase = CloudSyncUseCase(SupabaseCloudSyncRepository { configuredConfig() })
        val descriptor = useCase.createSnapshotDescriptor(
            userId = USER_ID,
            snapshotId = SNAPSHOT_ID,
            revision = "rev-1",
            schemaVersion = 1,
            contentSha256 = "a".repeat(64),
            contentSizeBytes = 1024L,
            target = CloudSyncTarget.SUPABASE,
        )

        assertEquals(CloudSyncClientContract.SNAPSHOT_BUCKET, descriptor.storageBucket)
        assertTrue(CloudSyncClientContract.validateSnapshotDescriptor(descriptor).isSuccess)
        assertEquals(CloudSyncTarget.SUPABASE, CloudSyncTarget.fromStorageValue("supabase"))
    }

    @Test
    fun snapshotDescriptorRejectsWrongBucketAndInvalidHash() {
        val useCase = CloudSyncUseCase(SupabaseCloudSyncRepository { configuredConfig() })
        val descriptor = useCase.createSnapshotDescriptor(
            userId = USER_ID,
            snapshotId = SNAPSHOT_ID,
            revision = "rev-2",
            schemaVersion = 1,
            contentSha256 = "b".repeat(64),
            contentSizeBytes = 1L,
        )

        assertTrue(
            CloudSyncClientContract.validateSnapshotDescriptor(
                descriptor.copy(storageBucket = "public")
            ).isFailure
        )
        assertTrue(
            CloudSyncClientContract.validateSnapshotDescriptor(
                descriptor.copy(contentSha256 = "not-a-hash")
            ).isFailure
        )
    }

    private fun configuredConfig() = SupabasePublicConfig(
        url = "https://project-ref.supabase.co",
        publishableKey = "sb_publishable_phase10_test",
        googleAuthClientId = "",
        googleDriveClientId = "",
    )

    private companion object {
        const val USER_ID = "11111111-1111-1111-1111-111111111111"
        const val OTHER_USER_ID = "22222222-2222-2222-2222-222222222222"
        const val SNAPSHOT_ID = "33333333-3333-3333-3333-333333333333"
    }
}
