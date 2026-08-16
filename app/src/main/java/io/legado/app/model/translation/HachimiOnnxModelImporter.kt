package io.legado.app.model.translation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object HachimiOnnxModelImporter {

    suspend fun import(
        context: Context,
        uri: Uri,
        onProgress: suspend (HachimiOnnxImportProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val registry = HachimiOnnxModelRegistry(context)
        val root = registry.modelRoot()
        cleanOrphanedStaging(root)
        val sourceBytes = querySourceSize(context, uri)
        if (sourceBytes != null && sourceBytes > MAX_SOURCE_PACKAGE_BYTES) {
            throw IOException("NMT model package is too large")
        }
        onProgress(
            HachimiOnnxImportProgress(
                phase = HachimiOnnxImportPhase.PREPARING,
                totalBytes = sourceBytes,
            )
        )
        val staging = File(root, "${HachimiOnnxModelRegistry.MODEL_ID}_import_${UUID.randomUUID()}")
        if (!staging.mkdirs()) {
            throw IOException("Could not create temporary NMT import directory")
        }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                extractRecognizedFiles(
                    input = input,
                    staging = staging,
                    digest = digest,
                    sourceBytes = sourceBytes,
                    onProgress = onProgress,
                )
            } ?: throw IOException("Could not read NMT model package")
            HachimiOnnxModelRegistry.REQUIRED_FILES.forEach { fileName ->
                if (!File(staging, fileName).isFile) {
                    throw IOException("NMT model package is missing $fileName")
                }
            }
            writeManifestIfMissing(staging, digest.digest().toHex())
            onProgress(
                HachimiOnnxImportProgress(
                    phase = HachimiOnnxImportPhase.WAITING_FOR_RUNTIME,
                    bytesProcessed = sourceBytes ?: 0L,
                    totalBytes = sourceBytes,
                )
            )
            HachimiOnnxRuntimeCoordinator.accessMutex.withLock {
                currentCoroutineContext().ensureActive()
                onProgress(
                    HachimiOnnxImportProgress(
                        phase = HachimiOnnxImportPhase.INSTALLING,
                        bytesProcessed = sourceBytes ?: 0L,
                        totalBytes = sourceBytes,
                    )
                )
                // Runtime sessions are generation-bound. Installing under the shared mutex keeps
                // translation away from the file swap; the next translation closes the old
                // generation and reloads the newly activated model.
                installAndActivateAtomically(root, staging)
            }
            onProgress(
                HachimiOnnxImportProgress(
                    phase = HachimiOnnxImportPhase.COMPLETE,
                    bytesProcessed = sourceBytes ?: 0L,
                    totalBytes = sourceBytes,
                )
            )
            registry.installedDirectory()
        } finally {
            staging.deleteRecursively()
        }
    }

    internal suspend fun extractRecognizedFiles(
        input: InputStream,
        staging: File,
        digest: MessageDigest,
        sourceBytes: Long? = null,
        onProgress: suspend (HachimiOnnxImportProgress) -> Unit = {},
    ): Set<String> {
        val extracted = hashSetOf<String>()
        var totalBytes = 0L
        var entryCount = 0
        var sawEntry = false
        val countingInput = CountingInputStream(input)
        var lastReportedBytes = 0L
        try {
            ZipInputStream(countingInput).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    sawEntry = true
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        throw IOException("NMT model package contains too many files")
                    }
                    val normalized = entry.name.replace('\\', '/')
                    if (normalized.startsWith('/') || normalized.split('/').any { it == ".." }) {
                        throw IOException("Unsafe path in NMT model package: ${entry.name}")
                    }
                    val fileName = normalized.substringAfterLast('/')
                    if (!entry.isDirectory && fileName.isRecognizedNmtModelFile()) {
                        if (fileName in extracted) {
                            if (fileName in HachimiOnnxModelRegistry.REQUIRED_FILES) {
                                throw IOException("Duplicate required NMT model file: $fileName")
                            }
                        } else {
                            extracted.add(fileName)
                            val destination = File(staging, fileName)
                            val importing = File(staging, "$fileName.importing")
                            importing.delete()
                            try {
                                importing.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var entryBytes = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val read = zip.read(buffer)
                                        if (read < 0) break
                                        entryBytes += read
                                        totalBytes += read
                                        if (isPackageSizeExceeded(entryBytes, totalBytes)) {
                                            throw IOException("NMT model package is too large")
                                        }
                                        output.write(buffer, 0, read)
                                        if (fileName in HachimiOnnxModelRegistry.REQUIRED_FILES) {
                                            digest.update(buffer, 0, read)
                                        }
                                        if (countingInput.bytesRead - lastReportedBytes >= PROGRESS_STEP_BYTES) {
                                            lastReportedBytes = countingInput.bytesRead
                                            onProgress(
                                                HachimiOnnxImportProgress(
                                                    phase = HachimiOnnxImportPhase.EXTRACTING,
                                                    bytesProcessed = countingInput.bytesRead,
                                                    totalBytes = sourceBytes,
                                                    currentFile = fileName,
                                                )
                                            )
                                        }
                                    }
                                    if (fileName in HachimiOnnxModelRegistry.REQUIRED_FILES && entryBytes == 0L) {
                                        throw IOException("NMT model file is empty: $fileName")
                                    }
                                }
                                if (!importing.renameTo(destination)) {
                                    importing.copyTo(destination, overwrite = true)
                                    importing.delete()
                                }
                            } catch (error: Throwable) {
                                importing.delete()
                                destination.delete()
                                throw error
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (error: ZipException) {
            throw IOException("NMT model package is not a readable ZIP", error)
        }
        if (!sawEntry) {
            throw IOException("NMT model package is not a valid ZIP or is empty")
        }
        if (extracted.isEmpty()) {
            throw IOException("NMT model package does not contain recognized Hachimi ONNX files")
        }
        return extracted
    }

    internal fun installAndActivateAtomically(root: File, staging: File) {
        installAtomically(root, staging)
        HachimiOnnxRuntimeCoordinator.markModelChanged()
    }

    private fun String.isRecognizedNmtModelFile(): Boolean =
        this in HachimiOnnxModelRegistry.REQUIRED_FILES ||
            HachimiOnnxModelRegistry.isOptionalCompanionFile(this)

    private fun writeManifestIfMissing(directory: File, sha256: String) {
        val manifest = File(directory, HachimiOnnxModelRegistry.MANIFEST_FILE)
        if (manifest.isFile) return
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("id", HachimiOnnxModelRegistry.MODEL_ID)
            .put("name", "HachimiMT-60 zh-vi ONNX INT8")
            .put("sha256", sha256)
        manifest.writeText(json.toString(2))
    }

    internal fun installAtomically(root: File, staging: File) {
        val target = File(root, HachimiOnnxModelRegistry.MODEL_ID)
        val installing = File(root, "${HachimiOnnxModelRegistry.MODEL_ID}_installing")
        val backup = File(root, "${HachimiOnnxModelRegistry.MODEL_ID}_backup")
        installing.deleteRecursively()
        backup.deleteRecursively()
        if (!staging.renameTo(installing)) {
            requireEnoughSpaceForCopy(root, staging.directorySize())
            staging.copyRecursively(installing, overwrite = true)
            staging.deleteRecursively()
        }
        var previousMoved = false
        var targetWriteStarted = false
        try {
            requireCompleteModelDirectory(installing)
            if (target.exists()) {
                if (!target.renameTo(backup)) {
                    throw IOException("Could not prepare old NMT model replacement")
                }
                previousMoved = true
            }
            targetWriteStarted = true
            if (!installing.renameTo(target)) {
                requireEnoughSpaceForCopy(root, installing.directorySize())
                installing.copyRecursively(target, overwrite = true)
                installing.deleteRecursively()
            }
            requireCompleteModelDirectory(target)
            backup.deleteRecursively()
        } catch (error: Throwable) {
            if (targetWriteStarted) {
                target.deleteRecursively()
            }
            if (previousMoved && backup.exists()) backup.renameTo(target)
            throw error
        } finally {
            installing.deleteRecursively()
            if (backup.exists() && !target.exists()) backup.renameTo(target)
        }
    }

    private fun requireCompleteModelDirectory(directory: File) {
        HachimiOnnxModelRegistry.REQUIRED_FILES.forEach { fileName ->
            if (!File(directory, fileName).isFile) {
                throw IOException("Prepared NMT model is missing $fileName")
            }
        }
    }

    private fun requireEnoughSpaceForCopy(root: File, bytesToCopy: Long) {
        if (!hasEnoughUsableSpaceForCopy(root.usableSpace, bytesToCopy)) {
            throw IOException("Not enough free storage to install NMT model")
        }
    }

    private fun File.directorySize(): Long =
        walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun querySourceSize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index).takeIf { it >= 0L }
            } else {
                null
            }
        }
    }.getOrNull()

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int = super.read().also { value ->
            if (value >= 0) bytesRead++
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { count ->
                if (count > 0) bytesRead += count
            }
    }

    internal fun isPackageSizeExceeded(entryBytes: Long, totalBytes: Long): Boolean =
        entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES

    internal fun hasEnoughUsableSpaceForCopy(usableSpace: Long, bytesToCopy: Long): Boolean =
        usableSpace >= bytesToCopy + MIN_FREE_SPACE_AFTER_COPY

    internal fun cleanOrphanedStaging(root: File) {
        runCatching {
            root.listFiles()?.forEach { file ->
                if (file.isDirectory && (
                    file.name.contains("_import_") ||
                    file.name.endsWith("_installing") ||
                    file.name.endsWith("_backup")
                )) {
                    file.deleteRecursively()
                }
            }
        }
    }

    private const val MAX_ENTRIES = 200
    private const val MAX_ENTRY_BYTES = 1_200L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 2_400L * 1024L * 1024L
    private const val MAX_SOURCE_PACKAGE_BYTES = 1_500L * 1024L * 1024L
    private const val MIN_FREE_SPACE_AFTER_COPY = 128L * 1024L * 1024L
    private const val PROGRESS_STEP_BYTES = 512L * 1024L
}

enum class HachimiOnnxImportPhase {
    PREPARING,
    EXTRACTING,
    WAITING_FOR_RUNTIME,
    INSTALLING,
    COMPLETE,
}

data class HachimiOnnxImportProgress(
    val phase: HachimiOnnxImportPhase,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long? = null,
    val currentFile: String? = null,
) {
    val percent: Int?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { total ->
                (bytesProcessed.coerceIn(0L, total) * 100L / total).toInt()
            }
}
