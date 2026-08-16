package io.legado.app.ui.ai.router

import io.legado.app.domain.model.AiConnectionStatus
import io.legado.app.domain.model.AiProviderCatalog
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRouterDashboardMapperTest {

    @Test
    fun mapsOpenCodeAndMiMoVariantsToStableProviderFamilies() {
        val entries = listOfNotNull(
            AiProviderCatalog.byId("opencode_free"),
            AiProviderCatalog.byId("opencode_go"),
            AiProviderCatalog.byId("mimo_free"),
            AiProviderCatalog.byId("xiaomi_mimo_token_plan_sgp"),
            AiProviderCatalog.byId("local_gguf"),
        )

        val items = buildProviderDashboardItems(
            catalogEntries = entries,
            oauthProviders = emptyList(),
            providers = emptyList(),
            models = emptyList(),
            credentials = emptyList(),
        )

        assertEquals(AiRouterProviderFamily.OPENCODE, items.first { it.id == "opencode_free" }.familyId)
        assertEquals("Free Console", items.first { it.id == "opencode_free" }.connectionMode)
        assertEquals(AiRouterProviderFamily.OPENCODE, items.first { it.id == "opencode_go" }.familyId)
        assertEquals("Go/API", items.first { it.id == "opencode_go" }.connectionMode)
        assertEquals(AiRouterProviderFamily.MIMO, items.first { it.id == "mimo_free" }.familyId)
        assertEquals(AiRouterProviderFamily.MIMO, items.first { it.id == "xiaomi_mimo_token_plan_sgp" }.familyId)
        assertEquals("Token Plan", items.first { it.id == "xiaomi_mimo_token_plan_sgp" }.connectionMode)
        assertEquals(AiRouterProviderFamily.LOCAL_GGUF, items.first { it.id == "local_gguf" }.familyId)
        assertEquals("Local file", items.first { it.id == "local_gguf" }.connectionMode)
    }

    @Test
    fun providerSearchIgnoresCaseAndVietnameseAccents() {
        val item = AiRouterProviderDashboardItemUi(
            id = "oauth_codex",
            name = "Đăng nhập Codex",
            familyId = "codex",
            familyName = "Codex",
            connectionMode = "OAuth",
            category = AiRouterProviderFilter.OAUTH,
            authLabel = "OAuth",
            status = AiConnectionStatus.LOGIN_REQUIRED,
            statusLabel = "Cần đăng nhập",
            installed = true,
            capabilityLabels = persistentListOf("llm"),
            notice = "Cần đăng nhập lại để refresh token",
        )

        val filtered = filterProviderDashboardItems(
            items = listOf(item),
            query = "dang nhap lai",
            filter = AiRouterProviderFilter.ALL,
        )

        assertEquals(listOf(item), filtered)
        assertEquals("dang nhap codex", normalizeAiRouterSearch("Đăng nhập Codex"))
    }

    @Test
    fun providerFilterKeepsSelectionStableAcrossSearch() {
        val readyFree = AiRouterProviderDashboardItemUi(
            id = "opencode_free",
            name = "OpenCode Free",
            familyId = AiRouterProviderFamily.OPENCODE,
            familyName = "OpenCode",
            connectionMode = "Free Console",
            category = "free",
            authLabel = "No key",
            status = AiConnectionStatus.READY,
            statusLabel = "Sẵn sàng",
            installed = true,
        )
        val api = readyFree.copy(
            id = "openrouter",
            name = "OpenRouter",
            category = "free_tier",
            connectionMode = "Free tier",
        )

        val state = AiRouterUiState(
            providerSearchQuery = "open",
            providerFilter = AiRouterProviderFilter.FREE,
            providerDashboardItems = persistentListOf(readyFree, api),
        ).withProviderDashboardFilters()

        assertEquals(AiRouterProviderFilter.FREE, state.providerFilter)
        assertEquals(listOf(readyFree), state.filteredProviderDashboardItems)
        assertTrue(state.providerFilters.any { it.id == AiRouterProviderFilter.FREE && it.count == 1 })
    }
}
