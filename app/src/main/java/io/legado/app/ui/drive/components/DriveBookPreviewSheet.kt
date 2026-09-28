package io.legado.app.ui.drive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.drive.DriveCatalogItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriveBookPreviewSheet(
    item: DriveCatalogItem?,
    isDownloading: Boolean,
    isImported: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadAndImport: (DriveCatalogItem) -> Unit,
    onOpenBook: (DriveCatalogItem) -> Unit
) {
    AppModalBottomSheet(
        data = item,
        onDismissRequest = onDismissRequest
    ) { book ->
        val (formatBg, formatText) = getFormatBadgeColors(book.format)
        val sizeStr = remember(book.size) { formatFileSize(book.size) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header: Cover + Primary metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Large 5:7 Cover
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(5f / 7f)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    CoilBookCover(
                        name = book.title,
                        author = book.author,
                        path = book.coverUrl,
                        modifier = Modifier.fillMaxSize(),
                        radius = 8.dp
                    )

                    if (isImported) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Đã trong kệ",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Details Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppText(
                        text = book.title,
                        style = LegadoTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!book.author.isNullOrBlank()) {
                        AppText(
                            text = book.author,
                            style = LegadoTheme.typography.bodyMedium,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Format Badge & File size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (book.format.isNotBlank()) {
                            TextCard(
                                text = book.format.uppercase(),
                                cornerRadius = 4.dp,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp,
                                textStyle = LegadoTheme.typography.labelSmallEmphasized,
                                backgroundColor = formatBg,
                                contentColor = formatText
                            )
                        }

                        if (sizeStr.isNotBlank()) {
                            AppText(
                                text = sizeStr,
                                style = LegadoTheme.typography.labelMedium,
                                color = LegadoTheme.colorScheme.outline
                            )
                        }
                    }

                    // Last modified date
                    if (book.lastModified > 0) {
                        val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                        AppText(
                            text = "Cập nhật: ${sdf.format(Date(book.lastModified))}",
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Intro / Synopsis section
            val introText = book.intro?.replace("\\s+".toRegex(), " ")?.trim()
            if (!introText.isNullOrBlank()) {
                AppText(
                    text = "Giới thiệu tác phẩm",
                    style = LegadoTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AppText(
                        text = introText,
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AppText(
                    text = "Tập tin trên đám mây chưa có bản tóm tắt nội dung.",
                    style = LegadoTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = LegadoTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            ConfirmDismissButtonsRow(
                onDismiss = onDismissRequest,
                onConfirm = {
                    if (isImported) {
                        onDismissRequest()
                        onOpenBook(book)
                    } else if (!isDownloading) {
                        onDownloadAndImport(book)
                    }
                },
                dismissText = "Đóng",
                confirmText = when {
                    isDownloading -> "Đang tải về..."
                    isImported -> "Đọc sách"
                    else -> "Tải vào kệ"
                },
                confirmEnabled = !isDownloading
            )
        }
    }
}
