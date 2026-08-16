package io.legado.app.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelRegistryTest {

    @Test
    fun deepSeekV4UsesReasoningBudget() {
        assertTrue(
            AiCapability.REASONING in
                AiModelRegistry.inferCapabilities("deepseek-v4-flash-free")
        )
    }

    @Test
    fun openCodeBigPickleUsesReasoningBudget() {
        assertTrue(
            AiCapability.REASONING in
                AiModelRegistry.inferCapabilities("big-pickle")
        )
    }
}
