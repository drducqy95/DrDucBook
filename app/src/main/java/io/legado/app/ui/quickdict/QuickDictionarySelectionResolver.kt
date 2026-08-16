package io.legado.app.ui.quickdict

import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.MappedSelection
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class QuickDictionarySelectionAnchor(
    val sourceText: String,
    val start: Int,
    val end: Int,
) {
    val rawText: String
        get() = sourceText.substring(start, end).trim()

    val contextBefore: String
        get() = sourceText.substring(0, start).takeLast(CONTEXT_CHARS)

    val contextAfter: String
        get() = sourceText.substring(end).take(CONTEXT_CHARS)

    val canExpandLeft: Boolean
        get() = previousPhraseIndex(sourceText, start - 1) != null

    val canExpandRight: Boolean
        get() = nextPhraseIndex(sourceText, end) != null

    val canShrinkLeft: Boolean
        get() = phraseCharCount(sourceText, start, end) > 1

    val canShrinkRight: Boolean
        get() = phraseCharCount(sourceText, start, end) > 1

    fun expandLeft(): QuickDictionarySelectionAnchor? = previousPhraseIndex(sourceText, start - 1)
        ?.let { copy(start = it) }

    fun expandRight(): QuickDictionarySelectionAnchor? = nextPhraseIndex(sourceText, end)
        ?.let { copy(end = it + 1) }

    fun shrinkLeft(): QuickDictionarySelectionAnchor? {
        if (!canShrinkLeft) return null
        return nextPhraseIndex(sourceText, start + 1)
            ?.takeIf { it < end }
            ?.let { copy(start = it) }
    }

    fun shrinkRight(): QuickDictionarySelectionAnchor? {
        if (!canShrinkRight) return null
        return previousPhraseIndex(sourceText, end - 2)
            ?.takeIf { it >= start }
            ?.let { copy(end = it + 1) }
    }
}

internal data class QuickDictionarySelectionResolution(
    val anchor: QuickDictionarySelectionAnchor?,
    val alternatives: List<QuickDictionarySelectionAnchor> = emptyList(),
    val confidence: Float = 0f,
) {
    val requiresConfirmation: Boolean
        get() = anchor == null && alternatives.isNotEmpty()
}

internal fun resolveQuickDictionarySelection(
    request: QuickDictionaryRequest,
    quickTranslationGateway: QuickTranslationGateway,
    candidateTranslator: (String) -> String = { candidate ->
        quickTranslationGateway.translate(candidate)
    },
    candidatePhoneticReader: (String) -> String = { candidate ->
        quickTranslationGateway.hanViet(candidate)
    },
): QuickDictionarySelectionAnchor? = resolveQuickDictionarySelectionResult(
    request = request,
    quickTranslationGateway = quickTranslationGateway,
    candidateTranslator = candidateTranslator,
    candidatePhoneticReader = candidatePhoneticReader,
).anchor

