package io.legado.app.ui.widget.components.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.legado.app.domain.gateway.AppearanceGateway
import io.legado.app.domain.model.IconSlot
import io.legado.app.ui.theme.LegadoTheme
import org.koin.compose.koinInject

@Composable
fun PersonalizedIcon(
    slot: IconSlot,
    fallback: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LegadoTheme.colorScheme.primary,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
) {
    val gateway = koinInject<AppearanceGateway>()
    val appearance by gateway.state.collectAsStateWithLifecycle()
    val spec = appearance.activeProfile.iconSlots[slot.key]
    val path = spec?.let { gateway.resolveAsset(it.assetId, it.legacyLocation) }
    val resolvedTint = spec?.tintColor?.let(::Color) ?: tint
    Box(
        modifier = modifier
            .size(containerSize)
            .then(
                if (spec?.backgroundColor != null) {
                    Modifier.background(Color(spec.backgroundColor), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .padding(((spec?.paddingPercent ?: 0) * 0.4f).dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            path != null -> AsyncImage(
                model = path,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(iconSize)
                    .scale((spec.scale).coerceIn(0.5f, 1f)),
                contentScale = ContentScale.Fit,
                colorFilter = spec.tintColor?.let { ColorFilter.tint(Color(it)) },
            )
            spec?.bundledIcon != null -> Icon(
                imageVector = when (spec.bundledIcon) {
                    "sparkles" -> Icons.Default.AutoAwesome
                    "rss" -> Icons.Default.RssFeed
                    else -> Icons.Default.AutoStories
                },
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(iconSize)
                    .scale(spec.scale.coerceIn(0.5f, 1f)),
                tint = resolvedTint,
            )
            else -> Icon(
                imageVector = fallback,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }
    }
}
