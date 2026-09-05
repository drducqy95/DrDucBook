package io.legado.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.drducbook.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class ImportBookshelfUseCase(
    private val context: Context,
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository,
    private val quickTranslationGateway: QuickTranslationGateway? = null
) {
    suspend fun import(
        str: String,
        groupId: Long,
        onProgress: suspend (String) -> Unit = {}
    ): Result<Unit> = kotlin.runCatching {
        val text = str.trim()
        when {
            text.isAbsUrl() -> {
                val downloadedText = okHttpClient.newCallResponseBody {
                    url(text)
                }.decompressed().text()
                import(downloadedText, groupId, onProgress).getOrThrow()
            }

            text.isJsonArray() -> {
                importByJson(text, groupId, onProgress)
            }

            else -> {
                throw NoStackTraceException("格式不对")
            }
        }
    }

    suspend fun import(
        uri: Uri,
        groupId: Long,
        onProgress: suspend (String) -> Unit = {}
    ): Result<Unit> = kotlin.runCatching {
        val text = uri.readText(context)
        import(text, groupId, onProgress).getOrThrow()
    }

    private suspend fun importByJson(
        json: String,
        groupId: Long,
        onProgress: suspend (String) -> Unit
    ) {
        onProgress("导入中...")
        val bookSourceParts = bookSourceRepository.getAllEnabledPart()
        val semaphore = Semaphore(AppConfig.threadCount)
        val books = GSON.fromJsonArray<Map<String, Any?>>(json).getOrThrow()

        withContext(Dispatchers.IO) {
            books.forEach { bookInfo ->
                val name = bookInfo["name"]?.toString()?.trim() ?: ""
                val author = bookInfo["author"]?.toString()?.trim() ?: ""
                val originalName = bookInfo["originalName"]?.toString()?.trim()
                val originalAuthor = bookInfo["originalAuthor"]?.toString()?.trim()
                val bookUrl = bookInfo["bookUrl"]?.toString()?.trim()

                if (name.isEmpty() && originalName.isNullOrEmpty()) {
                    return@forEach
                }

                // Strategy 1: Direct entity restore if bookUrl is provided (new rich export format)
                if (!bookUrl.isNullOrBlank()) {
                    val groupVal = if (groupId > 0) groupId else ((bookInfo["group"] as? Number)?.toLong() ?: bookInfo["group"]?.toString()?.toLongOrNull() ?: 0L)
                    val book = Book(
                        bookUrl = bookUrl,
                        tocUrl = bookInfo["tocUrl"]?.toString() ?: "",
                        origin = bookInfo["origin"]?.toString() ?: BookType.localTag,
                        originName = bookInfo["originName"]?.toString() ?: "",
                        name = originalName?.takeIf { it.isNotBlank() } ?: name,
                        author = originalAuthor?.takeIf { it.isNotBlank() } ?: author,
                        coverUrl = bookInfo["coverUrl"]?.toString(),
                        customCoverUrl = bookInfo["customCoverUrl"]?.toString(),
                        intro = bookInfo["originalIntro"]?.toString() ?: bookInfo["intro"]?.toString(),
                        customIntro = bookInfo["customIntro"]?.toString() ?: (if (!originalName.isNullOrBlank() && originalName != name) bookInfo["intro"]?.toString() else null),
                        type = (bookInfo["type"] as? Number)?.toInt() ?: (bookInfo["type"]?.toString()?.toIntOrNull() ?: BookType.text),
                        group = groupVal,
                        durChapterIndex = (bookInfo["durChapterIndex"] as? Number)?.toInt() ?: (bookInfo["durChapterIndex"]?.toString()?.toIntOrNull() ?: 0),
                        durChapterPos = (bookInfo["durChapterPos"] as? Number)?.toInt() ?: (bookInfo["durChapterPos"]?.toString()?.toIntOrNull() ?: 0),
                        durChapterTitle = bookInfo["durChapterTitle"]?.toString(),
                        totalChapterNum = (bookInfo["totalChapterNum"] as? Number)?.toInt() ?: (bookInfo["totalChapterNum"]?.toString()?.toIntOrNull() ?: 0),
                        customTag = bookInfo["customTag"]?.toString(),
                        variable = bookInfo["variable"]?.toString(),
                    )
                    val existing = bookRepository.getBook(book.bookUrl)
                    if (existing != null) {
                        bookRepository.update(book)
                    } else {
                        bookRepository.insert(book)
                    }
                    onProgress(name.ifEmpty { book.name })
                    return@forEach
                }

                // Strategy 2: If originalName is present and differs from name, search using originalName
                if (!originalName.isNullOrBlank() && originalName != name) {
                    val found = searchAndSave(
                        searchName = originalName,
                        searchAuthor = originalAuthor ?: author,
                        groupId = groupId,
                        bookSourceParts = bookSourceParts,
                        semaphore = semaphore,
                    )
                    if (found) {
                        onProgress("$name ($originalName)")
                        return@forEach
                    }
                }

                // Strategy 3: Check if book already exists in local repository by name & author
                if (bookRepository.getBook(name, author) != null) {
                    return@forEach
                }

                // Strategy 4: Online search with name & author
                var found = searchAndSave(
                    searchName = name,
                    searchAuthor = author,
                    groupId = groupId,
                    bookSourceParts = bookSourceParts,
                    semaphore = semaphore,
                )
                if (found) {
                    onProgress(name)
                    return@forEach
                }

                // Strategy 5: Backward compatibility for old exports where name is Vietnamese
                // Reverse lookup in QuickDictionary to find candidate original Chinese title
                val candidateChineseName = quickTranslationGateway?.let { gateway ->
                    val nameCandidates = gateway.searchBuiltInEntries(QuickDictionaryType.NAME, name, limit = 10)
                    nameCandidates.firstOrNull { it.target.trim().equals(name, ignoreCase = true) }?.raw?.trim()
                        ?: gateway.searchBuiltInEntries(QuickDictionaryType.VIETPHRASE, name, limit = 10)
                            .firstOrNull { it.target.trim().equals(name, ignoreCase = true) }?.raw?.trim()
                }

                if (!candidateChineseName.isNullOrBlank() && candidateChineseName != name) {
                    val candidateChineseAuthor = quickTranslationGateway?.let { gateway ->
                        if (author.isNotBlank()) {
                            gateway.searchBuiltInEntries(QuickDictionaryType.NAME, author, limit = 5)
                                .firstOrNull { it.target.trim().equals(author, ignoreCase = true) }?.raw?.trim()
                        } else null
                    } ?: author

                    found = searchAndSave(
                        searchName = candidateChineseName,
                        searchAuthor = candidateChineseAuthor,
                        groupId = groupId,
                        bookSourceParts = bookSourceParts,
                        semaphore = semaphore,
                    )
                    if (found) {
                        onProgress("$name ($candidateChineseName)")
                        return@forEach
                    }
                }

                withContext(Dispatchers.Main) {
                    context.toastOnUi(
                        context.getString(R.string.bookshelf_search_not_found, name, author)
                    )
                }
            }
        }
    }

    private suspend fun searchAndSave(
        searchName: String,
        searchAuthor: String,
        groupId: Long,
        bookSourceParts: List<BookSourcePart>,
        semaphore: Semaphore,
    ): Boolean {
        var foundBook: Book? = null
        semaphore.withPermit {
            for (s in bookSourceParts) {
                coroutineContext.ensureActive()
                val source = s.getBookSource() ?: continue
                foundBook = WebBook.preciseSearchAwait(source, searchName, searchAuthor).getOrNull()
                if (foundBook != null) break
            }
        }
        if (foundBook != null) {
            val book = foundBook!!
            if (groupId > 0) {
                book.group = groupId
            }
            if (bookRepository.getBook(book.bookUrl) != null) {
                bookRepository.update(book)
            } else {
                bookRepository.insert(book)
            }
            return true
        }
        return false
    }
}
