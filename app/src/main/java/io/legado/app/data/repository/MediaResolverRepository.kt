package io.legado.app.data.repository

import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.MediaDownloadDao
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.MediaResolverGateway
import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.MediaProtocol
import io.legado.app.domain.model.ResolvedBookMedia
import io.legado.app.domain.model.ResolvedMedia
import io.legado.app.domain.model.ResolvedMediaChapter
import io.legado.app.domain.model.ResolvedMediaVariant
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isVideo
import io.legado.app.help.media.MediaChapterPolicy
import io.legado.app.help.media.MediaSourceRuleResultParser
import io.legado.app.help.media.MediaUriResolver
import io.legado.app.help.vbook.VbookPluginAdapter
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.TextEncodingRepair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.coroutineContext

class MediaResolverRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val bookSourceDao: BookSourceDao,
    private val mediaDownloadDao: MediaDownloadDao,
) : MediaResolverGateway {

    override suspend fun resolveBookMedia(
        bookUrl: String,
        chapterIndex: Int?,
    ): Result<ResolvedBookMedia> {
        return try {
            Result.success(withContext(Dispatchers.IO) {
                withTimeout(MEDIA_RESOLVE_TIMEOUT_MS) {
                val book = bookDao.getBook(bookUrl)
                    ?: error("Không tìm thấy sách trong giá sách")
                val source = bookSourceDao.getBookSource(book.origin)
                val chapters = ensurePlayableChapters(book, source)
                require(chapters.isNotEmpty()) { "Danh sách tập/chương đang trống" }
                val requestedIndex = chapterIndex ?: book.durChapterIndex
                val chapter = chapters.firstOrNull { it.index == requestedIndex }
                    ?: chapters.getOrNull(requestedIndex.coerceIn(0, chapters.lastIndex))
                    ?: chapters.first()
                val position = chapters.indexOf(chapter)
                val offlineChapterIndices = mediaDownloadDao.getCompletedForBook(book.bookUrl)
                    .asSequence()
                    .filter { File(it.localPath).isFile && File(it.localPath).length() > 0L }
                    .map { it.chapterIndex }
                    .toSet()
                val completedDownload = mediaDownloadDao.getCompleted(book.bookUrl, chapter.index)
                val offline = completedDownload
                    ?.takeIf { File(it.localPath).isFile && File(it.localPath).length() > 0L }
                if (completedDownload != null && offline == null) {
                    mediaDownloadDao.updateItemState(
                        completedDownload.id,
                        "FAILED",
                        "Tệp media ngoại tuyến bị thiếu hoặc hỏng",
                        completedDownload.retryCount + 1,
                        System.currentTimeMillis(),
                    )
                }
                val media = if (offline != null) {
                    ResolvedMedia(
                        sourceId = "offline",
                        contentId = offline.id,
                        title = chapter.title,
                        variants = listOf(
                            ResolvedMediaVariant(
                                id = "offline:${offline.id}",
                                title = "Offline",
                                uri = File(offline.localPath).toURI().toString(),
                                contentKind = if (book.isVideo) {
                                    MediaContentKind.VIDEO
                                } else {
                                    MediaContentKind.AUDIO
                                },
                                protocol = MediaProtocol.DIRECT,
                                mimeType = offline.mimeType,
                                headers = emptyMap(),
                                referer = "",
                                expiresAt = null,
                                downloadSupported = false,
                                externalPlayerRequired = false,
                            )
                        ),
                        subtitles = emptyList(),
                        audioTracks = emptyList(),
                        resolvedAt = System.currentTimeMillis(),
                    )
                } else if (book.origin == BookType.localTag && !chapter.resourceUrl.isNullOrBlank()) {
                    val localMedia = MediaUriResolver.resolve(
                        sourceId = BookType.localTag,
                        contentId = chapter.url,
                        title = chapter.title,
                        uri = chapter.resourceUrl!!,
                        defaultKind = MediaContentKind.AUDIO,
                        headers = emptyMap(),
                    )
                    localMedia.copy(
                        variants = localMedia.variants.map {
                            it.copy(downloadSupported = false)
                        }
                    )
                } else if (source != null && VbookPluginAdapter.canHandle(source)) {
                    VbookPluginAdapter.resolveMedia(source, book, chapter)
                } else if (source != null && (book.isAudio || book.isVideo)) {
                    resolveSourceContentRuleMedia(
                        source = source,
                        book = book,
                        chapter = chapter,
                        nextChapterUrl = chapters.getOrNull(position + 1)?.url,
                    )
                } else {
                    val analyzed = AnalyzeUrl(
                        mUrl = chapter.getAbsoluteURL(),
                        source = source,
                        ruleData = book,
                        chapter = chapter,
                        coroutineContext = coroutineContext,
                    )
                    val (resolvedUrl, headers) = analyzed.getUrlAndHeaders()
                    MediaUriResolver.resolve(
                        sourceId = source?.bookSourceUrl.orEmpty().ifBlank { book.origin },
                        contentId = chapter.url,
                        title = chapter.title,
                        uri = resolvedUrl,
                        defaultKind = if (book.isVideo) {
                            MediaContentKind.VIDEO
                        } else {
                            MediaContentKind.AUDIO
                        },
                        headers = headers,
                    )
                }
                val normalizedMedia = media.copy(
                    title = TextEncodingRepair.repair(media.title).orEmpty(),
                    variants = media.variants.map { variant ->
                        variant.copy(title = TextEncodingRepair.repair(variant.title).orEmpty())
                    },
                    subtitles = media.subtitles.map { track ->
                        track.copy(label = TextEncodingRepair.repair(track.label).orEmpty())
                    },
                    audioTracks = media.audioTracks.map { track ->
                        track.copy(label = TextEncodingRepair.repair(track.label).orEmpty())
                    },
                )
                val resolvedIsVideo = book.isVideo ||
                        normalizedMedia.variants.any { it.contentKind == MediaContentKind.VIDEO } ||
                        MediaChapterPolicy.isVideoChapter(chapter)
                if (resolvedIsVideo) {
                    if (MediaChapterPolicy.normalizeVideoBookType(book, chapters)) {
                        bookDao.update(book)
                    }
                }
                ResolvedBookMedia(
                    bookUrl = book.bookUrl,
                    bookTitle = TextEncodingRepair.repair(book.name).orEmpty(),
                    coverUrl = book.getDisplayCover(),
                    chapterIndex = chapter.index,
                    chapterCount = chapters.size,
                    previousChapterIndex = chapters.getOrNull(position - 1)?.index,
                    nextChapterIndex = chapters.getOrNull(position + 1)?.index,
                    isVideo = resolvedIsVideo,
                    clipStartMs = chapter.start,
                    clipEndMs = chapter.end,
                    chapters = chapters.map {
                        ResolvedMediaChapter(
                            index = it.index,
                            title = TextEncodingRepair.repair(it.title).orEmpty(),
                            isOffline = it.index in offlineChapterIndices,
                        )
                    },
                    media = normalizedMedia,
                )
                }
            })
        } catch (error: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Nguồn media phản hồi quá lâu", error))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun ensurePlayableChapters(
        book: Book,
        source: BookSource?,
    ): List<BookChapter> {
        bookChapterDao.getChapterList(book.bookUrl)
            .filterNot { it.isVolume }
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        require(book.origin != BookType.localTag) {
            "Danh sách tập/chương media cục bộ đang trống"
        }
        val resolvedSource = requireNotNull(source) {
            "Không tìm thấy nguồn media tương ứng"
        }
        if (book.tocUrl.isBlank()) {
            WebBook.getBookInfoAwait(resolvedSource, book)
        }
        val loaded = WebBook.getChapterListAwait(resolvedSource, book).getOrThrow()
        bookChapterDao.delByBook(book.bookUrl)
        if (loaded.isNotEmpty()) {
            bookChapterDao.insert(*loaded.toTypedArray())
        }
        bookDao.update(book)
        return loaded.filterNot { it.isVolume }
    }

    private suspend fun resolveSourceContentRuleMedia(
        source: BookSource,
        book: Book,
        chapter: BookChapter,
        nextChapterUrl: String?,
    ): ResolvedMedia {
        val fallbackHeaders = runCatching { source.getHeaderMap(true).toMap() }
            .getOrDefault(emptyMap())
        val raw = runCatching {
            WebBook.getContentAwait(
                bookSource = source,
                book = book,
                bookChapter = chapter,
                nextChapterUrl = nextChapterUrl,
                needSave = false,
                followNextPages = false,
            )
        }.getOrElse { error ->
            throw IllegalStateException(
                "Media source content stage failed: ${error.message ?: error::class.java.simpleName}",
                error,
            )
        }
        return runCatching {
            MediaSourceRuleResultParser.parse(
                sourceId = source.bookSourceUrl,
                contentId = chapter.url,
                title = chapter.title.ifBlank { book.name },
                raw = raw,
                defaultKind = if (book.isVideo) {
                    MediaContentKind.VIDEO
                } else {
                    MediaContentKind.AUDIO
                },
                fallbackHeaders = fallbackHeaders,
                baseUrl = chapter.getAbsoluteURL(),
            )
        }.getOrElse { error ->
            throw IllegalStateException(
                "Media source parse stage failed: ${error.message ?: error::class.java.simpleName}",
                error,
            )
        }
    }

    private companion object {
        const val MEDIA_RESOLVE_TIMEOUT_MS = 45_000L
    }
}
