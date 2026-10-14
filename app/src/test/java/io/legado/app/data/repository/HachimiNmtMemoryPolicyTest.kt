package io.legado.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HachimiNmtMemoryPolicyTest {

    @Test
    fun hachimiGreedySelectorSelectTopKReturnsRankedCandidates() {
        val vocabSize = 10
        val selector = HachimiGreedySelector(vocabularySize = vocabSize, initialTokens = listOf(1))
        val buffer = java.nio.FloatBuffer.wrap(
            floatArrayOf(
                1.0f,  // token 0
                2.0f,  // token 1
                5.0f,  // token 2
                3.0f,  // token 3
                10.0f, // token 4 (highest)
                0.5f,  // token 5
                8.0f,  // token 6 (second highest)
                4.0f,  // token 7
                7.0f,  // token 8 (third highest)
                0.1f   // token 9
            )
        )
        val top3 = selector.selectTopK(
            logits = buffer,
            generated = listOf(1),
            repetitionPenalty = 1.0f,
            noRepeatNgramSize = 0,
            eosToken = 2,
            eosPenalty = 0.0f,
            biasedTokens = IntArray(0),
            biasedScores = FloatArray(0),
            biasedCount = 0,
            k = 3,
        )
        assertEquals(3, top3.size)
        // Ranked descending by probability: token 4, then token 6, then token 8
        assertEquals(4, top3[0].token)
        assertEquals(6, top3[1].token)
        assertEquals(8, top3[2].token)
        // Log-softmax probabilities are <= 0f
        assertTrue(top3[0].score <= 0f)
        assertTrue(top3[1].score <= top3[0].score)
        assertTrue(top3[2].score <= top3[1].score)
    }

    @Test
    fun hachimiGreedySelectorCopyPreservesStateIndependently() {
        val vocabSize = 5
        val selector = HachimiGreedySelector(vocabularySize = vocabSize, initialTokens = listOf(1))
        val clone = selector.copy()
        clone.record(3)

        val buffer1 = java.nio.FloatBuffer.wrap(floatArrayOf(1f, 1f, 1f, 10f, 1f))
        val buffer2 = java.nio.FloatBuffer.wrap(floatArrayOf(1f, 1f, 1f, 10f, 1f))

        val scoreOrig = selector.select(buffer1, listOf(1), repetitionPenalty = 5.0f, noRepeatNgramSize = 0, eosToken = 2, eosPenalty = 0f, biasedTokens = IntArray(0), biasedScores = FloatArray(0), biasedCount = 0)
        val scoreClone = clone.select(buffer2, listOf(1), repetitionPenalty = 5.0f, noRepeatNgramSize = 0, eosToken = 2, eosPenalty = 0f, biasedTokens = IntArray(0), biasedScores = FloatArray(0), biasedCount = 0)

        assertEquals(3, scoreOrig)
        assertEquals(3, scoreClone)
    }

    @Test
    fun twoHundredFiftySixMbHeapUsesLowMemorySessionProfile() {
        assertTrue(
            shouldUseLowMemoryNmtProfile(
                systemReportsLowRam = false,
                memoryClassMb = 256,
            )
        )
    }

    @Test
    fun javaHeapClassDoesNotRejectNativeNmtWhenSystemHasEnoughMemory() {
        val required = requiredNmtSystemHeadroomBytes(
            modelBytes = 96L * MIB,
            systemLowMemoryThresholdBytes = 64L * MIB,
        )

        assertTrue(
            hasEnoughNmtSystemMemory(
                availableBytes = 512L * MIB,
                systemReportsLowMemory = false,
                requiredHeadroomBytes = required,
            )
        )
    }

    @Test
    fun systemLowMemorySignalStillRejectsModelLoad() {
        assertFalse(
            hasEnoughNmtSystemMemory(
                availableBytes = 2L * 1024L * MIB,
                systemReportsLowMemory = true,
                requiredHeadroomBytes = 160L * MIB,
            )
        )
    }

    @Test
    fun modelAndSystemThresholdBothContributeToRequiredHeadroom() {
        assertFalse(
            hasEnoughNmtSystemMemory(
                availableBytes = 191L * MIB,
                systemReportsLowMemory = false,
                requiredHeadroomBytes = requiredNmtSystemHeadroomBytes(
                    modelBytes = 128L * MIB,
                    systemLowMemoryThresholdBytes = 64L * MIB,
                ),
            )
        )
    }

    @Test
    fun pruneSubsumedConstraintsRemovesShorterConstraintWhenFullyCovered() {
        val c1 = EncodedConstraint(
            sourceKeys = listOf("玉"),
            target = "Ngọc",
            tokens = intArrayOf(10),
            required = true,
            startBias = 1.0f,
            matchEverySourceOccurrence = false,
        )
        val c2 = EncodedConstraint(
            sourceKeys = listOf("玉简"),
            target = "Ngọc giản",
            tokens = intArrayOf(10, 20),
            required = true,
            startBias = 1.0f,
            matchEverySourceOccurrence = false,
        )
        val source = "里面摆放着很多玉简类符文"
        val pruned = pruneSubsumedConstraints(source, listOf(c1, c2))
        assertEquals(1, pruned.size)
        assertEquals("Ngọc giản", pruned[0].target)
    }

    @Test
    fun pruneSubsumedConstraintsPreservesBothWhenShorterHasIndependentOccurrences() {
        val c1 = EncodedConstraint(
            sourceKeys = listOf("玉"),
            target = "Ngọc",
            tokens = intArrayOf(10),
            required = true,
            startBias = 1.0f,
            matchEverySourceOccurrence = false,
        )
        val c2 = EncodedConstraint(
            sourceKeys = listOf("玉简"),
            target = "Ngọc giản",
            tokens = intArrayOf(10, 20),
            required = true,
            startBias = 1.0f,
            matchEverySourceOccurrence = false,
        )
        val source = "一枚玉佩和很多玉简类符文"
        val pruned = pruneSubsumedConstraints(source, listOf(c1, c2))
        assertEquals(2, pruned.size)
    }

    @Test
    fun canonicalizeHachimiNameOutputsRemovesPunctuationSeparatedDuplicates() {
        val constraints = listOf(
            HachimiLexicalConstraint(
                sourceKeys = listOf("玉"),
                target = "Ngọc",
                required = true,
            )
        )
        val rawOutput = "Lạc Ngôn đi theo Lý Tư Tề, từ vách đá bước vào, thỉnh thoảng còn gặp vài chấp pháp giả ngoài áo choàng đen, đeo mặt nạ ngọc. Ngọc"
        val cleaned = canonicalizeHachimiNameOutputs(
            source = "戴着玉面具",
            translated = rawOutput,
            constraints = constraints
        )
        assertEquals(
            "Lạc Ngôn đi theo Lý Tư Tề, từ vách đá bước vào, thỉnh thoảng còn gặp vài chấp pháp giả ngoài áo choàng đen, đeo mặt nạ ngọc.",
            cleaned
        )
    }

    @Test
    fun canonicalizeHachimiNameOutputsRemovesTrailingOrphanedEcho() {
        val constraints = listOf(
            HachimiLexicalConstraint(
                sourceKeys = listOf("玉简"),
                target = "Ngọc giản",
                required = true,
            )
        )
        val rawOutput = "Cuối cùng, bọn hắn đi tới một không gian dưới lòng đất, bên trong bày rất nhiều ngọc giản loại phù văn. Ngọc giản"
        val cleaned = canonicalizeHachimiNameOutputs(
            source = "摆着很多玉简符文",
            translated = rawOutput,
            constraints = constraints
        )
        assertEquals(
            "Cuối cùng, bọn hắn đi tới một không gian dưới lòng đất, bên trong bày rất nhiều ngọc giản loại phù văn.",
            cleaned
        )
    }

    @Test
    fun encodedConstraintIsSatisfiedMatchesLowercaseVariant() {
        val c = EncodedConstraint(
            sourceKeys = listOf("玉简"),
            target = "Ngọc giản",
            tokens = intArrayOf(100, 200),
            tokenVariants = listOf(intArrayOf(100, 200), intArrayOf(101, 200)),
            required = true,
            startBias = 1.0f,
            matchEverySourceOccurrence = false,
        )
        val generated = listOf(1, 2, 101, 200, 3)
        assertTrue(c.isSatisfied(generated))
    }

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
