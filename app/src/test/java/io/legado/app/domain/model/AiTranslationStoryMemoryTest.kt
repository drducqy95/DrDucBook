package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationStoryMemoryTest {

    @Test
    fun parsesStructuredTimelineAndRejectsHallucinatedEntities() {
        val source = "\u53f6\u957f\u751f\u62d4\u51fa\u9752\u950b\u5251\uff0c\u52a0\u5165\u5927\u68a6\u5b66\u5bab\u3002"
        val result = AiTranslationStoryMemoryPipeline.parseAnalysis(
            rawOutput = """
                {
                  "entities": [
                    {"raw":"\u53f6\u957f\u751f","target":"Diệp Trường Sinh","type":"character"},
                    {"raw":"\u4e0d\u5b58\u5728","target":"Không Tồn Tại","type":"character"}
                  ],
                  "relationships": [
                    {"source":"\u53f6\u957f\u751f","target":"\u5927\u68a6\u5b66\u5bab","relationship":"member_of"}
                  ],
                  "world_building": [
                    {"raw":"\u9752\u950b\u5251","target":"Thanh Phong Kiếm","category":"weapon","description":"佩剑"},
                    {"raw":"\u865a\u6784\u6cd5\u5b9d","target":"Bịa đặt","category":"item"}
                  ],
                  "timeline": {
                    "summary":"Diệp Trường Sinh gia nhập học cung.",
                    "events":["Rút Thanh Phong Kiếm","Gia nhập Đại Mộng Học Cung"],
                    "characters":[{"raw":"\u53f6\u957f\u751f","target":"Diệp Trường Sinh","status":"new","role":"protagonist","relationships":["member_of Đại Mộng Học Cung"]}],
                    "discoveries":[{"raw":"\u9752\u950b\u5251","target":"Thanh Phong Kiếm","category":"weapon"}]
                  }
                }
            """.trimIndent(),
            chapterIndex = 0,
            chapterTitle = "Chương 1",
            source = source,
        )

        assertEquals(listOf("\u53f6\u957f\u751f"), result.entities.map { it.raw })
        assertEquals(listOf("\u9752\u950b\u5251"), result.worldBuilding.map { it.raw })
        assertEquals("new", result.timeline.characters.single().status)
        assertEquals("weapon", result.timeline.discoveries.single().category)
    }

    @Test
    fun contextUsesOnlyTwoPreviousTimelinesAndCurrentReferencedMemory() {
        val current = "\u53f6\u957f\u751f\u4f7f\u7528\u9752\u950b\u5251\u3002"
        val snapshot = AiTranslationStoryMemorySnapshot(
            entities = listOf(
                AiTranslationStoryEntity("\u53f6\u957f\u751f", "Diệp Trường Sinh"),
                AiTranslationStoryEntity("\u9646\u96ea", "Lục Tuyết"),
            ),
            relationships = listOf(
                AiTranslationStoryRelationship("\u53f6\u957f\u751f", "\u9646\u96ea", "ally_of", chapterIndex = 2),
            ),
            worldBuilding = listOf(
                AiTranslationWorldEntry("\u9752\u950b\u5251", "Thanh Phong Kiếm", "weapon", chapterIndex = 1),
                AiTranslationWorldEntry("\u5927\u68a6\u5b66\u5bab", "Đại Mộng Học Cung", "faction", chapterIndex = 1),
            ),
            timelines = (0..3).map { index ->
                AiTranslationStoryTimeline(index, "Chương ${index + 1}", "Tóm tắt $index")
            },
        )

        val context = AiTranslationStoryMemoryPipeline.selectContext(snapshot, 4, current)

        assertEquals(listOf(2, 3), context.recentTimelines.map { it.chapterIndex })
        assertEquals(listOf("\u53f6\u957f\u751f"), context.currentEntities.map { it.raw })
        assertEquals(listOf("\u9752\u950b\u5251"), context.currentWorldBuilding.map { it.raw })
        assertTrue(context.currentRelationships.isNotEmpty())
        assertEquals(2, context.entityDictionary.size)
        assertFalse(context.currentWorldBuilding.any { it.raw == "\u5927\u68a6\u5b66\u5bab" })
    }

    @Test
    fun missingPreviousChapterMemoryIsSkippedWithoutDroppingOtherContext() {
        val snapshot = AiTranslationStoryMemorySnapshot(
            entities = listOf(AiTranslationStoryEntity("\u53f6\u957f\u751f", "Diệp Trường Sinh")),
            worldBuilding = listOf(
                AiTranslationWorldEntry("\u9752\u950b\u5251", "Thanh Phong Kiếm", "weapon")
            ),
            timelines = listOf(
                AiTranslationStoryTimeline(0, "Chương 1", "Khởi đầu"),
                AiTranslationStoryTimeline(4, "Chương 5", "Một chương không liền trước"),
            ),
        )

        val firstChapter = AiTranslationStoryMemoryPipeline.selectContext(
            snapshot = snapshot,
            chapterIndex = 0,
            source = "\u53f6\u957f\u751f\u62d4\u51fa\u9752\u950b\u5251",
        )
        val jumpedChapter = AiTranslationStoryMemoryPipeline.selectContext(
            snapshot = snapshot,
            chapterIndex = 8,
            source = "\u53f6\u957f\u751f\u62d4\u51fa\u9752\u950b\u5251",
        )

        assertTrue(firstChapter.recentTimelines.isEmpty())
        assertTrue(jumpedChapter.recentTimelines.isEmpty())
        assertEquals(listOf("\u53f6\u957f\u751f"), jumpedChapter.currentEntities.map { it.raw })
        assertEquals(listOf("\u9752\u950b\u5251"), jumpedChapter.currentWorldBuilding.map { it.raw })
    }

    @Test
    fun analysisPromptDefinesOrderedWorldMemoryWorkflow() {
        val prompt = AiTranslationStoryMemoryPipeline.buildAnalysisSystemPrompt()

        assertTrue(prompt.contains("entities, relationships, world building, then chapter timeline"))
        assertTrue(prompt.contains("equipment|weapon|technique|faction"))
        assertTrue(prompt.contains("status new/existing"))
    }
}
