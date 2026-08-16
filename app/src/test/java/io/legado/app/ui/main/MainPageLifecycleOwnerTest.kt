package io.legado.app.ui.main

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class MainPageLifecycleOwnerTest {

    @Test
    fun destroyIsValidBeforeParentReachesCreated() {
        val owner = MainPageLifecycleOwner()

        owner.update(Lifecycle.State.INITIALIZED, isActive = false)
        owner.destroy()

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    @Test
    fun destroyIsIdempotentAfterActivePageStops() {
        val owner = MainPageLifecycleOwner()

        owner.update(Lifecycle.State.RESUMED, isActive = true)
        owner.update(Lifecycle.State.STARTED, isActive = false)
        owner.destroy()
        owner.destroy()

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }
}
