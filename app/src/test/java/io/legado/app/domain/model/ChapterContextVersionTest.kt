package io.legado.app.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterContextVersionTest {

    @Test
    fun testChapterContextVersionUiProperties() {
        val rawVersion = ChapterContextVersionUi(
            id = "raw",
            title = "Bản gốc (Chữ Hán)",
            provider = null,
            charCount = 3500,
            statusDescription = null,
            isAvailable = true,
            previewText = "第一章 初始",
        )

        assertEquals("raw", rawVersion.id)
        assertEquals("Bản gốc (Chữ Hán)", rawVersion.title)
        assertEquals(3500, rawVersion.charCount)
        assertTrue(rawVersion.isAvailable)
        assertEquals("第一章 初始", rawVersion.previewText)
    }

    @Test
    fun testChapterContextCopyUiState() {
        val v1 = ChapterContextVersionUi(
            id = "raw",
            title = "Bản gốc",
            charCount = 2000,
            isAvailable = true,
        )
        val v2 = ChapterContextVersionUi(
            id = "provider:app_ai",
            title = "AI Provider",
            provider = "app_ai",
            charCount = 2500,
            statusDescription = "Đã chốt",
            isAvailable = true,
        )

        val state = ChapterContextCopyUiState(
            chapterTitle = "Chương 1",
            versions = persistentListOf(v1, v2),
            isLoading = false,
        )

        assertEquals("Chương 1", state.chapterTitle)
        assertEquals(2, state.versions.size)
        assertFalse(state.isLoading)
        assertEquals("provider:app_ai", state.versions[1].id)
    }
}
