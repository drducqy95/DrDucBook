package io.legado.app.help

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FontLoaderTest {

    @Test
    fun bundledVietnameseFontsAreAvailableWithoutChoosingAFolder() {
        val fonts = loadFontFiles(RuntimeEnvironment.getApplication(), null)
        val bundled = fonts.filter { it.name in expectedNames }

        assertEquals(expectedNames, bundled.map { it.name }.toSet())
        assertTrue(bundled.all { it.size > 100_000L })
    }

    private companion object {
        val expectedNames = setOf(
            "BeVietnamPro-Regular.ttf",
            "Literata-Variable.ttf",
            "NotoSans-Variable.ttf",
            "NotoSerif-Variable.ttf",
        )
    }
}
