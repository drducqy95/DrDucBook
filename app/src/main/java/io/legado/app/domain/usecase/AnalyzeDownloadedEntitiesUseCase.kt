package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.EntityAnalysisCandidate
import io.legado.app.domain.model.EntityAnalysisProgress
import io.legado.app.domain.model.EntityAnalysisResult
import io.legado.app.domain.model.QuickDictionaryType
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.coroutineContext

class AnalyzeDownloadedEntitiesUseCase(
    private val cachedChapterGateway: CachedChapterGateway,
    private val dictionaryGateway: QuickDictionaryGateway,
    private val translationGateway: QuickTranslationGateway,
) {

    suspend operator fun invoke(
        bookUrl: String,
        minimumOccurrences: Int = DEFAULT_MINIMUM_OCCURRENCES,
        resultLimit: Int = DEFAULT_RESULT_LIMIT,
        onProgress: (EntityAnalysisProgress) -> Unit = {},
    ): EntityAnalysisResult {
        val book = requireNotNull(cachedChapterGateway.getBook(bookUrl)) {
            "Book not found"
        }
        val totalChapters = cachedChapterGateway.getChapterCount(bookUrl)
        val existingSources = dictionaryGateway.getEffectiveEntries(book)
            .asSequence()
            .map { it.raw.trim() }
            .filter(String::isNotEmpty)
            .toHashSet()
        val accumulator = EntityCandidateAccumulator()
        var scannedChapters = 0
        var downloadedChapters = 0

        cachedChapterGateway.streamChapterCache(book).collect { chapter ->
            coroutineContext.ensureActive()
            scannedChapters += 1
            chapter.content?.takeIf(String::isNotBlank)?.let { content ->
                downloadedChapters += 1
                accumulator.addChapter(
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    content = content,
                )
            }
            onProgress(
                EntityAnalysisProgress(
                    scannedChapters = scannedChapters,
                    totalChapters = totalChapters,
                    downloadedChapters = downloadedChapters,
                    trackedCandidates = accumulator.size,
                )
            )
        }

        val candidates = accumulator.ranked(
            minimumOccurrences = minimumOccurrences.coerceAtLeast(2),
            limit = resultLimit.coerceIn(1, MAX_RESULT_LIMIT),
            excludedSources = existingSources,
        ).map { candidate ->
            coroutineContext.ensureActive()
            val type = if (candidate.codePointLength <= MAX_NAME_LENGTH) {
                QuickDictionaryType.NAME
            } else {
                QuickDictionaryType.TERM
            }
            val hanViet = translationGateway.hanViet(candidate.raw).trim()
            val translated = translationGateway.translate(candidate.raw).trim()
            EntityAnalysisCandidate(
                raw = candidate.raw,
                hanViet = hanViet,
                target = when (type) {
                    QuickDictionaryType.NAME -> translated.toVietnameseNameCase()
                    else -> translated.replaceFirstChar { it.lowercase() }
                },
                type = type,
                occurrences = candidate.occurrences,
                chapterCount = candidate.chapterCount,
                firstChapterTitle = candidate.firstChapterTitle,
                context = candidate.context,
            )
        }

        return EntityAnalysisResult(
            bookName = book.name,
            totalChapters = totalChapters,
            downloadedChapters = downloadedChapters,
            candidates = candidates,
        )
    }

    private fun String.toVietnameseNameCase(): String {
        return split(WHITESPACE)
            .filter(String::isNotBlank)
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.titlecase() }
            }
    }

    private companion object {
        const val DEFAULT_MINIMUM_OCCURRENCES = 2
        const val DEFAULT_RESULT_LIMIT = 500
        const val MAX_RESULT_LIMIT = 2_000
        const val MAX_NAME_LENGTH = 4
        val WHITESPACE = Regex("\\s+")
    }
}

class ImportEntityCandidatesUseCase(
    private val dictionaryGateway: QuickDictionaryGateway,
) {
    suspend operator fun invoke(
        bookUrl: String,
        candidates: List<EntityAnalysisCandidate>,
    ): Int {
        return dictionaryGateway.saveAll(
            candidates.map { candidate ->
                io.legado.app.domain.model.QuickDictionaryEntry(
                    raw = candidate.raw,
                    hanViet = candidate.hanViet,
                    target = candidate.target,
                    type = candidate.type,
                    scope = io.legado.app.domain.model.QuickDictionaryScope.PROJECT,
                    scopeKey = bookUrl,
                )
            }
        )
    }
}

