package io.legado.app.ui.drive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.DriveConnectionStatus
import io.legado.app.domain.model.DriveSourceType
import io.legado.app.ui.drive.components.DriveCatalogBookCard
import io.legado.app.ui.drive.components.DriveFolderItemRow
import io.legado.app.ui.drive.components.DriveGridBookCard
import io.legado.app.ui.drive.components.DriveListBookRow
import io.legado.app.ui.widget.components.card.NormalCard

@Composable
fun DriveLibrarySection(
    state: DriveLibraryUiState,
    onIntent: (DriveLibraryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val folders = remember(state.items) { state.items.filter { it.isDir } }
    val books = remember(state.items) { state.items.filter { !it.isDir } }

    NormalCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thư viện Drive & Cloud",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                IconButton(
                    onClick = { onIntent(DriveLibraryIntent.ShowAddSourceSheet) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Thêm nguồn",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // State: No sources configured
            if (state.sources.isEmpty()) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Chưa cấu hình thư viện đám mây",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kết nối Google Drive để duyệt sách với ảnh bìa và tóm tắt mà không cần tải về",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { onIntent(DriveLibraryIntent.ShowAddSourceSheet) }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thêm thư viện")
                        }
                    }
                }
                return@Column
            }

            // Sources horizontal selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.sources.forEach { source ->
                    val isSelected = state.activeSource?.id == source.id
                    val typeTag = when (source.type) {
                        DriveSourceType.GOOGLE_DRIVE_ACCOUNT -> "Google Drive"
                        DriveSourceType.GOOGLE_DRIVE_PUBLIC -> "GDrive Public"
                        DriveSourceType.GOOGLE_DRIVE_SERVICE_ACCOUNT -> "GDrive SA"
                        DriveSourceType.ONEDRIVE_PUBLIC -> "OneDrive"
                        DriveSourceType.DROPBOX_PUBLIC -> "Dropbox"
                        DriveSourceType.HTTP_INDEX -> "HTTP Index"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onIntent(DriveLibraryIntent.SelectSource(source.id)) },
                        label = { Text("${source.name} ($typeTag)") },
                        leadingIcon = {
                            val dotColor = when (source.status) {
                                DriveConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                DriveConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary
                                DriveConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = dotColor,
                                modifier = Modifier.size(8.dp)
                            ) {}
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Xóa",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            onIntent(DriveLibraryIntent.ShowDeleteDialog(source))
                                        }
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Breadcrumbs Navigation
            if (state.breadcrumbs.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.breadcrumbs.forEachIndexed { index, crumb ->
                        val displayName = if (index == 0) "Gốc" else crumb.trimEnd('/').split('/').last()
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (index == state.breadcrumbs.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable {
                                    onIntent(DriveLibraryIntent.NavigateBreadcrumb(index))
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                        if (index < state.breadcrumbs.lastIndex) {
                            Text(
                                text = "/",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // View Mode & Summary Toolbar
            if (!state.loading && state.items.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val countSummary = buildString {
                        val protocolLabel = if (state.viewMode == DriveViewMode.OPDS) "OPDS" else "WebDAV"
                        append("[$protocolLabel] ")
                        if (folders.isNotEmpty()) append("${folders.size} thư mục")
                        if (folders.isNotEmpty() && books.isNotEmpty()) append(" • ")
                        if (books.isNotEmpty()) append("${books.size} tài liệu")
                    }
                    Text(
                        text = countSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    // View mode switcher
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = state.viewMode == DriveViewMode.CATALOG,
                            onClick = { onIntent(DriveLibraryIntent.SetViewMode(DriveViewMode.CATALOG)) },
                            label = { Text("Mô tả") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        FilterChip(
                            selected = state.viewMode == DriveViewMode.GRID,
                            onClick = { onIntent(DriveLibraryIntent.SetViewMode(DriveViewMode.GRID)) },
                            label = { Text("Lưới") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        FilterChip(
                            selected = state.viewMode == DriveViewMode.LIST,
                            onClick = { onIntent(DriveLibraryIntent.SetViewMode(DriveViewMode.LIST)) },
                            label = { Text("Danh sách") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        if (state.opdsUrl != null) {
                            FilterChip(
                                selected = state.viewMode == DriveViewMode.OPDS,
                                onClick = { onIntent(DriveLibraryIntent.SetViewMode(DriveViewMode.OPDS)) },
                                label = { Text("OPDS") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Loading indicator
            AnimatedVisibility(visible = state.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Đang đồng bộ danh mục & metadata...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Folders List (always on top)
            if (!state.loading && folders.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    folders.forEach { folder ->
                        DriveFolderItemRow(
                            item = folder,
                            onClick = { onIntent(DriveLibraryIntent.OpenFolder(folder.path)) }
                        )
                    }
                }
                if (books.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Books List (rendered according to ViewMode)
            if (!state.loading && books.isNotEmpty()) {
                when (state.viewMode) {
                    DriveViewMode.CATALOG, DriveViewMode.OPDS -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            books.forEach { book ->
                                DriveCatalogBookCard(
                                    item = book,
                                    isDownloading = state.downloadingPaths.contains(book.path),
                                    isImported = book.isImported || state.importedPaths.contains(book.path),
                                    onClick = { onIntent(DriveLibraryIntent.ShowBookPreview(book)) },
                                    onDownloadAndImport = { onIntent(DriveLibraryIntent.DownloadAndImport(book)) },
                                    onOpenBook = { onIntent(DriveLibraryIntent.OpenBook(book)) }
                                )
                            }
                        }
                    }

                    DriveViewMode.GRID -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            books.chunked(2).forEach { rowBooks ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowBooks.forEach { book ->
                                        DriveGridBookCard(
                                            item = book,
                                            isDownloading = state.downloadingPaths.contains(book.path),
                                            isImported = book.isImported || state.importedPaths.contains(book.path),
                                            onClick = { onIntent(DriveLibraryIntent.ShowBookPreview(book)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowBooks.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    DriveViewMode.LIST -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            books.forEach { book ->
                                DriveListBookRow(
                                    item = book,
                                    isDownloading = state.downloadingPaths.contains(book.path),
                                    isImported = book.isImported || state.importedPaths.contains(book.path),
                                    onClick = { onIntent(DriveLibraryIntent.ShowBookPreview(book)) },
                                    onDownloadAndImport = { onIntent(DriveLibraryIntent.DownloadAndImport(book)) },
                                    onOpenBook = { onIntent(DriveLibraryIntent.OpenBook(book)) }
                                )
                            }
                        }
                    }
                }
            } else if (!state.loading && state.connectedServerId != null && state.items.isEmpty()) {
                Text(
                    text = "Thư mục trống",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}
