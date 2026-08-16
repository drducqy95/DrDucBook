package io.legado.app.domain.gateway

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.data.entities.TranslationRevisionStatus
import io.legado.app.domain.model.TranslationRevision
import java.io.File

interface TranslationCacheGateway {
    fun getCacheFile(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String? = null,
    ): File

    fun readCurrentTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        originalContentHash: String,
        provider: String,
    ): String?

    suspend fun readTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String? = null,
    ): String?

    suspend fun writeTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        content: String,
        originalContentHash: String? = null,
        provider: String? = null,
        revisionStatus: TranslationRevisionStatus = TranslationRevisionStatus.MACHINE_DRAFT,
        actor: String = "machine",
        parentRevisionId: String? = null,
        rawContentHash: String? = null,
        dictionaryRevision: String? = null,
        providerModelPromptRevision: String? = null,
    )

    suspend fun getCurrentRevision(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        currentRawContentHash: String? = null,
    ): TranslationRevision?

    suspend fun getRevisionHistory(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        currentRawContentHash: String? = null,
    ): List<TranslationRevision>

    suspend fun saveUserEdit(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        content: String,
        originalContentHash: String,
        rawContentHash: String = originalContentHash,
        actor: String = "user",
    ): TranslationRevision

    suspend fun finalizeChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        actor: String = "user",
    ): TranslationRevision

    suspend fun unlockChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        originalContentHash: String,
        rawContentHash: String = originalContentHash,
        actor: String = "user",
    ): TranslationRevision

    suspend fun restoreRevision(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        revisionId: String,
        originalContentHash: String,
        rawContentHash: String = originalContentHash,
        actor: String = "user",
    ): TranslationRevision

    suspend fun deleteTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String? = null,
    )
    suspend fun deleteTranslationForBook(book: Book, targetLanguage: String)
    suspend fun deleteAllTranslation()
    fun getTranslationCacheSize(): Long
    fun computeContentHash(content: String): String
    fun computeCacheKey(
        bookUrl: String,
        chapterIndex: Int,
        chunkIndex: Int,
        targetLanguage: String
    ): String

    suspend fun getCachedChunks(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String,
        provider: String,
    ): List<TranslationCache>

    suspend fun getCachedChunk(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        chunkIndex: Int,
        provider: String,
    ): TranslationCache?

    suspend fun saveChunk(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        chunkIndex: Int,
        originalChunkContent: String,
        originalContentHash: String,
        provider: String,
        status: Int,
        translatedContent: String?,
        errorMessage: String?
    )

    suspend fun clearChunkCacheForChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String? = null,
    )

    suspend fun clearChunkCacheForBook(book: Book, targetLanguage: String)
    suspend fun clearAllChunkCache()

    /**
     * Permanent display-only translation cache for dynamic book-source UI and book metadata.
     * It is deliberately separate from chapter caches so retranslating UI never removes a book
     * translation and never overwrites the original Room entities.
     */
    fun readDynamicUiTranslation(
        scopeKey: String,
        originalText: String,
        targetLanguage: String,
        provider: String,
    ): String?

    suspend fun writeDynamicUiTranslation(
        scopeKey: String,
        originalText: String,
        targetLanguage: String,
        provider: String,
        translatedText: String,
    )

    suspend fun clearDynamicUiTranslations()
}
