package io.legado.app.model.tts

/** Vietnamese grapheme-to-phoneme frontend used by the Valtec Android reference runtime. */
internal class VietnameseG2p {
    private lateinit var symbolToId: Map<String, Int>
    private var languageId: Int = 7

    private val toneMap = mapOf(1 to 0, 2 to 2, 3 to 3, 4 to 4, 5 to 1, 6 to 5)
    private val toneCharacters = mapOf(
        'à' to 2, 'ằ' to 2, 'ầ' to 2, 'è' to 2, 'ề' to 2, 'ì' to 2, 'ò' to 2,
        'ồ' to 2, 'ờ' to 2, 'ù' to 2, 'ừ' to 2, 'ỳ' to 2,
        'á' to 5, 'ắ' to 5, 'ấ' to 5, 'é' to 5, 'ế' to 5, 'í' to 5, 'ó' to 5,
        'ố' to 5, 'ớ' to 5, 'ú' to 5, 'ứ' to 5, 'ý' to 5,
        'ả' to 4, 'ẳ' to 4, 'ẩ' to 4, 'ẻ' to 4, 'ể' to 4, 'ỉ' to 4, 'ỏ' to 4,
        'ổ' to 4, 'ở' to 4, 'ủ' to 4, 'ử' to 4, 'ỷ' to 4,
        'ã' to 3, 'ẵ' to 3, 'ẫ' to 3, 'ẽ' to 3, 'ễ' to 3, 'ĩ' to 3, 'õ' to 3,
        'ỗ' to 3, 'ỡ' to 3, 'ũ' to 3, 'ữ' to 3, 'ỹ' to 3,
        'ạ' to 6, 'ặ' to 6, 'ậ' to 6, 'ẹ' to 6, 'ệ' to 6, 'ị' to 6, 'ọ' to 6,
        'ộ' to 6, 'ợ' to 6, 'ụ' to 6, 'ự' to 6, 'ỵ' to 6,
    )
    private val onsetMappings = mapOf(
        "ngh" to "ŋ", "ng" to "ŋ", "nh" to "ɲ", "ch" to "c", "tr" to "ʈ",
        "th" to "tʰ", "ph" to "f", "kh" to "x", "gh" to "ɣ", "gi" to "z",
        "qu" to "kw", "đ" to "d", "c" to "k", "d" to "z", "g" to "ɣ",
        "b" to "b", "h" to "h", "k" to "k", "l" to "l", "m" to "m",
        "n" to "n", "p" to "p", "r" to "r", "s" to "s", "t" to "t",
        "v" to "v", "x" to "s",
    )
    private val codaMappings = mapOf(
        "ng" to "ŋ", "nh" to "ɲ", "ch" to "k", "c" to "k", "m" to "m",
        "n" to "n", "p" to "p", "t" to "t",
    )
    private val vowels = mapOf(
        'a' to "a", 'ă' to "a", 'â' to "ə", 'e' to "ɛ", 'ê' to "e", 'i' to "i",
        'y' to "i", 'o' to "ɔ", 'ô' to "o", 'ơ' to "ɤ", 'u' to "u", 'ư' to "ɯ",
    )
    private val diphthongs = mapOf(
        "ai" to listOf("a", "j"), "ay" to listOf("a", "j"), "ây" to listOf("ə", "j"),
        "ao" to listOf("a", "w"), "au" to listOf("a", "w"), "âu" to listOf("ə", "w"),
        "oi" to listOf("ɔ", "j"), "ôi" to listOf("o", "j"), "ơi" to listOf("ɤ", "j"),
        "ui" to listOf("u", "j"), "ưi" to listOf("ɯ", "j"), "eo" to listOf("ɛ", "w"),
        "êu" to listOf("e", "w"), "iu" to listOf("i", "w"), "ưu" to listOf("ɯ", "w"),
        "ia" to listOf("i", "ə"), "iê" to listOf("i", "ə"), "ua" to listOf("u", "ə"),
        "uô" to listOf("u", "ə"), "ưa" to listOf("ɯ", "ə"), "ươ" to listOf("ɯ", "ə"),
    )

    fun initialize(symbolToId: Map<String, Int>, languageId: Int) {
        this.symbolToId = symbolToId
        this.languageId = languageId
    }

