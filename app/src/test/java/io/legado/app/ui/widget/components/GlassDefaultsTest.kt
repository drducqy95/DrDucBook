package io.legado.app.ui.widget.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassDefaultsTest {

    @Test
    fun customSecondaryTintsStructuralSurfaceWithoutReplacingIt() {
        val surface = Color(0xFFF7F9FA)
        val accent = Color(0xFF0B7171)

        val actual = GlassDefaults.harmonizedSurfaceColor(surface, accent)

        assertNotEquals(surface, actual)
        assertNotEquals(accent, actual)
        assertTrue(colorDistance(surface, actual) < colorDistance(actual, accent) / 4)
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
