package io.legado.app.utils.compress

import android.annotation.SuppressLint
import io.legado.app.utils.DebugLog
import io.legado.app.utils.printOnDebug
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.*

@SuppressLint("ObsoleteSdkInt")
@Suppress("unused", "MemberVisibilityCanBePrivate")
object ZipUtils {

    data class ExtractionLimits(
        val maxEntries: Int = 10_000,
        val maxEntryBytes: Long = 1L * 1024L * 1024L * 1024L,
        val maxTotalBytes: Long = 4L * 1024L * 1024L * 1024L,
        val maxCompressionRatio: Int = 200,
        val ratioCheckMinBytes: Long = 1L * 1024L * 1024L,
    )

    private val defaultExtractionLimits = ExtractionLimits()

    fun gzipByteArray(byteArray: ByteArray): ByteArray {
        val byteOut = ByteArrayOutputStream()
        val zip = GZIPOutputStream(byteOut)
        return zip.use {
            it.write(byteArray)
            byteOut.use {
                byteOut.toByteArray()
            }
        }
    }

    fun zipByteArray(byteArray: ByteArray, fileName: String): ByteArray {
        val byteOut = ByteArrayOutputStream()
        val zipOutputStream = ZipOutputStream(byteOut)
        zipOutputStream.putNextEntry(ZipEntry(fileName))
        zipOutputStream.write(byteArray)
        zipOutputStream.closeEntry()
        zipOutputStream.finish()
        return zipOutputStream.use {
            byteOut.use {
                byteOut.toByteArray()
            }
        }
    }

    /**
     * Zip the files.
     *
     * @param srcFiles    The source of files.
     * @param zipFilePath The path of ZIP file.
     * @return `true`: success<br></br>`false`: fail
     * @throws IOException if an I/O error has occurred
     */
    suspend fun zipFiles(
        srcFiles: Collection<String>,
        zipFilePath: String
    ): Boolean {
        return zipFiles(srcFiles, zipFilePath, null)
    }

    /**
     * Zip the files.
     *
     * @param srcFilePaths The paths of source files.
     * @param zipFilePath  The path of ZIP file.
     * @param comment      The comment.
     * @return `true`: success<br></br>`false`: fail
     * @throws IOException if an I/O error has occurred
     */
    suspend fun zipFiles(
        srcFilePaths: Collection<String>?,
        zipFilePath: String?,
        comment: String?
    ): Boolean = withContext(IO) {
        if (srcFilePaths == null || zipFilePath == null) return@withContext false
        ZipOutputStream(FileOutputStream(zipFilePath)).use {
            for (srcFile in srcFilePaths) {
                if (!zipFile(getFileByPath(srcFile)!!, "", it, comment))
                    return@withContext false
            }
            return@withContext true
        }
    }

