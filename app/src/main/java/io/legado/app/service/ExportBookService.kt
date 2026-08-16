package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.graphics.BitmapFactory
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.drducbook.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.model.VbookContentLockPolicy
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.translation.TranslationManager
import io.legado.app.service.export.EbookExportChapter
import io.legado.app.service.export.EbookExportContentSource
import io.legado.app.service.export.EbookExportFormat
import io.legado.app.service.export.EbookExportImage
import io.legado.app.service.export.EbookExportImageOptimization
import io.legado.app.service.export.EbookExportLabels
import io.legado.app.service.export.EbookExportPayload
import io.legado.app.service.export.EbookExportWriter
import io.legado.app.service.export.selectExportChapterIndices
import io.legado.app.service.export.splitExportChapters
import io.legado.app.ui.config.translation.TranslationConfig
import io.legado.app.ui.book.info.KindleSendActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.cnCompare
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.delete
import io.legado.app.utils.find
import io.legado.app.utils.list
import io.legado.app.utils.mapAsync
import io.legado.app.utils.mapAsyncIndexed
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeFile
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.ag2s.epublib.domain.Author
import me.ag2s.epublib.domain.Date
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.FileResourceProvider
import me.ag2s.epublib.domain.LazyResource
import me.ag2s.epublib.domain.LazyResourceProvider
import me.ag2s.epublib.domain.Metadata
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.domain.TOCReference
import me.ag2s.epublib.epub.EpubWriter
import me.ag2s.epublib.epub.EpubWriterProcessor
import me.ag2s.epublib.util.ResourceUtil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.nio.charset.Charset
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * 导出书籍服务
 */
class ExportBookService : BaseService(), KoinComponent {

    companion object {
        private const val LARGE_EXPORT_BYTES = 30L * 1024L * 1024L
        private const val KINDLE_PART_BYTES = 20L * 1024L * 1024L
        val exportProgress = ConcurrentHashMap<String, Int>()
        val exportMsg = ConcurrentHashMap<String, String>()
        private val _exportBookUpdateFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val exportBookUpdateFlow = _exportBookUpdateFlow.asSharedFlow()
    }

    data class ExportConfig(
        val path: String,
        val type: String,
        val epubSize: Int = 1,
        val scope: String? = null,
        val translationProvider: String,
        val targetLanguage: String,
        val contentSource: EbookExportContentSource = EbookExportContentSource.BOTH,
        val imageOptimization: EbookExportImageOptimization = EbookExportImageOptimization.ORIGINAL,
        val sendToKindle: Boolean = false,
    )

    /**
     * Content source for export - Original or Translation with target language.
     */
    private enum class ContentSource {
        Original,
        Translation
    }

    private val translationCacheRepository: TranslationCacheGateway by inject()

    private val groupKey = "${appCtx.packageName}.exportBook"
    private val waitExportBooks = linkedMapOf<String, ExportConfig>()
    private val exportQueueLock = Any()
    private val exportWorkerLock = Any()
    private var exportJob: Job? = null
    @Volatile
    private var latestStartId: Int = 0
    private var notificationContentText = appCtx.getString(R.string.service_starting)
    @Volatile
    private var lastExportedFile: ExportedFile? = null
    @Volatile
    private var lastExportedFiles: List<ExportedFile> = emptyList()
    @Volatile
    private var lastExportWantsKindle: Boolean = false
    @Volatile
    private var lastExportPartCount: Int = 1

    private data class ExportedFile(
        val uri: String,
        val fileName: String,
        val mimeType: String,
    )


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            IntentAction.start -> kotlin.runCatching {
                val bookUrl = intent.getStringExtra("bookUrl")!!
                val exportConfig = ExportConfig(
                    path = intent.getStringExtra("exportPath")!!,
                    type = intent.getStringExtra("exportType")!!,
                    epubSize = intent.getIntExtra("epubSize", 1),
                    scope = intent.getStringExtra("exportScope")
                        ?: intent.getStringExtra("epubScope"),
                    translationProvider = TranslationConfig.llmProvider,
                    targetLanguage = TranslationConfig.llmTargetLanguage,
                    contentSource = EbookExportContentSource.from(
                        intent.getStringExtra("exportContentSource")
                    ),
                    imageOptimization = EbookExportImageOptimization.from(
                        intent.getStringExtra("exportImageOptimization")
                    ),
                    sendToKindle = intent.getBooleanExtra("exportSendToKindle", false),
                )
                if (enqueueExport(bookUrl, exportConfig)) {
                    exportMsg[bookUrl] = getString(R.string.export_wait)
                    notifyExportBookChanged(bookUrl)
                    export()
                }
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }

            IntentAction.stop -> {
                notificationManager.cancel(NotificationId.ExportBook)
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        exportProgress.clear()
        exportMsg.clear()
        pendingExportBookUrls().forEach {
            notifyExportBookChanged(it)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_export)
            .setSubText(getString(R.string.export_book))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(groupKey)
            .setGroupSummary(true)
        startForeground(NotificationId.ExportBookService, notification.build())
    }

