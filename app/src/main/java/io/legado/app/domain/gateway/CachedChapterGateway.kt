package io.legado.app.domain.gateway

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.model.CachedChapterSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Read-only access to chapter files already present on the device.
 *
 * Implementations must never fetch a remote chapter while producing this stream.
 */
interface CachedChapterGateway {
    suspend fun getBooks(): List<Book> = emptyList()

    suspend fun getBook(bookUrl: String): Book?

    suspend fun getChapter(bookUrl: String, chapterIndex: Int): BookChapter? = null

    suspend fun getChapterContent(book: Book, chapter: BookChapter): String? = null

    suspend fun getChapterCount(bookUrl: String): Int

    fun streamChapterCache(book: Book): Flow<CachedChapterSnapshot>
}
