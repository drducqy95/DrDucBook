package io.legado.app.model.cache

import android.os.SystemClock
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

internal object DownloadPacingPolicy {

    fun intervalMillis(
        baseDelayMs: Int,
        jitterMs: Int,
        randomOffset: Int,
    ): Long {
        val safeBase = baseDelayMs.coerceAtLeast(0)
        val safeJitter = jitterMs.coerceAtLeast(0)
        return safeBase.toLong() + randomOffset.coerceIn(0, safeJitter)
    }
}

/**
 * Spaces chapter request starts per source. The lock is only held while waiting for the next
 * permitted start, so response bodies can still download with the configured concurrency.
 */
object DownloadPacingGate {

    private val sourceLocks = ConcurrentHashMap<String, Mutex>()
    private val lastRequestStarts = ConcurrentHashMap<String, Long>()

    suspend fun awaitTurn(sourceKey: String) {
        val baseDelayMs = DownloadCacheConfig.requestDelayMs.coerceAtLeast(0)
        val jitterMs = DownloadCacheConfig.requestJitterMs.coerceAtLeast(0)
        if (baseDelayMs == 0 && jitterMs == 0) return

        val normalizedKey = sourceKey.trim().ifEmpty { GLOBAL_SOURCE_KEY }
        val mutex = sourceLocks.getOrPut(normalizedKey) { Mutex() }
        mutex.withLock {
            val randomOffset = if (jitterMs == 0) 0 else Random.nextInt(jitterMs + 1)
            val interval = DownloadPacingPolicy.intervalMillis(
                baseDelayMs = baseDelayMs,
                jitterMs = jitterMs,
                randomOffset = randomOffset,
            )
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastRequestStarts.getOrDefault(normalizedKey, 0L)
            val remaining = interval - elapsed
            if (remaining > 0) delay(remaining)
            lastRequestStarts[normalizedKey] = SystemClock.elapsedRealtime()
        }
    }

    internal fun clearForTests() {
        sourceLocks.clear()
        lastRequestStarts.clear()
    }

    private const val GLOBAL_SOURCE_KEY = "__default_source__"
}