    private fun upExportNotification(finish: Boolean = false) {
        val notification = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_export)
            .setSubText(getString(R.string.export_book))
            .setContentIntent(
                activityPendingIntent(
                    MainActivity.createCacheIntent(this),
                    "cacheActivity"
                )
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentText(notificationContentText)
            .setDeleteIntent(servicePendingIntent<ExportBookService>(IntentAction.stop))
            .setGroup(groupKey)
            .setOnlyAlertOnce(true)
        if (!finish) {
            notification.setOngoing(true)
            notification.addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<ExportBookService>(IntentAction.stop)
            )
        } else {
            if (lastExportWantsKindle && lastExportPartCount > 1) {
                notificationContentText = "Export complete; ${lastExportPartCount} Kindle parts are ready"
            }
            lastExportedFile
                ?.takeIf { lastExportWantsKindle }
                ?.takeIf { exported -> KindleSendActivity.isSupportedFileName(exported.fileName) }
                ?.let { exported ->
                    val supportedParts = lastExportedFiles
                        .filter { part -> KindleSendActivity.isSupportedFileName(part.fileName) }
                    val intent = Intent(this, KindleSendActivity::class.java).apply {
                        putExtra(KindleSendActivity.EXTRA_URI, exported.uri)
                        putExtra(KindleSendActivity.EXTRA_FILE_NAME, exported.fileName)
                        putExtra(KindleSendActivity.EXTRA_MIME_TYPE, exported.mimeType)
                        if (supportedParts.size > 1) {
                            putStringArrayListExtra(
                                KindleSendActivity.EXTRA_PART_URIS,
                                ArrayList(supportedParts.map { it.uri }),
                            )
                            putStringArrayListExtra(
                                KindleSendActivity.EXTRA_PART_FILE_NAMES,
                                ArrayList(supportedParts.map { it.fileName }),
                            )
                            putStringArrayListExtra(
                                KindleSendActivity.EXTRA_PART_MIME_TYPES,
                                ArrayList(supportedParts.map { it.mimeType }),
                            )
                        }
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        NotificationId.ExportBook + 1,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    notification.addAction(
                        R.drawable.ic_share,
                        if (lastExportPartCount > 1) {
                            "Send Kindle part 1/${lastExportPartCount}"
                        } else {
                            getString(R.string.send_to_kindle)
                        },
                        pendingIntent,
                    )
                }
        }
        notificationManager.notify(NotificationId.ExportBook, notification.build())
    }

    private fun openKindleComposerIfPossible() {
        if (!lastExportWantsKindle) return
        val exported = lastExportedFile ?: return
        if (!KindleSendActivity.isSupportedFileName(exported.fileName)) return
        runCatching {
            val supportedParts = lastExportedFiles
                .filter { part -> KindleSendActivity.isSupportedFileName(part.fileName) }
            startActivity(Intent(this, KindleSendActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(KindleSendActivity.EXTRA_URI, exported.uri)
                putExtra(KindleSendActivity.EXTRA_FILE_NAME, exported.fileName)
                putExtra(KindleSendActivity.EXTRA_MIME_TYPE, exported.mimeType)
                if (supportedParts.size > 1) {
                    putStringArrayListExtra(
                        KindleSendActivity.EXTRA_PART_URIS,
                        ArrayList(supportedParts.map { it.uri }),
                    )
                    putStringArrayListExtra(
                        KindleSendActivity.EXTRA_PART_FILE_NAMES,
                        ArrayList(supportedParts.map { it.fileName }),
                    )
                    putStringArrayListExtra(
                        KindleSendActivity.EXTRA_PART_MIME_TYPES,
                        ArrayList(supportedParts.map { it.mimeType }),
                    )
                }
            })
        }
    }

    private fun export() {
        synchronized(exportWorkerLock) {
            if (exportJob?.isActive == true) return
            exportJob = lifecycleScope.launch(IO) {
                exportQueuedBooks()
            }
        }
    }

    private suspend fun exportQueuedBooks() {
        while (coroutineContext.isActive) {
            val next = takeNextExport()
            if (next == null) {
                val stopStartId = latestStartId
                synchronized(exportWorkerLock) {
                    exportJob = null
                }
                if (hasQueuedExports()) {
                    export()
                    return
                }
                notificationContentText = if (lastExportWantsKindle && lastExportPartCount > 1) {
                    "Export complete; $lastExportPartCount Kindle parts are ready"
                } else {
                    getString(R.string.export_complete)
                }
                upExportNotification(true)
                openKindleComposerIfPossible()
                stopSelfResult(stopStartId)
                return
            }
            val (bookUrl, exportConfig) = next
                exportProgress[bookUrl] = 0
                lastExportWantsKindle = exportConfig.sendToKindle
                lastExportedFile = null
                lastExportedFiles = emptyList()
                lastExportPartCount = 1
                val book = appDb.bookDao.getBook(bookUrl)
                try {
                    book ?: throw NoStackTraceException(
                        getString(R.string.export_book_not_found, bookUrl)
                    )
                    if (VbookContentLockPolicy.isLocked(book.origin, AppConfig.vbookEbookUnlockCode)) {
                        throw NoStackTraceException(getString(R.string.vbook_ebook_access_locked))
                    }
                    refreshChapterList(book)
                    notificationContentText = getString(
                        R.string.export_book_notification_content,
                        book.name,
                        queuedExportCount(),
                    )
                    upExportNotification()
                    val format = EbookExportFormat.from(exportConfig.type)
                    val exportOriginal = exportConfig.contentSource.includesOriginal
                    val exportTranslation = exportConfig.contentSource.includesTranslation
                    val strictTranslation = exportConfig.contentSource == EbookExportContentSource.TRANSLATION
                    if (format == EbookExportFormat.EPUB2) {
                        if (exportConfig.scope.isNullOrBlank() || exportConfig.scope == "all") {
                            if (exportOriginal) {
                                exportEpub(
                                    exportConfig.path,
                                    book,
                                    ContentSource.Original,
                                    exportConfig.translationProvider,
                                    exportConfig.targetLanguage,
                                )
                            }
                            exportTranslationIfAvailable(
                                requested = exportTranslation,
                                strict = strictTranslation,
                                book = book,
                                targetLanguage = exportConfig.targetLanguage,
                                provider = exportConfig.translationProvider,
                            ) {
                                exportEpub(
                                    exportConfig.path,
                                    book,
                                    ContentSource.Translation,
                                    exportConfig.translationProvider,
                                    exportConfig.targetLanguage,
                                    strictTranslation,
                                )
                            }
                        } else {
                            if (exportOriginal) {
                                CustomExporter(
                                    exportConfig.scope,
                                    exportConfig.epubSize
                                ).export(exportConfig.path, book)
                            }
                            exportTranslationIfAvailable(
                                requested = exportTranslation,
                                strict = strictTranslation,
                                book = book,
                                targetLanguage = exportConfig.targetLanguage,
                                provider = exportConfig.translationProvider,
                            ) {
                                exportEpub(
                                    exportConfig.path,
                                    book,
                                    ContentSource.Translation,
                                    exportConfig.translationProvider,
                                    exportConfig.targetLanguage,
                                    strictTranslation,
                                )
                            }
                        }
                    } else {
                        if (exportOriginal) {
                            exportModernFormat(
                                exportConfig.path,
                                book,
                                format,
                                ContentSource.Original,
                                exportConfig.scope,
                                exportConfig.translationProvider,
                                exportConfig.targetLanguage,
                                imageOptimization = exportConfig.imageOptimization,
                            )
                        }
                        exportTranslationIfAvailable(
                            requested = exportTranslation,
                            strict = strictTranslation,
                            book = book,
                            targetLanguage = exportConfig.targetLanguage,
                            provider = exportConfig.translationProvider,
                        ) {
                            exportModernFormat(
                                exportConfig.path,
                                book,
                                format,
                                ContentSource.Translation,
                                exportConfig.scope,
                                exportConfig.translationProvider,
                                exportConfig.targetLanguage,
                                strictTranslation,
                                exportConfig.imageOptimization,
                            )
                        }
                    }
                    exportMsg[book.bookUrl] = if (
                        exportConfig.sendToKindle && lastExportPartCount > 1
                    ) {
                        "Export complete; $lastExportPartCount parts will be opened for sequential Send-to-Kindle."
                    } else if (
                        exportConfig.imageOptimization == EbookExportImageOptimization.ORIGINAL &&
                        hasLargeExport(exportConfig.path, book, format)
                    ) {
                        "Export complete. The file is large; Balanced/Small can reduce it before Send to Kindle."
                    } else {
                        getString(R.string.export_success)
                    }
                } catch (e: Throwable) {
                    coroutineContext.ensureActive()
                    exportMsg[bookUrl] = e.localizedMessage ?: getString(R.string.error)
                    AppLog.put(
                        getString(
                            R.string.export_book_failed_log,
                            book?.name ?: bookUrl,
                            e.localizedMessage.orEmpty(),
                        ),
                        e,
                    )
                } finally {
                    exportProgress.remove(bookUrl)
                    notifyExportBookChanged(bookUrl)
                }
        }
    }

