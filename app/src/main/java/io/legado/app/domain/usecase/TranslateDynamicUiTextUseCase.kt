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
        // Translate each label independently. Quick Translator may normalize a newline or
        // concatenate adjacent labels when they are sent as one paragraph; one-line cache keys
        // keep partial success and prevent a single malformed label from reverting the whole UI.
        return withContext(Dispatchers.IO) {
            Result.success(
                normalized.mapIndexed { index, line ->
                    execute(
                        scopeKey = "$scopeKey:line:$index",
                        originalText = line,
                        book = book,
                        contextText = contextText,
                        forceRetranslate = forceRetranslate,
                    ).getOrElse { line }
                }
            )
        }
    }

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

internal fun String.containsCjk(): Boolean = codePoints().anyMatch { codePoint ->
    codePoint in 0x3400..0x4DBF ||
        codePoint in 0x4E00..0x9FFF ||
        codePoint in 0x20000..0x2A6DF
}
