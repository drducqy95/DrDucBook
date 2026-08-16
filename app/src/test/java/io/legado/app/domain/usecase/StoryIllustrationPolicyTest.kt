package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiTranslationStoryEntity
import io.legado.app.domain.model.AiTranslationStoryMemorySnapshot
import io.legado.app.domain.model.AiTranslationStoryRelationship
import io.legado.app.domain.model.AiTranslationStoryTimeline
import io.legado.app.domain.model.AiTranslationWorldEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryIllustrationPolicyTest {

    @Test
    fun `entity becomes eligible only after it has enough verified facts`() {
        val sparse = AiTranslationStoryEntity(raw = "韩立", target = "Hàn Lập")
        val detailed = sparse.copy(
            description = "Một thiếu niên thận trọng, mặc áo xanh và tu luyện theo con đường trường sinh.",
        )

        assertFalse(StoryIllustrationPolicy.isEntityReady(sparse))
        assertTrue(StoryIllustrationPolicy.isEntityReady(detailed))
    }

    @Test
    fun `world map requires several geography facts plus continuity`() {
        val entries = listOf(
            world("黄枫谷", "Hoàng Phong Cốc", "faction"),
            world("越国", "Việt Quốc", "location"),
            world("太南谷", "Thái Nam Cốc", "location"),
        )
        val withoutContinuity = AiTranslationStoryMemorySnapshot(worldBuilding = entries)
        val withContinuity = withoutContinuity.copy(
            timelines = listOf(AiTranslationStoryTimeline(chapterIndex = 0, summary = "Khởi hành")),
        )

        assertFalse(StoryIllustrationPolicy.isWorldMapReady(withoutContinuity))
        assertTrue(StoryIllustrationPolicy.isWorldMapReady(withContinuity))
    }

    @Test
    fun `world map prompt contains only stored map facts`() {
        val snapshot = AiTranslationStoryMemorySnapshot(
            worldBuilding = listOf(world("越国", "Việt Quốc", "location")),
            relationships = listOf(
                AiTranslationStoryRelationship(
                    source = "越国",
                    target = "黄枫谷",
                    relationship = "contains",
                )
            ),
        )

        val prompt = StoryIllustrationPolicy.worldMapPrompt(snapshot)

        assertTrue(prompt.contains("Việt Quốc"))
        assertFalse(prompt.contains("__story_world_map__"))
    }

    private fun world(raw: String, target: String, category: String) = AiTranslationWorldEntry(
        raw = raw,
        target = target,
        category = category,
        description = "Dữ kiện địa lý đã được xác nhận trong nội dung truyện.",
    )
}
