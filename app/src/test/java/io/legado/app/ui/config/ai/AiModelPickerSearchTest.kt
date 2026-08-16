package io.legado.app.ui.config.ai

import io.legado.app.ui.ai.router.normalizeAiRouterSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelPickerSearchTest {

    @Test
    fun normalizeAiRouterSearch_stripsAccentsAndDiacritics() {
        assertEquals("gpt-5", normalizeAiRouterSearch("GPT-5"))
        assertEquals("diep truong sinh", normalizeAiRouterSearch("Diệp Trường Sinh"))
        assertEquals("phien dich", normalizeAiRouterSearch("Phiên dịch"))
    }

    @Test
    fun modelSearchMatching_matchesWithoutAccentsAndCaseInsensitive() {
        val models = listOf(
            AiModelPickerOptionUi(
                id = "m1",
                providerName = "Google Gemini",
                modelName = "Gemini 2.5 Flash (Phiên dịch)",
                modelId = "gemini-2.5-flash",
            ),
            AiModelPickerOptionUi(
                id = "m2",
                providerName = "OpenAI",
                modelName = "GPT-4o Mini",
                modelId = "gpt-4o-mini",
            ),
            AiModelPickerOptionUi(
                id = "m3",
                providerName = "Anthropic",
                modelName = "Claude 3.5 Sonnet",
                modelId = "claude-3-5-sonnet",
                isMissing = true,
            ),
        )

        // Accent-insensitive search
        val filtered = filterAiModelPickerOptions(models, "phien dich")

        assertEquals(1, filtered.size)
        assertEquals("m1", filtered.first().id)

        // Missing badge flag
        val missingModel = models.first { it.id == "m3" }
        assertTrue(missingModel.isMissing)
        assertFalse(filtered.first().isMissing)
    }

    @Test
    fun modelSearchMatching_matchesProviderAndModelId() {
        val models = listOf(
            AiModelPickerOptionUi(
                id = "m1",
                providerName = "OpenCode Free",
                modelName = "DeepSeek Flash",
                modelId = "deepseek-v4-flash-free",
            ),
            AiModelPickerOptionUi(
                id = "m2",
                providerName = "Google Gemini",
                modelName = "Gemini Flash",
                modelId = "gemini-2.5-flash",
            ),
        )

        assertEquals(listOf("m1"), filterAiModelPickerOptions(models, "opencode").map { it.id })
        assertEquals(listOf("m2"), filterAiModelPickerOptions(models, "2.5").map { it.id })
        assertEquals(models, filterAiModelPickerOptions(models, ""))
    }
}
