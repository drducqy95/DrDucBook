package io.legado.app.domain.usecase

import io.legado.app.data.entities.SearchBook

/** Creates a translated display copy while preserving the source entity used by navigation/DB. */
class TranslateDynamicBookUiUseCase(
    private val translateDynamicUiTextUseCase: TranslateDynamicUiTextUseCase,
) {

    suspend fun execute(
        sourceBook: SearchBook,
        forceRetranslate: Boolean = false,
    ): SearchBook {
        val context = listOfNotNull(
            sourceBook.name,
            sourceBook.author,
            sourceBook.originName,
            sourceBook.kind,
            sourceBook.intro,
            sourceBook.latestChapterTitle,
            sourceBook.wordCount,
            sourceBook.chapterWordCountText,
        ).joinToString("\n")
        val dictionaryBook = sourceBook.toBook()
        val scopeKey = "book:${sourceBook.bookUrl}"

        val sourceValues = listOf(
            sourceBook.name,
            sourceBook.author,
            sourceBook.originName,
            sourceBook.kind,
            sourceBook.intro,
            sourceBook.latestChapterTitle,
            sourceBook.wordCount,
            sourceBook.chapterWordCountText,
        )
        val populatedValues = sourceValues.mapIndexedNotNull { index, value ->
            value?.takeIf(String::isNotBlank)?.let { index to it }
        }
        val translatedValues = translateDynamicUiTextUseCase.executeLines(
            scopeKey = scopeKey,
            originalLines = populatedValues.map(Pair<Int, String>::second),
            book = dictionaryBook,
            contextText = context,
            forceRetranslate = forceRetranslate,
        ).getOrElse { populatedValues.map(Pair<Int, String>::second) }
        val displayValues = sourceValues.toMutableList().apply {
            populatedValues.zip(translatedValues).forEach { (source, translated) ->
                this[source.first] = translated
            }
        }

        // Author uses Hán Việt + Title Case
        val translatedAuthor = sourceBook.author.takeIf(String::isNotBlank)?.let {
            translateDynamicUiTextUseCase.executeAuthorName(
                scopeKey = scopeKey,
                originalText = it,
                book = dictionaryBook,
                forceRetranslate = forceRetranslate,
            ).getOrElse { displayValues[1].orEmpty() }
        } ?: displayValues[1].orEmpty()

        // Latest chapter title uses QT + Title Case
        val translatedLatestChapter = sourceBook.latestChapterTitle?.takeIf(String::isNotBlank)?.let {
            translateDynamicUiTextUseCase.executeChapterTitle(
                scopeKey = scopeKey,
                originalText = it,
                book = dictionaryBook,
                contextText = context,
                forceRetranslate = forceRetranslate,
            ).getOrElse { displayValues[5].orEmpty() }
        } ?: displayValues[5]

        return sourceBook.copy(
            name = displayValues[0].orEmpty(),
            author = translatedAuthor,
            originName = displayValues[2].orEmpty(),
            kind = displayValues[3],
            intro = displayValues[4],
            latestChapterTitle = translatedLatestChapter,
            wordCount = displayValues[6],
            chapterWordCountText = displayValues[7],
        ).also { displayCopy ->
            displayCopy.infoHtml = sourceBook.infoHtml
            displayCopy.tocHtml = sourceBook.tocHtml
        }
    }
}
