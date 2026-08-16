package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class AppearancePresetsTest {

    @Test
    fun threeBuiltInThemesHaveStableDistinctContracts() {
        val presets = AppearancePresets.all

        assertEquals(3, presets.size)
        assertEquals(3, presets.map { it.id }.distinct().size)
        assertTrue(presets.all { it.builtIn })
        assertTrue(presets.all { it.schemaVersion == APPEARANCE_SCHEMA_VERSION })
        assertEquals(3, presets.map { it.lightColors.primary }.distinct().size)
        assertEquals(3, presets.map { it.darkColors.secondary }.distinct().size)
    }

    @Test
    fun builtInThemesKeepReadablePrimaryText() {
        AppearancePresets.all.forEach { profile ->
            assertTrue(contrast(profile.lightColors.primaryText, profile.lightColors.background) >= 4.5)
            assertTrue(contrast(profile.darkColors.primaryText, profile.darkColors.background) >= 4.5)
        }
    }

    private fun contrast(first: Int, second: Int): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}

