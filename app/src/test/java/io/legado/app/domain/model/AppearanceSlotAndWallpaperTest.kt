package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppearanceSlotAndWallpaperTest {

    @Test
    fun iconRegistryHasStableUniqueCoverageForEveryGroup() {
        assertEquals(IconSlot.entries.size, IconSlotRegistry.all.map { it.key }.distinct().size)
        IconSlotGroup.entries.forEach { group ->
            assertNotNull(IconSlotRegistry.all.firstOrNull { it.group == group })
        }
        IconSlotRegistry.all.forEach { slot ->
            assertSame(slot, IconSlotRegistry.fromKey(slot.key))
        }
    }

    @Test
    fun moduleWallpaperOverridesGlobalAndMissingThemeFallsBack() {
        val global = AppearanceWallpaperSpec(assetId = "global.png")
        val workspace = AppearanceWallpaperSpec(assetId = "workspace.png")
        val profile = AppearancePresets.fallback().copy(
            lightWallpapers = mapOf(
                AppearanceTarget.GLOBAL.key to global,
                AppearanceTarget.WORKSPACE.key to workspace,
            )
        )

        assertEquals(workspace, profile.wallpaperFor(AppearanceTarget.WORKSPACE, dark = false))
        assertEquals(global, profile.wallpaperFor(AppearanceTarget.HOME, dark = false))
        assertNull(profile.wallpaperFor(AppearanceTarget.HOME, dark = true))
    }
}