internal fun resolveQuickDictionarySelectionResult(
    request: QuickDictionaryRequest,
    quickTranslationGateway: QuickTranslationGateway,
    candidateTranslator: (String) -> String = { candidate ->
        quickTranslationGateway.translate(candidate)
    },
    candidatePhoneticReader: (String) -> String = { candidate ->
        quickTranslationGateway.hanViet(candidate)
    },
): QuickDictionarySelectionResolution {
    val source = request.sourceText.takeIf(String::isNotBlank) ?: request.selectedText
    if (source.isBlank()) return QuickDictionarySelectionResolution(null)
    val display = request.displayText.takeIf(String::isNotBlank) ?: source
    val selection = trimmedSelection(request, display.length)
    if (selection.text.isBlank()) return QuickDictionarySelectionResolution(null)
    val mappedSelection = request.mappedDisplayText
        ?.takeIf { it.sourceText == source && it.displayText == display }
        ?.mapSelection(selection.start, selection.end)
    mappedSelection?.highConfidenceRange(source)?.let { range ->
        return QuickDictionarySelectionResolution(
            anchor = QuickDictionarySelectionAnchor(source, range.first, range.last + 1),
            confidence = mappedSelection.confidence,
        )
    }
    val searchWindow = sourceSearchWindow(
        sourceText = source,
        displayText = display,
        selectionStart = selection.start,
        selectionEnd = selection.end,
    )
    val exactRange = findExactSelectionRange(source, display, selection)
        ?: findDirectRange(source, selection.text, searchWindow.approximatePosition)
    if (exactRange != null) {
        return QuickDictionarySelectionResolution(
            anchor = QuickDictionarySelectionAnchor(source, exactRange.first, exactRange.last + 1),
            confidence = 1f,
        )
    }
    var translatedMatch = findTranslatedRange(
        sourceText = source,
        selectedText = selection.text,
        searchWindow = searchWindow,
        candidateTranslator = candidateTranslator,
        candidatePhoneticReader = candidatePhoneticReader,
    )
    if (translatedMatch == null && source.length <= MAX_GLOBAL_ALIGNMENT_SOURCE_CHARS) {
        translatedMatch = findTranslatedRange(
                sourceText = source,
                selectedText = selection.text,
                searchWindow = searchWindow.expandedForFallback(source.length),
                candidateTranslator = candidateTranslator,
                candidatePhoneticReader = candidatePhoneticReader,
            )
    }
    val range = translatedMatch?.range ?: findHanVietRange(
            sourceText = source,
            selectedText = selection.text,
            searchWindow = searchWindow,
            quickTranslationGateway = quickTranslationGateway,
        )
        ?: if (source == display) {
            fallbackRange(source, selection.text, searchWindow.approximatePosition)
        } else {
            null
        }
    val heuristicAnchor = range?.let {
        QuickDictionarySelectionAnchor(source, it.first, it.last + 1)
    }
    val heuristicConfidence = translatedMatch?.confidence ?: heuristicAnchor?.let { anchor ->
        mappingConfidence(
            sourceCandidate = anchor.rawText,
            selectedText = selection.text,
            candidateTranslator = candidateTranslator,
            candidatePhoneticReader = candidatePhoneticReader,
        )
    } ?: 0f
    if (heuristicAnchor != null && heuristicConfidence >= MappedSelection.HIGH_CONFIDENCE_MAPPING) {
        return QuickDictionarySelectionResolution(
            anchor = heuristicAnchor,
            confidence = heuristicConfidence,
        )
    }
    val alternatives = buildList {
        mappedSelection?.toRange(source)?.let { mappedRange ->
            add(QuickDictionarySelectionAnchor(source, mappedRange.first, mappedRange.last + 1))
        }
        heuristicAnchor?.let(::add)
    }.distinctBy { it.start to it.end }
    return QuickDictionarySelectionResolution(
        anchor = null,
        alternatives = alternatives,
        confidence = maxOf(mappedSelection?.confidence ?: 0f, heuristicConfidence),
    )
}

private fun MappedSelection.highConfidenceRange(source: String): IntRange? {
    if (confidence < MappedSelection.HIGH_CONFIDENCE_MAPPING) return null
    return toRange(source)
}

private fun MappedSelection.toRange(source: String): IntRange? {
    val start = sourceStart ?: return null
    val end = sourceEnd ?: return null
    if (start !in 0..source.length || end !in start..source.length || end == start) return null
    return start until end
}

private fun mappingConfidence(
    sourceCandidate: String,
    selectedText: String,
    candidateTranslator: (String) -> String,
    candidatePhoneticReader: (String) -> String,
): Float {
    val selected = normalizeReading(selectedText)
    if (selected.isBlank()) return 0f
    val candidates = listOfNotNull(
        runCatching { candidateTranslator(sourceCandidate) }.getOrNull(),
        runCatching { candidatePhoneticReader(sourceCandidate) }.getOrNull(),
    ).map(::normalizeReading).filter(String::isNotBlank)
    return when {
        candidates.any { it == selected } -> 0.95f
        candidates.any { it.wordContains(selected) || selected.wordContains(it) } -> 0.65f
        else -> 0.35f
    }
}

