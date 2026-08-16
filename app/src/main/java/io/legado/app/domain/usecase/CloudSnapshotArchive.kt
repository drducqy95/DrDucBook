package io.legado.app.domain.usecase

import com.google.gson.reflect.TypeToken
import io.legado.app.domain.model.CloudSnapshotDataset
import io.legado.app.domain.model.CloudSnapshotEntry
import io.legado.app.domain.model.CloudSnapshotManifest
import io.legado.app.utils.GSON
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class CloudSnapshotPayload(
    val dataset: CloudSnapshotDataset,
    val bytes: ByteArray,
    val recordCount: Int,
) {
    init {
        require(recordCount >= 0) { "Snapshot payload record count must not be negative" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CloudSnapshotPayload) return false
        return dataset == other.dataset &&
            bytes.contentEquals(other.bytes) &&
            recordCount == other.recordCount
    }

    override fun hashCode(): Int {
        var result = dataset.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + recordCount
        return result
    }
}

data class BuiltCloudSnapshotArchive(
    val bytes: ByteArray,
    val manifest: CloudSnapshotManifest,
    val sha256: String,
    val sizeBytes: Long,
)

data class ReadCloudSnapshotArchive(
    val manifest: CloudSnapshotManifest,
    val payloads: List<CloudSnapshotPayload>,
)

object CloudSnapshotArchive {

    const val MANIFEST_PATH = "manifest.json"
    const val ENTRIES_DIR = "entries"

    fun build(
        snapshotId: String,
        revision: String,
        deviceId: String,
        createdAtEpochMillis: Long,
        payloads: List<CloudSnapshotPayload>,
    ): BuiltCloudSnapshotArchive {
        require(payloads.isNotEmpty()) { "Snapshot archive must contain at least one payload" }
        require(payloads.map { it.dataset }.distinct().size == payloads.size) {
            "Snapshot archive must contain at most one payload per dataset"
        }
        val entries = payloads.map { payload ->
            CloudSnapshotEntry(
                dataset = payload.dataset,
                objectPath = objectPath(payload.dataset),
                sha256 = payload.bytes.sha256Hex(),
                sizeBytes = payload.bytes.size.toLong(),
                recordCount = payload.recordCount,
            )
        }
        val manifest = CloudSnapshotPolicy.createManifest(
            snapshotId = snapshotId,
            revision = revision,
            deviceId = deviceId,
            createdAtEpochMillis = createdAtEpochMillis,
            entries = entries,
        )
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.writeEntry(MANIFEST_PATH, GSON.toJson(manifest).toByteArray(Charsets.UTF_8))
                payloads
                    .sortedBy { it.dataset.storageKey }
                    .forEach { payload ->
                        zip.writeEntry(objectPath(payload.dataset), payload.bytes)
                    }
            }
            output.toByteArray()
        }
        return BuiltCloudSnapshotArchive(
            bytes = bytes,
            manifest = manifest,
            sha256 = bytes.sha256Hex(),
            sizeBytes = bytes.size.toLong(),
        )
    }

    fun read(bytes: ByteArray): ReadCloudSnapshotArchive {
        require(bytes.isNotEmpty()) { "Snapshot archive is empty" }
        val files = unzip(bytes)
        val manifestBytes = files[MANIFEST_PATH]
            ?: throw IllegalArgumentException("Snapshot archive manifest is missing")
        val manifest = GSON.fromJson<CloudSnapshotManifest>(
            manifestBytes.toString(Charsets.UTF_8),
            manifestType,
        )
        val restorePlan = CloudSnapshotPolicy.restorePlan(manifest)
        val expectedPaths = restorePlan.manifest.entries.mapTo(linkedSetOf()) { it.objectPath }
        val allowedPaths = expectedPaths + MANIFEST_PATH
        require(files.keys.all { it in allowedPaths }) {
            "Snapshot archive contains undeclared files"
        }
        val payloads = restorePlan.manifest.entries.map { entry ->
            val payloadBytes = files[entry.objectPath]
                ?: throw IllegalArgumentException("Snapshot archive entry is missing: ${entry.objectPath}")
            require(payloadBytes.size.toLong() == entry.sizeBytes) {
                "Snapshot archive entry size mismatch: ${entry.objectPath}"
            }
            require(payloadBytes.sha256Hex() == entry.sha256) {
                "Snapshot archive entry checksum mismatch: ${entry.objectPath}"
            }
            CloudSnapshotPayload(
                dataset = entry.dataset,
                bytes = payloadBytes,
                recordCount = entry.recordCount,
            )
        }
        return ReadCloudSnapshotArchive(
            manifest = restorePlan.manifest,
            payloads = payloads,
        )
    }

    fun objectPath(dataset: CloudSnapshotDataset): String =
        "$ENTRIES_DIR/${dataset.storageKey}.json"

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                validateArchivePath(name)
                if (!entry.isDirectory) {
                    require(files.put(name, zip.readBytes()) == null) {
                        "Snapshot archive contains duplicate file: $name"
                    }
                }
                zip.closeEntry()
            }
        }
        return files
    }

    private fun validateArchivePath(path: String) {
        require(path.isNotBlank()) { "Snapshot archive path is blank" }
        require(!path.startsWith("/") && !path.startsWith("\\")) {
            "Snapshot archive path must be relative"
        }
        val parts = path.replace('\\', '/').split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) {
            "Snapshot archive path is invalid"
        }
    }

    private fun ZipOutputStream.writeEntry(path: String, bytes: ByteArray) {
        validateArchivePath(path)
        putNextEntry(ZipEntry(path).apply { time = 0L })
        write(bytes)
        closeEntry()
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private val manifestType = object : TypeToken<CloudSnapshotManifest>() {}.type
}
