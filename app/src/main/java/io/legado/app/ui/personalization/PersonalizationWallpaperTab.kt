package io.legado.app.ui.personalization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drducbook.app.R
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.domain.model.AppearanceWallpaperSpec
import io.legado.app.domain.model.WallpaperAlignment
import io.legado.app.domain.model.WallpaperFit
import io.legado.app.ui.theme.LegadoTheme
import kotlin.math.roundToInt

@Composable
internal fun PersonalizationWallpaperTab(
    state: PersonalizationUiState,
    onIntent: (PersonalizationIntent) -> Unit,
) {
    val draft = state.draft ?: return
    val wallpapers = if (state.editingDarkWallpaper) {
        draft.darkWallpapers
    } else {
        draft.lightWallpapers
    }
    val spec = wallpapers[state.selectedWallpaperTarget]
        ?: wallpapers[AppearanceTarget.GLOBAL.key]
        ?: AppearanceWallpaperSpec()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = personalizationContentPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_wallpaper_target))
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(AppearanceTarget.entries, key = { it.key }) { target ->
                    FilterChip(
                        selected = target.key == state.selectedWallpaperTarget,
                        onClick = {
                            onIntent(PersonalizationIntent.SelectWallpaperTarget(target.key))
                        },
                        label = { Text(target.label()) },
                    )
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow {
                listOf(false, true).forEachIndexed { index, dark ->
                    SegmentedButton(
                        selected = state.editingDarkWallpaper == dark,
                        onClick = {
                            onIntent(PersonalizationIntent.SetEditingDarkWallpaper(dark))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        icon = {
                            Icon(
                                if (dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(stringResource(if (dark) R.string.night else R.string.day))
                        },
                    )
                }
            }
        }
        item {
            WallpaperPreview(
                path = state.resolvedWallpaperPath,
                spec = spec,
                backgroundColor = if (state.editingDarkWallpaper) {
                    Color(draft.darkColors.background)
                } else {
                    Color(draft.lightColors.background)
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onIntent(PersonalizationIntent.RequestWallpaperImport) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.personalization_import_wallpaper))
                }
                OutlinedButton(
                    onClick = { onIntent(PersonalizationIntent.RemoveWallpaper) },
                    enabled = state.resolvedWallpaperPath != null,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        }
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_wallpaper_fit))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
                WallpaperFit.entries.forEachIndexed { index, fit ->
                    SegmentedButton(
                        selected = spec.fit == fit,
                        onClick = { onIntent(PersonalizationIntent.SetWallpaperFit(fit)) },
                        shape = SegmentedButtonDefaults.itemShape(index, WallpaperFit.entries.size),
                        label = { Text(fit.label()) },
                    )
                }
            }
        }
        item {
            AlignmentSelector(
                title = stringResource(R.string.personalization_horizontal_alignment),
                selected = spec.horizontalAlignment,
                onSelected = {
                    onIntent(PersonalizationIntent.SetWallpaperHorizontalAlignment(it))
                },
            )
        }
        item {
            AlignmentSelector(
                title = stringResource(R.string.personalization_vertical_alignment),
                selected = spec.verticalAlignment,
                onSelected = {
                    onIntent(PersonalizationIntent.SetWallpaperVerticalAlignment(it))
                },
            )
        }
        item {
            PersonalizationSlider(
                title = stringResource(R.string.personalization_wallpaper_opacity),
                value = spec.opacityPercent.toFloat(),
                valueLabel = spec.opacityPercent.toString() + "%",
                range = 10f..100f,
                steps = 17,
                onValueChange = {
                    onIntent(
                        PersonalizationIntent.UpdateWallpaper(
                            spec.copy(opacityPercent = it.roundToInt())
                        )
                    )
                },
            )
        }
        item {
            PersonalizationSlider(
                title = stringResource(R.string.personalization_wallpaper_blur),
                value = spec.blurDp.toFloat(),
                valueLabel = spec.blurDp.toString() + " dp",
                range = 0f..30f,
                steps = 14,
                onValueChange = {
                    onIntent(
                        PersonalizationIntent.UpdateWallpaper(
                            spec.copy(blurDp = it.roundToInt())
                        )
                    )
                },
            )
        }
        item {
            PersonalizationSlider(
                title = stringResource(R.string.personalization_wallpaper_dim),
                value = spec.dimPercent.toFloat(),
                valueLabel = spec.dimPercent.toString() + "%",
                range = 0f..80f,
                steps = 15,
                onValueChange = {
                    onIntent(
                        PersonalizationIntent.UpdateWallpaper(
                            spec.copy(dimPercent = it.roundToInt())
                        )
                    )
                },
            )
        }
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_wallpaper_overlay))
            WallpaperOverlayOptions(
                selected = spec.overlayColor,
                colors = listOf(
                    null,
                    withAlpha(draft.lightColors.primary, 0x33),
                    withAlpha(draft.lightColors.secondary, 0x33),
                ),
                onSelected = {
                    onIntent(
                        PersonalizationIntent.UpdateWallpaper(spec.copy(overlayColor = it))
                    )
                },
            )
        }
    }
}

@Composable
private fun AlignmentSelector(
    title: String,
    selected: WallpaperAlignment,
    onSelected: (WallpaperAlignment) -> Unit,
) {
    PersonalizationSectionTitle(title)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
        WallpaperAlignment.entries.forEachIndexed { index, alignment ->
            SegmentedButton(
                selected = selected == alignment,
                onClick = { onSelected(alignment) },
                shape = SegmentedButtonDefaults.itemShape(
                    index,
                    WallpaperAlignment.entries.size,
                ),
                label = { Text(alignment.label()) },
            )
        }
    }
}

@Composable
private fun WallpaperPreview(
    path: String?,
    spec: AppearanceWallpaperSpec,
    backgroundColor: Color,
) {
    val description = stringResource(R.string.personalization_wallpaper_preview)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .semantics { contentDescription = description },
    ) {
        if (path != null) {
            AsyncImage(
                model = path,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(spec.blurDp.dp)
                    .alpha(spec.opacityPercent / 100f),
                contentScale = if (spec.fit == WallpaperFit.COVER) {
                    ContentScale.Crop
                } else {
                    ContentScale.Fit
                },
                alignment = spec.composeAlignment(),
            )
        }
        spec.overlayColor?.let {
            Box(Modifier.fillMaxSize().background(Color(it)))
        }
        if (spec.dimPercent > 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = spec.dimPercent / 100f))
            )
        }
        Text(
            text = description,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .then(
                    if (path != null) {
                        Modifier
                            .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    }
                ),
            color = if (path == null) {
                backgroundColor.contrastContentColor()
            } else {
                Color.White
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WallpaperOverlayOptions(
    selected: Int?,
    colors: List<Int?>,
    onSelected: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        colors.forEach { color ->
            val description = if (color == null) {
                stringResource(R.string.personalization_no_overlay)
            } else {
                stringResource(R.string.personalization_choose_overlay)
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color?.let(::Color) ?: Color.Transparent)
                    .border(
                        width = if (selected == color) 3.dp else 1.dp,
                        color = if (selected == color) {
                            LegadoTheme.colorScheme.primary
                        } else {
                            LegadoTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelected(color) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                if (color == null) {
                    Text("X")
                } else if (selected == color) {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
            }
        }
    }
}

internal fun AppearanceWallpaperSpec.composeAlignment(): Alignment {
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

private fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

private fun Color.contrastContentColor(): Color =
    if (luminance() > 0.5f) Color.Black else Color.White
