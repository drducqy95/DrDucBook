package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EbookDocumentTest {

    @Test
    fun legacyProjectMigratesWithoutLosingChapterContent() {
        val project = AuthoringProject(
            id = "project",
            kind = AuthoringProjectKind.EBOOK_EDITOR,
            title = "Book",
            chapters = listOf(AuthoringChapter("chapter", "One", "Alpha\n\nBeta", 1L, 1L)),
            createdAt = 1L,
            updatedAt = 1L,
        )

        val document = project.resolveEbookDocument()

        assertEquals(2, document.chapters.single().blocks.size)
        assertEquals("Alpha\n\nBeta", document.toAuthoringChapters(project.chapters, 2L).single().content)
    }

    @Test
    fun readingOrderIsIndependentFromLayerOrder() {
        val block = EbookParagraphBlock(
            readingOrder = 1,
            geometry = EbookBlockGeometry(zIndex = 9),
        )

        val reordered = block.withReadingOrder(4)

        assertEquals(4, reordered.readingOrder)
        assertEquals(9, reordered.geometry?.zIndex)
        assertNotEquals(reordered.readingOrder, reordered.geometry?.zIndex)
    }

    @Test
    fun preWritingRevisionIncrementsOnlyOnUpdate() {
        val updated = WritingPreproduction().update(
            PreWritingSectionKey.CHARACTER_BIBLE,
            "Lan: protagonist",
            PreWritingSectionSource.USER,
            10L,
        )

        assertEquals(1, updated.characterBible.revision)
        assertTrue(updated.characterBible.content.contains("Lan"))
    }
}
