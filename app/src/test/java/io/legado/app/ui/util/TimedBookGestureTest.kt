package io.legado.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimedBookGestureTest {

    @Test
    fun classifiesTheThreeDocumentedHoldRanges() {
        val longPressMillis = 500L

        assertEquals(
            BookGestureAction.OPEN_READER,
            classifyBookGesture(499L, longPressMillis),
        )
        assertEquals(
            BookGestureAction.OPEN_INFO,
            classifyBookGesture(500L, longPressMillis),
        )
        assertEquals(
            BookGestureAction.OPEN_INFO,
            classifyBookGesture(1_299L, longPressMillis),
        )
        assertEquals(
            BookGestureAction.SELECT_PHRASE,
            classifyBookGesture(1_300L, longPressMillis),
        )
    }
}
