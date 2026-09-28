package io.legado.app.help.drive

import io.legado.app.constant.AppPattern
import io.legado.app.help.drive.extractor.CompanionCoverResolver
import io.legado.app.help.drive.extractor.TextRemoteExtractor
import io.legado.app.ui.drive.components.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveRemoteMetadataTest {

    @Test
    fun bookFileRegex_matchesAllTenRequestedFormats() {
        val formats = listOf(
            "novel.epub",
            "document.pdf",
            "story.mobi",
            "book.azw3",
            "book.azw",
            "archive.prc",
            "report.docx",
            "old_doc.doc",
            "story.txt",
            "notes.md",
            "page.html",
            "page.htm"
        )

        for (fileName in formats) {
            assertTrue(
                "Regex should match $fileName",
                fileName.matches(AppPattern.bookFileRegex)
            )
        }
    }

    @Test
    fun companionCoverResolver_matchesExactAndStandardNames() {
        val folderFiles = listOf(
            "WarAndPeace.epub",
            "WarAndPeace.jpg",
            "WarAndPeace.png",
            "cover.jpg",
            "folder.png",
            "poster.jpg",
            "unrelated.jpg"
        )

        // Exact match should find WarAndPeace.jpg
        val resolved = CompanionCoverResolver.resolveCompanionCover("WarAndPeace.epub", folderFiles)
        assertEquals("WarAndPeace.jpg", resolved)

        // Without exact name, should fallback to cover.jpg
        val fallbackResolved = CompanionCoverResolver.resolveCompanionCover(
            "AnnaKarenina.epub",
            listOf("AnnaKarenina.epub", "cover.jpg", "other.txt")
        )
        assertEquals("cover.jpg", fallbackResolved)
    }

    @Test
    fun textRemoteExtractor_parsesHtmlMetadata() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Tam Quốc Diễn Nghĩa</title>
                <meta name="author" content="La Quán Trung">
                <meta name="description" content="Một trong Tứ đại danh tác của văn học cổ điển Trung Hoa">
            </head>
            <body>Content</body>
            </html>
        """.trimIndent()

        val metadata = TextRemoteExtractor.extractHtml(html, "TamQuoc.html")
        assertEquals("Tam Quốc Diễn Nghĩa", metadata.title)
        assertEquals("La Quán Trung", metadata.author)
        assertEquals("Một trong Tứ đại danh tác của văn học cổ điển Trung Hoa", metadata.intro)
    }

    @Test
    fun textRemoteExtractor_parsesMarkdownFrontMatter() {
        val md = """
            ---
            title: Đắc Nhân Tâm
            author: Dale Carnegie
            intro: Nghệ thuật thu phục lòng người
            ---
            # Nội dung chính
            Chương 1...
        """.trimIndent()

        val metadata = TextRemoteExtractor.extractMarkdown(md, "DacNhanTam.md")
        assertEquals("Đắc Nhân Tâm", metadata.title)
        assertEquals("Dale Carnegie", metadata.author)
        assertEquals("Nghệ thuật thu phục lòng người", metadata.intro)
    }

    @Test
    fun formatFileSize_calculatesCorrectUnits() {
        assertEquals("", formatFileSize(0L))
        assertEquals("500 KB", formatFileSize(512000L))
        assertEquals("10.0 MB", formatFileSize(10485760L))
        assertEquals("1.5 MB", formatFileSize(1572864L))
    }

    @Test
    fun timestampParser_handlesAllIsoAndPostgresFormats() {
        val utcMillis = io.legado.app.data.repository.parseIsoTimestampToEpochMillis("2026-08-03T09:00:00+00:00")
        val zMillis = io.legado.app.data.repository.parseIsoTimestampToEpochMillis("2026-08-03T09:00:00Z")
        val vnMillis = io.legado.app.data.repository.parseIsoTimestampToEpochMillis("2026-08-03T16:00:00+07:00")
        val pgSpaceMillis = io.legado.app.data.repository.parseIsoTimestampToEpochMillis("2026-08-03 09:00:00+00")

        assertEquals(zMillis, utcMillis)
        assertEquals(zMillis, vnMillis)
        assertEquals(zMillis, pgSpaceMillis)
        assertEquals(null, io.legado.app.data.repository.parseIsoTimestampToEpochMillis(null))
        assertEquals(null, io.legado.app.data.repository.parseIsoTimestampToEpochMillis(""))
        assertEquals(null, io.legado.app.data.repository.parseIsoTimestampToEpochMillis("not-a-date"))
    }

    @Test
    fun epubOpfParsing_handlesCoverMetaAndNamespacesWithoutCrashing() {
        val opfXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>[huyền huyễn] Chứng đạo thành tựu đỉnh cao</dc:title>
                    <dc:creator>Cửu Di Chi Dạ</dc:creator>
                    <dc:description>Bản tóm tắt nội dung huyền huyễn đỉnh cao.</dc:description>
                    <meta name="cover" content="cover-image" />
                </metadata>
                <manifest>
                    <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" />
                </manifest>
            </package>
        """.trimIndent()

        val doc = org.jsoup.Jsoup.parse(opfXml, "", org.jsoup.parser.Parser.xmlParser())
        val title = doc.getElementsByTag("dc:title").firstOrNull()?.text()
            ?: doc.getElementsByTag("title").firstOrNull()?.text()
        val author = doc.getElementsByTag("dc:creator").firstOrNull()?.text()
            ?: doc.getElementsByTag("creator").firstOrNull()?.text()
        val intro = doc.getElementsByTag("dc:description").firstOrNull()?.text()
            ?: doc.getElementsByTag("description").firstOrNull()?.text()

        assertEquals("[huyền huyễn] Chứng đạo thành tựu đỉnh cao", title)
        assertEquals("Cửu Di Chi Dạ", author)
        assertEquals("Bản tóm tắt nội dung huyền huyễn đỉnh cao.", intro)

        val coverMeta = doc.select("metadata > meta[name=cover]").firstOrNull()
        val coverId = coverMeta?.attr("content")?.trim()
        val coverItem = doc.getElementById(coverId!!)
            ?: doc.select("manifest > item[id='$coverId']").firstOrNull()
            ?: doc.select("manifest > item[id=$coverId]").firstOrNull()

        assertEquals("images/cover.jpg", coverItem?.attr("href"))
    }
}
