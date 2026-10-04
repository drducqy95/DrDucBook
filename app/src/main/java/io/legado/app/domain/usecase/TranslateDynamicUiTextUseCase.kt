package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.model.QUICK_DICTIONARY_IGNORE_TARGET
import io.legado.app.domain.model.QuickDictionaryRevision
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.dictionaryAwareScopeKey
import io.legado.app.domain.model.toQuickPhoneticPair
import io.legado.app.domain.model.toQuickTranslationPair
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deterministic display-only translation for source labels and book metadata.
 *
 * Dynamic UI always uses the bundled Quick Translator and Vietnamese output. It must never wait
 * for the chapter provider (NMT, Google, or AI), and it never mutates the source [Book].
 */
class TranslateDynamicUiTextUseCase(
    private val translationCacheGateway: TranslationCacheGateway,
    private val dictionaryGateway: DictionaryGateway,
    private val quickTranslationGateway: QuickTranslationGateway,
    private val quickDictionaryGateway: QuickDictionaryGateway,
) {

    private val memoryCache = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Result<String>>>()

    suspend fun execute(
        scopeKey: String,
        originalText: String,
        book: Book? = null,
        contextText: String = originalText,
        forceRetranslate: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (originalText.isBlank() || !originalText.containsCjk()) {
            return@withContext Result.success(originalText)
        }

        val provider = TranslationConstants.PROVIDER_QUICK_TRANSLATOR
        val targetLanguage = TranslationConstants.TARGET_VIETNAMESE
        val dictionaryRevision = book?.let {
            quickDictionaryGateway.getEffectiveRevision(it, contextText)
        } ?: QuickDictionaryRevision(
            global = quickDictionaryGateway.revisionFor(QuickDictionaryScope.GLOBAL)
        )
        val cacheScopeKey = dictionaryAwareScopeKey(
            scopeKey = scopeKey,
            provider = provider,
            dictionaryRevision = dictionaryRevision,
            quickTranslationPackVersion = quickTranslationGateway.packVersion,
        )
        val memKey = "$cacheScopeKey:$originalText"

        if (!forceRetranslate) {
            memoryCache[memKey]?.let { return@withContext Result.success(it) }
            translationCacheGateway.readDynamicUiTranslation(
                scopeKey = cacheScopeKey,
                originalText = originalText,
                targetLanguage = targetLanguage,
                provider = provider,
            )?.let {
                memoryCache[memKey] = it
                return@withContext Result.success(it)
            }
        }

        val deferred = CompletableDeferred<Result<String>>()
        val existing = inFlight.putIfAbsent(memKey, deferred)
        if (existing != null) {
            return@withContext existing.await()
        }

        try {
            val res = runCatching {
                val quickEntries = book
                    ?.let { quickDictionaryGateway.getEffectiveEntries(it, contextText) }
                    .orEmpty()
                val quickTerms = quickEntries.mapNotNull { it.toQuickTranslationPair() }
                val ignoredTerms = quickTerms
                    .filter { it.translation == QUICK_DICTIONARY_IGNORE_TARGET }
                    .map { it.original }
                val bookTerms = book?.let(dictionaryGateway::getBookDictionaries)?.pairs.orEmpty()
                val translated = quickTranslationGateway.translate(
                    text = removeIgnoredTerms(originalText, ignoredTerms),
                    projectTerms = (quickTerms.filterNot {
                        it.translation == QUICK_DICTIONARY_IGNORE_TARGET
                    } + bookTerms).distinctBy { it.original.trim().lowercase() },
                    customPhonetics = quickEntries.mapNotNull { it.toQuickPhoneticPair() },
                )
                if (memoryCache.size > 4000) memoryCache.clear()
                memoryCache[memKey] = translated
                translationCacheGateway.writeDynamicUiTranslation(
                    scopeKey = cacheScopeKey,
                    originalText = originalText,
                    targetLanguage = targetLanguage,
                    provider = provider,
                    translatedText = translated,
                )
                translated
            }
            deferred.complete(res)
            res
        } finally {
            inFlight.remove(memKey)
        }
    }

    suspend fun executeLines(
        scopeKey: String,
        originalLines: List<String>,
        book: Book? = null,
        contextText: String = originalLines.joinToString("\n"),
        forceRetranslate: Boolean = false,
    ): Result<List<String>> {
        if (originalLines.isEmpty()) return Result.success(emptyList())
        val normalized = originalLines.map { it.replace('\r', ' ').replace('\n', ' ') }
        return withContext(Dispatchers.IO) {
            val provider = TranslationConstants.PROVIDER_QUICK_TRANSLATOR
            val targetLanguage = TranslationConstants.TARGET_VIETNAMESE
            val dictionaryRevision = book?.let {
                quickDictionaryGateway.getEffectiveRevision(it, contextText)
            } ?: QuickDictionaryRevision(
                global = quickDictionaryGateway.revisionFor(QuickDictionaryScope.GLOBAL)
            )
            val cacheScopeBaseKey = dictionaryAwareScopeKey(
                scopeKey = scopeKey,
                provider = provider,
                dictionaryRevision = dictionaryRevision,
                quickTranslationPackVersion = quickTranslationGateway.packVersion,
            )

            val results = arrayOfNulls<String>(normalized.size)
            val missingIndices = ArrayList<Int>()

            for (index in normalized.indices) {
                val line = normalized[index]
                if (line.isBlank() || !line.containsCjk()) {
                    results[index] = line
                    continue
                }
                val lineCacheKey = "$cacheScopeBaseKey:line:$index"
                val memKey = "$lineCacheKey:$line"
                if (!forceRetranslate) {
                    val memHit = memoryCache[memKey]
                    if (memHit != null) {
                        results[index] = memHit
                        continue
                    }
                    val diskHit = translationCacheGateway.readDynamicUiTranslation(
                        scopeKey = lineCacheKey,
                        originalText = line,
                        targetLanguage = targetLanguage,
                        provider = provider,
                    )
                    if (diskHit != null) {
                        memoryCache[memKey] = diskHit
                        results[index] = diskHit
                        continue
                    }
                }
                missingIndices.add(index)
            }

            if (missingIndices.isEmpty()) {
                return@withContext Result.success(results.map { it.orEmpty() })
            }

            val quickEntries = book
                ?.let { quickDictionaryGateway.getEffectiveEntries(it, contextText) }
                .orEmpty()
            val quickTerms = quickEntries.mapNotNull { it.toQuickTranslationPair() }
            val ignoredTerms = quickTerms
                .filter { it.translation == QUICK_DICTIONARY_IGNORE_TARGET }
                .map { it.original }
            val bookTerms = book?.let(dictionaryGateway::getBookDictionaries)?.pairs.orEmpty()
            val projectTerms = (quickTerms.filterNot {
                it.translation == QUICK_DICTIONARY_IGNORE_TARGET
            } + bookTerms).distinctBy { it.original.trim().lowercase() }
            val customPhonetics = quickEntries.mapNotNull { it.toQuickPhoneticPair() }

            if (missingIndices.size > 1) {
                val linesToTranslate = missingIndices.map { removeIgnoredTerms(normalized[it], ignoredTerms) }
                val joined = linesToTranslate.joinToString("\n")
                val batchedTranslated = runCatching {
                    quickTranslationGateway.translate(
                        text = joined,
                        projectTerms = projectTerms,
                        customPhonetics = customPhonetics,
                    )
                }.getOrNull()

                val split = batchedTranslated?.split("\n")
                if (split != null && split.size == missingIndices.size) {
                    for (i in missingIndices.indices) {
                        val index = missingIndices[i]
                        val originalLine = normalized[index]
                        val translatedLine = split[i]
                        val lineCacheKey = "$cacheScopeBaseKey:line:$index"
                        val memKey = "$lineCacheKey:$originalLine"
                        results[index] = translatedLine
                        memoryCache[memKey] = translatedLine
                        translationCacheGateway.writeDynamicUiTranslation(
                            scopeKey = lineCacheKey,
                            originalText = originalLine,
                            targetLanguage = targetLanguage,
                            provider = provider,
                            translatedText = translatedLine,
                        )
                    }
                    return@withContext Result.success(results.map { it.orEmpty() })
                }
            }

            for (index in missingIndices) {
                if (results[index] != null) continue
                val line = normalized[index]
                val lineCacheKey = "$cacheScopeBaseKey:line:$index"
                val memKey = "$lineCacheKey:$line"
                val translated = runCatching {
                    quickTranslationGateway.translate(
                        text = removeIgnoredTerms(line, ignoredTerms),
                        projectTerms = projectTerms,
                        customPhonetics = customPhonetics,
                    )
                }.getOrElse { line }
                results[index] = translated
                memoryCache[memKey] = translated
                translationCacheGateway.writeDynamicUiTranslation(
                    scopeKey = lineCacheKey,
                    originalText = line,
                    targetLanguage = targetLanguage,
                    provider = provider,
                    translatedText = translated,
                )
            }

            Result.success(results.map { it.orEmpty() })
        }
    }

    suspend fun executeAuthorName(
        scopeKey: String,
        originalText: String,
        book: Book? = null,
        forceRetranslate: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (originalText.isBlank()) {
            return@withContext Result.success(originalText)
        }
        if (!originalText.containsCjk()) {
            return@withContext Result.success(originalText.toTitleCase())
        }

        val provider = TranslationConstants.PROVIDER_QUICK_TRANSLATOR
        val targetLanguage = TranslationConstants.TARGET_VIETNAMESE
        val dictionaryRevision = book?.let {
            quickDictionaryGateway.getEffectiveRevision(it, originalText)
        } ?: QuickDictionaryRevision(
            global = quickDictionaryGateway.revisionFor(QuickDictionaryScope.GLOBAL)
        )
        val cacheScopeKey = dictionaryAwareScopeKey(
            scopeKey = "$scopeKey:author",
            provider = provider,
            dictionaryRevision = dictionaryRevision,
            quickTranslationPackVersion = quickTranslationGateway.packVersion,
        )
        if (!forceRetranslate) {
            translationCacheGateway.readDynamicUiTranslation(
                scopeKey = cacheScopeKey,
                originalText = originalText,
                targetLanguage = targetLanguage,
                provider = provider,
            )?.let { return@withContext Result.success(it.restructureChapterNumbers().toTitleCase()) }
        }

        runCatching {
            val quickEntries = book
                ?.let { quickDictionaryGateway.getEffectiveEntries(it, originalText) }
                .orEmpty()
            val quickTerms = quickEntries.mapNotNull { it.toQuickTranslationPair() }
            val ignoredTerms = quickTerms
                .filter { it.translation == QUICK_DICTIONARY_IGNORE_TARGET }
                .map { it.original }
            val bookTerms = book?.let(dictionaryGateway::getBookDictionaries)?.pairs.orEmpty()
            val customPhonetics = quickEntries.mapNotNull { it.toQuickPhoneticPair() }
            val translated = quickTranslationGateway.translate(
                text = removeIgnoredTerms(originalText, ignoredTerms),
                projectTerms = (quickTerms.filterNot {
                    it.translation == QUICK_DICTIONARY_IGNORE_TARGET
                } + bookTerms).distinctBy { it.original.trim().lowercase() },
                customPhonetics = customPhonetics,
            ).restructureChapterNumbers().toTitleCase()
            translationCacheGateway.writeDynamicUiTranslation(
                scopeKey = cacheScopeKey,
                originalText = originalText,
                targetLanguage = targetLanguage,
                provider = provider,
                translatedText = translated,
            )
            translated
        }
    }

    suspend fun executeBookName(
        scopeKey: String,
        originalText: String,
        book: Book? = null,
        contextText: String = originalText,
        forceRetranslate: Boolean = false,
    ): Result<String> = execute(
        scopeKey = "$scopeKey:bookname",
        originalText = originalText,
        book = book,
        contextText = contextText,
        forceRetranslate = forceRetranslate,
    ).map { it.toTitleCase() }

    suspend fun executeChapterTitle(
        scopeKey: String,
        originalText: String,
        book: Book? = null,
        contextText: String = originalText,
        forceRetranslate: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (originalText.isBlank() || !originalText.containsCjk()) {
            return@withContext Result.success(originalText)
        }
        val targetLanguage = TranslationConstants.TARGET_VIETNAMESE
        val titleScopeKey = "$scopeKey:title"
        if (!forceRetranslate) {
            translationCacheGateway.readDynamicUiTranslation(
                scopeKey = titleScopeKey,
                originalText = originalText,
                targetLanguage = targetLanguage,
                provider = TranslationConstants.PROVIDER_APP_AI,
            )?.let { return@withContext Result.success(it.restructureChapterNumbers().toTitleCase()) }
        }
        execute(
            scopeKey = titleScopeKey,
            originalText = originalText,
            book = book,
            contextText = contextText,
            forceRetranslate = forceRetranslate,
        ).map { it.restructureChapterNumbers().toTitleCase() }
    }

    suspend fun saveAiChapterTitle(
        scopeKey: String,
        originalText: String,
        aiTitle: String,
    ) = withContext(Dispatchers.IO) {
        if (originalText.isBlank() || aiTitle.isBlank() || aiTitle.containsCjk()) return@withContext
        val targetLanguage = TranslationConstants.TARGET_VIETNAMESE
        val titleScopeKey = "$scopeKey:title"
        val formatted = aiTitle.restructureChapterNumbers().toTitleCase()
        translationCacheGateway.writeDynamicUiTranslation(
            scopeKey = titleScopeKey,
            originalText = originalText,
            targetLanguage = targetLanguage,
            provider = TranslationConstants.PROVIDER_APP_AI,
            translatedText = formatted,
        )
    }

    suspend fun executeChapterTitles(
        scopeKey: String,
        originalLines: List<String>,
        book: Book? = null,
        contextText: String = originalLines.joinToString("\n"),
        forceRetranslate: Boolean = false,
    ): Result<List<String>> = executeLines(
        scopeKey = scopeKey,
        originalLines = originalLines,
        book = book,
        contextText = contextText,
        forceRetranslate = forceRetranslate,
    ).map { titles -> titles.map { it.restructureChapterNumbers().toTitleCase() } }

    suspend fun clearCache() {
        memoryCache.clear()
        inFlight.clear()
        translationCacheGateway.clearDynamicUiTranslations()
    }

    private fun removeIgnoredTerms(text: String, terms: List<String>): String {
        if (text.isBlank() || terms.isEmpty()) return text
        return terms.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sortedByDescending(String::length)
            .fold(text) { output, term -> output.replace(term, "") }
    }
}

