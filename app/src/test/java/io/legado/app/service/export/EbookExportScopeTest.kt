package io.legado.app.service.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EbookExportScopeTest {
    @Test
    fun blankAndAll_selectEveryChapter() {
        assertEquals(setOf(0, 1, 2), selectExportChapterIndices(null, 3))
        assertEquals(setOf(0, 1, 2), selectExportChapterIndices("all", 3))
    }

    @Test
    fun oneBasedRanges_areMergedAndClamped() {
        assertEquals(
            linkedSetOf(0, 1, 2, 4, 5),
            selectExportChapterIndices("1-3, 5, 6, 99", 6),
        )
    }

    @Test
    fun invalidScope_returnsEmptySelection() {
        assertTrue(selectExportChapterIndices("0,-1,9-2,abc", 5).isEmpty())
    }

    @Test
    fun modernExportFormatsExposeWriterBackedFormats() {
        assertEquals(
            listOf(
                EbookExportFormat.EPUB3,
                EbookExportFormat.PDF,
                EbookExportFormat.TXT,
                EbookExportFormat.HTML,
                EbookExportFormat.CBZ,
            ),
            modernEbookExportFormats,
        )
    }

    @Test
    fun contentSourceParser_keepsLegacyBothDefaultAndRawAlias() {
        assertEquals(EbookExportContentSource.BOTH, EbookExportContentSource.from(null))
        assertEquals(EbookExportContentSource.ORIGINAL, EbookExportContentSource.from("raw"))
        assertEquals(EbookExportContentSource.TRANSLATION, EbookExportContentSource.from("translated"))
        assertTrue(EbookExportContentSource.BOTH.includesOriginal)
        assertTrue(EbookExportContentSource.BOTH.includesTranslation)
    }

    @Test
    fun largeExportChapters_areSplitInStableOrder() {
        val chapters = listOf(
            EbookExportChapter(0, "One", "1234", "1234"),
            EbookExportChapter(1, "Two", "5678", "5678"),
            EbookExportChapter(2, "Three", "9", "9"),
        )
        val parts = splitExportChapters(chapters, maxPartBytes = 15)
        assertEquals(2, parts.size)
        assertEquals(listOf("One"), parts[0].map { it.title })
        assertEquals(listOf("Two", "Three"), parts[1].map { it.title })
    }
}
