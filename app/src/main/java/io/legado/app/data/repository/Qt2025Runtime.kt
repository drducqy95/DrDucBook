package io.legado.app.data.repository

import java.text.NumberFormat
import java.util.Locale

/** Android reconstruction of the deterministic runtime stages in QT2025 TranslatorEngine.dll. */
internal class Qt2025Runtime private constructor(
    private val nameRules: List<Rule>,
    private val numberRules: List<Rule>,
    private val surnames: List<DictionaryEntry>,
    private val suffixes: List<DictionaryEntry>,
) {

    internal data class Match(
        val endExclusive: Int,
        val translation: String,
        val priority: Int,
        val kind: MatchKind,
    )

    internal enum class MatchKind { NAME_RULE, SURNAME_SUFFIX, NUMBER_RULE }

    internal fun matchAt(
        text: String,
        offset: Int,
        resolveName: (start: Int, endExclusive: Int) -> String?,
        containsExact: (String) -> Boolean,
    ): Match? {
        if (offset !in text.indices) return null
        val scanEnd = (offset + MAX_SCAN_CHARS).coerceAtMost(text.length)
        val input = normalizeNumberModifiers(text.substring(offset, scanEnd))
        matchRule(input, offset, nameRules, resolveName)?.let { return it }
        matchSurnameSuffix(text, offset, containsExact)?.let { return it }
        return matchRule(input, offset, numberRules, resolveName)
    }

    private fun normalizeNumberModifiers(text: String): String {
        if (text.length < 2) return text
        var index = 0
        var output: StringBuilder? = null
        while (index < text.length) {
            val current = text[index]
            val next = text.getOrNull(index + 1)
            if (
                (current == '余' || current == '多') &&
                next != null && next in NUMBER_SECTION_UNITS
            ) {
                val builder = output ?: StringBuilder(text.length)
                    .append(text, 0, index)
                    .also { output = it }
                builder.append(next).append(current)
                index += 2
            } else {
                output?.append(current)
                index++
            }
        }
        return output?.toString() ?: text
    }

    private fun matchRule(
        input: String,
        sourceOffset: Int,
        rules: List<Rule>,
        resolveName: (start: Int, endExclusive: Int) -> String?,
    ): Match? {
        rules.forEach { rule ->
            val match = rule.pattern.find(input) ?: return@forEach
            if (match.range.first != 0) return@forEach
            var rendered = rule.replacement
            var effectiveEndExclusive = sourceOffset + match.value.length
            val values = ArrayList<String>(rule.slots.size)
            rule.slots.forEachIndexed { index, slot ->
                val group = match.groups[index + 1] ?: return@forEach
                val raw = group.value.trim()
                if (raw.isEmpty()) return@forEach
                val value = when (slot) {
                    Slot.NAME -> {
                        val absoluteStart = sourceOffset + group.range.first
                        val absoluteEnd = sourceOffset + group.range.last + 1
                        if (rule.source.endsWith("{n}")) {
                            var resolved: String? = null
                            for (candidateEnd in absoluteEnd downTo absoluteStart + 1) {
                                resolved = resolveName(absoluteStart, candidateEnd)
                                if (resolved != null) {
                                    effectiveEndExclusive = candidateEnd
                                    break
                                }
                            }
                            resolved ?: return@forEach
                        } else {
                            resolveName(absoluteStart, absoluteEnd) ?: return@forEach
                        }
                    }

                    Slot.NUMBER -> Qt2025Numbers.renderForRule(
                        raw = raw,
                        sourcePattern = rule.source,
                        replacement = rule.replacement,
                        slotNumber = index + 1,
                    ) ?: return@forEach
                }
                values += value.trim()
            }
            if (rule.slots.size == 1) {
                rendered = rendered
                    .replace("{n}", values[0])
                    .replace("{s}", values[0])
                    .replace("{1}", values[0])
            } else {
                values.forEachIndexed { index, value ->
                    rendered = rendered.replace("{${index + 1}}", value)
                }
            }
            rendered = renderSmallDayAsMung(rendered)
            return Match(
                endExclusive = effectiveEndExclusive,
                translation = rendered,
                priority = if (rule.slots.firstOrNull() == Slot.NAME) {
                    NAME_RULE_PRIORITY
                } else {
                    NUMBER_RULE_PRIORITY
                },
                kind = if (rule.slots.firstOrNull() == Slot.NAME) {
                    MatchKind.NAME_RULE
                } else {
                    MatchKind.NUMBER_RULE
                },
            )
        }
        return null
    }

    private fun renderSmallDayAsMung(rendered: String): String {
        if (!rendered.startsWith("ngày ", ignoreCase = true)) return rendered
        val dayStart = rendered.indexOf(' ') + 1
        var dayEnd = dayStart
        while (dayEnd < rendered.length && rendered[dayEnd].isDigit()) dayEnd++
        val day = rendered.substring(dayStart, dayEnd).toIntOrNull() ?: return rendered
        return if (day in 1..9) "mùng${rendered.substring(4)}" else rendered
    }

    private fun matchSurnameSuffix(
        text: String,
        offset: Int,
        containsExact: (String) -> Boolean,
    ): Match? {
        surnames.forEach { surname ->
            if (!text.startsWith(surname.source, offset)) return@forEach
            val suffixOffset = offset + surname.source.length
            suffixes.forEach { suffix ->
                if (!text.startsWith(suffix.source, suffixOffset)) return@forEach
                val endExclusive = suffixOffset + suffix.source.length
                if (endExclusive - offset !in 2..MAX_SURNAME_SUFFIX_CHARS) return@forEach
                val source = text.substring(offset, endExclusive)
                if (containsExact(source)) return@forEach
                return Match(
                    endExclusive = endExclusive,
                    translation = "${surname.target.trim()} ${suffix.target.trim()}",
                    priority = SURNAME_SUFFIX_PRIORITY,
                    kind = MatchKind.SURNAME_SUFFIX,
                )
            }
        }
        return null
    }

    private data class DictionaryEntry(
        val source: String,
        val target: String,
    )

    private data class Rule(
        val source: String,
        val replacement: String,
        val pattern: Regex,
        val slots: List<Slot>,
        val normalizedLength: Int,
    )

    private enum class Slot { NAME, NUMBER }

    internal companion object {
        private const val MAX_SCAN_CHARS = 20
        private const val MAX_SURNAME_SUFFIX_CHARS = 6
        private const val NUMBER_SECTION_UNITS = "百千万亿"
        private const val NAME_RULE_PRIORITY = 185
        private const val SURNAME_SUFFIX_PRIORITY = 175
        private const val NUMBER_RULE_PRIORITY = 165
        private const val NUMBER_CAPTURE =
            "((?:(?:\\d+(?:[.,]\\d+)?|[零一二三四五六七八九十百千万亿两〇点]+)\\s*)+)"
        private const val NAME_CAPTURE = "([^,，.。!?！？?\\s]{1,10}?)"

        private val TRAILING_NAME_CAPTURE = NAME_CAPTURE.replace("}?)", "})")

        fun create(
            rules: List<Pair<String, String>>,
            surnames: List<Pair<String, String>>,
            suffixes: List<Pair<String, String>>,
        ): Qt2025Runtime? {
            val compiled = rules.mapNotNull { (source, replacement) ->
                compileRule(source.trim(), replacement.trim())
            }
            val nameRules = compiled.filter { it.slots.firstOrNull() == Slot.NAME }
                .sortedWith(compareByDescending<Rule> { it.normalizedLength }.thenBy { it.source })
            val numberRules = compiled.filter { it.slots.firstOrNull() == Slot.NUMBER }
                .filterNot { it.source == "{s}" }
                .sortedWith(compareByDescending<Rule> { it.source.length }.thenBy { it.source })
            val surnameEntries = surnames.toEntries()
            val suffixEntries = suffixes.toEntries()
            if (compiled.isEmpty() && surnameEntries.isEmpty() && suffixEntries.isEmpty()) return null
            return Qt2025Runtime(nameRules, numberRules, surnameEntries, suffixEntries)
        }

        private fun compileRule(source: String, replacement: String): Rule? {
            if (source.isEmpty() || replacement.isEmpty() || source == "{h}{t}") return null
            val hasName = "{n}" in source
            val hasNumber = "{s}" in source
            if (hasName == hasNumber) return null
            val slotToken = if (hasName) "{n}" else "{s}"
            val slot = if (hasName) Slot.NAME else Slot.NUMBER
            val slots = mutableListOf<Slot>()
            val normalizedSource = source.replace("(", "(?:")
            val pattern = buildString(normalizedSource.length * 2) {
                var cursor = 0
                while (cursor < normalizedSource.length) {
                    if (normalizedSource.startsWith(slotToken, cursor)) {
                        if (slot == Slot.NUMBER) append("\\s*")
                        append(
                            when {
                                slot == Slot.NUMBER -> NUMBER_CAPTURE
                                cursor + slotToken.length == normalizedSource.length -> {
                                    TRAILING_NAME_CAPTURE
                                }
                                else -> NAME_CAPTURE
                            }
                        )
                        if (slot == Slot.NUMBER) append("\\s*")
                        slots += slot
                        cursor += slotToken.length
                    } else if (normalizedSource[cursor].isWhitespace()) {
                        append("\\s*")
                        while (
                            cursor < normalizedSource.length &&
                            normalizedSource[cursor].isWhitespace()
                        ) {
                            cursor++
                        }
                    } else {
                        append(normalizedSource[cursor])
                        cursor++
                    }
                }
            }
            val guardedPattern = if (source == "{s}两") {
                pattern + "(?!(?:[零一二三四五六七八九十百千万亿两〇\\d]+){1,2})"
            } else {
                pattern
            }
            return runCatching {
                Rule(
                    source = source,
                    replacement = replacement,
                    pattern = Regex("^$guardedPattern"),
                    slots = slots,
                    normalizedLength = normalizedRuleLength(source),
                )
            }.getOrNull()
        }

        private fun normalizedRuleLength(source: String): Int {
            return source
                .replace(Regex("\\([^)]*\\)")) { match ->
                    match.value.removeSurrounding("(", ")")
                        .split('|')
                        .minByOrNull(String::length)
                        .orEmpty()
                }
                .replace(Regex("\\[[^]]*]"), "c")
                .replace("{n}", "c")
                .replace("{s}", "c")
                .replace("?", "")
                .length
        }

        private fun List<Pair<String, String>>.toEntries(): List<DictionaryEntry> =
            asSequence()
                .map { (source, target) -> DictionaryEntry(source.trim(), target.trim()) }
                .filter { it.source.isNotEmpty() && it.target.isNotEmpty() }
                .distinctBy(DictionaryEntry::source)
                .sortedByDescending { it.source.length }
                .toList()
    }
}

