package io.legado.app.web

import io.legado.app.api.controller.BookController
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.webservice.WebServiceTranslationJobListResponse
import io.legado.app.domain.webservice.WebServiceTranslationJobRequest
import io.legado.app.domain.webservice.WebServicePretranslateRequest
import io.legado.app.domain.webservice.WebServicePretranslateResponse
import io.legado.app.domain.webservice.WebServiceTranslationJobResponse
import io.legado.app.domain.webservice.WebServiceTranslationContentResponse
import io.legado.app.domain.webservice.WebServiceTranslationProviderListResponse
import io.legado.app.domain.webservice.buildWebServiceTranslationProviderList
import io.legado.app.domain.webservice.WebServiceTranslationJobs
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.model.translation.TranslationChapterKey
import io.legado.app.model.translation.TranslationChapterState
import io.legado.app.model.translation.TranslationChapterStatus
import io.legado.app.model.translation.TranslationManager
import io.legado.app.ui.config.translation.TranslationConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object WebServiceTranslationJobController {

    private val jobsById = ConcurrentHashMap<String, JobRecord>()
    private val jobIdsByKey = ConcurrentHashMap<TranslationChapterKey, String>()

    suspend fun getCachedContent(
        bookUrlValue: String?,
        chapterIndexValue: String?,
        providerValue: String?,
        targetLanguageValue: String?,
    ): WebServiceTranslationContentResponse {
        val bookUrl = WebServiceTranslationJobs.normalizedOptionalText(bookUrlValue)
            ?: throw IllegalArgumentException("BOOK_URL_REQUIRED")
        val chapterIndex = WebServiceTranslationJobs.normalizedChapterIndex(chapterIndexValue)
            ?: throw IllegalArgumentException("CHAPTER_INDEX_REQUIRED")
        val provider = WebServiceTranslationJobs.normalizedOptionalText(providerValue)
        val targetLanguage = WebServiceTranslationJobs.normalizedOptionalText(targetLanguageValue)
            ?: TranslationConfig.llmTargetLanguage
        if (provider != null) {
            require(provider in TranslationConstants.providerValues) { "PROVIDER_UNSUPPORTED" }
            require(TranslationConstants.supportsTargetLanguage(provider, targetLanguage)) {
                "TARGET_LANGUAGE_UNSUPPORTED"
            }
        }
        val book = appDb.bookDao.getBook(bookUrl)
            ?: throw IllegalArgumentException("BOOK_NOT_FOUND")
        val chapter = findChapter(bookUrl, chapterIndex)
            ?: throw IllegalArgumentException("CHAPTER_NOT_FOUND")
        if (io.legado.app.help.book.BookHelp.getContent(book, chapter) == null) {
            ensureChapterContent(bookUrl, chapterIndex)
        }
        val resolved = if (provider == null) {
            TranslationManager.getPreferredCachedTranslation(
                book = book,
                chapter = chapter,
                targetLanguage = targetLanguage,
            )
        } else {
            val content = TranslationManager.getCachedTranslation(
                book = book,
                chapter = chapter,
                provider = provider,
                targetLanguage = targetLanguage,
            )
            content?.let {
                TranslationManager.ResolvedTranslationContent(
                    content = it,
                    provider = provider,
                    targetLanguage = targetLanguage,
                    revision = TranslationManager.getCurrentRevision(
                        book = book,
                        chapter = chapter,
                        provider = provider,
                        targetLanguage = targetLanguage,
                    ),
                )
            }
        }
        return WebServiceTranslationContentResponse(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            content = resolved?.content,
            provider = resolved?.provider,
            targetLanguage = resolved?.targetLanguage ?: targetLanguage,
            updatedAt = resolved?.revision?.updatedAt ?: 0L,
        )
    }

    fun providers(): WebServiceTranslationProviderListResponse =
        buildWebServiceTranslationProviderList(
            providerValues = TranslationConstants.providerValues,
            providerDisplayNames = TranslationConstants.providerDisplayNames,
            defaultProvider = TranslationConfig.llmProvider,
            defaultTargetLanguage = TranslationConfig.llmTargetLanguage,
            targetLanguagesForProvider = { provider ->
                TranslationConstants.targetLanguagesForProvider(provider).map { it.first }
            },
        )

    suspend fun create(request: WebServiceTranslationJobRequest): WebServiceTranslationJobResponse {
        pruneFinishedJobs()
        val bookUrl = WebServiceTranslationJobs.normalizedOptionalText(request.bookUrl)
            ?: throw IllegalArgumentException("BOOK_URL_REQUIRED")
        val chapterIndex = request.chapterIndex
            ?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("CHAPTER_INDEX_REQUIRED")
        val provider = WebServiceTranslationJobs.normalizedOptionalText(request.provider)
            ?: TranslationConfig.llmProvider
        val targetLanguage = WebServiceTranslationJobs.normalizedOptionalText(request.targetLanguage)
            ?: TranslationConfig.llmTargetLanguage
        require(provider in TranslationConstants.providerValues) { "PROVIDER_UNSUPPORTED" }
        require(TranslationConstants.supportsTargetLanguage(provider, targetLanguage)) {
            "TARGET_LANGUAGE_UNSUPPORTED"
        }
        val key = TranslationChapterKey(bookUrl, chapterIndex, provider, targetLanguage)

        if (!request.forceRetranslate) {
            jobIdsByKey[key]?.let { jobId ->
                jobsById[jobId]?.let { record ->
                    responseFor(record).takeIf { it.isReusable }?.let { return it }
                }
            }
        }

        val book = appDb.bookDao.getBook(bookUrl)
            ?: throw IllegalArgumentException("BOOK_NOT_FOUND")
        val chapter = findChapter(bookUrl, chapterIndex)
            ?: throw IllegalArgumentException("CHAPTER_NOT_FOUND")
        val flow = TranslationManager.startTranslation(
            book = book,
            chapter = chapter,
            forceRetranslate = request.forceRetranslate,
            provider = provider,
            targetLanguage = targetLanguage,
        ) ?: run {
            ensureChapterContent(bookUrl, chapterIndex)
            TranslationManager.startTranslation(
                book = book,
                chapter = chapter,
                forceRetranslate = request.forceRetranslate,
                provider = provider,
                targetLanguage = targetLanguage,
            )
        } ?: throw IllegalArgumentException("CHAPTER_CONTENT_EMPTY")

        val record = JobRecord(
            jobId = UUID.randomUUID().toString(),
            key = key,
            createdAt = System.currentTimeMillis(),
        )
        jobsById[record.jobId] = record
        jobIdsByKey[key] = record.jobId
        return responseFor(record, flow.value)
    }

    suspend fun pretranslate(request: WebServicePretranslateRequest): WebServicePretranslateResponse {
        val bookUrl = WebServiceTranslationJobs.normalizedOptionalText(request.bookUrl)
            ?: throw IllegalArgumentException("BOOK_URL_REQUIRED")
        require(request.fromChapter >= 0) { "FROM_CHAPTER_INVALID" }
        val count = request.count.coerceIn(1, 50)
        val jobs = (request.fromChapter until request.fromChapter + count).map { chapterIndex ->
            create(
                WebServiceTranslationJobRequest(
                    bookUrl = bookUrl,
                    chapterIndex = chapterIndex,
                    forceRetranslate = request.forceRetranslate,
                    provider = request.provider,
                    targetLanguage = request.targetLanguage,
                )
            )
        }
        return WebServicePretranslateResponse(bookUrl, jobs)
    }

    fun get(jobId: String?): WebServiceTranslationJobResponse {
        val record = jobRecord(jobId)
        return responseFor(record)
    }

    fun list(): WebServiceTranslationJobListResponse =
        WebServiceTranslationJobListResponse(
            jobs = jobsById.values
                .sortedByDescending(JobRecord::createdAt)
                .map(::responseFor),
        )

    private fun pruneFinishedJobs() {
        val overflow = jobsById.size - MAX_RETAINED_JOBS + 1
        if (overflow <= 0) return
        jobsById.values
            .asSequence()
            .sortedBy(JobRecord::createdAt)
            .filter { record ->
                responseFor(record).status !in setOf(
                    WebServiceTranslationJobs.STATUS_IDLE,
                    WebServiceTranslationJobs.STATUS_TRANSLATING,
                )
            }
            .take(overflow)
            .forEach { record ->
                jobsById.remove(record.jobId, record)
                if (jobIdsByKey.remove(record.key, record.jobId)) {
                    TranslationManager.clearChapterState(
                        bookUrl = record.key.bookUrl,
                        chapterIndex = record.key.chapterIndex,
                        provider = record.key.provider,
                        targetLanguage = record.key.targetLanguage,
                    )
                }
            }
    }

    fun cancel(jobId: String?): WebServiceTranslationJobResponse {
        val record = jobRecord(jobId)
        TranslationManager.cancelTranslation(
            bookUrl = record.key.bookUrl,
            chapterIndex = record.key.chapterIndex,
            provider = record.key.provider,
            targetLanguage = record.key.targetLanguage,
        )
        return responseFor(record)
    }

    fun cancelAll(): Int {
        var cancelled = 0
        jobsById.values.forEach { record ->
            if (TranslationManager.cancelTranslation(
                    bookUrl = record.key.bookUrl,
                    chapterIndex = record.key.chapterIndex,
                    provider = record.key.provider,
                    targetLanguage = record.key.targetLanguage,
                )
            ) {
                cancelled += 1
            }
        }
        return cancelled
    }

    private fun jobRecord(jobId: String?): JobRecord {
        val normalized = WebServiceTranslationJobs.normalizedOptionalText(jobId)
            ?: throw NoSuchElementException("JOB_NOT_FOUND")
        return jobsById[normalized]
            ?: throw NoSuchElementException("JOB_NOT_FOUND")
    }

    private fun responseFor(record: JobRecord): WebServiceTranslationJobResponse =
        responseFor(record, stateFor(record))

    private fun responseFor(
        record: JobRecord,
        state: TranslationChapterState?,
    ): WebServiceTranslationJobResponse {
        val status = state?.status?.toWebStatus() ?: WebServiceTranslationJobs.STATUS_IDLE
        return WebServiceTranslationJobResponse(
            jobId = record.jobId,
            bookUrl = record.key.bookUrl,
            chapterIndex = record.key.chapterIndex,
            provider = record.key.provider,
            targetLanguage = record.key.targetLanguage,
            status = status,
            currentChunk = state?.currentChunk ?: 0,
            totalChunks = state?.totalChunks ?: 0,
            progress = WebServiceTranslationJobs.progress(
                currentChunk = state?.currentChunk ?: 0,
                totalChunks = state?.totalChunks ?: 0,
            ),
            content = state?.translatedContent
                ?.takeIf { status == WebServiceTranslationJobs.STATUS_TRANSLATED },
            preview = state?.mixedContent
                ?.takeIf { status == WebServiceTranslationJobs.STATUS_TRANSLATING },
            error = state?.errorMessage ?: state?.failure?.userMessage,
            updatedAt = state?.updatedAt ?: record.createdAt,
        )
    }

    private fun stateFor(record: JobRecord): TranslationChapterState? =
        TranslationManager.getChapterStateFlow(
            bookUrl = record.key.bookUrl,
            chapterIndex = record.key.chapterIndex,
            provider = record.key.provider,
            targetLanguage = record.key.targetLanguage,
        )?.value

    private suspend fun findChapter(
        bookUrl: String,
        chapterIndex: Int,
    ): BookChapter? {
        appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)?.let { return it }
        BookController.getChapterListAwait(mapOf("url" to listOf(bookUrl)))
        return appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
    }

    private suspend fun ensureChapterContent(
        bookUrl: String,
        chapterIndex: Int,
    ) {
        val result = BookController.getBookContentAwait(
            mapOf(
                "url" to listOf(bookUrl),
                "index" to listOf(chapterIndex.toString()),
            )
        )
        if (!result.isSuccess) {
            throw IllegalArgumentException(result.errorMsg)
        }
    }

    private fun TranslationChapterStatus.toWebStatus(): String =
        when (this) {
            TranslationChapterStatus.Idle -> WebServiceTranslationJobs.STATUS_IDLE
            TranslationChapterStatus.Translating -> WebServiceTranslationJobs.STATUS_TRANSLATING
            TranslationChapterStatus.Translated -> WebServiceTranslationJobs.STATUS_TRANSLATED
            TranslationChapterStatus.Failed -> WebServiceTranslationJobs.STATUS_FAILED
            TranslationChapterStatus.Cancelled -> WebServiceTranslationJobs.STATUS_CANCELLED
        }

    private val WebServiceTranslationJobResponse.isReusable: Boolean
        get() = status == WebServiceTranslationJobs.STATUS_IDLE ||
            status == WebServiceTranslationJobs.STATUS_TRANSLATING ||
            status == WebServiceTranslationJobs.STATUS_TRANSLATED

    private data class JobRecord(
        val jobId: String,
        val key: TranslationChapterKey,
        val createdAt: Long,
    )

    private const val MAX_RETAINED_JOBS = 64
}
