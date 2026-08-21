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

        assertTrue("Prompt size ${prompt.length} >= 1600", prompt.length < 1_600)
        assertTrue("Missing previous_context", prompt.contains("previous_context"))
        assertTrue("Missing next_context", prompt.contains("next_context"))
        assertTrue("Missing locked_dictionary", prompt.contains("locked_dictionary"))
        assertTrue("Missing raw_segments", prompt.contains("raw_segments"))
        assertTrue("Missing QT", prompt.contains("QT"))
        assertTrue("Missing refined_segments", prompt.contains("refined_segments"))
        assertTrue("Missing new_entities", prompt.contains("new_entities"))
        assertTrue("Missing No Markdown", prompt.contains("No Markdown", ignoreCase = true))
        assertTrue("Missing no [result]/[dictionary]", prompt.contains("no [result]/[dictionary]", ignoreCase = true))
        assertTrue("OUTPUT_FORMAT length ${TranslationConstants.OUTPUT_FORMAT.length} >= 280", TranslationConstants.OUTPUT_FORMAT.length < 280)
        assertTrue("OUTPUT_FORMAT missing refined_segments", TranslationConstants.OUTPUT_FORMAT.contains("refined_segments"))
    }
}
