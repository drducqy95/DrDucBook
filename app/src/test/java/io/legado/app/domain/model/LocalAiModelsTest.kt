package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiModelsTest {

    @Test
    fun huaweiPuraClassUsesBoundedCpuProfile() {
        val profile = LocalAiRuntimePlanner.plan(
            device = LocalAiDeviceInfo(
                primaryAbi = "arm64-v8a",
                supportedAbis = setOf("arm64-v8a", "armeabi-v7a"),
                availableProcessors = 8,
                totalMemoryMb = 12_000,
                manufacturer = "HUAWEI",
                model = "Pura 70 Pro",
            ),
            requestedContextWindow = 8_192,
        )

        assertEquals(6, profile.threads)
        assertEquals(4_096, profile.contextWindow)
        assertEquals(768, profile.preferredChunkChars)
        assertEquals(0, profile.gpuLayers)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects32BitRuntimeForLargeGgufModel() {
        LocalAiRuntimePlanner.plan(
            LocalAiDeviceInfo("armeabi-v7a", setOf("armeabi-v7a"), 8, 8_000, "", ""),
            requestedContextWindow = 4_096,
        )
    }

    @Test
    fun localBudgetKeepsPromptSourceOutputAndSafetyInsideContext() {
        val budget = LocalAiTranslationBudgetPlanner.plan(
            contextWindow = 4_096,
            providerMaxOutputTokens = 4_096,
            configuredMaxOutputTokens = null,
            configuredMaxSourceChars = 10_000,
            preferredChunkChars = 768,
            adjacentContextChars = 160,
            fixedPromptChars = 1_800,
        )

        assertTrue(budget.maxSourceChars in 500..768)
        assertTrue(budget.maxOutputTokens in 1_000..1_600)
        assertEquals(409, budget.safetyTokens)
    }

    @Test
    fun localBudgetRespectsManualSmallChunkSetting() {
        val budget = LocalAiTranslationBudgetPlanner.plan(
            contextWindow = 4_096,
            providerMaxOutputTokens = 4_096,
            configuredMaxOutputTokens = 700,
            configuredMaxSourceChars = 120,
            preferredChunkChars = 768,
            adjacentContextChars = 160,
            fixedPromptChars = 1_000,
            sourceChars = 120,
        )

        assertEquals(120, budget.maxSourceChars)
        assertTrue(budget.maxOutputTokens <= 700)
        assertTrue(budget.adjacentContextChars <= 60)
    }
}
