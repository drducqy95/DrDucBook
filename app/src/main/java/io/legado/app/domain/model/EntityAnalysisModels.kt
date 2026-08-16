package io.legado.app.domain.model

data class CachedChapterSnapshot(
    val index: Int,
    val title: String,
    val content: String?,
)

data class EntityAnalysisProgress(
    val scannedChapters: Int,
    val totalChapters: Int,
    val downloadedChapters: Int,
    val trackedCandidates: Int,
)

data class EntityAnalysisCandidate(
    val raw: String,
    val hanViet: String,
    val target: String,
    val type: QuickDictionaryType,
    val occurrences: Int,
    val chapterCount: Int,
    val firstChapterTitle: String,
    val context: String,
)

data class EntityAnalysisResult(
    val bookName: String,
    val totalChapters: Int,
    val downloadedChapters: Int,
    val candidates: List<EntityAnalysisCandidate>,
)
