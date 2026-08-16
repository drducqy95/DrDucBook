package io.legado.app.domain.model

/**
 * Splits translation input while retaining an exact whitespace template outside translated text.
 *
 * Providers receive canonical blank lines between paragraphs. Their output is accepted only when
 * the same paragraph cardinality can be recovered, then the source separators are restored
 * byte-for-byte. This prevents regex cleanup, provider trimming, and chunk merging from flattening
 * indentation or blank paragraphs.
 */
object ContentChunker {

    private val sentenceDelimiters = setOf('。', '！', '？', '.', '!', '?', '；', ';', '\n')
    /** Every source line boundary is structural; repeated newlines stay in the same separator. */
    private val paragraphBreak = Regex("[\\t ]*(?:\\r?\\n[\\t ]*)+")

    fun chunk(text: String, maxCharsPerChunk: Int = 3000): List<TextChunk> {
        if (text.isBlank()) return emptyList()
        val maxChars = maxCharsPerChunk.coerceAtLeast(1)
        val layout = parseLayout(text)
        if (layout.paragraphs.isEmpty()) return emptyList()

        val chunks = mutableListOf<TextChunk>()
        val content = StringBuilder()
        val paragraphIndices = mutableListOf<Int>()
        val separators = mutableListOf<String>()
        var leadingWhitespace = ""

        fun flush(trailingWhitespace: String = "") {
            if (content.isEmpty()) return
            chunks += TextChunk(
                index = chunks.size,
                content = content.toString(),
                paragraphIndices = paragraphIndices.toList(),
                leadingWhitespace = leadingWhitespace,
                paragraphSeparators = separators.toList(),
                trailingWhitespace = trailingWhitespace,
            )
            content.clear()
            paragraphIndices.clear()
            separators.clear()
            leadingWhitespace = ""
        }

        layout.paragraphs.forEachIndexed { paragraphPosition, paragraph ->
            val isLastParagraph = paragraphPosition == layout.paragraphs.lastIndex
            if (paragraph.content.length > maxChars) {
                flush()
                val pieces = splitOversizedParagraph(paragraph.content, maxChars)
                pieces.forEachIndexed { pieceIndex, piece ->
                    chunks += TextChunk(
                        index = chunks.size,
                        content = piece,
                        paragraphIndices = listOf(paragraph.index),
                        leadingWhitespace = if (pieceIndex == 0) paragraph.separatorBefore else "",
                        trailingWhitespace = if (
                            isLastParagraph && pieceIndex == pieces.lastIndex
                        ) {
                            layout.trailingWhitespace
                        } else {
                            ""
                        },
                    )
                }
                return@forEachIndexed
            }

            val extraLength = if (content.isEmpty()) 0 else 2 + paragraph.content.length
            if (content.isNotEmpty() && content.length + extraLength > maxChars) {
                flush()
            }
            if (content.isEmpty()) {
                leadingWhitespace = paragraph.separatorBefore
                content.append(paragraph.content)
            } else {
                separators += paragraph.separatorBefore
                content.append("\n\n").append(paragraph.content)
            }
            paragraphIndices += paragraph.index
            if (isLastParagraph) {
                flush(layout.trailingWhitespace)
            }
        }

        return chunks.mapIndexed { index, chunk -> chunk.copy(index = index) }
    }

    /**
     * Restores the source paragraph separators around a provider result.
     *
     * A null result means the provider changed paragraph cardinality; callers should retry with a
     * stricter prompt instead of caching structurally damaged output.
     */
    fun restoreLayout(chunk: TextChunk, translated: String): String? {
        if (translated.isBlank() && chunk.content.isNotBlank()) return null
        val clean = translated.trim()
        val expectedSeparators = chunk.paragraphSeparators.size
        val parts = when {
            expectedSeparators == 0 -> {
                if (paragraphBreak.containsMatchIn(clean)) return null
                listOf(clean)
            }
            else -> splitForExpectedCount(clean, expectedSeparators + 1) ?: return null
        }
        return buildString(
            chunk.leadingWhitespace.length +
                chunk.trailingWhitespace.length +
                parts.sumOf(String::length) +
                chunk.paragraphSeparators.sumOf(String::length)
        ) {
            append(chunk.leadingWhitespace)
            parts.forEachIndexed { index, part ->
                if (index > 0) append(chunk.paragraphSeparators[index - 1])
                append(part.trim())
            }
            append(chunk.trailingWhitespace)
        }
    }