    /**
     * Zip the files.
     *
     * @param srcFiles The source of files.
     * @param zipFile  The ZIP file.
     * @param comment  The comment.
     * @return `true`: success<br></br>`false`: fail
     * @throws IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    @JvmOverloads
    fun zipFiles(
        srcFiles: Collection<File>?,
        zipFile: File?,
        comment: String? = null
    ): Boolean {
        if (srcFiles == null || zipFile == null) return false
        ZipOutputStream(FileOutputStream(zipFile)).use {
            for (srcFile in srcFiles) {
                if (!zipFile(srcFile, "", it, comment)) return false
            }
            return true
        }
    }

    /**
     * Zip the file.
     *
     * @param srcFilePath The path of source file.
     * @param zipFilePath The path of ZIP file.
     * @return `true`: success<br></br>`false`: fail
     * @throws IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    fun zipFile(
        srcFilePath: String,
        zipFilePath: String
    ): Boolean {
        return zipFile(getFileByPath(srcFilePath), getFileByPath(zipFilePath), null)
    }

    /**
     * Zip the file.
     *
     * @param srcFilePath The path of source file.
     * @param zipFilePath The path of ZIP file.
     * @param comment     The comment.
     * @return `true`: success<br></br>`false`: fail
     * @throws IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    fun zipFile(
        srcFilePath: String,
        zipFilePath: String,
        comment: String
    ): Boolean {
        return zipFile(getFileByPath(srcFilePath), getFileByPath(zipFilePath), comment)
    }

    /**
     * Zip the file.
     *
     * @param srcFile The source of file.
     * @param zipFile The ZIP file.
     * @param comment The comment.
     * @return `true`: success<br></br>`false`: fail
     * @throws IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    @JvmOverloads
    fun zipFile(
        srcFile: File?,
        zipFile: File?,
        comment: String? = null
    ): Boolean {
        if (srcFile == null || zipFile == null) return false
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            return zipFile(srcFile, "", zos, comment)
        }
    }

    @Throws(IOException::class)
    private fun zipFile(
        srcFile: File,
        rootPath: String,
        zos: ZipOutputStream,
        comment: String?
    ): Boolean {
        var rootPath1 = rootPath
        if (!srcFile.exists()) return true
        rootPath1 = rootPath1 + (if (isSpace(rootPath1)) "" else File.separator) + srcFile.name
        if (srcFile.isDirectory) {
            val fileList = srcFile.listFiles()
            if (fileList == null || fileList.isEmpty()) {
                val entry = ZipEntry("$rootPath1/")
                entry.comment = comment
                zos.putNextEntry(entry)
                zos.closeEntry()
            } else {
                for (file in fileList) {
                    if (!zipFile(file, rootPath1, zos, comment)) return false
                }
            }
        } else {
            BufferedInputStream(FileInputStream(srcFile)).use {
                val entry = ZipEntry(rootPath1)
                entry.comment = comment
                zos.putNextEntry(entry)
                it.copyTo(zos)
                zos.closeEntry()
            }
        }
        return true
    }

    @Throws(SecurityException::class)
    fun unZipToPath(file: File, path: String, filter: ((String) -> Boolean)? = null): List<File> {
        validateArchive(file, defaultExtractionLimits)
        return FileInputStream(file).use {
            unZipToPath(it, File(path), filter, defaultExtractionLimits)
        }
    }

    @Throws(SecurityException::class)
    fun unZipToPath(file: File, dir: File, filter: ((String) -> Boolean)? = null): List<File> {
        validateArchive(file, defaultExtractionLimits)
        return FileInputStream(file).use {
            unZipToPath(it, dir, filter, defaultExtractionLimits)
        }
    }

    @Throws(SecurityException::class)
    fun unZipToPath(
        file: File,
        dir: File,
        filter: ((String) -> Boolean)? = null,
        limits: ExtractionLimits,
    ): List<File> {
        validateArchive(file, limits)
        return FileInputStream(file).use { unZipToPath(it, dir, filter, limits) }
    }

    @Throws(SecurityException::class)
    fun unZipToPath(
        inputStream: InputStream,
        path: String,
        filter: ((String) -> Boolean)? = null
    ): List<File> {
        return ZipInputStream(inputStream).use {
            unZipToPath(it, File(path), filter, defaultExtractionLimits)
        }
    }

    @Throws(SecurityException::class)
    fun unZipToPath(
        inputStream: InputStream,
        dir: File,
        filter: ((String) -> Boolean)? = null
    ): List<File> {
        return unZipToPath(inputStream, dir, filter, defaultExtractionLimits)
    }

    @Throws(SecurityException::class)
    fun unZipToPath(
        inputStream: InputStream,
        dir: File,
        filter: ((String) -> Boolean)? = null,
        limits: ExtractionLimits,
    ): List<File> {
        return ZipInputStream(inputStream).use {
            unZipToPath(it, dir, filter, limits)
        }
    }

    @Throws(SecurityException::class)
    private fun unZipToPath(
        zipInputStream: ZipInputStream,
        dir: File,
        filter: ((String) -> Boolean)? = null,
        limits: ExtractionLimits,
    ): List<File> {
        val files = arrayListOf<File>()
        val root = dir.canonicalFile.apply { mkdirs() }
        val rootPrefix = root.path + File.separator
        var entryCount = 0
        var totalBytes = 0L
        var entry: ZipEntry?
        while (zipInputStream.nextEntry.also { entry = it } != null) {
            val entryName = entry!!.name
            entryCount++
            if (entryCount > limits.maxEntries) {
                throw SecurityException("ZIP contains too many entries")
            }
            val entryFile = File(root, entryName).canonicalFile
            if (entryFile.path != root.path && !entryFile.path.startsWith(rootPrefix)) {
                throw SecurityException("ZIP entry escapes the destination directory")
            }
            if (entry.isDirectory) {
                if (!entryFile.exists()) {
                    if (!entryFile.mkdirs()) throw IOException("Unable to create ${entryFile.path}")
                }
                continue
            }
            if (filter != null && !filter.invoke(entryName)) continue
            if (entryFile.parentFile?.exists() != true) {
                if (entryFile.parentFile?.mkdirs() != true) {
                    throw IOException("Unable to create ${entryFile.parentFile?.path}")
                }
            }
            var entryBytes = 0L
            try {
                FileOutputStream(entryFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zipInputStream.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        totalBytes += read
                        if (entryBytes > limits.maxEntryBytes) {
                            throw SecurityException("ZIP entry exceeds the extraction limit")
                        }
                        if (totalBytes > limits.maxTotalBytes) {
                            throw SecurityException("ZIP exceeds the total extraction limit")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                entryFile.setReadable(true)
                files.add(entryFile)
            } catch (error: Throwable) {
                entryFile.delete()
                throw error
            }
            zipInputStream.closeEntry()
        }
        return files
    }

    private fun validateArchive(file: File, limits: ExtractionLimits) {
        ZipFile(file).use { zip ->
            var entryCount = 0
            var totalBytes = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > limits.maxEntries) {
                    throw SecurityException("ZIP contains too many entries")
                }
                if (entry.isDirectory) continue
                val size = entry.size
                if (size > limits.maxEntryBytes) {
                    throw SecurityException("ZIP entry exceeds the extraction limit")
                }
                if (size > 0) {
                    totalBytes += size
                    if (totalBytes > limits.maxTotalBytes) {
                        throw SecurityException("ZIP exceeds the total extraction limit")
                    }
                }
                val compressedSize = entry.compressedSize
                if (
                    size >= limits.ratioCheckMinBytes &&
                    compressedSize > 0 &&
                    size / compressedSize > limits.maxCompressionRatio
                ) {
                    throw SecurityException("ZIP entry exceeds the compression ratio limit")
                }
            }
        }
    }

    /* 遍历目录获取所有文件名 */
    @Throws(SecurityException::class)
    fun getFilesName(
        inputStream: InputStream,
        filter: ((String) -> Boolean)? = null
    ): List<String> {
        return ZipInputStream(inputStream).use {
            getFilesName(it, filter)
        }
    }

