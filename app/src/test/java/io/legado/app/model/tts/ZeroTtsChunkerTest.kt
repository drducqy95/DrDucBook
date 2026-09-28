package io.legado.app.model.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroTtsChunkerTest {

    @Test
    fun shortTextIsNotSplit() {
        val text = "Xin chào Việt Nam."
        val chunks = ZeroTtsOnnxEngine.splitIntoChunks(text, 120)
        assertEquals(1, chunks.size)
        assertEquals("Xin chào Việt Nam.", chunks[0])
    }

    @Test
    fun emptyOrBlankTextReturnsEmpty() {
        assertTrue(ZeroTtsOnnxEngine.splitIntoChunks("", 120).isEmpty())
        assertTrue(ZeroTtsOnnxEngine.splitIntoChunks("   \n\t  ", 120).isEmpty())
    }

    @Test
    fun sentenceBoundariesAreRespected() {
        val text = "Câu thứ nhất kết thúc ở đây. Câu thứ hai dài hơn một chút và cũng kết thúc ở đây! Còn câu thứ ba thì sao?"
        val chunks = ZeroTtsOnnxEngine.splitIntoChunks(text, 70)
        assertTrue(chunks.size >= 2)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.length} should be <= 70", chunk.length <= 70)
        }
    }

    @Test
    fun paragraph7SplitsCleanlyAtCommas() {
        val text = "Có thể nước máy luôn luôn không sạch sẽ, uống nhiều cũng sẽ sinh bệnh, trong trường hợp bị ốm, ở Nam Giao như này trong cô nhi viện của nghèo nàn, chờ đợi hắn của chính xác bị nhân vật thần thoại phụ trách lấy đi linh hồn của người qua đời thu đi."
        val chunks = ZeroTtsOnnxEngine.splitIntoChunks(text, 120)
        assertTrue("Expected 2..4 chunks, got ${chunks.size}: $chunks", chunks.size in 2..4)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.length} should be <= 120: $chunk", chunk.length <= 120)
        }
        val combined = chunks.joinToString(" ")
        assertTrue(combined.contains("nước máy luôn luôn không sạch sẽ"))
        assertTrue(combined.contains("thu đi."))
    }

    @Test
    fun paragraph7SplitsCleanlyAt85Chars() {
        val text = "Có thể nước máy luôn luôn không sạch sẽ, uống nhiều cũng sẽ sinh bệnh, trong trường hợp bị ốm, ở Nam Giao như này trong cô nhi viện của nghèo nàn, chờ đợi hắn của chính xác bị nhân vật thần thoại phụ trách lấy đi linh hồn của người qua đời thu đi."
        val chunks = ZeroTtsOnnxEngine.splitIntoChunks(text, 85)
        assertTrue("Expected 3..5 chunks at 85 chars, got ${chunks.size}: $chunks", chunks.size in 3..5)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.length} should be <= 85: $chunk", chunk.length <= 85)
        }
        val combined = chunks.joinToString(" ")
        assertTrue(combined.contains("nước máy luôn luôn không sạch sẽ"))
        assertTrue(combined.contains("thu đi."))
    }

    @Test
    fun ellipsisDoesNotSplitIncorrectly() {
        val text = "Chờ một chút... Đừng đi vội... Có chuyện gì thế?"
        val chunks = ZeroTtsOnnxEngine.splitIntoChunks(text, 30)
        for (chunk in chunks) {
            assertTrue("Chunk should be <= 30: $chunk", chunk.length <= 30)
        }
        assertTrue(chunks.any { it.contains("...") })
    }

    @Test
    fun longUnpunctuatedSentenceSplitsOnSpaces() {
        val words = List(30) { "từ$it" }
        val text = words.joinToString(" ")
        val chunks = ZeroTtsOnnxEngine.splitIntoChunks(text, 50)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.length} should be <= 50", chunk.length <= 50)
        }
        val reconstructed = chunks.joinToString(" ")
        assertEquals(text, reconstructed)
    }

    @Test
    fun longWordHardSplits() {
        val text = "A".repeat(150)
        val chunks = ZeroTtsOnnxEngine.splitIntoChunks(text, 50)
        assertEquals(3, chunks.size)
        assertEquals(50, chunks[0].length)
        assertEquals(50, chunks[1].length)
        assertEquals(50, chunks[2].length)
    }
}
