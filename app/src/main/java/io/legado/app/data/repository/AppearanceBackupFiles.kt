package io.legado.app.data.repository

import java.io.File
import java.security.MessageDigest

object AppearanceBackupFiles {
    private val safeAssetName = Regex("^[a-f0-9]{64}\\.(png|jpg|webp|svg|ttf|otf)$")

    fun copyValidatedSnapshot(source: File, destination: File) {
        val sourceStore = AppearanceFileStore(source)
        val snapshot = sourceStore.readOrNull() ?: error("Appearance snapshot is invalid")
        exportValidatedSnapshot(
            snapshot = snapshot,
            sourceAssets = File(source, AppearanceRepository.ASSET_FOLDER),
            destination = destination,
        )
    }

    fun exportValidatedSnapshot(
        snapshot: io.legado.app.domain.model.AppearanceSnapshot,
        sourceAssets: File,
        destination: File,
    ) {
        val destinationStore = AppearanceFileStore(destination)
        val validatedSnapshot = destinationStore.validate(snapshot)
        destination.mkdirs()
        destinationStore.write(validatedSnapshot)
        val destinationAssets = File(destination, AppearanceRepository.ASSET_FOLDER)
        destinationAssets.mkdirs()
        sourceAssets.listFiles().orEmpty().filter(File::isFile).forEach { file ->
            require(file.name.matches(safeAssetName)) { "Invalid appearance asset name" }
            file.copyTo(File(destinationAssets, file.name), overwrite = true)
        }
    }

    fun treeHash(root: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        root.walkTopDown()
            .filter(File::isFile)
            .filterNot { it.name.endsWith(".tmp") || it.name.endsWith(".bak") }
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
}
