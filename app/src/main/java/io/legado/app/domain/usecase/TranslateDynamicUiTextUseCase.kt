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
        if (!forceRetranslate) {
            translationCacheGateway.readDynamicUiTranslation(
                scopeKey = cacheScopeKey,
                originalText = originalText,
                targetLanguage = targetLanguage,
                provider = provider,
            )?.let { return@withContext Result.success(it) }
        }

        runCatching {
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

            Result.success(
                normalized.mapIndexed { index, line ->
                    if (line.isBlank() || !line.containsCjk()) {
                        return@mapIndexed line
                    }
                    val lineCacheKey = "$cacheScopeBaseKey:line:$index"
                    if (!forceRetranslate) {
                        translationCacheGateway.readDynamicUiTranslation(
                            scopeKey = lineCacheKey,
                            originalText = line,
                            targetLanguage = targetLanguage,
                            provider = provider,
                        )?.let { return@mapIndexed it }
                    }
                    runCatching {
                        val translated = quickTranslationGateway.translate(
                            text = removeIgnoredTerms(line, ignoredTerms),
                            projectTerms = projectTerms,
                            customPhonetics = customPhonetics,
                        )
                        translationCacheGateway.writeDynamicUiTranslation(
                            scopeKey = lineCacheKey,
                            originalText = line,
                            targetLanguage = targetLanguage,
                            provider = provider,
                            translatedText = translated,
                        )
                        translated
                    }.getOrElse { line }
                }
            )
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
    ): Result<String> = execute(
        scopeKey = "$scopeKey:title",
        originalText = originalText,
        book = book,
        contextText = contextText,
        forceRetranslate = forceRetranslate,
    ).map { it.restructureChapterNumbers().toTitleCase() }

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
