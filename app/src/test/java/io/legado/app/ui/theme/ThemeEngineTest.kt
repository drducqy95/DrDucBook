package io.legado.app.ui.theme

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ThemeEngineTest {

    @Test
    fun amoledThemeAppliesCoherentSurfaceRamp() {
        val scheme = ThemeEngine.getColorScheme(
            context = RuntimeEnvironment.getApplication(),
            mode = AppThemeMode.WH,
            darkTheme = true,
            isAmoled = true,
            paletteStyle = null,
        )

        assertEquals(Color.Black, scheme.surface)
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surfaceContainerLowest)
        assertEquals(Color(0xFF060606), scheme.surfaceContainerLow)
        assertEquals(Color(0xFF0D0D0D), scheme.surfaceContainer)
        assertEquals(Color(0xFF141414), scheme.surfaceContainerHigh)
        assertEquals(Color(0xFF1B1B1B), scheme.surfaceContainerHighest)
    }

    @Test
    fun transparentDarkThemeUsesBlackContainerRamp() {
        val scheme = ThemeEngine.getColorScheme(
            context = RuntimeEnvironment.getApplication(),
            mode = AppThemeMode.Transparent,
            darkTheme = true,
            isAmoled = false,
            paletteStyle = null,
        )

        assertEquals(Color.Transparent, scheme.surface)
        assertEquals(Color.Transparent, scheme.background)
        assertEquals(Color.Transparent, scheme.surfaceContainerLowest)
        assertEquals(Color(0x26000000), scheme.surfaceContainerLow)
        assertEquals(Color(0x3D000000), scheme.surfaceContainer)
        assertEquals(Color(0x52000000), scheme.surfaceContainerHigh)
        assertEquals(Color(0x66000000), scheme.surfaceContainerHighest)
    }

    @Test
    fun transparentLightThemeUsesWhiteContainerRamp() {
        val scheme = ThemeEngine.getColorScheme(
            context = RuntimeEnvironment.getApplication(),
            mode = AppThemeMode.Transparent,
            darkTheme = false,
            isAmoled = false,
            paletteStyle = null,
        )

        assertEquals(Color.Transparent, scheme.surface)
        assertEquals(Color.Transparent, scheme.background)
        assertEquals(Color.Transparent, scheme.surfaceContainerLowest)
        assertEquals(Color(0x1FFFFFFF), scheme.surfaceContainerLow)
        assertEquals(Color(0x2EFFFFFF), scheme.surfaceContainer)
        assertEquals(Color(0x47FFFFFF), scheme.surfaceContainerHigh)
        assertEquals(Color(0x5CFFFFFF), scheme.surfaceContainerHighest)
    }

    @Test
    fun customThemeKeepsSurfaceContainersInSoftRamp() {
        val background = Color(0xFFFEF7FF)
        val labelContainer = Color(0xFFA8E7DF)
        val scheme = generateColorScheme(
            UserColorPalette(
                primaryColor = Color(0xFF006A60),
                secondaryColor = Color(0xFF4A635F),
                backgroundColor = background,
                primaryFontColor = Color(0xFF171D1B),
                secondaryFontColor = Color(0xFF3F4946),
                labelContainerColor = labelContainer,
            ),
            isDark = false
        )

        assertEquals(background, scheme.surface)
        assertEquals(background, scheme.surfaceContainerLowest)
        assertNotEquals(labelContainer, scheme.surfaceContainerLow)
        assertNotEquals(background, scheme.surfaceContainer)
        assertTrue(
            colorDistance(background, scheme.surfaceContainerLow) <
                colorDistance(background, labelContainer) / 3
        )
        assertTrue(
            colorDistance(scheme.surfaceContainerLow, scheme.surfaceContainer) <= 16
        )
        assertTrue(
            colorDistance(scheme.surfaceContainer, scheme.surfaceContainerHigh) <= 16
        )
    }

    private fun colorDistance(first: Color, second: Color): Int {
        val firstArgb = first.toArgb()
        val secondArgb = second.toArgb()
        return kotlin.math.abs(firstArgb.red - secondArgb.red) +
            kotlin.math.abs(firstArgb.green - secondArgb.green) +
            kotlin.math.abs(firstArgb.blue - secondArgb.blue)
    }

    private val Int.red: Int get() = this shr 16 and 0xFF
    private val Int.green: Int get() = this shr 8 and 0xFF
    private val Int.blue: Int get() = this and 0xFF
}
