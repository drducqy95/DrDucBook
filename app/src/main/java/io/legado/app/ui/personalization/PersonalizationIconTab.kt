package io.legado.app.ui.personalization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drducbook.app.R
import io.legado.app.domain.model.AppearanceIconSpec
import io.legado.app.domain.model.IconSlot
import io.legado.app.domain.model.IconSlotGroup
import io.legado.app.ui.theme.LegadoTheme
import kotlin.math.roundToInt

@Composable
internal fun PersonalizationIconTab(
    state: PersonalizationUiState,
    onIntent: (PersonalizationIntent) -> Unit,
) {
    val draft = state.draft ?: return
    val spec = draft.iconSlots[state.selectedIconSlot.key] ?: AppearanceIconSpec()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = personalizationContentPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_icon_slot))
            IconSlotPicker(state.selectedIconSlot) {
                onIntent(PersonalizationIntent.SelectIconSlot(it))
            }
        }
        item {
            IconPreview(
                path = state.resolvedIconPath,
                spec = spec,
                slot = state.selectedIconSlot,
            )
        }
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_bundled_icons))
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bundledIcons(), key = { it.key }) { option ->
                    FilterChip(
                        selected = spec.bundledIcon == option.key && spec.assetId.isBlank(),
                        onClick = {
                            onIntent(
                                PersonalizationIntent.UpdateIcon(
                                    spec.copy(
                                        assetId = "",
                                        legacyLocation = null,
                                        bundledIcon = option.key,
                                    )
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(option.icon, contentDescription = null, Modifier.size(18.dp))
                        },
                        label = { Text(option.label()) },
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onIntent(PersonalizationIntent.RequestIconImport) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.personalization_import_icon))
                }
                OutlinedButton(
                    onClick = { onIntent(PersonalizationIntent.RemoveIcon) },
                    enabled = state.resolvedIconPath != null || spec.bundledIcon != null,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        }
        item {
            PersonalizationSlider(
                title = stringResource(R.string.personalization_icon_scale),
                value = spec.scale,
                valueLabel = (spec.scale * 100).roundToInt().toString() + "%",
                range = 0.5f..1f,
                steps = 9,
                onValueChange = {
                    onIntent(PersonalizationIntent.UpdateIcon(spec.copy(scale = it)))
                },
            )
        }
        item {
            PersonalizationSlider(
                title = stringResource(R.string.personalization_icon_padding),
                value = spec.paddingPercent.toFloat(),
                valueLabel = spec.paddingPercent.toString() + "%",
                range = 0f..30f,
                steps = 5,
                onValueChange = {
                    onIntent(
                        PersonalizationIntent.UpdateIcon(
                            spec.copy(paddingPercent = it.roundToInt())
                        )
                    )
                },
            )
        }
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_icon_tint))
            IconColorOptions(
                selected = spec.tintColor,
                colors = listOf(null, draft.lightColors.primary, draft.lightColors.secondary),
                onSelected = {
                    onIntent(PersonalizationIntent.UpdateIcon(spec.copy(tintColor = it)))
                },
            )
        }
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_icon_background))
            IconColorOptions(
                selected = spec.backgroundColor,
                colors = listOf(null, draft.lightColors.container, draft.darkColors.container),
                onSelected = {
                    onIntent(PersonalizationIntent.UpdateIcon(spec.copy(backgroundColor = it)))
                },
            )
        }
    }
}

@Composable
private fun IconSlotPicker(
    selected: IconSlot,
    onSelected: (IconSlot) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconSlotGroup.entries.forEach { group ->
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IconSlot.entries.filter { it.group == group }, key = { it.key }) { slot ->
                    FilterChip(
                        selected = slot == selected,
                        onClick = { onSelected(slot) },
                        label = {
                            Text(
                                slot.label(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPreview(
    path: String?,
    spec: AppearanceIconSpec,
    slot: IconSlot,
) {
    val background = spec.backgroundColor?.let(::Color)
        ?: LegadoTheme.colorScheme.surfaceContainer
    val description = stringResource(R.string.personalization_icon_preview_slot, slot.label())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(background, RoundedCornerShape(8.dp))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when {
            path != null -> AsyncImage(
                model = path,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .padding((spec.paddingPercent * 0.72f).dp)
                    .scale(spec.scale.coerceIn(0.5f, 1f)),
                contentScale = ContentScale.Fit,
                colorFilter = spec.tintColor?.let { ColorFilter.tint(Color(it)) },
            )
            spec.bundledIcon != null -> Icon(
                imageVector = bundledIconVector(spec.bundledIcon),
                contentDescription = null,
                modifier = Modifier.size(64.dp).scale(spec.scale.coerceIn(0.5f, 1f)),
                tint = spec.tintColor?.let(::Color) ?: LegadoTheme.colorScheme.primary,
            )
            else -> Icon(
                Icons.Default.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = LegadoTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun IconColorOptions(
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
                stringResource(R.string.personalization_no_color)
            } else {
                stringResource(R.string.personalization_choose_color)
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
                    Text("X", color = LegadoTheme.colorScheme.onSurface)
                } else if (selected == color) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

private data class BundledIconOption(
    val key: String,
    val icon: ImageVector,
)

private fun bundledIcons() = listOf(
    BundledIconOption("book", Icons.Default.AutoStories),
    BundledIconOption("sparkles", Icons.Default.AutoAwesome),
    BundledIconOption("rss", Icons.Default.RssFeed),
)

@Composable
private fun BundledIconOption.label(): String = when (key) {
    "sparkles" -> stringResource(R.string.personalization_icon_sparkles)
    "rss" -> "RSS"
    else -> stringResource(R.string.personalization_icon_book)
}

private fun bundledIconVector(key: String): ImageVector = when (key) {
    "sparkles" -> Icons.Default.AutoAwesome
    "rss" -> Icons.Default.RssFeed
    else -> Icons.Default.AutoStories
}
