package io.legado.app.domain.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MangaExportPlanTest {
    @Test
    fun validatesSourceHashWithoutChangingSourceBytes() {
        val bytes = "source-image".toByteArray()
        val original = bytes.copyOf()
        val hash = mangaImageHash(bytes)
        val page = MangaOverlayPage(
            imageId = "one",
            imageHash = hash,
            cacheKey = "a".repeat(64),
            width = 100,
            height = 100,
            blocks = emptyList(),
            translations = emptyList(),
        )
        val plan = MangaExportPlan(
            baseName = "Book: chapter 1",
            pages = listOf(MangaExportPage(page, bytes)),
            format = MangaExportFormat.CBZ,
            manifest = MangaExportManifest(
                sourceHashes = listOf(hash),
                ocrVersion = "ocr",
                providerModelPromptRevision = "route",
                targetLanguage = "vi",
            ),
        )

        plan.validate()

        assertEquals(original.toList(), bytes.toList())
        assertEquals("Book_ chapter 1.cbz", plan.outputFileName())
    }

    @Test
    fun rejectsChangedSourceImage() {
        val bytes = "changed".toByteArray()
        val page = MangaOverlayPage(
            imageId = "one",
            imageHash = mangaImageHash("original".toByteArray()),
            cacheKey = "b".repeat(64),
            width = 1,
            height = 1,
            blocks = emptyList(),
            translations = emptyList(),
        )
        val plan = MangaExportPlan(
            baseName = "book",
            pages = listOf(MangaExportPage(page, bytes)),
            format = MangaExportFormat.PDF,
            manifest = MangaExportManifest(
                sourceHashes = listOf(page.imageHash),
                ocrVersion = "ocr",
                providerModelPromptRevision = "route",
                targetLanguage = "vi",
            ),
        )

        assertThrows(IllegalArgumentException::class.java, plan::validate)
    }
}
