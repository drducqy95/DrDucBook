package io.legado.app.help.drive

import io.legado.app.domain.model.DriveSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLinkResolverTest {

    @Test
    fun testGoogleDriveFolderResolution() {
        val url = "https://drive.google.com/drive/folders/1AbC-xYz_987654321"
        val result = DriveLinkResolver.resolve(url)
        assertTrue(result.isSuccess)

        val resolved = result.getOrThrow()
        assertEquals(DriveSourceType.GOOGLE_DRIVE_PUBLIC, resolved.type)
        assertEquals("1AbC-xYz_987654321", resolved.targetId)
        assertTrue(resolved.isFolder)
        assertEquals("https://drive.google.com/drive/folders/1AbC-xYz_987654321", resolved.normalizedUrl)

        val source = DriveLinkResolver.createManagedSource(resolved, "My Books")
        assertEquals("My Books", source.name)
        assertEquals("1AbC-xYz_987654321", source.rootFolderId)
        assertEquals(DriveSourceType.GOOGLE_DRIVE_PUBLIC, source.type)
    }

    @Test
    fun testGoogleDriveUserScopedFolderResolution() {
        val url = "https://drive.google.com/drive/u/1/folders/FolderId123"
        val result = DriveLinkResolver.resolve(url)
        assertTrue(result.isSuccess)

        val resolved = result.getOrThrow()
        assertEquals(DriveSourceType.GOOGLE_DRIVE_PUBLIC, resolved.type)
        assertEquals("FolderId123", resolved.targetId)
        assertTrue(resolved.isFolder)
    }

    @Test
    fun testGoogleDriveFileResolution() {
        val url = "https://drive.google.com/file/d/FileId999/view?usp=sharing"
        val result = DriveLinkResolver.resolve(url)
        assertTrue(result.isSuccess)

        val resolved = result.getOrThrow()
        assertEquals(DriveSourceType.GOOGLE_DRIVE_PUBLIC, resolved.type)
        assertEquals("FileId999", resolved.targetId)
        assertFalse(resolved.isFolder)
        assertEquals("https://drive.google.com/file/d/FileId999/view", resolved.normalizedUrl)

        val source = DriveLinkResolver.createManagedSource(resolved)
        assertEquals("Google Drive File (FileId999)", source.name)
        assertEquals("", source.rootFolderId)
    }

    @Test
    fun testOneDriveResolution() {
        val url = "https://1drv.ms/u/s!AmZ-12345678"
        val result = DriveLinkResolver.resolve(url)
        assertTrue(result.isSuccess)

        val resolved = result.getOrThrow()
        assertEquals(DriveSourceType.ONEDRIVE_PUBLIC, resolved.type)
        assertTrue(resolved.isFolder)
        assertEquals("OneDrive Cloud", resolved.suggestedName)
    }

    @Test
    fun testDropboxFileAndFolderResolution() {
        val fileUrl = "https://www.dropbox.com/scl/fi/xyz123/novel.epub?rlkey=456&dl=0"
        val fileResult = DriveLinkResolver.resolve(fileUrl)
        assertTrue(fileResult.isSuccess)
        val fileResolved = fileResult.getOrThrow()
        assertEquals(DriveSourceType.DROPBOX_PUBLIC, fileResolved.type)
        assertFalse(fileResolved.isFolder)

        val folderUrl = "https://www.dropbox.com/scl/fo/abc789/novels?rlkey=999&dl=0"
        val folderResult = DriveLinkResolver.resolve(folderUrl)
        assertTrue(folderResult.isSuccess)
        val folderResolved = folderResult.getOrThrow()
        assertEquals(DriveSourceType.DROPBOX_PUBLIC, folderResolved.type)
        assertTrue(folderResolved.isFolder)
    }

    @Test
    fun testGenericHttpResolution() {
        val dirUrl = "https://example.com/ebooks/vietnamese/"
        val dirResult = DriveLinkResolver.resolve(dirUrl)
        assertTrue(dirResult.isSuccess)
        val dirResolved = dirResult.getOrThrow()
        assertEquals(DriveSourceType.HTTP_INDEX, dirResolved.type)
        assertTrue(dirResolved.isFolder)

        val fileUrl = "https://example.com/ebooks/book.epub"
        val fileResult = DriveLinkResolver.resolve(fileUrl)
        assertTrue(fileResult.isSuccess)
        val fileResolved = fileResult.getOrThrow()
        assertEquals(DriveSourceType.HTTP_INDEX, fileResolved.type)
        assertFalse(fileResolved.isFolder)
    }

    @Test
    fun testInvalidUrlHandling() {
        assertTrue(DriveLinkResolver.resolve("").isFailure)
        assertTrue(DriveLinkResolver.resolve("   ").isFailure)
        assertTrue(DriveLinkResolver.resolve("ftp://files.example.com/books").isFailure)
    }
}
