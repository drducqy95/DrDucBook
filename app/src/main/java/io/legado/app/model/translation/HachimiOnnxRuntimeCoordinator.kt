package io.legado.app.model.translation

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong

internal object HachimiOnnxRuntimeCoordinator {
    val accessMutex = Mutex()

    private val modelGeneration = AtomicLong(0L)
    @Volatile private var activeModelId: String = HachimiOnnxModelRegistry.DEFAULT_MODEL_ID

    fun currentGeneration(): Long = modelGeneration.get()

    fun currentModelId(): String = activeModelId

    fun setActiveModelId(modelId: String) {
        if (activeModelId != modelId) {
            activeModelId = modelId
            markModelChanged()
        }
    }

    fun markModelChanged(): Long = modelGeneration.incrementAndGet()
}
