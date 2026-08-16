package io.legado.app.ui.book.read.page.delegate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperPageGeometryTest {

    @Test
    fun nextPageCreaseMovesFromRightToLeft() {
        val start = PaperPageGeometry.frame(1_000, 0f, isNext = true)
        val middle = PaperPageGeometry.frame(1_000, 0.5f, isNext = true)
        val end = PaperPageGeometry.frame(1_000, 1f, isNext = true)

        assertEquals(1_000f, start.creaseX)
        assertEquals(500f, middle.creaseX)
        assertEquals(0f, end.creaseX)
        assertTrue(middle.foldWidth > 0f)
        assertEquals(0f, start.foldWidth)
        assertEquals(0f, end.foldWidth)
    }

    @Test
    fun previousPageCreaseMovesFromLeftToRightAndClampsProgress() {
        val beforeStart = PaperPageGeometry.frame(800, -1f, isNext = false)
        val afterEnd = PaperPageGeometry.frame(800, 2f, isNext = false)

        assertEquals(0f, beforeStart.progress)
        assertEquals(0f, beforeStart.creaseX)
        assertEquals(1f, afterEnd.progress)
        assertEquals(800f, afterEnd.creaseX)
    }
}
