package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DictionaryAwareCacheIdentityTest {

    @Test
    fun dictionaryProvidersChangeIdentityWhenDictionaryOrPackChanges() {
        val providers = listOf(
            TranslationConstants.PROVIDER_QUICK_TRANSLATOR,
            TranslationConstants.PROVIDER_HAN_VIET,
            TranslationConstants.PROVIDER_NMT,
            TranslationConstants.PROVIDER_APP_AI,
            TranslationConstants.PROVIDER_ML_KIT,
        )
        providers.forEach { provider ->
            val initial = dictionaryAwareContentHash("source", provider, 1, "pack-a")
            assertNotEquals(
                initial,
                dictionaryAwareContentHash("source", provider, 2, "pack-a"),
            )
            assertNotEquals(
                initial,
                dictionaryAwareContentHash("source", provider, 1, "pack-b"),
            )
        }
    }

    @Test
    fun providerWithoutDictionaryKeepsStableSourceHashAndScope() {
        val provider = TranslationConstants.PROVIDER_GOOGLE

        assertEquals(
            "source",
            dictionaryAwareContentHash("source", provider, 99, "new-pack"),
        )
        assertEquals(
            "book",
            dictionaryAwareScopeKey("book", provider, 99, "new-pack"),
        )
    }

    @Test
    fun scopedRevisionChangesOnlyForTheApplicableProjectOrUniverse() {
        val base = QuickDictionaryRevision(
            global = 3,
            universeKey = "xianxia",
            universe = 5,
            projectKey = "book-a",
            project = 7,
        )
        val provider = TranslationConstants.PROVIDER_QUICK_TRANSLATOR
        val initial = dictionaryAwareContentHash("source", provider, base, "pack")

        assertEquals(
            initial,
            dictionaryAwareContentHash("source", provider, base.copy(), "pack"),
        )
        assertNotEquals(
            initial,
            dictionaryAwareContentHash("source", provider, base.copy(project = 8), "pack"),
        )
        assertNotEquals(
            initial,
            dictionaryAwareContentHash("source", provider, base.copy(universe = 6), "pack"),
        )
        assertNotEquals(
            initial,
            dictionaryAwareContentHash(
                "source",
                provider,
                base.copy(universeKey = "modern"),
                "pack",
            ),
        )
    }
}
