package io.legado.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateDynamicUiTextUseCaseTest {

    @Test
    fun detectsChineseTextThatNeedsQuickTranslation() {
        assertTrue("第1章 地下车库".containsCjk())
        assertTrue("24,7万字".containsCjk())
    }

    @Test
    fun leavesVietnameseAndNumericLabelsUntouched() {
        assertFalse("Chương 1".containsCjk())
        assertFalse("24,7 triệu chữ".containsCjk())
    }
}
