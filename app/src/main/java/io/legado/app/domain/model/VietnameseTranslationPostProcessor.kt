package io.legado.app.domain.model

/** Display-safe Vietnamese typography fixes that never change whitespace or punctuation. */
object VietnameseTranslationPostProcessor {

    private val sentenceEnd = setOf('.', '!', '?', '…', '。', '！', '？')
    private val paragraphEnd = setOf('\n', '\r', '\u2028', '\u2029')

    fun cleanRogueBooleanLiterals(text: String): String {
        if (text.isEmpty() || (!text.contains("true", ignoreCase = true) && !text.contains("false", ignoreCase = true))) {
            return text
        }
        return text.replace(Regex("(?<=\\s|^)(?:true|false)(?=\\s|$|[.,!?;:\"'”’])", RegexOption.IGNORE_CASE), "")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    private val QUESTION_WORDS = setOf(
        "ai", "gì", "đâu", "sao", "nào", "chưa", "hả", "thế", "chăng",
    )

    /**
     * Removes rogue question marks embedded inside names (caused by mojibake of middle dots,
     * e.g. "Noah? Pat" -> "Noah Pat" or "Noah?Pat" -> "Noah Pat").
     * Does not affect valid sentence-ending question marks.
     */
    fun cleanRogueNameQuestionMarks(text: String): String {
        if (!text.contains("?")) return text
        // 1. Unspaced mojibake: "Noah?Pat" -> "Noah Pat"
        var result = text.replace(Regex("""(?<=\p{L})\?(?=\p{L})"""), " ")
        // 2. Capitalized name + '?' + Capitalized surname: "Noah? Pat" -> "Noah Pat"
        result = result.replace(Regex("""(?<!\p{L})(\p{Lu}\p{Ll}{1,20})\s*\?\s+(\p{Lu}\p{Ll}{1,20})(?!\p{L})""")) { match ->
            val first = match.groupValues[1]
            val second = match.groupValues[2]
            if (first.lowercase() in QUESTION_WORDS) {
                match.value
            } else {
                "$first $second"
            }
        }
        return result.replace(Regex(" {2,}"), " ")
    }

    /**
     * Corrects contradictory kinship dialogue pronouns caused by calque translation or literal mapping.
     * E.g.
     * - "Em trai thân yêu của tôi, Angel. Nghe giọng điệu của anh..." -> "Em trai thân yêu của anh, Angel. Nghe giọng điệu của em..."
     * - "Anh trai thân yêu của tôi..." -> "Anh trai thân yêu của em..."
     */
    fun fixContradictoryDialoguePronouns(text: String): String {
        if (text.isBlank()) return text
        var result = text

        // 1. Possessive calque in direct dialogue greeting
        result = result.replace(
            Regex("""(?i)(“|")(?:người\s+)?em trai thân yêu của tôi\b"""),
            "$1Em trai thân yêu của anh",
        )
        result = result.replace(
            Regex("""(?i)(“|")(?:người\s+)?anh trai thân yêu của tôi\b"""),
            "$1Anh trai thân yêu của em",
        )
        result = result.replace(
            Regex("""(?i)(“|")(?:người\s+)?chị gái thân yêu của tôi\b"""),
            "$1Chị gái thân yêu của em",
        )
        result = result.replace(
            Regex("""(?i)(“|")(?:người\s+)?em gái thân yêu của tôi\b"""),
            "$1Em gái thân yêu của anh",
        )
        result = result.replace(
            Regex("""(?i)(“|")(?:đứa\s+)?cháu thân yêu của tôi\b"""),
            "$1Cháu thân yêu của ta",
        )

        // 2. Intra-sentence contradiction when elder sibling addresses younger sibling
        result = result.replace(
            Regex("""((?:“|")\s*Em trai thân yêu của anh[^\n”"]*?[.!?][\s\u3000]*[^\n”"]*?giọng điệu của\s+)anh\b"""),
        ) { match -> "${match.groupValues[1]}em" }
        result = result.replace(
            Regex("""((?:“|")\s*Em trai thân yêu của anh[^\n”"]*?[.!?][\s\u3000]*[^\n”"]*?chẳng lẽ\s+)anh(\s+biết\b)"""),
        ) { match -> "${match.groupValues[1]}em${match.groupValues[2]}" }
        result = result.replace(
            Regex("""((?:“|")\s*Em trai thân yêu của anh[^\n”"]*?[.!?][\s\u3000]*[^\n”"]*?hôm nay\s+)tôi(\s+sẽ\s+(?:đến|tới)\b)"""),
        ) { match -> "${match.groupValues[1]}anh${match.groupValues[2]}" }

        return result
    }

    /**
     * Replaces rogue Han-Viet transliterations of well-known Western character/title names
     * with their canonical forms. E.g. "đại sư Mai Kiệt Phu" -> "đại sư Medvedev",
     * "Mai Kiệt Phu" -> "Medvedev".
     */
    fun fixRogueForeignHanVietNames(text: String): String {
        if (text.isBlank()) return text
        var result = text
        // Medvedev (Mai Kiệt Phu)
        result = result.replace(Regex("""(?i)\bđại\s+sư\s+Mai\s+Kiệt\s+Phu\b"""), "đại sư Medvedev")
        result = result.replace(Regex("""(?i)\bMai\s+Kiệt\s+Phu\b"""), "Medvedev")
        return result
    }

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

    /**
     * Thụt đầu dòng 2 khoảng trắng cho đoạn văn tự sự.
     * KHÔNG thụt dòng thoại bắt đầu bằng —, –, -, ", “, « hoặc dòng trống.
     */
    fun indentNarrativeParagraphs(text: String): String {
        if (text.isBlank()) return text
        val dialogueStarters = setOf('—', '–', '-', '"', '“', '«', '”', '\u2014', '\u2013', '\u201C', '\u201D')
        return text.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.isBlank() -> ""
                trimmed.firstOrNull() in dialogueStarters -> trimmed
                else -> "  $trimmed"
            }
        }
    }
}
