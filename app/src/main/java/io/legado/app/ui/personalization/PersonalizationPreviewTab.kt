package io.legado.app.ui.personalization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drducbook.app.R
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.domain.model.WallpaperFit
import io.legado.app.domain.model.wallpaperFor
import io.legado.app.ui.theme.LegadoTheme

@Composable
internal fun PersonalizationPreviewTab(
    state: PersonalizationUiState,
    onIntent: (PersonalizationIntent) -> Unit,
) {
    val profile = state.draft ?: return
    val colors = if (state.previewDark) profile.darkColors else profile.lightColors
    val wallpaper = profile.wallpaperFor(AppearanceTarget.GLOBAL, state.previewDark)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = personalizationContentPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow {
                listOf(false, true).forEachIndexed { index, dark ->
                    SegmentedButton(
                        selected = state.previewDark == dark,
                        onClick = { onIntent(PersonalizationIntent.SetPreviewDark(dark)) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = {
                            Text(stringResource(if (dark) R.string.night else R.string.day))
                        },
                    )
                }
            }
        }
        if (state.contrastWarning) {
            item {
                Surface(
                    color = LegadoTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = LegadoTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            stringResource(R.string.personalization_contrast_warning),
                            color = LegadoTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp),
                color = Color(colors.background),
                contentColor = Color(colors.primaryText),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(colors.secondaryText).copy(alpha = 0.3f)),
            ) {
                Box {
                    if (state.resolvedPreviewWallpaperPath != null && wallpaper != null) {
                        AsyncImage(
                            model = state.resolvedPreviewWallpaperPath,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(wallpaper.blurDp.dp)
                                .alpha(wallpaper.opacityPercent / 100f),
                            contentScale = if (wallpaper.fit == WallpaperFit.COVER) {
                                ContentScale.Crop
                            } else {
                                ContentScale.Fit
                            },
                            alignment = wallpaper.composeAlignment(),
                        )
                        wallpaper.overlayColor?.let {
                            Box(Modifier.fillMaxSize().background(Color(it)))
                        }
                        if (wallpaper.dimPercent > 0) {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Color.Black.copy(alpha = wallpaper.dimPercent / 100f)
                                )
                            )
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        Surface(color = Color(colors.container).copy(alpha = 0.94f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(58.dp).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    profile.displayName(),
                                    color = Color(colors.primaryText),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            repeat(3) { index ->
                                Surface(
                                    color = Color(colors.container).copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(36.dp)
                                                .background(
                                                    if (index == 1) {
                                                        Color(colors.secondary)
                                                    } else {
                                                        Color(colors.primary)
                                                    },
                                                    CircleShape,
                                                )
                                        )
                                        Column {
                                            Text(
                                                stringResource(R.string.personalization_preview_item),
                                                color = Color(colors.primaryText),
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                stringResource(R.string.personalization_preview_detail),
                                                color = Color(colors.secondaryText),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Surface(color = Color(colors.container).copy(alpha = 0.96f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(68.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                repeat(5) { index ->
                                    Box(
                                        Modifier
                                            .size(if (index == 3) 34.dp else 28.dp)
                                            .background(
                                                if (index == 3) {
                                                    Color(colors.primary)
                                                } else {
                                                    Color(colors.secondaryText).copy(alpha = 0.45f)
                                                },
                                                CircleShape,
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
