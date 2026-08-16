package io.legado.app.ui.main

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainResponsiveNavigationTest {

    @Test
    fun automaticModeUsesRailAtExpandedWidth() {
        assertTrue(
            resolveUseNavigationRail(
                tabletInterface = "auto",
                orientation = Configuration.ORIENTATION_PORTRAIT,
                smallestWidthDp = 600,
            )
        )
    }

    @Test
    fun automaticModeUsesBottomBarAtCompactWidth() {
        assertFalse(
            resolveUseNavigationRail(
                tabletInterface = "auto",
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                smallestWidthDp = 599,
            )
        )
    }

    @Test
    fun landscapePreferenceDoesNotDependOnDeviceClass() {
        assertTrue(
            resolveUseNavigationRail(
                tabletInterface = "landscape",
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                smallestWidthDp = 360,
            )
        )
    }
}
