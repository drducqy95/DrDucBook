package io.legado.app.domain.gateway

import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.MappedTranslation
import io.legado.app.domain.model.QuickDictionaryCatalog
import io.legado.app.domain.model.QuickDictionaryCatalogEntry
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickTranslationPronounMode
import io.legado.app.domain.model.alignedParagraphMapping

/** Deterministic offline Chinese-to-Vietnamese translation and Hán-Việt reading. */
interface QuickTranslationGateway {
    val packVersion: String

    fun packVersionFor(pronounMode: QuickTranslationPronounMode? = null): String = packVersion

    /** Loads the mmap/trie pack off the reader's critical path. */
    fun warmUp() = Unit

    fun translate(
        text: String,
        projectTerms: List<DictPair> = emptyList(),
        customPhonetics: List<DictPair> = emptyList(),
    ): String

    fun translate(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
        pronounMode: QuickTranslationPronounMode?,
    ): String = translate(text, projectTerms, customPhonetics)

    fun translateMapped(
        text: String,
        projectTerms: List<DictPair> = emptyList(),
        customPhonetics: List<DictPair> = emptyList(),
    ): MappedTranslation {
        val translated = translate(text, projectTerms, customPhonetics)
        val mapping = alignedParagraphMapping(text, translated, "quick_translator")
        return MappedTranslation(translated, mapping.segments, mapping.engine)
    }

    fun translateMapped(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
        pronounMode: QuickTranslationPronounMode?,
    ): MappedTranslation {
        val translated = translate(text, projectTerms, customPhonetics, pronounMode)
        val mapping = alignedParagraphMapping(text, translated, "quick_translator")
        return MappedTranslation(translated, mapping.segments, mapping.engine)
    }

    fun hanViet(
        text: String,
        customPhonetics: List<DictPair> = emptyList(),
    ): String

    fun hanVietMapped(
        text: String,
        customPhonetics: List<DictPair> = emptyList(),
    ): MappedTranslation {
        val translated = hanViet(text, customPhonetics)
        val mapping = alignedParagraphMapping(text, translated, "han_viet")
        return MappedTranslation(translated, mapping.segments, mapping.engine)
    }

    fun getBuiltInCatalogs(): List<QuickDictionaryCatalog>

    fun searchBuiltInEntries(
        type: QuickDictionaryType,
        query: String = "",
        limit: Int = 500,
        catalogId: String? = null,
    ): List<QuickDictionaryCatalogEntry>

    /** Exact, allocation-bounded lookup used to skip duplicates during large user imports. */
    fun containsBuiltInEntry(type: QuickDictionaryType, raw: String): Boolean = false
}