fun String.toTitleCase(): String =
    split(" ").joinToString(" ") { word ->
        if (word.isBlank()) {
            word
        } else {
            val letterIdx = word.indexOfFirst { it.isLetter() }
            if (letterIdx >= 0 && word[letterIdx].isLowerCase()) {
                word.substring(0, letterIdx) +
                    word[letterIdx].titlecase() +
                    word.substring(letterIdx + 1)
            } else {
                word
            }
        }
    }

private val chapterNumberPattern = Regex(
    """(?i)(?:^|(?<=[\s\p{Punct}]))[Đđ]ệ\s*([0-9IVXLCDMivxlcdm]+(?:[.\-_][0-9IVXLCDMivxlcdm]+)?|[Nn]hất|[Nn]hị|[Tt]am|[Tt]ứ|[Nn]gũ|[Ll]ục|[Tt]hất|[Bb]át|[Cc]ửu|[Tt]hập(?:\s*(?:[Nn]hất|[Nn]hị|[Tt]am|[Tt]ứ|[Nn]gũ|[Ll]ục|[Tt]hất|[Bb]át|[Cc]ửu))?|[Bb]ách|[Tt]hiên|[Vv]ạn)\s*([Cc]hương|[Tt]iết|[Qq]uyển|[Hh]ồi|[Tt]hiên|[Tt]ập|[Pp]hần|[Mm]ục|[Tt]hoại|[Bb]ộ|[Kk]ỳ|[Bb]ản|[Tt]rang)"""
)

fun String.restructureChapterNumbers(): String =
    chapterNumberPattern.replace(this) { match ->
        val number = match.groupValues[1]
        val classifier = match.groupValues[2]
        val classifierTitle = classifier.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        val hasTrailingLetter = match.range.last + 1 < length && this[match.range.last + 1].isLetter()
        if (hasTrailingLetter) "$classifierTitle $number " else "$classifierTitle $number"
    }

internal fun String.containsCjk(): Boolean = codePoints().anyMatch { codePoint ->
    codePoint in 0x3400..0x4DBF ||
        codePoint in 0x4E00..0x9FFF ||
        codePoint in 0x20000..0x2A6DF
}
