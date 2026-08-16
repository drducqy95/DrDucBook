package io.legado.app.model.translation

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.AiFailureKind
import io.legado.app.domain.model.AiProviderException
import io.legado.app.domain.model.AiProviderFailure
import io.legado.app.domain.model.RevisionStatus
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.TranslationRevision
import io.legado.app.domain.model.dictionaryAwareContentHash
import io.legado.app.domain.model.protectsMachineTranslation
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap

object TranslationManager : KoinComponent {

    private val translationCacheGateway: TranslationCacheGateway by inject()
    private val translateChapterUseCase: TranslateChapterUseCase by inject()
    private val quickDictionaryGateway: QuickDictionaryGateway by inject()
    private val quickTranslationGateway: QuickTranslationGateway by inject()

    /** Per-translation task state flows, isolated by chapter, provider, and target language. */
    private val _taskStateFlows =
        ConcurrentHashMap<TranslationChapterKey, MutableStateFlow<TranslationChapterState>>()
    private val taskJobs =
        ConcurrentHashMap<TranslationChapterKey, Coroutine<Unit>>()

    data class ResolvedTranslationContent(
        val content: String,
        val provider: String,
        val targetLanguage: String,
        val revision: TranslationRevision? = null,
    )

    private fun getChapterKey(
        book: Book,
        chapter: BookChapter,
        provider: String,
        targetLanguage: String,
    ): TranslationChapterKey {
        return TranslationChapterKey(book.bookUrl, chapter.index, provider, targetLanguage)
    }

    /**
     * Get task StateFlow for a chapter if translation is in progress.
     * Returns null if no in-progress translation exists.
     */
    fun getChapterTaskStateFlow(
        bookUrl: String,
        chapterIndex: Int,
        provider: String = TranslationConfig.llmProvider,
        targetLanguage: String = currentTargetLanguage(),
    ): StateFlow<TranslationChapterState>? {
        val key = TranslationChapterKey(bookUrl, chapterIndex, provider, targetLanguage)
        return _taskStateFlows[key]?.takeIf { it.value.status == TranslationChapterStatus.Translating }
    }

    fun getChapterStateFlow(
        bookUrl: String,
        chapterIndex: Int,
        provider: String = TranslationConfig.llmProvider,
        targetLanguage: String = currentTargetLanguage(),
    ): StateFlow<TranslationChapterState>? {
        return _taskStateFlows[
            TranslationChapterKey(bookUrl, chapterIndex, provider, targetLanguage)
        ]
    }

    /**
     * Check if translated cache file exists for a chapter.
     */
    suspend fun hasTranslatedCache(
        book: Book,
        chapter: BookChapter,
        provider: String = TranslationConfig.llmProvider,
        targetLanguage: String = currentTargetLanguage(),
    ): Boolean {
        if (provider == TranslationConfig.llmProvider) {
            return getPreferredCachedTranslation(
                book = book,
                chapter = chapter,
                targetLanguage = targetLanguage,
            ) != null
        }
        return getCachedTranslation(book, chapter, provider, targetLanguage) != null
    }

