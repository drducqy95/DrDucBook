package io.legado.app.domain.model

/**
 * Post-processor for Google ML Kit Vietnamese translations.
 *
 * Repairs common English-pivot NMT translation artifacts, including:
 * 1. Adverbial calques where post-modifier adverbs are misplaced at clause end ("tàn sát một đất nước một mình")
 * 2. Adjective + infinitive calques ("rất đơn giản để làm")
 * 3. Copula & symptom calques ("Điều này là rất quan trọng", "có một số đau đầu")
 * 4. Passive & usage calques ("khác tạm thời không thể được sử dụng")
 * 5. Pronoun drift where 2nd-person pronouns leak into 3rd-person narrative ("trước mặt bạn", "bạn có thể đoán")
 * 6. Rogue mid-sentence capitalization of verbs and adverbs following subjects/names ("Tô Hiểu Cố gắng", "Tô Hiểu Bây giờ")
 */
object MlKitGrammarPostProcessor {

    private val QUOTE_REGEX = Regex("""(“[^”]*”|"[^"]*"|「[^」]*」|『[^』]*』|«[^»]*»|【[^】]*】|\[[^\]]*\])""")

    fun process(
        text: String,
        sourceCjk: String = "",
        pronounMode: QuickTranslationPronounMode = QuickTranslationPronounMode.AUTO,
    ): String {
        if (text.isBlank()) return text

        // 1. Normalize malformed bracket and quote lines from ML Kit
        val normalized = normalizeBracketsAndQuotes(text, sourceCjk)

        // 2. Mask quotes and brackets to isolate narrative rules from dialogue / system prompts
        val quotes = mutableListOf<String>()
        val placeholderPrefix = "\u0000QUOTE_"
        val placeholderSuffix = "\u0000"

        val masked = QUOTE_REGEX.replace(normalized) { match ->
            val index = quotes.size
            quotes.add(match.value)
            "$placeholderPrefix$index$placeholderSuffix"
        }

        var result = masked

        // 0. Remove residual placeholder artifacts like "___", "__", or stray "ent_X"
        result = result.replace(Regex("""_{2,}"""), "")
        result = result.replace(Regex("""(?iu)(?<![\p{L}\p{N}])(?:ENT|ent)_\d+\s*"""), "")

        // 1. Repair unwanted mid-sentence capitalization after subjects/names
        result = repairMidSentenceCapitalization(result)

        // 2. Heal English-pivot syntactic adverbial calques ("... một mình")
        result = fixAloneAdverbialCalque(result)

        // 3. Heal English-pivot adjective + infinitive calques
        result = fixAdjectiveInfinitiveCalques(result)

        // 4. Heal copula & symptom calques
        result = fixCopulaAndSymptomCalques(result)

        // 5. Heal passive and usage calques
        result = fixPassiveUsageCalques(result)

        // 6. Heal second-person pronoun drifts in 3rd-person narrative
        result = fixPronounDrifts(result, pronounMode)

        // 7. Heal duplicate verbs, clothing calques, and distorted phrases
        result = fixDuplicateAndDistortedPhrases(result)

        // 8. Restore dialogue quotes and system prompts
        var unmasked = result
        quotes.forEachIndexed { index, quote ->
            val cleanedQuote = cleanLeadingPunctuationInQuote(quote)
            unmasked = unmasked.replace("$placeholderPrefix$index$placeholderSuffix", cleanedQuote)
        }

        // 9. Clean excessive spaces around punctuation and brackets
        unmasked = unmasked.replace(Regex("""\s+([,.:;!?])"""), "$1")
        unmasked = unmasked.replace(Regex("""([“‘\[【(])\s+"""), "$1")
        unmasked = unmasked.replace(Regex("""\s+([”’\]】)])"""), "$1")

        return unmasked
    }

    private fun normalizeBracketsAndQuotes(text: String, sourceCjk: String): String {
        return text.lines().joinToString("\n") { line ->
            var l = line.trimEnd()
            if ((l.startsWith("[") || l.startsWith("【")) && (l.endsWith("\"") || l.endsWith("”") || l.endsWith("」"))) {
                l = l.dropLast(1).trimEnd()
                if (l.endsWith(".")) {
                    l = l.dropLast(1).trimEnd()
                }
                if (!l.endsWith("]") && !l.endsWith("】")) {
                    if (l.startsWith("【") || sourceCjk.contains("【")) {
                        if (l.startsWith("[")) l = "【" + l.substring(1)
                        "$l】"
                    } else {
                        "$l]"
                    }
                } else {
                    l
                }
            } else {
                l
            }
        }
    }

