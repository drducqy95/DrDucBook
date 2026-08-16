package io.legado.app.ui.book.read.page.delegate

import kotlin.math.min

/** Stable geometry for the lightweight paper-fold renderer. */
internal object PaperPageGeometry {

    data class Frame(
        val progress: Float,
        val creaseX: Float,
        val foldWidth: Float,
        val shadowWidth: Float,
        val shadowAlpha: Int,
    )

    fun frame(width: Int, rawProgress: Float, isNext: Boolean): Frame {
        if (width <= 0) {
            return Frame(0f, 0f, 0f, 0f, 0)
        }
        val progress = rawProgress.coerceIn(0f, 1f)
        val travel = width * progress
        val remaining = width - travel
        val foldWidth = min(width * 0.18f, min(travel, remaining) * 0.72f)
            .coerceAtLeast(0f)
        val creaseX = if (isNext) remaining else travel
        val shadowWidth = (width * 0.055f).coerceAtLeast(1f)
        val shadowAlpha = (36 + 82 * (1f - kotlin.math.abs(progress - 0.5f) * 2f))
            .toInt()
            .coerceIn(0, 118)
        return Frame(progress, creaseX, foldWidth, shadowWidth, shadowAlpha)
    }
}
