package io.legado.app.ui.book.explore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorePaginationPolicyTest {

    @Test
    fun requestsNextPageWhenListReachesPreloadWindow() {
        assertTrue(
            shouldLoadNextExplorePage(
                totalItemsCount = 20,
                lastVisibleIndex = 17,
                preloadDistance = 3,
            )
        )
    }

    @Test
    fun waitsWhenListIsOutsidePreloadWindowOrEmpty() {
        assertFalse(
            shouldLoadNextExplorePage(
                totalItemsCount = 20,
                lastVisibleIndex = 16,
                preloadDistance = 3,
            )
        )
        assertFalse(
            shouldLoadNextExplorePage(
                totalItemsCount = 0,
                lastVisibleIndex = -1,
                preloadDistance = 3,
            )
        )
    }

    @Test
    fun gridPreloadsOneVisibleRow() {
        assertTrue(
            shouldLoadNextExplorePage(
                totalItemsCount = 24,
                lastVisibleIndex = 20,
                preloadDistance = 4,
            )
        )
    }

    @Test
    fun userPullAtExhaustedBottomRequestsContinuation() {
        assertTrue(
            shouldRequestExploreContinuationOnEndPull(
                hasUnconsumedUpwardDrag = true,
                canScrollForward = false,
                hasBooks = true,
                isLoading = false,
                isRefreshing = false,
                hasError = false,
                isEnd = true,
            )
        )
    }

    @Test
    fun passiveBottomOrBusyStateDoesNotRequestContinuation() {
        assertFalse(
            shouldRequestExploreContinuationOnEndPull(
                hasUnconsumedUpwardDrag = false,
                canScrollForward = false,
                hasBooks = true,
                isLoading = false,
                isRefreshing = false,
                hasError = false,
                isEnd = true,
            )
        )
        assertFalse(
            shouldRequestExploreContinuationOnEndPull(
                hasUnconsumedUpwardDrag = true,
                canScrollForward = false,
                hasBooks = true,
                isLoading = true,
                isRefreshing = false,
                hasError = false,
                isEnd = true,
            )
        )
        assertFalse(
            shouldRequestExploreContinuationOnEndPull(
                hasUnconsumedUpwardDrag = true,
                canScrollForward = true,
                hasBooks = true,
                isLoading = false,
                isRefreshing = false,
                hasError = false,
                isEnd = true,
            )
        )
    }
}
