package io.legado.app.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import io.legado.app.ui.book.read.page.provider.ChapterProvider

internal class DropCapColumn(
    override var start: Float,
    override var end: Float,
    override val charData: String,
    private val textSize: Float,
    private val topOffset: Float,
    private val typeface: Typeface?,
) : TextBaseColumn {

    override val textColor: Int? = null
    override val bgColor: Int? = null
    override val underlineMode: Int = 0
    override val underlineColor: Int? = null
    override val underlineWidth: Float = 0f
    override val underlineOffset: Float = 0f
    override val underlineSvgPath: String = ""
    override val bgImage: String = ""
    override val bgImageFit: Int = 0
    override val bgImageScale: Float = 1f
    override val fontPath: String = ""
    override val italic: Boolean = false
    override var textLine: TextLine = emptyTextLine

    private val paint by lazy {
        TextPaint(ChapterProvider.contentPaint).apply {
            this.textSize = this@DropCapColumn.textSize
            this.typeface = this@DropCapColumn.typeface
        }
    }
    private val bounds by lazy {
        Rect().also { paint.getTextBounds(charData, 0, charData.length, it) }
    }

    val visualBottom: Float get() = topOffset + bounds.height()

    override var selected: Boolean = false
        set(value) {
            if (field != value) textLine.invalidate()
            field = value
        }

    override var isSearchResult: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
                textLine.searchResultColumnCount += if (value) 1 else -1
            }
            field = value
        }

    override fun draw(view: ContentTextView, canvas: Canvas) {
        paint.color = if (textLine.isReadAloud || isSearchResult) {
            ReadBookConfig.textAccentColor
        } else {
            ReadBookConfig.textColor
        }
        canvas.drawText(
            charData,
            start - bounds.left,
            topOffset - bounds.top,
            paint,
        )
        if (selected) {
            canvas.drawRect(start, topOffset, end, visualBottom, view.selectedPaint)
        }
    }
}
