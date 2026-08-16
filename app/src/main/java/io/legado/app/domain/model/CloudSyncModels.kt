package io.legado.app.domain.model

enum class CloudSyncTarget(val storageValue: String) {
    SUPABASE("supabase"),
    GOOGLE_DRIVE("google_drive"),
    BOTH("both");

    companion object {
        fun fromStorageValue(value: String): CloudSyncTarget? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class CloudDeviceDescriptor(
    val installId: String,
    val appId: String,
    val appVersion: String?,
    val platform: String = "android",
    val deviceName: String? = null,
)

data class CloudSnapshotDescriptor(
    val userId: String,
    val snapshotId: String,
    val revision: String,
    val schemaVersion: Int,
    val contentSha256: String,
    val contentSizeBytes: Long,
    val target: CloudSyncTarget,
    val storageBucket: String,
    val storagePath: String,
)

data class CloudSyncHeadDescriptor(
    val userId: String,
    val target: CloudSyncTarget,
    val namespace: String,
    val headRevision: String?,
    val snapshotId: String?,
)
