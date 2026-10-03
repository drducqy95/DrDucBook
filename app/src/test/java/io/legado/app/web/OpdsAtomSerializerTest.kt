package io.legado.app.web

import io.legado.app.domain.model.OpdsCatalog
import io.legado.app.domain.model.OpdsEntry
import io.legado.app.domain.model.OpdsLink
import io.legado.app.domain.model.OpdsMimeTypes
import io.legado.app.help.drive.OpdsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsAtomSerializerTest {

    @Test
    fun escapeXmlHandlesSpecialCharacters() {
        val raw = "Tom & Jerry <Special> \"Edition\" '2026'"
        val escaped = OpdsAtomSerializer.escapeXml(raw)
        assertEquals("Tom &amp; Jerry &lt;Special&gt; &quot;Edition&quot; &apos;2026&apos;", escaped)
    }

    @Test
    fun serializeAndParseRoundtrip() {
        val originalCatalog = OpdsCatalog(
            id = "urn:drducbook:test:root",
            title = "Test Library & Books",
            updated = "2026-10-03T12:00:00Z",
            selfUrl = "http://127.0.0.1:8765/opds/catalog",
            links = listOf(
                OpdsLink(
                    href = "http://127.0.0.1:8765/opds/catalog",
                    rel = OpdsMimeTypes.REL_SELF,
                    type = OpdsMimeTypes.NAVIGATION,
                )
            ),
            entries = listOf(
                OpdsEntry(
                    id = "urn:drducbook:test:folder:ln",
                    title = "Light Novels",
                    links = listOf(
                        OpdsLink(
                            href = "http://127.0.0.1:8765/opds/catalog/Light%20Novels",
                            rel = OpdsMimeTypes.REL_SUBSECTION,
                            type = OpdsMimeTypes.NAVIGATION,
                            title = "Light Novels",
                        )
                    )
                ),
                OpdsEntry(
                    id = "urn:drducbook:test:book:sao1",
                    title = "Sword Art Online Vol 1",
                    author = "Kawahara Reki",
                    summary = "Aincrad arc introductory volume",
                    coverUrl = "http://127.0.0.1:8765/opds/cover/SAO1.epub",
                    links = listOf(
                        OpdsLink(
                            href = "http://127.0.0.1:8765/opds/download/SAO1.epub",
                            rel = OpdsMimeTypes.REL_ACQUISITION,
                            type = OpdsMimeTypes.MIME_EPUB,
                            length = 2048000L,
                        )
                    )
                )
            )
        )

        val xml = OpdsAtomSerializer.serialize(originalCatalog)
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xml.contains("<feed xmlns=\"http://www.w3.org/2005/Atom\""))
        assertTrue(xml.contains("Test Library &amp; Books"))

        val parsed = OpdsParser.parse(xml, "http://127.0.0.1:8765/opds/catalog")
        assertEquals("urn:drducbook:test:root", parsed.id)
        assertEquals("Test Library & Books", parsed.title)
        assertEquals(2, parsed.entries.size)

        // Entry 1: Navigation folder
        val folderEntry = parsed.entries[0]
        assertEquals("Light Novels", folderEntry.title)
        assertTrue(folderEntry.isNavigation)
        assertEquals("http://127.0.0.1:8765/opds/catalog/Light%20Novels", folderEntry.navigationUrl)

        // Entry 2: Book acquisition
        val bookEntry = parsed.entries[1]
        assertEquals("Sword Art Online Vol 1", bookEntry.title)
        assertEquals("Kawahara Reki", bookEntry.author)
        assertEquals("Aincrad arc introductory volume", bookEntry.summary)
        assertTrue(bookEntry.isAcquisition)
        assertEquals("http://127.0.0.1:8765/opds/download/SAO1.epub", bookEntry.acquisitionUrl)
        assertEquals("EPUB", bookEntry.acquisitionFormat)
        assertEquals("http://127.0.0.1:8765/opds/cover/SAO1.epub", bookEntry.coverUrl)
    }

    @Test
    fun mapDirectoryToCatalogGeneratesCorrectStructure() {
        val files = listOf(
            DriveFileInfo(
                name = "Fantasy",
                path = "/Fantasy",
                isDir = true,
            ),
            DriveFileInfo(
                name = "Overlord_Vol01.epub",
                path = "/Overlord_Vol01.epub",
                isDir = false,
                size = 5000000L,
                author = "Kugane Maruyama",
                intro = "The Undead King",
                thumbnailUrl = "https://example.com/cover.jpg"
            ),
            DriveFileInfo(
                name = "notes.txt",
                path = "/notes.txt",
                isDir = false,
                size = 1000L,
            ),
            DriveFileInfo(
                name = "ignored.exe",
                path = "/ignored.exe",
                isDir = false,
            )
        )

        val catalog = OpdsDriveMapper.mapDirectoryToCatalog(
            sourceId = "test_src",
            sourceName = "My Google Drive",
            currentPath = "/",
            files = files,
            baseServerUrl = "http://127.0.0.1:8765"
        )

        assertEquals("My Google Drive", catalog.title)
        // 1 folder + 2 books (epub + txt), ignored.exe is excluded
        assertEquals(3, catalog.entries.size)

        val folder = catalog.entries.first { it.isNavigation }
        assertEquals("Fantasy", folder.title)
        assertEquals("http://127.0.0.1:8765/opds/catalog/Fantasy", folder.navigationUrl)

        val epub = catalog.entries.first { it.title == "Overlord_Vol01" }
        assertEquals("Kugane Maruyama", epub.author)
        assertEquals("The Undead King", epub.summary)
        assertEquals("https://example.com/cover.jpg", epub.coverUrl)
        assertEquals("http://127.0.0.1:8765/opds/download/Overlord_Vol01.epub", epub.acquisitionUrl)
    }
}
