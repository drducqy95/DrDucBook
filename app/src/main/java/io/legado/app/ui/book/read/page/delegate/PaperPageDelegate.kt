package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import kotlin.math.abs

/**
 * A stable, low-allocation paper-fold animation.
 *
 * The outgoing page is split into its front and a mirrored back strip. Gradients on both the
 * revealed page and the strip create a cylindrical paper crease without allocating bitmaps per
 * frame (the page recorders are owned by [HorizontalPageDelegate]).
 */
class PaperPageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onAnimStart(animationSpeed: Int) {
        val distanceX = when (mDirection) {
            PageDirection.NEXT -> if (isCancel) {
                startX - touchX
            } else {
                startX - viewWidth - touchX
            }

            PageDirection.PREV -> if (isCancel) {
                startX - touchX
            } else {
                startX + viewWidth - touchX
            }

            else -> 0f
        }
        startScroll(touchX.toInt(), 0, distanceX.toInt(), 0, animationSpeed)
    }

    override fun onDraw(canvas: Canvas) {
        if (!isRunning || viewWidth <= 0 || viewHeight <= 0) return
        val offset = touchX - startX
        val isNext = mDirection == PageDirection.NEXT
        if ((isNext && offset > 0f) || (mDirection == PageDirection.PREV && offset < 0f)) return

        val frame = PaperPageGeometry.frame(viewWidth, abs(offset) / viewWidth, isNext)
        if (isNext) nextRecorder.draw(canvas) else prevRecorder.draw(canvas)

        if (isNext) {
            drawNext(canvas, frame)
        } else if (mDirection == PageDirection.PREV) {
            drawPrevious(canvas, frame)
        }
    }

    private fun drawNext(canvas: Canvas, frame: PaperPageGeometry.Frame) {
        val crease = frame.creaseX
        canvas.withClip(0f, 0f, crease, viewHeight.toFloat()) {
            curRecorder.draw(this)
        }
        if (frame.foldWidth > 0f) {
            val foldEnd = (crease + frame.foldWidth).coerceAtMost(viewWidth.toFloat())
            canvas.save()
            canvas.clipRect(crease, 0f, foldEnd, viewHeight.toFloat())
            canvas.scale(-1f, 1f, crease, 0f)
            curRecorder.draw(canvas)
            canvas.restore()
            drawFoldTint(canvas, crease, foldEnd, isNext = true)
        }
        drawTargetShadow(canvas, crease, frame, isNext = true)
    }

    private fun drawPrevious(canvas: Canvas, frame: PaperPageGeometry.Frame) {
        val crease = frame.creaseX
        canvas.withClip(crease, 0f, viewWidth.toFloat(), viewHeight.toFloat()) {
            curRecorder.draw(this)
        }
        if (frame.foldWidth > 0f) {
            val foldStart = (crease - frame.foldWidth).coerceAtLeast(0f)
            canvas.save()
            canvas.clipRect(foldStart, 0f, crease, viewHeight.toFloat())
            canvas.scale(-1f, 1f, crease, 0f)
            curRecorder.draw(canvas)
            canvas.restore()
            drawFoldTint(canvas, foldStart, crease, isNext = false)
        }
        drawTargetShadow(canvas, crease, frame, isNext = false)
    }

    private fun drawFoldTint(canvas: Canvas, start: Float, end: Float, isNext: Boolean) {
        val colors = if (isNext) {
            intArrayOf(0x669E9E9E, 0x26FFFFFF, 0x7A6F6F6F)
        } else {
            intArrayOf(0x7A6F6F6F, 0x26FFFFFF, 0x669E9E9E)
        }
        paint.shader = LinearGradient(start, 0f, end, 0f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(start, 0f, end, viewHeight.toFloat(), paint)
        paint.shader = null
    }

    private fun drawTargetShadow(
        canvas: Canvas,
        crease: Float,
        frame: PaperPageGeometry.Frame,
        isNext: Boolean,
    ) {
        val start = if (isNext) crease else (crease - frame.shadowWidth).coerceAtLeast(0f)
        val end = if (isNext) (crease + frame.shadowWidth).coerceAtMost(viewWidth.toFloat()) else crease
        if (end <= start) return
        val opaque = Color.argb(frame.shadowAlpha, 0, 0, 0)
        val transparent = Color.TRANSPARENT
        val colors = if (isNext) intArrayOf(opaque, transparent) else intArrayOf(transparent, opaque)
        paint.shader = LinearGradient(start, 0f, end, 0f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(RectF(start, 0f, end, viewHeight.toFloat()), paint)
        paint.shader = null
    }

    override fun onAnimStop() {
        if (!isCancel) readView.fillPage(mDirection)
    }
}
