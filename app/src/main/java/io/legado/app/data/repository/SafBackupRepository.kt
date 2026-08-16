package io.legado.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.domain.gateway.BackupRestoreGateway
import io.legado.app.domain.gateway.AiSecretStore
import io.legado.app.domain.gateway.SafBackupGateway
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.BackupContentProfile
import io.legado.app.help.storage.CloudBackupCrypto
import io.legado.app.worker.SafBackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class SafBackupRepository(
    context: Context,
    private val backupRestoreGateway: BackupRestoreGateway,
    private val secretStore: AiSecretStore,
) : SafBackupGateway {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override val configured: Boolean
        get() = selectedTreeUri()?.let(::hasPersistedAccess) == true

    override fun selectedTreeUri(): Uri? = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    override val scheduleIntervalHours: Long?
        get() = preferences.getLong(KEY_SCHEDULE_INTERVAL_HOURS, 0L).takeIf { it > 0L }

    override suspend fun setTreeUri(uri: Uri) {
        require(hasPersistedAccess(uri)) {
            "SAF folder permission is unavailable; choose the folder again"
        }
        preferences.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    override suspend fun clearTreeUri() {
        clearSchedule()
        preferences.edit().remove(KEY_TREE_URI).apply()
    }

    override suspend fun setSchedule(intervalHours: Long, password: String) {
        require(intervalHours == DAILY_HOURS || intervalHours == WEEKLY_HOURS) {
            "SAF schedule must be daily or weekly"
        }
        require(password.length >= 8) { "Backup password must contain at least 8 characters" }
        check(configured) { "Chưa chọn thư mục sao lưu SAF" }
        secretStore.put(password, SCHEDULE_PASSWORD_REF)
        preferences.edit().putLong(KEY_SCHEDULE_INTERVAL_HOURS, intervalHours).apply()
        SafBackupWorker.schedule(appContext, intervalHours)
    }

    override suspend fun clearSchedule() {
        secretStore.delete(SCHEDULE_PASSWORD_REF)
        preferences.edit().remove(KEY_SCHEDULE_INTERVAL_HOURS).apply()
        SafBackupWorker.cancel(appContext)
    }

    override suspend fun uploadLatest(password: String): Long = withContext(Dispatchers.IO) {
        val tree = requireTree()
        val backupDir = tree.findFile(BACKUP_DIRECTORY)
            ?: tree.createDirectory(BACKUP_DIRECTORY)
            ?: error("Cannot create SAF backup directory")
        val root = File(appContext.cacheDir, "saf_backup_${UUID.randomUUID()}").apply { mkdirs() }
        val plain = File(root, "snapshot.zip")
        val encrypted = File(root, "snapshot.drducsnapshot")
        try {
            Backup.createArchiveLocked(
                appContext,
                plain,
                profile = BackupContentProfile.FULL,
            )
            CloudBackupCrypto.encrypt(plain, encrypted, password)
            require(encrypted.isFile && encrypted.length() > 0L) { "Encrypted backup is empty" }
            val finalName = "snapshot-${System.currentTimeMillis()}.drducsnapshot"
            backupDir.listFiles()
                .filter { it.isFile && it.name?.endsWith(".part") == true }
                .forEach { it.delete() }
            val partial = backupDir.createFile(SNAPSHOT_MIME, "$finalName.part")
                ?: error("Cannot create SAF backup file")
            try {
                copyToDocument(partial, encrypted)
                if (!partial.renameTo(finalName)) {
                    backupDir.findFile(finalName)?.delete()
                    val finalized = backupDir.createFile(SNAPSHOT_MIME, finalName)
                        ?: error("SAF provider cannot finalize backup file")
                    try {
                        copyToDocument(finalized, encrypted)
                    } catch (error: Throwable) {
                        finalized.delete()
                        throw error
                    }
                    partial.delete()
                }
            } catch (error: Throwable) {
                partial.delete()
                throw error
            }
            runCatching {
                val head = backupDir.findFile(HEAD_FILE_NAME)
                    ?: backupDir.createFile(HEAD_MIME, HEAD_FILE_NAME)
                    ?: return@runCatching
                val headJson = JSONObject()
                    .put("schemaVersion", 2)
                    .put("file", finalName)
                    .put("size", encrypted.length())
                    .toString()
                appContext.contentResolver.openOutputStream(head.uri, "wt")?.bufferedWriter()?.use {
                    it.write(headJson)
                }
            }
            backupDir.listFiles()
                .filter { it.isFile && it.name?.endsWith(".drducsnapshot") == true }
                .sortedByDescending { it.lastModified() }
                .drop(MAX_SNAPSHOTS)
                .forEach { it.delete() }
            encrypted.length()
        } finally {
            root.deleteRecursively()
        }
    }

    override suspend fun restoreLatest(password: String): Boolean = withContext(Dispatchers.IO) {
        val tree = requireTree()
        val backupDir = tree.findFile(BACKUP_DIRECTORY) ?: return@withContext false
        val candidate = selectLatestSnapshot(backupDir)
            ?: return@withContext false
        val root = File(appContext.cacheDir, "saf_restore_${UUID.randomUUID()}").apply { mkdirs() }
        val encrypted = File(root, "restore.drducsnapshot")
        val plain = File(root, "restore.zip")
        try {
            appContext.contentResolver.openInputStream(candidate.uri)?.use { input ->
                encrypted.outputStream().buffered().use { input.copyTo(it) }
            } ?: return@withContext false
            require(encrypted.isFile && encrypted.length() > 0L) { "SAF backup file is empty" }
            CloudBackupCrypto.decrypt(encrypted, plain, password)
            backupRestoreGateway.restoreLocal(Uri.fromFile(plain).toString())
            true
        } finally {
            root.deleteRecursively()
        }
    }

    override suspend fun runScheduled(): Boolean {
        val password = secretStore.get(SCHEDULE_PASSWORD_REF) ?: return false
        if (!configured || scheduleIntervalHours == null) return false
        uploadLatest(password)
        return true
    }

    private fun requireTree(): DocumentFile {
        val uri = selectedTreeUri()
            ?: error("No SAF backup folder is selected")
        require(hasPersistedAccess(uri)) {
            "SAF folder permission expired; choose the folder again"
        }
        return DocumentFile.fromTreeUri(appContext, uri)
            ?: error("SAF folder is no longer available")
    }

    private fun hasPersistedAccess(uri: Uri): Boolean {
        val persisted = appContext.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri &&
                permission.isReadPermission &&
                permission.isWritePermission
        }
        if (!persisted) return false
        val tree = DocumentFile.fromTreeUri(appContext, uri) ?: return false
        return tree.canRead() && tree.canWrite()
    }

    private fun copyToDocument(target: DocumentFile, source: File) {
        appContext.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
            source.inputStream().buffered().use { input -> input.copyTo(output) }
        } ?: error("SAF provider cannot open the backup file")
        val providerLength = target.length()
        require(providerLength <= 0L || providerLength == source.length()) {
            "SAF provider wrote an incomplete backup file"
        }
    }

    private fun selectLatestSnapshot(backupDir: DocumentFile): DocumentFile? {
        val snapshots = backupDir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".drducsnapshot", ignoreCase = true) == true }
        if (snapshots.isEmpty()) return null
        val headName = backupDir.findFile(HEAD_FILE_NAME)?.let { file ->
            runCatching {
                appContext.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { reader ->
                    JSONObject(reader.readText()).optString("file")
                }
            }.getOrNull()
        }
        headName?.takeIf(::isSafeSnapshotName)?.let { name ->
            snapshots.firstOrNull { it.name == name }?.let { return it }
        }
        return snapshots.maxWithOrNull(
            compareBy<DocumentFile> { snapshotTimestamp(it.name) }
                .thenBy { it.lastModified() },
        )
    }

    private fun snapshotTimestamp(name: String?): Long {
        val value = name?.removePrefix("snapshot-")?.removeSuffix(".drducsnapshot")
        return value?.toLongOrNull() ?: Long.MIN_VALUE
    }

    private fun isSafeSnapshotName(name: String): Boolean =
        name.matches(Regex("snapshot-\\d+\\.drducsnapshot"))

    private companion object {
        const val PREFERENCES = "saf_backup"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_SCHEDULE_INTERVAL_HOURS = "schedule_interval_hours"
        const val SCHEDULE_PASSWORD_REF = "saf_backup_schedule_password"
        const val DAILY_HOURS = 24L
        const val WEEKLY_HOURS = 168L
        const val BACKUP_DIRECTORY = "LegadoBackup"
        const val HEAD_FILE_NAME = "head.json"
        const val HEAD_MIME = "application/json"
        const val MAX_SNAPSHOTS = 3
        const val SNAPSHOT_MIME = "application/octet-stream"
    }
}
