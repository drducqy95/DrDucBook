package io.legado.app.help.book

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BookIntroFormatterTest {

    @Test
    fun formatsCrammedMetadataIntroIntoSeparatedSections() {
        val raw = "📖Sách ID:53485 🗳️Thời điểm hiện tại: 2026-03-31 🔥Nhiệt độ: 1421.37 vạn 📁Chuyên mục: 52 Thư khố >> Ngôn tình tiểu thuyết 🏷️Nhãn: Xuyên không Hệ thống Hiện đại Hào môn 🕰️Thời gian đổi mới: 2026-03-31 📜Tóm tắt: [Vạn giới giao dịch hệ thống + Thần hào + Nữ cường] Vân Tử Khâm ngoài ý muốn khóa lại vạn giới hệ thống... 🔻Quận bình luận về: Truyện đọc rất cuốn!"

        val formatted = BookIntroFormatter.format(raw)

        // Metadata tags should be extracted
        assertTrue(formatted.metadataTags.size >= 5)
        assertTrue(formatted.metadataTags.any { it.contains("Sách ID") })
        assertTrue(formatted.metadataTags.any { it.contains("Nhiệt độ") })
        assertTrue(formatted.metadataTags.any { it.contains("Chuyên mục") })

        // Synopsis should be clean text
        assertTrue(formatted.synopsis.contains("Vân Tử Khâm ngoài ý muốn khóa lại"))

        // Notes should contain the review section
        assertTrue(formatted.notes.contains("Quận bình luận về"))

        // Full formatted text should have clean newlines separating the tags
        assertTrue(formatted.fullFormattedText.contains("\n"))
    }

    @Test
    fun preservesNormalCleanParagraphIntros() {
        val raw = "<p>Đây là một câu chuyện tình yêu lãng mạn.</p><p>Nam chính và nữ chính gặp nhau tại trường đại học.</p>"
        val formatted = BookIntroFormatter.format(raw)

        assertEquals(0, formatted.metadataTags.size)
        assertTrue(formatted.fullFormattedText.contains("Đây là một câu chuyện tình yêu lãng mạn."))
        assertTrue(formatted.fullFormattedText.contains("Nam chính và nữ chính gặp nhau"))
    }
}
