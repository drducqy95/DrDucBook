package io.legado.app.data.repository

import io.legado.app.domain.gateway.AuthoringRecoveryDiagnostic
import io.legado.app.domain.gateway.AuthoringRecoveryType
import io.legado.app.domain.model.AuthoringProject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class AuthoringProjectFileStore(
    private val root: File,
) {

    private val projectsDir = File(root, "projects")
    private val assetsDir = File(root, "assets")
    private val legacyDir = File(root, "legacy-projects")
    private val recoveryDir = File(root, "recovery")
    private val quarantineDir = File(recoveryDir, "quarantine")
    private val historyDir = File(recoveryDir, "history")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        classDiscriminator = "blockType"
    }
    private val legacyJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun loadProjects(): List<AuthoringProject> {
        ensureDirectories()
        migrateLegacyProjectFiles()
        return projectsDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .mapNotNull(::readProjectDirectory)
            .distinctBy(AuthoringProject::id)
            .sortedByDescending(AuthoringProject::updatedAt)
            .toList()
    }

    fun saveProject(project: AuthoringProject) {
        ensureDirectories()
        val directory = projectDirectory(project.id).apply { mkdirs() }
        snapshotExistingManifest(project.id, directory)
        val envelope = AuthoringProjectMigrationDispatcher.envelopeFor(
            project = project,
            savedAt = System.currentTimeMillis(),
            contentHash = projectContentHash(project),
            assets = readAssetIndex(project.id).assets,
        )
        writeAtomically(projectManifestFile(directory), json.encodeToString(envelope).toByteArray())
        snapshotExistingManifest(project.id, directory)
    }

    fun deleteProject(id: String) {
        projectDirectory(id).deleteRecursively()
        legacyProjectFile(id).delete()
        File(assetsDir, safePathSegment(id)).deleteRecursively()
        historyDirectory(id).deleteRecursively()
    }

    fun importAsset(projectId: String, displayName: String, bytes: ByteArray): File {
        ensureDirectories()
        val now = System.currentTimeMillis()
        val sha256 = bytes.sha256Hex()
        val extension = displayName.safeExtension()
        val assetDirectory = File(assetsDir, safePathSegment(projectId)).apply { mkdirs() }
        val file = File(assetDirectory, "$sha256.$extension")
        if (!file.isFile) {
            writeAtomically(file, bytes)
        }
        val index = readAssetIndex(projectId)
        val entry = AuthoringAssetRef(
            assetId = sha256,
            originalName = displayName.safeDisplayName(),
            relativePath = root.toPath().relativize(file.toPath()).toString().replace('\\', '/'),
            sha256 = sha256,
            sizeBytes = bytes.size.toLong(),
            createdAt = index.assets.firstOrNull { it.assetId == sha256 }?.createdAt ?: now,
            updatedAt = now,
        )
        writeAssetIndex(projectId, index.upsert(entry, now))
        refreshProjectManifestAssets(projectId)
        return file
    }

    fun recoveryDiagnostics(): List<AuthoringRecoveryDiagnostic> {
        ensureDirectories()
        return recoveryDir.listFiles { file -> file.isFile && file.name.endsWith(RECOVERY_META_SUFFIX) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    json.decodeFromString<AuthoringRecoveryRecord>(file.readText()).toDiagnostic()
                }.getOrNull()
            }
            .sortedByDescending(AuthoringRecoveryDiagnostic::createdAt)
    }

    fun restoreLatestProjectSnapshot(projectId: String): AuthoringProject? {
        ensureDirectories()
        return restoreLatestSnapshot(projectId, projectDirectory(projectId))
    }

    fun deleteRecoveryDiagnostic(id: String) {
        ensureDirectories()
        val meta = recoveryMetaFile(id).takeIf(File::isFile) ?: return
        val record = runCatching {
            json.decodeFromString<AuthoringRecoveryRecord>(meta.readText())
        }.getOrNull()
        record?.recoveryPath
            ?.let { File(root, it) }
            ?.takeIf { file -> file.toPath().normalize().startsWith(root.toPath().normalize()) }
            ?.delete()
        meta.delete()
    }

    private fun readProjectDirectory(directory: File): AuthoringProject? {
        val manifest = projectManifestFile(directory).takeIf(File::isFile) ?: return null
        return try {
            readProjectEnvelope(manifest).project
        } catch (failure: ProjectReadFailure) {
            val projectId = directory.name
            quarantineFile(
                projectId = projectId,
                type = failure.type,
                file = manifest,
                message = failure.message.orEmpty(),
            )
            restoreLatestSnapshot(projectId, directory)
        }
    }

    private fun migrateLegacyProjectFiles() {
        projectsDir.listFiles { file -> file.isFile && file.extension == LEGACY_EXTENSION }
            .orEmpty()
            .forEach { file ->
                val project = runCatching {
                    legacyJson.decodeFromString<AuthoringProject>(file.readText())
                }.recoverCatching {
                    json.decodeFromString<AuthoringProject>(file.readText())
                }.getOrNull() ?: return@forEach
                saveProject(project)
                legacyDir.mkdirs()
                moveReplacing(
                    source = file,
                    target = File(legacyDir, file.name),
                )
            }
    }

    private fun refreshProjectManifestAssets(projectId: String) {
        val directory = projectDirectory(projectId)
        val manifest = projectManifestFile(directory).takeIf(File::isFile) ?: return
        val envelope = runCatching {
            readProjectEnvelope(manifest)
        }.getOrNull() ?: return
        writeAtomically(
            manifest,
            json.encodeToString(
                envelope.copy(
                    assets = readAssetIndex(projectId).assets,
                    savedAt = System.currentTimeMillis(),
                )
            ).toByteArray(),
        )
    }

    private fun readAssetIndex(projectId: String): AuthoringAssetIndex {
        val file = assetIndexFile(projectId)
        if (!file.isFile) return AuthoringAssetIndex(projectId = projectId)
        val index = runCatching {
            json.decodeFromString<AuthoringAssetIndex>(file.readText())
        }.getOrElse { error ->
            quarantineFile(
                projectId = projectId,
                type = AuthoringRecoveryType.CORRUPT_ASSET_INDEX,
                file = file,
                message = "Asset index is corrupt: ${error.message.orEmpty()}",
            )
            AuthoringAssetIndex(projectId = projectId)
        }
        return index.also { reportMissingAssets(projectId, it.assets) }
    }

    private fun writeAssetIndex(projectId: String, index: AuthoringAssetIndex) {
        val file = assetIndexFile(projectId)
        file.parentFile?.mkdirs()
        writeAtomically(file, json.encodeToString(index).toByteArray())
    }

    private fun ensureDirectories() {
        projectsDir.mkdirs()
        assetsDir.mkdirs()
        quarantineDir.mkdirs()
        historyDir.mkdirs()
    }

    private fun projectDirectory(id: String): File =
        File(projectsDir, safePathSegment(id))

    private fun historyDirectory(id: String): File =
        File(historyDir, safePathSegment(id))

    private fun projectManifestFile(directory: File): File =
        File(directory, PROJECT_MANIFEST_FILE)

    private fun legacyProjectFile(id: String): File =
        File(projectsDir, "${safePathSegment(id)}.$LEGACY_EXTENSION")

    private fun assetIndexFile(projectId: String): File =
        File(File(assetsDir, safePathSegment(projectId)), ASSET_INDEX_FILE)

    private fun recoveryMetaFile(id: String): File =
        File(recoveryDir, "$id$RECOVERY_META_SUFFIX")

    private fun projectContentHash(project: AuthoringProject): String =
        json.encodeToString(project).toByteArray().sha256Hex()

    private fun readProjectEnvelope(manifest: File): AuthoringProjectEnvelope {
        val envelope = try {
            AuthoringProjectMigrationDispatcher.migrate(
                json.decodeFromString<AuthoringProjectEnvelope>(manifest.readText()),
            )
        } catch (error: Throwable) {
            val type = if (error.message.orEmpty().contains("Unsupported authoring schema")) {
                AuthoringRecoveryType.UNSUPPORTED_SCHEMA
            } else {
                AuthoringRecoveryType.CORRUPT_MANIFEST
            }
            throw ProjectReadFailure(type, "Authoring manifest is unreadable: ${error.message.orEmpty()}", error)
        }
        if (envelope.contentHash.isNotBlank() && envelope.contentHash != projectContentHash(envelope.project)) {
            throw ProjectReadFailure(
                AuthoringRecoveryType.HASH_MISMATCH,
                "Authoring project content hash mismatch: ${envelope.project.id}",
            )
        }
        return envelope
    }

    private fun snapshotExistingManifest(projectId: String, directory: File) {
        val manifest = projectManifestFile(directory).takeIf(File::isFile) ?: return
        val envelope = runCatching { readProjectEnvelope(manifest) }.getOrNull() ?: return
        val snapshotDirectory = historyDirectory(projectId).apply { mkdirs() }
        val snapshotName = uniqueSnapshotName(
            savedAt = System.currentTimeMillis(),
            contentHash = envelope.contentHash,
        )
        writeAtomically(File(snapshotDirectory, snapshotName), manifest.readBytes())
        pruneHistory(projectId)
    }

    private fun restoreLatestSnapshot(projectId: String, directory: File): AuthoringProject? {
        val snapshot = historyDirectory(projectId)
            .listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .maxByOrNull(File::lastModified)
            ?: return null
        val envelope = runCatching { readProjectEnvelope(snapshot) }.getOrNull() ?: return null
        directory.mkdirs()
        writeAtomically(projectManifestFile(directory), snapshot.readBytes())
        return envelope.project
    }

    private fun pruneHistory(projectId: String) {
        historyDirectory(projectId)
            .listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(HISTORY_LIMIT)
            .forEach(File::delete)
    }

    private fun quarantineFile(
        projectId: String,
        type: AuthoringRecoveryType,
        file: File,
        message: String,
    ) {
        val now = System.currentTimeMillis()
        val id = "${now}-${type.name.lowercase()}-${safePathSegment(projectId)}"
        val target = File(quarantineDir, "$id-${safePathSegment(file.name)}")
        val size = file.takeIf(File::isFile)?.length() ?: 0L
        if (file.isFile) {
            moveReplacing(file, target)
        }
        writeRecoveryRecord(
            AuthoringRecoveryRecord(
                id = id,
                projectId = projectId,
                type = type,
                message = redact(message),
                sourcePath = relativePath(file),
                recoveryPath = target.takeIf(File::isFile)?.let(::relativePath),
                createdAt = now,
                sizeBytes = size,
            )
        )
    }

    private fun reportMissingAssets(projectId: String, assets: List<AuthoringAssetRef>) {
        assets.forEach { asset ->
            val file = File(root, asset.relativePath)
            if (!file.isFile) {
                val id = "missing-asset-${safePathSegment(projectId)}-${safePathSegment(asset.assetId)}"
                writeRecoveryRecord(
                    AuthoringRecoveryRecord(
                        id = id,
                        projectId = projectId,
                        type = AuthoringRecoveryType.MISSING_ASSET,
                        message = "Authoring asset is missing: ${asset.originalName}",
                        sourcePath = asset.relativePath,
                        recoveryPath = null,
                        createdAt = System.currentTimeMillis(),
                        sizeBytes = asset.sizeBytes,
                    )
                )
            }
        }
    }

    private fun writeRecoveryRecord(record: AuthoringRecoveryRecord) {
        val file = recoveryMetaFile(record.id)
        if (file.isFile) return
        writeAtomically(file, json.encodeToString(record).toByteArray())
    }

    private fun uniqueSnapshotName(savedAt: Long, contentHash: String): String {
        val suffix = contentHash.take(12).ifBlank { "snapshot" }
        return "$savedAt-$suffix.json"
    }

    private fun relativePath(file: File): String =
        runCatching { root.toPath().relativize(file.toPath()).toString().replace('\\', '/') }
            .getOrDefault(file.name)

    private fun redact(message: String): String =
        message.replace(root.absolutePath, "<authoring-root>")

    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        moveReplacing(temporary, target)
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        const val PROJECT_MANIFEST_FILE = "manifest.json"
        const val ASSET_INDEX_FILE = "asset-index.json"
        const val LEGACY_EXTENSION = "json"
        const val RECOVERY_META_SUFFIX = ".meta.json"
        const val HISTORY_LIMIT = 5
    }
}

