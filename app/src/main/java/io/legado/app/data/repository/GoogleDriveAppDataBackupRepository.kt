package io.legado.app.data.repository

import android.content.Context
import android.net.Uri
import com.drducbook.app.cloud.CloudSyncClientContract
import com.drducbook.app.cloud.GoogleDriveApiRequest
import com.drducbook.app.cloud.GoogleDriveAppDataContract
import io.legado.app.domain.gateway.BackupRestoreGateway
import io.legado.app.domain.gateway.GoogleDriveBackupGateway
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.CloudBackupReceipt
import io.legado.app.domain.model.CloudSnapshotDescriptor
import io.legado.app.domain.model.CloudSyncTarget
import io.legado.app.domain.model.GoogleDriveAccountLink
import io.legado.app.domain.model.GoogleDriveSnapshotObject
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.BackupContentProfile
import io.legado.app.help.storage.CloudBackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.coroutineContext

class GoogleDriveAppDataBackupRepository(
    context: Context? = null,
    private val backupRestoreGateway: BackupRestoreGateway? = null,
    private val client: OkHttpClient = okHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : GoogleDriveBackupGateway {

    private val appContext = context?.applicationContext

    override val configured: Boolean = true

    override val requiredScopes: Set<String> = GoogleDriveAppDataContract.requiredScopes

    override fun validateConsentScopes(scopes: Set<String>): Result<Unit> =
        GoogleDriveAppDataContract.validateConsentScopes(scopes)

    override fun snapshotObject(descriptor: CloudSnapshotDescriptor): GoogleDriveSnapshotObject =
        GoogleDriveAppDataContract.snapshotObject(descriptor)

    override fun accountLink(
        supabaseUserHash: String,
        driveAccountHash: String,
    ): GoogleDriveAccountLink = GoogleDriveAccountLink(
        supabaseUserHash = supabaseUserHash,
        driveAccountHash = driveAccountHash,
    )

    override suspend fun uploadLatest(
        accessToken: String,
        session: AccountSession,
        password: String,
    ): CloudBackupReceipt = withContext(Dispatchers.IO) {
        val context = requireNotNull(appContext) { "Google Drive backup is not initialized" }
        val userId = CloudSyncClientContract.normalizeUuid(session.userId, "userId")
        val snapshotId = UUID.randomUUID().toString()
        val revision = revisionNow()
        val tempRoot = createTempRoot(context)
        val archive = File(tempRoot, "$snapshotId${CloudSyncClientContract.SNAPSHOT_EXTENSION}")
        val plainArchive = File(tempRoot, "$snapshotId.plain.zip")
        try {
            Backup.createArchiveLocked(
                context,
                plainArchive,
                profile = BackupContentProfile.FULL,
            )
            CloudBackupCrypto.encrypt(plainArchive, archive, password)
            require(archive.isFile && archive.length() > 0L) {
                "Không thể tạo tệp sao lưu Google Drive"
            }
            val descriptor = descriptor(
                userId = userId,
                snapshotId = snapshotId,
                revision = revision,
                sizeBytes = archive.length(),
                sha256 = sha256(archive),
            )
            val snapshot = GoogleDriveAppDataContract.snapshotObject(descriptor)
            val startRequest = GoogleDriveAppDataContract.buildStartResumableUploadRequest(
                accessToken = accessToken,
                snapshot = snapshot,
                supabaseUserHash = accountHash(userId),
            )
            val uploadUrl = client.newCall(startRequest.toOkHttpRequest()).execute().use { response ->
                response.requireGoogleSuccess()
                response.header("Location")
                    ?.takeIf(String::isNotBlank)
                    ?: throw IOException("Google Drive không trả về URL tải lên")
            }
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", GoogleDriveAppDataContract.SNAPSHOT_MIME_TYPE)
                .put(archive.asRequestBody(OCTET_STREAM))
                .build()
            client.newCall(uploadRequest).execute().use { response ->
                response.requireGoogleSuccess()
            }
            descriptor.toReceipt()
        } finally {
            deleteTempRoot(context, tempRoot)
        }
    }

    override suspend fun restoreLatest(
        accessToken: String,
        session: AccountSession,
        password: String,
    ): CloudBackupReceipt? = withContext(Dispatchers.IO) {
        val context = requireNotNull(appContext) { "Google Drive backup is not initialized" }
        val restoreGateway = requireNotNull(backupRestoreGateway) {
            "Google Drive restore is not initialized"
        }
        val userId = CloudSyncClientContract.normalizeUuid(session.userId, "userId")
        val latest = latestSnapshot(accessToken, accountHash(userId), userId) ?: return@withContext null
        val request = GoogleDriveAppDataContract.buildDownloadRequest(accessToken, latest.fileId)
        val tempRoot = createTempRoot(context)
        val archive = File(tempRoot, "restore${CloudSyncClientContract.SNAPSHOT_EXTENSION}")
        val plainArchive = File(tempRoot, "restore.plain.zip")
        try {
            client.newCall(request.toOkHttpRequest()).execute().use { response ->
                if (response.code == 404) throw IOException("Bản sao Google Drive không còn tồn tại")
                response.requireGoogleSuccess()
                archive.outputStream().buffered().use { output ->
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (archive.length() != latest.descriptor.contentSizeBytes) {
                throw IOException("Kích thước bản sao Google Drive không khớp")
            }
            if (!sha256(archive).equals(latest.descriptor.contentSha256, ignoreCase = true)) {
                throw IOException("SHA-256 của bản sao Google Drive không khớp")
            }
            if (CloudBackupCrypto.isEncrypted(archive)) {
                CloudBackupCrypto.decrypt(archive, plainArchive, password)
            } else {
                archive.copyTo(plainArchive, overwrite = true)
            }
            restoreGateway.restoreLocal(Uri.fromFile(plainArchive).toString())
            latest.descriptor.toReceipt()
        } finally {
            deleteTempRoot(context, tempRoot)
        }
    }

    private fun latestSnapshot(
        accessToken: String,
        userHash: String,
        userId: String,
    ): DriveSnapshotFile? {
        val request = GoogleDriveAppDataContract.buildListRequest(accessToken, userHash)
        val responseText = client.newCall(request.toOkHttpRequest()).execute().use { response ->
            response.requireGoogleSuccess()
            response.body.string()
        }
        val files = (json.parseToJsonElement(responseText).jsonObject["files"] as? JsonArray)
            ?: return null
        return files.asSequence().mapNotNull { element ->
            runCatching {
                val row = element.jsonObject
                val properties = row["appProperties"]?.jsonObject ?: return@runCatching null
                val snapshotId = requireNotNull(properties.string("snapshotId"))
                val revision = requireNotNull(properties.string("revision"))
                val sha256 = requireNotNull(properties.string("sha256"))
                val sizeBytes = row["size"]?.jsonPrimitive?.longOrNull
                    ?: throw IOException("Metadata Google Drive thiếu kích thước")
                DriveSnapshotFile(
                    fileId = requireNotNull(row.string("id")),
                    descriptor = descriptor(userId, snapshotId, revision, sizeBytes, sha256),
                )
            }.getOrNull()
        }.firstOrNull()
    }

    private fun descriptor(
        userId: String,
        snapshotId: String,
        revision: String,
        sizeBytes: Long,
        sha256: String,
    ): CloudSnapshotDescriptor = CloudSnapshotDescriptor(
        userId = userId,
        snapshotId = snapshotId,
        revision = revision,
        schemaVersion = BACKUP_SCHEMA_VERSION,
        contentSha256 = sha256,
        contentSizeBytes = sizeBytes,
        target = CloudSyncTarget.GOOGLE_DRIVE,
        storageBucket = CloudSyncClientContract.SNAPSHOT_BUCKET,
        storagePath = CloudSyncClientContract.snapshotObjectPath(userId, snapshotId, revision),
    ).also { CloudSyncClientContract.validateSnapshotDescriptor(it).getOrThrow() }

    private fun GoogleDriveApiRequest.toOkHttpRequest(): Request {
        val builder = Request.Builder().url(url)
        headers.forEach(builder::header)
        return when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post(body.orEmpty().toRequestBody(JSON_MEDIA_TYPE)).build()
            else -> error("Unsupported Google Drive request method: $method")
        }
    }

    private fun okhttp3.Response.requireGoogleSuccess() {
        if (isSuccessful) return
        val detail = body.string().take(500).replace(Regex("[\\r\\n]+"), " ").trim()
        throw IOException(
            if (code == 401) "Phiên Google Drive đã hết hạn; hãy cấp quyền lại"
            else if (code == 403) "Google Drive từ chối quyền drive.appdata. Hãy bật Drive API và thêm tài khoản vào Test users"
            else if (code == 400 && detail.contains("accessNotConfigured", ignoreCase = true))
                "Google Drive API chưa được bật cho dự án OAuth của DrDucBook"
            else if (detail.isBlank()) "Google Drive trả về lỗi HTTP $code"
            else "Google Drive trả về lỗi HTTP $code: $detail"
        )
    }

    private fun createTempRoot(context: Context): File {
        val parent = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
        return File(parent, UUID.randomUUID().toString()).also { root ->
            check(root.mkdirs()) { "Không thể tạo thư mục Google Drive tạm" }
        }
    }

    private fun deleteTempRoot(context: Context, root: File) {
        val parent = File(context.cacheDir, TEMP_DIR).canonicalFile
        val candidate = root.canonicalFile
        if (candidate.parentFile == parent) candidate.deleteRecursively()
    }

    private fun accountHash(userId: String): String = sha256(userId.toByteArray(Charsets.UTF_8))

    private fun sha256(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

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

    private data class DriveSnapshotFile(
        val fileId: String,
        val descriptor: CloudSnapshotDescriptor,
    )

    private companion object {
        const val BACKUP_SCHEMA_VERSION = 2
        const val TEMP_DIR = "google_drive_backup"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM = GoogleDriveAppDataContract.SNAPSHOT_MIME_TYPE.toMediaType()
        val REVISION_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC)
    }
}
