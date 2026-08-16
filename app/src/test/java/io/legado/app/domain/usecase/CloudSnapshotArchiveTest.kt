package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotDataset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CloudSnapshotArchiveTest {

    @Test
    fun buildAndReadRoundTripVerifiesPayloads() {
        val built = CloudSnapshotArchive.build(
            snapshotId = "snapshot-1",
            revision = "rev-1",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            payloads = listOf(
                payload(CloudSnapshotDataset.BOOK_SOURCES, """[{"name":"source"}]"""),
                payload(CloudSnapshotDataset.SETTINGS, """{"theme":"ink"}"""),
            ),
        )

        assertTrue(built.sha256.matches(Regex("^[a-f0-9]{64}$")))
        assertEquals(built.bytes.size.toLong(), built.sizeBytes)

        val restored = CloudSnapshotArchive.read(built.bytes)
        assertEquals(built.manifest, restored.manifest)
        assertEquals(2, restored.payloads.size)
        assertArrayEquals(
            """[{"name":"source"}]""".toByteArray(),
            restored.payloads.first { it.dataset == CloudSnapshotDataset.BOOK_SOURCES }.bytes,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildRejectsExcludedRuntimeDataset() {
        CloudSnapshotArchive.build(
            snapshotId = "snapshot-1",
            revision = "rev-1",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            payloads = listOf(payload(CloudSnapshotDataset.COOKIES, "{}")),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun readRejectsPathTraversalEntry() {
        CloudSnapshotArchive.read(
            zipBytes(
                "../manifest.json" to "{}".toByteArray(),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun readRejectsUndeclaredFiles() {
        val built = CloudSnapshotArchive.build(
            snapshotId = "snapshot-1",
            revision = "rev-1",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            payloads = listOf(payload(CloudSnapshotDataset.SETTINGS, "{}")),
        )
        val tampered = zipBytesFromBuiltWithExtraFile(built, "entries/cookies.json", "{}")

        CloudSnapshotArchive.read(tampered)
    }

    @Test(expected = IllegalArgumentException::class)
    fun readRejectsPayloadChecksumMismatch() {
        val built = CloudSnapshotArchive.build(
            snapshotId = "snapshot-1",
            revision = "rev-1",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            payloads = listOf(payload(CloudSnapshotDataset.SETTINGS, "{}")),
        )
        val tampered = zipBytes(
            CloudSnapshotArchive.MANIFEST_PATH to builtManifestBytes(built),
            CloudSnapshotArchive.objectPath(CloudSnapshotDataset.SETTINGS) to """{"changed":true}""".toByteArray(),
        )

        CloudSnapshotArchive.read(tampered)
    }

    private fun payload(
        dataset: CloudSnapshotDataset,
        text: String,
    ) = CloudSnapshotPayload(
        dataset = dataset,
        bytes = text.toByteArray(),
        recordCount = 1,
    )

    private fun builtManifestBytes(built: BuiltCloudSnapshotArchive): ByteArray {
        val files = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(built.bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) files[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return requireNotNull(files[CloudSnapshotArchive.MANIFEST_PATH])
    }

    private fun zipBytesFromBuiltWithExtraFile(
        built: BuiltCloudSnapshotArchive,
        extraPath: String,
        extraText: String,
    ): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(built.bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        entries[extraPath] = extraText.toByteArray()
        return zipBytes(*entries.map { it.key to it.value }.toTypedArray())
    }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
