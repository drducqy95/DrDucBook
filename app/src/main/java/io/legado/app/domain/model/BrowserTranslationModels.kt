package io.legado.app.domain.model

data class BrowserPageTextNode(
    val id: String,
    val text: String,
    val contentHash: String,
)

data class BrowserPageTextTranslation(
    val id: String,
    val originalText: String,
    val translatedText: String,
    val contentHash: String,
)

data class BrowserPageTranslationResult(
    val translations: List<BrowserPageTextTranslation>,
    val skippedCount: Int,
    val failedCount: Int,
)
