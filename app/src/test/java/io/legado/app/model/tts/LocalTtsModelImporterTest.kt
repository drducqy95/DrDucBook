package io.legado.app.model.tts

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalTtsModelImporterTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("local-tts-import-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun extractsNestedValtecFilesInOnePass() {
        val extracted = extract(
            zipBytes(
                "voice/text_encoder.onnx" to byteArrayOf(1, 2),
                "voice/tts_config.json" to "{}".toByteArray(),
                "voice/LICENSE" to "license".toByteArray(),
            )
        )

        assertEquals(setOf("text_encoder.onnx", "tts_config.json", "LICENSE"), extracted)
        assertTrue(File(root, "text_encoder.onnx").isFile)
    }

    @Test
    fun recognizesSinglePiperVoicePairWithoutTreatingItAsValtec() {
        val extracted = extract(
            zipBytes(
                "banmai.onnx" to byteArrayOf(1),
                "banmai.onnx.json" to "{}".toByteArray(),
            )
        )

        assertEquals(setOf("banmai.onnx", "banmai.onnx.json"), extracted)
    }

    @Test
    fun preparesPiperPairIntoRuntimeFilesAndMetadata() {
        File(root, "banmai.onnx").writeBytes(byteArrayOf())
        File(root, "banmai.onnx.json").writeText(
            """{
                "audio":{"sample_rate":22050},
                "espeak":{"voice":"vi"},
                "phoneme_type":"espeak",
                "num_speakers":1,
                "inference":{"noise_scale":0.667,"length_scale":1.0,"noise_w":0.8},
                "phoneme_id_map":{"_":[0],"a":[1]}
            }""".trimIndent()
        )

        val descriptor = LocalTtsModelImporter.preparePiperModel(
            root,
            setOf("banmai.onnx", "banmai.onnx.json"),
        )

        assertEquals("vi", descriptor.language)
        assertEquals(22050, descriptor.sampleRate)
        assertTrue(File(root, LocalTtsModelRegistry.PIPER_MODEL_FILE).isFile)
        assertTrue(File(root, LocalTtsModelRegistry.PIPER_CONFIG_FILE).isFile)
        assertTrue(File(root, LocalTtsModelRegistry.PIPER_TOKENS_FILE).readText().contains("a 1"))
        assertEquals(
            "piper",
            OnnxMetadataEditor.read(File(root, LocalTtsModelRegistry.PIPER_MODEL_FILE))["comment"],
        )
    }

    @Test
    fun rejectsMismatchedPiperPair() {
        File(root, "banmai.onnx").writeBytes(byteArrayOf())
        File(root, "other.onnx.json").writeText("{}")

        val error = assertThrows(IOException::class.java) {
            LocalTtsModelImporter.preparePiperModel(
                root,
                setOf("banmai.onnx", "other.onnx.json"),
            )
        }

        assertTrue(error.message.orEmpty().contains("không khớp"))
    }

    @Test
    fun rejectsPathTraversal() {
        val error = assertThrows(IOException::class.java) {
            extract(zipBytes("../decoder.onnx" to byteArrayOf(1)))
        }

        assertTrue(error.message.orEmpty().contains("không an toàn"))
    }

    @Test
    fun rejectsFilesOutsideWhitelist() {
        val error = assertThrows(IOException::class.java) {
            extract(zipBytes("voice/readme.exe" to byteArrayOf(1)))
        }

        assertTrue(error.message.orEmpty().contains("whitelist"))
    }

    @Test
    fun rejectsDuplicateBasename() {
        val error = assertThrows(IOException::class.java) {
            extract(
                zipBytes(
                    "old/decoder.onnx" to byteArrayOf(1),
                    "new/decoder.onnx" to byteArrayOf(2),
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("trùng"))
    }

    @Test
    fun rejectsMoreThanTwentyEntries() {
        val entries = Array(LocalTtsModelImporter.MAX_ENTRIES + 1) { index ->
            "folder-$index/" to byteArrayOf()
        }

        val error = assertThrows(IOException::class.java) {
            extract(zipBytes(*entries))
        }

        assertTrue(error.message.orEmpty().contains("quá nhiều"))
    }

    @Test
    fun rejectsNonZipInput() {
        val error = assertThrows(IOException::class.java) {
            extract(byteArrayOf(1, 2, 3, 4))
        }

        assertTrue(error.message.orEmpty().contains("ZIP"))
    }

    @Test
    fun reportsMonotonicExtractedBytes() = runBlocking {
        val progress = mutableListOf<Long>()

        LocalTtsModelImporter.extractRecognizedFiles(
            input = ByteArrayInputStream(
                zipBytes(
                    "voice.onnx" to ByteArray(32_000) { 1 },
                    "voice.onnx.json" to ByteArray(8_000) { 2 },
                )
            ),
            staging = root,
            onBytesExtracted = progress::add,
        )

        assertTrue(progress.isNotEmpty())
        assertTrue(progress.zipWithNext().all { (before, after) -> after >= before })
        assertEquals(40_000L, progress.last())
    }

    @Test
    fun rejectsInvalidRuntimeProbeSamples() {
        assertThrows(IOException::class.java) {
            LocalTtsModelImporter.validateProbeSamples(floatArrayOf())
        }
        assertThrows(IOException::class.java) {
            LocalTtsModelImporter.validateProbeSamples(floatArrayOf(Float.NaN))
        }
        LocalTtsModelImporter.validateProbeSamples(floatArrayOf(0f, 0.25f, -0.25f))
    }

    @Test
    fun cleansAllStagingArtifactsAcrossOneHundredFaultCycles() {
        repeat(100) { cycle ->
            File(root, "import_$cycle").mkdirs()
            File(root, "model_${cycle}_installing").mkdirs()
            File(root, "model_${cycle}_backup").mkdirs()
            LocalTtsModelImporter.cleanOrphanedStaging(root)
            assertTrue(root.listFiles().orEmpty().isEmpty())
        }
    }

    private fun extract(bytes: ByteArray): Set<String> = runBlocking {
        LocalTtsModelImporter.extractRecognizedFiles(
            input = ByteArrayInputStream(bytes),
            staging = root,
        )
    }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                if (!name.endsWith('/')) zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
