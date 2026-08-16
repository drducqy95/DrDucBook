package io.legado.app.utils

import android.content.Context
import com.drducbook.app.R
import splitties.init.appCtx

fun formatReadDuration(millis: Long): String = formatReadDuration(appCtx, millis)

fun formatReadDuration(context: Context, millis: Long): String {
    return formatReadDuration(
        millis = millis,
        formatter = ReadDurationFormatter(
            days = { context.getString(R.string.read_duration_days_format, it) },
            hours = { context.getString(R.string.read_duration_hours_format, it) },
            minutes = { context.getString(R.string.read_duration_minutes_format, it) },
            seconds = { context.getString(R.string.read_duration_seconds_format, it) },
            zero = context.getString(R.string.read_duration_zero_seconds),
        )
    )
}

internal data class ReadDurationFormatter(
    val days: (Long) -> String,
    val hours: (Long) -> String,
    val minutes: (Long) -> String,
    val seconds: (Long) -> String,
    val zero: String,
)

internal fun formatReadDuration(
    millis: Long,
    formatter: ReadDurationFormatter,
): String {
    val safeMillis = millis.coerceAtLeast(0L)
    val days = safeMillis / (1000 * 60 * 60 * 24)
    val hours = safeMillis % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
    val minutes = safeMillis % (1000 * 60 * 60) / (1000 * 60)
    val seconds = safeMillis % (1000 * 60) / 1000
    val parts = buildList {
        if (days > 0) add(formatter.days(days))
        if (hours > 0) add(formatter.hours(hours))
        if (minutes > 0) add(formatter.minutes(minutes))
        if (seconds > 0) add(formatter.seconds(seconds))
    }
    return parts.joinToString(" ").ifBlank { formatter.zero }
}
