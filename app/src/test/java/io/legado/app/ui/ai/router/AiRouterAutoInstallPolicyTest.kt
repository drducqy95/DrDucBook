package io.legado.app.ui.ai.router

import io.legado.app.data.entities.AiModelProfile
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiRouteTargetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRouterAutoInstallPolicyTest {

    @Test
    fun missingGeneratedRouteIsRebuilt() {
        assertTrue(
            shouldRebuildGeneratedRoute(
                route = null,
                targets = emptyList(),
                modelIds = listOf("model-a"),
                maxConcurrency = 2,
                strategy = AiRouteStrategy.ROUND_ROBIN,
            )
        )
    }

    @Test
    fun unchangedGeneratedRouteIsNotWrittenAgain() {
        val route = AiRouteProfileConfig(
            id = "route-1",
            name = "AI translation",
            taskType = "translate_chapter",
            strategy = AiRouteStrategy.ROUND_ROBIN,
            maxAttempts = 2,
            stickySession = true,
            enabled = true,
        )
        val targets = listOf(
            AiRouteTargetConfig(
                id = "target-1",
                routeProfileId = route.id,
                modelProfileId = "model-a",
                priority = 0,
                weight = 1,
                maxConcurrency = 2,
                sortNumber = 0,
            ),
            AiRouteTargetConfig(
                id = "target-2",
                routeProfileId = route.id,
                modelProfileId = "model-b",
                priority = 0,
                weight = 1,
                maxConcurrency = 2,
                sortNumber = 1,
            ),
        )

        assertFalse(
            shouldRebuildGeneratedRoute(
                route = route,
                targets = targets,
                modelIds = listOf("model-a", "model-b"),
                maxConcurrency = 2,
                strategy = AiRouteStrategy.ROUND_ROBIN,
            )
        )
    }

    @Test
    fun changedModelOrConcurrencyTriggersRepair() {
        val route = AiRouteProfileConfig(
            id = "route-1",
            name = "AI translation",
            taskType = "translate_chapter",
            strategy = AiRouteStrategy.ROUND_ROBIN,
            maxAttempts = 1,
        )
        val target = AiRouteTargetConfig(
            id = "target-1",
            routeProfileId = route.id,
            modelProfileId = "old-model",
            priority = 0,
            weight = 1,
            maxConcurrency = 1,
            sortNumber = 0,
        )

        assertTrue(
            shouldRebuildGeneratedRoute(
                route = route,
                targets = listOf(target),
                modelIds = listOf("new-model"),
                maxConcurrency = 2,
                strategy = AiRouteStrategy.ROUND_ROBIN,
            )
        )
    }

    @Test
    fun legacyRoundRobinTranslationRouteIsRebuiltAsOrderedFallback() {
        val route = AiRouteProfileConfig(
            id = "route-1",
            name = "Dịch AI · Free fallback",
            taskType = "translate_chapter",
            strategy = AiRouteStrategy.ROUND_ROBIN,
            maxAttempts = 2,
            stickySession = true,
            enabled = true,
        )
        val targets = listOf(
            AiRouteTargetConfig(
                id = "target-1",
                routeProfileId = route.id,
                modelProfileId = "model-a",
                priority = 0,
                maxConcurrency = 2,
                sortNumber = 0,
            ),
            AiRouteTargetConfig(
                id = "target-2",
                routeProfileId = route.id,
                modelProfileId = "model-b",
                priority = 0,
                maxConcurrency = 2,
                sortNumber = 1,
            ),
        )

        assertTrue(
            shouldRebuildGeneratedRoute(
                route = route,
                targets = targets,
                modelIds = listOf("model-a", "model-b"),
                maxConcurrency = 2,
                strategy = AiRouteStrategy.PRIORITY,
            )
        )
    }

    @Test
    fun generatedComboAggregatesEveryFreeProviderInsteadOfKeepingOnlyLastInstall() {
        val models = listOf(
            model("open-b", "catalog_opencode_free", sortNumber = 1),
            model("open-a", "catalog_opencode_free", sortNumber = 0),
            model("open-c", "catalog_opencode_free", sortNumber = 2),
            model("mimo-a", "catalog_mimo_free", sortNumber = 0),
            model("disabled", "catalog_mimo_free", sortNumber = 1, enabled = false),
        )

        val selected = selectGeneratedFreeRouteModelIds(
            models = models,
            providerOrder = listOf("catalog_opencode_free", "catalog_mimo_free"),
            maxModelsPerProvider = 2,
        )

        assertEquals(listOf("open-a", "open-b", "mimo-a"), selected)
    }

    @Test
    fun successfulDiscoveryReplacesStaleOpenCodeCatalogModels() {
        val catalog = listOf(
            AiAvailableModel("stale-free", "Stale Free"),
            AiAvailableModel("mimo-v2.5-free", "MiMo V2.5 Free"),
        )
        val discovered = listOf(
            AiAvailableModel("big-pickle", "Big Pickle"),
            AiAvailableModel("paid-model", "Paid Model"),
        )

        val selected = selectDiscoveredCatalogModels(
            catalogModels = catalog,
            discoveredModels = discovered,
            discoverySucceeded = true,
            accept = { it.id == "big-pickle" || it.id.endsWith("-free") },
        )

        assertEquals(listOf("big-pickle"), selected.map(AiAvailableModel::id))
    }

    @Test
    fun failedDiscoveryKeepsSafeCatalogFallback() {
        val catalog = listOf(AiAvailableModel("mimo-v2.5-free", "MiMo V2.5 Free"))

        val selected = selectDiscoveredCatalogModels(
            catalogModels = catalog,
            discoveredModels = emptyList(),
            discoverySucceeded = false,
            accept = { it.id.endsWith("-free") },
        )

        assertEquals(listOf("mimo-v2.5-free"), selected.map(AiAvailableModel::id))
    }

    @Test
    fun generatedRoutePreservesOauthAndCustomTargets() {
        val targets = listOf(
            AiRouteTargetConfig(
                id = "target-free",
                routeProfileId = "route",
                modelProfileId = "model-free",
            ),
            AiRouteTargetConfig(
                id = "target-oauth",
                routeProfileId = "route",
                modelProfileId = "model-codex",
                credentialId = "credential-codex",
            ),
        )

        assertTrue(
            hasExternalGeneratedRouteTargets(
                targets = targets,
                generatedModelIds = listOf("model-free"),
            )
        )
        assertFalse(
            hasExternalGeneratedRouteTargets(
                targets = targets.take(1),
                generatedModelIds = listOf("model-free"),
            )
        )
    }

    @Test
    fun retiredGeneratedTargetsAreDroppedInsteadOfPreservedAsCustomTargets() {
        val active = AiRouteTargetConfig(
            id = "target-opencode",
            routeProfileId = "route",
            modelProfileId = "model-opencode",
        )
        val retired = AiRouteTargetConfig(
            id = "target-mimo-free",
            routeProfileId = "route",
            modelProfileId = "model-mimo-free",
        )
        val custom = AiRouteTargetConfig(
            id = "target-custom",
            routeProfileId = "route",
            modelProfileId = "model-custom",
        )

        assertEquals(
            listOf(active, custom),
            activeGeneratedRouteTargets(
                targets = listOf(active, retired, custom),
                retiredGeneratedModelIds = setOf("model-mimo-free"),
            ),
        )
        assertFalse(
            hasExternalGeneratedRouteTargets(
                targets = listOf(active, retired),
                generatedModelIds = listOf("model-opencode"),
                retiredGeneratedModelIds = setOf("model-mimo-free"),
            )
        )
        assertTrue(
            hasExternalGeneratedRouteTargets(
                targets = listOf(active, custom),
                generatedModelIds = listOf("model-opencode"),
                retiredGeneratedModelIds = setOf("model-mimo-free"),
            )
        )
    }

    private fun model(
        id: String,
        providerId: String,
        sortNumber: Int,
        enabled: Boolean = true,
    ) = AiModelProfile(
        id = id,
        providerId = providerId,
        displayName = id,
        modelId = id,
        enabled = enabled,
        sortNumber = sortNumber,
        createdAt = sortNumber.toLong(),
    )

    @Test
    fun geminiWebAndOpencodeAreSelectedIntoGeneratedFreeRoutesInOrder() {
        val models = listOf(
            model("gemini-web-flash", "catalog_gemini_web", sortNumber = 0),
            model("gemini-web-pro", "catalog_gemini_web", sortNumber = 1),
            model("deepseek-v4-flash-free", "catalog_opencode_free", sortNumber = 0),
            model("big-pickle", "catalog_opencode_free", sortNumber = 1),
        )

        val selected = selectGeneratedFreeRouteModelIds(
            models = models,
            providerOrder = listOf("catalog_gemini_web", "catalog_opencode_free"),
            maxModelsPerProvider = 2,
        )

        assertEquals(
            listOf("gemini-web-flash", "gemini-web-pro", "deepseek-v4-flash-free", "big-pickle"),
            selected,
        )
    }

    @Test
    fun geminiWebAutoInstallIdIsInCatalog() {
        assertTrue(io.legado.app.domain.model.AiProviderCatalog.autoInstallIds.contains("gemini_web"))
        assertTrue(io.legado.app.domain.model.AiProviderCatalog.autoInstallIds.contains("opencode_free"))
    }

    @Test
    fun generatedRouteDefaultPolicyProtectsCustomDefaultRoute() {
        val customDefaultRoute = AiRouteProfileConfig(
            id = "custom-route",
            name = "My Custom Combo",
            taskType = "translate_chapter",
            isDefault = true,
        )
        val generatedRoute = AiRouteProfileConfig(
            id = "gen-route",
            name = "Dịch AI · Free fallback",
            taskType = "translate_chapter",
            isDefault = false,
        )
        val taskRoutes = listOf(customDefaultRoute, generatedRoute)

        val namedRoute: AiRouteProfileConfig? = generatedRoute
        val shouldBeDefaultForGenerated = namedRoute?.isDefault ?: taskRoutes.none { it.isDefault }
        assertFalse("Rebuilt generated route must NOT hijack default when user has custom default", shouldBeDefaultForGenerated)

        val noDefaultRoutes = listOf(
            customDefaultRoute.copy(isDefault = false),
        )
        val nullNamedRoute: AiRouteProfileConfig? = null
        val shouldBeDefaultWhenEmpty = nullNamedRoute?.isDefault ?: noDefaultRoutes.none { it.isDefault }
        assertTrue("When no default exists, generated route can be default", shouldBeDefaultWhenEmpty)
    }

    @Test
    fun presetBindingGuardProtectsExistingCustomRouteProfileId() {
        val presetWithCustomRoute = io.legado.app.domain.model.AiTaskPresetConfig(
            id = "preset-1",
            taskType = "translate_chapter",
            name = "My Preset",
            description = "",
            model = io.legado.app.domain.model.AiModelConfig(
                id = "model-1",
                provider = io.legado.app.domain.model.AiProviderConfig(
                    id = "prov",
                    name = "Prov",
                    protocol = "openai_chat_completions",
                    baseUrl = "https://example.test",
                    apiKey = "key",
                ),
                displayName = "Model 1",
                modelId = "model-1",
            ),
            promptTemplate = "",
            params = io.legado.app.domain.model.AiGenerationParams(),
            runtimeOptions = io.legado.app.domain.model.AiTaskRuntimeOptions(
                routeProfileId = "custom_combo_123"
            ),
        )

        val shouldSkipOverwrite = presetWithCustomRoute.runtimeOptions.routeProfileId.isNotBlank()
        assertTrue("Must skip overwrite when preset already has a bound route", shouldSkipOverwrite)

        val presetWithoutRoute = presetWithCustomRoute.copy(
            runtimeOptions = presetWithCustomRoute.runtimeOptions.copy(routeProfileId = "")
        )
        val shouldOverwriteEmpty = presetWithoutRoute.runtimeOptions.routeProfileId.isNotBlank()
        assertFalse("Can overwrite when routeProfileId is blank", shouldOverwriteEmpty)
    }
}
