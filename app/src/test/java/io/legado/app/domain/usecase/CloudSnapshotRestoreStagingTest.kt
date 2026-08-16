package io.legado.app.domain.usecase

import io.legado.app.domain.model.CloudSnapshotDataset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CloudSnapshotRestoreStagingTest {

    @Test
    fun stagesVerifiedArchiveWithoutCommittingToRuntimeStores() {
        val built = CloudSnapshotArchive.build(
            snapshotId = "snapshot-1",
            revision = "rev-1",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            payloads = listOf(
                payload(CloudSnapshotDataset.SETTINGS, """{"fontScale":1.1}"""),
                payload(CloudSnapshotDataset.APPEARANCE, """{"activeProfileId":"ink"}"""),
            ),
        )
        val root = tempDir()

        val staged = CloudSnapshotRestoreStaging.stageArchive(built.bytes, root)

        assertEquals(built.manifest, staged.manifest)
        assertTrue(staged.directory.isDirectory)
        assertEquals(2, staged.entries.size)
        staged.entries.forEach { entry ->
            assertTrue(entry.file.canonicalFile.toPath().startsWith(staged.directory.canonicalFile.toPath()))
            assertEquals(entry.sizeBytes, entry.file.length())
            assertTrue(entry.sha256.matches(Regex("^[a-f0-9]{64}$")))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesFileAsStagingRoot() {
        val built = CloudSnapshotArchive.build(
            snapshotId = "snapshot-1",
            revision = "rev-1",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            payloads = listOf(payload(CloudSnapshotDataset.SETTINGS, "{}")),
        )
        val rootFile = File(tempDir(), "not-a-dir").apply { writeText("x") }

        CloudSnapshotRestoreStaging.stageArchive(built.bytes, rootFile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesCorruptArchiveBeforeStaging() {
        val built = CloudSnapshotArchive.build(
            snapshotId = "snapshot-1",
            revision = "rev-1",
            deviceId = "device-1",
            createdAtEpochMillis = 1L,
            payloads = listOf(payload(CloudSnapshotDataset.SETTINGS, "{}")),
        )
        val corrupt = tamperPayload(
            built = built,
            path = CloudSnapshotArchive.objectPath(CloudSnapshotDataset.SETTINGS),
            bytes = """{"changed":true}""".toByteArray(),
        )

        CloudSnapshotRestoreStaging.stageArchive(corrupt, tempDir())
    }

    private fun payload(
        dataset: CloudSnapshotDataset,
        text: String,
    ) = CloudSnapshotPayload(
        dataset = dataset,
        bytes = text.toByteArray(),
        recordCount = 1,
    )

    private fun tempDir(): File =
        Files.createTempDirectory("drducbook-snapshot-staging-test").toFile()

    private fun tamperPayload(
        built: BuiltCloudSnapshotArchive,
        path: String,
        bytes: ByteArray,
    ): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(built.bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        entries[path] = bytes
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, entryBytes) ->
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                zip.write(entryBytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
