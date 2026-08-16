package io.legado.app.data.repository

import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.model.CachedChapterSnapshot
import io.legado.app.help.book.BookHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class CachedChapterRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
) : CachedChapterGateway {

    override suspend fun getBooks(): List<Book> = withContext(Dispatchers.IO) {
        bookDao.getAll().filter { bookChapterDao.getChapterCount(it.bookUrl) > 0 }
    }

    override suspend fun getBook(bookUrl: String): Book? = withContext(Dispatchers.IO) {
        bookDao.getBook(bookUrl)
    }

    override suspend fun getChapter(
        bookUrl: String,
        chapterIndex: Int,
    ): BookChapter? = withContext(Dispatchers.IO) {
        bookChapterDao.getChapter(bookUrl, chapterIndex)
    }

    override suspend fun getChapterContent(
        book: Book,
        chapter: BookChapter,
    ): String? = withContext(Dispatchers.IO) {
        BookHelp.getContent(book, chapter)
    }

    override suspend fun getChapterCount(bookUrl: String): Int = withContext(Dispatchers.IO) {
        bookChapterDao.getChapterCount(bookUrl)
    }

    override fun streamChapterCache(book: Book): Flow<CachedChapterSnapshot> = flow {
        bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
            val content = runCatching { BookHelp.getContent(book, chapter) }.getOrNull()
            emit(
                CachedChapterSnapshot(
                    index = chapter.index,
                    title = chapter.title,
                    content = content,
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}
