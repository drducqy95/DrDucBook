package io.legado.app.model.webBook

import android.text.TextUtils
import com.drducbook.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isWebFile
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.utils.DebugLog
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StringUtils.wordCountFormat
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext


/**
 * 获取详情
 */
object BookInfo {

    @Throws(Exception::class)
    suspend fun analyzeBookInfo(
        bookSource: BookSource,
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        canReName: Boolean,
    ) {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        Debug.log(bookSource.bookSourceUrl, "≡Tải thành công:${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 20)
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body).setBaseUrl(baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(coroutineContext)
        analyzeBookInfo(book, body, analyzeRule, bookSource, baseUrl, redirectUrl, canReName)
    }

    suspend fun analyzeBookInfo(
        book: Book,
        body: String,
        analyzeRule: AnalyzeRule,
        bookSource: BookSource,
        baseUrl: String,
        redirectUrl: String,
        canReName: Boolean,
    ) {
        val infoRule = bookSource.getBookInfoRule()
        infoRule.init?.let {
            if (it.isNotBlank()) {
                coroutineContext.ensureActive()
                Debug.log(bookSource.bookSourceUrl, "≡Thực thi quy tắc khởi tạo trang chi tiết")
                analyzeRule.setContent(analyzeRule.getElement(it))
            }
        }
        val mCanReName = canReName && !infoRule.canReName.isNullOrBlank()
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌Lấy tên sách")
        BookHelp.formatBookName(analyzeRule.getString(infoRule.name)).let {
            if (it.isNotEmpty() && (mCanReName || book.name.isEmpty())) {
                book.name = it
            }
            Debug.log(bookSource.bookSourceUrl, "└${it}")
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌Lấy tác giả")
        BookHelp.formatBookAuthor(analyzeRule.getString(infoRule.author)).let {
            if (it.isNotEmpty() && (mCanReName || book.author.isEmpty())) {
                book.author = it
            }
            Debug.log(bookSource.bookSourceUrl, "└${it}")
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌Lấy thể loại")
        try {
            analyzeRule.getStringList(infoRule.kind)
                ?.joinToString(",")
                ?.take(1000)
                ?.let {
                    if (it.isNotEmpty()) book.kind = it
                    Debug.log(bookSource.bookSourceUrl, "└${it}")
                } ?: Debug.log(bookSource.bookSourceUrl, "└")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("Lỗi lấy thể loại", e)
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌Lấy số từ")
        try {
            wordCountFormat(analyzeRule.getString(infoRule.wordCount)).let {
                if (it.isNotEmpty()) book.wordCount = it
                Debug.log(bookSource.bookSourceUrl, "└${it}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("Lỗi lấy số từ", e)
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌Lấy chương mới nhất")
        try {
            analyzeRule.getString(infoRule.lastChapter).let {
                if (it.isNotEmpty()) book.latestChapterTitle = it
                Debug.log(bookSource.bookSourceUrl, "└${it}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("Lỗi lấy chương mới nhất", e)
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌Lấy giới thiệu")
        try {
            HtmlFormatter.format(analyzeRule.getString(infoRule.intro)).take(5000).let {
                if (it.isNotEmpty()) book.intro = it
                Debug.log(bookSource.bookSourceUrl, "└${it}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("Lỗi lấy giới thiệu", e)
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌Lấy liên kết ảnh bìa")
        try {
            analyzeRule.getString(infoRule.coverUrl).let {
                if (it.isNotEmpty()) {
                    book.coverUrl =
                        NetworkUtils.getAbsoluteURL(redirectUrl, it)
                }
                Debug.log(bookSource.bookSourceUrl, "└${it}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "└${e.localizedMessage}")
            DebugLog.e("Lỗi lấy ảnh bìa", e)
        }
        coroutineContext.ensureActive()
        if (!book.isWebFile) {
            Debug.log(bookSource.bookSourceUrl, "┌Lấy liên kết mục lục")
            book.tocUrl = analyzeRule.getString(infoRule.tocUrl, isUrl = true)
            if (book.tocUrl.isEmpty()) book.tocUrl = baseUrl
            if (book.tocUrl == baseUrl) {
                book.tocHtml = body
            }
            Debug.log(bookSource.bookSourceUrl, "└${book.tocUrl}")
        } else {
            Debug.log(bookSource.bookSourceUrl, "┌Lấy liên kết tải tệp")
            book.downloadUrls = analyzeRule.getStringList(infoRule.downloadUrls, isUrl = true)
            if (book.downloadUrls.isNullOrEmpty()) {
                Debug.log(bookSource.bookSourceUrl, "└")
                throw NoStackTraceException("Liên kết tải xuống trống")
            } else {
                Debug.log(
                    bookSource.bookSourceUrl,
                    "└" + TextUtils.join("，\n", book.downloadUrls!!)
                )
            }
        }
    }

}