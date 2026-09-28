package io.legado.app.model.tts

import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/**
 * Native Kotlin BPE (Byte Pair Encoding) tokenizer for ZeroTTS.
 * Parses HuggingFace `tokenizer.json` and implements the exact pre-tokenization
 * and BPE merge algorithm used by ZeroTTS without external native libraries.
 */
internal class ZeroTtsBpeTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val mergeRanks: Map<Pair<String, String>, Int>,
    private val unkId: Int,
) {

    /**
     * Encodes raw text into token IDs wrapped with <bos> (1) and <eot> (2).
     */
    fun encode(text: String, maxLength: Int = 512): LongArray {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) {
            return longArrayOf(BOS_ID.toLong(), EOT_ID.toLong())
        }
        val preTokens = preTokenize(normalized)
        val bodyIds = ArrayList<Long>(preTokens.size * 2)
        for (token in preTokens) {
            val subwords = bpe(token)
            for (subword in subwords) {
                val id = vocab[subword] ?: unkId
                bodyIds.add(id.toLong())
                if (bodyIds.size >= maxLength) break
            }
            if (bodyIds.size >= maxLength) break
        }
        val result = LongArray(bodyIds.size + 2)
        result[0] = BOS_ID.toLong()
        for (i in bodyIds.indices) {
            result[i + 1] = bodyIds[i]
        }
        result[result.size - 1] = EOT_ID.toLong()
        return result
    }

    private fun bpe(token: String): List<String> {
        if (token.length <= 1) return listOf(token)
        var symbols = ArrayList<String>(token.length)
        for (i in 0 until token.length) {
            symbols.add(token[i].toString())
        }
        while (symbols.size >= 2) {
            var minRank = Int.MAX_VALUE
            var bestPair: Pair<String, String>? = null
            for (i in 0 until symbols.size - 1) {
                val pair = Pair(symbols[i], symbols[i + 1])
                val rank = mergeRanks[pair]
                if (rank != null && rank < minRank) {
                    minRank = rank
                    bestPair = pair
                }
            }
            if (bestPair == null) break
            val first = bestPair.first
            val second = bestPair.second
            val merged = ArrayList<String>(symbols.size)
            var i = 0
            while (i < symbols.size) {
                if (i < symbols.size - 1 && symbols[i] == first && symbols[i + 1] == second) {
                    merged.add(first + second)
                    i += 2
                } else {
                    merged.add(symbols[i])
                    i++
                }
            }
            symbols = merged
        }
        return symbols
    }

    private fun preTokenize(text: String): List<String> {
        val tokens = ArrayList<String>()
        val currentWord = StringBuilder()
        for (i in 0 until text.length) {
            val ch = text[i]
            val type = Character.getType(ch).toByte()
            val isPunctOrSymbol = isPunctuationOrSymbol(type)
            if (Character.isWhitespace(ch)) {
                if (currentWord.isNotEmpty()) {
                    tokens.add(currentWord.toString())
                    currentWord.setLength(0)
                }
                tokens.add(ch.toString())
            } else if (Character.isDigit(ch)) {
                if (currentWord.isNotEmpty()) {
                    tokens.add(currentWord.toString())
                    currentWord.setLength(0)
                }
                tokens.add(ch.toString())
            } else if (isPunctOrSymbol) {
                if (currentWord.isNotEmpty()) {
                    tokens.add(currentWord.toString())
                    currentWord.setLength(0)
                }
                tokens.add(ch.toString())
            } else {
                currentWord.append(ch)
            }
        }
        if (currentWord.isNotEmpty()) {
            tokens.add(currentWord.toString())
        }
        return tokens
    }

    private fun isPunctuationOrSymbol(type: Byte): Boolean = when (type) {
        Character.CONNECTOR_PUNCTUATION,
        Character.DASH_PUNCTUATION,
        Character.START_PUNCTUATION,
        Character.END_PUNCTUATION,
        Character.INITIAL_QUOTE_PUNCTUATION,
        Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION,
        Character.MATH_SYMBOL,
        Character.CURRENCY_SYMBOL,
        Character.MODIFIER_SYMBOL,
        Character.OTHER_SYMBOL -> true
        else -> false
    }

    private fun normalizeText(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace(WS_REGEX, " ")
            .trim()

    companion object {
        const val PAD_ID = 0
        const val BOS_ID = 1
        const val EOT_ID = 2
        const val SOA_ID = 3
        const val SLOT_ID = 4
        const val EOA_ID = 5
        const val EN_ID = 6
        const val VI_ID = 7
        const val DEFAULT_UNK_ID = 8

        private val WS_REGEX = Regex("\\s+")

        fun fromFile(file: File): ZeroTtsBpeTokenizer {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val modelObj = json.getJSONObject("model")
            val vocabObj = modelObj.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabObj.length())
            for (key in vocabObj.keys()) {
                vocab[key] = vocabObj.getInt(key)
            }
            val mergesArr = modelObj.getJSONArray("merges")
            val mergeRanks = HashMap<Pair<String, String>, Int>(mergesArr.length())
            for (i in 0 until mergesArr.length()) {
                val item = mergesArr.get(i)
                val pair: Pair<String, String> = if (item is org.json.JSONArray) {
                    Pair(item.getString(0), item.getString(1))
                } else {
                    val str = item.toString()
                    val space = str.indexOf(' ')
                    if (space >= 0) {
                        Pair(str.substring(0, space), str.substring(space + 1))
                    } else {
                        continue
                    }
                }
                mergeRanks[pair] = i
            }
            val unkId = vocab["<unk>"] ?: DEFAULT_UNK_ID
            return ZeroTtsBpeTokenizer(vocab, mergeRanks, unkId)
        }
    }
}
