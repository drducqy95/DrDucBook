package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPromptStageTest {

    @Test
    fun taskType_roundTripsForEveryAiPromptStage() {
        TranslationPromptStage.entries.forEach { stage ->
            assertEquals(stage, TranslationPromptStage.fromTaskType(stage.taskType))
        }
    }

    @Test
    fun fromTaskType_rejectsNonAiPromptTaskTypes() {
        assertNull(TranslationPromptStage.fromTaskType("translation"))
        assertNull(TranslationPromptStage.fromTaskType("nmt:translate"))
        assertNull(TranslationPromptStage.fromTaskType("translation_prompt:unknown"))
    }

    @Test
    fun activeStages_matchNormalAndRetranslateRequests() {
        val normal = activeTranslationPromptStages(includeRetranslateStage = false)
        val retranslating = activeTranslationPromptStages(includeRetranslateStage = true)

        assertFalse(normal.contains(TranslationPromptStage.RETRANSLATE))
        assertTrue(retranslating.contains(TranslationPromptStage.RETRANSLATE))
        assertEquals(
            TranslationPromptStage.entries.filterNot {
                it == TranslationPromptStage.RETRANSLATE
            },
            normal,
        )
        assertEquals(TranslationPromptStage.entries, retranslating)
    }
}
