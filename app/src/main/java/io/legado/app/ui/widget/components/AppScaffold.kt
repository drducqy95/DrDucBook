package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.domain.gateway.AppearanceGateway
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.domain.model.AppearanceWallpaperSpec
import io.legado.app.domain.model.WallpaperAlignment
import io.legado.app.domain.model.WallpaperFit
import io.legado.app.domain.model.wallpaperFor
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeSource
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FabPosition as MiuixFabPosition
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import org.koin.compose.koinInject

val LocalAppearanceTarget = staticCompositionLocalOf { AppearanceTarget.GLOBAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (HazeState) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    contentColor: Color = contentColorFor(MiuixTheme.colorScheme.surface),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    alwaysDrawBehindBars: Boolean = false,
    disableHazeSource: Boolean = false,
    appearanceTarget: AppearanceTarget = LocalAppearanceTarget.current,
    content: @Composable (PaddingValues) -> Unit
) {
    val isDark = LegadoTheme.isDark
    val appearanceGateway = koinInject<AppearanceGateway>()
    val appearanceState by appearanceGateway.state.collectAsState()
    val wallpaper = appearanceState.activeProfile.wallpaperFor(appearanceTarget, isDark)
    val appearanceImagePath = wallpaper?.let {
        appearanceGateway.resolveAsset(it.assetId, it.legacyLocation)
    }
    val legacyImagePath = if (isDark) ThemeConfig.bgImageDark else ThemeConfig.bgImageLight
    val imagePath = appearanceImagePath ?: legacyImagePath
    val hasImageBg = !imagePath.isNullOrBlank()
    val hazeState = remember { HazeState() }
    val composeEngine = LegadoTheme.composeEngine
    val contentDrawsBehindBars =
        alwaysDrawBehindBars || ThemeConfig.enableBlur || ThemeConfig.enableProgressiveBlur

    val containerColor = if (hasImageBg) {
        Color.Transparent
    } else {
        LegadoTheme.colorScheme.background
    }

    val miuixContainerColor = if (hasImageBg) {
        Color.Transparent
    } else {
        MiuixTheme.colorScheme.surface
    }

    CompositionLocalProvider(
        LocalHazeState provides if (ThemeConfig.enableBlur) hazeState else null
    ) {
        when {
            ThemeResolver.isMiuixEngine(composeEngine) -> {
                val miuixFabPosition = when (floatingActionButtonPosition) {
                    FabPosition.End -> MiuixFabPosition.End
                    FabPosition.Center -> MiuixFabPosition.Center
                    else -> MiuixFabPosition.End
                }
                Box(modifier = modifier.fillMaxSize()) {
                    BackgroundImageContent(
                        imagePath = imagePath,
                        wallpaper = wallpaper,
                        legacyBlur = if (isDark) {
                            ThemeConfig.bgImageNBlurring
                        } else {
                            ThemeConfig.bgImageBlurring
                        },
                        hazeState = hazeState,
                    )
                    MiuixScaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            topBar(hazeState)
                        },
                        bottomBar = bottomBar,
                        snackbarHost = snackbarHost,
                        floatingActionButton = floatingActionButton,
                        floatingActionButtonPosition = miuixFabPosition,
                        containerColor = miuixContainerColor,
                        contentWindowInsets = contentWindowInsets
                    ) { paddingValues ->
                        val scaffoldPadding = if (ThemeConfig.useFloatingBottomBar) {
                            PaddingValues(top = paddingValues.calculateTopPadding())
                        } else {
                            paddingValues
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (!disableHazeSource) Modifier.responsiveHazeSource(hazeState)
                                    else Modifier
                                )
                                .then(
                                    if (contentDrawsBehindBars) Modifier
                                    else Modifier.padding(scaffoldPadding)
                                )
                        ) {
                            content(
                                if (contentDrawsBehindBars) scaffoldPadding
                                else PaddingValues(0.dp)
                            )
                        }
                    }
                }
            }

            else -> {
                Box(modifier = modifier.fillMaxSize()) {
                    BackgroundImageContent(
                        imagePath = imagePath,
                        wallpaper = wallpaper,
                        legacyBlur = if (isDark) {
                            ThemeConfig.bgImageNBlurring
                        } else {
                            ThemeConfig.bgImageBlurring
                        },
                        hazeState = hazeState,
                    )
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            topBar(hazeState)
                        },
                        bottomBar = bottomBar,
                        snackbarHost = snackbarHost,
                        floatingActionButton = floatingActionButton,
                        floatingActionButtonPosition = floatingActionButtonPosition,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        contentWindowInsets = contentWindowInsets
                    ) { paddingValues ->
                        val scaffoldPadding = if (ThemeConfig.useFloatingBottomBar) {
                            PaddingValues(top = paddingValues.calculateTopPadding())
                        } else {
                            paddingValues
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (!disableHazeSource) Modifier.responsiveHazeSource(hazeState)
                                    else Modifier
                                )
                                .then(
                                    if (contentDrawsBehindBars) Modifier
                                    else Modifier.padding(scaffoldPadding)
                                )
                        ) {
                            content(
                                if (contentDrawsBehindBars) scaffoldPadding
                                else PaddingValues(0.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BackgroundImageContent(
    imagePath: String?,
    wallpaper: AppearanceWallpaperSpec?,
    legacyBlur: Int,
    hazeState: HazeState,
) {
    if (imagePath.isNullOrBlank()) return
    val blur = wallpaper?.blurDp ?: legacyBlur
    val imageModifier = Modifier
        .fillMaxSize()
        .blur(blur.coerceIn(0, 50).dp)
        .alpha((wallpaper?.opacityPercent ?: 100).coerceIn(0, 100) / 100f)
        .then(if (ThemeConfig.enableBlur) Modifier.hazeSource(hazeState) else Modifier)
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = imagePath,
            contentDescription = null,
            imageLoader = koinInject(),
            modifier = imageModifier,
            contentScale = if (wallpaper?.fit == WallpaperFit.CONTAIN) {
                ContentScale.Fit
            } else {
                ContentScale.Crop
            },
            alignment = wallpaper?.composeAlignment() ?: Alignment.Center,
        )
        wallpaper?.overlayColor?.let {
            Box(Modifier.fillMaxSize().background(Color(it)))
        }
        wallpaper?.dimPercent?.takeIf { it > 0 }?.let {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = it.coerceIn(0, 100) / 100f))
            )
        }
    }
}

private fun AppearanceWallpaperSpec.composeAlignment(): Alignment {
    return when {
        horizontalAlignment == WallpaperAlignment.START &&
            verticalAlignment == WallpaperAlignment.START -> Alignment.TopStart
        horizontalAlignment == WallpaperAlignment.END &&
            verticalAlignment == WallpaperAlignment.START -> Alignment.TopEnd
        horizontalAlignment == WallpaperAlignment.START &&
            verticalAlignment == WallpaperAlignment.END -> Alignment.BottomStart
        horizontalAlignment == WallpaperAlignment.END &&
            verticalAlignment == WallpaperAlignment.END -> Alignment.BottomEnd
        horizontalAlignment == WallpaperAlignment.START -> Alignment.CenterStart
        horizontalAlignment == WallpaperAlignment.END -> Alignment.CenterEnd
        verticalAlignment == WallpaperAlignment.START -> Alignment.TopCenter
        verticalAlignment == WallpaperAlignment.END -> Alignment.BottomCenter
        else -> Alignment.Center
    }
}