private data class TrimmedSelection(
    val text: String,
    val start: Int,
    val end: Int,
)

private data class SourceSearchWindow(
    val start: Int,
    val endInclusive: Int,
    val approximatePosition: Int,
)

private data class TextSegment(
    val start: Int,
    val end: Int,
) {
    val length: Int
        get() = end - start

    val endInclusive: Int
        get() = end - 1
}

private data class ScoredRange(
    val range: IntRange,
    val score: Int,
    val confidence: Float,
)

private fun trimmedSelection(
    request: QuickDictionaryRequest,
    displayLength: Int,
): TrimmedSelection {
    val selected = request.selectedText
    val first = selected.indexOfFirst { !it.isWhitespace() }
    if (first < 0) return TrimmedSelection("", request.selectionStart, request.selectionStart)
    val last = selected.indexOfLast { !it.isWhitespace() }
    val start = (request.selectionStart + first).coerceIn(0, displayLength)
    val end = (request.selectionStart + last + 1).coerceIn(start, displayLength)
    return TrimmedSelection(selected.substring(first, last + 1), start, end)
}

private fun findExactSelectionRange(
    sourceText: String,
    displayText: String,
    selection: TrimmedSelection,
): IntRange? {
    if (sourceText != displayText) return null
    val start = selection.start.coerceIn(0, sourceText.length)
    val end = selection.end.coerceIn(start, sourceText.length)
    if (end <= start) return null
    return start until end
}

private fun sourceSearchWindow(
    sourceText: String,
    displayText: String,
    selectionStart: Int,
    selectionEnd: Int,
): SourceSearchWindow {
    if (sourceText.length <= 1) {
        return SourceSearchWindow(0, sourceText.lastIndex.coerceAtLeast(0), 0)
    }
    val fallbackApproximate = scalePosition(
        displayPosition = selectionStart,
        displayLength = displayText.length.coerceAtLeast(selectionEnd).coerceAtLeast(1),
        sourceLength = sourceText.length,
    )
    val sourceSegments = lineSegments(sourceText)
    val displaySegments = lineSegments(displayText)
    val displaySegmentIndex = displaySegments.segmentIndexAt(selectionStart)
    val selectionEndPosition = (selectionEnd - 1).coerceAtLeast(selectionStart)
    val displayEndSegmentIndex = displaySegments.segmentIndexAt(selectionEndPosition)
    val sourceSegment = sourceSegments.getOrNull(displaySegmentIndex)
    val sourceEndSegment = sourceSegments.getOrNull(displayEndSegmentIndex)
    val displaySegment = displaySegments.getOrNull(displaySegmentIndex)
    val displayEndSegment = displaySegments.getOrNull(displayEndSegmentIndex)
    if (sourceSegments.size != displaySegments.size ||
        sourceSegment == null ||
        sourceEndSegment == null ||
        displaySegment == null ||
        displayEndSegment == null ||
        sourceSegment.length <= 0 ||
        sourceEndSegment.length <= 0
    ) {
        return SourceSearchWindow(
            start = (fallbackApproximate - SEARCH_RADIUS).coerceAtLeast(0),
            endInclusive = (fallbackApproximate + SEARCH_RADIUS).coerceAtMost(sourceText.lastIndex),
            approximatePosition = fallbackApproximate,
        )
    }
    val ratio = if (displaySegment.length <= 0) {
        0.0
    } else {
        (selectionStart - displaySegment.start).toDouble() / displaySegment.length.toDouble()
    }.coerceIn(0.0, 1.0)
    val approximate = (sourceSegment.start + sourceSegment.length * ratio)
        .roundToInt()
        .coerceIn(sourceSegment.start, sourceSegment.end.coerceAtMost(sourceText.lastIndex))
    val endRatio = if (displayEndSegment.length <= 0) {
        1.0
    } else {
        (selectionEnd - displayEndSegment.start).toDouble() / displayEndSegment.length.toDouble()
    }.coerceIn(0.0, 1.0)
    val approximateEnd = (sourceEndSegment.start + sourceEndSegment.length * endRatio)
        .roundToInt()
        .coerceIn(sourceEndSegment.start, sourceEndSegment.end.coerceAtMost(sourceText.lastIndex))
    val start = (minOf(approximate, approximateEnd) - SEARCH_RADIUS)
        .coerceAtLeast(sourceSegment.start)
        .coerceAtLeast(0)
    val end = (maxOf(approximate, approximateEnd) + SEARCH_RADIUS)
        .coerceAtMost(sourceEndSegment.endInclusive.coerceAtLeast(sourceEndSegment.start))
        .coerceAtMost(sourceText.lastIndex)
    return SourceSearchWindow(
        start = start,
        endInclusive = end,
        approximatePosition = approximate.coerceIn(0, sourceText.lastIndex),
    )
}

