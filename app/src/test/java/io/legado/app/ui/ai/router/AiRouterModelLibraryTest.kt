package io.legado.app.ui.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRouterModelLibraryTest {

    @Test
    fun blankSearchKeepsEveryDiscoveredGatewayModel() {
        val models = (0 until 474).map { index ->
            AiRouterModelUi(
                id = "profile_$index",
                providerId = "catalog_openai",
                label = "9router · model-$index",
            )
        }

        val visible = filterAiRouterLibraryModels(
            models = models,
            providerNameById = mapOf("catalog_openai" to "OpenAI API"),
            query = "",
        )

        assertEquals(474, visible.size)
        assertTrue(visible.any { it.id == "profile_473" })
    }

    @Test
    fun searchFindsModelBeyondFormerEightItemPreview() {
        val models = (0 until 474).map { index ->
            AiRouterModelUi(
                id = "profile_$index",
                providerId = "catalog_openai",
                label = if (index == 401) "9router · oc/mimo-v2.5-free" else "9router · model-$index",
            )
        }

        val visible = filterAiRouterLibraryModels(
            models = models,
            providerNameById = mapOf("catalog_openai" to "OpenAI API"),
            query = "mimo-v2.5-free",
        )

        assertEquals(listOf("profile_401"), visible.map(AiRouterModelUi::id))
    }

    @Test
    fun selectedModelIsSavedFirstWithoutDroppingDiscoveredModels() {
        val models = (0 until 474).map { index ->
            AiRouterModelOptionUi(
                id = "model-$index",
                name = "Model $index",
            )
        }

        val prioritized = prioritizeSelectedModel(models, "model-401")

        assertEquals(474, prioritized.size)
        assertEquals("model-401", prioritized.first().id)
    }

    @Test
    fun reopenedCompatibleOpenAiProfilePrefersKnownWorkingGatewayModel() {
        val models = listOf(
            AiRouterModelOptionUi("alicode/qwen", "Qwen"),
            AiRouterModelOptionUi("oc/mimo-v2.5-free", "MiMo"),
        )

        assertEquals(
            "oc/mimo-v2.5-free",
            preferredProviderEditorModel(models)?.id,
        )
    }
}
