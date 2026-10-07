package io.legado.app.domain.usecase

import io.legado.app.domain.model.CanonicalTranslationMemory
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.MlKitDictionaryEnforcer
import io.legado.app.domain.model.QuickDictionaryType
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitDictionaryEnforcerTest {

    @Test
    fun testEnforcesDictionaryOverHanVietVariant() {
        val sourceCjk = "漩涡鸣人来到了木叶村。"
        val translatedVi = "Toàn Oa Minh Nhân đã đến làng lá."
        val dictionaries = listOf(
            DictPair(original = "漩涡鸣人", translation = "Uzumaki Naruto", type = QuickDictionaryType.NAME)
        )
        val result = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk,
            translatedVi = translatedVi,
            dictionaries = dictionaries,
            hanVietResolver = { if (it == "漩涡鸣人") "Toàn Oa Minh Nhân" else "" },
        )
        assertEquals("Uzumaki Naruto đã đến làng lá.", result)
    }

    @Test
    fun testGreedyLongestMatchPrecedence() {
        val sourceCjk = "漩涡鸣人和鸣人不是同一个人。"
        val translatedVi = "Toàn Oa Minh Nhân và Minh Nhân không phải là cùng một người."
        val dictionaries = listOf(
            DictPair(original = "鸣人", translation = "Naruto", type = QuickDictionaryType.NAME),
            DictPair(original = "漩涡鸣人", translation = "Uzumaki Naruto", type = QuickDictionaryType.NAME),
        )
        val result = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk,
            translatedVi = translatedVi,
            dictionaries = dictionaries,
            hanVietResolver = {
                when (it) {
                    "漩涡鸣人" -> "Toàn Oa Minh Nhân"
                    "鸣人" -> "Minh Nhân"
                    else -> ""
                }
            },
        )
        assertEquals("Uzumaki Naruto và Naruto không phải là cùng một người.", result)
    }

    @Test
    fun testEnforcesResidualCjkDirectReplacement() {
        val sourceCjk = "宇智波佐助开启了写轮眼。"
        val translatedVi = "宇智波佐助 đã mở Tả Luân Nhãn."
        val dictionaries = listOf(
            DictPair(original = "宇智波佐助", translation = "Uchiha Sasuke", type = QuickDictionaryType.NAME)
        )
        val result = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk,
            translatedVi = translatedVi,
            dictionaries = dictionaries,
        )
        assertEquals("Uchiha Sasuke đã mở Tả Luân Nhãn.", result)
    }

    @Test
    fun testEnforcesCanonicalMemoryAliases() {
        val sourceCjk = "宇智波佐助看着前方。"
        val translatedVi = "Tá Trợ nhìn về phía trước."
        val canonicalMemory = listOf(
            CanonicalTranslationMemory(
                identity = "term_sasuke",
                raw = "宇智波佐助",
                target = "Uchiha Sasuke",
                aliases = listOf("Tá Trợ", "Sasuke"),
            )
        )
        val result = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk,
            translatedVi = translatedVi,
            dictionaries = emptyList(),
            canonicalMemory = canonicalMemory,
        )
        assertEquals("Uchiha Sasuke nhìn về phía trước.", result)
    }

    @Test
    fun testSkipsWhenTargetAlreadyPresent() {
        val sourceCjk = "漩涡鸣人出现了。"
        val translatedVi = "Uzumaki Naruto đã xuất hiện."
        val dictionaries = listOf(
            DictPair(original = "漩涡鸣人", translation = "Uzumaki Naruto", type = QuickDictionaryType.NAME)
        )
        val result = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk,
            translatedVi = translatedVi,
            dictionaries = dictionaries,
            hanVietResolver = { "Toàn Oa Minh Nhân" },
        )
        assertEquals("Uzumaki Naruto đã xuất hiện.", result)
    }

    @Test
    fun testPreservesUnrelatedText() {
        val sourceCjk = "今天天气很好。"
        val translatedVi = "Hôm nay thời tiết rất tốt."
        val dictionaries = listOf(
            DictPair(original = "漩涡鸣人", translation = "Uzumaki Naruto", type = QuickDictionaryType.NAME)
        )
        val result = MlKitDictionaryEnforcer.enforce(
            sourceCjk = sourceCjk,
            translatedVi = translatedVi,
            dictionaries = dictionaries,
        )
        assertEquals(translatedVi, result)
    }
}
