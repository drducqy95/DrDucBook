package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiRouteTargetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRouteSelectorTest {

    @Test
    fun `priority selects the lowest priority then preserves fallback order`() {
        val ordered = AiRouteSelector().order(
            profile = profile(strategy = AiRouteStrategy.PRIORITY),
            targets = listOf(target("slow", priority = 20), target("primary", priority = 0)),
            now = 100,
        )

        assertEquals(listOf("primary", "slow"), ordered.map { it.id })
    }

    @Test
    fun `round robin rotates the first target`() {
        val selector = AiRouteSelector()
        val targets = listOf(target("a"), target("b"), target("c"))

        val selected = List(4) {
            selector.order(profile(AiRouteStrategy.ROUND_ROBIN), targets, now = 100).first().id
        }

        assertEquals(listOf("a", "b", "c", "a"), selected)
    }

    @Test
    fun `round robin rotates only within active priority before fallback`() {
        val selector = AiRouteSelector()
        val targets = listOf(
            target("primary-key-1", priority = 0),
            target("primary-key-2", priority = 0),
            target("fallback-model", priority = 1),
        )

        val selected = List(4) {
            selector.order(profile(AiRouteStrategy.ROUND_ROBIN), targets, now = 100).first().id
        }

        assertEquals(
            listOf("primary-key-1", "primary-key-2", "primary-key-1", "primary-key-2"),
            selected,
        )
    }

    @Test
    fun `weighted round robin honors weights deterministically`() {
        val selector = AiRouteSelector()
        val targets = listOf(target("a", weight = 2), target("b", weight = 1))

        val selected = List(6) {
            selector.order(profile(AiRouteStrategy.WEIGHTED_ROUND_ROBIN), targets, now = 100).first().id
        }

        assertEquals(listOf("a", "a", "b", "a", "a", "b"), selected)
    }

    @Test
    fun `cooldown and disabled targets are excluded`() {
        val ordered = AiRouteSelector().order(
            profile = profile(AiRouteStrategy.PRIORITY),
            targets = listOf(
                target("disabled", enabled = false),
                target("cooldown", cooldownUntil = 101),
                target("ready"),
            ),
            now = 100,
        )

        assertEquals(listOf("ready"), ordered.map { it.id })
    }

    @Test
    fun `sticky session keeps its target and can be forgotten`() {
        val selector = AiRouteSelector()
        val route = profile(AiRouteStrategy.ROUND_ROBIN, sticky = true)
        val targets = listOf(target("a"), target("b"))

        assertEquals("a", selector.order(route, targets, 100, "chat-1").first().id)
        assertEquals("a", selector.order(route, targets, 100, "chat-1").first().id)
        selector.forgetSticky(route.id, "chat-1")
        assertEquals("b", selector.order(route, targets, 100, "chat-1").first().id)
        assertTrue(selector.order(route, targets, 100, "chat-2").isNotEmpty())
    }

    private fun profile(
        strategy: String,
        sticky: Boolean = false,
    ) = AiRouteProfileConfig(
        id = "route",
        name = "Route",
        taskType = "chat",
        strategy = strategy,
        stickySession = sticky,
    )

    private fun target(
        id: String,
        priority: Int = 0,
        weight: Int = 1,
        enabled: Boolean = true,
        cooldownUntil: Long = 0,
    ) = AiRouteTargetConfig(
        id = id,
        routeProfileId = "route",
        modelProfileId = "model-$id",
        priority = priority,
        weight = weight,
        enabled = enabled,
        cooldownUntil = cooldownUntil,
    )
}
