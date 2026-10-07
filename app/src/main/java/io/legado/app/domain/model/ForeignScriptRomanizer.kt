package io.legado.app.domain.model

object JapaneseKanaRomajizer {

    private val DIGRAPHS = mapOf(
        // Hiragana digraphs
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        // Katakana digraphs
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
        // Foreign Katakana loanwords
        "ティ" to "ti", "ディ" to "di", "トゥ" to "tu", "ドゥ" to "du",
        "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo",
        "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
        "ヴァ" to "va", "ヴィ" to "vi", "ヴ" to "vu", "ヴェ" to "ve", "ヴォ" to "vo",
        "シェ" to "she", "ジェ" to "je", "チェ" to "che",
    )

    private val MONOGRAPHS = mapOf(
        // Hiragana
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "o", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        // Katakana
        "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
        "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヲ" to "o", "ン" to "n",
        "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
        "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
        "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po",
        "ヴ" to "vu",
    )

    fun hasKana(text: String): Boolean {
        return text.any { it.code in 0x3040..0x30FF || it.code in 0x31F0..0x31FF || it.code in 0xFF66..0xFF9F }
    }

    fun toRomaji(text: String): String {
        if (!hasKana(text)) return text
        val sb = StringBuilder()
        var i = 0
        var doubleNextConsonant = false

        while (i < text.length) {
            val c = text[i]

            // Script boundary transition (Hiragana <-> Katakana)
            if (i > 0) {
                val prev = text[i - 1]
                if ((prev.code in 0x3040..0x309F && c.code in 0x30A0..0x30FF) ||
                    (prev.code in 0x30A0..0x30FF && c.code in 0x3040..0x309F)) {
                    if (sb.isNotEmpty() && sb.last() != ' ') {
                        sb.append(' ')
                    }
                }
            }

            // Middle dot (・) or whitespace
            if (c == '・' || c == '·' || c == '•') {
                if (sb.isNotEmpty() && sb.last() != ' ') {
                    sb.append(' ')
                }
                i++
                continue
            }

            // Sokuon (っ / ッ)
            if (c == 'っ' || c == 'ッ') {
                doubleNextConsonant = true
                i++
                continue
            }

            // Long vowel mark (ー)
            if (c == 'ー') {
                i++
                continue
            }

            // Try 2-char digraph
            if (i + 1 < text.length) {
                val pair = text.substring(i, i + 2)
                val romaji = DIGRAPHS[pair]
                if (romaji != null) {
                    if (doubleNextConsonant) {
                        val firstConsonant = if (romaji.startsWith("ch")) 't' else romaji.first()
                        sb.append(firstConsonant)
                        doubleNextConsonant = false
                    }
                    sb.append(romaji)
                    i += 2
                    continue
                }
            }

            // Try 1-char monograph
            val single = c.toString()
            val romaji = MONOGRAPHS[single]
            if (romaji != null) {
                if (doubleNextConsonant) {
                    val firstConsonant = if (romaji.startsWith("ch")) 't' else romaji.first()
                    sb.append(firstConsonant)
                    doubleNextConsonant = false
                }
                sb.append(romaji)
            } else {
                doubleNextConsonant = false
                sb.append(c)
            }
            i++
        }

        return formatWordsTitleCase(sb.toString())
    }
}

object KoreanHangulRomanizer {

    private val INITIALS = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )

    private val VOWELS = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    )

    private val FINALS = arrayOf(
        "", "g", "kk", "ks", "n", "nj", "nh", "d", "l", "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "b", "bs", "s", "ss", "ng", "j", "ch", "k", "t", "p", "h"
    )

    fun hasHangul(text: String): Boolean {
        return text.any { it.code in 0xAC00..0xD7AF || it.code in 0x1100..0x11FF || it.code in 0x3130..0x318F }
    }

    fun toLatin(text: String): String {
        if (!hasHangul(text)) return text
        val sb = StringBuilder()

        for (c in text) {
            val code = c.code
            if (code in 0xAC00..0xD7AF) {
                val syllableIndex = code - 0xAC00
                val initialIndex = syllableIndex / 588
                val vowelIndex = (syllableIndex % 588) / 28
                val finalIndex = syllableIndex % 28

                val initial = INITIALS[initialIndex]
                val vowel = VOWELS[vowelIndex]
                val finalConsonant = FINALS[finalIndex]

                sb.append(initial).append(vowel).append(finalConsonant)
            } else {
                sb.append(c)
            }
        }

        return formatWordsTitleCase(sb.toString())
    }
}

internal fun formatWordsTitleCase(text: String): String {
    if (text.isBlank()) return text
    return text.split(Regex("""\s+""")).filter { it.isNotBlank() }.joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