    fun sourceWithLayout(chunk: TextChunk): String {
        return requireNotNull(restoreLayout(chunk, chunk.content)) {
            "Internal translation layout is inconsistent"
        }
    }

    /**
     * Restores source separators while an AI result is still streaming. Untranslated paragraphs
     * remain visible until their translated counterpart arrives, so streaming never collapses the
     * reader layout or temporarily removes the tail of a chunk.
     */
    fun previewWithLayout(chunk: TextChunk, partial: String): String {
        if (partial.isBlank()) return sourceWithLayout(chunk)
        val expectedParts = chunk.paragraphSeparators.size + 1
        if (expectedParts == 1) {
            return chunk.leadingWhitespace + partial.trimStart() + chunk.trailingWhitespace
        }
        val sourceParts = splitForExpectedCount(chunk.content, expectedParts)
            ?: return sourceWithLayout(chunk)
        val partialParts = partial.trimStart()
            .split(paragraphBreak)
            .dropLastWhile(String::isEmpty)
        if (partialParts.isEmpty() || partialParts.size > expectedParts) {
            return sourceWithLayout(chunk)
        }
        return buildString {
            append(chunk.leadingWhitespace)
            sourceParts.forEachIndexed { index, sourcePart ->
                if (index > 0) append(chunk.paragraphSeparators[index - 1])
                append(partialParts.getOrNull(index)?.trim() ?: sourcePart)
            }
            append(chunk.trailingWhitespace)
        }
    }

    fun merge(chunks: List<TextChunk>): String {
        return chunks.sortedBy { it.index }.joinToString(separator = "") { it.content }
    }

    private fun splitForExpectedCount(value: String, expectedParts: Int): List<String>? {
        val paragraphParts = value.split(paragraphBreak)
        return paragraphParts.takeIf { it.size == expectedParts }
    }

    private fun parseLayout(text: String): ParsedLayout {
        val paragraphs = mutableListOf<LayoutParagraph>()
        var pendingWhitespace = ""
        var cursor = 0
        var paragraphIndex = 0

        fun consume(raw: String) {
            if (raw.isEmpty()) return
            val firstContent = raw.indexOfFirst { !it.isWhitespace() }
            if (firstContent < 0) {
                pendingWhitespace += raw
                return
            }
            val lastContent = raw.indexOfLast { !it.isWhitespace() }
            pendingWhitespace += raw.substring(0, firstContent)
            paragraphs += LayoutParagraph(
                index = paragraphIndex++,
                content = raw.substring(firstContent, lastContent + 1),
                separatorBefore = pendingWhitespace,
            )
            pendingWhitespace = raw.substring(lastContent + 1)
        }

        paragraphBreak.findAll(text).forEach { boundary ->
            consume(text.substring(cursor, boundary.range.first))
            pendingWhitespace += boundary.value
            cursor = boundary.range.last + 1
        }
        consume(text.substring(cursor))
        return ParsedLayout(paragraphs, pendingWhitespace)
    }

    private fun splitOversizedParagraph(
        paragraph: String,
        maxCharsPerChunk: Int,
    ): List<String> {
        val sentences = mutableListOf<String>()
        var currentSentence = StringBuilder()
        paragraph.forEach { char ->
            currentSentence.append(char)
            if (char in sentenceDelimiters) {
                sentences += currentSentence.toString()
                currentSentence = StringBuilder()
            }
        }
        if (currentSentence.isNotEmpty()) sentences += currentSentence.toString()

        val output = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                output += current.toString()
                current.clear()
            }
        }
        sentences.forEach { sentence ->
            if (sentence.length > maxCharsPerChunk) {
                flush()
                var offset = 0
                while (offset < sentence.length) {
                    val end = (offset + maxCharsPerChunk).coerceAtMost(sentence.length)
                    output += sentence.substring(offset, end)
                    offset = end
                }
            } else if (current.length + sentence.length <= maxCharsPerChunk) {
                current.append(sentence)
            } else {
                flush()
                current.append(sentence)
            }
        }
        flush()
        return output.filter(String::isNotEmpty)
    }

    private data class ParsedLayout(
        val paragraphs: List<LayoutParagraph>,
        val trailingWhitespace: String,
    )

    private data class LayoutParagraph(
        val index: Int,
        val content: String,
        val separatorBefore: String,
    )
}
