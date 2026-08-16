package io.legado.app.domain.usecase

import io.legado.app.domain.model.DictPair
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslateChapterDictionaryMergeTest {

    @Test
    fun primaryTermsWinOverFallbackTermsForSameSource() {
        val merged = mergeDictionaryTerms(
            primaryTerms = listOf(DictPair("叶长生", "Diep Truong Sinh da chot")),
            fallbackTerms = listOf(DictPair("叶长生", "Nghia QT global")),
        )

        assertEquals(listOf("Diep Truong Sinh da chot"), merged.map { it.translation })
    }

    @Test
    fun sourceKeyIsTrimmedAndCaseInsensitive() {
        val merged = mergeDictionaryTerms(
            primaryTerms = listOf(DictPair(" Codex ", "primary")),
            fallbackTerms = listOf(DictPair("codex", "fallback")),
        )

        assertEquals(listOf(DictPair("Codex", "primary")), merged)
    }
}
