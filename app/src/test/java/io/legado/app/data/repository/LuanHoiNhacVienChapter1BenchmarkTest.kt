package io.legado.app.data.repository

import android.app.Application
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.QuickTranslationPronounMode
import io.legado.app.utils.putPrefString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LuanHoiNhacVienChapter1BenchmarkTest {

    @Before
    fun setUpAppCtx() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        app.putPrefString(
            PreferKey.quickTranslationPronounMode,
            QuickTranslationPronounMode.AUTO.value,
        )
    }

    @Test
    fun benchmarkLuanHoiNhacVienChapter1Translation() {
        val rawFile = File("chapter1_luan_hoi_nhac_vien.txt").takeIf { it.exists() }
            ?: File("app/chapter1_luan_hoi_nhac_vien.txt").takeIf { it.exists() }
            ?: File("d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/chapter1_luan_hoi_nhac_vien.txt")
        assertTrue("Raw chapter file must exist", rawFile.exists())
        val rawText = rawFile.readText(Charsets.UTF_8)
        assertTrue(rawText.isNotBlank())

        val repo = QuickTranslationRepository()

        // 1. Warm-up
        repo.translate(
            text = "苏晓身穿黑衣",
            projectTerms = emptyList(),
            customPhonetics = emptyList(),
            pronounMode = null,
            beamWidth = 1,
        )

        // 2. Translate with Beam Width = 1 (Greedy / DP)
        val t0 = System.currentTimeMillis()
        val transBeam1 = repo.translate(
            text = rawText,
            projectTerms = emptyList(),
            customPhonetics = emptyList(),
            pronounMode = null,
            beamWidth = 1,
        )
        val timeBeam1 = System.currentTimeMillis() - t0
        File("chapter1_qt_beam1.txt").writeText(transBeam1, Charsets.UTF_8)

        // 3. Translate with Beam Width = 3 (Default Beam Search)
        val t1 = System.currentTimeMillis()
        val transBeam3 = repo.translate(
            text = rawText,
            projectTerms = emptyList(),
            customPhonetics = emptyList(),
            pronounMode = null,
            beamWidth = 3,
        )
        val timeBeam3 = System.currentTimeMillis() - t1
        File("chapter1_qt_beam3.txt").writeText(transBeam3, Charsets.UTF_8)

        // 4. Translate with Beam Width = 5 (Max Beam Search)
        val t2 = System.currentTimeMillis()
        val transBeam5 = repo.translate(
            text = rawText,
            projectTerms = emptyList(),
            customPhonetics = emptyList(),
            pronounMode = null,
            beamWidth = 5,
        )
        val timeBeam5 = System.currentTimeMillis() - t2
        File("chapter1_qt_beam5.txt").writeText(transBeam5, Charsets.UTF_8)

        // 5. Translate with Custom Project Terms (Luân Hồi Nhạc Viện + Kẻ Săn Đuổi)
        val projectTerms = listOf(
            DictPair("轮回乐园", "Luân Hồi Nhạc Viện"),
            DictPair("猎杀者", "Kẻ Săn Đuổi"),
            DictPair("苏晓", "Tô Hiểu"),
        )
        val transWithTerms = repo.translate(
            text = rawText,
            projectTerms = projectTerms,
            customPhonetics = emptyList(),
            pronounMode = null,
            beamWidth = 3,
        )
        File("chapter1_qt_with_terms.txt").writeText(transWithTerms, Charsets.UTF_8)

        println("=== BENCHMARK CHAPTER 1 LUAN HOI NHAC VIEN ===")
        println("Raw Character Count: ${rawText.length}")
        println("Beam 1 (Greedy DP) Duration: ${timeBeam1} ms | Output Chars: ${transBeam1.length}")
        println("Beam 3 (Beam Search) Duration: ${timeBeam3} ms | Output Chars: ${transBeam3.length}")
        println("Beam 5 (Max Beam) Duration: ${timeBeam5} ms | Output Chars: ${transBeam5.length}")

        // Quality assertions
        assertFalse("Output must not be empty", transBeam3.isBlank())
        assertTrue("Contains protagonist name Tô Hiểu", transBeam3.contains("Tô Hiểu"))
        assertTrue("Contains luân hồi", transBeam3.contains("luân hồi", ignoreCase = true))
        assertTrue("With terms contains Luân Hồi Nhạc Viện", transWithTerms.contains("Luân Hồi Nhạc Viện"))
        assertTrue("With terms contains Kẻ Săn Đuổi", transWithTerms.contains("Kẻ Săn Đuổi"))

        // Pure QT assertions: "bạn" must be gone, "ngươi" preserved
        assertFalse("Must not contain 'vì bạn mở'", transBeam3.contains("vì bạn mở"))
        assertFalse("Must not contain 'thân phận của bạn'", transBeam3.contains("thân phận của bạn"))

        // Grammar polish assertions: excessive 'của' and awkward inverted convert clauses healed
        assertFalse("Must not contain 'của muốn'", transBeam3.contains("của muốn"))
        assertFalse("Must not contain 'ngồi dậy của phí sức'", transBeam3.contains("ngồi dậy của phí sức"))
        assertFalse("Must not contain 'đã cừu nhân của say rượu'", transBeam3.contains("đã cừu nhân của say rượu"))
        assertTrue("Must contain 'mở ra cho ngươi'", transBeam3.contains("mở ra cho ngươi"))
        assertTrue("Must contain 'cừu nhân đã say rượu'", transBeam3.contains("cừu nhân đã say rượu", ignoreCase = true) || transBeam3.contains("cừu nhân say rượu", ignoreCase = true))
        assertTrue("Must contain 'phí sức ngồi dậy'", transBeam3.contains("phí sức ngồi dậy", ignoreCase = true))
    }

    @Test
    fun testTextSelectionMappingAndDictionaryAddition() {
        val repo = QuickTranslationRepository()
        val cjkSentence = "【猎杀者，你并没有死亡，是/否愿意加入‘轮回乐园’，这里有你想要的一切。】"
        val translated = repo.translate(
            text = cjkSentence,
            projectTerms = emptyList(),
            customPhonetics = emptyList(),
            pronounMode = null,
            beamWidth = 3,
        )

        // 1. Verify pure QT translated output
        assertTrue(translated, translated.contains("ngươi không hề chết") || translated.contains("ngươi cũng không tử vong"))
        assertFalse(translated, translated.contains("bạn"))

        // 2. Test Selection Mapping: user highlights "ngươi không hề chết" in translated text
        val selectedText = if (translated.contains("ngươi không hề chết")) "ngươi không hề chết" else "ngươi"
        val selStart = translated.indexOf(selectedText)
        val request = io.legado.app.ui.quickdict.QuickDictionaryRequest(
            bookUrl = "book_test",
            selectedText = selectedText,
            sourceText = cjkSentence,
            displayText = translated,
            selectionStart = selStart,
            selectionEnd = selStart + selectedText.length,
            sourceLocation = "",
        )

        val resolution = io.legado.app.ui.quickdict.resolveQuickDictionarySelectionResult(
            request = request,
            quickTranslationGateway = repo,
        )

        val anchor = resolution.anchor
        assertTrue("Anchor raw text must be resolved", anchor != null && anchor.rawText.isNotBlank())
        assertTrue("Resolved anchor must be subset of CJK sentence", cjkSentence.contains(anchor!!.rawText))

        // 3. Test Dynamic Dictionary Addition: add "衍生位面" -> "Vị Diện Phái Sinh"
        val cjkParagraph = "签订契约，你将穿梭在一个个‘衍生位面’中，去完成‘轮回乐园’发布的任务。"
        val beforeAdd = repo.translate(cjkParagraph, emptyList())
        assertFalse(beforeAdd.contains("Vị Diện Phái Sinh"))

        val newDictTerms = listOf(
            DictPair("衍生位面", "Vị Diện Phái Sinh"),
            DictPair("轮回乐园", "Luân Hồi Nhạc Viện"),
        )
        val afterAdd = repo.translate(cjkParagraph, newDictTerms)
        assertTrue(afterAdd.contains("Vị Diện Phái Sinh"))
        assertTrue(afterAdd.contains("Luân Hồi Nhạc Viện"))
        assertFalse(afterAdd.contains("bạn"))
    }
}
