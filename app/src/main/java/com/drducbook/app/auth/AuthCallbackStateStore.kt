package com.drducbook.app.auth

import android.content.Context
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefStringSync

/** Carries browser callback failures back to the Compose account screen without logging tokens. */
object AuthCallbackStateStore {
    private const val KEY = "auth.callback.error"

    fun saveError(context: Context, error: Throwable?) {
        val message = error?.message?.trim().orEmpty().take(300).ifBlank { "Xác thực từ trình duyệt thất bại" }
        context.putPrefStringSync(KEY, message)
    }

    fun consumeError(context: Context): String? {
        val value = context.getPrefString(KEY)?.takeIf(String::isNotBlank)
        if (value != null) context.putPrefStringSync(KEY, "")
        return value
    }
}