@Serializable
internal data class AuthoringProjectEnvelope(
    val schemaVersion: Int = AuthoringProjectMigrationDispatcher.CURRENT_SCHEMA_VERSION,
    val project: AuthoringProject,
    val assets: List<AuthoringAssetRef> = emptyList(),
    val contentHash: String = "",
    val savedAt: Long = 0L,
)

@Serializable
internal data class AuthoringAssetRef(
    val assetId: String,
    val originalName: String,
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class AuthoringAssetIndex(
    val schemaVersion: Int = AuthoringProjectMigrationDispatcher.CURRENT_SCHEMA_VERSION,
    val projectId: String,
    val assets: List<AuthoringAssetRef> = emptyList(),
    val contentHash: String = "",
    val updatedAt: Long = 0L,
) {
    fun upsert(entry: AuthoringAssetRef, now: Long): AuthoringAssetIndex {
        val nextAssets = (assets.filterNot { it.assetId == entry.assetId } + entry)
            .sortedBy(AuthoringAssetRef::assetId)
        return copy(
            assets = nextAssets,
            contentHash = nextAssets.joinToString("|") { "${it.assetId}:${it.sha256}:${it.sizeBytes}" }
                .toByteArray()
                .sha256Hex(),
            updatedAt = now,
        )
    }
}

@Serializable
private data class AuthoringRecoveryRecord(
    val id: String,
    val projectId: String,
    val type: AuthoringRecoveryType,
    val message: String,
    val sourcePath: String,
    val recoveryPath: String? = null,
    val createdAt: Long,
    val sizeBytes: Long,
) {
    fun toDiagnostic(): AuthoringRecoveryDiagnostic = AuthoringRecoveryDiagnostic(
        id = id,
        projectId = projectId,
        type = type,
        message = message,
        sourcePath = sourcePath,
        recoveryPath = recoveryPath,
        createdAt = createdAt,
        sizeBytes = sizeBytes,
    )
}

private class ProjectReadFailure(
    val type: AuthoringRecoveryType,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal object AuthoringProjectMigrationDispatcher {
    const val CURRENT_SCHEMA_VERSION = 2

    fun envelopeFor(
        project: AuthoringProject,
        savedAt: Long,
        contentHash: String,
        assets: List<AuthoringAssetRef>,
    ): AuthoringProjectEnvelope = AuthoringProjectEnvelope(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        project = project,
        assets = assets.sortedBy(AuthoringAssetRef::assetId),
        contentHash = contentHash,
        savedAt = savedAt,
    )

    fun migrate(envelope: AuthoringProjectEnvelope): AuthoringProjectEnvelope {
        require(envelope.schemaVersion <= CURRENT_SCHEMA_VERSION) {
            "Unsupported authoring schema ${envelope.schemaVersion}"
        }
        return when (envelope.schemaVersion) {
            1 -> envelope.copy(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                // Version 2 adds workflow fields whose serialization changes the project hash.
                // The next normal save writes a fresh v2 hash after the legacy project is loaded.
                contentHash = "",
            )
            else -> envelope.copy(schemaVersion = CURRENT_SCHEMA_VERSION)
        }
    }
}

private fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

private fun String.safeDisplayName(): String =
    substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "asset.bin" }

private fun String.safeExtension(): String =
    safeDisplayName()
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: "bin"

private fun safePathSegment(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "project" }
