package io.legado.app.domain.model

import androidx.compose.runtime.Stable
import kotlin.math.min

@Stable
data class DisplaySourceSegment(
    val sourceStart: Int,
    val sourceEnd: Int,
    val displayStart: Int,
    val displayEnd: Int,
    val confidence: Float = 1f,
    val exactCharacterMapping: Boolean = false,
) {
    init {
        require(sourceStart >= 0 && sourceEnd >= sourceStart)
        require(displayStart >= 0 && displayEnd >= displayStart)
        require(confidence in 0f..1f)
    }
}

@Stable
data class MappedDisplayText(
    val sourceText: String,
    val displayText: String,
    val engine: String,
    val segments: List<DisplaySourceSegment>,
) {
    fun mapSelection(displayStart: Int, displayEnd: Int): MappedSelection {
        val start = displayStart.coerceIn(0, displayText.length)
        val end = displayEnd.coerceIn(start, displayText.length)
        if (end <= start) return MappedSelection()
        if (sourceText == displayText) {
            return MappedSelection(
                sourceStart = start.coerceAtMost(sourceText.length),
                sourceEnd = end.coerceAtMost(sourceText.length),
                confidence = 1f,
            )
        }

        val overlaps = segments.asSequence()
            .filter { it.sourceEnd <= sourceText.length && it.displayEnd <= displayText.length }
            .filter { it.displayStart < end && it.displayEnd > start }
            .sortedBy(DisplaySourceSegment::displayStart)
            .toList()
        if (overlaps.isEmpty()) return MappedSelection()

        val selected = displayText.substring(start, end)
        val selectedSignal = selected.count { !it.isWhitespace() }.coerceAtLeast(1)
        val coveredSignal = overlaps.sumOf { segment ->
            val overlapStart = maxOf(start, segment.displayStart)
            val overlapEnd = minOf(end, segment.displayEnd)
            displayText.substring(overlapStart, overlapEnd).count { !it.isWhitespace() }
        }.coerceAtMost(selectedSignal)
        val coverage = coveredSignal.toFloat() / selectedSignal.toFloat()
        var sourceStart = overlaps.first().sourceStart
        var sourceEnd = overlaps.last().sourceEnd
        if (overlaps.size == 1) {
            val segment = overlaps.single()
            val sourceLength = segment.sourceEnd - segment.sourceStart
            val displayLength = segment.displayEnd - segment.displayStart
            if (segment.exactCharacterMapping && sourceLength == displayLength) {
                sourceStart = segment.sourceStart + (start - segment.displayStart).coerceAtLeast(0)
                sourceEnd = segment.sourceStart + (end - segment.displayStart).coerceAtMost(displayLength)
            }
        }
        val confidence = overlaps.fold(1f) { value, segment -> min(value, segment.confidence) } * coverage
        return MappedSelection(
            sourceStart = sourceStart,
            sourceEnd = sourceEnd,
            confidence = confidence.coerceIn(0f, 1f),
        )
    }
}

fun MappedDisplayText.rebaseDisplayText(actualDisplayText: String): MappedDisplayText {
    if (displayText == actualDisplayText) return this
    if (actualDisplayText.isEmpty() || segments.isEmpty()) {
        return copy(displayText = actualDisplayText, segments = emptyList())
    }
    val rebased = mutableListOf<DisplaySourceSegment>()
    var cursor = 0
    segments.sortedBy(DisplaySourceSegment::displayStart).forEach { segment ->
        if (segment.displayStart !in 0..displayText.length ||
            segment.displayEnd !in segment.displayStart..displayText.length
        ) {
            return@forEach
        }
        val rendered = displayText.substring(segment.displayStart, segment.displayEnd)
        if (rendered.isEmpty()) return@forEach
        val matchStart = actualDisplayText.indexOf(
            string = rendered,
            startIndex = cursor,
            ignoreCase = true,
        ).takeIf { it >= 0 } ?: return@forEach
        val matchEnd = matchStart + rendered.length
        rebased += segment.copy(
            displayStart = matchStart,
            displayEnd = matchEnd,
        )
        cursor = matchEnd
    }
    return MappedDisplayText(
        sourceText = sourceText,
        displayText = actualDisplayText,
        engine = "$engine:rebased",
        segments = rebased,
    )
}

@Stable
data class MappedTranslation(
    val text: String,
    val segments: List<DisplaySourceSegment>,
    val engine: String,
) {
    fun asDisplayText(sourceText: String): MappedDisplayText = MappedDisplayText(
        sourceText = sourceText,
        displayText = text,
        engine = engine,
        segments = segments,
    )
}

@Stable
data class ReaderContentSnapshot(
    val rawText: String,
    val displayText: String,
    val mappedDisplayText: MappedDisplayText,
)

@Stable
data class MappedSelection(
    val sourceStart: Int? = null,
    val sourceEnd: Int? = null,
    val confidence: Float = 0f,
) {
    val isMapped: Boolean
        get() = sourceStart != null && sourceEnd != null && sourceEnd > sourceStart

    val requiresConfirmation: Boolean
        get() = isMapped && confidence < HIGH_CONFIDENCE_MAPPING

    companion object {
        const val HIGH_CONFIDENCE_MAPPING = 0.8f
    }
}

fun alignedParagraphMapping(
    sourceText: String,
    displayText: String,
    engine: String,
): MappedDisplayText {
    if (sourceText == displayText) {
        return MappedDisplayText(
            sourceText = sourceText,
            displayText = displayText,
            engine = engine,
            segments = listOf(
                DisplaySourceSegment(
                    sourceStart = 0,
                    sourceEnd = sourceText.length,
                    displayStart = 0,
                    displayEnd = displayText.length,
                    confidence = 1f,
                    exactCharacterMapping = true,
                )
            ),
        )
    }
    val sourceLines = sourceText.lineRanges()
    val displayLines = displayText.lineRanges()
    val segments = if (sourceLines.size == displayLines.size) {
        sourceLines.zip(displayLines).map { (source, display) ->
            val exact = sourceText.substring(source.first, source.last + 1) ==
                displayText.substring(display.first, display.last + 1)
            DisplaySourceSegment(
                sourceStart = source.first,
                sourceEnd = source.last + 1,
                displayStart = display.first,
                displayEnd = display.last + 1,
                confidence = if (exact) 1f else 0.65f,
                exactCharacterMapping = exact,
            )
        }
    } else {
        listOf(
            DisplaySourceSegment(
                sourceStart = 0,
                sourceEnd = sourceText.length,
                displayStart = 0,
                displayEnd = displayText.length,
                confidence = 0.35f,
            )
        )
    }
    return MappedDisplayText(sourceText, displayText, engine, segments)
}

private fun String.lineRanges(): List<IntRange> {
    if (isEmpty()) return listOf(0 until 0)
    val result = mutableListOf<IntRange>()
    var start = 0
    forEachIndexed { index, char ->
        if (char == '\n') {
            result += start until index
            start = index + 1
        }
    }
    result += start until length
    return result
}
