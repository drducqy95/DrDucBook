package io.legado.app.data.repository

import io.legado.app.domain.model.AuthoringChapter
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AuthoringBackupFilesTest {

    private val root = Files.createTempDirectory("authoring-backup-test").toFile()

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun exportedAndRestoredSnapshotKeepsProjectAndAssetHash() {
        val source = File(root, "source")
        val backup = File(root, "backup")
        val restored = File(root, "restored")
        val store = AuthoringProjectFileStore(source)
        store.saveProject(project("one", "One"))
        store.saveProject(project("two", "Two"))
        val asset = store.importAsset("one", "Cover.PNG", "image bytes".toByteArray())
        File(source, "recovery/quarantine/corrupt.json").apply {
            parentFile?.mkdirs()
            writeText("{bad")
        }
        File(source, "projects/one/manifest.json.tmp").writeText("temporary")

        AuthoringBackupFiles.copyValidatedSnapshot(source, backup)
        AuthoringBackupFiles.restoreValidatedSnapshot(backup, restored)

        assertEquals(2, AuthoringProjectFileStore(restored).loadProjects().size)
        assertTrue(File(restored, source.toPath().relativize(asset.toPath()).toString()).isFile)
        assertEquals(AuthoringBackupFiles.treeHash(backup), AuthoringBackupFiles.treeHash(restored))
        assertFalse(File(backup, "recovery").exists())
        assertFalse(File(backup, "projects/one/manifest.json.tmp").exists())
    }

    @Test
    fun invalidAssetSnapshotIsRejectedBeforeDestinationChanges() {
        val source = File(root, "source")
        val target = File(root, "target")
        val store = AuthoringProjectFileStore(source)
        store.saveProject(project("one", "One"))
        val asset = store.importAsset("one", "Cover.PNG", "image bytes".toByteArray())
        asset.writeText("tampered")
        val targetStore = AuthoringProjectFileStore(target)
        targetStore.saveProject(project("existing", "Existing"))
        val beforeHash = AuthoringBackupFiles.treeHash(target)

        val result = runCatching {
            AuthoringBackupFiles.restoreValidatedSnapshot(source, target)
        }

        assertTrue(result.isFailure)
        assertEquals(beforeHash, AuthoringBackupFiles.treeHash(target))
        assertEquals("Existing", AuthoringProjectFileStore(target).loadProjects().single().title)
    }

    @Test
    fun copiedSnapshotRejectsRecoveryFolderIfInjectedLater() {
        val source = File(root, "source")
        val backup = File(root, "backup")
        val store = AuthoringProjectFileStore(source)
        store.saveProject(project("one", "One"))
        AuthoringBackupFiles.copyValidatedSnapshot(source, backup)
        File(backup, "recovery/history/one/old.json").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }

        val result = runCatching { AuthoringBackupFiles.validateSnapshot(backup) }

        assertTrue(result.isFailure)
    }

    @Test
    fun repositoryRecreateAndBackupRoundTripLargeAsset() = runBlocking {
        val source = File(root, "source")
        val backup = File(root, "backup")
        val restored = File(root, "restored")
        val repository = AuthoringProjectRepository(source)
        repository.saveProject(project("large", "Large"))

        val assetPath = repository.importImage(
            projectId = "large",
            displayName = "large-cover.PNG",
            bytes = largeAssetBytes(),
        )
        val reloaded = AuthoringProjectRepository(source).getProject("large")

        AuthoringBackupFiles.copyValidatedSnapshot(source, backup)
        AuthoringBackupFiles.restoreValidatedSnapshot(backup, restored)

        assertEquals("Large", reloaded?.title)
        assertEquals(AuthoringBackupFiles.treeHash(backup), AuthoringBackupFiles.treeHash(restored))
        val relativeAssetPath = source.toPath()
            .relativize(File(assetPath).toPath())
            .toString()
        assertEquals(
            File(assetPath).length(),
            File(restored, relativeAssetPath).length(),
        )
    }

    private fun project(id: String, title: String) = AuthoringProject(
        id = id,
        kind = AuthoringProjectKind.WRITING,
        title = title,
        chapters = listOf(
            AuthoringChapter(
                id = "chapter-$id",
                title = "Chapter",
                content = "Hello $title",
                createdAt = 1L,
                updatedAt = 1L,
            )
        ),
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun largeAssetBytes(): ByteArray =
        ByteArray(2 * 1024 * 1024) { index -> (index % 251).toByte() }
}