    private fun cleanLeadingPunctuationInQuote(quote: String): String {
        return quote.replace(Regex("""^([“‘"\[【])\s*[,，、]\s*"""), "$1")
    }

    private val MID_SENTENCE_CAPITALIZATION_REGEX = Regex(
        """(?iu)(?<![\p{L}\p{N}])(Tô Hiểu|Su Xiao|Luffy|Zoro|Madara|Uchiha|Naruto|Kakashi|Orochimaru|Liệp sát giả|Khế ước giả|Thợ săn|Chiến binh|Người chơi|Thiếu niên|Nam tử|Nữ tử|Đối phương|Hai người|Mọi người|hắn|nàng|anh|cô|ông|bà|bọn họ|\p{Lu}\p{Ll}+(?:\s+\p{Lu}\p{Ll}+)?)\s+(Không|Chưa|Chẳng|Đừng|Im|Cứ|Đều|Cùng|Tự|Lập|Trực|Hoàn|Toàn|Nhận|Khám|Gắng|Cố|Dường|Hình|Tựa|Như|Theo|Rời|Tới|Về|Xuống|Lên|Cố gắng|Bây giờ|Đang|Đã|Sẽ|Có thể|Có|Là|Đi|Đến|Vào|Ra|Nhìn|Bước|Nghe|Nói|Nghĩ|Cảm thấy|Phát hiện|Bắt đầu|Muốn|Cần|Phải|Chỉ|Cũng|Lại|Liền|Vẫn|Từng|Vừa|Mới|Ngồi|Mặc|Đeo|Cầm|Nhặt|Biết|Thong thả|Ném|Rơi|Nhảy|Chạy|Quét|Xoay|Đứng|Thấy|Hiểu rõ|Hiểu được|Nhớ|Bị|Được|Rút|Cúi|Nằm|Chờ|Nắm|Giữ|Kéo|Đẩy|Bắn|Giơ|Đưa|Vung|Chém|Đâm|Thở|Hít|Cắn|Nuốt|Uống|Ăn|Chết|Sống|Trở|Tiến|Lùi|Ngã|Bay|Lăn|Chìm|Nổi|Dừng|Tiếp|Mang|Dựa|Ngoảnh|Quay|Lắc|Gật|Cười|Khóc|Quỳ)(?![\p{L}\p{N}])"""
    )

    private fun repairMidSentenceCapitalization(text: String): String {
        if (text.length < 5) return text
        return MID_SENTENCE_CAPITALIZATION_REGEX.replace(text) { match ->
            val subject = match.groupValues[1]
            val verbOrAdverb = match.groupValues[2]
            "$subject ${verbOrAdverb.replaceFirstChar { it.lowercase() }}"
        }
    }

