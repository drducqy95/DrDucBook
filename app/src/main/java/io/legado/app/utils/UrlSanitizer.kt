package io.legado.app.utils

/**
 * Normalizes and sanitizes book, chapter, and source URLs.
 * Handles common dirty artifacts such as:
 * - Stray braces: `{https://...}` or `{Https://...`
 * - Stray quotation marks: `"https://..."` or `'https://...'`
 * - Case variations in schemes: `Https://` or `HTTP://` -> `https://` / `http://`
 * - Leading/trailing brackets, whitespace, and newlines
 */
object UrlSanitizer {

    fun sanitizeBookUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        var cleaned = rawUrl.trim()

        // Strip leading JSON/quote artifacts
        while (cleaned.isNotEmpty() && (cleaned.startsWith("{") || cleaned.startsWith("\"") || cleaned.startsWith("'") || cleaned.startsWith("["))) {
            cleaned = cleaned.substring(1).trimStart()
        }

        // Strip trailing JSON/quote artifacts
        while (cleaned.isNotEmpty() && (cleaned.endsWith("}") || cleaned.endsWith("\"") || cleaned.endsWith("'") || cleaned.endsWith("]"))) {
            cleaned = cleaned.substring(0, cleaned.length - 1).trimEnd()
        }

        // Normalize URL scheme to lowercase if it starts with http:// or https:// (case-insensitive)
        cleaned = when {
            cleaned.startsWith("https://", ignoreCase = true) -> "https://" + cleaned.substring(8)
            cleaned.startsWith("http://", ignoreCase = true) -> "http://" + cleaned.substring(7)
            else -> cleaned
        }

        return cleaned.trim()
    }

    fun sanitize(rawUrl: String?): String = sanitizeBookUrl(rawUrl)
}
