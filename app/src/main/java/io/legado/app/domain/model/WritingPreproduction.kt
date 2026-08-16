package io.legado.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PreWritingSectionKey {
    PREMISE,
    KEY_POINTS,
    WORLD_BIBLE,
    CHARACTER_BIBLE,
    PLOT_THREADS,
    OUTLINE,
    DETAILED_OUTLINE,
    ARC_VOLUME_OUTLINE,
    CHAPTER_ROADMAP,
    TIMELINE,
    STYLE_TONE,
}

@Serializable
enum class PreWritingSectionSource {
    USER,
    AI_APPLIED,
    IMPORT,
}

@Serializable
data class PreWritingSection(
    val content: String = "",
    val updatedAt: Long = 0L,
    val revision: Int = 0,
    val source: PreWritingSectionSource = PreWritingSectionSource.USER,
)

@Serializable
data class WritingPreproduction(
    val premise: PreWritingSection = PreWritingSection(),
    val keyPoints: PreWritingSection = PreWritingSection(),
    val worldBible: PreWritingSection = PreWritingSection(),
    val characterBible: PreWritingSection = PreWritingSection(),
    val plotThreads: PreWritingSection = PreWritingSection(),
    val outline: PreWritingSection = PreWritingSection(),
    val detailedOutline: PreWritingSection = PreWritingSection(),
    val arcVolumeOutline: PreWritingSection = PreWritingSection(),
    val chapterRoadmap: PreWritingSection = PreWritingSection(),
    val timeline: PreWritingSection = PreWritingSection(),
    val styleTone: PreWritingSection = PreWritingSection(),
) {
    fun section(key: PreWritingSectionKey): PreWritingSection = when (key) {
        PreWritingSectionKey.PREMISE -> premise
        PreWritingSectionKey.KEY_POINTS -> keyPoints
        PreWritingSectionKey.WORLD_BIBLE -> worldBible
        PreWritingSectionKey.CHARACTER_BIBLE -> characterBible
        PreWritingSectionKey.PLOT_THREADS -> plotThreads
        PreWritingSectionKey.OUTLINE -> outline
        PreWritingSectionKey.DETAILED_OUTLINE -> detailedOutline
        PreWritingSectionKey.ARC_VOLUME_OUTLINE -> arcVolumeOutline
        PreWritingSectionKey.CHAPTER_ROADMAP -> chapterRoadmap
        PreWritingSectionKey.TIMELINE -> timeline
        PreWritingSectionKey.STYLE_TONE -> styleTone
    }

    fun update(
        key: PreWritingSectionKey,
        content: String,
        source: PreWritingSectionSource,
        now: Long,
    ): WritingPreproduction {
        val current = section(key)
        val updated = current.copy(
            content = content,
            updatedAt = now,
            revision = current.revision + 1,
            source = source,
        )
        return when (key) {
            PreWritingSectionKey.PREMISE -> copy(premise = updated)
            PreWritingSectionKey.KEY_POINTS -> copy(keyPoints = updated)
            PreWritingSectionKey.WORLD_BIBLE -> copy(worldBible = updated)
            PreWritingSectionKey.CHARACTER_BIBLE -> copy(characterBible = updated)
            PreWritingSectionKey.PLOT_THREADS -> copy(plotThreads = updated)
            PreWritingSectionKey.OUTLINE -> copy(outline = updated)
            PreWritingSectionKey.DETAILED_OUTLINE -> copy(detailedOutline = updated)
            PreWritingSectionKey.ARC_VOLUME_OUTLINE -> copy(arcVolumeOutline = updated)
            PreWritingSectionKey.CHAPTER_ROADMAP -> copy(chapterRoadmap = updated)
            PreWritingSectionKey.TIMELINE -> copy(timeline = updated)
            PreWritingSectionKey.STYLE_TONE -> copy(styleTone = updated)
        }
    }
}
