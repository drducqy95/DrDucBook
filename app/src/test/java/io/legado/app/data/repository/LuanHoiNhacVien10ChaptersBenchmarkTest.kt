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
class LuanHoiNhacVien10ChaptersBenchmarkTest {

    private val projectTerms = listOf(
        DictPair("轮回乐园", "Luân Hồi Nhạc Viện"),
        DictPair("猎杀者", "Kẻ Săn Đuổi"),
        DictPair("苏晓", "Tô Hiểu"),
        DictPair("契约者", "Khế Ước Giả"),
        DictPair("世界之源", "Thế Giới Chi Nguyên"),
        DictPair("米娅", "Mia"),
        DictPair("汉克", "Hank"),
        DictPair("欧卡", "Ouka"),
    )

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
    fun benchmarkAndAudit10Chapters() {
        val candidateDirs = listOf(
            File("test_chapters_1_to_10"),
            File("app/test_chapters_1_to_10"),
            File("d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/test_chapters_1_to_10"),
        )
        val chapterDir = candidateDirs.firstOrNull { it.exists() && it.listFiles()?.isNotEmpty() == true }
        assertTrue("Chapter directory must exist with chapter files", chapterDir != null)

        val outputDir = File("test_chapters_1_to_10_output").apply { mkdirs() }
        val repo = QuickTranslationRepository()

        // Warm up engine
        repo.translate(
            text = "苏晓身穿黑衣，手持长刀。",
            projectTerms = emptyList(),
            customPhonetics = emptyList(),
            pronounMode = null,
            beamWidth = 1,
        )

        val cjkRegex = Regex("""[\u3400-\u4DBF\u4E00-\u9FFF]""")
        val banPronounRegex = Regex("""(?iu)(?<!địch hay\s|kết\s|người\s|tình\s|làm\s)(?:\bcủa bạn\b|\bcác bạn\b|\bcho bạn\b|\bvì bạn\b|\bbạn đã\b|\bbạn có\b|\bbạn sẽ\b|\bbạn là\b|\bbạn không\b|\bbạn muốn\b|\bbạn đang\b|\bbạn phải\b|\bbạn hãy\b)""")
        val excessiveCuaRegex = Regex("""(?iu)\bcủa\s+(muốn|say rượu|phát hành|sống sót|bất hạnh|bất công|ngột ngạt|đáng ngờ|sụp đổ|phí sức|nổi bật|táo bạo|yêu thích|hi vọng|kỳ vọng)\b""")

        val allBanIssues = mutableListOf<String>()
        val allCjkIssues = mutableListOf<String>()
        val allCuaIssues = mutableListOf<String>()

        println("\n==========================================================================================")
        println("                 BENCHMARK & QUALITY AUDIT: 10 CHƯƠNG ĐẦU LUÂN HỒI NHẠC VIỆN               ")
        println("==========================================================================================")
        println(String.format("%-10s | %-8s | %-8s | %-10s | %-10s | %-10s | %-8s | %-6s",
            "Chương", "CJK Len", "VI Len", "Beam 1(ms)", "Beam 3(ms)", "Terms(ms)", "CJK Left", "Bạn?"))
        println("------------------------------------------------------------------------------------------")

        var totalCjkChars = 0
        var totalBeam1Time = 0L
        var totalBeam3Time = 0L
        var totalTermsTime = 0L
        var totalResidualCjk = 0

        for (ch in 1..10) {
            val fileName = String.format("chapter_%02d.txt", ch)
            val file = File(chapterDir, fileName)
            assertTrue("Chapter $ch file must exist: ${file.absolutePath}", file.exists())
            val rawText = file.readText(Charsets.UTF_8)
            totalCjkChars += rawText.length

            // 1. Beam Width 1 (Greedy / DP)
            val t0 = System.currentTimeMillis()
            val transBeam1 = repo.translate(
                text = rawText,
                projectTerms = emptyList(),
                customPhonetics = emptyList(),
                pronounMode = null,
                beamWidth = 1,
            )
            val timeBeam1 = System.currentTimeMillis() - t0
            totalBeam1Time += timeBeam1

            // 2. Beam Width 3 (Beam Search standard)
            val t1 = System.currentTimeMillis()
            val transBeam3 = repo.translate(
                text = rawText,
                projectTerms = emptyList(),
                customPhonetics = emptyList(),
                pronounMode = null,
                beamWidth = 3,
            )
            val timeBeam3 = System.currentTimeMillis() - t1
            totalBeam3Time += timeBeam3

            // 3. Beam Width 3 with Project Terms
            val t2 = System.currentTimeMillis()
            val transTerms = repo.translate(
                text = rawText,
                projectTerms = projectTerms,
                customPhonetics = emptyList(),
                pronounMode = null,
                beamWidth = 3,
            )
            val timeTerms = System.currentTimeMillis() - t2
            totalTermsTime += timeTerms

            // Save outputs
            File(outputDir, String.format("chapter_%02d_beam1.txt", ch)).writeText(transBeam1, Charsets.UTF_8)
            File(outputDir, String.format("chapter_%02d_beam3.txt", ch)).writeText(transBeam3, Charsets.UTF_8)
            File(outputDir, String.format("chapter_%02d_beam3_terms.txt", ch)).writeText(transTerms, Charsets.UTF_8)

            // Quality audit metrics
            val cjkMatches = cjkRegex.findAll(transTerms).map { it.value }.toList()
            val banMatches = banPronounRegex.findAll(transTerms).map { it.value }.toList()
            val cuaIssues = excessiveCuaRegex.findAll(transTerms).map { it.value }.toList()
            totalResidualCjk += cjkMatches.size

            if (cjkMatches.isNotEmpty()) {
                allCjkIssues.add("Chương $ch: ${cjkMatches.joinToString("")}")
            }
            if (banMatches.isNotEmpty()) {
                allBanIssues.add("Chương $ch: ${banMatches.joinToString(", ")}")
            }
            if (cuaIssues.isNotEmpty()) {
                allCuaIssues.add("Chương $ch: ${cuaIssues.joinToString(", ")}")
            }

            println(String.format("%-10s | %-8d | %-8d | %-10d | %-10d | %-10d | %-8d | %-6s",
                "Chương $ch", rawText.length, transTerms.length, timeBeam1, timeBeam3, timeTerms,
                cjkMatches.size, if (banMatches.isEmpty()) "0 (OK)" else "${banMatches.size} (!)"))

            if (cjkMatches.isNotEmpty()) {
                println("   -> [CJK Dư] Chương $ch: ${cjkMatches.take(10).joinToString(", ")}")
            }
            if (cuaIssues.isNotEmpty()) {
                println("   -> [Của Dư] Chương $ch: ${cuaIssues.take(5).joinToString(", ")}")
            }

            // Assertions for every chapter
            assertFalse("Output for chapter $ch must not be blank", transTerms.isBlank())
        }

        println("------------------------------------------------------------------------------------------")
        println(String.format("%-10s | %-8d | %-8s | %-10d | %-10d | %-10d | %-8d |",
            "TỔNG CỘNG", totalCjkChars, "-", totalBeam1Time, totalBeam3Time, totalTermsTime, totalResidualCjk))
        println("==========================================================================================")
        println("THỐNG KÊ TỐC ĐỘ:")
        println("  - Trung bình Beam 1: ${totalBeam1Time / 10} ms/chương (${totalCjkChars * 1000 / (totalBeam1Time.coerceAtLeast(1))} CJK chars/s)")
        println("  - Trung bình Beam 3: ${totalBeam3Time / 10} ms/chương (${totalCjkChars * 1000 / (totalBeam3Time.coerceAtLeast(1))} CJK chars/s)")
        println("==========================================================================================\n")

        assertTrue("Zero colloquial second-person pronouns in 10 chapters: $allBanIssues", allBanIssues.isEmpty())
        assertTrue("Zero residual CJK characters across all 10 chapters: $allCjkIssues", allCjkIssues.isEmpty())
    }
}
