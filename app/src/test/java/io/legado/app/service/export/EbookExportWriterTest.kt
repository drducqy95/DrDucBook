package io.legado.app.service.export

import android.app.Application
import android.net.Uri
import io.legado.app.utils.FileDoc
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class EbookExportWriterTest {

    @Test
    fun fixedLayoutEpubContainsRenditionAndViewportMetadata() {
        val directory = Files.createTempDirectory("ebook-writer").toFile()
        try {
            val outputDirectory = FileDoc(
                name = directory.name,
                isDir = true,
                size = directory.length(),
                lastModified = directory.lastModified(),
                uri = Uri.fromFile(directory),
            )
            val target = EbookExportWriter(outputDirectory, Charsets.UTF_8)
                .write(
                    EbookExportPayload(
                        title = "Book",
                        author = "Author",
                        intro = "Intro",
                        language = "vi",
                        chapters = listOf(EbookExportChapter(0, "One", "<p class=\"legado-dropcap\">Alpha</p>", "Alpha")),
                        layoutMode = "FIXED_PAGE",
                        viewportWidth = 794f,
                        viewportHeight = 1123f,
                        layoutCss = ".legado-dropcap::first-letter{float:left}",
                    ),
                    EbookExportFormat.EPUB3,
                    "book.epub",
                ).asFile()!!

            ZipFile(target).use { zip ->
                val packageText = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).bufferedReader().readText()
                val chapterText = zip.getInputStream(zip.getEntry("OEBPS/Text/chapter_0.xhtml")).bufferedReader().readText()
                assertTrue(packageText.contains("rendition:layout"))
                assertTrue(chapterText.contains("width=794.0,height=1123.0"))
            }
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun txtExportNormalizesLineEndingsToLf() {
        val directory = Files.createTempDirectory("ebook-writer-txt").toFile()
        try {
            val target = EbookExportWriter(outputDirectory(directory), Charsets.UTF_8)
                .write(
                    EbookExportPayload(
                        title = "Book",
                        author = "Author",
                        intro = "Intro\r\nNext",
                        language = "vi",
                        chapters = listOf(
                            EbookExportChapter(0, "One", "Alpha", "Alpha\rBeta"),
                        ),
                    ),
                    EbookExportFormat.TXT,
                    "book.txt",
                ).asFile()!!

            assertEquals(
                "Book\nAuthor: Author\n\nIntroduction\nIntro\nNext\n\n\nOne\n\nAlpha\nBeta\n",
                target.readText(Charsets.UTF_8),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun epubExportIsDeterministicAndKeepsCoverAndImageManifestValid() {
        val directory = Files.createTempDirectory("ebook-writer-epub").toFile()
        val assets = Files.createTempDirectory("ebook-writer-assets").toFile()
        try {
            val cover = File(assets, "cover.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val first = File(File(assets, "a").apply { mkdirs() }, "shared.png").apply {
                writeBytes(byteArrayOf(4, 5, 6))
            }
            val second = File(File(assets, "b").apply { mkdirs() }, "shared.png").apply {
                writeBytes(byteArrayOf(7, 8, 9))
            }
            val payload = EbookExportPayload(
                title = "Book",
                author = "Author",
                intro = "Intro",
                language = "vi",
                cover = cover,
                chapters = listOf(
                    EbookExportChapter(
                        index = 0,
                        title = "One",
                        html = """<p><img src="${first.absolutePath}"/><img src="${second.absolutePath}"/></p>""",
                        plainText = "One",
                        images = listOf(
                            EbookExportImage(first.absolutePath, first, first.name),
                            EbookExportImage(second.absolutePath, second, second.name),
                        ),
                    ),
                ),
            )
            val writer = EbookExportWriter(outputDirectory(directory), Charsets.UTF_8)

            val firstBytes = writer.write(payload, EbookExportFormat.EPUB3, "book.epub").asFile()!!.readBytes()
            val secondFile = writer.write(payload, EbookExportFormat.EPUB3, "book-copy.epub").asFile()!!

            assertArrayEquals(firstBytes, secondFile.readBytes())
            ZipFile(secondFile).use { zip ->
                val packageText = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).bufferedReader().readText()
                val chapterText = zip.getInputStream(zip.getEntry("OEBPS/Text/chapter_0.xhtml")).bufferedReader().readText()
                val imageEntries = zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("OEBPS/Images/shared") && it.endsWith(".png") }
                    .toList()

                assertTrue(packageText.contains("href=\"Images/cover.png\" media-type=\"image/png\" properties=\"cover-image\""))
                assertEquals(2, imageEntries.size)
                assertEquals(2, Regex("""\.\./Images/shared[^"]*\.png""").findAll(chapterText).count())
                assertTrue(zip.entries().asSequence().all { it.time == 0L })
            }
        } finally {
            directory.deleteRecursively()
            assets.deleteRecursively()
        }
    }

    @Test
    fun epub2ExportUsesNcxAndVersionTwoPackage() {
        val directory = Files.createTempDirectory("ebook-writer-epub2").toFile()
        try {
            val target = EbookExportWriter(outputDirectory(directory), Charsets.UTF_8)
                .write(
                    EbookExportPayload(
                        title = "Book",
                        author = "Author",
                        intro = "Intro",
                        language = "vi",
                        chapters = listOf(EbookExportChapter(0, "One", "<p>Text</p>", "Text")),
                    ),
                    EbookExportFormat.EPUB2,
                    "book.epub",
                ).asFile()!!

            ZipFile(target).use { zip ->
                val packageText = zip.getInputStream(zip.getEntry("OEBPS/content.opf"))
                    .bufferedReader().use { it.readText() }
                assertTrue(packageText.contains("version=\"2.0\""))
                assertTrue(packageText.contains("href=\"toc.ncx\""))
                assertTrue(packageText.contains("<spine toc=\"ncx\">"))
                assertTrue(zip.getEntry("OEBPS/toc.ncx") != null)
                assertTrue(zip.getEntry("OEBPS/nav.xhtml") == null)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun exportRewritesAbsoluteAndRelativeImageReferences() {
        val directory = Files.createTempDirectory("ebook-writer-image-alias").toFile()
        val assets = Files.createTempDirectory("ebook-writer-image-alias-assets").toFile()
        try {
            val image = File(assets, "page-001.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val absolute = "https://cdn.example.com/comic/page-001.jpg"
            val relative = "/comic/page-001.jpg"
            val payload = EbookExportPayload(
                title = "Comic",
                author = "Author",
                intro = "Intro",
                language = "vi",
                chapters = listOf(
                    EbookExportChapter(
                        index = 0,
                        title = "One",
                        html = """<section><img src="$absolute"/><img src="$relative"/></section>""",
                        plainText = "One",
                        images = listOf(
                            EbookExportImage(
                                source = absolute,
                                file = image,
                                fileName = image.name,
                                aliases = listOf(relative),
                            ),
                        ),
                    ),
                ),
            )
            val writer = EbookExportWriter(outputDirectory(directory), Charsets.UTF_8)

            val epub = writer.write(payload, EbookExportFormat.EPUB3, "comic.epub").asFile()!!
            ZipFile(epub).use { zip ->
                val chapterText = zip.getInputStream(zip.getEntry("OEBPS/Text/chapter_0.xhtml"))
                    .bufferedReader()
                    .readText()

                assertEquals(2, Regex("""\.\./Images/page-001\.jpg""").findAll(chapterText).count())
                assertFalse(chapterText.contains(absolute))
                assertFalse(chapterText.contains(relative))
                assertTrue(zip.getEntry("OEBPS/Images/page-001.jpg") != null)
            }

            val html = writer.write(payload, EbookExportFormat.HTML, "comic.html").asFile()!!
                .readText(Charsets.UTF_8)
            assertEquals(2, Regex("""data:image/jpeg;base64,""").findAll(html).count())
            assertFalse(html.contains(absolute))
            assertFalse(html.contains(relative))
        } finally {
            directory.deleteRecursively()
            assets.deleteRecursively()
        }
    }

    @Test
    fun epubExportStreamsLargeImageWithStableZipMetadata() {
        val directory = Files.createTempDirectory("ebook-writer-large-epub").toFile()
        val assets = Files.createTempDirectory("ebook-writer-large-assets").toFile()
        try {
            val image = File(assets, "large-cover.jpg").also(::writeLargeAsset)
            val target = EbookExportWriter(outputDirectory(directory), Charsets.UTF_8)
                .write(
                    EbookExportPayload(
                        title = "Large",
                        author = "Author",
                        intro = "Intro",
                        language = "vi",
                        chapters = listOf(
                            EbookExportChapter(
                                index = 0,
                                title = "One",
                                html = """<p><img src="${image.absolutePath}"/></p>""",
                                plainText = "One",
                                images = listOf(
                                    EbookExportImage(image.absolutePath, image, image.name),
                                ),
                            ),
                        ),
                    ),
                    EbookExportFormat.EPUB3,
                    "large.epub",
                ).asFile()!!

            ZipFile(target).use { zip ->
                val entry = zip.getEntry("OEBPS/Images/large-cover.jpg")
                assertEquals(image.length(), entry.size)
                assertEquals(crc32(image), entry.crc)
                assertEquals(0L, entry.time)
            }
        } finally {
            directory.deleteRecursively()
            assets.deleteRecursively()
        }
    }

    private fun outputDirectory(directory: File) = FileDoc(
        name = directory.name,
        isDir = true,
        size = directory.length(),
        lastModified = directory.lastModified(),
        uri = Uri.fromFile(directory),
    )

    private fun writeLargeAsset(file: File) {
        file.outputStream().buffered().use { output ->
            repeat(2 * 1024) { block ->
                output.write(ByteArray(1024) { index -> ((block + index) % 251).toByte() })
            }
        }
    }

    private fun crc32(file: File): Long {
        val crc = CRC32()
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }
}
