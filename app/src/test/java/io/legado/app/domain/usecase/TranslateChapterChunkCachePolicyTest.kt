package io.legado.app.domain.usecase

import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.TranslationConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateChapterChunkCachePolicyTest {

    @Test
    fun presetRuntimeOptionsOverrideGlobalTranslationDefaults() {
        val runtime = AiTaskRuntimeOptions(
            maxInputChars = 1_000,
            concurrentRequests = 1,
            retryCount = 2,
        )

        assertEquals(1_000, resolveAiRuntimeMaxInputChars(runtime, globalFallback = 5_000))
        assertEquals(1, resolveAiRuntimeConcurrentRequests(runtime, globalFallback = 2))
        assertEquals(2, resolveAiRuntimeRetryCount(runtime, globalFallback = 4))
    }

    @Test
    fun unrelatedDictionaryChangeKeepsSuccessfulChunkCache() {
        val source = "叶长生 bước vào đại điện."
        val before = chunkHash(source, listOf(DictPair("叶长生", "Diệp Trường Sinh")))
        val after = chunkHash(
            source,
            listOf(
                DictPair("叶长生", "Diệp Trường Sinh"),
                DictPair("大梦学宫", "Đại Mộng Học Cung"),
            ),
        )

        assertEquals(before, after)
    }

    @Test
    fun relatedDictionaryChangeInvalidatesOnlyContainingChunk() {
        val firstChunk = "叶长生 bước vào đại điện."
        val secondChunk = "大梦学宫 mở cửa."
        val oldTerms = listOf(DictPair("叶长生", "Diệp Trường Sinh"))
        val newTerms = listOf(DictPair("叶长生", "Diệp Trường Sinh mới"))

        assertNotEquals(chunkHash(firstChunk, oldTerms), chunkHash(firstChunk, newTerms))
        assertEquals(chunkHash(secondChunk, oldTerms), chunkHash(secondChunk, newTerms))
    }

    @Test
    fun aiModelOrPromptChangeInvalidatesChunkCache() {
        val source = "\u53f6\u957f\u751f enters the hall."
        val before = chunkTranslationDependencyHash(
            sourceContent = source,
            provider = TranslationConstants.PROVIDER_APP_AI,
            dictionaryTerms = listOf(DictPair("\u53f6\u957f\u751f", "Diep Truong Sinh")),
            quickTranslationPackVersion = "pack-1",
            providerConfigurationRevision = "combo-a:model-a:prompt-a",
            computeHash = { it.hashCodeString() },
        )
        val after = chunkTranslationDependencyHash(
            sourceContent = source,
            provider = TranslationConstants.PROVIDER_APP_AI,
            dictionaryTerms = listOf(DictPair("\u53f6\u957f\u751f", "Diep Truong Sinh")),
            quickTranslationPackVersion = "pack-1",
            providerConfigurationRevision = "single-model-b:prompt-b",
            computeHash = { it.hashCodeString() },
        )

        assertNotEquals(before, after)
        assertTrue(before.contains("|ai-pipeline:translator-engine-android-v5-structured-split-fallback"))
    }

    @Test
    fun aiDictionaryChangeInvalidatesOnlyChunkContainingChangedTerm() {
        val firstChunk = "\u53f6\u957f\u751f enters the hall."
        val secondChunk = "\u5927\u68a6\u5b66\u5bab opens its gate."
        val oldTerms = listOf(DictPair("\u53f6\u957f\u751f", "Diep Truong Sinh"))
        val newTerms = listOf(DictPair("\u53f6\u957f\u751f", "Ten moi"))

        fun aiHash(source: String, terms: List<DictPair>) = chunkTranslationDependencyHash(
            sourceContent = source,
            provider = TranslationConstants.PROVIDER_APP_AI,
            dictionaryTerms = terms,
            quickTranslationPackVersion = "pack",
            providerConfigurationRevision = "ignored-combo-model-prompt",
            computeHash = { it.hashCodeString() },
        )

        assertNotEquals(aiHash(firstChunk, oldTerms), aiHash(firstChunk, newTerms))
        assertEquals(aiHash(secondChunk, oldTerms), aiHash(secondChunk, newTerms))
    }

    @Test
    fun mlKitDictionaryChangeInvalidatesOnlyChunkContainingChangedTerm() {
        val affected = "叶长生 enters the hall."
        val unrelated = "大梦学宫 opens its gate."
        val oldTerms = listOf(DictPair("叶长生", "Diep Truong Sinh"))
        val newTerms = listOf(DictPair("叶长生", "Ten moi"))

        fun mlKitHash(source: String, terms: List<DictPair>) = chunkTranslationDependencyHash(
            sourceContent = source,
            provider = TranslationConstants.PROVIDER_ML_KIT,
            dictionaryTerms = terms,
            quickTranslationPackVersion = "pack",
            computeHash = { it.hashCodeString() },
        )

        assertNotEquals(mlKitHash(affected, oldTerms), mlKitHash(affected, newTerms))
        assertEquals(mlKitHash(unrelated, oldTerms), mlKitHash(unrelated, newTerms))
    }

    private fun chunkHash(source: String, terms: List<DictPair>): String =
        chunkTranslationDependencyHash(
            sourceContent = source,
            provider = TranslationConstants.PROVIDER_QUICK_TRANSLATOR,
            dictionaryTerms = terms,
            quickTranslationPackVersion = "test-pack",
            computeHash = { it.hashCodeString() },
        )
}

private fun String.hashCodeString(): String = hashCode().toString()
