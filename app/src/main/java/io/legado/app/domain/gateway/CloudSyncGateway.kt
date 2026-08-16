package io.legado.app.domain.gateway

import io.legado.app.domain.model.CloudSnapshotDescriptor

interface CloudSyncGateway {
    val configured: Boolean
    val snapshotBucket: String
    val userAssetBucket: String

    fun snapshotStoragePath(
        userId: String,
        snapshotId: String,
        revision: String,
    ): String

    fun validateSnapshotDescriptor(descriptor: CloudSnapshotDescriptor): Result<Unit>
}
