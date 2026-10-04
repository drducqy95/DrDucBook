package io.legado.app.help.book

import androidx.core.text.HtmlCompat

data class FormattedBookIntro(
    val metadataTags: List<String> = emptyList(),
    val synopsis: String = "",
    val notes: String = "",
    val fullFormattedText: String = "",
)

object BookIntroFormatter {

    // Emojis typically used by Chinese webnovel aggregators/sources for intro sections
    private val EMOJI_SECTION_PATTERN = Regex(
        """(?=[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u26FF\u2700-\u27BF][\uFE0E\uFE0F]?\s*[^:\n]{1,25}[:：])"""
    )

    private fun isSynopsis(part: String): Boolean {
        val lower = part.lowercase()
        return lower.contains("tóm tắt") || lower.contains("giới thiệu") ||
                lower.contains("văn án") || lower.contains("nội dung") ||
                part.contains("简介") || part.contains("内容简介")
    }

    private fun isNotes(part: String): Boolean {
        val lower = part.lowercase()
        return lower.contains("quận bình luận") || lower.contains("bình luận") ||
                lower.contains("ghi chú") || lower.contains("lưu ý") ||
                lower.contains("góc trên") || lower.contains("lôi điểm") ||
                lower.contains("review") || part.contains("评论") || part.contains("注意事项")
    }

    /**
     * Cleans raw HTML text into clean readable plain text while preserving intentional paragraph breaks.
     */
    fun cleanHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val preprocessed = raw
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</?p\b[^>]*>"""), "\n\n")
            .replace(Regex("""(?i)</?div\b[^>]*>"""), "\n")
            .replace("\u3000", " ") // full-width whitespace
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

        val converted = try {
            HtmlCompat.fromHtml(preprocessed, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        } catch (_: Throwable) {
            preprocessed.trim()
        }

        return converted
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }

    /**
     * Intelligently parses and formats raw book intro text into structured components.
     * Separates crammed metadata emoji badges from the novel's synopsis and reviewer notes.
     */
    fun format(raw: String?): FormattedBookIntro {
        if (raw.isNullOrBlank()) return FormattedBookIntro()

        val cleaned = cleanHtml(raw)
        if (cleaned.isBlank()) return FormattedBookIntro()

        // Check heuristic: does this text contain jammed emoji metadata tags?
        val parts = cleaned.split(EMOJI_SECTION_PATTERN)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // If there are fewer than 2 emoji tags, treat as normal paragraph text
        if (parts.size <= 1) {
            return FormattedBookIntro(
                metadataTags = emptyList(),
                synopsis = cleaned,
                notes = "",
                fullFormattedText = cleaned,
            )
        }

        val metadataTags = mutableListOf<String>()
        var synopsisText = ""
        val notesList = mutableListOf<String>()

        for (part in parts) {
            when {
                isSynopsis(part) -> {
                    val colonIdx = part.indexOfAny(charArrayOf(':', '：'))
                    val body = if (colonIdx in 1..25) part.substring(colonIdx + 1).trim() else part.trim()
                    synopsisText = if (synopsisText.isBlank()) body else "$synopsisText\n\n$body"
                }
                isNotes(part) -> {
                    notesList.add(part)
                }
                // Check if it's a short metadata tag (e.g. 📖Sách ID: 53485)
                part.length < 120 && (part.contains(":") || part.contains("：")) -> {
                    // Normalize space after colon
                    val normalizedTag = part.replace(Regex("""([:：])\s*"""), ": ")
                    metadataTags.add(normalizedTag)
                }
                else -> {
                    // Long prose that doesn't explicitly have synopsis header, treat as synopsis body
                    synopsisText = if (synopsisText.isBlank()) part else "$synopsisText\n\n$part"
                }
            }
        }

        val formattedNotes = notesList.joinToString("\n\n")

        val fullText = buildString {
            if (metadataTags.isNotEmpty()) {
                append(metadataTags.joinToString("\n"))
                append("\n\n")
            }
            if (synopsisText.isNotBlank()) {
                append("📜 Tóm tắt:\n")
                append(synopsisText)
            }
            if (formattedNotes.isNotBlank()) {
                append("\n\n")
                append(formattedNotes)
            }
        }.trim()

        return FormattedBookIntro(
            metadataTags = metadataTags,
            synopsis = synopsisText,
            notes = formattedNotes,
            fullFormattedText = fullText.ifBlank { cleaned },
        )
    }
}
