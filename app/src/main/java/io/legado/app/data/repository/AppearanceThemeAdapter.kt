package io.legado.app.data.repository

import io.legado.app.constant.EventBus
import io.legado.app.domain.model.AppearanceProfile
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.domain.model.IconSlot
import io.legado.app.domain.model.wallpaperFor
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.postEvent

internal object AppearanceThemeAdapter {
    fun fromLegacy(): AppearanceProfile {
        val light = ThemeConfig.customThemeColors(isDark = false)
        val dark = ThemeConfig.customThemeColors(isDark = true)
        val fallback = io.legado.app.domain.model.AppearancePresets.fallback()
        return AppearanceProfile(
            id = LEGACY_PROFILE_ID,
            name = "Giao diện hiện tại",
            engine = io.legado.app.domain.model.AppearanceEngine.entries.firstOrNull {
                it.configValue == ThemeConfig.composeEngine
            } ?: io.legado.app.domain.model.AppearanceEngine.MATERIAL,
            themeMode = io.legado.app.domain.model.AppearanceThemeMode.entries.firstOrNull {
                it.configValue == ThemeConfig.themeMode
            } ?: io.legado.app.domain.model.AppearanceThemeMode.SYSTEM,
            lightColors = fallback.lightColors.copy(
                primary = light.primary.takeIf { it != 0 } ?: fallback.lightColors.primary,
                secondary = light.secondary.takeIf { it != 0 } ?: fallback.lightColors.secondary,
                primaryText = light.primaryText.takeIf { it != 0 } ?: fallback.lightColors.primaryText,
                secondaryText = light.secondaryText.takeIf { it != 0 }
                    ?: fallback.lightColors.secondaryText,
                background = light.background.takeIf { it != 0 } ?: fallback.lightColors.background,
                container = light.labelContainer.takeIf { it != 0 } ?: fallback.lightColors.container,
            ),
            darkColors = fallback.darkColors.copy(
                primary = dark.primary.takeIf { it != 0 } ?: fallback.darkColors.primary,
                secondary = dark.secondary.takeIf { it != 0 } ?: fallback.darkColors.secondary,
                primaryText = dark.primaryText.takeIf { it != 0 } ?: fallback.darkColors.primaryText,
                secondaryText = dark.secondaryText.takeIf { it != 0 }
                    ?: fallback.darkColors.secondaryText,
                background = dark.background.takeIf { it != 0 } ?: fallback.darkColors.background,
                container = dark.labelContainer.takeIf { it != 0 } ?: fallback.darkColors.container,
            ),
            fontScale = ThemeConfig.fontScale,
            containerOpacity = ThemeConfig.containerOpacity,
            topBarOpacity = ThemeConfig.topBarOpacity,
            bottomBarOpacity = ThemeConfig.bottomBarOpacity,
            blurEnabled = ThemeConfig.enableBlur,
            progressiveBlurEnabled = ThemeConfig.enableProgressiveBlur,
            iconSlots = buildMap {
                putLegacyIcon(IconSlot.NAV_HOME, ThemeConfig.navIconHome)
                putLegacyIcon(IconSlot.NAV_BOOKSHELF, ThemeConfig.navIconBookshelf)
                putLegacyIcon(IconSlot.NAV_EXPLORE, ThemeConfig.navIconExplore)
                putLegacyIcon(IconSlot.NAV_WORKSPACE, ThemeConfig.navIconWorkspace)
                putLegacyIcon(IconSlot.NAV_MY, ThemeConfig.navIconMy)
            },
            lightWallpapers = legacyWallpaper(ThemeConfig.bgImageLight, ThemeConfig.bgImageBlurring),
            darkWallpapers = legacyWallpaper(ThemeConfig.bgImageDark, ThemeConfig.bgImageNBlurring),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun apply(
        profile: AppearanceProfile,
        resolveAsset: (String, String?) -> String?,
    ) {
        ThemeConfig.composeEngine = profile.engine.configValue
        ThemeConfig.themeMode = profile.themeMode.configValue
        ThemeConfig.appTheme = "12"
        ThemeConfig.enableDeepPersonalization = true
        ThemeConfig.cPrimary = profile.lightColors.primary
        ThemeConfig.cNPrimary = profile.darkColors.primary
        ThemeConfig.themeColor = profile.lightColors.primary
        ThemeConfig.secondaryThemeColor = profile.lightColors.secondary
        ThemeConfig.primaryTextColor = profile.lightColors.primaryText
        ThemeConfig.secondaryTextColor = profile.lightColors.secondaryText
        ThemeConfig.themeBackgroundColor = profile.lightColors.background
        ThemeConfig.labelContainerColor = profile.lightColors.container
        ThemeConfig.themeColorNight = profile.darkColors.primary
        ThemeConfig.secondaryThemeColorNight = profile.darkColors.secondary
        ThemeConfig.primaryTextColorNight = profile.darkColors.primaryText
        ThemeConfig.secondaryTextColorNight = profile.darkColors.secondaryText
        ThemeConfig.themeBackgroundColorNight = profile.darkColors.background
        ThemeConfig.labelContainerColorNight = profile.darkColors.container
        ThemeConfig.fontScale = profile.fontScale.coerceIn(8, 15)
        ThemeConfig.containerOpacity = profile.containerOpacity.coerceIn(0, 100)
        ThemeConfig.topBarOpacity = profile.topBarOpacity.coerceIn(0, 100)
        ThemeConfig.bottomBarOpacity = profile.bottomBarOpacity.coerceIn(0, 100)
        ThemeConfig.enableBlur = profile.blurEnabled
        ThemeConfig.enableProgressiveBlur = profile.progressiveBlurEnabled

        ThemeConfig.navIconHome = profile.iconPath(IconSlot.NAV_HOME, resolveAsset)
        ThemeConfig.navIconBookshelf = profile.iconPath(IconSlot.NAV_BOOKSHELF, resolveAsset)
        ThemeConfig.navIconExplore = profile.iconPath(IconSlot.NAV_EXPLORE, resolveAsset)
        ThemeConfig.navIconWorkspace = profile.iconPath(IconSlot.NAV_WORKSPACE, resolveAsset)
        ThemeConfig.navIconMy = profile.iconPath(IconSlot.NAV_MY, resolveAsset)

        profile.wallpaperFor(AppearanceTarget.GLOBAL, dark = false).let { wallpaper ->
            ThemeConfig.bgImageLight = wallpaper?.let {
                resolveAsset(it.assetId, it.legacyLocation)
            }
            ThemeConfig.bgImageBlurring = wallpaper?.blurDp?.coerceIn(0, 50) ?: 0
        }
        profile.wallpaperFor(AppearanceTarget.GLOBAL, dark = true).let { wallpaper ->
            ThemeConfig.bgImageDark = wallpaper?.let {
                resolveAsset(it.assetId, it.legacyLocation)
            }
            ThemeConfig.bgImageNBlurring = wallpaper?.blurDp?.coerceIn(0, 50) ?: 0
        }
        postEvent(EventBus.RECREATE, "")
    }

    private fun AppearanceProfile.iconPath(
        slot: IconSlot,
        resolveAsset: (String, String?) -> String?,
    ): String {
        val spec = iconSlots[slot.key] ?: return ""
        spec.bundledIcon?.let { return "bundled://$it" }
        return resolveAsset(spec.assetId, spec.legacyLocation).orEmpty()
    }

    private fun MutableMap<String, io.legado.app.domain.model.AppearanceIconSpec>.putLegacyIcon(
        slot: IconSlot,
        path: String,
    ) {
        if (path.isNotBlank()) {
            put(slot.key, io.legado.app.domain.model.AppearanceIconSpec(legacyLocation = path))
        }
    }

    private fun legacyWallpaper(
        path: String?,
        blur: Int,
    ): Map<String, io.legado.app.domain.model.AppearanceWallpaperSpec> =
        if (path.isNullOrBlank()) {
            emptyMap()
        } else {
            mapOf(
                AppearanceTarget.GLOBAL.key to
                    io.legado.app.domain.model.AppearanceWallpaperSpec(
                        legacyLocation = path,
                        blurDp = blur,
                    )
            )
        }

    const val LEGACY_PROFILE_ID = "migrated-current"
}
