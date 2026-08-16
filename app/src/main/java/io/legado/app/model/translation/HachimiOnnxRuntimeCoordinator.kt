package io.legado.app.model.translation

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong

internal object HachimiOnnxRuntimeCoordinator {
    val accessMutex = Mutex()

    private val modelGeneration = AtomicLong(0L)

    fun currentGeneration(): Long = modelGeneration.get()

    fun markModelChanged(): Long = modelGeneration.incrementAndGet()
}
