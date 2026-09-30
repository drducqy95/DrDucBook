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

    @Test
    fun everyStageHasACompleteDefaultInstruction() {
        TranslationPromptStage.entries.forEach { stage ->
            val instruction = TranslationPromptStage.defaultInstruction(stage)
            assertTrue("${stage.name} must have a nonblank instruction", instruction.isNotBlank())
            assertTrue("${stage.name} must be more complete than its legacy prompt", instruction.length > TranslationPromptStage.legacyInstruction(stage).length)
        }
    }

    @Test
    fun defaultInstructionsContainStageSpecificGuardrails() {
        assertTrue(TranslationPromptStage.defaultInstruction(TranslationPromptStage.PREPARE).contains("RAW"))
        assertTrue(TranslationPromptStage.defaultInstruction(TranslationPromptStage.FILTER).contains("boilerplate"))
        assertTrue(TranslationPromptStage.defaultInstruction(TranslationPromptStage.DICTIONARY).contains("locked_dictionary"))
        assertTrue(TranslationPromptStage.defaultInstruction(TranslationPromptStage.TRANSLATE).contains("refined_segments"))
        assertTrue(TranslationPromptStage.defaultInstruction(TranslationPromptStage.RETRANSLATE).contains("previous attempt"))
    }
}
