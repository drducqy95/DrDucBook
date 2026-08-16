package io.legado.app.model.translation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HachimiOnnxModelImporterTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("hachimi-import-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun extractsRecognizedFilesFromNestedPackage() {
        val zip = zipBytes(
            "nested/${HachimiOnnxModelRegistry.ENCODER_FILE}" to byteArrayOf(1, 2, 3),
            "nested/ignored.tmp" to byteArrayOf(9),
        )
        val extracted = extract(zip)

        assertEquals(setOf(HachimiOnnxModelRegistry.ENCODER_FILE), extracted)
        assertTrue(File(root, HachimiOnnxModelRegistry.ENCODER_FILE).isFile)
    }

    @Test
    fun extractsOnnxExternalDataCompanionFiles() {
        val zip = zipBytes(
            "model/${HachimiOnnxModelRegistry.ENCODER_FILE}" to byteArrayOf(1, 2, 3),
            "model/${HachimiOnnxModelRegistry.ENCODER_FILE}_data" to byteArrayOf(4, 5, 6),
            "model/weights.bin" to byteArrayOf(7, 8, 9),
        )

        val extracted = extract(zip)

        assertEquals(
            setOf(
                HachimiOnnxModelRegistry.ENCODER_FILE,
                "${HachimiOnnxModelRegistry.ENCODER_FILE}_data",
                "weights.bin",
            ),
            extracted,
        )
        assertTrue(File(root, "${HachimiOnnxModelRegistry.ENCODER_FILE}_data").isFile)
        assertTrue(File(root, "weights.bin").isFile)
    }

    @Test
    fun rejectsUnsafeEntryPathAsImportError() {
        val zip = zipBytes("../${HachimiOnnxModelRegistry.ENCODER_FILE}" to byteArrayOf(1))

        val error = assertThrows(IOException::class.java) {
            extract(zip)
        }

        assertTrue(error.message.orEmpty().contains("Unsafe path"))
    }

    @Test
    fun rejectsEmptyRequiredModelFile() {
        val zip = zipBytes(HachimiOnnxModelRegistry.ENCODER_FILE to byteArrayOf())

        val error = assertThrows(IOException::class.java) {
            extract(zip)
        }

        assertTrue(error.message.orEmpty().contains("empty"))
    }

    @Test
    fun rejectsNonZipPackageWithImportError() {
        val error = assertThrows(IOException::class.java) {
            extract(byteArrayOf(1, 2, 3, 4))
        }

        assertTrue(error.message.orEmpty().contains("valid ZIP"))
    }

    @Test
    fun rejectsZipWithoutRecognizedModelFiles() {
        val zip = zipBytes("ignored.tmp" to byteArrayOf(1, 2, 3))

        val error = assertThrows(IOException::class.java) {
            extract(zip)
        }

        assertTrue(error.message.orEmpty().contains("recognized"))
    }

    @Test
    fun rejectsDuplicateRequiredModelBasename() {
        val zip = zipBytes(
            "old/${HachimiOnnxModelRegistry.ENCODER_FILE}" to byteArrayOf(1),
            "new/${HachimiOnnxModelRegistry.ENCODER_FILE}" to byteArrayOf(2),
        )

        val error = assertThrows(IOException::class.java) {
            extract(zip)
        }

        assertTrue(error.message.orEmpty().contains("Duplicate required NMT model file"))
    }

    @Test
    fun installAtomicallyReplacesExistingModelAndConsumesStagingDirectory() {
        val existing = File(root, HachimiOnnxModelRegistry.MODEL_ID)
        writeCompleteModel(existing, marker = 1)
        val staging = File(root, "incoming")
        writeCompleteModel(staging, marker = 2)

        HachimiOnnxModelImporter.installAtomically(root, staging)

        val installed = File(root, HachimiOnnxModelRegistry.MODEL_ID)
        assertTrue(installed.isDirectory)
        assertEquals(2, File(installed, HachimiOnnxModelRegistry.ENCODER_FILE).readBytes().first().toInt())
        assertEquals(false, staging.exists())
        assertEquals(false, File(root, "${HachimiOnnxModelRegistry.MODEL_ID}_backup").exists())
        assertEquals(false, File(root, "${HachimiOnnxModelRegistry.MODEL_ID}_installing").exists())
    }

    @Test
    fun installAtomicallyKeepsExistingModelWhenIncomingPackageIsIncomplete() {
        val existing = File(root, HachimiOnnxModelRegistry.MODEL_ID)
        writeCompleteModel(existing, marker = 1)
        val staging = File(root, "incoming").apply { mkdirs() }
        File(staging, HachimiOnnxModelRegistry.ENCODER_FILE).writeBytes(byteArrayOf(2))

        val error = assertThrows(IOException::class.java) {
            HachimiOnnxModelImporter.installAtomically(root, staging)
        }

        val installed = File(root, HachimiOnnxModelRegistry.MODEL_ID)
        assertTrue(error.message.orEmpty().contains("missing"))
        assertTrue(installed.isDirectory)
        assertEquals(1, File(installed, HachimiOnnxModelRegistry.ENCODER_FILE).readBytes().first().toInt())
    }

    @Test
    fun activatingInstalledModelAdvancesRuntimeGenerationOnlyAfterSuccess() {
        val before = HachimiOnnxRuntimeCoordinator.currentGeneration()
        val staging = File(root, "incoming").also { writeCompleteModel(it, marker = 2) }

        HachimiOnnxModelImporter.installAndActivateAtomically(root, staging)

        assertTrue(HachimiOnnxRuntimeCoordinator.currentGeneration() > before)
        val afterSuccess = HachimiOnnxRuntimeCoordinator.currentGeneration()
        val incomplete = File(root, "incomplete").apply { mkdirs() }
        File(incomplete, HachimiOnnxModelRegistry.ENCODER_FILE).writeBytes(byteArrayOf(3))
        assertThrows(IOException::class.java) {
            HachimiOnnxModelImporter.installAndActivateAtomically(root, incomplete)
        }
        assertEquals(afterSuccess, HachimiOnnxRuntimeCoordinator.currentGeneration())
    }

    @Test
    fun cancellationDuringExtractionRemovesPartialFile() {
        val randomBytes = ByteArray(1024 * 1024).also {
            java.util.Random(7L).nextBytes(it)
        }
        val zip = zipBytes(HachimiOnnxModelRegistry.ENCODER_FILE to randomBytes)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                HachimiOnnxModelImporter.extractRecognizedFiles(
                    input = ByteArrayInputStream(zip),
                    staging = root,
                    digest = MessageDigest.getInstance("SHA-256"),
                    sourceBytes = zip.size.toLong(),
                    onProgress = { throw CancellationException("cancel test") },
                )
            }
        }

        assertEquals(false, File(root, HachimiOnnxModelRegistry.ENCODER_FILE).exists())
        assertEquals(false, File(root, "${HachimiOnnxModelRegistry.ENCODER_FILE}.importing").exists())
    }

    @Test
    fun sizeLimitAcceptsRealisticOnnxBundlesButStillRejectsHugePackages() {
        val previousEntryLimit = 200L * 1024L * 1024L
        val previousTotalLimit = 400L * 1024L * 1024L
        val twoGigabytes = 2L * 1024L * 1024L * 1024L
        val threeGigabytes = 3L * 1024L * 1024L * 1024L

        assertEquals(
            false,
            HachimiOnnxModelImporter.isPackageSizeExceeded(
                entryBytes = previousEntryLimit + 1,
                totalBytes = previousTotalLimit + 1,
            ),
        )
        assertEquals(
            true,
            HachimiOnnxModelImporter.isPackageSizeExceeded(
                entryBytes = twoGigabytes,
                totalBytes = threeGigabytes,
            ),
        )
    }

    @Test
    fun storageGuardLeavesHeadroomForFallbackCopy() {
        val modelBytes = 900L * 1024L * 1024L

        assertEquals(
            true,
            HachimiOnnxModelImporter.hasEnoughUsableSpaceForCopy(
                usableSpace = modelBytes + 128L * 1024L * 1024L,
                bytesToCopy = modelBytes,
            ),
        )
        assertEquals(
            false,
            HachimiOnnxModelImporter.hasEnoughUsableSpaceForCopy(
                usableSpace = modelBytes + 128L * 1024L * 1024L - 1,
                bytesToCopy = modelBytes,
            ),
        )
    }

    private fun extract(zip: ByteArray): Set<String> =
        runBlocking {
            HachimiOnnxModelImporter.extractRecognizedFiles(
                input = ByteArrayInputStream(zip),
                staging = root,
                digest = MessageDigest.getInstance("SHA-256"),
            )
        }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun writeCompleteModel(directory: File, marker: Byte) {
        directory.mkdirs()
        HachimiOnnxModelRegistry.REQUIRED_FILES.forEach { fileName ->
            File(directory, fileName).writeBytes(byteArrayOf(marker))
        }
    }
}