    fun encode(text: String): Triple<List<Int>, List<Int>, List<Int>> {
        val phones = mutableListOf<Int>()
        val tones = mutableListOf<Int>()
        val languages = mutableListOf<Int>()
        text.lowercase().split(Regex("\\s+")).forEach { token ->
            if (token.isEmpty()) return@forEach
            val word = token.trimEnd(*PUNCTUATION)
            if (word.isNotEmpty()) {
                val (wordPhones, sourceTone) = syllable(word)
                val tone = toneMap[sourceTone] ?: 0
                wordPhones.forEach { phone ->
                    phones += symbolToId[phone] ?: symbolToId["UNK"] ?: 305
                    tones += tone + VI_TONE_OFFSET
                    languages += languageId
                }
            }
            token.drop(word.length).forEach { punctuation ->
                phones += symbolToId[punctuation.toString()] ?: symbolToId["UNK"] ?: 305
                tones += VI_TONE_OFFSET
                languages += languageId
            }
        }
        val boundary = symbolToId["_"] ?: 0
        phones.add(0, boundary)
        phones += boundary
        tones.add(0, VI_TONE_OFFSET)
        tones += VI_TONE_OFFSET
        languages.add(0, languageId)
        languages += languageId
        return addBlanks(phones, tones, languages)
    }

    private fun syllable(word: String): Pair<List<String>, Int> {
        val result = mutableListOf<String>()
        var remaining = word
        val tone = word.firstNotNullOfOrNull { toneCharacters[it] } ?: 1
        for (length in listOf(3, 2, 1)) {
            val onset = remaining.take(length)
            val mapped = onsetMappings[onset] ?: continue
            result += mapped
            remaining = remaining.drop(length)
            break
        }
        var coda = ""
        val cleanRemaining = remaining.map(::removeTone).joinToString("")
        for (length in listOf(2, 1)) {
            val candidate = cleanRemaining.takeLast(length)
            val mapped = codaMappings[candidate] ?: continue
            coda = mapped
            remaining = remaining.dropLast(length)
            break
        }
        val nucleus = remaining.map(::removeTone).joinToString("")
        val pair = diphthongs.entries.firstOrNull { nucleus == it.key || nucleus.endsWith(it.key) }
        if (pair != null) {
            result += pair.value
        } else {
            nucleus.forEach { character -> vowels[character]?.let(result::add) }
        }
        if (coda.isNotEmpty()) result += coda
        return result to tone
    }

    private fun addBlanks(
        phones: List<Int>,
        tones: List<Int>,
        languages: List<Int>,
    ): Triple<List<Int>, List<Int>, List<Int>> {
        val outputPhones = ArrayList<Int>(phones.size * 2 + 1)
        val outputTones = ArrayList<Int>(phones.size * 2 + 1)
        val outputLanguages = ArrayList<Int>(phones.size * 2 + 1)
        phones.indices.forEach { index ->
            outputPhones += 0
            outputTones += 0
            outputLanguages += languageId
            outputPhones += phones[index]
            outputTones += tones[index]
            outputLanguages += languages[index]
        }
        outputPhones += 0
        outputTones += 0
        outputLanguages += languageId
        return Triple(outputPhones, outputTones, outputLanguages)
    }

    private fun removeTone(character: Char): Char = when (character) {
        'à', 'á', 'ả', 'ã', 'ạ' -> 'a'; 'ằ', 'ắ', 'ẳ', 'ẵ', 'ặ' -> 'ă'
        'ầ', 'ấ', 'ẩ', 'ẫ', 'ậ' -> 'â'; 'è', 'é', 'ẻ', 'ẽ', 'ẹ' -> 'e'
        'ề', 'ế', 'ể', 'ễ', 'ệ' -> 'ê'; 'ì', 'í', 'ỉ', 'ĩ', 'ị' -> 'i'
        'ò', 'ó', 'ỏ', 'õ', 'ọ' -> 'o'; 'ồ', 'ố', 'ổ', 'ỗ', 'ộ' -> 'ô'
        'ờ', 'ớ', 'ở', 'ỡ', 'ợ' -> 'ơ'; 'ù', 'ú', 'ủ', 'ũ', 'ụ' -> 'u'
        'ừ', 'ứ', 'ử', 'ữ', 'ự' -> 'ư'; 'ỳ', 'ý', 'ỷ', 'ỹ', 'ỵ' -> 'y'
        else -> character
    }

    companion object {
        private const val VI_TONE_OFFSET = 16
        private val PUNCTUATION = charArrayOf(',', '.', '!', '?', ';', ':', '\'', '"', ')', ']', '}')
    }
}