private fun SourceSearchWindow.expandedForFallback(sourceLength: Int): SourceSearchWindow {
    if (sourceLength <= 0) return this
    return copy(
        start = 0,
        endInclusive = sourceLength - 1,
    )
}

private fun lineSegments(text: String): List<TextSegment> {
    if (text.isEmpty()) return emptyList()
    val segments = ArrayList<TextSegment>()
    var start = 0
    text.forEachIndexed { index, char ->
        if (char == '\n') {
            segments.add(TextSegment(start, index))
            start = index + 1
        }
    }
    segments.add(TextSegment(start, text.length))
    return segments
}

private fun List<TextSegment>.segmentIndexAt(position: Int): Int {
    if (isEmpty()) return -1
    val bounded = position.coerceAtLeast(0)
    val index = indexOfFirst { segment ->
        bounded >= segment.start && bounded <= segment.end
    }
    return if (index >= 0) index else lastIndex
}

private fun scalePosition(
    displayPosition: Int,
    displayLength: Int,
    sourceLength: Int,
): Int {
    if (sourceLength <= 0) return 0
    if (displayLength <= 0) return displayPosition.coerceIn(0, sourceLength - 1)
    return (displayPosition.toDouble() / displayLength.toDouble() * sourceLength)
        .roundToInt()
        .coerceIn(0, sourceLength - 1)
}

private fun findDirectRange(
    sourceText: String,
    selectedText: String,
    approximatePosition: Int,
): IntRange? {
    if (selectedText.isBlank()) return null
    var match = sourceText.indexOf(selectedText)
    if (match < 0) return null
    var closest = match
    var closestDistance = abs(match - approximatePosition)
    while (match >= 0) {
        val distance = abs(match - approximatePosition)
        if (distance < closestDistance) {
            closest = match
            closestDistance = distance
        }
        match = sourceText.indexOf(selectedText, match + 1)
    }
    return closest until (closest + selectedText.length)
}

private fun findHanVietRange(
    sourceText: String,
    selectedText: String,
    searchWindow: SourceSearchWindow,
    quickTranslationGateway: QuickTranslationGateway,
): IntRange? {
    val targetReading = normalizeReading(selectedText)
    if (targetReading.isBlank()) return null
    val targetWordCount = targetReading.split(' ').count(String::isNotBlank).coerceAtLeast(1)
    val minChars = (targetWordCount - 1).coerceAtLeast(1)
    val maxChars = (targetWordCount + 2).coerceAtMost(12)
    val windowStart = searchWindow.start.coerceAtLeast(0)
    val windowEnd = searchWindow.endInclusive.coerceAtMost(sourceText.lastIndex)
    var bestRange: IntRange? = null
    var bestScore = Int.MAX_VALUE
    for (start in windowStart..windowEnd) {
        if (!sourceText[start].isPhraseChar()) continue
        for (charCount in minChars..maxChars) {
            val end = endAfterPhraseChars(sourceText, start, charCount) ?: continue
            if (end - 1 > windowEnd) continue
            val candidate = sourceText.substring(start, end)
            val reading = normalizeReading(quickTranslationGateway.hanViet(candidate))
            if (reading == targetReading) return start until end
            if (reading.isNotBlank() &&
                (reading.contains(targetReading) || targetReading.contains(reading))
            ) {
                val score = abs(start - searchWindow.approximatePosition) +
                    abs(reading.length - targetReading.length) * 4
                if (score < bestScore) {
                    bestScore = score
                    bestRange = sourceRangeForReading(
                        sourceText = sourceText,
                        candidateRange = start until end,
                        candidateReading = reading,
                        targetReading = targetReading,
                    )
                }
            }
        }
    }
    return bestRange
}

