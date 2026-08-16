package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentChunkerTest {

    @Test
    fun singleParagraphRejectsProviderOutputThatAddsParagraphs() {
        val chunk = ContentChunker.chunk("Một đoạn duy nhất.").single()

        assertNull(ContentChunker.restoreLayout(chunk, "Đoạn một.\n\nĐoạn hai ngoài ý muốn."))
    }

    @Test
    fun streamingAssemblyKeepsChunkOrderWithoutPersistingPreview() {
        val chunks = ContentChunker.chunk("Một.\n\nHai.", maxCharsPerChunk = 4)

        val preview = PartialTranslationAssembler.assembleStreaming(
            originalChunks = chunks,
            translatedMap = emptyMap(),
            partialMap = mapOf(chunks.first().index to "One."),
        )

        assertEquals("One.\n\nHai.", preview)
    }

    @Test
    fun restoresExactSourceWhitespaceAroundTranslatedParagraphs() {
        val source = "  Đoạn một  \r\n \r\n\tĐoạn hai\n\n\nĐoạn ba  "
        val chunk = ContentChunker.chunk(source, maxCharsPerChunk = 1_000).single()

        val restored = ContentChunker.restoreLayout(
            chunk,
            "Paragraph one\n\nParagraph two\n\nParagraph three",
        )

        assertEquals(
            "  Paragraph one  \r\n \r\n\tParagraph two\n\n\nParagraph three  ",
            restored,
        )
    }

    @Test
    fun chunkMergeDoesNotInventBlankLinesAtChunkBoundaries() {
        val source = "\n\n第一句。第二句。\n \n第三句。\n\n"
        val chunks = ContentChunker.chunk(source, maxCharsPerChunk = 4)
        assertTrue(chunks.size > 1)

        val translatedChunks = chunks.map { chunk ->
            chunk.copy(
                content = requireNotNull(
                    ContentChunker.restoreLayout(chunk, chunk.content.uppercase())
                )
            )
        }

        assertEquals(source.uppercase(), ContentChunker.merge(translatedChunks))
    }

    @Test
    fun treatsEverySourceLineAsStructuralAndRestoresExactSeparators() {
        val source = "  Line one  \r\n\tLine two\n\n  Line three  "
        val chunk = ContentChunker.chunk(source, 1_000).single()

        val restored = ContentChunker.restoreLayout(
            chunk,
            "Translated one\nTranslated two\nTranslated three",
        )

        assertEquals(
            "  Translated one  \r\n\tTranslated two\n\n  Translated three  ",
            restored,
        )
    }

    @Test
    fun streamingPreviewKeepsUntranslatedParagraphsAndExactSeparators() {
        val source = "  Source one.  \r\n \r\n\tSource two.\n\n\nSource three.  "
        val chunk = ContentChunker.chunk(source, 1_000).single()

        val preview = ContentChunker.previewWithLayout(chunk, "One.\n\nTwo in progress")

        assertEquals(
            "  One.  \r\n \r\n\tTwo in progress\n\n\nSource three.  ",
            preview,
        )
    }

    @Test
    fun rejectsProviderOutputThatLostAParagraph() {
        val chunk = ContentChunker.chunk("Đoạn một\n\nĐoạn hai", 1_000).single()

        assertNull(ContentChunker.restoreLayout(chunk, "Chỉ còn một đoạn"))
    }
}
