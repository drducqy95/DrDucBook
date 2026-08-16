package io.legado.app.ui.book.readRecord

import android.content.Context
import com.drducbook.app.R
import io.legado.app.utils.formatReadDuration

object ReadRecordFormatter {
    fun formatWords(context: Context, words: Long): String {
        return if (words >= 10000) {
            context.getString(
                R.string.reading_words_compact,
                String.format(context.resources.configuration.locales[0], "%.1f", words / 10000f)
            )
        } else {
            context.getString(R.string.reading_words_count, words)
        }
    }

    fun formatDuration(millis: Long): String = formatReadDuration(millis)
}
