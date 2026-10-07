package io.legado.app.domain.model

object MlKitPronounNeutralizer {

    private val QUOTE_REGEX = Regex("""(“[^”]*”|"[^"]*"|「[^」]*」|『[^』]*』|«[^»]*»|【[^】]*】|\[[^\]]*\])""")

    private val STANDALONE_HO_REGEX = Regex(
        """(?iu)(?<![\p{L}\p{N}])họ(?![\p{L}\p{N}])"""
    )

    private val PRECEDING_HO_EXCLUSIONS = setOf("dòng", "gia", "cùng", "đồng", "nhà", "bọn", "chúng")
    private val FOLLOWING_HO_EXCLUSIONS = setOf("hàng", "tên", "tộc", "nội", "ngoại")

    fun neutralize(
        text: String,
        mode: QuickTranslationPronounMode = QuickTranslationPronounMode.AUTO,
    ): String {
        if (text.isBlank() || mode == QuickTranslationPronounMode.OFF) {
            return text
        }
        return text.lines().joinToString("\n") { line ->
            neutralizeLine(line, mode)
        }
    }

    private fun neutralizeLine(line: String, mode: QuickTranslationPronounMode): String {
        if (line.isBlank()) return line

        val trimmed = line.trimStart()
        val indent = line.substring(0, line.length - trimmed.length)

        if (trimmed.startsWith("—") || trimmed.startsWith("–") || (trimmed.startsWith("-") && trimmed.length > 1 && trimmed[1].isWhitespace())) {
            val dashChar = when {
                trimmed.startsWith("—") -> "—"
                trimmed.startsWith("–") -> "–"
                else -> "-"
            }
            val rest = trimmed.substring(dashChar.length)
            return indent + dashChar + neutralizeDashSegments(rest, mode)
        }

        return neutralizeNarrative(line, mode)
    }

    private fun neutralizeDashSegments(content: String, mode: QuickTranslationPronounMode): String {
        val delimiterRegex = Regex("""\s*[—–]\s*""")
        val matches = delimiterRegex.findAll(content).toList()
        val segments = content.split(delimiterRegex)

        if (segments.size <= 1) {
            // Entire content is dialogue without following speech tags
            return content
        }

        val sb = StringBuilder()
        for (i in segments.indices) {
            val segment = segments[i]
            if (i % 2 == 1) {
                // Speech tag / narrative segment
                sb.append(neutralizeNarrative(segment, mode))
            } else {
                // Dialogue segment - preserve quotes and text
                sb.append(segment)
            }
            if (i < matches.size) {
                sb.append(matches[i].value)
            }
        }
        return sb.toString()
    }

    private fun neutralizeNarrative(text: String, mode: QuickTranslationPronounMode): String {
        if (text.isBlank()) return text

        val quotes = mutableListOf<String>()
        val placeholderPrefix = "\u0000QUOTE_"
        val placeholderSuffix = "\u0000"

        val masked = QUOTE_REGEX.replace(text) { match ->
            val index = quotes.size
            quotes.add(match.value)
            "$placeholderPrefix$index$placeholderSuffix"
        }

        val processed = applyPronounRules(masked, mode)

        var unmasked = processed
        quotes.forEachIndexed { index, quote ->
            unmasked = unmasked.replace("$placeholderPrefix$index$placeholderSuffix", quote)
        }
        return unmasked
    }

