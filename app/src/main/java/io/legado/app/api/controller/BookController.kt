package io.legado.app.api.controller

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.CacheManager
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.model.BookCover
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.cnCompare
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import io.legado.app.web.WebTextRepair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

object BookController {

    private val defaultCoverCache by lazy { WeakHashMap<Drawable, Bitmap>() }

    /**
     * 书架所有书籍
     */
    val bookshelf: ReturnData
        get() {
            val books = appDb.bookDao.all
            val returnData = ReturnData()
            return if (books.isEmpty()) {
                returnData.setErrorMsg("Chưa có truyện nào trong giá sách")
            } else {
                val data = when (AppConfig.bookshelfSort) {
                    1 -> books.sortedByDescending { it.latestChapterTime }
                    2 -> books.sortedWith { o1, o2 ->
                        o1.name.cnCompare(o2.name)
                    }

                    3 -> books.sortedBy { it.order }
                    else -> books.sortedByDescending { it.durChapterTime }
                }
                returnData.setData(
                    data.map { book ->
                        book.copy(
                            name = WebTextRepair.repair(book.name).orEmpty(),
                            author = WebTextRepair.repair(book.author).orEmpty(),
                            kind = WebTextRepair.repair(book.kind),
                            intro = WebTextRepair.repair(book.intro),
                            latestChapterTitle = WebTextRepair.repair(book.latestChapterTitle),
                            durChapterTitle = WebTextRepair.repair(book.durChapterTitle),
                        )
                    },
                )
            }
        }

    /**
     * 获取封面
     */
    fun getCover(parameters: Map<String, List<String>>): ReturnData {
        return runBlocking { getCoverAwait(parameters) }
    }

    suspend fun getCoverAwait(parameters: Map<String, List<String>>): ReturnData =
        withContext(Dispatchers.IO) {
        val returnData = ReturnData()
        val coverPath = parameters["path"]?.firstOrNull()
        val ftBitmap = ImageLoader.loadBitmap(appCtx, coverPath)
            .override(84, 112)
            .centerCrop()
            .submit()
        try {
            returnData.setData(ftBitmap.get(3, TimeUnit.SECONDS))
        } catch (e: Exception) {
            try {
                val defaultBitmap = synchronized(defaultCoverCache) {
                    defaultCoverCache.getOrPut(BookCover.defaultDrawable) {
                        Glide.with(appCtx)
                            .asBitmap()
                            .load(BookCover.defaultDrawable.toBitmap())
                            .override(84, 112)
                            .centerCrop()
                            .submit()
                            .get()
                    }
                }
                returnData.setData(defaultBitmap)
            } catch (e: Exception) {
                returnData.setErrorMsg(e.localizedMessage ?: "Không thể tải ảnh bìa")
            }
        }
    }

    /**
     * 获取正文图片
     */
    fun getImg(parameters: Map<String, List<String>>): ReturnData {
        return runBlocking { getImgAwait(parameters) }
    }

    suspend fun getImgAwait(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val bookUrl = parameters["url"]?.firstOrNull()
            ?: return returnData.setErrorMsg("bookUrl đang trống")
        val src = parameters["path"]?.firstOrNull()
            ?: return returnData.setErrorMsg("Liên kết ảnh đang trống")
        val width = parameters["width"]?.firstOrNull()?.toInt() ?: 640
        val book = appDb.bookDao.getBook(bookUrl)
            ?: return returnData.setErrorMsg("bookUrl không hợp lệ")
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
        ImageProvider.cacheImage(book, src, bookSource)
        val bitmap = ImageProvider.getImage(book, src, width)
        return returnData.setData(bitmap)
    }

    /**
     * 更新目录
     */
    fun refreshToc(parameters: Map<String, List<String>>): ReturnData {
        return runBlocking { refreshTocAwait(parameters) }
    }

    suspend fun refreshTocAwait(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        try {
            val bookUrl = parameters["url"]?.firstOrNull()
            if (bookUrl.isNullOrEmpty()) {
                return returnData.setErrorMsg("Tham số URL không được để trống; hãy chỉ định địa chỉ truyện")
            }
            val book = appDb.bookDao.getBook(bookUrl)
                ?: return returnData.setErrorMsg("Không tìm thấy truyện trong cơ sở dữ liệu; hãy thêm truyện trước")
            if (book.isLocal) {
                val toc = LocalBook.getChapterList(book)
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                appDb.bookDao.update(book)
                return returnData.setData(toc)
            } else {
                val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                    ?: return returnData.setErrorMsg("Không tìm thấy nguồn tương ứng; hãy đổi nguồn")
                if (book.tocUrl.isBlank()) {
                    WebBook.getBookInfoAwait(bookSource, book)
                }
                val toc = WebBook.getChapterListAwait(bookSource, book).getOrThrow()
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                appDb.bookDao.update(book)
                return returnData.setData(toc)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return returnData.setErrorMsg(e.localizedMessage ?: "Không thể làm mới mục lục")
        }
    }

    /**
     * 获取目录
     */
    fun getChapterList(parameters: Map<String, List<String>>): ReturnData {
        return runBlocking { getChapterListAwait(parameters) }
    }

    suspend fun getChapterListAwait(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("Tham số URL không được để trống; hãy chỉ định địa chỉ truyện")
        }
        val chapterList = appDb.bookChapterDao.getChapterList(bookUrl)
        if (chapterList.isEmpty()) {
            return refreshTocAwait(parameters)
        }
        return returnData.setData(chapterList)
    }

