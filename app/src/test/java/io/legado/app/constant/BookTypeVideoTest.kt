package io.legado.app.constant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTypeVideoTest {

    @Test
    fun allBookTypeMasks_includeVideo() {
        assertTrue(BookType.allBookType and BookType.video != 0)
        assertTrue(BookType.allBookTypeLocal and BookType.video != 0)
        assertTrue(BookType.allBookTypeLocal and BookType.local != 0)
    }

    @Test
    fun sourceAndBookVideoTypes_keepTheirPersistedValues() {
        assertEquals(4, BookSourceType.video)
        assertEquals(0b100, BookType.video)
    }
}
