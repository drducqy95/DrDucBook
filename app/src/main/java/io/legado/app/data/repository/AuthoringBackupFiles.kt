package io.legado.app.data.repository

import io.legado.app.domain.model.AuthoringProject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object AuthoringBackupFiles {

    private val safePathSegment = Regex("^[A-Za-z0-9._-]+$")
    private val safeAssetName = Regex("^[a-f0-9]{64}\\.[a-z0-9]{1,8}$")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        classDiscriminator = "blockType"
    }

    fun copyValidatedSnapshot(source: File, destination: File) {
        if (!source.exists()) {
            destination.deleteRecursively()
            destination.mkdirs()
            return
        }
        val parent = destination.parentFile ?: destination.absoluteFile.parentFile
            ?: error("Authoring backup destination has no parent")
        val staging = File(parent, "${destination.name}.staging")
        staging.deleteRecursively()
        try {
            staging.mkdirs()
            copyValidProjects(source, staging)
            copyValidAssets(source, staging)
            validateSnapshot(staging)
            destination.deleteRecursively()
            staging.copyRecursively(destination, overwrite = true)
        } finally {
            staging.deleteRecursively()
        }
    }

    fun restoreValidatedSnapshot(source: File, destination: File) {
        require(source.isDirectory) { "Authoring snapshot is missing" }
        val parent = destination.parentFile ?: error("Authoring destination has no parent")
        val staging = File(parent, "${destination.name}.restore-staging")
        val previous = File(parent, "${destination.name}.restore-previous")
        staging.deleteRecursively()
        previous.deleteRecursively()
        copyValidatedSnapshot(source, staging)
        validateSnapshot(staging)
        try {
            if (destination.exists()) {
                moveOrCopy(destination, previous)
            }
            moveOrCopy(staging, destination)
            previous.deleteRecursively()
        } catch (error: Throwable) {
            destination.deleteRecursively()
            if (previous.exists()) {
                moveOrCopy(previous, destination)
            }
            throw error
        } finally {
            staging.deleteRecursively()
            previous.deleteRecursively()
        }
    }

    fun validateSnapshot(root: File) {
        require(root.isDirectory) { "Authoring snapshot directory is missing" }
        root.walkTopDown().forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            require(!relative.startsWith("recovery/")) { "Authoring recovery data must not be backed up" }
            require(!file.name.endsWith(".tmp")) { "Temporary authoring file must not be backed up" }
        }
        projectManifestFiles(root).forEach { file ->
            val envelope = readEnvelope(file)
            require(envelope.contentHash.isBlank() || envelope.contentHash == projectContentHash(envelope.project)) {
                "Authoring project content hash mismatch: ${envelope.project.id}"
            }
        }
        assetIndexFiles(root).forEach { indexFile ->
            val index = json.decodeFromString<AuthoringAssetIndex>(indexFile.readText())
            index.assets.forEach { asset ->
                require(asset.assetId.matches(Regex("^[a-f0-9]{64}$"))) { "Invalid authoring asset id" }
                require(asset.relativePath.startsWith("assets/")) { "Invalid authoring asset path" }
                val assetFile = File(root, asset.relativePath)
                require(assetFile.isFile) { "Authoring asset is missing: ${asset.originalName}" }
                require(assetFile.name.matches(safeAssetName)) { "Invalid authoring asset file name" }
                require(assetFile.length() == asset.sizeBytes) { "Authoring asset size mismatch: ${asset.originalName}" }
                require(assetFile.sha256Hex() == asset.sha256) {
                    "Authoring asset hash mismatch: ${asset.originalName}"
                }
            }
        }
    }

    fun treeHash(root: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        root.walkTopDown()
            .filter(File::isFile)
            .filterNot { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                file.name.endsWith(".tmp") || relative.startsWith("recovery/")
            }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .forEach { file ->
                digest.update(file.relativeTo(root).invariantSeparatorsPath.toByteArray())
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyValidProjects(source: File, destination: File) {
        val sourceProjects = File(source, PROJECTS_DIR)
        val destinationProjects = File(destination, PROJECTS_DIR)
        sourceProjects.listFiles(File::isDirectory).orEmpty().forEach { projectDir ->
            require(projectDir.name.matches(safePathSegment)) { "Invalid authoring project directory" }
            val manifest = File(projectDir, PROJECT_MANIFEST_FILE).takeIf(File::isFile) ?: return@forEach
            val envelope = readEnvelope(manifest)
            require(envelope.contentHash.isBlank() || envelope.contentHash == projectContentHash(envelope.project)) {
                "Authoring project content hash mismatch: ${envelope.project.id}"
            }
            val targetDir = File(destinationProjects, projectDir.name).apply { mkdirs() }
            manifest.copyTo(File(targetDir, PROJECT_MANIFEST_FILE), overwrite = true)
        }
    }

    private fun copyValidAssets(source: File, destination: File) {
        val sourceAssets = File(source, ASSETS_DIR)
        val destinationAssets = File(destination, ASSETS_DIR)
        sourceAssets.listFiles(File::isDirectory).orEmpty().forEach { projectAssets ->
            require(projectAssets.name.matches(safePathSegment)) { "Invalid authoring asset directory" }
            val sourceIndex = File(projectAssets, ASSET_INDEX_FILE).takeIf(File::isFile) ?: return@forEach
            val index = json.decodeFromString<AuthoringAssetIndex>(sourceIndex.readText())
            val targetDir = File(destinationAssets, projectAssets.name).apply { mkdirs() }
            sourceIndex.copyTo(File(targetDir, ASSET_INDEX_FILE), overwrite = true)
            index.assets.forEach { asset ->
                require(asset.relativePath.startsWith("assets/")) { "Invalid authoring asset path" }
                val assetFile = File(source, asset.relativePath)
                require(assetFile.isFile) { "Authoring asset is missing: ${asset.originalName}" }
                require(assetFile.name.matches(safeAssetName)) { "Invalid authoring asset file name" }
                require(assetFile.length() == asset.sizeBytes) { "Authoring asset size mismatch: ${asset.originalName}" }
                require(assetFile.sha256Hex() == asset.sha256) {
                    "Authoring asset hash mismatch: ${asset.originalName}"
                }
                val target = File(destination, asset.relativePath)
                target.parentFile?.mkdirs()
                assetFile.copyTo(target, overwrite = true)
            }
        }
    }

    private fun projectManifestFiles(root: File): List<File> =
        File(root, PROJECTS_DIR)
            .listFiles(File::isDirectory)
            .orEmpty()
            .mapNotNull { File(it, PROJECT_MANIFEST_FILE).takeIf(File::isFile) }

    private fun assetIndexFiles(root: File): List<File> =
        File(root, ASSETS_DIR)
            .listFiles(File::isDirectory)
            .orEmpty()
            .mapNotNull { File(it, ASSET_INDEX_FILE).takeIf(File::isFile) }

    private fun readEnvelope(file: File): AuthoringProjectEnvelope =
        AuthoringProjectMigrationDispatcher.migrate(
            json.decodeFromString<AuthoringProjectEnvelope>(file.readText()),
        )

    private fun projectContentHash(project: AuthoringProject): String =
        json.encodeToString(project).toByteArray().sha256Hex()

    private fun moveOrCopy(source: File, target: File) {
        target.deleteRecursively()
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            source.copyRecursively(target, overwrite = true)
            source.deleteRecursively()
        }
    }

    private fun ByteArray.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun File.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private const val PROJECTS_DIR = "projects"
    private const val ASSETS_DIR = "assets"
    private const val PROJECT_MANIFEST_FILE = "manifest.json"
    private const val ASSET_INDEX_FILE = "asset-index.json"
}
