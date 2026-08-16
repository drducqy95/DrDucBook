package io.legado.app.ui.book.manga.config

import android.content.Context
import com.drducbook.app.R

object MangaScrollMode {
    const val PAGE_LEFT_TO_RIGHT = 1      // 单页式（从左到右）
    const val PAGE_RIGHT_TO_LEFT = 2      // 单页式（从右到左）
    const val PAGE_TOP_TO_BOTTOM = 3      // 单页式（从上到下）
    const val WEBTOON = 4                 // 条漫
    const val WEBTOON_WITH_GAP = 5        // 条漫（页面有空隙）

    val ALL = listOf(
        PAGE_LEFT_TO_RIGHT,
        PAGE_RIGHT_TO_LEFT,
        PAGE_TOP_TO_BOTTOM,
        WEBTOON,
        WEBTOON_WITH_GAP
    )

    fun labelOf(context: Context, mode: Int): String = context.getString(when (mode) {
        PAGE_LEFT_TO_RIGHT -> R.string.manga_page_left_to_right
        PAGE_RIGHT_TO_LEFT -> R.string.manga_page_right_to_left
        PAGE_TOP_TO_BOTTOM -> R.string.manga_page_top_to_bottom
        WEBTOON -> R.string.manga_webtoon
        WEBTOON_WITH_GAP -> R.string.manga_webtoon_with_gap
        else -> R.string.unknown_source_url
    })
}
