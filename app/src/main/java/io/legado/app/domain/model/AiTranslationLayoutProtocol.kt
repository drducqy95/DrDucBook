package io.legado.app.domain.model

import kotlin.math.roundToInt

/**
 * Provider-facing paragraph framing. Blank lines are easy for an LLM to merge or duplicate;
 * stable markers make the paragraph contract explicit while [ContentChunker] restores the exact
 * source whitespace after validation.
 */
object AiTranslationLayoutProtocol {

    private val markerPattern = Regex(
        "(?:\\[\\[|\\[|【)\\s*P\\s*(\\d+)\\s*(?:\\]\\]|\\]|】)",
        RegexOption.IGNORE_CASE,
    )

    data class EncodedText(
        val value: String,
        val paragraphCount: Int,
    )

    fun encode(text: String): EncodedText {
        val paragraphs = text.split("\n\n")
        return EncodedText(
            value = paragraphs.mapIndexed { index, paragraph ->
                "[[P$index]]\n$paragraph"
            }.joinToString("\n\n"),
            paragraphCount = paragraphs.size,
        )
    }

    /** Decodes a completed response only when every marker appears once and in order. */
    fun decodeComplete(text: String, expectedParagraphs: Int): String? {
        val matches = markerPattern.findAll(text).toList()
        if (matches.size != expectedParagraphs) return null
        if (matches.mapIndexed { index, match -> match.groupValues[1].toIntOrNull() == index }
                .any { !it }
        ) return null
        val parts = extractParts(text, matches)
        return parts.takeIf { it.size == expectedParagraphs && it.all(String::isNotBlank) }
            ?.joinToString("\n\n")
    }

    /**
     * Accepts provider marker variants and, as a safe fallback, plain output only when it still
     * contains the exact expected number of non-empty paragraphs. Merged or missing paragraphs
     * remain rejected and are retried.
     */
    fun decodeCompleteOrPlain(text: String, expectedParagraphs: Int): String? {
        decodeComplete(text, expectedParagraphs)?.let { return it }
        val normalized = stripMarkers(text)
        val paragraphs = normalized
            .split(Regex("\\r?\\n[\\t ]*\\r?\\n"))
            .map(String::trim)
        if (paragraphs.size == expectedParagraphs && paragraphs.all(String::isNotBlank)) {
            return paragraphs.joinToString("\n\n")
        }
        val lineParagraphs = normalized.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        return lineParagraphs
            .takeIf { it.size == expectedParagraphs }
            ?.joinToString("\n\n")
    }

    /**
     * Last-resort recovery for models that translated all content but merged paragraph framing.
     * Every output token is retained exactly once and distributed using source paragraph lengths.
     */
    fun decodeCompleteOrReflow(text: String, sourceText: String): String? {
        val sourceParagraphs = sourceText.split("\n\n")
        decodeCompleteOrPlain(text, sourceParagraphs.size)?.let { return it }
        val tokens = stripMarkers(text)
            .lineSequence()
            .flatMap { line -> Regex("\\S+").findAll(line).map(MatchResult::value) }
            .toList()
        if (tokens.size < sourceParagraphs.size || sourceParagraphs.isEmpty()) return null
        if (sourceParagraphs.size == 1) return tokens.joinToString(" ")

        val weights = sourceParagraphs.map { it.length.coerceAtLeast(1) }
        val totalWeight = weights.sum().coerceAtLeast(1)
        var tokenStart = 0
        var cumulativeWeight = 0
        return sourceParagraphs.indices.joinToString("\n\n") { paragraphIndex ->
            cumulativeWeight += weights[paragraphIndex]
            val remainingParagraphs = sourceParagraphs.lastIndex - paragraphIndex
            val tokenEnd = if (remainingParagraphs == 0) {
                tokens.size
            } else {
                (tokens.size.toDouble() * cumulativeWeight / totalWeight)
                    .roundToInt()
                    .coerceIn(tokenStart + 1, tokens.size - remainingParagraphs)
            }
            tokens.subList(tokenStart, tokenEnd).joinToString(" ").also {
                tokenStart = tokenEnd
            }
        }
    }

    /** Decodes the prefix already produced by a streaming response. */
    fun decodePartial(text: String): String? {
        val matches = markerPattern.findAll(text).toList()
        if (matches.isEmpty()) return null
        val parts = extractParts(text, matches)
        return parts.takeIf { it.all(String::isNotBlank) }?.joinToString("\n\n")
    }

    /** Removes any surviving markers before the legacy paragraph-count validator runs. */
    fun stripMarkers(text: String): String = markerPattern.replace(text, "").trim()

    fun containsMarker(text: String): Boolean = markerPattern.containsMatchIn(text)

    private fun extractParts(text: String, matches: List<MatchResult>): List<String> =
        matches.mapIndexed { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            text.substring(start, end).trim()
        }
}
