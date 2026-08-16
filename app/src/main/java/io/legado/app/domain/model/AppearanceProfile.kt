package io.legado.app.domain.model

const val APPEARANCE_SCHEMA_VERSION = 1

enum class AppearanceEngine(val configValue: String) {
    MATERIAL("material"),
    MIUIX("miuix"),
}

enum class AppearanceThemeMode(val configValue: String) {
    SYSTEM("0"),
    LIGHT("1"),
    DARK("2"),
}

enum class AppearanceTarget(val key: String) {
    GLOBAL("global"),
    HOME("home"),
    BOOKSHELF("bookshelf"),
    WORKSPACE("workspace"),
    AGENT("agent"),
    AUTHORING("authoring"),
    EBOOK("ebook"),
    READER("reader"),
}

enum class WallpaperFit {
    COVER,
    CONTAIN,
}

enum class WallpaperAlignment {
    START,
    CENTER,
    END,
}

enum class AppearanceAssetKind {
    ICON,
    WALLPAPER,
    FONT,
}

data class AppearanceColors(
    val primary: Int,
    val secondary: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val background: Int,
    val container: Int,
)

data class AppearanceIconSpec(
    val assetId: String = "",
    val legacyLocation: String? = null,
    val bundledIcon: String? = null,
    val scale: Float = 1f,
    val paddingPercent: Int = 0,
    val tintColor: Int? = null,
    val backgroundColor: Int? = null,
)

data class AppearanceWallpaperSpec(
    val assetId: String = "",
    val legacyLocation: String? = null,
    val fit: WallpaperFit = WallpaperFit.COVER,
    val horizontalAlignment: WallpaperAlignment = WallpaperAlignment.CENTER,
    val verticalAlignment: WallpaperAlignment = WallpaperAlignment.CENTER,
    val opacityPercent: Int = 100,
    val blurDp: Int = 0,
    val overlayColor: Int? = null,
    val dimPercent: Int = 0,
)

data class AppearanceProfile(
    val schemaVersion: Int = APPEARANCE_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val builtIn: Boolean = false,
    val engine: AppearanceEngine = AppearanceEngine.MATERIAL,
    val themeMode: AppearanceThemeMode = AppearanceThemeMode.SYSTEM,
    val lightColors: AppearanceColors,
    val darkColors: AppearanceColors,
    val fontScale: Int = 10,
    val containerOpacity: Int = 100,
    val topBarOpacity: Int = 100,
    val bottomBarOpacity: Int = 100,
    val blurEnabled: Boolean = false,
    val progressiveBlurEnabled: Boolean = false,
    val iconSlots: Map<String, AppearanceIconSpec> = emptyMap(),
    val lightWallpapers: Map<String, AppearanceWallpaperSpec> = emptyMap(),
    val darkWallpapers: Map<String, AppearanceWallpaperSpec> = emptyMap(),
    val updatedAt: Long = 0L,
)

data class AppearanceState(
    val activeProfileId: String,
    val profiles: List<AppearanceProfile>,
    val revision: Long = 0L,
) {
    val activeProfile: AppearanceProfile
        get() = profiles.firstOrNull { it.id == activeProfileId }
            ?: profiles.first()
}

data class AppearanceSnapshot(
    val schemaVersion: Int = APPEARANCE_SCHEMA_VERSION,
    val activeProfileId: String,
    val profiles: List<AppearanceProfile>,
)

object AppearancePresets {
    const val COPPER_CYAN_ID = "builtin-copper-cyan"
    const val FOREST_CORAL_ID = "builtin-forest-coral"
    const val INK_AMBER_ID = "builtin-ink-amber"

    val all: List<AppearanceProfile> = listOf(
        AppearanceProfile(
            id = COPPER_CYAN_ID,
            name = "Dong & Lam",
            builtIn = true,
            lightColors = AppearanceColors(
                primary = argb("8A3E1B"),
                secondary = argb("006A6A"),
                primaryText = argb("211A17"),
                secondaryText = argb("55443D"),
                background = argb("F7F9FA"),
                container = argb("DDEDEA"),
            ),
            darkColors = AppearanceColors(
                primary = argb("FFB68F"),
                secondary = argb("80D5D1"),
                primaryText = argb("F6EFEB"),
                secondaryText = argb("D9C3B8"),
                background = argb("151413"),
                container = argb("203937"),
            ),
            blurEnabled = true,
            topBarOpacity = 88,
            bottomBarOpacity = 90,
        ),
        AppearanceProfile(
            id = FOREST_CORAL_ID,
            name = "Rung & San ho",
            builtIn = true,
            lightColors = AppearanceColors(
                primary = argb("216B57"),
                secondary = argb("984359"),
                primaryText = argb("17201D"),
                secondaryText = argb("46534F"),
                background = argb("F7FAF8"),
                container = argb("DCEFE8"),
            ),
            darkColors = AppearanceColors(
                primary = argb("83D5B5"),
                secondary = argb("FFB1C0"),
                primaryText = argb("EEF5F1"),
                secondaryText = argb("BECBC5"),
                background = argb("101614"),
                container = argb("293B36"),
            ),
            containerOpacity = 96,
            topBarOpacity = 96,
            bottomBarOpacity = 96,
        ),
        AppearanceProfile(
            id = INK_AMBER_ID,
            name = "Muc & Ho phach",
            builtIn = true,
            lightColors = AppearanceColors(
                primary = argb("394D8F"),
                secondary = argb("925800"),
                primaryText = argb("191B22"),
                secondaryText = argb("494B56"),
                background = argb("F8F9FD"),
                container = argb("E1E7FA"),
            ),
            darkColors = AppearanceColors(
                primary = argb("BAC6FF"),
                secondary = argb("FFB95F"),
                primaryText = argb("F3F1F6"),
                secondaryText = argb("C7C5CF"),
                background = argb("101116"),
                container = argb("282D3F"),
            ),
            blurEnabled = true,
            progressiveBlurEnabled = true,
            topBarOpacity = 84,
            bottomBarOpacity = 88,
        ),
    )

    fun fallback(): AppearanceProfile = all.first()

    private fun argb(rgb: String): Int = ("FF$rgb").toLong(16).toInt()
}

fun AppearanceProfile.wallpaperFor(
    target: AppearanceTarget,
    dark: Boolean,
): AppearanceWallpaperSpec? {
    val wallpapers = if (dark) darkWallpapers else lightWallpapers
    return wallpapers[target.key] ?: wallpapers[AppearanceTarget.GLOBAL.key]
}

