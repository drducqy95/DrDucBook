package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiRouteProfileConfig
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiRouteTargetConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Stateful, thread-safe route ordering. Health persistence belongs to the repository;
 * this class only decides the attempt order for one request.
 */
class AiRouteSelector {

    private val cursors = ConcurrentHashMap<String, AtomicLong>()
    private val stickyTargets = ConcurrentHashMap<String, String>()

    fun order(
        profile: AiRouteProfileConfig,
        targets: List<AiRouteTargetConfig>,
        now: Long,
        sessionKey: String? = null,
    ): List<AiRouteTargetConfig> {
        val available = targets.asSequence()
            .filter(AiRouteTargetConfig::enabled)
            .filter { it.cooldownUntil <= now }
            .sortedWith(targetComparator)
            .toList()
        if (available.isEmpty()) return emptyList()

        val stickyKey = sessionKey
            ?.takeIf { profile.stickySession && it.isNotBlank() }
            ?.let { "${profile.id}:$it" }
        val sticky = stickyKey
            ?.let(stickyTargets::get)
            ?.let { id -> available.firstOrNull { it.id == id } }

        // A route is a priority ladder, not a flat list. Targets at the lowest available
        // priority form the active pool (normally several API keys for one model); higher
        // priorities are only fallback targets. This keeps a book/session on one model while
        // still rotating credentials inside that model's pool.
        val activePriority = available.first().priority
        val activePool = available.filter { it.priority == activePriority }
        val selected = sticky ?: when (profile.strategy) {
            AiRouteStrategy.ROUND_ROBIN -> selectRoundRobin(profile.id, activePool)
            AiRouteStrategy.WEIGHTED_ROUND_ROBIN -> selectWeighted(profile.id, activePool)
            else -> activePool.first()
        }
        stickyKey?.let { stickyTargets[it] = selected.id }
        return listOf(selected) + available.filterNot { it.id == selected.id }
    }

    fun forgetSticky(profileId: String, sessionKey: String?) {
        sessionKey?.takeIf(String::isNotBlank)?.let {
            stickyTargets.remove("$profileId:$it")
        }
    }

    private fun selectRoundRobin(
        profileId: String,
        targets: List<AiRouteTargetConfig>,
    ): AiRouteTargetConfig {
        val cursor = cursors.getOrPut(profileId) { AtomicLong() }.getAndIncrement()
        return targets[Math.floorMod(cursor, targets.size.toLong()).toInt()]
    }

    private fun selectWeighted(
        profileId: String,
        targets: List<AiRouteTargetConfig>,
    ): AiRouteTargetConfig {
        val totalWeight = targets.sumOf { it.weight.coerceIn(1, MAX_WEIGHT) }
        val cursor = cursors.getOrPut(profileId) { AtomicLong() }.getAndIncrement()
        var slot = Math.floorMod(cursor, totalWeight.toLong()).toInt()
        targets.forEach { target ->
            slot -= target.weight.coerceIn(1, MAX_WEIGHT)
            if (slot < 0) return target
        }
        return targets.last()
    }

    private companion object {
        const val MAX_WEIGHT = 100

        val targetComparator = compareBy<AiRouteTargetConfig>(
            AiRouteTargetConfig::priority,
            AiRouteTargetConfig::sortNumber,
            AiRouteTargetConfig::id,
        )
    }
}
