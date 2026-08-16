package io.legado.app.ui.ai.router

import io.legado.app.domain.model.AiProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderEndpointRebaseTest {

    @Test
    fun changingPresetBaseUrlMovesDerivedModelsUrlToSameServer() {
        assertEquals(
            "http://localhost:20128/v1/models",
            rebaseDerivedModelsUrl(
                previousBaseUrl = "https://api.openai.com/v1",
                newBaseUrl = "http://localhost:20128/v1",
                currentModelsUrl = "https://api.openai.com/v1/models",
            )
        )
    }

    @Test
    fun changingBaseUrlPreservesExplicitModelsUrlOnAnotherServer() {
        assertEquals(
            "https://models.example/catalog",
            rebaseDerivedModelsUrl(
                previousBaseUrl = "https://api.openai.com/v1",
                newBaseUrl = "http://localhost:20128/v1",
                currentModelsUrl = "https://models.example/catalog",
            )
        )
    }

    @Test
    fun clearingBaseUrlAlsoClearsItsDerivedModelsUrl() {
        assertEquals(
            "",
            rebaseDerivedModelsUrl(
                previousBaseUrl = "https://api.openai.com/v1/",
                newBaseUrl = "",
                currentModelsUrl = "https://api.openai.com/v1/models",
            )
        )
    }

    @Test
    fun movingOpenAiResponsesPresetToLocalhostUsesCompatibleChatProtocol() {
        assertEquals(
            AiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolForRebasedEndpoint(
                currentProtocol = AiProtocol.OPENAI_RESPONSES,
                previousBaseUrl = "https://api.openai.com/v1",
                newBaseUrl = "http://localhost:20128/v1",
            )
        )
    }

    @Test
    fun officialOpenAiEndpointKeepsResponsesProtocol() {
        assertEquals(
            AiProtocol.OPENAI_RESPONSES,
            protocolForRebasedEndpoint(
                currentProtocol = AiProtocol.OPENAI_RESPONSES,
                previousBaseUrl = "https://api.openai.com/v1",
                newBaseUrl = "https://api.openai.com/v1/",
            )
        )
    }

    @Test
    fun explicitNonResponsesProtocolIsPreserved() {
        assertEquals(
            AiProtocol.ANTHROPIC_MESSAGES,
            protocolForRebasedEndpoint(
                currentProtocol = AiProtocol.ANTHROPIC_MESSAGES,
                previousBaseUrl = "https://api.openai.com/v1",
                newBaseUrl = "http://localhost:20128/v1",
            )
        )
    }

    @Test
    fun staleOpenAiCatalogModelIsClearedForCompatibleGateway() {
        assertEquals(
            true,
            shouldClearCatalogModelForEndpoint(
                catalogProtocol = AiProtocol.OPENAI_RESPONSES,
                catalogBaseUrl = "https://api.openai.com/v1",
                newBaseUrl = "http://localhost:20128/v1",
                selectedModelId = "gpt-5.4-mini",
                catalogModelIds = setOf("gpt-5.4-mini", "gpt-5.4"),
            )
        )
    }

    @Test
    fun discoveredGatewayModelIsPreserved() {
        assertEquals(
            false,
            shouldClearCatalogModelForEndpoint(
                catalogProtocol = AiProtocol.OPENAI_RESPONSES,
                catalogBaseUrl = "https://api.openai.com/v1",
                newBaseUrl = "http://localhost:20128/v1",
                selectedModelId = "alicode-intl/qwen3.5-plus",
                catalogModelIds = setOf("gpt-5.4-mini", "gpt-5.4"),
            )
        )
    }
}
