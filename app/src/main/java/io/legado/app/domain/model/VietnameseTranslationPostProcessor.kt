package io.legado.app.domain.model

/** Display-safe Vietnamese typography fixes that never change whitespace or punctuation. */
object VietnameseTranslationPostProcessor {

    private val sentenceEnd = setOf('.', '!', '?', '…', '。', '！', '？')
    private val paragraphEnd = setOf('\n', '\r', '\u2028', '\u2029')

    fun capitalizeSentences(text: String): String {
        if (text.isEmpty()) return text
        val output = StringBuilder(text.length)
        var capitalizeNext = true
        var offset = 0
        while (offset < text.length) {
            val protectedEnd = protectedSpanEnd(text, offset)
            if (protectedEnd != null) {
                output.append(text, offset, protectedEnd)
                offset = protectedEnd
                continue
            }

            val codePoint = text.codePointAt(offset)
            val source = String(Character.toChars(codePoint))
            if (capitalizeNext && Character.isLetter(codePoint)) {
                output.append(source.replaceFirstChar { it.titlecaseChar() })
                capitalizeNext = false
            } else {
                output.append(source)
                if (Character.isLetterOrDigit(codePoint)) capitalizeNext = false
            }
            if (source.length == 1) {
                val char = source[0]
                when {
                    char in paragraphEnd -> capitalizeNext = true
                    char in sentenceEnd && isSentenceBoundary(text, offset) -> capitalizeNext = true
                }
            }
            offset += Character.charCount(codePoint)
        }
        return output.toString()
    }

    private fun isSentenceBoundary(text: String, index: Int): Boolean {
        val char = text[index]
        if (char != '.') return true
        val previous = text.getOrNull(index - 1)
        val next = nextVisibleChar(text, index + 1)
        if (previous?.isDigit() == true && next?.isDigit() == true) return false
        return next == null || next.isWhitespace() || next in "\"'”’»)]}"
    }

    private fun nextVisibleChar(text: String, start: Int): Char? {
        var offset = start
        while (offset < text.length) {
            val end = protectedSpanEnd(text, offset) ?: return text[offset]
            offset = end
        }
        return null
    }

    /** Markup and entities are copied exactly and do not consume the pending capital letter. */
    private fun protectedSpanEnd(text: String, start: Int): Int? {
        return when (text[start]) {
            '<' -> text.indexOf('>', start + 1).takeIf { it >= 0 }?.plus(1)
            '&' -> text.indexOf(';', start + 1)
                .takeIf { it in (start + 2)..(start + 32) }
                ?.plus(1)
            '\uE600' -> text.indexOf('\uE601', start + 1).takeIf { it >= 0 }?.plus(1)
            else -> null
        }
    }
}