internal class EntityCandidateAccumulator(
    private val minLength: Int = 2,
    private val maxLength: Int = 6,
    private val maxTrackedCandidates: Int = 40_000,
) {
    private val stats = HashMap<String, MutableEntityStats>()

    val size: Int
        get() = stats.size

    fun addChapter(
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
    ) {
        val seenInChapter = HashSet<String>()
        content.lineSequence().forEach { line ->
            addLine(
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                line = line,
                seenInChapter = seenInChapter,
            )
        }
        if (stats.size > maxTrackedCandidates) {
            prune(chapterIndex)
        }
    }

    fun ranked(
        minimumOccurrences: Int,
        limit: Int,
        excludedSources: Set<String> = emptySet(),
    ): List<RankedEntityCandidate> {
        return stats.asSequence()
            .filter { (raw, value) ->
                value.occurrences >= minimumOccurrences &&
                    raw !in excludedSources &&
                    raw.codePoints().distinct().count() > 1
            }
            .map { (raw, value) ->
                RankedEntityCandidate(
                    raw = raw,
                    codePointLength = raw.codePointCount(0, raw.length),
                    occurrences = value.occurrences,
                    chapterCount = value.chapterCount,
                    firstChapterTitle = value.firstChapterTitle,
                    context = value.context,
                )
            }
            .sortedWith(
                compareByDescending<RankedEntityCandidate> { it.score }
                    .thenByDescending { it.occurrences }
                    .thenByDescending { it.codePointLength }
                    .thenBy { it.raw }
            )
            .take(limit)
            .toList()
    }

    private fun addLine(
        chapterIndex: Int,
        chapterTitle: String,
        line: String,
        seenInChapter: MutableSet<String>,
    ) {
        var offset = 0
        var runStart = -1
        while (offset < line.length) {
            val codePoint = line.codePointAt(offset)
            if (isHan(codePoint)) {
                if (runStart < 0) runStart = offset
            } else if (runStart >= 0) {
                addRun(
                    run = line.substring(runStart, offset),
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    context = excerpt(line, runStart, offset),
                    seenInChapter = seenInChapter,
                )
                runStart = -1
            }
            offset += Character.charCount(codePoint)
        }
        if (runStart >= 0) {
            addRun(
                run = line.substring(runStart),
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                context = excerpt(line, runStart, line.length),
                seenInChapter = seenInChapter,
            )
        }
    }

    private fun addRun(
        run: String,
        chapterIndex: Int,
        chapterTitle: String,
        context: String,
        seenInChapter: MutableSet<String>,
    ) {
        val codePointCount = run.codePointCount(0, run.length)
        if (codePointCount < minLength) return
        val boundaries = IntArray(codePointCount + 1)
        var charOffset = 0
        var codePointOffset = 0
        while (charOffset < run.length) {
            boundaries[codePointOffset++] = charOffset
            charOffset += Character.charCount(run.codePointAt(charOffset))
        }
        boundaries[codePointCount] = run.length

        for (start in 0 until codePointCount) {
            val maximumEnd = (start + maxLength).coerceAtMost(codePointCount)
            for (end in (start + minLength)..maximumEnd) {
                val raw = run.substring(boundaries[start], boundaries[end])
                var value = stats[raw]
                if (value == null) {
                    if (stats.size >= maxTrackedCandidates) {
                        prune(chapterIndex)
                    }
                    if (stats.size >= maxTrackedCandidates) continue
                    value = MutableEntityStats(
                        firstChapterTitle = chapterTitle,
                        context = context,
                    )
                    stats[raw] = value
                }
                value.occurrences += 1
                value.lastSeenChapter = chapterIndex
                if (seenInChapter.add(raw)) {
                    value.chapterCount += 1
                }
            }
        }
    }

    private fun prune(currentChapterIndex: Int) {
        val targetSize = (maxTrackedCandidates * 3 / 4).coerceAtLeast(1)
        val iterator = stats.entries.iterator()
        while (iterator.hasNext() && stats.size > targetSize) {
            val value = iterator.next().value
            if (value.occurrences == 1 && value.lastSeenChapter < currentChapterIndex) {
                iterator.remove()
            }
        }
        if (stats.size <= maxTrackedCandidates) return
        val retained = stats.entries
            .sortedByDescending { (_, value) ->
                value.occurrences * 4 + value.chapterCount * 6
            }
            .take(targetSize)
            .map { (raw, value) -> raw to value }
        stats.clear()
        retained.forEach { (raw, value) -> stats[raw] = value }
    }

    private fun excerpt(line: String, start: Int, end: Int): String {
        val from = (start - 48).coerceAtLeast(0)
        val to = (end + 96).coerceAtMost(line.length)
        return line.substring(from, to)
            .trim()
            .replace(WHITESPACE, " ")
            .take(MAX_CONTEXT_LENGTH)
    }

    private fun isHan(codePoint: Int): Boolean {
        return codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2FA1F
    }

    private data class MutableEntityStats(
        var occurrences: Int = 0,
        var chapterCount: Int = 0,
        var lastSeenChapter: Int = -1,
        val firstChapterTitle: String,
        val context: String,
    )

    private companion object {
        const val MAX_CONTEXT_LENGTH = 180
        val WHITESPACE = Regex("\\s+")
    }
}

internal data class RankedEntityCandidate(
    val raw: String,
    val codePointLength: Int,
    val occurrences: Int,
    val chapterCount: Int,
    val firstChapterTitle: String,
    val context: String,
) {
    val score: Int
        get() = occurrences * 4 + chapterCount * 6 + codePointLength * 2
}
