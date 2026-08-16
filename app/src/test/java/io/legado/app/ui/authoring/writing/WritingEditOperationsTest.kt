package io.legado.app.ui.authoring.writing

import io.legado.app.domain.model.AuthoringChapter
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WritingEditOperationsTest {

    @Test
    fun countsAndReplacesLiteralMatchesWithoutRegexSideEffects() {
        val text = "a.b a.b a-b"

        assertEquals(2, countLiteralOccurrences(text, "a.b"))
        assertEquals(
            WritingReplacementResult("X a.b a-b", 1),
            replaceFirstLiteral(text, "a.b", "X"),
        )
        assertEquals(
            WritingReplacementResult("X X a-b", 2),
            replaceAllLiteral(text, "a.b", "X"),
        )
    }

    @Test
    fun duplicateChapterInsertsCopyAfterSelectedChapter() {
        val project = project()

        val (updated, duplicateId) = duplicateChapterInProject(
            project = project,
            chapterId = "one",
            now = 10L,
            copyLabel = "Copy",
        )!!

        assertEquals(listOf("one", duplicateId, "two"), updated.chapters.map { it.id })
        assertNotEquals("one", duplicateId)
        updated.chapters[1].let { duplicate ->
            assertEquals("One Copy", duplicate.title)
            assertEquals("Body one", duplicate.content)
            assertEquals(10L, duplicate.createdAt)
            assertEquals(10L, duplicate.updatedAt)
        }
    }

    @Test
    fun duplicateChapterReturnsNullWhenSelectionIsMissing() {
        assertNull(duplicateChapterInProject(project(), "missing", now = 10L))
    }

    private fun project() = AuthoringProject(
        id = "project",
        kind = AuthoringProjectKind.WRITING,
        title = "Draft",
        chapters = listOf(
            AuthoringChapter(
                id = "one",
                title = "One",
                content = "Body one",
                createdAt = 1L,
                updatedAt = 1L,
            ),
            AuthoringChapter(
                id = "two",
                title = "Two",
                content = "Body two",
                createdAt = 2L,
                updatedAt = 2L,
            ),
        ),
        createdAt = 1L,
        updatedAt = 2L,
    )
}
