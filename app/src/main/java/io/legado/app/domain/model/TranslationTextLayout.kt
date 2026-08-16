package io.legado.app.domain.model

/**
 * Structural tokens surrounding translatable prose.
 *
 * Translation engines may replace [TextToken.raw], while every other token is an immutable part
 * of the source layout. Keeping these values outside regex/dictionary processing prevents a rule
 * from flattening paragraphs, indentation, Unicode whitespace, markup, URLs, or placeholders.
 */
sealed interface TranslationTextToken {
    val raw: String

    data class ParagraphToken(override val raw: String) : TranslationTextToken
    data class WhitespaceToken(override val raw: String) : TranslationTextToken
    data class ProtectedToken(
        override val raw: String,
        val occurrence: Int,
    ) : TranslationTextToken
    data class TextToken(override val raw: String) : TranslationTextToken
}

data class TranslationTextLayout(
    val tokens: List<TranslationTextToken>,
) {
    fun render(transformText: (String) -> String): String = buildString {
        tokens.forEach { token ->
            append(
                if (token is TranslationTextToken.TextToken) {
                    transformText(token.raw)
                } else {
                    token.raw
                }
            )
        }
    }
}

object TranslationTextTokenizer {

    private val protectedPattern = Regex(
        "<[^>]{1,2048}>|https?://[^\\s<>]+|\\{\\{[^{}]{1,2048}\\}\\}|" +
            "\\$\\{[^{}]{1,2048}\\}|%(?:\\d+\\$)?[a-zA-Z]|" +
            "\\{[A-Za-z_][A-Za-z0-9_.-]{0,255}\\}",
        RegexOption.IGNORE_CASE,
    )

    fun tokenize(value: String): TranslationTextLayout {
        if (value.isEmpty()) return TranslationTextLayout(emptyList())
        val tokens = ArrayList<TranslationTextToken>()
        var cursor = 0
        var protectedOccurrence = 0
        protectedPattern.findAll(value).forEach { match ->
            tokenizePlain(value.substring(cursor, match.range.first), tokens)
            tokens += TranslationTextToken.ProtectedToken(
                raw = match.value,
                occurrence = protectedOccurrence++,
            )
            cursor = match.range.last + 1
        }
        tokenizePlain(value.substring(cursor), tokens)
        return TranslationTextLayout(tokens)
    }

    private fun tokenizePlain(
        value: String,
        output: MutableList<TranslationTextToken>,
    ) {
        var cursor = 0
        while (cursor < value.length) {
            val isWhitespace = value[cursor].isWhitespace()
            var end = cursor + 1
            while (end < value.length && value[end].isWhitespace() == isWhitespace) {
                end += 1
            }
            val raw = value.substring(cursor, end)
            output += when {
                !isWhitespace -> TranslationTextToken.TextToken(raw)
                raw.any(::isParagraphBoundary) -> TranslationTextToken.ParagraphToken(raw)
                else -> TranslationTextToken.WhitespaceToken(raw)
            }
            cursor = end
        }
    }

    private fun isParagraphBoundary(char: Char): Boolean =
        char == '\n' || char == '\r' || char == '\u2028' || char == '\u2029'
}
