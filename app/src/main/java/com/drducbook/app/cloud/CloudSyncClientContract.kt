package com.drducbook.app.cloud

import io.legado.app.domain.model.CloudSnapshotDescriptor
import java.util.Locale
import java.util.UUID

object CloudSyncClientContract {
    const val NAMESPACE = "drducbook"
    const val SNAPSHOT_BUCKET = "drducbook-snapshots"
    const val USER_ASSET_BUCKET = "drducbook-user-assets"
    const val SNAPSHOT_EXTENSION = ".drducsnapshot"

    val exposedTables: Set<String> = setOf(
        "profiles",
        "cloud_devices",
        "sync_snapshots",
        "sync_heads",
        "sync_events",
    )

    fun snapshotObjectPath(
        userId: String,
        snapshotId: String,
        revision: String,
    ): String {
        val normalizedUserId = normalizeUuid(userId, "userId")
        val normalizedSnapshotId = normalizeUuid(snapshotId, "snapshotId")
        val normalizedRevision = normalizeRevision(revision)
        return "$normalizedUserId/snapshots/$normalizedRevision/$normalizedSnapshotId$SNAPSHOT_EXTENSION"
    }

    fun userAssetObjectPath(userId: String, relativePath: String): String {
        val normalizedUserId = normalizeUuid(userId, "userId")
        val normalizedPath = normalizeRelativePath(relativePath)
        return "$normalizedUserId/assets/$normalizedPath"
    }

    fun ownsObjectPath(userId: String, objectPath: String): Boolean {
        val normalizedUserId = normalizeUuid(userId, "userId")
        return objectPath.startsWith("$normalizedUserId/")
    }

    fun validateSnapshotDescriptor(descriptor: CloudSnapshotDescriptor): Result<Unit> =
        runCatching {
            require(descriptor.storageBucket == SNAPSHOT_BUCKET) { "Unexpected snapshot bucket" }
            require(descriptor.storagePath == snapshotObjectPath(
                userId = descriptor.userId,
                snapshotId = descriptor.snapshotId,
                revision = descriptor.revision,
            )) {
                "Snapshot storage path does not match the ownership contract"
            }
            require(descriptor.schemaVersion > 0) { "Snapshot schema version must be positive" }
            require(descriptor.contentSizeBytes >= 0) { "Snapshot size must not be negative" }
            require(sha256Regex.matches(descriptor.contentSha256)) { "Snapshot SHA-256 is invalid" }
        }

    fun normalizeRevision(revision: String): String {
        val normalized = revision.trim()
        require(revisionRegex.matches(normalized)) { "Invalid snapshot revision" }
        return normalized
    }

    fun normalizeUuid(value: String, fieldName: String): String =
        runCatching { UUID.fromString(value.trim()).toString() }
            .getOrElse { throw IllegalArgumentException("$fieldName must be a UUID") }

    private fun normalizeRelativePath(relativePath: String): String {
        val normalized = relativePath.trim().replace('\\', '/')
        require(relativePathRegex.matches(normalized)) { "Invalid asset path" }
        require(!normalized.contains("..")) { "Asset path traversal is not allowed" }
        require(!normalized.contains("//")) { "Asset path must not contain empty segments" }
        return normalized.lowercase(Locale.US)
    }

    private val revisionRegex = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private val relativePathRegex = Regex("^[A-Za-z0-9][A-Za-z0-9._/-]{0,191}$")
    private val sha256Regex = Regex("^[a-f0-9]{64}$")
}
