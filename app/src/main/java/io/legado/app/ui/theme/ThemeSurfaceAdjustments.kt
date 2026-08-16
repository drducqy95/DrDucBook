package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal fun ColorScheme.withAmoledSurfaceRamp(): ColorScheme = copy(
    surface = Color.Black,
    background = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF202020),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF060606),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1B1B1B),
)

internal fun ColorScheme.withTransparentSurfaceRamp(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        copy(
            surface = Color.Transparent,
            background = Color.Transparent,
            surfaceDim = Color(0x1A000000),
            surfaceBright = Color(0x33000000),
            surfaceContainerLowest = Color.Transparent,
            surfaceContainerLow = Color(0x26000000),
            surfaceContainer = Color(0x3D000000),
            surfaceContainerHigh = Color(0x52000000),
            surfaceContainerHighest = Color(0x66000000),
        )
    } else {
        copy(
            surface = Color.Transparent,
            background = Color.Transparent,
            surfaceDim = Color(0x14FFFFFF),
            surfaceBright = Color(0x2EFFFFFF),
            surfaceContainerLowest = Color.Transparent,
            surfaceContainerLow = Color(0x1FFFFFFF),
            surfaceContainer = Color(0x2EFFFFFF),
            surfaceContainerHigh = Color(0x47FFFFFF),
            surfaceContainerHighest = Color(0x5CFFFFFF),
        )
    }
}
