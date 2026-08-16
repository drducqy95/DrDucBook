package io.legado.app.help.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveProfileTest {

    @Test
    fun metadataProfileDoesNotIncludeDownloadedContent() {
        assertFalse(BackupArchivePolicy.includesDownloadedContent(BackupContentProfile.METADATA))
    }

    @Test
    fun fullProfileIncludesDownloadedContent() {
        assertTrue(BackupArchivePolicy.includesDownloadedContent(BackupContentProfile.FULL))
        assertTrue("book_cache" in BackupArchivePolicy.fullDirectories)
        assertTrue("media_downloads" in BackupArchivePolicy.fullDirectories)
    }
}
