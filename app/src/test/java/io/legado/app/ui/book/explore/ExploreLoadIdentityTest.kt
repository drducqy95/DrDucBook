package io.legado.app.ui.book.explore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreLoadIdentityTest {

    @Test
    fun rejectsCompletionFromCancelledGeneration() {
        assertFalse(
            isCurrentExploreLoad(
                requestGeneration = 2,
                activeGeneration = 3,
                requestSource = "source",
                activeSource = "source",
                requestUrl = "kind-a",
                activeUrl = "kind-a",
            )
        )
    }

    @Test
    fun rejectsCompletionFromPreviouslySelectedCategory() {
        assertFalse(
            isCurrentExploreLoad(
                requestGeneration = 3,
                activeGeneration = 3,
                requestSource = "source",
                activeSource = "source",
                requestUrl = "kind-a",
                activeUrl = "kind-b",
            )
        )
    }

    @Test
    fun acceptsCurrentCategoryAndGeneration() {
        assertTrue(
            isCurrentExploreLoad(
                requestGeneration = 3,
                activeGeneration = 3,
                requestSource = "source",
                activeSource = "source",
                requestUrl = "kind-b",
                activeUrl = "kind-b",
            )
        )
    }
}
