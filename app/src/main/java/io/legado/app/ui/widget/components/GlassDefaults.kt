package io.legado.app.ui.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme

object GlassDefaults {

    /**
     * 统一的玻璃颜色处理方法
     * @param noBlurColor 未开启模糊时使用的颜色
     * @param blurAlpha 开启模糊时应用的透明度
     */
    @Composable
    fun glassColor(noBlurColor: Color, blurAlpha: Float): Color {
        return if (ThemeConfig.enableBlur) {
            noBlurColor.copy(alpha = blurAlpha)
        } else {
            noBlurColor
        }
    }

    @Composable
    fun secondaryColorOr(fallback: @Composable () -> Color): Color {
        val surfaceColor = fallback()
        val secondaryColor = ThemeConfig.customThemeColors(LegadoTheme.isDark).secondary
        return if (ThemeConfig.isDeepPersonalizationActive && secondaryColor != 0) {
            harmonizedSurfaceColor(surfaceColor, Color(secondaryColor))
        } else {
            surfaceColor
        }
    }

    internal fun harmonizedSurfaceColor(surfaceColor: Color, accentColor: Color): Color {
        return lerp(surfaceColor, accentColor, CustomSurfaceTintFraction)
    }

    val DefaultBlurAlpha = 0.36f
    val ThickBlurAlpha = 0.72f
    val TransparentAlpha = 0f

    private const val CustomSurfaceTintFraction = 0.12f
}