    @Throws(SecurityException::class)
    private fun getFilesName(
        zipInputStream: ZipInputStream,
        filter: ((String) -> Boolean)? = null
    ): List<String> {
        val fileNames = mutableListOf<String>()
        var entry: ZipEntry?
        while (zipInputStream.nextEntry.also { entry = it } != null) {
            if (entry!!.isDirectory) {
                continue
            }
            val fileName = entry.name
            if (filter != null && filter.invoke(fileName))
                fileNames.add(fileName)
        }
        return fileNames
    }

    /**
     * Return the files' path in ZIP file.
     *
     * @param zipFilePath The path of ZIP file.
     * @return the files' path in ZIP file
     * @throws IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    fun getFilesPath(zipFilePath: String): List<String>? {
        return getFilesPath(getFileByPath(zipFilePath))
    }

    /**
     * Return the files' path in ZIP file.
     *
     * @param zipFile The ZIP file.
     * @return the files' path in ZIP file
     * @throws IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    fun getFilesPath(zipFile: File?): List<String>? {
        if (zipFile == null) return null
        val paths = ArrayList<String>()
        val zip = ZipFile(zipFile)
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entryName = (entries.nextElement() as ZipEntry).name
            if (entryName.contains("../")) {
                DebugLog.e(javaClass.name, "entryName: $entryName is dangerous!")
                paths.add(entryName)
            } else {
                paths.add(entryName)
            }
        }
        zip.close()
        return paths
    }

    /**
     * Return the files' comment in ZIP file.
     *
     * @param zipFilePath The path of ZIP file.
     * @return the files' comment in ZIP file
     * @throws IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    fun getComments(zipFilePath: String): List<String>? {
        return getComments(getFileByPath(zipFilePath))
    }

    /**
     * Return the files' comment in ZIP file.
     *
     * @param zipFile The ZIP file.
     * @return the files' comment in ZIP file
     * @throws IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    fun getComments(zipFile: File?): List<String>? {
        if (zipFile == null) return null
        val comments = ArrayList<String>()
        val zip = ZipFile(zipFile)
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement() as ZipEntry
            comments.add(entry.comment)
        }
        zip.close()
        return comments
    }

    private fun createOrExistsDir(file: File?): Boolean {
        return file != null && if (file.exists()) file.isDirectory else file.mkdirs()
    }

    private fun createOrExistsFile(file: File?): Boolean {
        if (file == null) return false
        if (file.exists()) return file.isFile
        if (!createOrExistsDir(file.parentFile)) return false
        return try {
            file.createNewFile()
        } catch (e: IOException) {
            e.printOnDebug()
            false
        }
    }

    private fun getFileByPath(filePath: String): File? {
        return if (isSpace(filePath)) null else File(filePath)
    }

    private fun isSpace(s: String?): Boolean {
        if (s == null) return true
        var i = 0
        val len = s.length
        while (i < len) {
            if (!Character.isWhitespace(s[i])) {
                return false
            }
            ++i
        }
        return true
    }
}
