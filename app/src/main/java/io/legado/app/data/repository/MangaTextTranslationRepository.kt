package io.legado.app.data.repository

import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.MangaTextTranslationGateway
import io.legado.app.domain.usecase.TranslateChapterUseCase

class MangaTextTranslationRepository(
    private val translateChapterUseCase: TranslateChapterUseCase,
) : MangaTextTranslationGateway {
    override suspend fun translate(
        text: String,
        provider: String,
        targetLanguage: String,
        book: Book?,
    ): String = translateChapterUseCase.executeSuggestion(
        text = text,
        provider = provider,
        book = book,
        targetLanguage = targetLanguage,
    ).getOrThrow()

    override suspend fun dependencyHash(text: String, provider: String, book: Book?): String =
        translateChapterUseCase.computeSuggestionDependencyHash(text, provider, book)
}
