package io.legado.app.domain.gateway

import io.legado.app.data.entities.Book
import io.legado.app.domain.manga.MangaOcrScript
import io.legado.app.domain.manga.MangaOverlayPage
import io.legado.app.domain.manga.MangaTextBlock

data class MangaOcrResult(
    val width: Int,
    val height: Int,
    val blocks: List<MangaTextBlock>,
)

interface MangaOcrGateway {
    suspend fun recognize(imageBytes: ByteArray, script: MangaOcrScript): MangaOcrResult
}

interface MangaTextTranslationGateway {
    suspend fun translate(
        text: String,
        provider: String,
        targetLanguage: String,
        book: Book? = null,
    ): String

    suspend fun dependencyHash(text: String, provider: String, book: Book? = null): String = text
}

interface MangaTranslationCacheGateway {
    suspend fun read(cacheKey: String): MangaOverlayPage?
    suspend fun write(page: MangaOverlayPage)
    suspend fun delete(cacheKey: String)
    suspend fun clear()
}