    /**
     * Get finished cached translation for a chapter.
     */
    suspend fun getCachedTranslation(
        book: Book,
        chapter: BookChapter,
        provider: String = TranslationConfig.llmProvider,
        targetLanguage: String = currentTargetLanguage(),
    ): String? {
        val originalContent = BookHelp.getContent(book, chapter) ?: return null
        val rawContentHash = translationCacheGateway.computeContentHash(originalContent)
        getCurrentRevision(
            book = book,
            chapter = chapter,
            provider = provider,
            targetLanguage = targetLanguage,
            rawContentHash = rawContentHash,
        )?.takeIf { it.protectsMachineTranslation }
            ?.let { return it.content }
        val dictionaryRevision = quickDictionaryGateway.getEffectiveRevision(book, originalContent)
        val dictionaryContentHash = dictionaryAwareContentHash(
            originalContentHash = rawContentHash,
            provider = provider,
            dictionaryRevision = dictionaryRevision,
            quickTranslationPackVersion = quickTranslationGateway.packVersionFor(
                book.getQuickTranslationPronounModeOverride(),
            ),
        )
        val contentHash = io.legado.app.domain.usecase.applyProviderConfigurationRevision(
            contentHash = dictionaryContentHash,
            providerConfigurationRevision =
                translateChapterUseCase.currentProviderConfigurationRevision(provider),
            computeHash = translationCacheGateway::computeContentHash,
        )
        return translationCacheGateway.readCurrentTranslation(
            book = book,
            bookChapter = chapter,
            targetLanguage = targetLanguage,
            originalContentHash = contentHash,
            provider = provider,
        )
    }

    suspend fun getPreferredCachedTranslation(
        book: Book,
        chapter: BookChapter,
        targetLanguage: String = currentTargetLanguage(),
    ): ResolvedTranslationContent? {
        val originalContent = BookHelp.getContent(book, chapter) ?: return null
        val rawContentHash = translationCacheGateway.computeContentHash(originalContent)
        val identities = TranslationConstants.preferredContentProviders(targetLanguage)
        val protectedRevision = identities
            .mapIndexedNotNull { index, identity ->
                getCurrentRevision(
                    book = book,
                    chapter = chapter,
                    provider = identity.provider,
                    targetLanguage = identity.targetLanguage,
                    rawContentHash = rawContentHash,
                )?.takeIf { it.protectsMachineTranslation }
                    ?.let { revision -> index to identity to revision }
            }
            .sortedWith(
                compareBy<Pair<Pair<Int, TranslationConstants.TranslationProviderIdentity>, TranslationRevision>> {
                    when (it.second.sourceStatus) {
                        RevisionStatus.FINAL -> 0
                        RevisionStatus.USER_EDITED -> 1
                        else -> 2
                    }
                }.thenBy { it.first.first }
            )
            .firstOrNull()
        if (protectedRevision != null) {
            val identity = protectedRevision.first.second
            val revision = protectedRevision.second
            return ResolvedTranslationContent(
                content = revision.content,
                provider = identity.provider,
                targetLanguage = identity.targetLanguage,
                revision = revision,
            )
        }
        identities.forEach { identity ->
            // Legacy QT payloads have no dictionary-pack version. Do not surface them after a
            // pack update; the caller should retranslate with the current dictionary instead.
            val content = getCachedTranslation(
                book = book,
                chapter = chapter,
                provider = identity.provider,
                targetLanguage = identity.targetLanguage,
            ) ?: if (identity.provider == TranslationConstants.PROVIDER_QUICK_TRANSLATOR) {
                null
            } else {
                translationCacheGateway.readTranslation(
                    book,
                    chapter,
                    identity.targetLanguage,
                    identity.provider,
                )
            }
            if (!content.isNullOrBlank()) {
                return ResolvedTranslationContent(
                    content = content,
                    provider = identity.provider,
                    targetLanguage = identity.targetLanguage,
                )
            }
        }
        return null
    }

    suspend fun getCurrentRevision(
        book: Book,
        chapter: BookChapter,
        provider: String = TranslationConfig.llmProvider,
        targetLanguage: String = currentTargetLanguage(),
        rawContentHash: String? = null,
    ): TranslationRevision? {
        val resolvedRawHash = rawContentHash ?: BookHelp.getContent(book, chapter)
            ?.let(translationCacheGateway::computeContentHash)
            ?: return null
        return translationCacheGateway.getCurrentRevision(
            book = book,
            bookChapter = chapter,
            targetLanguage = targetLanguage,
            provider = provider,
            currentRawContentHash = resolvedRawHash,
        )
    }

