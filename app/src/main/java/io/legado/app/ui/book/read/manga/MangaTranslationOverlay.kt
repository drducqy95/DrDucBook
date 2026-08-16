package io.legado.app.ui.book.read.manga

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import android.widget.ImageView
import io.legado.app.domain.manga.MangaOverlayPage
import io.legado.app.domain.manga.MangaTranslationResult
import io.legado.app.domain.manga.MangaTranslationStage
import kotlin.math.max

class MangaTranslationOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var imageView: ImageView? = null
    var onTranslationClick: ((MangaTranslationResult) -> Unit)? = null
        set(value) {
            field = value
            isClickable = value != null
        }
    private var page: MangaOverlayPage? = null
    private var progressText: String? = null
    private var errorText: String? = null
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(17, 17, 17)
        textAlign = Paint.Align.CENTER
    }

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun showProgress(stage: MangaTranslationStage, current: Int, total: Int) {
        progressText = if (total > 0) "${stage.name.lowercase()} ${current.coerceAtMost(total)}/$total" else {
            stage.name.lowercase()
        }
        errorText = null
        invalidate()
    }

    fun showPage(value: MangaOverlayPage) {
        page = value
        progressText = null
        errorText = null
        invalidate()
    }

    fun showError(message: String) {
        progressText = null
        errorText = message
        invalidate()
    }

    fun clearPage() {
        page = null
        progressText = null
        errorText = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentPage = page
        val targetImage = imageView
        if (currentPage != null && targetImage != null) {
            canvas.save()
            canvas.translate(
                (targetImage.left + targetImage.paddingLeft).toFloat(),
                (targetImage.top + targetImage.paddingTop).toFloat(),
            )
            canvas.concat(targetImage.imageMatrix)
            currentPage.translations.forEach { drawTranslation(canvas, it) }
            canvas.restore()
        }
        val status = errorText ?: progressText
        if (!status.isNullOrBlank()) drawStatus(canvas, status, errorText != null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return isClickable
        val targetImage = imageView ?: return false
        val currentPage = page ?: return false
        val inverse = Matrix()
        if (!targetImage.imageMatrix.invert(inverse)) return false
        val point = floatArrayOf(
            event.x - targetImage.left - targetImage.paddingLeft,
            event.y - targetImage.top - targetImage.paddingTop,
        )
        inverse.mapPoints(point)
        val selected = currentPage.translations.lastOrNull { translation ->
            val box = translation.region.boundingBox
            point[0] >= box.left && point[0] <= box.right &&
                point[1] >= box.top && point[1] <= box.bottom
        } ?: return false
        onTranslationClick?.invoke(selected)
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawTranslation(canvas: Canvas, translation: MangaTranslationResult) {
        val bounds = translation.region.boundingBox
        val rect = RectF(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
        )
        backgroundPaint.color = translation.style.backgroundColor.toInt()
        canvas.drawRoundRect(rect, 4f, 4f, backgroundPaint)
        textPaint.color = translation.style.textColor.toInt()
        val padding = max(4f, minOf(rect.width(), rect.height()) * 0.06f)
        val availableWidth = (rect.width() - padding * 2).coerceAtLeast(8f)
        val availableHeight = (rect.height() - padding * 2).coerceAtLeast(8f)
        val lines = fitLines(
            text = translation.translatedText,
            width = availableWidth,
            height = availableHeight,
            preferredSize = translation.style.textSizeSp,
        )
        val lineHeight = textPaint.fontSpacing
        var baseline = rect.centerY() - (lines.size - 1) * lineHeight / 2f -
            (textPaint.ascent() + textPaint.descent()) / 2f
        lines.forEach { line ->
            canvas.drawText(line, rect.centerX(), baseline, textPaint)
            baseline += lineHeight
        }
    }

    private fun fitLines(
        text: String,
        width: Float,
        height: Float,
        preferredSize: Float,
    ): List<String> {
        var low = 8f
        var high = minOf(height, preferredSize.coerceIn(8f, 48f)).coerceAtLeast(low)
        var best = listOf(text)
        repeat(8) {
            val size = (low + high) / 2f
            textPaint.textSize = size
            val lines = wrap(text, width)
            if (lines.size * textPaint.fontSpacing <= height) {
                best = lines
                low = size
            } else {
                high = size
            }
        }
        textPaint.textSize = low
        return best
    }

    private fun wrap(text: String, width: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            .flatMap { word -> splitWordToWidth(word, width) }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && textPaint.measureText(candidate) > width) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun splitWordToWidth(word: String, width: Float): List<String> {
        if (textPaint.measureText(word) <= width) return listOf(word)
        val parts = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            val count = textPaint.breakText(word, start, word.length, true, width, null)
                .coerceAtLeast(1)
            parts += word.substring(start, (start + count).coerceAtMost(word.length))
            start += count
        }
        return parts
    }

    private fun drawStatus(canvas: Canvas, text: String, error: Boolean) {
        textPaint.textSize = resources.displayMetrics.scaledDensity * 14f
        textPaint.color = Color.WHITE
        val padding = resources.displayMetrics.density * 10f
        val width = textPaint.measureText(text) + padding * 2
        val height = textPaint.fontSpacing + padding
        val rect = RectF(
            (canvas.width - width) / 2f,
            padding,
            (canvas.width + width) / 2f,
            padding + height,
        )
        backgroundPaint.color = if (error) 0xDD9B1C1C.toInt() else 0xCC202020.toInt()
        canvas.drawRoundRect(rect, 6f, 6f, backgroundPaint)
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(text.take(120), rect.centerX(), baseline, textPaint)
    }
}
