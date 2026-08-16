package io.legado.app.ui.main.explore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorePreviewPaginationTest {

    @Test
    fun loadsWhenLastPreviewBooksBecomeVisible() {
        assertTrue(
            shouldLoadNextExplorePreviewPage(
                lastVisibleIndex = 17,
                bookCount = 21,
                leadingItemCount = 2,
                preloadDistance = 6,
            )
        )
    }

    @Test
    fun doesNotLoadWhileFarFromPreviewEnd() {
        assertFalse(
            shouldLoadNextExplorePreviewPage(
                lastVisibleIndex = 10,
                bookCount = 21,
                leadingItemCount = 2,
                preloadDistance = 6,
            )
        )
    }

    @Test
    fun ignoresHeaderOnlyGrid() {
        assertFalse(
            shouldLoadNextExplorePreviewPage(
                lastVisibleIndex = 1,
                bookCount = 0,
                leadingItemCount = 2,
                preloadDistance = 6,
            )
        )
    }
}