    private fun enqueueExport(bookUrl: String, config: ExportConfig): Boolean =
        synchronized(exportQueueLock) {
            if (exportProgress.containsKey(bookUrl) || waitExportBooks.containsKey(bookUrl)) {
                false
            } else {
                waitExportBooks[bookUrl] = config
                true
            }
        }

    private fun takeNextExport(): Pair<String, ExportConfig>? = synchronized(exportQueueLock) {
        val entry = waitExportBooks.entries.firstOrNull() ?: return@synchronized null
        waitExportBooks.remove(entry.key)
        entry.key to entry.value
    }

    private fun queuedExportCount(): Int = synchronized(exportQueueLock) {
        waitExportBooks.size
    }

    private fun hasQueuedExports(): Boolean = synchronized(exportQueueLock) {
        waitExportBooks.isNotEmpty()
    }

    private fun pendingExportBookUrls(): List<String> = synchronized(exportQueueLock) {
        waitExportBooks.keys.toList()
    }

    private fun refreshChapterList(book: Book) {
        if (!book.isLocalModified()) {
            return
        }
        kotlin.runCatching {
            LocalBook.getChapterList(book)
        }.onSuccess {
            appDb.bookChapterDao.delByBook(book.bookUrl)
            appDb.bookChapterDao.insert(*it.toTypedArray())
            appDb.bookDao.update(book)
            ReadBook.onChapterListUpdated(book)
        }
    }

    private data class SrcData(
        val chapterTitle: String,
        val index: Int,
        val src: String
    )

    private suspend fun exportTxt(path: String, book: Book) {
        exportMsg.remove(book.bookUrl)
        notifyExportBookChanged(book.bookUrl)
        val fileDoc = FileDoc.fromDir(path)
        exportTxt(fileDoc, book, ContentSource.Original)
    }

    private suspend fun exportTxt(fileDoc: FileDoc, book: Book, source: ContentSource) {
        val targetLanguage = TranslationConfig.llmTargetLanguage
        val filename = when (source) {
            ContentSource.Original -> book.getExportFileName("txt")
            ContentSource.Translation -> getTranslatedFileName(book.getExportFileName("txt"), targetLanguage)
        }
        fileDoc.find(filename)?.delete()

        val bookDoc = fileDoc.createFileIfNotExist(filename)
        val charset = Charset.forName(AppConfig.exportCharset)
        bookDoc.openOutputStream().getOrThrow().bufferedWriter(charset).use { bw ->
            getAllContents(book, source) { text, srcList ->
                bw.write(text)
                // Only export images for original source
                if (source == ContentSource.Original) {
                    srcList?.forEach {
                        val vFile = BookHelp.getImage(book, it.src)
                        if (vFile.exists()) {
                            fileDoc.createFileIfNotExist(
                                "${it.index}-${MD5Utils.md5Encode16(it.src)}.jpg",
                                subDirs = arrayOf(
                                    "${book.name}_${book.author}",
                                    "images",
                                    it.chapterTitle
                                )
                            ).writeFile(vFile)
                        }
                    }
                }
            }
        }
        if (AppConfig.exportToWebDav) {
            // 导出到webdav
            AppWebDav.exportWebDav(bookDoc.uri, filename)
        }
    }

    /**
     * Get translated filename by inserting target language before extension.
     * e.g., "book.txt" -> "book.zh.txt"
     */
    private fun getTranslatedFileName(originalName: String, targetLanguage: String): String {
        val lastDot = originalName.lastIndexOf('.')
        return if (lastDot > 0) {
            "${originalName.substring(0, lastDot)}.$targetLanguage${originalName.substring(lastDot)}"
        } else {
            "$originalName.$targetLanguage"
        }
    }

    private suspend fun hasAnyPreferredTranslatedChapter(
        book: Book,
        targetLanguage: String,
    ): Boolean {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        return chapters.any { chapter ->
            TranslationManager.getPreferredCachedTranslation(
                book = book,
                chapter = chapter,
                targetLanguage = targetLanguage,
            ) != null
        }
    }