private fun findTranslatedRange(
    sourceText: String,
    selectedText: String,
    searchWindow: SourceSearchWindow,
    candidateTranslator: (String) -> String,
    candidatePhoneticReader: (String) -> String,
): ScoredRange? {
    val targetReading = normalizeReading(selectedText)
    if (targetReading.isBlank()) return null
    val targetWordCount = targetReading.split(' ').count(String::isNotBlank).coerceAtLeast(1)
    val minChars = 1
    val maxChars = (targetWordCount + 6).coerceIn(2, MAX_TRANSLATED_CANDIDATE_CHARS)
    val windowStart = searchWindow.start.coerceAtLeast(0)
    val windowEnd = searchWindow.endInclusive.coerceAtMost(sourceText.lastIndex)
    var best: ScoredRange? = null
    val readingCache = HashMap<String, List<String>>()
    for (start in windowStart..windowEnd) {
        if (!sourceText[start].isPhraseChar()) continue
        for (charCount in minChars..maxChars) {
            val end = endAfterPhraseChars(sourceText, start, charCount) ?: continue
            if (end - 1 > windowEnd) continue
            val candidate = sourceText.substring(start, end)
            val candidateReadings = readingCache.getOrPut(candidate) {
                listOfNotNull(
                    runCatching { candidateTranslator(candidate) }.getOrNull(),
                    runCatching { candidatePhoneticReader(candidate) }.getOrNull(),
                )
            }
            for (candidateReading in candidateReadings) {
                val normalizedCandidateReading = normalizeReading(candidateReading)
                val score = candidateMatchScore(
                    candidateReading = normalizedCandidateReading,
                    targetReading = targetReading,
                    candidateStart = start,
                    approximatePosition = searchWindow.approximatePosition,
                ) ?: continue
                if (best == null || score < best.score) {
                    val resolvedRange = sourceRangeForReading(
                        sourceText = sourceText,
                        candidateRange = start until end,
                        candidateReading = normalizedCandidateReading,
                        targetReading = targetReading,
                    )
                    best = ScoredRange(
                        range = resolvedRange,
                        score = score,
                        confidence = when {
                            normalizedCandidateReading == targetReading -> 0.95f
                            normalizedCandidateReading.wordContains(targetReading) &&
                                resolvedRange != (start until end) -> 0.9f
                            else -> 0.65f
                        },
                    )
                }
            }
        }
    }
    return best
}

private fun sourceRangeForReading(
    sourceText: String,
    candidateRange: IntRange,
    candidateReading: String,
    targetReading: String,
): IntRange {
    if (candidateReading == targetReading) return candidateRange
    val candidateWords = candidateReading.split(' ').filter(String::isNotBlank)
    val targetWords = targetReading.split(' ').filter(String::isNotBlank)
    if (candidateWords.isEmpty() || targetWords.isEmpty()) return candidateRange
    val targetStart = candidateWords
        .windowed(targetWords.size)
        .indexOfFirst { it == targetWords }
    if (targetStart < 0 || targetWords.size >= candidateWords.size) return candidateRange
    val phraseIndices = candidateRange
        .filter { it in sourceText.indices && sourceText[it].isPhraseChar() }
    if (phraseIndices.isEmpty()) return candidateRange
    val startOffset = (phraseIndices.size * targetStart.toDouble() / candidateWords.size)
        .roundToInt()
        .coerceIn(0, phraseIndices.lastIndex)
    val endOffset = (phraseIndices.size * (targetStart + targetWords.size).toDouble() /
        candidateWords.size)
        .roundToInt()
        .coerceIn(startOffset + 1, phraseIndices.size)
    return phraseIndices[startOffset] until phraseIndices[endOffset - 1] + 1
}

