package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BookCacheFolderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun inaccessibleFolderIsQuarantinedAndRecreated() {
        val cacheRoot = temporaryFolder.newFolder("book_cache")
        val staleFolder = File(cacheRoot, "book-id").apply { mkdirs() }
        File(staleFolder, "chapter.nb").writeText("old cache")
        var accessCheckCount = 0

        val repaired = prepareWritableBookCacheFolder(
            cacheRoot = cacheRoot,
            folderName = "book-id",
            nowMillis = { 1_234L },
            isUsable = {
                accessCheckCount += 1
                accessCheckCount > 1
            },
        )

        assertEquals(File(cacheRoot, "book-id"), repaired)
        assertTrue(repaired.isDirectory)
        assertTrue(File(cacheRoot, "book-id.inaccessible-1234/chapter.nb").isFile)
    }

    @Test
    fun usableFolderIsKeptInPlace() {
        val cacheRoot = temporaryFolder.newFolder("book_cache")
        val existing = File(cacheRoot, "book-id").apply { mkdirs() }
        val marker = File(existing, "chapter.nb").apply { writeText("current cache") }

        val prepared = prepareWritableBookCacheFolder(
            cacheRoot = cacheRoot,
            folderName = "book-id",
        )

        assertEquals(existing.absoluteFile, prepared.absoluteFile)
        assertEquals("current cache", marker.readText())
    }
}