    private suspend fun exportTranslationIfAvailable(
        requested: Boolean,
        strict: Boolean,
        book: Book,
        targetLanguage: String,
        @Suppress("UNUSED_PARAMETER") provider: String,
        exportAction: suspend () -> Unit,
    ) {
        if (!requested) return
        val hasTranslation = hasAnyPreferredTranslatedChapter(book, targetLanguage)
        if (!hasTranslation) {
            if (strict) {
                throw NoStackTraceException(getString(R.string.no_translation_cache))
            }
            return
        }
        exportAction()
    }

    private suspend fun readTranslatedContent(
        book: Book,
        chapter: BookChapter,
        targetLanguage: String,
        @Suppress("UNUSED_PARAMETER") provider: String,
        strict: Boolean,
    ): String? {
        val translated = TranslationManager.getPreferredCachedTranslation(
            book,
            chapter,
            targetLanguage,
        )?.content
        if (translated != null || !strict) {
            return translated ?: BookHelp.getContent(book, chapter)
        }
        throw NoStackTraceException(getString(R.string.no_translation_cache))
    }

    private suspend fun exportModernFormat(
        path: String,
        book: Book,
        format: EbookExportFormat,
        source: ContentSource,
        scope: String?,
        translationProvider: String,
        targetLanguage: String,
        strictTranslation: Boolean = false,
        imageOptimization: EbookExportImageOptimization = EbookExportImageOptimization.ORIGINAL,
    ) {
        exportMsg.remove(book.bookUrl)
        notifyExportBookChanged(book.bookUrl)
        val allChapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val selectedIndices = selectExportChapterIndices(scope, allChapters.size)
        if (selectedIndices.isEmpty()) {
            throw NoStackTraceException(getString(R.string.export_scope_empty))
        }
        val selectedChapters = allChapters.filterIndexed { index, _ -> index in selectedIndices }
        val useReplace = AppConfig.exportUseReplace && book.getUseReplaceRule()
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val exportChapters = selectedChapters.mapIndexed { progress, chapter ->
            coroutineContext.ensureActive()
            val rawContent = when (source) {
                ContentSource.Original -> BookHelp.getContent(book, chapter)
                ContentSource.Translation -> readTranslatedContent(
                    book,
                    chapter,
                    targetLanguage,
                    translationProvider,
                    strictTranslation,
                )
            }.orEmpty()
            chapter.isVip = false
            val processed = contentProcessor.getContent(
                book = book,
                chapter = chapter,
                content = rawContent,
                includeTitle = false,
                useReplace = useReplace,
                chineseConvert = false,
                reSegment = false,
            ).toString()
            val title = chapter.getDisplayTitle(
                contentProcessor.getTitleReplaceRules(),
                useReplace = useReplace,
            ).replace("\uD83D\uDD12", "")
            val images = if (source == ContentSource.Original) {
                collectExportImages(book, chapter, rawContent)
            } else {
                emptyList()
            }
            exportProgress[book.bookUrl] = progress + 1
            notifyExportBookChanged(book.bookUrl)
            EbookExportChapter(
                index = chapter.index,
                title = title,
                html = processed,
                plainText = HtmlFormatter.format(processed),
                images = images,
            )
        }
        val cover = runCatching {
            resolveExportCover(book)
        }.getOrNull()
        val effectiveOptimization = if (
            imageOptimization == EbookExportImageOptimization.ORIGINAL &&
            lastExportWantsKindle
        ) {
            EbookExportImageOptimization.BALANCED
        } else {
            imageOptimization
        }
        val payload = EbookExportPayload(
            title = book.name,
            author = book.getRealAuthor(),
            intro = HtmlFormatter.format(book.getDisplayIntro()),
            language = if (source == ContentSource.Translation) targetLanguage else "zh",
            description = HtmlFormatter.format(book.getDisplayIntro()),
            identifier = "urn:drducbook:book:${book.bookUrl.hashCode().toUInt().toString(16)}",
            subjects = listOfNotNull(book.kind, book.customTag)
                .flatMap { it.split(',', ';', '|') }
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct(),
            metadataDate = book.lastCheckTime
                .takeIf { it > 0L }
                ?.let { java.time.Instant.ofEpochMilli(it).toString() },
            cover = cover,
            chapters = exportChapters,
            labels = EbookExportLabels(
                author = getString(R.string.author),
                introduction = getString(R.string.book_intro),
                tableOfContents = getString(R.string.view_toc),
            ),
            imageOptimization = effectiveOptimization,
        )
        val baseName = book.getExportFileName(format.extension)
        val fileName = when (source) {
            ContentSource.Original -> baseName
            ContentSource.Translation -> getTranslatedFileName(baseName, targetLanguage)
        }
        val fileDoc = FileDoc.fromDir(path)
        val writer = EbookExportWriter(
            outputDirectory = fileDoc,
            charset = Charset.forName(AppConfig.exportCharset),
            imageOptimization = effectiveOptimization,
            onProgress = { completed, _ ->
                exportProgress[book.bookUrl] = completed
                notifyExportBookChanged(book.bookUrl)
            },
        )
        val output = writer.write(payload, format, fileName)
        val outputs = if (
            lastExportWantsKindle &&
            output.size >= KINDLE_PART_BYTES &&
            payload.chapters.size > 1
        ) {
            splitModernExport(
                writer = writer,
                fileDoc = fileDoc,
                payload = payload,
                format = format,
                fileName = fileName,
            )
        } else {
            listOf(output)
        }
        lastExportPartCount = outputs.size
        val kindleOutput = outputs.first()
        lastExportedFiles = outputs.map { exported ->
            ExportedFile(
                uri = exported.uri.toString(),
                fileName = exported.name,
                mimeType = mimeTypeFor(format),
            )
        }
        lastExportedFile = lastExportedFiles.firstOrNull()
        if (AppConfig.exportToWebDav) {
            outputs.forEach { exported -> AppWebDav.exportWebDav(exported.uri, exported.name) }
        }
    }

    private fun splitModernExport(
        writer: EbookExportWriter,
        fileDoc: FileDoc,
        payload: EbookExportPayload,
        format: EbookExportFormat,
        fileName: String,
    ): List<FileDoc> {
        fileDoc.find(fileName)?.delete()
        val groups = splitExportChapters(payload.chapters, KINDLE_PART_BYTES)
        if (groups.size <= 1) {
            return listOf(writer.write(payload, format, fileName))
        }
        return groups.mapIndexed { index, chapters ->
            val partPayload = payload.copy(
                title = "${payload.title} (Part ${index + 1}/${groups.size})",
                chapters = chapters,
            )
            writer.write(partPayload, format, addPartSuffix(fileName, index + 1))
        }
    }

