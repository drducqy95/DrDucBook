package io.legado.app.domain.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MangaTranslationModelsTest {

    @Test
    fun horizontalReadingOrdersRowsThenLeftToRight() {
        val ordered = MangaReadingOrder.order(
            listOf(
                block("bottom", 10, 200),
                block("top-right", 200, 10),
                block("top-left", 10, 12),
            ),
            verticalReading = false,
        )

        assertEquals(listOf("top-left", "top-right", "bottom"), ordered.map { it.id })
        assertEquals(listOf(0, 1, 2), ordered.map { it.readingOrder })
    }

    @Test
    fun verticalReadingOrdersRightColumnsBeforeLeftColumns() {
        val ordered = MangaReadingOrder.order(
            listOf(
                block("left", 20, 20),
                block("right-bottom", 200, 140),
                block("right-top", 205, 10),
            ),
            verticalReading = true,
        )

        assertEquals(listOf("right-top", "right-bottom", "left"), ordered.map { it.id })
    }

    @Test
    fun cacheKeyChangesWhenImageModelPromptOrTargetChanges() {
        val base = mangaTranslationCacheKey(
            "image", MangaOcrScript.CHINESE, "ocr-1", "quick", "route-1", "vi"
        )

        assertNotEquals(
            base,
            mangaTranslationCacheKey(
                "image-2", MangaOcrScript.CHINESE, "ocr-1", "quick", "route-1", "vi"
            ),
        )
        assertNotEquals(
            base,
            mangaTranslationCacheKey(
                "image", MangaOcrScript.CHINESE, "ocr-1", "quick", "route-2", "vi"
            ),
        )
        assertNotEquals(
            base,
            mangaTranslationCacheKey(
                "image", MangaOcrScript.CHINESE, "ocr-1", "quick", "route-1", "en"
            ),
        )
        assertNotEquals(
            base,
            mangaTranslationCacheKey(
                "image", MangaOcrScript.CHINESE, "ocr-1", "nmt", "route-1", "vi"
            ),
        )
    }

    @Test
    fun aiCacheKeyIsSharedAcrossModelComboAndPromptChanges() {
        val combo = mangaTranslationCacheKey(
            "image", MangaOcrScript.CHINESE, "ocr-1", "app_ai", "combo-a", "vi"
        )
        val model = mangaTranslationCacheKey(
            "image", MangaOcrScript.CHINESE, "ocr-1", "app_ai", "model-b", "vi"
        )

        assertEquals(combo, model)
    }

    private fun block(id: String, left: Int, top: Int) = MangaTextBlock(
        id = id,
        text = id,
        polygon = emptyList(),
        boundingBox = MangaRect(left, top, left + 60, top + 40),
        confidence = 0.9f,
        orientation = MangaTextOrientation.HORIZONTAL,
        script = MangaOcrScript.CHINESE,
    )
}
