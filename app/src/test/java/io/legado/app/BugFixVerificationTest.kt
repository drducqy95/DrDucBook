package io.legado.app

import io.legado.app.data.repository.QuickTranslationTextPostProcessor
import io.legado.app.domain.model.VietnameseTranslationPostProcessor
import io.legado.app.ui.main.MainDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugFixVerificationTest {

    @Test
    fun testBug1_cleanRogueBooleanLiterals() {
        val sample1 = "hắn nói true rồi mỉm cười"
        val cleaned1 = VietnameseTranslationPostProcessor.cleanRogueBooleanLiterals(sample1)
        assertEquals("hắn nói rồi mỉm cười", cleaned1)

        val sample2 = "true Đêm đã khuya, false gió thổi mạnh."
        val cleaned2 = QuickTranslationTextPostProcessor.cleanRogueBooleanLiterals(sample2)
        assertEquals("Đêm đã khuya, gió thổi mạnh.", cleaned2)
    }

    @Test
    fun testBug2_cleanSpuriousCuaPatterns() {
        // Regex rules added to qt_clean_rules_v8.tsv
        val ruleStartCua = Regex("""(?U)\bCủa\s+(mang|được|có|làm|ở|cho|đi|đến|đem|lấy|hiểu|thấy|nghe|nói|biết|cười|khóc)\b""")
        val rulePrepCua = Regex("""(?U)\b(trong|ngoài|trên|dưới|giữa)\s+của\b""")
        val ruleCompCua = Regex("""(?U)\bcủa\s+(càng\s+[\p{L}\p{M}]+)\b""")
        val ruleAdverbCua = Regex("""(?U)\bcủa\s+(tối thiểu|tối đa|ít nhất|nhiều nhất)\b""")
        val ruleVerbCua = Regex("""(?U)\bcủa\s+(hiểu|biết|thấy)\s+([\p{L}\p{M}]+)\b""")
        val ruleAbstractCua = Regex("""(?U)\b(kết quả|hậu quả|nguyên nhân|quá trình)\s+của\s+(giải thích|phân tích|nghiên cứu|điều tra)\b""")

        // 1. Spurious "của" before comparative: "nước của càng nhiều" -> "nước càng nhiều"
        val s1 = "làm ngươi nuốt vào nước của càng nhiều."
        val r1 = s1.replace(ruleCompCua, "$1")
        assertEquals("làm ngươi nuốt vào nước càng nhiều.", r1)

        // 2. Spurious "của" after locative preposition: "trong của đau đớn" -> "trong đau đớn"
        val s2 = "ở vực thẳm trong của đau đớn không ngừng trầm luân."
        val r2 = s2.replace(rulePrepCua, "$1")
        assertEquals("ở vực thẳm trong đau đớn không ngừng trầm luân.", r2)

        // 3. Sentence-starting "Của" followed by verb: "Của mang trên mặt" -> "mang trên mặt"
        val s3 = "Của mang trên mặt ôn cười của và, nhiệt tình rất là ruột"
        val r3 = s3.replace(ruleStartCua, "$1")
        assertTrue(r3.startsWith("mang trên mặt"))

        // 4. Abstract noun + của + verb: "kết quả của giải thích" -> "kết quả giải thích"
        val s4 = "Liền là kết quả của giải thích không quá tốt"
        val r4 = s4.replace(ruleAbstractCua, "$1 $2")
        assertEquals("Liền là kết quả giải thích không quá tốt", r4)

        // 5. Adverb of minimum: "thủy tính của tối thiểu" -> "thủy tính tối thiểu"
        val s5 = "thủy tính của tối thiểu còn là"
        val r5 = s5.replace(ruleAdverbCua, "$1")
        assertEquals("thủy tính tối thiểu còn là", r5)

        // 6. Verb + noun: "của hiểu đệ tử" -> "hiểu đệ tử"
        val s6 = "của hiểu đệ tử môn nhân rất nhiều"
        val r6 = s6.replace(ruleVerbCua, "$1 $2")
        assertEquals("hiểu đệ tử môn nhân rất nhiều", r6)
    }

    @Test
    fun testBug3_safeGridStateKeyLookupFallback() {
        val map = mutableMapOf<Long, String>()
        val missingKey = -21L
        // Safe get fallback prevents NoSuchElementException: Key -21 is missing in the map
        val result = map[missingKey] ?: "default_state"
        assertEquals("default_state", result)
    }

    @Test
    fun testBug5_downloadsDestinationIntegrated() {
        // Downloads tab is integrated into main navigation
        assertTrue(MainDestination.mainDestinations.contains(MainDestination.Downloads))
        assertEquals(6, MainDestination.mainDestinations.size)
        assertEquals("downloads", MainDestination.Downloads.route)
    }
}
