package io.legado.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateDynamicUiTextUseCaseTest {

    @Test
    fun detectsChineseTextThatNeedsQuickTranslation() {
        assertTrue("第1章 地下车库".containsCjk())
        assertTrue("24,7万字".containsCjk())
    }

    @Test
    fun leavesVietnameseAndNumericLabelsUntouched() {
        assertFalse("Chương 1".containsCjk())
        assertFalse("24,7 triệu chữ".containsCjk())
    }

    @Test
    fun restructuresChapterNumberPatterns() {
        org.junit.Assert.assertEquals(
            "Chương 695 Thái Tưởng Tiến Bộ Liễu",
            "Đệ695 Chương Thái Tưởng Tiến Bộ Liễu".restructureChapterNumbers().toTitleCase()
        )
        org.junit.Assert.assertEquals(
            "1.Chương 1 Thiên Hàng Kỳ Duyên",
            "1.Đệ1 Chương Thiên Hàng Kỳ Duyên".restructureChapterNumbers().toTitleCase()
        )
        org.junit.Assert.assertEquals(
            "130.Chương 130 Kế Hoạch",
            "130.Đệ130 Chương Kế Hoạch".restructureChapterNumbers().toTitleCase()
        )
        org.junit.Assert.assertEquals(
            "Chương Nhất: Mở Đầu",
            "Đệ Nhất Chương: Mở Đầu".restructureChapterNumbers().toTitleCase()
        )
        org.junit.Assert.assertEquals(
            "Tiết 10",
            "Đệ 10 Tiết".restructureChapterNumbers().toTitleCase()
        )
        org.junit.Assert.assertEquals(
            "Quyển 2: Chương 5",
            "Đệ 2 Quyển: Đệ 5 Chương".restructureChapterNumbers().toTitleCase()
        )
        org.junit.Assert.assertEquals(
            "Huynh Đệ Cùng Tiến",
            "Huynh đệ cùng tiến".restructureChapterNumbers().toTitleCase()
        )
        org.junit.Assert.assertEquals(
            "Lâm Hi Ngộ Lộc · Chương 568 566: Nhậm Ý Môn",
            "Lâm Hi Ngộ Lộc · Đệ568 Chương 566: Nhậm Ý Môn".restructureChapterNumbers().toTitleCase()
        )
    }

    @Test
    fun bookNameTitleCaseCapitalizesEachWord() {
        org.junit.Assert.assertEquals(
            "Hogwarts Học Tập Bảng Diện",
            "hogwarts học tập bảng diện".toTitleCase()
        )
        org.junit.Assert.assertEquals(
            "Hoắc Cách Học Của Watts Bảng",
            "hoắc cách học của watts bảng".toTitleCase()
        )
    }

    @Test
    fun refinerHandlesSingleSegmentPlainTextFallback() {
        val result = io.legado.app.domain.model.AiTranslationRefinePipeline.parseRefinerStructureOutput(
            rawOutput = "\"Tiêu Viêm\"",
            expectedIds = listOf(0),
        )
        org.junit.Assert.assertEquals(1, result.refined_segments.size)
        org.junit.Assert.assertEquals(0, result.refined_segments.first().id)
        org.junit.Assert.assertEquals("Tiêu Viêm", result.refined_segments.first().refined_translation)
    }

    @Test
    fun refinerParsesChapterTitleInStoryTimeline() {
        val json = """
            {
                "refined_segments": [{"id": 0, "refined_translation": "Nội dung chương"}],
                "story_timeline": {
                    "chapter_title": "Chương 1: Mở Đầu Kỳ Duyên",
                    "summary": "Tóm tắt chương 1",
                    "events": ["Sự kiện 1"]
                }
            }
        """.trimIndent()
        val result = io.legado.app.domain.model.AiTranslationRefinePipeline.parseRefinerStructureOutput(
            rawOutput = json,
            expectedIds = listOf(0),
        )
        org.junit.Assert.assertEquals("Chương 1: Mở Đầu Kỳ Duyên", result.story_memory?.timeline?.chapterTitle)
        org.junit.Assert.assertEquals("Tóm tắt chương 1", result.story_memory?.timeline?.summary)
    }

    @Test
    fun canonicalScopeKeysAreWellFormed() {
        val chapterScope = TranslateDynamicUiTextUseCase.canonicalChapterTitleScopeKey("https://example.com/book/1", 42)
        org.junit.Assert.assertEquals("chapter-title:https://example.com/book/1:42", chapterScope)

        val metadataScope = TranslateDynamicUiTextUseCase.canonicalBookMetadataScopeKey("https://example.com/book/1", "name")
        org.junit.Assert.assertEquals("book-metadata:https://example.com/book/1:name", metadataScope)
    }
}