    private fun applyPronounRules(text: String, mode: QuickTranslationPronounMode): String {
        var result = text

        // 1. Eliminate subject token repetitions like "Tô Hiểu Tôi ", "Su Xiao Tôi "
        result = result.replace(Regex("""(?iu)(?<![\p{L}\p{N}])(Tô Hiểu|Su Xiao|[\p{Lu}\p{Lt}][\p{Ll}\p{Lo}]+(?:\s+[\p{Lu}\p{Lt}][\p{Ll}\p{Lo}]+)?)\s+Tôi\s+"""), "$1 ")

        val thirdPersonMasculine = when (mode) {
            QuickTranslationPronounMode.MODERN -> "anh"
            QuickTranslationPronounMode.WESTERN -> "chàng"
            QuickTranslationPronounMode.ANCIENT,
            QuickTranslationPronounMode.AUTO,
            QuickTranslationPronounMode.OFF -> "hắn"
        }
        val thirdPersonFeminine = when (mode) {
            QuickTranslationPronounMode.MODERN -> "cô"
            QuickTranslationPronounMode.WESTERN,
            QuickTranslationPronounMode.ANCIENT,
            QuickTranslationPronounMode.AUTO,
            QuickTranslationPronounMode.OFF -> "nàng"
        }

        when (mode) {
            QuickTranslationPronounMode.ANCIENT, QuickTranslationPronounMode.AUTO,
            QuickTranslationPronounMode.MODERN, QuickTranslationPronounMode.WESTERN -> {
                // Plural pronouns
                result = replaceDetachedWord(result, "các anh ấy", "bọn họ")
                result = replaceDetachedWord(result, "các cô ấy", "bọn họ")
                result = replaceDetachedWord(result, "các anh ta", "bọn họ")
                result = replaceDetachedWord(result, "các cô ta", "bọn họ")
                result = replaceDetachedWord(result, "bọn anh ấy", "bọn họ")
                result = replaceDetachedWord(result, "bọn cô ấy", "bọn họ")
                result = replaceDetachedWord(result, "những người họ", "bọn họ")

                // Singular masculine / general 3rd person
                result = replaceDetachedWord(result, "anh ta", thirdPersonMasculine)
                result = replaceDetachedWord(result, "anh ấy", thirdPersonMasculine)
                result = replaceDetachedWord(result, "hắn ta", thirdPersonMasculine)

                // Singular feminine 3rd person
                result = replaceDetachedWord(result, "cô ta", thirdPersonFeminine)
                result = replaceDetachedWord(result, "cô ấy", thirdPersonFeminine)

                // Elder pronouns
                result = replaceDetachedWord(result, "ông ấy", "ông ta")
                result = replaceDetachedWord(result, "bà ấy", "bà ta")

                if (mode != QuickTranslationPronounMode.MODERN) {
                    result = replaceDetachedWord(result, "của họ", "của $thirdPersonMasculine")
                    result = replaceThirdPersonPluralHo(result)
                }

                // Neutralize 2nd-person narrative leaks outside quotes
                result = replaceDetachedWord(result, "của bạn", "của $thirdPersonMasculine")
                result = replaceDetachedWord(result, "trước mặt bạn", "trước mắt $thirdPersonMasculine")
                result = replaceDetachedWord(result, "trước mắt bạn", "trước mắt $thirdPersonMasculine")
                result = replaceDetachedWord(result, "nếu bạn", "nếu $thirdPersonMasculine")
                result = replaceDetachedWord(result, "khi bạn", "khi $thirdPersonMasculine")
                result = replaceDetachedWord(result, "bạn có thể đoán", "có thể đoán được")
                result = replaceDetachedWord(result, "bạn có thể thấy", "có thể thấy")
                result = replaceDetachedWord(result, "bạn có thể biết", "có thể biết")
                result = replaceDetachedWord(result, "bạn có thể", "$thirdPersonMasculine có thể")
                result = replaceDetachedWord(result, "bạn không thể", "$thirdPersonMasculine không thể")
                result = replaceDetachedWord(result, "bạn phải", "$thirdPersonMasculine phải")
                result = replaceDetachedWord(result, "bạn cần", "$thirdPersonMasculine cần")
                result = replaceDetachedWord(result, "bạn đã", "$thirdPersonMasculine đã")
                result = replaceDetachedWord(result, "bạn sẽ", "$thirdPersonMasculine sẽ")
                result = replaceDetachedWord(result, "bạn đang", "$thirdPersonMasculine đang")
                result = replaceDetachedWord(result, "bạn có", "$thirdPersonMasculine có")
                result = replaceDetachedWord(result, "bạn là", "$thirdPersonMasculine là")
                result = replaceDetachedWord(result, "bạn không", "$thirdPersonMasculine không")

                // Neutralize 1st-person narrative leaks outside quotes
                result = replaceDetachedWord(result, "tôi cảm thấy", "$thirdPersonMasculine cảm thấy")
                result = replaceDetachedWord(result, "tôi sẽ cố gắng", "$thirdPersonMasculine cố gắng")
                result = replaceDetachedWord(result, "tôi sẽ", "$thirdPersonMasculine sẽ")
                result = replaceDetachedWord(result, "tôi muốn", "$thirdPersonMasculine muốn")
                result = replaceDetachedWord(result, "tôi nghĩ", "$thirdPersonMasculine nghĩ")
                result = replaceDetachedWord(result, "tôi thấy", "$thirdPersonMasculine thấy")
                result = replaceDetachedWord(result, "tôi biết", "$thirdPersonMasculine biết")
                result = replaceDetachedWord(result, "tôi đã", "$thirdPersonMasculine đã")
                result = replaceDetachedWord(result, "tôi liền", "$thirdPersonMasculine liền")
                result = replaceDetachedWord(result, "tôi không", "$thirdPersonMasculine không")
            }
            QuickTranslationPronounMode.OFF -> {
                // Do not modify
            }
        }
        return result
    }

    private fun replaceDetachedWord(text: String, target: String, replacement: String): String {
        if (!text.contains(target, ignoreCase = true)) return text
        val escaped = Regex.escape(target)
        val regex = Regex("""(?iu)(?<![\p{L}\p{N}])$escaped(?![\p{L}\p{N}])""")
        return regex.replace(text) { matchResult ->
            val matched = matchResult.value
            when {
                matched.all { it.isUpperCase() || !it.isLetter() } -> replacement.uppercase()
                matched.first().isUpperCase() -> replacement.replaceFirstChar { it.uppercase() }
                else -> replacement.lowercase()
            }
        }
    }

    private fun replaceThirdPersonPluralHo(text: String): String {
        if (!text.contains("họ", ignoreCase = true)) return text
        return STANDALONE_HO_REGEX.replace(text) { matchResult ->
            val matchRange = matchResult.range
            val matchedText = matchResult.value

            // 1. Check preceding token (skip whitespace backwards)
            val prefix = text.substring(0, matchRange.first).trimEnd()
            val precedingWord = prefix.takeLastWhile { it.isLetter() }.lowercase()
            if (precedingWord in PRECEDING_HO_EXCLUSIONS) {
                return@replace matchedText
            }

            // 2. Check following token (skip whitespace forwards)
            val suffix = text.substring(matchRange.last + 1)
            val trimmedSuffix = suffix.trimStart()
            val followingWord = trimmedSuffix.takeWhile { it.isLetter() }
            if (followingWord.isNotBlank()) {
                val followingLower = followingWord.lowercase()
                if (followingLower in FOLLOWING_HO_EXCLUSIONS) {
                    return@replace matchedText
                }
                // If following word starts with uppercase letter (surname like "họ Lý", "họ Trần")
                if (followingWord.first().isUpperCase()) {
                    return@replace matchedText
                }
            }

            if (matchedText.first().isUpperCase()) "Bọn họ" else "bọn họ"
        }
    }
}
