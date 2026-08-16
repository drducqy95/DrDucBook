package io.legado.app.domain.usecase

import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.EbookBlockGeometry
import io.legado.app.domain.model.EbookDocument
import io.legado.app.domain.model.EbookDocumentChapter
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.domain.model.EbookLayoutMode
import io.legado.app.domain.model.EbookMetadata
import io.legado.app.domain.model.EbookPageSize
import io.legado.app.domain.model.EbookParagraphBlock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ValidateEbookProjectUseCaseTest {

    @Test
    fun reportsDuplicateMissingImageAndInvalidGeometry() {
        val duplicateId = "same"
        val project = AuthoringProject(
            id = "project",
            kind = AuthoringProjectKind.EBOOK_EDITOR,
            title = "Book",
            document = EbookDocument(
                layoutMode = EbookLayoutMode.FIXED_PAGE,
                pageSize = EbookPageSize(100f, 100f),
                chapters = listOf(
                    EbookDocumentChapter(
                        id = "chapter",
                        title = "One",
                        blocks = listOf(
                            EbookParagraphBlock(
                                id = duplicateId,
                                text = "Text",
                                geometry = EbookBlockGeometry(width = -1f),
                            ),
                            EbookImageBlock(
                                id = duplicateId,
                                uri = "missing.png",
                                alt = "",
                                geometry = EbookBlockGeometry(x = 90f, width = 20f),
                            ),
                        ),
                    )
                ),
            ),
            createdAt = 1L,
            updatedAt = 1L,
        )

        val issues = ValidateEbookProjectUseCase().execute(project)

        assertTrue(issues.any { it.message.contains("Duplicate block") })
        assertTrue(issues.any { it.message.contains("missing or corrupt") })
        assertTrue(issues.any { it.message.contains("Invalid fixed-layout geometry") })
        assertTrue(issues.any { it.message.contains("outside the page") })
    }

    @Test
    fun reportsMissingFontBrokenLinkAndOrphanResource() {
        val orphan = Files.createTempFile("ebook-orphan", ".bin").toFile()
        try {
            val project = AuthoringProject(
                id = "project",
                kind = AuthoringProjectKind.EBOOK_EDITOR,
                title = "Book",
                document = EbookDocument(
                    metadata = EbookMetadata(
                        customFontPaths = listOf(orphan.resolveSibling("missing.ttf").toString()),
                        resources = listOf(orphan.absolutePath),
                    ),
                    chapters = listOf(
                        EbookDocumentChapter(
                            id = "chapter",
                            title = "One",
                            blocks = listOf(EbookParagraphBlock(text = "[Missing](#unknown)")),
                        )
                    ),
                ),
                createdAt = 1L,
                updatedAt = 1L,
            )

            val issues = ValidateEbookProjectUseCase().execute(project)

            assertTrue(issues.any { it.message.contains("Font is missing") })
            assertTrue(issues.any { it.message.contains("Broken internal link") })
            assertTrue(issues.any { it.message.contains("Orphan resource") })
        } finally {
            orphan.delete()
        }
    }

    @Test
    fun acceptsFileUriImagesAndResourcesAsPresentAndUsed() {
        val image = Files.createTempFile("ebook-image", ".png").toFile()
        try {
            image.writeBytes(byteArrayOf(1, 2, 3))
            val imageUri = "file://${image.absolutePath}"
            val project = AuthoringProject(
                id = "project",
                kind = AuthoringProjectKind.EBOOK_EDITOR,
                title = "Book",
                document = EbookDocument(
                    metadata = EbookMetadata(resources = listOf(imageUri)),
                    chapters = listOf(
                        EbookDocumentChapter(
                            id = "chapter",
                            title = "One",
                            blocks = listOf(EbookImageBlock(uri = imageUri, alt = "Cover")),
                        )
                    ),
                ),
                createdAt = 1L,
                updatedAt = 1L,
            )

            val issues = ValidateEbookProjectUseCase().execute(project)

            assertFalse(issues.any { it.message.contains("missing or corrupt") })
            assertFalse(issues.any { it.message.contains("Resource is missing") })
            assertFalse(issues.any { it.message.contains("Orphan resource") })
        } finally {
            image.delete()
        }
    }
}
