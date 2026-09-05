package io.legado.app

import android.app.Application
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.ReaderContentMode
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.translationCacheIdentity
import io.legado.app.domain.usecase.toTitleCase
import io.legado.app.ui.config.translation.TranslationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BugFixVerificationPhase2Test {

    @Before
    fun setUpAppCtx() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
    }

    @Test
    fun testTitleCaseCapitalization() {
        assertEquals("Thiên Tằm Thổ Đậu", "thiên tằm thổ đậu".toTitleCase())
        assertEquals("Chương 100: Sức Mạnh Trời Đất", "chương 100: sức mạnh trời đất".toTitleCase())
        assertEquals("Uchiha Sasuke", "uchiha sasuke".toTitleCase())
        assertEquals("Đường Gia Tam Thiếu", "đường gia tam thiếu".toTitleCase())
        assertEquals("", "".toTitleCase())
    }

    @Test
    fun testPhase1_cleanTsvRulesForPhase2() {
        val rulePositionCua = Regex("""(?U)\bcủa\s+(đối diện|bên cạnh|gần đây|xung quanh|phía trước|phía sau|bên trái|bên phải)\b""")
        val ruleStartCuaV2 = Regex("""(?U)(?:^|\n)\s*[Cc]ủa\s+([\p{L}\p{M}]+(?:\s+[\p{L}\p{M}]+){0,2})\s+(trên|dưới|trong|ngoài|ở)\b""")
        val ruleDesignCua = Regex("""(?U)\b(thiết kế|mẫu|hình|kiểu|loại)\s+của\s+([\p{L}\p{M}]+)\b""")
        val ruleAgeCua = Regex("""(?U)\b(\d+)\s+tuổi\s+của\s+([\p{L}\p{M}]+(?:\s+[\p{L}\p{M}]+){0,3})\b""")
        val rulePastCua = Regex("""(?U)\bcủa\s+(đã\s+từng|từng)\s+([\p{L}\p{M}]+)\b""")
        val ruleAbstractCuaV2 = Regex("""(?U)\b(kết quả|hậu quả|nguyên nhân|quá trình|nội dung|mục đích|ý nghĩa)\s+của\s+(giải thích|phân tích|nghiên cứu|điều tra|giáo dục|đào tạo|huấn luyện)\b""")

        // 1. Spurious "của" before position words: "thiếu niên của đối diện" -> "thiếu niên đối diện"
        val s1 = "thiếu niên của đối diện đang ngồi yên lặng."
        val r1 = s1.replace(rulePositionCua, "$1")
        assertEquals("thiếu niên đối diện đang ngồi yên lặng.", r1)

        // 2. Sentence start "Của mang trên mặt" -> "mang trên mặt"
        val s2 = "Của mang trên mặt nụ cười ấm áp."
        val r2 = s2.replace(ruleStartCuaV2, "$1 $2")
        assertEquals("mang trên mặt nụ cười ấm áp.", r2)

        // 3. Design/pattern: "thiết kế của chồn sóc" -> "thiết kế chồn sóc"
        val s3 = "phía trên có thiết kế của chồn sóc rất đẹp."
        val r3 = s3.replace(ruleDesignCua, "$1 $2")
        assertEquals("phía trên có thiết kế chồn sóc rất đẹp.", r3)

        // 4. Age modifier reordering: "12 tuổi của ký ức" -> "ký ức lúc 12 tuổi"
        val s4 = "liên quan đến 12 tuổi của ký ức."
        val r4 = s4.replace(ruleAgeCua, "$2 lúc $1 tuổi")
        assertEquals("liên quan đến ký ức lúc 12 tuổi.", r4)

        // 5. Past tense: "của đã từng làm" -> "đã từng làm"
        val s5 = "những người của đã từng làm tổn thương bạn."
        val r5 = s5.replace(rulePastCua, "$1 $2")
        assertEquals("những người đã từng làm tổn thương bạn.", r5)

        // 6. Abstract noun: "mục đích của nghiên cứu" -> "mục đích nghiên cứu"
        val s6 = "hiểu rõ mục đích của nghiên cứu khoa học này."
        val r6 = s6.replace(ruleAbstractCuaV2, "$1 $2")
        assertEquals("hiểu rõ mục đích nghiên cứu khoa học này.", r6)
    }

    @Test
    fun testPhase3_languageDetectionFromSampleText() {
        val book = Book()

        // Chinese text sample
        val chineseSample = "这是一个测试文本，用于验证语言检测功能。"
        val langZh = book.detectAndCacheSourceLanguage(chineseSample)
        assertEquals("zh", langZh)
        assertFalse(book.isVietnameseSource())

        // Vietnamese text sample
        val bookVi = Book()
        val vietnameseSample = "Đây là một đoạn văn bản tiếng Việt có dấu đầy đủ để kiểm tra."
        val langVi = bookVi.detectAndCacheSourceLanguage(vietnameseSample)
        assertEquals("vi", langVi)
        assertTrue(bookVi.isVietnameseSource())
    }

    @Test
    fun testPhase4_rewriteCacheIdentity() {
        val rewriteIdentity = ReaderContentMode.REWRITE.translationCacheIdentity(TranslationConstants.TARGET_VIETNAMESE)
        assertEquals(TranslationConstants.PROVIDER_REWRITE, rewriteIdentity?.provider)
        assertEquals(TranslationConstants.TARGET_VIETNAMESE, rewriteIdentity?.targetLanguage)
    }

    @Test
    fun testPhase4_autoRewriteConfigDefaults() {
        assertFalse(TranslationConfig.autoRewriteEnabled)
        assertEquals(3, TranslationConfig.autoRewriteNextChapters)
    }
}
