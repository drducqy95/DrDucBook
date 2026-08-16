package io.legado.app.ui.authoring.writing

import io.legado.app.domain.model.AuthoringProject
import java.util.UUID

internal data class WritingReplacementResult(
    val text: String,
    val replacements: Int,
)

internal fun countLiteralOccurrences(text: String, query: String): Int {
    if (query.isEmpty()) return 0
    var count = 0
    var index = text.indexOf(query)
    while (index >= 0) {
        count += 1
        index = text.indexOf(query, startIndex = index + query.length)
    }
    return count
}

internal fun replaceFirstLiteral(
    text: String,
    query: String,
    replacement: String,
): WritingReplacementResult {
    if (query.isEmpty()) return WritingReplacementResult(text, 0)
    val index = text.indexOf(query)
    if (index < 0) return WritingReplacementResult(text, 0)
    return WritingReplacementResult(
        text = text.replaceRange(index, index + query.length, replacement),
        replacements = 1,
    )
}

internal fun replaceAllLiteral(
    text: String,
    query: String,
    replacement: String,
): WritingReplacementResult {
    if (query.isEmpty()) return WritingReplacementResult(text, 0)
    val count = countLiteralOccurrences(text, query)
    if (count == 0) return WritingReplacementResult(text, 0)
    return WritingReplacementResult(
        text = text.replace(query, replacement),
        replacements = count,
    )
}

internal fun duplicateChapterInProject(
    project: AuthoringProject,
    chapterId: String,
    now: Long,
    copyLabel: String = "Copy",
): Pair<AuthoringProject, String>? {
    val index = project.chapters.indexOfFirst { it.id == chapterId }
    if (index < 0) return null
    val source = project.chapters[index]
    val duplicate = source.copy(
        id = UUID.randomUUID().toString(),
        title = "${source.title} $copyLabel".trim(),
        createdAt = now,
        updatedAt = now,
    )
    val chapters = project.chapters.toMutableList().apply {
        add(index + 1, duplicate)
    }
    return project.copy(chapters = chapters, updatedAt = now) to duplicate.id
}

internal fun materializeChapterEdit(
    project: AuthoringProject,
    selectedChapterId: String?,
    chapterTitle: String,
    chapterContent: String,
    now: Long,
): AuthoringProject = project.copy(
    chapters = project.chapters.map { chapter ->
        if (chapter.id == selectedChapterId) {
            chapter.copy(
                title = chapterTitle.trim().ifBlank { chapter.title },
                content = chapterContent,
                updatedAt = now,
            )
        } else chapter
    },
    updatedAt = now,
)
