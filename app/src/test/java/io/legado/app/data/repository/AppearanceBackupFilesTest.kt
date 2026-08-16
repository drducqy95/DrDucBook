package io.legado.app.data.repository

import io.legado.app.domain.model.AppearancePresets
import io.legado.app.domain.model.AppearanceSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppearanceBackupFilesTest {
    private val root = Files.createTempDirectory("appearance-backup-test").toFile()

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun exportedAndRestoredSnapshotHasIdenticalHash() {
        val source = File(root, "source")
        AppearanceFileStore(source).write(
            AppearanceSnapshot(
                activeProfileId = AppearancePresets.COPPER_CYAN_ID,
                profiles = AppearancePresets.all,
            )
        )
        val assets = File(source, AppearanceRepository.ASSET_FOLDER).apply { mkdirs() }
        File(assets, "a".repeat(64) + ".png").writeBytes(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        )
        val backup = File(root, "backup")
        val restored = File(root, "restored")

        AppearanceBackupFiles.copyValidatedSnapshot(source, backup)
        AppearanceBackupFiles.copyValidatedSnapshot(backup, restored)

        assertEquals(
            AppearanceBackupFiles.treeHash(source),
            AppearanceBackupFiles.treeHash(restored),
        )
    }

    @Test
    fun exportWritesValidatedFallbackInsteadOfCopyingCorruptPrimary() {
        val source = File(root, "source")
        val sourceStore = AppearanceFileStore(source)
        val first = AppearanceSnapshot(
            activeProfileId = AppearancePresets.COPPER_CYAN_ID,
            profiles = AppearancePresets.all,
        )
        sourceStore.write(first)
        sourceStore.write(first.copy(activeProfileId = AppearancePresets.FOREST_CORAL_ID))
        sourceStore.profileFile.writeText("{broken")
        val backup = File(root, "backup")

        AppearanceBackupFiles.copyValidatedSnapshot(source, backup)

        val exported = AppearanceFileStore(backup).readOrNull()
        assertNotNull(exported)
        assertEquals(AppearancePresets.COPPER_CYAN_ID, exported?.activeProfileId)
    }

    @Test
    fun exportFromMemoryDoesNotDependOnCorruptLocalSnapshot() {
        val source = File(root, "source")
        val sourceStore = AppearanceFileStore(source)
        val snapshot = AppearanceSnapshot(
            activeProfileId = AppearancePresets.INK_AMBER_ID,
            profiles = AppearancePresets.all,
        )
        sourceStore.write(snapshot)
        sourceStore.write(snapshot)
        sourceStore.profileFile.writeText("{broken")
        File(source, "profiles.json.bak").writeText("{also-broken")
        val backup = File(root, "backup")

        AppearanceBackupFiles.exportValidatedSnapshot(
            snapshot = snapshot,
            sourceAssets = File(source, AppearanceRepository.ASSET_FOLDER),
            destination = backup,
        )

        assertEquals(
            AppearancePresets.INK_AMBER_ID,
            AppearanceFileStore(backup).readOrNull()?.activeProfileId,
        )
    }
}
