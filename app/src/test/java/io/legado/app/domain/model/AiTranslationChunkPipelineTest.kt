package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTranslationChunkPipelineTest {

    @Test
    fun streamAccumulatorKeepsDeltaFragmentsInOrder() {
        val accumulator = AiTranslationStreamAccumulator()

        listOf("A", "B", "C").forEach(accumulator::append)

        assertEquals("ABC", accumulator.toString())
    }

    @Test
    fun streamAccumulatorReplacesCumulativeProviderEvents() {
        val accumulator = AiTranslationStreamAccumulator()

        listOf("A", "AB", "ABC", "ABC").forEach(accumulator::append)

        assertEquals("ABC", accumulator.toString())
    }

    @Test
    fun plannerUsesOnlyBoundedAdjacentSourceContext() {
        val chunks = listOf(
            TextChunk(0, "previous-123456", emptyList()),
            TextChunk(1, "current", emptyList()),
            TextChunk(2, "next-123456", emptyList()),
        )

        val context = AiTranslationChunkPlanner.contextFor(chunks, 1, maxCharsPerChunk = 10)

        assertEquals("ous-123456", context.previous)
        assertEquals("next-12345", context.next)
    }

    @Test
    fun plannerPrefersCompleteAdjacentSentencesInsideTheContextBudget() {
        val chunks = listOf(
            TextChunk(0, "discard me. Previous sentence.", emptyList()),
            TextChunk(1, "current", emptyList()),
            TextChunk(2, "Next sentence. discard tail", emptyList()),
        )

        val context = AiTranslationChunkPlanner.contextFor(
            chunks = chunks,
            chunkIndex = 1,
            maxCharsPerChunk = 20,
        )

        assertEquals("Previous sentence.", context.previous)
        assertEquals("Next sentence.", context.next)
    }

    @Test
    fun outputBudgetScalesWithChunkAndHonorsSmallerCaps() {
        assertEquals(2_768, AiTranslationTokenBudget.forSourceChars(1_000, null, 65_536))
        assertEquals(2_000, AiTranslationTokenBudget.forSourceChars(1_000, 2_000, 65_536))
        assertEquals(4_096, AiTranslationTokenBudget.forSourceChars(2_000, null, 4_096))
    }

    @Test
    fun structuredTranslationBudgetIncludesJsonAndStoryMemoryOverhead() {
        assertEquals(
            6_048,
            AiTranslationTokenBudget.forSourceChars(
                sourceChars = 1_000,
                configuredLimit = null,
                providerLimit = 65_536,
                structuredJson = true,
            ),
        )
        assertEquals(
            2_000,
            AiTranslationTokenBudget.forSourceChars(
                sourceChars = 1_000,
                configuredLimit = 2_000,
                providerLimit = 65_536,
                structuredJson = true,
            ),
        )
    }

    @Test
    fun reasoningOutputBudgetCannotBeStarvedByPresetCap() {
        assertEquals(
            4_096,
            AiTranslationTokenBudget.forSourceChars(
                sourceChars = 4,
                configuredLimit = 1_024,
                providerLimit = 65_536,
                reasoningModel = true,
            ),
        )
        assertEquals(
            2_048,
            AiTranslationTokenBudget.forSourceChars(
                sourceChars = 4,
                configuredLimit = null,
                providerLimit = 2_048,
                reasoningModel = true,
            ),
        )
    }

    @Test
    fun fallbackTargetRecalculatesBudgetForItsActualModel() {
        assertEquals(
            4_096,
            AiTranslationTokenBudget.forRouteTarget(
                requestedLimit = 1_024,
                providerLimit = 65_536,
                reasoningModel = true,
            ),
        )
        assertEquals(
            2_048,
            AiTranslationTokenBudget.forRouteTarget(
                requestedLimit = 1_024,
                providerLimit = 2_048,
                reasoningModel = true,
            ),
        )
        assertEquals(
            1_024,
            AiTranslationTokenBudget.forRouteTarget(
                requestedLimit = 1_024,
                providerLimit = 65_536,
                reasoningModel = false,
            ),
        )
    }

}
