package io.legado.app.domain.model

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptPresetTransferTest {

    @Test
    fun roundTripKeepsPromptRuntimeAndPortableModelIdentity() {
        val source = AiPromptPresetTransferFile(
            presets = listOf(
                AiPromptPresetTransfer(
                    taskType = AiTaskType.TRANSLATE_CHAPTER,
                    name = "Dịch văn học",
                    description = "Dành cho truyện hiện đại.",
                    providerName = "Gemini",
                    modelId = "gemini-3.1-flash-lite",
                    modelDisplayName = "Gemini Flash Lite",
                    promptTemplate = "Giữ nguyên bố cục và thuật ngữ.",
                    params = AiGenerationParams(
                        temperature = 0.2f,
                        maxOutputTokens = 8_192,
                        topP = 0.9f,
                        topK = 20,
                        repetitionPenalty = 1.05f,
                    ),
                    runtimeOptions = AiTaskRuntimeOptions(
                        targetLanguage = "vi",
                        maxInputChars = 1_000,
                        concurrentRequests = 2,
                        retryCount = 1,
                    ),
                    makeDefault = true,
                )
            )
        )

        val json = GSON.toJson(source)
        val restored = GSON.fromJson(json, AiPromptPresetTransferFile::class.java)
        val preset = restored.presets.orEmpty().single()

        assertEquals(AiPromptPresetTransferFile.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
        assertEquals("gemini-3.1-flash-lite", preset.modelId)
        assertEquals("Dành cho truyện hiện đại.", preset.description)
        assertEquals("Giữ nguyên bố cục và thuật ngữ.", preset.promptTemplate)
        assertEquals(20, preset.params?.topK)
        assertEquals(1.05f, preset.params?.repetitionPenalty)
        assertEquals(1_000, preset.runtimeOptions?.maxInputChars)
        assertTrue(preset.makeDefault == true)
        assertFalse(json.contains("apiKey", ignoreCase = true))
    }
}