    /**
     * Start translation for a chapter.
     * - If translation is already in progress, return existing task flow.
     * Cache validation happens inside the use case so its scoped dictionary revision is authoritative.
     * - If original content doesn't exist, return null.
     * The returned flow updates with mixedContent during translation.
     */
    @Synchronized
    fun startTranslation(
        book: Book,
        chapter: BookChapter,
        forceRetranslate: Boolean = false,
        provider: String = TranslationConfig.llmProvider,
        targetLanguage: String = currentTargetLanguage(),
        onTranslateStarted: () -> Unit = {}
    ): MutableStateFlow<TranslationChapterState>? {
        val key = getChapterKey(book, chapter, provider, targetLanguage)

        // Check if already translating
        _taskStateFlows[key]?.let { taskFlow ->
            if (taskFlow.value.status == TranslationChapterStatus.Translating) {
                return taskFlow
            }
        }

        // Check if original content exists
        if (BookHelp.getContent(book, chapter) == null) {
            return null
        }

        // Create new task flow
        val taskFlow = MutableStateFlow(
            TranslationChapterState(
                key = key,
                status = TranslationChapterStatus.Idle,
                logs = listOf(TranslationLogEntry(TranslationLogType.TASK_CREATED)),
            )
        )
        _taskStateFlows[key] = taskFlow

        // Start translation in background
        val task = Coroutine.async {
            translateChapter(
                book = book,
                bookChapter = chapter,
                forceRetranslate = forceRetranslate,
                provider = provider,
                targetLanguage = targetLanguage,
                onTranslateStarted = onTranslateStarted,
            )
        }.onCancel {
            taskFlow.update {
                val failure = AiProviderFailure(
                    kind = AiFailureKind.CANCELLED,
                    provider = key.provider,
                    model = "",
                    attempt = 1,
                )
                it.copy(
                    status = TranslationChapterStatus.Cancelled,
                    failure = failure,
                    logs = appendLog(
                        it.logs,
                        TranslationLogEntry(TranslationLogType.CANCELLED),
                    ),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }.onFinally {
            taskJobs.remove(key)
        }
        taskJobs[key] = task

        return taskFlow
    }

    private suspend fun translateChapter(
        book: Book,
        bookChapter: BookChapter,
        forceRetranslate: Boolean,
        provider: String,
        targetLanguage: String,
        onTranslateStarted: () -> Unit
    ) = withContext(Dispatchers.IO) {
        val key = getChapterKey(book, bookChapter, provider, targetLanguage)
        val taskFlow = _taskStateFlows[key] ?: return@withContext

        taskFlow.update {
            it.copy(
                status = TranslationChapterStatus.Translating,
                logs = appendLog(
                    it.logs,
                    TranslationLogEntry(
                        type = TranslationLogType.CONFIGURATION,
                        provider = provider,
                        targetLanguage = targetLanguage,
                    ),
                ),
                updatedAt = System.currentTimeMillis(),
            )
        }

        val result = translateChapterUseCase.execute(
            book = book,
            bookChapter = bookChapter,
            forceRetranslate = forceRetranslate,
            provider = provider,
            targetLanguage = targetLanguage,
            onProgress = { progress ->
                taskFlow.update {
                    val progressChanged = progress.currentChunk != it.currentChunk ||
                        progress.totalChunks != it.totalChunks
                    val stageLog = progress.stage?.let { stage ->
                        val type = when {
                            stage.startsWith("MEMORY_COMMITTED") -> TranslationLogType.MEMORY_COMMITTED
                            stage.startsWith("MEMORY_PENDING") -> TranslationLogType.MEMORY_WARNING
                            else -> TranslationLogType.PIPELINE_STAGE
                        }
                        TranslationLogEntry(type = type, detail = stage)
                    }
                    it.copy(
                        currentChunk = progress.currentChunk,
                        totalChunks = progress.totalChunks,
                        mixedContent = progress.mixedContent,
                        logs = buildList {
                            addAll(it.logs)
                            if (progressChanged) {
                                add(
                                    TranslationLogEntry(
                                        type = TranslationLogType.CHUNK_COMPLETED,
                                        currentChunk = progress.currentChunk,
                                        totalChunks = progress.totalChunks,
                                    )
                                )
                            }
                            stageLog?.let(::add)
                        },
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            },
            onTranslateStarted = onTranslateStarted
        )

        result.onSuccess { content ->
            taskFlow.update {
                it.copy(
                    status = TranslationChapterStatus.Translated,
                    translatedContent = content,
                    mixedContent = null,
                    errorMessage = null,
                    failure = null,
                    logs = appendLog(
                        it.logs,
                        TranslationLogEntry(TranslationLogType.COMPLETED),
                    ),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }.onFailure { error ->
            taskFlow.update {
                val failure = (error as? AiProviderException)?.failure
                val errorMessage = failure?.userMessage
                    ?: error.message?.takeIf(String::isNotBlank)
                    ?: "Không thể dịch bằng $provider (${error::class.java.simpleName})"
                it.failPreservingProgress(
                    errorMessage = errorMessage,
                    failure = failure,
                    logs = appendLog(
                        it.logs,
                        TranslationLogEntry(
                            type = TranslationLogType.FAILED,
                            provider = failure?.provider ?: provider,
                            detail = failure?.let { typed ->
                                buildString {
                                    append(typed.userMessage)
                                    typed.technicalDetail.takeIf(String::isNotBlank)?.let { detail ->
                                        append(" | ").append(detail)
                                    }
                                }
                            } ?: errorMessage,
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Clear state for a single chapter.
     */
    fun clearChapterState(
        bookUrl: String,
        chapterIndex: Int,
        provider: String? = null,
        targetLanguage: String? = null,
    ) {
        val keys = _taskStateFlows.keys.filter { key ->
            key.bookUrl == bookUrl &&
                key.chapterIndex == chapterIndex &&
                (provider == null || key.provider == provider) &&
                (targetLanguage == null || key.targetLanguage == targetLanguage)
        }
        keys.forEach { key ->
            taskJobs.remove(key)?.cancel()
            _taskStateFlows.remove(key)
        }
    }

    /**
     * Clear all chapter states.
     */
    fun clearAllChapterStates() {
        taskJobs.values.forEach { it.cancel() }
        taskJobs.clear()
        _taskStateFlows.clear()
    }

    fun cancelTranslation(
        bookUrl: String,
        chapterIndex: Int,
        provider: String = TranslationConfig.llmProvider,
        targetLanguage: String = currentTargetLanguage(),
    ): Boolean {
        val key = TranslationChapterKey(bookUrl, chapterIndex, provider, targetLanguage)
        val task = taskJobs[key] ?: return false
        task.cancel()
        return true
    }

    /**
     * Delete translation cache and state for a chapter.
     */
    suspend fun deleteTranslationCache(book: Book, bookChapter: BookChapter) {
        val targetLanguage = currentTargetLanguage()
        val provider = TranslationConfig.llmProvider
        translationCacheGateway.deleteTranslation(
            book,
            bookChapter,
            targetLanguage,
            provider,
        )
        translationCacheGateway.clearChunkCacheForChapter(
            book,
            bookChapter,
            targetLanguage,
            provider,
        )
        clearChapterState(book.bookUrl, bookChapter.index, provider, targetLanguage)
    }

    private fun currentTargetLanguage(): String {
        return TranslationConfig.llmTargetLanguage
    }

    private fun appendLog(
        logs: List<TranslationLogEntry>,
        entry: TranslationLogEntry,
    ): List<TranslationLogEntry> {
        return appendTranslationLog(logs, entry)
    }

}

internal fun appendTranslationLog(
    logs: List<TranslationLogEntry>,
    entry: TranslationLogEntry,
): List<TranslationLogEntry> = logs + entry
