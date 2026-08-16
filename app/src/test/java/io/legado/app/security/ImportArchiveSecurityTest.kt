package io.legado.app.security

import io.legado.app.utils.compress.ZipUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportArchiveSecurityTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun pathTraversalIsRejected() {
        val zip = zipFile("../outside.txt" to "blocked".toByteArray())
        val destination = temporaryFolder.newFolder("extract")

        assertThrows(SecurityException::class.java) {
            ZipUtils.unZipToPath(zip, destination)
        }
        assertFalse(File(destination.parentFile, "outside.txt").exists())
    }

    @Test
    fun siblingPrefixTraversalIsRejected() {
        val zip = zipFile("../extract-copy/outside.txt" to "blocked".toByteArray())
        val destination = temporaryFolder.newFolder("extract-prefix")

        assertThrows(SecurityException::class.java) {
            ZipUtils.unZipToPath(zip, destination)
        }
        assertFalse(File(destination.parentFile, "extract-copy/outside.txt").exists())
    }

    @Test
    fun entryCountAndSingleFileLimitsAreEnforced() {
        val twoEntries = zipFile(
            "one.txt" to byteArrayOf(1),
            "two.txt" to byteArrayOf(2),
        )
        assertThrows(SecurityException::class.java) {
            ZipUtils.unZipToPath(
                twoEntries,
                temporaryFolder.newFolder("count-limit"),
                limits = ZipUtils.ExtractionLimits(maxEntries = 1),
            )
        }

        val oversized = zipFile("large.bin" to ByteArray(64))
        assertThrows(SecurityException::class.java) {
            ZipUtils.unZipToPath(
                oversized,
                temporaryFolder.newFolder("size-limit"),
                limits = ZipUtils.ExtractionLimits(maxEntryBytes = 32),
            )
        }
    }

    @Test
    fun excessiveCompressionRatioIsRejectedBeforeExtraction() {
        val zip = zipFile("bomb.bin" to ByteArray(256 * 1024))

        assertThrows(SecurityException::class.java) {
            ZipUtils.unZipToPath(
                zip,
                temporaryFolder.newFolder("ratio-limit"),
                limits = ZipUtils.ExtractionLimits(
                    maxCompressionRatio = 10,
                    ratioCheckMinBytes = 1,
                ),
            )
        }
    }

    private fun zipFile(vararg entries: Pair<String, ByteArray>): File {
        val file = temporaryFolder.newFile("archive-${System.nanoTime()}.zip")
        ZipOutputStream(FileOutputStream(file)).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }
}
