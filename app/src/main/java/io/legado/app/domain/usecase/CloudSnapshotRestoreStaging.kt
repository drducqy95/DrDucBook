package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotDataset
import io.legado.app.domain.model.CloudSnapshotManifest
import java.io.File
import java.security.MessageDigest

data class StagedCloudSnapshotEntry(
    val dataset: CloudSnapshotDataset,
    val file: File,
    val sha256: String,
    val sizeBytes: Long,
    val recordCount: Int,
)

data class StagedCloudSnapshot(
    val manifest: CloudSnapshotManifest,
    val directory: File,
    val entries: List<StagedCloudSnapshotEntry>,
)

object CloudSnapshotRestoreStaging {

    fun stageArchive(
        archiveBytes: ByteArray,
        stagingRoot: File,
    ): StagedCloudSnapshot {
        val archive = CloudSnapshotArchive.read(archiveBytes)
        val root = stagingRoot.canonicalFile
        require(!root.exists() || root.isDirectory) {
            "Snapshot staging root is not a directory"
        }
        if (!root.exists() && !root.mkdirs()) {
            throw IllegalStateException("Cannot create snapshot staging root")
        }

        val directory = File(
            root,
            safeName("${archive.manifest.snapshotId}-${archive.manifest.revision}"),
        ).canonicalFile
        require(directory.toPath().startsWith(root.toPath())) {
            "Snapshot staging directory escaped the staging root"
        }
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IllegalStateException("Cannot reset snapshot staging directory")
        }
        if (!directory.mkdirs()) {
            throw IllegalStateException("Cannot create snapshot staging directory")
        }

        val stagedEntries = archive.payloads.map { payload ->
            val relativePath = CloudSnapshotArchive.objectPath(payload.dataset)
            val file = File(directory, relativePath).canonicalFile
            require(file.toPath().startsWith(directory.toPath())) {
                "Snapshot staged file escaped the staging directory"
            }
            file.parentFile?.mkdirs()
            file.writeBytes(payload.bytes)
            val sha256 = file.readBytes().sha256Hex()
            require(sha256 == payload.bytes.sha256Hex()) {
                "Snapshot staged file checksum mismatch"
            }
            StagedCloudSnapshotEntry(
                dataset = payload.dataset,
                file = file,
                sha256 = sha256,
                sizeBytes = file.length(),
                recordCount = payload.recordCount,
            )
        }

        return StagedCloudSnapshot(
            manifest = archive.manifest,
            directory = directory,
            entries = stagedEntries,
        )
    }

    private fun safeName(value: String): String =
        value.replace(unsafeNameChars, "_").take(180).ifBlank { "snapshot" }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private val unsafeNameChars = Regex("[\\u0000-\\u001f\\\\/:*?\"<>|]")
}
