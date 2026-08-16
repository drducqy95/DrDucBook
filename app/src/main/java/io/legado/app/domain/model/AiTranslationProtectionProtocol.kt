package io.legado.app.domain.model

/**
 * Protects source fragments that must survive AI translation byte-for-byte, such as markup,
 * URLs, app placeholders, and format specifiers. The model sees stable ASCII sentinels and the
 * app restores the original values before layout validation/caching.
 */
object AiTranslationProtectionProtocol {

    data class ProtectedText(
        val value: String,
        val replacements: List<Replacement>,
    ) {
        val hasProtectedTokens: Boolean
            get() = replacements.isNotEmpty()

        fun restore(text: String): String =
            replacements.fold(text) { result, replacement ->
                result.replace(replacement.placeholder, replacement.raw)
            }

        fun missingPlaceholders(text: String): List<String> =
            replacements.mapNotNull { replacement ->
                replacement.placeholder.takeUnless(text::contains)
            }

        fun integrityViolations(text: String): List<String> {
            val violations = mutableListOf<String>()
            var previousIndex = -1
            replacements.forEach { replacement ->
                val firstIndex = text.indexOf(replacement.placeholder)
                when {
                    firstIndex < 0 -> violations += "missing ${replacement.placeholder}"
                    text.indexOf(
                        replacement.placeholder,
                        firstIndex + replacement.placeholder.length,
                    ) >= 0 -> violations += "duplicated ${replacement.placeholder}"
                    firstIndex < previousIndex -> violations += "reordered ${replacement.placeholder}"
                }
                if (firstIndex >= 0) previousIndex = firstIndex
            }
            return violations
        }
    }

    data class Replacement(
        val placeholder: String,
        val raw: String,
    )

    fun protect(text: String): ProtectedText {
        val layout = TranslationTextTokenizer.tokenize(text)
        val replacements = mutableListOf<Replacement>()
        val placeholderPrefix = collisionFreePrefix(text)
        val protectedValue = layout.tokens.joinToString(separator = "") { token ->
            if (token is TranslationTextToken.ProtectedToken) {
                val placeholder =
                    "$placeholderPrefix${token.occurrence.toString().padStart(3, '0')}__"
                replacements += Replacement(
                    placeholder = placeholder,
                    raw = token.raw,
                )
                placeholder
            } else {
                token.raw
            }
        }
        return ProtectedText(protectedValue, replacements)
    }

    private fun collisionFreePrefix(text: String): String {
        var suffix = 0
        while (true) {
            val prefix = if (suffix == 0) "__LG_KEEP_" else "__LG_KEEP${suffix}_"
            if (!text.contains(prefix)) return prefix
            suffix++
        }
    }
}