private object Qt2025Numbers {
    private val mixedNumber = Regex("^(\\d+)\\s*([万亿])\\s*(\\d)$")
    private val complexRange = Regex("^([十百千]+?)([一二三四五六七八九])([一二三四五六七八九])([万亿])$")
    private val simpleRange = Regex("^([一二三四五六七八九两])([一二三四五六七八九])([十百千万亿])$")
    private val simpleRangeWithUnit =
        Regex("^([一二三四五六七八九两])([一二三四五六七八九])([十百千])([万亿])$")
    private val suffixRange = Regex("^(.*[万亿])([一二三四五六七八九])([一二三四五六七八九])$")
    private val digits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
        '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )
    private val units = mapOf('十' to 10L, '百' to 100L, '千' to 1_000L)
    private val integerFormatter = NumberFormat.getIntegerInstance(Locale.US)

    fun renderForRule(
        raw: String,
        sourcePattern: String,
        replacement: String,
        slotNumber: Int,
    ): String? {
        if (sourcePattern == "{s}两") {
            return parseInteger(raw)?.let { "${numberText(it)} lượng" }
        }
        if (sourcePattern.startsWith("百分")) return decimalString(raw)
        if (raw.contains('.') || raw.contains(',') || raw.length > 1 && raw.startsWith('0')) {
            return raw.replace(',', '.')
        }
        tryVietnameseRange(raw)?.let { return it }
        tryPostfixedRange(raw)?.let { return it }
        if (raw.length == 2 && raw.all(digits::containsKey)) {
            return "${digits.getValue(raw[0])}-${digits.getValue(raw[1])}"
        }
        val number = parseInteger(raw) ?: return null
        val numericContext = hasNumericReplacementContext(replacement, slotNumber)
        return if (numericContext) number.toString() else numberText(number)
    }

