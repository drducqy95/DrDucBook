package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptCatalogTest {

    @Test
    fun everySupportedTaskHasAnEditableStarterTemplate() {
        AiPromptCatalog.supportedTaskTypes.forEach { taskType ->
            val templates = AiPromptCatalog.templates.filter { it.taskType == taskType }
            assertTrue("Missing starter template for $taskType", templates.isNotEmpty())
            assertTrue(templates.all { it.name.isNotBlank() && it.prompt.isNotBlank() })
        }
    }

    @Test
    fun templateIdsAreStableAndUnique() {
        val ids = AiPromptCatalog.templates.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.matches(Regex("[a-z0-9_]+")) })
    }

    @Test
    fun contextTranslationTemplatesRetainMandatoryBasePolicy() {
        val translations = AiPromptCatalog.templates.filter {
            it.taskType == AiTaskType.TRANSLATE_CHAPTER
        }
        assertTrue(translations.size >= 6)
        translations.forEach { template ->
            assertTrue(
                "${template.id} lost the mandatory translation policy",
                template.prompt.contains(TranslationConstants.DEFAULT_PROMPT),
            )
        }
    }

    @Test
    fun mandatoryPolicyMatchesTheActualLegadoTranslationPayload() {
        val prompt = TranslationConstants.DEFAULT_PROMPT

        assertTrue("The standard translation prompt regressed in size", prompt.length < 1_600)
        assertTrue(prompt.contains("previous_context"))
        assertTrue(prompt.contains("next_context"))
        assertTrue(prompt.contains("locked_dictionary"))
        assertTrue(prompt.contains("raw_segments"))
        assertTrue(prompt.contains("QT"))
        assertTrue(prompt.contains("refined_segments"))
        assertTrue(prompt.contains("new_entities"))
        assertTrue(prompt.contains("No Markdown", ignoreCase = true))
        assertTrue(prompt.contains("no [result]/[dictionary]", ignoreCase = true))
        assertTrue(TranslationConstants.OUTPUT_FORMAT.length < 280)
        assertTrue(TranslationConstants.OUTPUT_FORMAT.contains("refined_segments"))
    }
}
