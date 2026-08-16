package io.legado.app.data.repository

import com.drducbook.app.cloud.CloudSyncClientContract
import com.drducbook.app.cloud.SupabaseClientProvider
import com.drducbook.app.cloud.SupabasePublicConfig
import io.legado.app.domain.gateway.CloudSyncGateway
import io.legado.app.domain.model.CloudSnapshotDescriptor

class SupabaseCloudSyncRepository(
    private val configProvider: () -> SupabasePublicConfig = { SupabaseClientProvider.config },
) : CloudSyncGateway {

    override val configured: Boolean
        get() = configProvider().isConfigured

    override val snapshotBucket: String = CloudSyncClientContract.SNAPSHOT_BUCKET

    override val userAssetBucket: String = CloudSyncClientContract.USER_ASSET_BUCKET

    override fun snapshotStoragePath(
        userId: String,
        snapshotId: String,
        revision: String,
    ): String = CloudSyncClientContract.snapshotObjectPath(
        userId = userId,
        snapshotId = snapshotId,
        revision = revision,
    )

    override fun validateSnapshotDescriptor(descriptor: CloudSnapshotDescriptor): Result<Unit> =
        CloudSyncClientContract.validateSnapshotDescriptor(descriptor)
}
