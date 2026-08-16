package io.legado.app.service.export

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.utils.FileDoc
import io.legado.app.domain.model.EbookBlockGeometry
import io.legado.app.domain.model.EbookDocumentChapter
import io.legado.app.domain.model.EbookParagraphBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class EbookExportWriterInstrumentedTest {

    @Test
    fun fixedLayoutPdfKeepsDocumentPageBoundaries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outputDir = File(context.cacheDir, "ebook-fixed-pdf-${System.nanoTime()}").apply { mkdirs() }
        try {
            val chapter = EbookDocumentChapter(
                id = "chapter",
                title = "One",
                blocks = listOf(
                    EbookParagraphBlock(
                        text = "First page",
                        geometry = EbookBlockGeometry(x = 20f, y = 30f, width = 180f, height = 80f, page = 0),
                    ),
                    EbookParagraphBlock(
                        text = "Second page",
                        geometry = EbookBlockGeometry(x = 40f, y = 50f, width = 160f, height = 80f, page = 1),
                    ),
                ),
            )
            val output = EbookExportWriter(FileDoc.fromDir(outputDir.absolutePath), Charsets.UTF_8)
                .write(
                    EbookExportPayload(
                        title = "Fixed book",
                        author = "Author",
                        intro = "Intro",
                        language = "vi",
                        chapters = listOf(
                            EbookExportChapter(
                                index = 0,
                                title = chapter.title,
                                html = "",
                                plainText = "First page\nSecond page",
                                documentChapter = chapter,
                            )
                        ),
                        layoutMode = "FIXED_PAGE",
                        viewportWidth = 240f,
                        viewportHeight = 320f,
                    ),
                    EbookExportFormat.PDF,
                    "fixed.pdf",
                ).asFile()!!

            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(3, renderer.pageCount)
                    renderer.openPage(1).use { page ->
                        assertEquals(240, page.width)
                        assertEquals(320, page.height)
                    }
                }
            }
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun writesAllModernFormatsWithLocalizedIntroduction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outputDir = File(context.cacheDir, "ebook-export-test-${System.nanoTime()}")
            .apply { mkdirs() }
        try {
            val payload = EbookExportPayload(
                title = "Truyện kiểm thử",
                author = "Tác giả mẫu",
                intro = "Đây là phần giới thiệu.",
                language = "vi",
                chapters = listOf(
                    EbookExportChapter(
                        index = 0,
                        title = "Chương 1",
                        html = "<p>Nội dung chương một.</p>",
                        plainText = "Nội dung chương một.",
                    ),
                    EbookExportChapter(
                        index = 1,
                        title = "Chương 2",
                        html = "<p>Nội dung chương hai.</p>",
                        plainText = "Nội dung chương hai.",
                    ),
                ),
                labels = EbookExportLabels(
                    author = "Tác giả",
                    introduction = "Giới thiệu",
                    tableOfContents = "Mục lục",
                ),
            )
            val progress = mutableMapOf<EbookExportFormat, Int>()
            val directory = FileDoc.fromDir(outputDir.absolutePath)

            modernEbookExportFormats.forEach { format ->
                val output = EbookExportWriter(
                    outputDirectory = directory,
                    charset = Charsets.UTF_8,
                    onProgress = { completed, _ -> progress[format] = completed },
                ).write(payload, format, "probe.${format.extension}").asFile()!!

                assertTrue("${format.value} output is empty", output.length() > 0L)
                assertEquals(2, progress[format])
                verifyFormat(format, output)
            }
        } finally {
            outputDir.deleteRecursively()
        }
    }

    private fun verifyFormat(format: EbookExportFormat, output: File) {
        when (format) {
            EbookExportFormat.TXT -> {
                val text = output.readText()
                assertTrue(text.contains("Tác giả: Tác giả mẫu"))
                assertTrue(text.contains("Giới thiệu"))
                assertTrue(text.contains("Chương 2"))
            }

            EbookExportFormat.HTML -> {
                val text = output.readText()
                assertTrue(text.contains("<html lang=\"vi\""))
                assertTrue(text.contains("<h2>Giới thiệu</h2>"))
                assertTrue(text.contains("Nội dung chương hai."))
            }

            EbookExportFormat.EPUB3 -> ZipFile(output).use { zip ->
                assertTrue(zip.getEntry("mimetype") != null)
                assertTrue(zip.getEntry("META-INF/container.xml") != null)
                assertTrue(zip.getEntry("OEBPS/content.opf") != null)
                val intro = zip.getInputStream(zip.getEntry("OEBPS/intro.xhtml"))
                    .bufferedReader().use { it.readText() }
                val navigation = zip.getInputStream(zip.getEntry("OEBPS/nav.xhtml"))
                    .bufferedReader().use { it.readText() }
                assertTrue(intro.contains("Giới thiệu"))
                assertTrue(navigation.contains("Mục lục"))
                assertTrue(navigation.contains("Chương 2"))
            }

            EbookExportFormat.PDF -> {
                assertTrue(output.inputStream().use { input ->
                    val header = ByteArray(4)
                    input.read(header) == header.size && header.contentEquals("%PDF".toByteArray())
                })
            }

            EbookExportFormat.CBZ -> ZipFile(output).use { zip ->
                assertTrue(zip.entries().asSequence().any { it.name.endsWith(".jpg") })
            }

            EbookExportFormat.EPUB2 -> error("EPUB2 is handled by the legacy writer")
        }
    }
}
