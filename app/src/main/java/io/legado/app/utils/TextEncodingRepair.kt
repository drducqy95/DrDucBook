package io.legado.app.utils

import java.nio.charset.Charset

/**
 * Repairs text that was decoded as Latin-1 after being encoded as UTF-8.
 * Only strings with a clear mojibake signature are changed, so normal
 * Vietnamese, Chinese, and Japanese text remains untouched.
 */
object TextEncodingRepair {
    private val markerRegex = Regex("\\u00C3|\\u00C2(?:[\\u00B7\\u00A0])|(?:\\u00E1|t\\u00E1)[\\u00BA\\u00BB]|\\u00C4|\\u00C5|\\u00C6|\\u00D0|\\u00D1")
    private val latin1 = Charset.forName("ISO-8859-1")

    fun repair(value: String?): String? {
        var current = value ?: return null
        if (current.isBlank()) return current
        repeat(2) {
            val score = markerRegex.findAll(current).count()
            if (score == 0 || current.any { it.code > 0xFF }) return current
            val decoded = runCatching {
                current.toByteArray(latin1).toString(Charsets.UTF_8)
            }.getOrNull() ?: return current
            if (decoded == current || markerRegex.findAll(decoded).count() >= score) return current
            current = decoded
        }
        return current
    }
}
