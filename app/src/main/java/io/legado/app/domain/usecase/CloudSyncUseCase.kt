package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.CloudSyncGateway
import io.legado.app.domain.model.CloudSnapshotDescriptor
import io.legado.app.domain.model.CloudSyncTarget

class CloudSyncUseCase(
    private val cloudSyncGateway: CloudSyncGateway,
) {

    val configured: Boolean
        get() = cloudSyncGateway.configured

    fun createSnapshotDescriptor(
        userId: String,
        snapshotId: String,
        revision: String,
        schemaVersion: Int,
        contentSha256: String,
        contentSizeBytes: Long,
        target: CloudSyncTarget = CloudSyncTarget.SUPABASE,
    ): CloudSnapshotDescriptor {
        val descriptor = CloudSnapshotDescriptor(
            userId = userId,
            snapshotId = snapshotId,
            revision = revision,
            schemaVersion = schemaVersion,
            contentSha256 = contentSha256,
            contentSizeBytes = contentSizeBytes,
            target = target,
            storageBucket = cloudSyncGateway.snapshotBucket,
            storagePath = cloudSyncGateway.snapshotStoragePath(userId, snapshotId, revision),
        )
        cloudSyncGateway.validateSnapshotDescriptor(descriptor).getOrThrow()
        return descriptor
    }
}
