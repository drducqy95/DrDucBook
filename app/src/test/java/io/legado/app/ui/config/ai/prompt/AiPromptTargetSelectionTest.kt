package io.legado.app.ui.config.ai.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiPromptTargetSelectionTest {

    private val models = listOf(
        AiPromptModelOptionUi(
            id = "model_1",
            providerName = "Provider",
            modelName = "Model 1",
            modelId = "model-1",
        )
    )

    private val route = AiPromptRouteOptionUi(
        id = "route_1",
        taskType = "translate_chapter",
        name = "Translation fallback",
        targetCount = 2,
        primaryModelProfileId = "model_1",
    )

    @Test
    fun decodeSupportsModelRouteAndDefaultSelections() {
        assertEquals(
            AiPromptTargetSelection.Model("model_1"),
            decodeAiPromptTargetSelection("router:model:model_1"),
        )
        assertEquals(
            AiPromptTargetSelection.Route("route_1"),
            decodeAiPromptTargetSelection("router:route:route_1"),
        )
        assertEquals(
            AiPromptTargetSelection.Default,
            decodeAiPromptTargetSelection(AI_PROMPT_SELECTION_DEFAULT),
        )
        assertNull(decodeAiPromptTargetSelection("router:model:"))
    }

    @Test
    fun modelOnlyTargetIsValidWithoutCombo() {
        val editor = AiPromptPresetEditorUi(
            taskType = "translate_chapter",
            modelProfileId = "model_1",
        )

        assertNull(validateAiPromptTargetSelection(editor, models, emptyList()))
    }

    @Test
    fun comboTargetIsValidWithoutSeparateModelSelection() {
        val editor = AiPromptPresetEditorUi(
            taskType = "translate_chapter",
            routeProfileId = "route_1",
        )

        assertNull(validateAiPromptTargetSelection(editor, emptyList(), listOf(route)))
    }

    @Test
    fun emptyOrWrongComboIsRejected() {
        val wrongTaskEditor = AiPromptPresetEditorUi(
            taskType = "chat",
            routeProfileId = "route_1",
        )
        assertEquals(
            AiPromptTargetValidationError.ROUTE_INVALID,
            validateAiPromptTargetSelection(wrongTaskEditor, models, listOf(route)),
        )

        val emptyRoute = route.copy(targetCount = 0)
        val emptyEditor = AiPromptPresetEditorUi(
            taskType = "translate_chapter",
            routeProfileId = "route_1",
        )
        assertEquals(
            AiPromptTargetValidationError.ROUTE_EMPTY,
            validateAiPromptTargetSelection(emptyEditor, models, listOf(emptyRoute)),
        )
    }
}
