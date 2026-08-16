package io.legado.app.domain.usecase

import android.app.Application
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.CachedChapterSnapshot
import io.legado.app.domain.model.EbookDocument
import io.legado.app.domain.model.EbookDocumentChapter
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.service.export.EbookExportFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ExportAuthoringProjectUseCaseTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun epubExportPackagesEscapedLocalImageReferences() = runBlocking {
        val imageDirectory = Files.createTempDirectory("ebook-export-image").toFile()
        try {
            val image = File(imageDirectory, "page&one.png").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val imageUri = "file://${image.absolutePath}"
            val project = AuthoringProject(
                id = "project",
                kind = AuthoringProjectKind.EBOOK_EDITOR,
                title = "Comic",
                author = "Author",
                document = EbookDocument(
                    chapters = listOf(
                        EbookDocumentChapter(
                            id = "chapter",
                            title = "One",
                            blocks = listOf(EbookImageBlock(uri = imageUri, alt = "Page one")),
                        )
                    ),
                ),
                createdAt = 1L,
                updatedAt = 1L,
            )
            val output = ExportAuthoringProjectUseCase(
                context = RuntimeEnvironment.getApplication(),
                cachedChapterGateway = EmptyCachedChapterGateway,
                validateEbookProject = ValidateEbookProjectUseCase(),
            ).execute(project, EbookExportFormat.EPUB3)

            ZipFile(output).use { zip ->
                val chapterText = zip.getInputStream(zip.getEntry("OEBPS/Text/chapter_0.xhtml"))
                    .bufferedReader()
                    .readText()
                assertTrue(zip.getEntry("OEBPS/Images/page_one.png") != null)
                assertTrue(chapterText.contains("../Images/page_one.png"))
                assertFalse(chapterText.contains(image.absolutePath))
                assertFalse(chapterText.contains("page&amp;one.png"))
            }
        } finally {
            imageDirectory.deleteRecursively()
        }
    }
}

private object EmptyCachedChapterGateway : CachedChapterGateway {
    override suspend fun getBook(bookUrl: String): Book? = null
    override suspend fun getChapterCount(bookUrl: String): Int = 0
    override fun streamChapterCache(book: Book): Flow<CachedChapterSnapshot> = emptyFlow()
}
