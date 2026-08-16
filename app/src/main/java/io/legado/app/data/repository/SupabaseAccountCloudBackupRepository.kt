package io.legado.app.data.repository

import android.content.Context
import android.net.Uri
import com.drducbook.app.cloud.CloudSyncClientContract
import com.drducbook.app.cloud.SupabasePublicConfig
import io.legado.app.domain.gateway.AccountAuthGateway
import io.legado.app.domain.gateway.AccountCloudBackupGateway
import io.legado.app.domain.gateway.BackupRestoreGateway
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.CloudBackupReceipt
import io.legado.app.domain.model.CloudSnapshotDescriptor
import io.legado.app.domain.model.CloudSyncTarget
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.BackupContentProfile
import io.legado.app.help.storage.CloudBackupCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class SupabaseAccountCloudBackupRepository(
    context: Context,
    config: SupabasePublicConfig,
    accountAuthGateway: AccountAuthGateway,
    private val backupRestoreGateway: BackupRestoreGateway,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AccountCloudBackupGateway {

    private val appContext = context.applicationContext
    private val rest = SupabaseAuthenticatedRestClient(config, accountAuthGateway)

    override val configured: Boolean
        get() = rest.configured

    override suspend fun uploadLatest(
        session: AccountSession,
        password: String,
    ): CloudBackupReceipt {
        val userId = CloudSyncClientContract.normalizeUuid(session.userId, "userId")
        val snapshotId = UUID.randomUUID().toString()
        val revision = revisionNow()
        val tempRoot = createTempRoot()
        val archive = File(tempRoot, "$snapshotId${CloudSyncClientContract.SNAPSHOT_EXTENSION}")
        val plainArchive = File(tempRoot, "$snapshotId.plain.zip")
        var uploaded = false
        var metadataInserted = false
        var descriptor: CloudSnapshotDescriptor? = null
        try {
            Backup.createArchiveLocked(
                appContext,
                plainArchive,
                profile = BackupContentProfile.METADATA,
            )
            CloudBackupCrypto.encrypt(plainArchive, archive, password)
            require(archive.isFile && archive.length() > 0L) {
                "Không thể tạo tệp sao lưu"
            }
            val sha256 = sha256(archive)
            val currentDescriptor = CloudSnapshotDescriptor(
                userId = userId,
                snapshotId = snapshotId,
                revision = revision,
                schemaVersion = BACKUP_SCHEMA_VERSION,
                contentSha256 = sha256,
                contentSizeBytes = archive.length(),
                target = CloudSyncTarget.SUPABASE,
                storageBucket = CloudSyncClientContract.SNAPSHOT_BUCKET,
                storagePath = CloudSyncClientContract.snapshotObjectPath(
                    userId = userId,
                    snapshotId = snapshotId,
                    revision = revision,
                ),
            )
            descriptor = currentDescriptor
            CloudSyncClientContract.validateSnapshotDescriptor(currentDescriptor).getOrThrow()

            if (archive.length() >= RESUMABLE_UPLOAD_THRESHOLD_BYTES) {
                rest.uploadFileResumable(
                    bucket = currentDescriptor.storageBucket,
                    objectPath = currentDescriptor.storagePath,
                    source = archive,
                )
            } else {
                rest.putFile(
                    path = "storage/v1/object/${currentDescriptor.storageBucket}/${currentDescriptor.storagePath}",
                    source = archive,
                )
            }
            uploaded = true
            insertSnapshot(currentDescriptor)
            metadataInserted = true
            updateHead(currentDescriptor)
            insertEvent(userId, snapshotId, "upload")
            return currentDescriptor.toReceipt()
        } catch (error: Throwable) {
            // A failed metadata/head request must not leave an unreachable private object behind.
            if (metadataInserted) {
                runCatching {
                    rest.delete(
                        path = "rest/v1/sync_snapshots",
                        query = mapOf("id" to "eq.$snapshotId", "user_id" to "eq.$userId"),
                    )
                }
            }
            if (uploaded) {
                val uploadedDescriptor = descriptor
                runCatching {
                    if (uploadedDescriptor == null) return@runCatching
                    rest.delete(
                        path = "storage/v1/object/${uploadedDescriptor.storageBucket}/${uploadedDescriptor.storagePath}",
                    )
                }
            }
            throw error
        } finally {
            deleteTempRoot(tempRoot)
        }
    }

    override suspend fun restoreLatest(
        session: AccountSession,
        password: String,
    ): CloudBackupReceipt? {
        val userId = CloudSyncClientContract.normalizeUuid(session.userId, "userId")
        val head = latestHead(userId) ?: return null
        val descriptor = snapshotDescriptor(userId, head.snapshotId)
        CloudSyncClientContract.validateSnapshotDescriptor(descriptor).getOrThrow()
        val tempRoot = createTempRoot()
        val archive = File(tempRoot, "restore${CloudSyncClientContract.SNAPSHOT_EXTENSION}")
        val plainArchive = File(tempRoot, "restore.plain.zip")
        try {
            val found = rest.downloadTo(
                path = "storage/v1/object/authenticated/${descriptor.storageBucket}/${descriptor.storagePath}",
                destination = archive,
            )
            if (!found) throw IOException("Không tìm thấy tệp sao lưu trên đám mây")
            if (archive.length() != descriptor.contentSizeBytes) {
                throw IOException("Kích thước tệp sao lưu không khớp")
            }
            if (!sha256(archive).equals(descriptor.contentSha256, ignoreCase = true)) {
                throw IOException("Mã kiểm tra SHA-256 của bản sao lưu không khớp")
            }
            if (CloudBackupCrypto.isEncrypted(archive)) {
                CloudBackupCrypto.decrypt(archive, plainArchive, password)
            } else {
                archive.copyTo(plainArchive, overwrite = true)
            }
            backupRestoreGateway.restoreLocal(Uri.fromFile(plainArchive).toString())
            insertEvent(userId, descriptor.snapshotId, "restore")
            return descriptor.toReceipt()
        } finally {
            deleteTempRoot(tempRoot)
        }
    }

    private suspend fun insertSnapshot(descriptor: CloudSnapshotDescriptor) {
        val body = buildJsonObject {
            put("id", descriptor.snapshotId)
            put("user_id", descriptor.userId)
            put("revision", descriptor.revision)
            put("schema_version", descriptor.schemaVersion)
            put("content_sha256", descriptor.contentSha256)
            put("content_size_bytes", descriptor.contentSizeBytes)
            put("storage_bucket", descriptor.storageBucket)
            put("storage_path", descriptor.storagePath)
            put("encrypted", true)
        }.toString()
        rest.post(
            path = "rest/v1/sync_snapshots",
            body = body,
            prefer = "return=minimal",
        )
    }

    private suspend fun updateHead(descriptor: CloudSnapshotDescriptor) {
        val body = buildJsonObject {
            put("user_id", descriptor.userId)
            put("target", CloudSyncTarget.SUPABASE.storageValue)
            put("namespace", CloudSyncClientContract.NAMESPACE)
            put("head_revision", descriptor.revision)
            put("snapshot_id", descriptor.snapshotId)
        }.toString()
        rest.post(
            path = "rest/v1/sync_heads",
            body = body,
            query = mapOf("on_conflict" to "user_id,target,namespace"),
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    private suspend fun latestHead(userId: String): SyncHead? {
        val response = rest.get(
            path = "rest/v1/sync_heads",
            query = mapOf(
                "user_id" to "eq.$userId",
                "target" to "eq.${CloudSyncTarget.SUPABASE.storageValue}",
                "namespace" to "eq.${CloudSyncClientContract.NAMESPACE}",
                "select" to "snapshot_id,head_revision",
                "limit" to "1",
            ),
        )
        val row = (json.parseToJsonElement(response) as? JsonArray)
            ?.firstOrNull()
            ?.jsonObject
            ?: return null
        return SyncHead(
            snapshotId = requireNotNull(row.string("snapshot_id")) {
                "Cloud head không có snapshot_id"
            },
            revision = row.string("head_revision").orEmpty(),
        )
    }

    private suspend fun snapshotDescriptor(
        userId: String,
        snapshotId: String,
    ): CloudSnapshotDescriptor {
        val response = rest.get(
            path = "rest/v1/sync_snapshots",
            query = mapOf(
                "id" to "eq.$snapshotId",
                "user_id" to "eq.$userId",
                "select" to "id,user_id,revision,schema_version,content_sha256,content_size_bytes,storage_bucket,storage_path",
                "limit" to "1",
            ),
        )
        val row = (json.parseToJsonElement(response) as? JsonArray)
            ?.firstOrNull()
            ?.jsonObject
            ?: throw IOException("Không tìm thấy metadata của bản sao lưu")
        return CloudSnapshotDescriptor(
            userId = requireNotNull(row.string("user_id")),
            snapshotId = requireNotNull(row.string("id")),
            revision = requireNotNull(row.string("revision")),
            schemaVersion = row["schema_version"]?.jsonPrimitive?.longOrNull?.toInt()
                ?: BACKUP_SCHEMA_VERSION,
            contentSha256 = requireNotNull(row.string("content_sha256")),
            contentSizeBytes = row["content_size_bytes"]?.jsonPrimitive?.longOrNull
                ?: throw IOException("Metadata thiếu kích thước bản sao lưu"),
            target = CloudSyncTarget.SUPABASE,
            storageBucket = requireNotNull(row.string("storage_bucket")),
            storagePath = requireNotNull(row.string("storage_path")),
        )
    }

    private suspend fun insertEvent(
        userId: String,
        snapshotId: String,
        eventType: String,
    ) {
        runCatching {
            rest.post(
                path = "rest/v1/sync_events",
                body = buildJsonObject {
                    put("user_id", userId)
                    put("target", CloudSyncTarget.SUPABASE.storageValue)
                    put("namespace", CloudSyncClientContract.NAMESPACE)
                    put("event_type", eventType)
                    put("snapshot_id", snapshotId)
                }.toString(),
                prefer = "return=minimal",
            )
        }
    }

    private fun createTempRoot(): File {
        val parent = File(appContext.cacheDir, TEMP_DIR).apply { mkdirs() }
        return File(parent, UUID.randomUUID().toString()).also { root ->
            check(root.mkdirs()) { "Không thể tạo thư mục đồng bộ tạm" }
        }
    }

    private fun deleteTempRoot(root: File) {
        val parent = File(appContext.cacheDir, TEMP_DIR).canonicalFile
        val candidate = root.canonicalFile
        if (candidate.parentFile == parent) candidate.deleteRecursively()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun CloudSnapshotDescriptor.toReceipt(): CloudBackupReceipt = CloudBackupReceipt(
        snapshotId = snapshotId,
        revision = revision,
        sizeBytes = contentSizeBytes,
        sha256 = contentSha256,
        completedAtEpochMillis = System.currentTimeMillis(),
    )

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun revisionNow(): String = "android_${REVISION_FORMATTER.format(Instant.now())}_${
        UUID.randomUUID().toString().take(8)
    }"

    private data class SyncHead(
        val snapshotId: String,
        val revision: String,
    )

    private companion object {
        const val BACKUP_SCHEMA_VERSION = 2
        const val RESUMABLE_UPLOAD_THRESHOLD_BYTES = 6L * 1024L * 1024L
        const val TEMP_DIR = "account_cloud_sync"
        val REVISION_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC)
    }
}