private fun candidateMatchScore(
    candidateReading: String,
    targetReading: String,
    candidateStart: Int,
    approximatePosition: Int,
): Int? {
    if (candidateReading.isBlank()) return null
    val distance = abs(candidateStart - approximatePosition)
    val lengthPenalty = abs(candidateReading.length - targetReading.length) * 4
    if (candidateReading == targetReading) {
        return distance + lengthPenalty
    }
    val allowPartial = targetReading.length >= PARTIAL_MATCH_MIN_CHARS ||
        targetReading.split(' ').count(String::isNotBlank) > 1
    if (!allowPartial) return null
    val containsTarget = candidateReading.wordContains(targetReading)
    val targetContains = targetReading.wordContains(candidateReading)
    if (!containsTarget && !targetContains) return null
    return PARTIAL_MATCH_SCORE_OFFSET + distance + lengthPenalty
}

private fun String.wordContains(other: String): Boolean {
    if (other.isBlank()) return false
    return this == other ||
        startsWith("$other ") ||
        endsWith(" $other") ||
        contains(" $other ")
}

private fun fallbackRange(
    sourceText: String,
    selectedText: String,
    approximatePosition: Int,
): IntRange {
    val targetChars = normalizeReading(selectedText).split(' ')
        .count(String::isNotBlank)
        .coerceIn(1, 8)
    val nearest = nearestPhraseIndex(sourceText, approximatePosition)
        ?: return approximatePosition until (approximatePosition + 1).coerceAtMost(sourceText.length)
    val end = endAfterPhraseChars(sourceText, nearest, targetChars)
        ?: (nearest + 1).coerceAtMost(sourceText.length)
    return nearest until end
}

private fun normalizeReading(text: String): String = text.lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")

private fun nearestPhraseIndex(text: String, index: Int): Int? {
    if (text.isEmpty()) return null
    val bounded = index.coerceIn(0, text.lastIndex)
    if (text[bounded].isPhraseChar()) return bounded
    for (offset in 1..48) {
        val left = bounded - offset
        if (left >= 0 && text[left].isPhraseChar()) return left
        val right = bounded + offset
        if (right <= text.lastIndex && text[right].isPhraseChar()) return right
    }
    return null
}

private fun previousPhraseIndex(text: String, fromIndex: Int): Int? {
    if (text.isEmpty()) return null
    for (index in fromIndex.coerceAtMost(text.lastIndex) downTo 0) {
        if (text[index].isPhraseChar()) return index
    }
    return null
}

private fun nextPhraseIndex(text: String, fromIndex: Int): Int? {
    if (text.isEmpty()) return null
    for (index in fromIndex.coerceAtLeast(0)..text.lastIndex) {
        if (text[index].isPhraseChar()) return index
    }
    return null
}

private fun endAfterPhraseChars(text: String, start: Int, charCount: Int): Int? {
    var index = start
    var remaining = charCount
    while (index <= text.lastIndex) {
        if (text[index].isPhraseChar()) {
            remaining--
            if (remaining == 0) return index + 1
        } else if (remaining < charCount) {
            return null
        }
        index++
    }
    return null
}

private fun phraseCharCount(text: String, start: Int, end: Int): Int {
    if (start !in text.indices || end <= start) return 0
    return (start until end.coerceAtMost(text.length)).count { text[it].isPhraseChar() }
}

private fun Char.isPhraseChar(): Boolean = isLetterOrDigit() ||
    code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF || code in 0xF900..0xFAFF

private const val CONTEXT_CHARS = 400
private const val SEARCH_RADIUS = 192
private const val MAX_TRANSLATED_CANDIDATE_CHARS = 16
private const val MAX_GLOBAL_ALIGNMENT_SOURCE_CHARS = 2_400
private const val PARTIAL_MATCH_MIN_CHARS = 4
private const val PARTIAL_MATCH_SCORE_OFFSET = 10_000
