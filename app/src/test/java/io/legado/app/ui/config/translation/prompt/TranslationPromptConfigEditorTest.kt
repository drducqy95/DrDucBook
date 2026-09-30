package io.legado.app.ui.config.translation.prompt

import io.legado.app.domain.model.TranslationPromptStage
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationPromptConfigEditorTest {

    @Test
    fun selectingAnotherStageLoadsThatStagesPrompt() {
        val filter = TranslationPromptItemUi(
            id = "filter-id",
            stage = TranslationPromptStage.FILTER,
            name = "Lọc nội dung",
            instruction = "Filter instruction",
            enabled = true,
            sortNumber = 0,
        )

        val editor = translationPromptEditorForStage(
            stage = TranslationPromptStage.FILTER,
            currentId = "prepare-id",
            items = listOf(filter),
        )

        assertEquals("filter-id", editor.id)
        assertEquals(TranslationPromptStage.FILTER, editor.stage)
        assertEquals("Lọc nội dung", editor.name)
        assertEquals("Filter instruction", editor.instruction)
    }

    @Test
    fun selectingStageWithoutExistingPromptUsesItsDefaultTemplate() {
        val editor = translationPromptEditorForStage(
            stage = TranslationPromptStage.RETRANSLATE,
            currentId = "prepare-id",
            items = emptyList(),
        )

        assertEquals(null, editor.id)
        assertEquals("Retranslate", editor.name)
        assertEquals(
            TranslationPromptStage.defaultInstruction(TranslationPromptStage.RETRANSLATE),
            editor.instruction,
        )
    }
}