    private fun addPartSuffix(fileName: String, part: Int): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) {
            "${fileName.substring(0, dot)}-part-${part.toString().padStart(2, '0')}${fileName.substring(dot)}"
        } else {
            "$fileName-part-${part.toString().padStart(2, '0')}"
        }
    }

    private fun mimeTypeFor(format: EbookExportFormat): String = when (format) {
        EbookExportFormat.EPUB2, EbookExportFormat.EPUB3 -> "application/epub+zip"
        EbookExportFormat.PDF -> "application/pdf"
        EbookExportFormat.TXT -> "text/plain"
        EbookExportFormat.HTML -> "text/html"
        EbookExportFormat.CBZ -> "application/zip"
    }

    private fun hasLargeExport(path: String, book: Book, format: EbookExportFormat): Boolean {
        val directory = FileDoc.fromDir(path)
        val expected = book.getExportFileName(format.extension)
        val candidates = listOfNotNull(directory.find(expected)) +
            (directory.list().orEmpty().filter { file ->
                !file.isDir && file.name.endsWith(".${format.extension}", ignoreCase = true)
            })
        return candidates.any { it.size >= LARGE_EXPORT_BYTES }
    }

    private fun collectExportImages(
        book: Book,
        chapter: BookChapter,
        content: String,
    ): List<EbookExportImage> {
        val result = arrayListOf<EbookExportImage>()
        val seen = hashSetOf<String>()
        content.lineSequence().forEach { line ->
            val matcher = AppPattern.imgPattern.matcher(line)
            while (matcher.find()) {
                val relative = matcher.group(1) ?: continue
                val source = NetworkUtils.getAbsoluteURL(chapter.url, relative)
                if (!seen.add(source)) continue
                val file = BookHelp.getImage(book, source)
                if (file.exists()) {
                    result += EbookExportImage(
                        source = source,
                        file = file,
                        fileName = "${MD5Utils.md5Encode16(source)}.${BookHelp.getImageSuffix(source)}",
                        aliases = listOf(relative),
                    )
                }
            }
        }
        return result
    }

    private suspend fun getAllContents(
        book: Book,
        source: ContentSource,
        translationProvider: String = TranslationConfig.llmProvider,
        targetLanguage: String = TranslationConfig.llmTargetLanguage,
        append: (text: String, srcList: ArrayList<SrcData>?) -> Unit
    ) = coroutineScope {
        val useReplace = AppConfig.exportUseReplace && book.getUseReplaceRule()
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val qy = "${book.name}\n${
            getString(R.string.author_show, book.getRealAuthor())
        }\n${
            getString(
                R.string.intro_show,
                "\n" + HtmlFormatter.format(book.getDisplayIntro())
            )
        }"
        append(qy, null)
        val threads = if (AppConfig.parallelExportBook) {
            AppConst.MAX_THREAD
        } else {
            1
        }
        flow {
            appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
                emit(chapter)
            }
        }.mapAsync(threads) { chapter ->
            getExportData(
                book,
                chapter,
                contentProcessor,
                useReplace,
                source,
                translationProvider,
                targetLanguage,
                strictTranslation = false,
            )
        }.collectIndexed { index, result ->
            notifyExportBookChanged(book.bookUrl)
            exportProgress[book.bookUrl] = index
            append.invoke(result.first, result.second)
        }

    }

    private fun notifyExportBookChanged(bookUrl: String) {
        postEvent(EventBus.EXPORT_BOOK, bookUrl)
        _exportBookUpdateFlow.tryEmit(bookUrl)
    }

    private suspend fun getExportData(
        book: Book,
        chapter: BookChapter,
        contentProcessor: ContentProcessor,
        useReplace: Boolean,
        source: ContentSource,
        translationProvider: String,
        targetLanguage: String,
        strictTranslation: Boolean,
    ): Pair<String, ArrayList<SrcData>?> {
        val content = when (source) {
            ContentSource.Original -> BookHelp.getContent(book, chapter)
            ContentSource.Translation -> readTranslatedContent(
                book,
                chapter,
                targetLanguage,
                translationProvider,
                strictTranslation,
            )
        }
        val processedContent = contentProcessor
            .getContent(
                book,
                // 不导出vip标识
                chapter.apply { isVip = false },
                content ?: if (chapter.isVolume) "" else "null",
                includeTitle = !AppConfig.exportNoChapterName,
                useReplace = useReplace,
                chineseConvert = false,
                reSegment = false
            ).toString()
        if (AppConfig.exportPictureFile && source == ContentSource.Original) {
            //txt导出图片文件 - only for original source
            val srcList = arrayListOf<SrcData>()
            content?.split("\n")?.forEachIndexed { index, text ->
                val matcher = AppPattern.imgPattern.matcher(text)
                while (matcher.find()) {
                    matcher.group(1)?.let {
                        val src = NetworkUtils.getAbsoluteURL(chapter.url, it)
                        srcList.add(SrcData(chapter.title, index, src))
                    }
                }
            }
            return Pair("\n\n$processedContent", srcList)
        } else {
            return Pair("\n\n$processedContent", null)
        }
    }

    /**
     * 导出Epub
     */
    private suspend fun exportEpub(
        path: String,
        book: Book,
        source: ContentSource,
        translationProvider: String,
        targetLanguage: String,
        strictTranslation: Boolean = false,
    ) {
        exportMsg.remove(book.bookUrl)
        postEvent(EventBus.EXPORT_BOOK, book.bookUrl)
        val fileDoc = FileDoc.fromDir(path)
        exportEpub(
            fileDoc,
            book,
            source,
            translationProvider,
            targetLanguage,
            strictTranslation,
        )
    }

    private suspend fun exportEpub(
        fileDoc: FileDoc,
        book: Book,
        source: ContentSource,
        translationProvider: String,
        targetLanguage: String,
        strictTranslation: Boolean = false,
    ) {
        val filename = when (source) {
            ContentSource.Original -> book.getExportFileName("epub")
            ContentSource.Translation -> getTranslatedFileName(book.getExportFileName("epub"), targetLanguage)
        }
        fileDoc.find(filename)?.delete()

        val epubBook = EpubBook()
        epubBook.version = "2.0"
        //set metadata
        setEpubMetadata(
            book,
            epubBook,
            if (source == ContentSource.Translation) targetLanguage else "zh",
        )
        //set cover
        setCover(book, epubBook)
        //set css
        val contentModel = setAssets(fileDoc, book, epubBook)

        //设置正文
        setEpubContent(
            contentModel,
            book,
            epubBook,
            source,
            translationProvider,
            targetLanguage,
            strictTranslation,
        )

        val bookDoc = fileDoc.createFileIfNotExist(filename)
        bookDoc.openOutputStream().getOrThrow().buffered().use { bookOs ->
            EpubWriter().write(epubBook, bookOs)
        }
        lastExportedFile = ExportedFile(
            uri = bookDoc.uri.toString(),
            fileName = bookDoc.name,
            mimeType = mimeTypeFor(EbookExportFormat.EPUB2),
        )
        lastExportedFiles = listOfNotNull(lastExportedFile)

        if (AppConfig.exportToWebDav) {
            // 导出到webdav
            AppWebDav.exportWebDav(bookDoc.uri, filename)
        }
    }

    private fun setAssets(doc: FileDoc, book: Book, epubBook: EpubBook): String {
        val customPath = doc.find("Asset")
        val contentModel = if (customPath == null) {//使用内置模板
            setAssets(book, epubBook)
        } else {//外部模板
            setAssetsExternal(customPath, book, epubBook)
        }

        return contentModel
    }

    private fun setAssetsExternal(doc: FileDoc, book: Book, epubBook: EpubBook): String {
        var contentModel = ""
        doc.list()!!.forEach { folder ->
            if (folder.isDir && folder.name == "Text") {
                folder.list()!!.sortedWith { o1, o2 ->
                    o1.name.cnCompare(o2.name)
                }.forEach loop@{ file ->
                    if (file.isDir) {
                        return@loop
                    }
                    when {
                        //正文模板
                        file.name.equals("chapter.html", true)
                                || file.name.equals("chapter.xhtml", true) -> {
                            contentModel = file.readText()
                        }
                        //封面等其他模板
                        file.name.endsWith("html", true) -> {
                            epubBook.addSection(
                                FileUtils.getNameExcludeExtension(file.name),
                                ResourceUtil.createPublicResource(
                                    book.name,
                                    book.getRealAuthor(),
                                    book.getDisplayIntro(),
                                    book.kind,
                                    book.wordCount,
                                    file.readText(),
                                    "${folder.name}/${file.name}"
                                )
                            )
                        }
                        //其他格式文件当做资源文件
                        else -> {
                            epubBook.resources.add(
                                Resource(
                                    file.readBytes(),
                                    "${folder.name}/${file.name}"
                                )
                            )
                        }
                    }
                }
            } else if (folder.isDir) {
                //资源文件
                folder.list()!!.forEach loop2@{
                    if (it.isDir) {
                        return@loop2
                    }
                    epubBook.resources.add(
                        Resource(
                            it.readBytes(),
                            "${folder.name}/${it.name}"
                        )
                    )
                }
            } else {//Asset下面的资源文件
                epubBook.resources.add(
                    Resource(
                        folder.readBytes(),
                        folder.name
                    )
                )
            }
        }
        return contentModel
    }

    private fun setAssets(book: Book, epubBook: EpubBook): String {
        epubBook.resources.add(
            Resource(
                appCtx.assets.open("epub/fonts.css").readBytes(),
                "Styles/fonts.css"
            )
        )
        epubBook.resources.add(
            Resource(
                appCtx.assets.open("epub/main.css").readBytes(),
                "Styles/main.css"
            )
        )
        epubBook.resources.add(
            Resource(
                appCtx.assets.open("epub/logo.png").readBytes(),
                "Images/logo.png"
            )
        )
        epubBook.addSection(
            getString(R.string.img_cover),
            ResourceUtil.createPublicResource(
                book.name,
                book.getRealAuthor(),
                book.getDisplayIntro(),
                book.kind,
                book.wordCount,
                String(appCtx.assets.open("epub/cover.html").readBytes()),
                "Text/cover.html"
            )
        )
        epubBook.addSection(
            getString(R.string.book_intro),
            ResourceUtil.createPublicResource(
                book.name,
                book.getRealAuthor(),
                book.getDisplayIntro(),
                book.kind,
                book.wordCount,
                String(appCtx.assets.open("epub/intro.html").readBytes(), Charsets.UTF_8)
                    .replace("{language}", epubBook.metadata.language.ifBlank { "zh" })
                    .replace("{introTitle}", getString(R.string.book_intro)),
                "Text/intro.html"
            )
        )
        return String(appCtx.assets.open("epub/chapter.html").readBytes())
    }

    private fun setCover(book: Book, epubBook: EpubBook) {
        kotlin.runCatching {
            val file = resolveExportCover(book) ?: error("Không tìm thấy ảnh bìa")
            val provider = LazyResourceProvider { _ ->
                file.inputStream()
            }
            epubBook.coverImage = LazyResource(provider, "Images/cover.jpg")
        }.onFailure {
            AppLog.put(
                getString(R.string.export_cover_failed, it.localizedMessage.orEmpty()),
                it,
            )
        }
    }

    /** Resolve a real cover file for every exporter, falling back to the bundled cover. */
    private fun resolveExportCover(book: Book): File? {
        val displayCover = book.getDisplayCover().orEmpty().trim()
        val localPath = displayCover.removePrefix("file://")
        File(localPath).takeIf { it.isFile && it.length() > 0L }?.let { return it }

        val downloaded = runCatching {
            Glide.with(this).asFile().load(displayCover).submit().get()
        }.getOrNull()
        if (downloaded?.isFile == true && downloaded.length() > 0L) return downloaded

        val fallbackDir = File(cacheDir, "export-covers").apply { mkdirs() }
        val fallback = File(fallbackDir, "default-cover.jpg")
        if (!fallback.isFile || fallback.length() == 0L) {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.image_cover_default)
                ?: return null
            FileOutputStream(fallback).use { output ->
                if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, output)) {
                    fallback.delete()
                    return null
                }
            }
            bitmap.recycle()
        }
        return fallback.takeIf { it.isFile && it.length() > 0L }
    }

    private suspend fun setEpubContent(
        contentModel: String,
        book: Book,
        epubBook: EpubBook,
        source: ContentSource = ContentSource.Original,
        translationProvider: String = TranslationConfig.llmProvider,
        targetLanguage: String = TranslationConfig.llmTargetLanguage,
        strictTranslation: Boolean = false,
    ) = coroutineScope {
        //正文
        val useReplace = AppConfig.exportUseReplace && book.getUseReplaceRule()
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val threads = if (AppConfig.parallelExportBook) {
            AppConst.MAX_THREAD
        } else {
            1
        }
        var parentSection: TOCReference? = null
        flow {
            appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
                emit(chapter)
            }
        }.mapAsyncIndexed(threads) { index, chapter ->
            val content = when (source) {
                ContentSource.Original -> BookHelp.getContent(book, chapter)
                ContentSource.Translation -> readTranslatedContent(
                    book,
                    chapter,
                    targetLanguage,
                    translationProvider,
                    strictTranslation,
                )
            }
            // For translation source, don't extract images (skip fixPic)
            val (contentFix, resources) = if (source == ContentSource.Translation) {
                Pair(content ?: if (chapter.isVolume) "" else "null", arrayListOf())
            } else {
                fixPic(
                    book,
                    content ?: if (chapter.isVolume) "" else "null",
                    chapter
                )
            }
            // 不导出vip标识
            chapter.isVip = false
            val content1 = contentProcessor
                .getContent(
                    book,
                    chapter,
                    contentFix,
                    includeTitle = false,
                    useReplace = useReplace,
                    chineseConvert = false,
                    reSegment = false
                ).toString()
            val title = chapter.run {
                // 不导出vip标识
                isVip = false
                getDisplayTitle(
                    contentProcessor.getTitleReplaceRules(),
                    useReplace = useReplace
                )
            }
            val chapterResource = ResourceUtil.createChapterResource(
                title.replace("\uD83D\uDD12", ""),
                content1,
                contentModel,
                "Text/chapter_${index}.html"
            )
            ExportChapter(title, chapterResource, resources, chapter)
        }.collectIndexed { index, exportChapter ->
            postEvent(EventBus.EXPORT_BOOK, book.bookUrl)
            exportProgress[book.bookUrl] = index
            val (title, chapterResource, resources, chapter) = exportChapter
            epubBook.resources.addAll(resources)
            if (chapter.isVolume) {
                parentSection = epubBook.addSection(title, chapterResource)
            } else if (parentSection == null) {
                epubBook.addSection(title, chapterResource)
            } else {
                epubBook.addSection(parentSection, title, chapterResource)
            }
        }
    }

    data class ExportChapter(
        val title: String,
        val chapterResource: Resource,
        val resources: ArrayList<Resource>,
        val chapter: BookChapter
    )

    private fun fixPic(
        book: Book,
        content: String,
        chapter: BookChapter
    ): Pair<String, ArrayList<Resource>> {
        val data = StringBuilder("")
        val resources = arrayListOf<Resource>()
        content.split("\n").forEach { text ->
            var text1 = text
            val matcher = AppPattern.imgPattern.matcher(text)
            while (matcher.find()) {
                matcher.group(1)?.let {
                    val src = NetworkUtils.getAbsoluteURL(chapter.url, it)
                    val originalHref =
                        "${MD5Utils.md5Encode16(src)}.${BookHelp.getImageSuffix(src)}"
                    val href =
                        "Images/${MD5Utils.md5Encode16(src)}.${BookHelp.getImageSuffix(src)}"
                    val vFile = BookHelp.getImage(book, src)
                    val fp = FileResourceProvider(vFile.parent)
                    if (vFile.exists()) {
                        val img = LazyResource(fp, href, originalHref)
                        resources.add(img)
                    }
                    text1 = text1.replace(src, "../${href}")
                }
            }
            data.append(text1).append("\n")
        }
        return data.toString() to resources
    }

    private fun setEpubMetadata(
        book: Book,
        epubBook: EpubBook,
        language: String = "zh",
    ) {
        val metadata = Metadata()
        metadata.titles.add(book.name)//书籍的名称
        metadata.authors.add(Author(book.getRealAuthor()))//书籍的作者
        metadata.language = language
        metadata.dates.add(Date())//数据的创建日期
        metadata.publishers.add("Legado")//数据的创建者
        metadata.descriptions.add(book.getDisplayIntro())//书籍的简介
        //metadata.subjects.add("")//书籍的主题，在静读天下里面有使用这个分类书籍
        epubBook.metadata = metadata
    }

    //////end of EPUB

    //////start of custom exporter
    /**
     * 自定义Exporter
     * @param scope 导出范围
     * @param size epub 文件包含最大章节数
     */
    inner class CustomExporter(scopeStr: String, private val size: Int) {

        private var scope = parseScope(scopeStr)

        /**
         * 导出Epub
         * @param path 导出的路径
         * @param book 书籍
         */
        suspend fun export(
            path: String,
            book: Book
        ) {
            exportProgress[book.bookUrl] = 0
            exportMsg.remove(book.bookUrl)
            postEvent(EventBus.EXPORT_BOOK, book.bookUrl)
            val currentTimeMillis = System.currentTimeMillis()
            val count = appDb.bookChapterDao.getChapterCount(book.bookUrl)
            scope = scope.filter { it < count }.toHashSet()

            val fileDoc = FileDoc.fromDir(path)

            val (contentModel, epubList) = createEpubs(book, fileDoc)
            var progressBar = 0.0
            epubList.forEachIndexed { index, ep ->
                val (filename, epubBook) = ep
                //设置正文
                setEpubContent(
                    contentModel,
                    book,
                    epubBook,
                    index
                ) { _, _ ->
                    // 将章节写入内存时更新进度条
                    postEvent(EventBus.EXPORT_BOOK, book.bookUrl)
                    progressBar += book.totalChapterNum.toDouble() / scope.size / 2
                    exportProgress[book.bookUrl] = progressBar.toInt()
                }
                save2Drive(filename, epubBook, fileDoc) { total, _ ->
                    //写入硬盘时更新进度条
                    progressBar += book.totalChapterNum.toDouble() / epubList.size / total / 2
                    postEvent(EventBus.EXPORT_BOOK, book.bookUrl)
                    exportProgress[book.bookUrl] = progressBar.toInt()
                }
            }

            val elapsed = System.currentTimeMillis() - currentTimeMillis
            AppLog.put(getString(R.string.export_split_elapsed, book.name, elapsed))
        }


        /**
         * 设置epub正文
         *
         * @param contentModel 正文模板
         * @param book 书籍
         * @param epubBook 分割后的epub
         * @param epubBookIndex 分割后的epub序号
         */
        private suspend fun setEpubContent(
            contentModel: String,
            book: Book,
            epubBook: EpubBook,
            epubBookIndex: Int,
            updateProgress: (chapterList: MutableList<BookChapter>, index: Int) -> Unit
        ) {
            //正文
            val useReplace = AppConfig.exportUseReplace && book.getUseReplaceRule()
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            var chapterList: MutableList<BookChapter> = ArrayList()
            appDb.bookChapterDao.getChapterList(book.bookUrl).forEachIndexed { index, chapter ->
                if (scope.contains(index)) {
                    chapterList.add(chapter)
                }
                if (scope.size == chapterList.size) {
                    return@forEachIndexed
                }
            }
            // val totalChapterNum = book.totalChapterNum / scope.size
            if (chapterList.isEmpty()) {
                throw RuntimeException(
                    getString(
                        R.string.export_missing_chapters,
                        book.name,
                        epubBookIndex + 1,
                    )
                )
            }
            chapterList = chapterList.subList(
                epubBookIndex * size,
                min(scope.size, (epubBookIndex + 1) * size)
            )
            chapterList.forEachIndexed { index, chapter ->
                coroutineContext.ensureActive()
                updateProgress(chapterList, index)
                BookHelp.getContent(book, chapter).let { content ->
                    val (contentFix, resources) = fixPic(
                        book,
                        content ?: if (chapter.isVolume) "" else "null",
                        chapter
                    )
                    epubBook.resources.addAll(resources)
                    val content1 = contentProcessor
                        .getContent(
                            book,
                            chapter,
                            contentFix,
                            includeTitle = false,
                            useReplace = useReplace,
                            chineseConvert = false,
                            reSegment = false
                        ).toString()
                    val title = chapter.run {
                        // 不导出vip标识
                        isVip = false
                        getDisplayTitle(
                            contentProcessor.getTitleReplaceRules(),
                            useReplace = useReplace
                        )
                    }
                    epubBook.addSection(
                        title,
                        ResourceUtil.createChapterResource(
                            title.replace("\uD83D\uDD12", ""),
                            content1,
                            contentModel,
                            "Text/chapter_${index}.html"
                        )
                    )
                }
            }
        }

        /**
         * 创建多个epub 对象
         *
         * 分割epub时，一个书籍需要创建多个epub对象
         * @param book 书籍
         * @param fileDoc 导出文件夹文档
         *
         * @return <内容模板字符串, <epub文件名, epub对象>>
         */
        private fun createEpubs(
            book: Book,
            fileDoc: FileDoc
        ): Pair<String, List<Pair<String, EpubBook>>> {
            val paresNumOfEpub = paresNumOfEpub(scope.size, size)
            val result: MutableList<Pair<String, EpubBook>> = ArrayList(paresNumOfEpub)
            var contentModel = ""
            for (i in 1..paresNumOfEpub) {
                val filename = book.getExportFileName("epub", i)
                fileDoc.find(filename)?.delete()

                val epubBook = EpubBook()
                epubBook.version = "2.0"
                //set metadata
                setEpubMetadata(book, epubBook)
                //set cover
                setCover(book, epubBook)
                //set css
                contentModel = setAssets(fileDoc, book, epubBook)

                // add epubBook
                result.add(Pair(filename, epubBook))
            }
            return Pair(contentModel, result)
        }

        /**
         * 保存文件到 设备
         */
        private suspend fun save2Drive(
            filename: String,
            epubBook: EpubBook,
            fileDoc: FileDoc,
            callback: (total: Int, progress: Int) -> Unit
        ) {
            val bookDoc = fileDoc.createFileIfNotExist(filename)
            bookDoc.openOutputStream().getOrThrow().buffered().use { bookOs ->
                EpubWriter()
                    .setCallback(object : EpubWriterProcessor.Callback {
                        override fun onProgressing(total: Int, progress: Int) {
                            callback(total, progress)
                        }
                    })
                    .write(epubBook, bookOs)
            }

            if (AppConfig.exportToWebDav) {
                // 导出到webdav
                AppWebDav.exportWebDav(bookDoc.uri, filename)
            }
        }

        /**
         * 解析 分割epub后的数量
         *
         * @param total 章节总数
         * @param size 每个epub文件包含多少章节
         */
        private fun paresNumOfEpub(total: Int, size: Int): Int {
            val i = total % size
            var result = total / size
            if (i > 0) {
                result++
            }
            return result
        }

        /**
         * 解析范围字符串
         *
         * @param scope 范围字符串
         * @return 范围
         *
         * @since 2023/5/22
         * @author Discut
         */
        private fun parseScope(scope: String): Set<Int> {
            val split = scope.split(",")

            val result = linkedSetOf<Int>()
            for (s in split) {
                val v = s.split("-")
                if (v.size != 2) {
                    result.add(s.toInt() - 1)
                    continue
                }
                val left = v[0].toInt()
                val right = v[1].toInt()
                if (left > right) {
                    AppLog.put("Error expression : $s; left > right")
                    continue
                }
                for (i in left..right)
                    result.add(i - 1)
            }
            return result
        }
    }
}
