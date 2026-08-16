package io.legado.app.model.localBook

import org.jsoup.Jsoup

internal data class EpubResolvedTitle(
    val value: String,
    val derivedFromBody: Boolean = false,
)

internal object EpubTitleResolver {

    private val placeholders = setOf(
        "unknown",
        "untitled",
        "no title",
        "null",
        "undefined",
        "n/a",
    )

    fun resolve(resourceTitle: String?, html: String, fallback: String): EpubResolvedTitle {
        resourceTitle.meaningfulTitle()?.let { return EpubResolvedTitle(it) }
        val document = runCatching { Jsoup.parse(html) }.getOrNull()
            ?: return EpubResolvedTitle(fallback)
        document.title().meaningfulTitle()?.let { return EpubResolvedTitle(it) }
        document.selectFirst("h1, h2, h3, h4, h5, h6")
            ?.text()
            ?.meaningfulTitle()
            ?.take(160)
            ?.let { return EpubResolvedTitle(it, derivedFromBody = true) }
        return EpubResolvedTitle(fallback)
    }

    private fun String?.meaningfulTitle(): String? {
        val value = this?.trim().orEmpty()
        if (value.isEmpty() || value.lowercase() in placeholders) return null
        return value
    }
}