    /**
     * 获取正文
     */
    fun getBookContent(parameters: Map<String, List<String>>): ReturnData {
        return runBlocking { getBookContentAwait(parameters) }
    }

    suspend fun getBookContentAwait(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val index = parameters["index"]?.firstOrNull()?.toInt()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("Tham số URL không được để trống; hãy chỉ định địa chỉ truyện")
        }
        if (index == null) {
            return returnData.setErrorMsg("Tham số index không được để trống; hãy chỉ định số thứ tự chương")
        }
        val book = appDb.bookDao.getBook(bookUrl)
            ?: return returnData.setErrorMsg("Không tìm thấy truyện")
        var chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
        if (chapter == null) {
            val refreshResult = refreshTocAwait(parameters)
            if (!refreshResult.isSuccess) {
                return returnData.setErrorMsg(refreshResult.errorMsg)
            }
            chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
        }
        if (chapter == null) {
            return returnData.setErrorMsg("Không tìm thấy truyện hoặc chương")
        }
        var content: String? = BookHelp.getContent(book, chapter)
        if (content != null) {
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            content = contentProcessor.getContent(book, chapter, content, includeTitle = false)
                .toString()
            return returnData.setData(content)
        }
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            ?: return returnData.setErrorMsg("Không tìm thấy nguồn truyện")
        try {
            content = WebBook.getContentAwait(bookSource, book, chapter).let {
                val contentProcessor = ContentProcessor.get(book.name, book.origin)
                contentProcessor.getContent(book, chapter, it, includeTitle = false)
                    .toString()
            }
            returnData.setData(content)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            returnData.setErrorMsg(e.stackTraceStr)
        }
        return returnData
    }

    /**
     * 保存书籍
     */
    suspend fun saveBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<Book>(postData).getOrNull()?.let { book ->
            AppWebDav.uploadBookProgress(book)
            book.save()
            return returnData.setData("")
        }
        return returnData.setErrorMsg("Định dạng dữ liệu không hợp lệ")
    }

    /**
     * 删除书籍
     */
    fun deleteBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<Book>(postData).getOrNull()?.let { book ->
            book.delete()
            return returnData.setData("")
        }
        return returnData.setErrorMsg("Định dạng dữ liệu không hợp lệ")
    }

    /**
     * 保存进度
     */
    suspend fun saveBookProgress(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<BookProgress>(postData)
            .onFailure { it.printOnDebug() }
            .getOrNull()?.let { bookProgress ->
                appDb.bookDao.getBook(bookProgress.name, bookProgress.author)?.let { book ->
                    book.durChapterIndex = bookProgress.durChapterIndex
                    book.durChapterPos = bookProgress.durChapterPos
                    book.durChapterTitle = bookProgress.durChapterTitle
                    book.durChapterTime = bookProgress.durChapterTime
                    AppWebDav.uploadBookProgress(bookProgress) {
                        book.syncTime = System.currentTimeMillis()
                    }
                    appDb.bookDao.update(book)
                    ReadBook.book?.let {
                        if (it.name == bookProgress.name &&
                            it.author == bookProgress.author
                        ) {
                            ReadBook.webBookProgress = bookProgress
                        }
                    }
                    return returnData.setData("")
                }
            }
        return returnData.setErrorMsg("Định dạng dữ liệu không hợp lệ")
    }

    /**
     * 添加本地书籍
     */
    fun addLocalBook(
        parameters: Map<String, List<String>>,
        files: Map<String, String>
    ): ReturnData {
        val returnData = ReturnData()
        val fileName = parameters["fileName"]?.firstOrNull()
            ?: return returnData.setErrorMsg("fileName không được để trống")
        val fileData = files["fileData"]
            ?: return returnData.setErrorMsg("fileData không được để trống")
        kotlin.runCatching {
            val uri = LocalBook.saveBookFile(File(fileData).inputStream(), fileName)
            LocalBook.importFile(uri)
        }.onFailure {
            return when (it) {
                is SecurityException -> returnData.setErrorMsg("Cần thiết lập lại vị trí lưu sách!")
                else -> returnData.setErrorMsg("Không thể lưu sách\n${it.localizedMessage}")
            }
        }
        return returnData.setData(true)
    }

    /**
     * 保存web阅读界面配置
     */
    fun saveWebReadConfig(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData?.let {
            CacheManager.put("webReadConfig", postData)
        } ?: CacheManager.delete("webReadConfig")
        return returnData.setData("")
    }

    /**
     * 获取web阅读界面配置
     */
    fun getWebReadConfig(): ReturnData {
        val returnData = ReturnData()
        val data = CacheManager.get("webReadConfig")
            ?: return returnData.setErrorMsg("Chưa có cấu hình")
        return returnData.setData(data)
    }

}
