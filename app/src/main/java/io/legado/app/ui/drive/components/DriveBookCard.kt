package io.legado.app.ui.drive.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.drive.DriveCatalogItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(size: Long): String {
    if (size <= 0) return ""
    return if (size >= 1024 * 1024) {
        String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
    } else {
        String.format(Locale.US, "%d KB", size / 1024)
    }
}

@Composable
fun getFormatBadgeColors(format: String): Pair<Color, Color> {
    val norm = format.lowercase().trim()
    return when (norm) {
        "epub" -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        "pdf" -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        "mobi", "azw", "azw3", "prc" -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        "docx", "doc" -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        "txt", "md", "html", "htm" -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Pair(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Rich catalog book card with 5:7 cover image, format badge, author, file size,
 * 2-line intro/synopsis, and quick import/open buttons.
 */
@Composable
fun DriveCatalogBookCard(
    item: DriveCatalogItem,
    isDownloading: Boolean,
    isImported: Boolean,
    onClick: () -> Unit,
    onDownloadAndImport: () -> Unit,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (formatBg, formatText) = getFormatBadgeColors(item.format)
    val sizeStr = remember(item.size) { formatFileSize(item.size) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 5:7 Cover Image (Zero-download cached or remote URL)
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .aspectRatio(5f / 7f)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                CoilBookCover(
                    name = item.title,
                    author = item.author,
                    path = item.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    radius = 6.dp
                )

                if (isImported) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Đã trong kệ",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                // Title and format badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppText(
                        text = item.title,
                        style = LegadoTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (item.format.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        TextCard(
                            text = item.format.uppercase(),
                            cornerRadius = 4.dp,
                            horizontalPadding = 5.dp,
                            verticalPadding = 1.dp,
                            textStyle = LegadoTheme.typography.labelSmallEmphasized,
                            backgroundColor = formatBg,
                            contentColor = formatText
                        )
                    }
                }

                // Author
                if (!item.author.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    AppText(
                        text = item.author,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // File size & date
                if (sizeStr.isNotBlank() || item.lastModified > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val dateStr = if (item.lastModified > 0) {
                        val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
                        sdf.format(Date(item.lastModified))
                    } else ""
                    val infoStr = listOf(sizeStr, dateStr).filter { it.isNotBlank() }.joinToString(" • ")

                    AppText(
                        text = infoStr,
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }

                // Intro / Synopsis
                if (!item.intro.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = item.intro,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        isDownloading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        isImported -> {
                            FilledTonalButton(
                                onClick = onOpenBook,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Đọc sách", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = onDownloadAndImport,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tải vào kệ", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact grid item for 2-column or 3-column view mode.
 */
@Composable
fun DriveGridBookCard(
    item: DriveCatalogItem,
    isDownloading: Boolean,
    isImported: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (formatBg, formatText) = getFormatBadgeColors(item.format)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        // 5:7 Cover image with badges
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 7f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            CoilBookCover(
                name = item.title,
                author = item.author,
                path = item.coverUrl,
                modifier = Modifier.fillMaxSize(),
                radius = 8.dp
            )

            // Format badge top-end
            if (item.format.isNotBlank()) {
                TextCard(
                    text = item.format.uppercase(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    cornerRadius = 4.dp,
                    horizontalPadding = 4.dp,
                    verticalPadding = 1.dp,
                    textStyle = LegadoTheme.typography.labelSmallEmphasized,
                    backgroundColor = formatBg,
                    contentColor = formatText
                )
            }

            // Shelf status badge bottom-end
            if (isImported) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Đã trong kệ",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else if (isDownloading) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(topStart = 6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(2.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title
        AppText(
            text = item.title,
            style = LegadoTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Author
        if (!item.author.isNullOrBlank()) {
            AppText(
                text = item.author,
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact list row for dense list view mode.
 */
@Composable
fun DriveListBookRow(
    item: DriveCatalogItem,
    isDownloading: Boolean,
    isImported: Boolean,
    onClick: () -> Unit,
    onDownloadAndImport: () -> Unit,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (formatBg, formatText) = getFormatBadgeColors(item.format)
    val sizeStr = remember(item.size) { formatFileSize(item.size) }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Mini 5:7 cover
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .aspectRatio(5f / 7f)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    CoilBookCover(
                        name = item.title,
                        author = item.author,
                        path = item.coverUrl,
                        modifier = Modifier.fillMaxSize(),
                        radius = 4.dp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (item.format.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            TextCard(
                                text = item.format.uppercase(),
                                cornerRadius = 3.dp,
                                horizontalPadding = 4.dp,
                                verticalPadding = 1.dp,
                                textStyle = LegadoTheme.typography.labelSmallEmphasized,
                                backgroundColor = formatBg,
                                contentColor = formatText
                            )
                        }
                    }

                    val subText = listOfNotNull(item.author, sizeStr.takeIf { it.isNotBlank() })
                        .joinToString(" • ")
                    if (subText.isNotBlank()) {
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                isDownloading -> {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                isImported -> {
                    IconButton(onClick = onOpenBook, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Đã có trong kệ",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                else -> {
                    IconButton(onClick = onDownloadAndImport, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Tải vào kệ",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard folder navigation row.
 */
@Composable
fun DriveFolderItemRow(
    item: DriveCatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