    private fun fixDuplicateAndDistortedPhrases(text: String): String {
        var r = text
        // 1. Remove residual double underscores or placeholder leftovers
        r = r.replace(Regex("""_{2,}"""), "")

        // 2. Fix compound verb duplication ("Ngồi xổm Ngồi" -> "ngồi xổm", "mặc đồng phục mặc đồng phục" -> "mặc đồng phục")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])Ngồi\s+xổm\s+Ngồi(?!\p{L})"""), "ngồi xổm")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])ngồi\s+xổm\s+ngồi(?!\p{L})"""), "ngồi xổm")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])(mặc\s+đồng\s+phục)\s+\1(?!\p{L})"""), "$1")

        // 3. Fix repeated clothing/accessory verb ("mặc màu đen, mặc một chiếc mũ" -> "mặc đồ đen, đội một chiếc mũ")
        r = r.replace(Regex("""(?iu)mặc\s+màu\s+đen"""), "mặc đồ đen")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])mặc\s+(?:một\s+)?(?:chiếc\s+)?mũ(?!\p{L})"""), "đội một chiếc mũ")

        // 4. Fix "hãy để [hắn/nàng/người ta]" calque in narrative ("không thể hãy để hắn" -> "không làm hắn", "hãy để hắn" -> "khiến hắn")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])(?:không thể|không|chẳng thể|đủ để|cũng không)\s+hãy để\s+(hắn|nàng|người ta|anh ta|cô ta)(?![\p{L}\p{N}])""")) { match ->
            val prefix = match.value.substringBefore("hãy để").trim()
            val pronoun = match.groupValues[1]
            "$prefix làm $pronoun"
        }
        r = r.replace(Regex("""(?iu),\s*hãy để\s+(hắn|nàng|người ta|anh ta|cô ta)(?![\p{L}\p{N}])"""), ", khiến $1")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])đủ để hãy để(?!\p{L})"""), "đủ để khiến cho")

        // 5. Heal English-pivot calque "và bạn nên chết" / "bạn nên chết"
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])(?:và\s+)?bạn\s+nên\s+chết(?!\p{L})"""), "vốn dĩ hẳn là đã chết")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])(?:ngươi|hắn)\s+nên\s+chết(?!\p{L})"""), "vốn dĩ hẳn là đã chết")

        // 6. Heal English-pivot posture & symptom calques ("... của squat" -> "... đang ngồi xổm", "đầu của cái đầu" -> "đầu óc choáng váng")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])(\p{Lu}\p{Ll}+(?:\s+\p{Lu}\p{Ll}+)?|hắn|nàng)\s+của\s+squat(?!\p{L})"""), "$1 đang ngồi xổm")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])của\s+squat(?!\p{L})"""), "đang ngồi xổm")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])đầu\s+của\s+cái\s+đầu(?!\p{L})"""), "đầu óc choáng váng")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])(?:và\s+)?nó\s+bị\s+giết\s+bởi\s+kẻ\s+thù\s+vô\s+danh(?!\p{L})"""), "lại bị giết bởi kẻ thù vô danh")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])bộ\s+phận\s+bảo\s+vệ(?!\p{L})"""), "nhân viên bảo vệ")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])máu\s+bị\s+xịt(?!\p{L})"""), "máu tươi phun trào")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])không\s+có\s+máu\s+miễn\s+phí(?!\p{L})"""), "không có vết máu chảy ra")
        r = r.replace(Regex("""(?iu)(?<![\p{L}\p{N}])Yu\s*Guang(?:\s*quét)?(?!\p{L})"""), "khóe mắt quét qua")
        r = r.replace(Regex("""(?iu)thời\s+gian\s+âm\s+ỉ"""), "không khí oi bức")
        r = r.replace(Regex("""(?iu)phép\s+màu\s+có\s+khả\s+năng\s+sống"""), "kỳ tích sống sót")
        r = r.replace(Regex("""(?iu)đó\s+là\s+một\s+sự\s+xỏm\s+kỳ\s+diệu\s+của\s+bộ\s+ngực\s+an\s+ninh\s+súng"""), "lại kỳ tích đâm xuyên qua lồng ngực tên bảo vệ cầm súng")
        r = r.replace(Regex("""(?iu)sự\s+xỏm\s+kỳ\s+diệu"""), "kỳ tích đâm xuyên")
        r = r.replace(Regex("""(?iu)bộ\s+ngực\s+an\s+ninh\s+súng"""), "lồng ngực tên bảo vệ cầm súng")
        r = r.replace(Regex("""(?iu)Tô Hiểu\s+đang\s+ngồi\s+xổm\s+bị\s+đảo\s+ngược"""), "Tô Hiểu ngã gục sang một bên")
        r = r.replace(Regex("""(?iu)đang\s+ngồi\s+xổm\s+bị\s+đảo\s+ngược"""), "ngã gục sang một bên")

        // 7. Clean excessive spaces around punctuation and brackets
        r = r.replace(Regex("""\s+([,.:;!?])"""), "$1")
        r = r.replace(Regex("""([“‘\[【(])\s+"""), "$1")
        r = r.replace(Regex("""\s+([”’\]】)])"""), "$1")
        return r
    }

    private val ALONE_CALQUE_REGEX = Regex(
        """(?iu)(?<!\p{L})((?:đánh chìm|tàn sát|tiêu diệt|hủy diệt|đánh bại|chiến đấu|đối mặt|hoàn thành|xử lý|thực hiện|chiếm lĩnh|chém giết|sát hại)[^,.\n;!?]{1,60}?)\s+một mình(?!\p{L})"""
    )

    private fun fixAloneAdverbialCalque(text: String): String {
        if (!text.contains("một mình", ignoreCase = true)) return text
        return ALONE_CALQUE_REGEX.replace(text) { match ->
            val actionAndObject = match.groupValues[1].trim()
            "một mình $actionAndObject"
        }
    }

    private fun fixAdjectiveInfinitiveCalques(text: String): String {
        var r = text
        if (r.contains("để làm", ignoreCase = true) || r.contains("để thực hiện", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})(?:rất|khá)\s+đơn giản\s+(?:để làm|để thực hiện)(?!\p{L})"""), "rất dễ thực hiện")
            r = r.replace(Regex("""(?iu)(?<!\p{L})thật\s+đơn giản\s+(?:để làm|để thực hiện)(?!\p{L})"""), "thật dễ thực hiện")
            r = r.replace(Regex("""(?iu)(?<!\p{L})(?:rất|quá)\s+khó khăn\s+(?:để làm|để thực hiện)(?!\p{L})"""), "rất khó thực hiện")
            r = r.replace(Regex("""(?iu)(?<!\p{L})đơn giản\s+để làm(?!\p{L})"""), "dễ thực hiện")
        }
        return r
    }

    private fun fixCopulaAndSymptomCalques(text: String): String {
        var r = text
        if (r.contains("là rất", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})(điều này|điều đó|việc này|việc đó)\s+là\s+rất(?!\p{L})"""), "$1 rất")
        }
        if (r.contains("đau đầu", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})có một\s+(?:số|chút)\s+đau đầu(?!\p{L})"""), "hơi đau đầu")
            r = r.replace(Regex("""(?iu)(?<!\p{L})có\s+(?:chút|một chút)\s+đau đầu(?!\p{L})"""), "hơi đau đầu")
        }
        if (r.contains("nhức đầu", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})có một\s+(?:số|chút)\s+nhức đầu(?!\p{L})"""), "hơi nhức đầu")
        }
        return r
    }

    private fun fixPassiveUsageCalques(text: String): String {
        var r = text
        if (r.contains("sử dụng", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})khác\s+tạm thời\s+không thể\s+được\s+sử dụng(?!\p{L})"""), "các mục khác tạm thời chưa thể sử dụng")
            r = r.replace(Regex("""(?iu)(?<!\p{L})tạm thời\s+không thể\s+được\s+sử dụng(?!\p{L})"""), "tạm thời chưa thể sử dụng")
            r = r.replace(Regex("""(?iu)(?<!\p{L})không thể\s+được\s+sử dụng(?!\p{L})"""), "không thể sử dụng")
        }
        return r
    }

    private fun fixPronounDrifts(
        text: String,
        pronounMode: QuickTranslationPronounMode,
    ): String {
        var r = text
        val thirdPerson = when (pronounMode) {
            QuickTranslationPronounMode.MODERN,
            QuickTranslationPronounMode.WESTERN -> "anh"
            QuickTranslationPronounMode.ANCIENT,
            QuickTranslationPronounMode.AUTO,
            QuickTranslationPronounMode.OFF -> "hắn"
        }
        if (r.contains("trước mặt bạn", ignoreCase = true) || r.contains("trước mắt bạn", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})(?:trước mặt bạn|trước mắt bạn)(?!\p{L})"""), "trước mắt $thirdPerson")
        }
        if (r.contains("bạn có thể", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})bạn có thể đoán(?!\p{L})"""), "có thể đoán được")
            r = r.replace(Regex("""(?iu)(?<!\p{L})bạn có thể thấy(?!\p{L})"""), "có thể thấy")
            r = r.replace(Regex("""(?iu)(?<!\p{L})bạn có thể biết(?!\p{L})"""), "có thể biết")
            r = r.replace(Regex("""(?iu)(?<!\p{L})bạn có thể(?!\p{L})"""), "$thirdPerson có thể")
        }
        if (r.contains("bạn không thể", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})bạn không thể(?!\p{L})"""), "$thirdPerson không thể")
        }
        if (r.contains("bạn phải", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})bạn phải(?!\p{L})"""), "$thirdPerson phải")
        }
        if (r.contains("bạn cần", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})bạn cần(?!\p{L})"""), "$thirdPerson cần")
        }
        if (r.contains("của bạn", ignoreCase = true)) {
            r = r.replace(Regex("""(?iu)(?<!\p{L})trước mặt của bạn(?!\p{L})"""), "trước mắt $thirdPerson")
            r = r.replace(Regex("""(?iu)(?<!\p{L})của bạn(?!\p{L})"""), "của $thirdPerson")
        }
        return r
    }
}
