package io.legado.app.ui.ai.bubble

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatBubblePositionerTest {

    @Test
    fun defaultPositionUsesBottomEndInsideBounds() {
        val bounds = ChatBubblePixelBounds(
            minX = 16f,
            minY = 48f,
            maxX = 320f,
            maxY = 680f,
        )

        val position = ChatBubblePositioner.defaultPosition(bounds)

        assertEquals(320f, position.x, 0.01f)
        assertEquals(680f, position.y, 0.01f)
    }

    @Test
    fun normalizeAndRestoreRoundTripWithinSafeBounds() {
        val bounds = ChatBubblePixelBounds(
            minX = 20f,
            minY = 40f,
            maxX = 420f,
            maxY = 840f,
        )
        val original = ChatBubblePixelPosition(220f, 440f)

        val normalized = ChatBubblePositioner.normalize(original, bounds)
        val restored = ChatBubblePositioner.restore(normalized, bounds)

        assertEquals(original.x, restored.x, 0.01f)
        assertEquals(original.y, restored.y, 0.01f)
    }

    @Test
    fun restoreClampsInvalidNormalizedPosition() {
        val bounds = ChatBubblePixelBounds(
            minX = 10f,
            minY = 30f,
            maxX = 210f,
            maxY = 430f,
        )

        val restored = ChatBubblePositioner.restore(
            normalized = ChatBubbleNormalizedPosition(x = 2f, y = -1f),
            bounds = bounds,
        )

        assertEquals(210f, restored.x, 0.01f)
        assertEquals(30f, restored.y, 0.01f)
    }

    @Test
    fun snapChoosesNearestHorizontalEdgeAndKeepsVerticalPosition() {
        val bounds = ChatBubblePixelBounds(
            minX = 0f,
            minY = 50f,
            maxX = 300f,
            maxY = 700f,
        )

        val left = ChatBubblePositioner.snapToNearestEdge(
            position = ChatBubblePixelPosition(80f, 220f),
            bounds = bounds,
        )
        val right = ChatBubblePositioner.snapToNearestEdge(
            position = ChatBubblePixelPosition(240f, 620f),
            bounds = bounds,
        )

        assertEquals(0f, left.x, 0.01f)
        assertEquals(220f, left.y, 0.01f)
        assertEquals(300f, right.x, 0.01f)
        assertEquals(620f, right.y, 0.01f)
    }

    @Test
    fun pipPanelOpensBesideBubbleAndStaysInsideWindow() {
        val fromRightEdge = ChatBubblePositioner.positionPip(
            anchor = ChatBubbleRect(left = 920, top = 1680, right = 984, bottom = 1744),
            windowWidth = 1000,
            windowHeight = 1800,
            popupWidth = 560,
            popupHeight = 760,
            margin = 16,
        )
        val fromLeftEdge = ChatBubblePositioner.positionPip(
            anchor = ChatBubbleRect(left = 16, top = 40, right = 80, bottom = 104),
            windowWidth = 1000,
            windowHeight = 1800,
            popupWidth = 560,
            popupHeight = 760,
            margin = 16,
        )

        assertEquals(344f, fromRightEdge.x, 0.01f)
        assertEquals(984f, fromRightEdge.y, 0.01f)
        assertEquals(96f, fromLeftEdge.x, 0.01f)
        assertEquals(16f, fromLeftEdge.y, 0.01f)
    }
}
