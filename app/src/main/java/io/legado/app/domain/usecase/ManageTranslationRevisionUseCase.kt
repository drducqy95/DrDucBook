package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.model.RevisionStatus
import io.legado.app.domain.model.TranslationRevision
import io.legado.app.domain.model.dictionaryAwareContentHash

class ManageTranslationRevisionUseCase(
    private val cachedChapterGateway: CachedChapterGateway,
    private val translationCacheGateway: TranslationCacheGateway,
    private val quickDictionaryGateway: QuickDictionaryGateway,
    private val quickTranslationGateway: QuickTranslationGateway,
) {

    private companion object {
        const val LEGACY_PAYLOAD_REVISION_PREFIX = "payload-legacy-"
    }

    data class Snapshot(
        val book: Book,
        val chapter: BookChapter,
        val rawContent: String,
        val rawContentHash: String,
        val cacheContentHash: String,
        val current: TranslationRevision?,
        val history: List<TranslationRevision>,
    )

    suspend fun load(
        bookUrl: String,
        chapterIndex: Int,
        targetLanguage: String,
        provider: String,
    ): Snapshot {
        val book = cachedChapterGateway.getBook(bookUrl)
            ?: error("Book not found")
        val chapter = cachedChapterGateway.getChapter(bookUrl, chapterIndex)
            ?: error("Chapter not found")
        val rawContent = cachedChapterGateway.getChapterContent(book, chapter)
            ?: error("Chapter content is not cached")
        val rawContentHash = translationCacheGateway.computeContentHash(rawContent)
        val dictionaryRevision = quickDictionaryGateway.getEffectiveRevision(book, rawContent)
        val cacheContentHash = dictionaryAwareContentHash(
            originalContentHash = rawContentHash,
            provider = provider,
            dictionaryRevision = dictionaryRevision,
            quickTranslationPackVersion = quickTranslationGateway.packVersion,
        )
        val current = translationCacheGateway.getCurrentRevision(
            book,
            chapter,
            targetLanguage,
            provider,
            rawContentHash,
        ) ?: legacyPayloadRevision(
            book = book,
            chapter = chapter,
            targetLanguage = targetLanguage,
            provider = provider,
            rawContentHash = rawContentHash,
            cacheContentHash = cacheContentHash,
        )
        val history = translationCacheGateway.getRevisionHistory(
            book,
            chapter,
            targetLanguage,
            provider,
            rawContentHash,
        ).let { revisions ->
            if (current != null && revisions.none { it.revisionId == current.revisionId }) {
                listOf(current) + revisions
            } else {
                revisions
            }
        }
        return Snapshot(
            book = book,
            chapter = chapter,
            rawContent = rawContent,
            rawContentHash = rawContentHash,
            cacheContentHash = cacheContentHash,
            current = current,
            history = history,
        )
    }

    suspend fun saveUserEdit(
        snapshot: Snapshot,
        targetLanguage: String,
        provider: String,
        content: String,
    ): TranslationRevision = translationCacheGateway.saveUserEdit(
        book = snapshot.book,
        bookChapter = snapshot.chapter,
        targetLanguage = targetLanguage,
        provider = provider,
        content = content,
        originalContentHash = snapshot.cacheContentHash,
        rawContentHash = snapshot.rawContentHash,
    )

    suspend fun finalize(
        snapshot: Snapshot,
        targetLanguage: String,
        provider: String,
    ): TranslationRevision {
        val current = snapshot.current
        if (current != null && current.revisionId.startsWith(LEGACY_PAYLOAD_REVISION_PREFIX)) {
            translationCacheGateway.saveUserEdit(
                book = snapshot.book,
                bookChapter = snapshot.chapter,
                targetLanguage = targetLanguage,
                provider = provider,
                content = current.content,
                originalContentHash = snapshot.cacheContentHash,
                rawContentHash = snapshot.rawContentHash,
            )
        }
        return translationCacheGateway.finalizeChapter(
            book = snapshot.book,
            bookChapter = snapshot.chapter,
            targetLanguage = targetLanguage,
            provider = provider,
        )
    }

    suspend fun unlock(
        snapshot: Snapshot,
        targetLanguage: String,
        provider: String,
    ): TranslationRevision = translationCacheGateway.unlockChapter(
        book = snapshot.book,
        bookChapter = snapshot.chapter,
        targetLanguage = targetLanguage,
        provider = provider,
        originalContentHash = snapshot.cacheContentHash,
        rawContentHash = snapshot.rawContentHash,
    )

    suspend fun restore(
        snapshot: Snapshot,
        targetLanguage: String,
        provider: String,
        revisionId: String,
    ): TranslationRevision = translationCacheGateway.restoreRevision(
        book = snapshot.book,
        bookChapter = snapshot.chapter,
        targetLanguage = targetLanguage,
        provider = provider,
        revisionId = revisionId,
        originalContentHash = snapshot.cacheContentHash,
        rawContentHash = snapshot.rawContentHash,
    )

    private suspend fun legacyPayloadRevision(
        book: Book,
        chapter: BookChapter,
        targetLanguage: String,
        provider: String,
        rawContentHash: String,
        cacheContentHash: String,
    ): TranslationRevision? {
        val content = translationCacheGateway.readTranslation(
            book,
            chapter,
            targetLanguage,
            provider,
        )?.takeIf(String::isNotBlank) ?: return null
        val revisionId = LEGACY_PAYLOAD_REVISION_PREFIX + translationCacheGateway.computeContentHash(
            listOf(book.bookUrl, chapter.index, targetLanguage, provider, cacheContentHash)
                .joinToString("\u0000")
        )
        val now = System.currentTimeMillis()
        return TranslationRevision(
            revisionId = revisionId,
            content = content,
            status = RevisionStatus.MACHINE_DRAFT,
            rawContentHash = rawContentHash,
            cacheContentHash = cacheContentHash,
            provider = provider,
            targetLanguage = targetLanguage,
            createdAt = now,
            updatedAt = now,
            actor = "legacy-cache",
        )
    }
}
