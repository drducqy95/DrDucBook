package io.legado.app.help.config

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefLong
import splitties.init.appCtx

object BookshelfAutomationConfig {

    var enabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.bookshelfAutomationEnabled, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.bookshelfAutomationEnabled, value)

    var intervalHours: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfAutomationIntervalHours, DEFAULT_INTERVAL_HOURS)
            .coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS)
        set(value) = appCtx.putPrefInt(
            PreferKey.bookshelfAutomationIntervalHours,
            value.coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS),
        )

    var autoDownloadNewChapters: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.bookshelfAutomationDownloadNew, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.bookshelfAutomationDownloadNew, value)

    var notifyNewChapters: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.bookshelfAutomationNotifyNew, true)
        set(value) = appCtx.putPrefBoolean(PreferKey.bookshelfAutomationNotifyNew, value)

    var lastCheckAt: Long
        get() = appCtx.getPrefLong(PreferKey.bookshelfAutomationLastCheckAt, 0L)
        set(value) = appCtx.putPrefLong(PreferKey.bookshelfAutomationLastCheckAt, value)

    var lastNewChapterCount: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfAutomationLastNewChapterCount, 0)
        set(value) = appCtx.putPrefInt(PreferKey.bookshelfAutomationLastNewChapterCount, value)

    var lastUpdatedBookCount: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfAutomationLastUpdatedBookCount, 0)
        set(value) = appCtx.putPrefInt(PreferKey.bookshelfAutomationLastUpdatedBookCount, value)

    const val MIN_INTERVAL_HOURS = 1
    const val MAX_INTERVAL_HOURS = 168
    const val DEFAULT_INTERVAL_HOURS = 6
}
