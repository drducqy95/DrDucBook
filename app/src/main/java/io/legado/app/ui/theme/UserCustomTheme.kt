package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// 用户自定义的颜色集合
data class UserColorPalette(
    val primaryColor: Color,        // 用户定义的主题色
    val secondaryColor: Color,      // 用户定义的次要主题色
    val backgroundColor: Color,     // 用户定义的背景色
    val primaryFontColor: Color,    // 用户定义的主要字体色
    val secondaryFontColor: Color,  // 用户定义的次要字体色
    val labelContainerColor: Color  // 用户定义的标签容器色
)

// 将用户自定义颜色映射到 Material Theme 的 ColorScheme
fun generateColorScheme(userPalette: UserColorPalette, isDark: Boolean): ColorScheme {
    val surfaceRamp = userSurfaceRamp(userPalette, isDark)
    return if (isDark) {
        darkColorScheme(
            primary = userPalette.primaryColor,
            onPrimary = userPalette.primaryFontColor,
            primaryContainer = userPalette.labelContainerColor,
            onPrimaryContainer = userPalette.primaryFontColor,

            secondary = userPalette.secondaryColor,
            onSecondary = userPalette.secondaryFontColor,
            secondaryContainer = userPalette.labelContainerColor,
            onSecondaryContainer = userPalette.secondaryFontColor,

            tertiary = userPalette.secondaryColor,
            onTertiary = userPalette.secondaryFontColor,

            background = surfaceRamp.base,
            surface = surfaceRamp.base,
            onBackground = userPalette.primaryFontColor,
            onSurface = userPalette.primaryFontColor,
            surfaceVariant = userPalette.labelContainerColor,
            onSurfaceVariant = userPalette.secondaryFontColor,

            // 其他颜色
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFC4C7C5),
            scrim = Color(0xFF000000),

            // 表面颜色变体
            surfaceBright = surfaceRamp.bright,
            surfaceDim = surfaceRamp.dim,
            surfaceContainer = surfaceRamp.container,
            surfaceContainerHigh = surfaceRamp.high,
            surfaceContainerHighest = surfaceRamp.highest,
            surfaceContainerLow = surfaceRamp.low,
            surfaceContainerLowest = surfaceRamp.lowest,

            // 固定颜色
            primaryFixed = userPalette.primaryColor,
            primaryFixedDim = userPalette.primaryColor.copy(alpha = 0.8f),
            onPrimaryFixed = userPalette.primaryFontColor,
            onPrimaryFixedVariant = userPalette.primaryFontColor,
            secondaryFixed = userPalette.secondaryColor,
            secondaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onSecondaryFixed = userPalette.secondaryFontColor,
            onSecondaryFixedVariant = userPalette.secondaryFontColor,
            tertiaryFixed = userPalette.secondaryColor,
            tertiaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onTertiaryFixed = userPalette.secondaryFontColor,
            onTertiaryFixedVariant = userPalette.secondaryFontColor
        )
    } else {
        lightColorScheme(
            primary = userPalette.primaryColor,
            onPrimary = userPalette.primaryFontColor,
            primaryContainer = userPalette.labelContainerColor,
            onPrimaryContainer = userPalette.primaryFontColor,

            secondary = userPalette.secondaryColor,
            onSecondary = userPalette.secondaryFontColor,
            secondaryContainer = userPalette.labelContainerColor,
            onSecondaryContainer = userPalette.secondaryFontColor,

            tertiary = userPalette.secondaryColor,
            onTertiary = userPalette.secondaryFontColor,

            background = surfaceRamp.base,
            surface = surfaceRamp.base,
            onBackground = userPalette.primaryFontColor,
            onSurface = userPalette.primaryFontColor,
            surfaceVariant = userPalette.labelContainerColor,
            onSurfaceVariant = userPalette.secondaryFontColor,

            // 其他颜色
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFC4C7C5),
            scrim = Color(0xFF000000),

            // 表面颜色变体
            surfaceBright = surfaceRamp.bright,
            surfaceDim = surfaceRamp.dim,
            surfaceContainer = surfaceRamp.container,
            surfaceContainerHigh = surfaceRamp.high,
            surfaceContainerHighest = surfaceRamp.highest,
            surfaceContainerLow = surfaceRamp.low,
            surfaceContainerLowest = surfaceRamp.lowest,

            // 固定颜色
            primaryFixed = userPalette.primaryColor,
            primaryFixedDim = userPalette.primaryColor.copy(alpha = 0.8f),
            onPrimaryFixed = userPalette.primaryFontColor,
            onPrimaryFixedVariant = userPalette.primaryFontColor,
            secondaryFixed = userPalette.secondaryColor,
            secondaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onSecondaryFixed = userPalette.secondaryFontColor,
            onSecondaryFixedVariant = userPalette.secondaryFontColor,
            tertiaryFixed = userPalette.secondaryColor,
            tertiaryFixedDim = userPalette.secondaryColor.copy(alpha = 0.8f),
            onTertiaryFixed = userPalette.secondaryFontColor,
            onTertiaryFixedVariant = userPalette.secondaryFontColor
        )
    }
}

private data class UserSurfaceRamp(
    val base: Color,
    val dim: Color,
    val bright: Color,
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
)

private fun userSurfaceRamp(
    userPalette: UserColorPalette,
    isDark: Boolean
): UserSurfaceRamp {
    val base = userPalette.backgroundColor
    val accent = userPalette.labelContainerColor
    val lowWeight = if (isDark) 0.10f else 0.08f
    val containerWeight = if (isDark) 0.16f else 0.14f
    val highWeight = if (isDark) 0.22f else 0.20f
    val highestWeight = if (isDark) 0.28f else 0.26f
    return UserSurfaceRamp(
        base = base,
        dim = if (isDark) lerp(base, Color.Black, 0.08f) else lerp(base, Color.Black, 0.06f),
        bright = if (isDark) lerp(base, Color.White, 0.10f) else base,
        lowest = base,
        low = lerp(base, accent, lowWeight),
        container = lerp(base, accent, containerWeight),
        high = lerp(base, accent, highWeight),
        highest = lerp(base, accent, highestWeight),
    )
}