    private fun hasNumericReplacementContext(
        replacement: String,
        slotNumber: Int,
    ): Boolean {
        val normalized = replacement.lowercase(Locale.ROOT)
        return listOf("{s}", "{$slotNumber}").any { placeholder ->
            var searchFrom = 0
            while (searchFrom < normalized.length) {
                val placeholderIndex = normalized.indexOf(placeholder, searchFrom)
                if (placeholderIndex < 0) break
                val prefix = normalized.substring(0, placeholderIndex).trimEnd()
                if (prefix.endsWith("năm") || prefix.endsWith("chương")) return true
                searchFrom = placeholderIndex + placeholder.length
            }
            false
        }
    }

    private fun decimalString(raw: String): String? {
        val normalized = raw.trim().replace(',', '.')
        normalized.toBigDecimalOrNull()?.let { return normalized }
        val parts = normalized.split('点', limit = 2)
        if (parts.size == 2) {
            val integer = parseInteger(parts[0]) ?: return null
            val decimal = parts[1].map { char ->
                when {
                    char.isDigit() -> char
                    char in digits -> ('0'.code + digits.getValue(char)).toChar()
                    else -> return null
                }
            }.joinToString("")
            return "$integer.$decimal"
        }
        if (normalized.length >= 2 && normalized.all(digits::containsKey)) {
            return normalized.map { digits.getValue(it) }.joinToString("")
        }
        return parseInteger(normalized)?.toString()
    }

