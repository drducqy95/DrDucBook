package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Test

class VietnameseProsePunctuationTest {

    @Test
    fun normalizeVietnameseProsePunctuation_fixesMissingSpacesAfterPunctuation() {
        val input = "Nguyệt, xin lỗi em, anh không giúp được em báo thù...Nhìn trước mắt Dị Ma Chi Vương bổ xuống đạo kiếm quang ngập trời kia, Quân Hằng mỉm cười như trút được gánh nặng.Một giây sau, hư không vỡ vụn, kiếm quang lướt qua thân thể, suy nghĩ của hắn mãi mãi dừng lại....\"Vẫn chưa chịu dậy à?!\"\"Em định ngủ đến bao giờ? « Dị Vực Phủ Xuống » sắp open rồi đấy!\"Một giọng nói vô cùng quen thuộc vang lên bên tai, kéo Quân Hằng bừng tỉnh khỏi cơn ác mộng.« Dị Vực Phủ Xuống » open?!Trong thoáng chốc mở mắt ra, đập vào mắt là căn phòng của hắn và Giang Minh Nguyệt.Chuyện gì đang xảy ra vậy?Rõ ràng ta... đã bị Dị Ma Chi Vương chém thành hai đoạn.Quân Hằng ngơ ngác nhìn quanh, cho đến khi nhìn thấy chiếc lịch điện tử lơ lửng trên tường.2043       năm        ngày30"
        val expected = "Nguyệt, xin lỗi em, anh không giúp được em báo thù... Nhìn trước mắt Dị Ma Chi Vương bổ xuống đạo kiếm quang ngập trời kia, Quân Hằng mỉm cười như trút được gánh nặng. Một giây sau, hư không vỡ vụn, kiếm quang lướt qua thân thể, suy nghĩ của hắn mãi mãi dừng lại.... \"Vẫn chưa chịu dậy à?!\" \"Em định ngủ đến bao giờ? « Dị Vực Phủ Xuống » sắp open rồi đấy!\" Một giọng nói vô cùng quen thuộc vang lên bên tai, kéo Quân Hằng bừng tỉnh khỏi cơn ác mộng. « Dị Vực Phủ Xuống » open?! Trong thoáng chốc mở mắt ra, đập vào mắt là căn phòng của hắn và Giang Minh Nguyệt. Chuyện gì đang xảy ra vậy? Rõ ràng ta... đã bị Dị Ma Chi Vương chém thành hai đoạn. Quân Hằng ngơ ngác nhìn quanh, cho đến khi nhìn thấy chiếc lịch điện tử lơ lửng trên tường. 2043       năm        ngày30"
        
        val actual = normalizeVietnameseProsePunctuation(input)
        assertEquals(expected, actual)
    }

    @Test
    fun normalizeVietnameseProsePunctuation_preservesParagraphBreaks() {
        val input = "Đoạn một.\n\nĐoạn hai.\n\nĐoạn ba."
        val actual = normalizeVietnameseProsePunctuation(input)
        assertEquals(input, actual)
    }

    @Test
    fun normalizeVietnameseProsePunctuation_doesNotSplitDecimals() {
        val input = "Tỷ lệ 3.14 và phiên bản 2.0 hoạt động tốt."
        val actual = normalizeVietnameseProsePunctuation(input)
        assertEquals(input, actual)
    }
}
