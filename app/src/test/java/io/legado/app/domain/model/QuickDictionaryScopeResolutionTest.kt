package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickDictionaryScopeResolutionTest {

    @Test
    fun projectOverridesUniverseAndGlobalForSamePhrase() {
        val resolved = resolveQuickDictionaryScopeConflicts(
            listOf(
                entry("天道", "thiên đạo chung", QuickDictionaryScope.GLOBAL, updatedAt = 30),
                entry("天道", "thiên đạo vũ trụ", QuickDictionaryScope.UNIVERSE, updatedAt = 20),
                entry("天道", "Thiên Đạo dự án", QuickDictionaryScope.PROJECT, updatedAt = 10),
            )
        )

        assertEquals(listOf("Thiên Đạo dự án"), resolved.map { it.target })
    }

    @Test
    fun universeOverridesGlobalOnlyWhenUniverseRowsWereLoaded() {
        val resolved = resolveQuickDictionaryScopeConflicts(
            listOf(
                entry("学院", "học viện", QuickDictionaryScope.GLOBAL),
                entry("学院", "ma pháp học viện", QuickDictionaryScope.UNIVERSE),
            )
        )

        assertEquals("ma pháp học viện", resolved.single().target)
    }

    @Test
    fun higherScopeIgnoreOverridesTranslationButPhoneticRemainsFallback() {
        val resolved = resolveQuickDictionaryScopeConflicts(
            listOf(
                entry("的", "của", QuickDictionaryScope.GLOBAL),
                entry(
                    raw = "的",
                    target = "",
                    scope = QuickDictionaryScope.PROJECT,
                    type = QuickDictionaryType.IGNORE,
                ),
                entry(
                    raw = "的",
                    target = "đích",
                    scope = QuickDictionaryScope.GLOBAL,
                    type = QuickDictionaryType.PHONETIC,
                ),
            )
        )

        assertEquals(2, resolved.size)
        assertTrue(resolved.any { it.type == QuickDictionaryType.IGNORE })
        assertTrue(resolved.any { it.type == QuickDictionaryType.PHONETIC })
    }

    private fun entry(
        raw: String,
        target: String,
        scope: QuickDictionaryScope,
        type: QuickDictionaryType = QuickDictionaryType.VIETPHRASE,
        updatedAt: Long = 1,
    ) = QuickDictionaryEntry(
        raw = raw,
        target = target,
        hanViet = if (type == QuickDictionaryType.PHONETIC) target else "",
        scope = scope,
        scopeKey = scope.name,
        type = type,
        updatedAt = updatedAt,
    )
}