    private fun parseInteger(raw: String): Long? {
        val text = raw.filterNot(Char::isWhitespace)
        text.toLongOrNull()?.let { return it }
        mixedNumber.matchEntire(text)?.let { match ->
            val leading = match.groupValues[1].toLong()
            val section = if (match.groupValues[2] == "万") 10_000L else 100_000_000L
            return leading * section + match.groupValues[3].toLong() * (section / 10)
        }
        val hundredMillion = text.lastIndexOf('亿')
        if (hundredMillion >= 0) {
            val leading = text.substring(0, hundredMillion).takeIf(String::isNotEmpty)
                ?.let(::parseInteger) ?: 1L
            val trailing = text.substring(hundredMillion + 1)
            val base = leading * 100_000_000L
            if (trailing.length == 1 && trailing[0] in digits) {
                return base + digits.getValue(trailing[0]) * 10_000_000L
            }
            return base + (parseInteger(trailing) ?: 0L)
        }
        val tenThousand = text.lastIndexOf('万')
        if (tenThousand >= 0) {
            val leading = text.substring(0, tenThousand).takeIf(String::isNotEmpty)
                ?.let(::parseInteger) ?: 1L
            val trailing = text.substring(tenThousand + 1)
            val base = leading * 10_000L
            if (trailing.length == 1 && trailing[0] in digits) {
                return base + digits.getValue(trailing[0]) * 1_000L
            }
            return base + (parseInteger(trailing) ?: 0L)
        }
        if (text.length >= 3 && text.all(digits::containsKey)) {
            return text.map { digits.getValue(it) }.joinToString("").toLongOrNull()
        }
        if (text == "十") return 10L
        var result = 0L
        var digit: Long? = null
        text.forEach { char ->
            if (char in digits) {
                digit = digits.getValue(char).toLong()
            } else {
                val unit = units[char] ?: return null
                result += (digit ?: 1L) * unit
                digit = null
            }
        }
        return result + (digit ?: 0L)
    }

    private fun tryPostfixedRange(raw: String): String? {
        if (raw.length < 3) return null
        val first = digits[raw[raw.length - 2]] ?: return null
        val second = digits[raw.last()] ?: return null
        if (first == 0) return null
        val base = parseInteger(raw.dropLast(2)) ?: return null
        if (base <= 0 || base % 10 != 0L) return null
        return "${base + first}-${base + second}"
    }

    private fun tryVietnameseRange(raw: String): String? {
        complexRange.matchEntire(raw)?.let { match ->
            val baseText = match.groupValues[1]
            val first = digits.getValue(match.groupValues[2][0])
            val second = digits.getValue(match.groupValues[3][0])
            if (first >= second) return null
            val section = if (match.groupValues[4] == "万") 10_000L else 100_000_000L
            val step = when {
                baseText.endsWith('千') -> 100L
                baseText.endsWith('百') -> 10L
                else -> 1L
            }
            val base = parseInteger(baseText) ?: return null
            return "${numberText((base + first * step) * section)}-" +
                numberText((base + second * step) * section)
        }
        simpleRangeWithUnit.matchEntire(raw)?.let { match ->
            val first = digits.getValue(match.groupValues[1][0])
            val second = digits.getValue(match.groupValues[2][0])
            if (first >= second) return null
            val unit = units[match.groupValues[3][0]] ?: return null
            val label = if (match.groupValues[4] == "万") "vạn" else "ức"
            return "${first * unit}-${second * unit} $label"
        }
        simpleRange.matchEntire(raw)?.let { match ->
            val first = digits.getValue(match.groupValues[1][0])
            val second = digits.getValue(match.groupValues[2][0])
            if (first >= second) return null
            return when (val unit = match.groupValues[3][0]) {
                '亿' -> "$first-$second ức"
                '万' -> "$first-$second vạn"
                '千' -> "$first-$second ngàn"
                else -> "${first * (units[unit] ?: return null)}-${second * units.getValue(unit)}"
            }
        }
        suffixRange.matchEntire(raw)?.let { match ->
            val baseText = match.groupValues[1]
            val first = digits.getValue(match.groupValues[2][0])
            val second = digits.getValue(match.groupValues[3][0])
            if (first >= second) return null
            val step = if (baseText.endsWith('亿')) 10_000_000L else 1_000L
            val base = parseInteger(baseText) ?: return null
            return "${numberText(base + first * step)}-${numberText(base + second * step)}"
        }
        return null
    }

    private fun numberText(number: Long): String {
        if (number == 0L) return "0"
        if (number in -9_999L..9_999L) return integerFormatter.format(number)
        val parts = mutableListOf<String>()
        var remainder = number
        val hundredMillion = remainder / 100_000_000L
        if (hundredMillion > 0) {
            parts += "${numberText(hundredMillion)} ức"
            remainder %= 100_000_000L
        }
        val tenThousand = remainder / 10_000L
        if (tenThousand > 0) {
            parts += "${integerFormatter.format(tenThousand)} vạn"
            remainder %= 10_000L
        }
        if (remainder > 0) parts += integerFormatter.format(remainder)
        return parts.joinToString(" ")
    }
}
