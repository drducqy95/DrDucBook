package io.legado.app.ui.ai.bubble

enum class ChatBubbleOrientation {
    PORTRAIT,
    LANDSCAPE,
}

data class ChatBubbleNormalizedPosition(
    val x: Float,
    val y: Float,
)

data class ChatBubblePixelPosition(
    val x: Float,
    val y: Float,
)

data class ChatBubblePixelBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

data class ChatBubbleRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

object ChatBubblePositioner {

    fun positionPip(
        anchor: ChatBubbleRect,
        windowWidth: Int,
        windowHeight: Int,
        popupWidth: Int,
        popupHeight: Int,
        margin: Int,
    ): ChatBubblePixelPosition {
        val safeMargin = margin.coerceAtLeast(0)
        val maxX = (windowWidth - popupWidth - safeMargin).coerceAtLeast(safeMargin)
        val maxY = (windowHeight - popupHeight - safeMargin).coerceAtLeast(safeMargin)
        val opensToRight = anchor.left + anchor.right <= windowWidth
        val preferredX = if (opensToRight) {
            anchor.right + safeMargin
        } else {
            anchor.left - popupWidth - safeMargin
        }
        val preferredY = anchor.bottom - popupHeight
        return ChatBubblePixelPosition(
            x = preferredX.coerceIn(safeMargin, maxX).toFloat(),
            y = preferredY.coerceIn(safeMargin, maxY).toFloat(),
        )
    }

    fun defaultPosition(bounds: ChatBubblePixelBounds): ChatBubblePixelPosition {
        return ChatBubblePixelPosition(
            x = bounds.maxX,
            y = bounds.maxY,
        )
    }

    fun restore(
        normalized: ChatBubbleNormalizedPosition?,
        bounds: ChatBubblePixelBounds,
    ): ChatBubblePixelPosition {
        if (normalized == null) return defaultPosition(bounds)
        val rangeX = (bounds.maxX - bounds.minX).coerceAtLeast(0f)
        val rangeY = (bounds.maxY - bounds.minY).coerceAtLeast(0f)
        return clamp(
            ChatBubblePixelPosition(
                x = bounds.minX + rangeX * normalized.x.coerceIn(0f, 1f),
                y = bounds.minY + rangeY * normalized.y.coerceIn(0f, 1f),
            ),
            bounds,
        )
    }

    fun normalize(
        position: ChatBubblePixelPosition,
        bounds: ChatBubblePixelBounds,
    ): ChatBubbleNormalizedPosition {
        val clamped = clamp(position, bounds)
        val rangeX = (bounds.maxX - bounds.minX).coerceAtLeast(0f)
        val rangeY = (bounds.maxY - bounds.minY).coerceAtLeast(0f)
        return ChatBubbleNormalizedPosition(
            x = if (rangeX == 0f) 0f else ((clamped.x - bounds.minX) / rangeX).coerceIn(0f, 1f),
            y = if (rangeY == 0f) 0f else ((clamped.y - bounds.minY) / rangeY).coerceIn(0f, 1f),
        )
    }

    fun snapToNearestEdge(
        position: ChatBubblePixelPosition,
        bounds: ChatBubblePixelBounds,
    ): ChatBubblePixelPosition {
        val clamped = clamp(position, bounds)
        val leftDistance = kotlin.math.abs(clamped.x - bounds.minX)
        val rightDistance = kotlin.math.abs(bounds.maxX - clamped.x)
        return clamped.copy(
            x = if (leftDistance <= rightDistance) bounds.minX else bounds.maxX,
        )
    }

    fun clamp(
        position: ChatBubblePixelPosition,
        bounds: ChatBubblePixelBounds,
    ): ChatBubblePixelPosition {
        val minX = minOf(bounds.minX, bounds.maxX)
        val maxX = maxOf(bounds.minX, bounds.maxX)
        val minY = minOf(bounds.minY, bounds.maxY)
        val maxY = maxOf(bounds.minY, bounds.maxY)
        return ChatBubblePixelPosition(
            x = position.x.coerceIn(minX, maxX),
            y = position.y.coerceIn(minY, maxY),
        )
    }
}
