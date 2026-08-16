package io.legado.app.service.export

import io.legado.app.domain.model.EbookDocumentChapter
import java.io.File
import java.util.Locale

enum class EbookExportFormat(val value: String, val extension: String) {
    EPUB2("epub2", "epub"),
    EPUB3("epub3", "epub"),
    PDF("pdf", "pdf"),
    TXT("txt", "txt"),
    HTML("html", "html"),
    CBZ("cbz", "cbz");

    companion object {
        fun from(value: String): EbookExportFormat = when (value.lowercase(Locale.ROOT)) {
            "epub", "epub2" -> EPUB2
            "epub3" -> EPUB3
            "pdf" -> PDF
            "html", "htm" -> HTML
            "cbz" -> CBZ
            else -> TXT
        }
    }
}

enum class EbookExportContentSource(val value: String) {
    ORIGINAL("original"),
    TRANSLATION("translation"),
    BOTH("both");

    val includesOriginal: Boolean
        get() = this == ORIGINAL || this == BOTH

    val includesTranslation: Boolean
        get() = this == TRANSLATION || this == BOTH

    companion object {
        fun from(value: String?): EbookExportContentSource {
            val normalized = value?.lowercase(Locale.ROOT)
            return when (normalized) {
                "raw", "original" -> ORIGINAL
                "translated", "translation" -> TRANSLATION
                "all", "both" -> BOTH
                else -> BOTH
            }
        }
    }
}

enum class EbookExportImageOptimization(val value: String) {
    ORIGINAL("original"),
    BALANCED("balanced"),
    SMALL("small");

    val maxEdge: Int
        get() = when (this) {
            ORIGINAL -> Int.MAX_VALUE
            BALANCED -> 1600
            SMALL -> 1200
        }

    val quality: Int
        get() = when (this) {
            ORIGINAL -> 100
            BALANCED -> 82
            SMALL -> 70
        }

    companion object {
        fun from(value: String?): EbookExportImageOptimization = entries.firstOrNull {
            it.value.equals(value, ignoreCase = true)
        } ?: BALANCED
    }
}

val modernEbookExportFormats: List<EbookExportFormat> = listOf(
    EbookExportFormat.EPUB3,
    EbookExportFormat.PDF,
    EbookExportFormat.TXT,
    EbookExportFormat.HTML,
    EbookExportFormat.CBZ,
)

data class EbookExportImage(
    val source: String,
    val file: File,
    val fileName: String,
    val aliases: List<String> = emptyList(),
)

data class EbookExportChapter(
    val index: Int,
    val title: String,
    val html: String,
    val plainText: String,
    val images: List<EbookExportImage> = emptyList(),
    val documentChapter: EbookDocumentChapter? = null,
)

data class EbookExportLabels(
    val author: String = "Author",
    val introduction: String = "Introduction",
    val tableOfContents: String = "Table of contents",
)

data class EbookExportPayload(
    val title: String,
    val author: String,
    val intro: String,
    val language: String,
    val description: String = "",
    val publisher: String = "DrDucBook",
    val identifier: String? = null,
    val subjects: List<String> = emptyList(),
    /** Stable metadata timestamp; callers may provide the source's update time. */
    val metadataDate: String? = null,
    val cover: File? = null,
    val chapters: List<EbookExportChapter>,
    val labels: EbookExportLabels = EbookExportLabels(),
    val layoutMode: String = "REFLOW",
    val viewportWidth: Float? = null,
    val viewportHeight: Float? = null,
    val layoutCss: String = "",
    val imageOptimization: EbookExportImageOptimization = EbookExportImageOptimization.ORIGINAL,
)

/** Parses one-based expressions such as `1-10,15,20-25`; null/blank means all chapters. */
fun selectExportChapterIndices(scope: String?, chapterCount: Int): Set<Int> {
    if (chapterCount <= 0) return emptySet()
    if (scope.isNullOrBlank() || scope.equals("all", ignoreCase = true)) {
        return (0 until chapterCount).toSet()
    }
    val result = linkedSetOf<Int>()
    scope.split(',').map(String::trim).filter(String::isNotEmpty).forEach { token ->
        val range = token.split('-').map(String::trim)
        when (range.size) {
            1 -> range[0].toIntOrNull()?.minus(1)?.let { result += it }
            2 -> {
                val start = range[0].toIntOrNull()
                val end = range[1].toIntOrNull()
                if (start != null && end != null && start <= end) {
                    for (index in start..end) result += index - 1
                }
            }
        }
    }
    return result.filterTo(linkedSetOf()) { it in 0 until chapterCount }
}

/** Groups chapters into size-aware parts while keeping chapter order intact. */
fun splitExportChapters(
    chapters: List<EbookExportChapter>,
    maxPartBytes: Long,
): List<List<EbookExportChapter>> {
    if (chapters.isEmpty()) return emptyList()
    val limit = maxPartBytes.coerceAtLeast(1L)
    val groups = arrayListOf<MutableList<EbookExportChapter>>()
    var current = arrayListOf<EbookExportChapter>()
    var currentWeight = 0L
    chapters.forEach { chapter ->
        val weight = chapter.html.length.toLong() + chapter.plainText.length.toLong() +
            chapter.images.sumOf { image -> image.file.length().coerceAtLeast(0L) }
        if (current.isNotEmpty() && currentWeight + weight > limit) {
            groups += current
            current = arrayListOf()
            currentWeight = 0L
        }
        current += chapter
        currentWeight += weight
    }
    if (current.isNotEmpty()) groups += current
    return groups
}
