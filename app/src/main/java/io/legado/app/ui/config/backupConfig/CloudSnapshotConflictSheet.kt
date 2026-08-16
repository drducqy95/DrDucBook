package io.legado.app.ui.config.backupConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.CloudSnapshotResolutionPlan
import io.legado.app.domain.usecase.CloudSnapshotConflictPrompt
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSnapshotConflictSheet(
    prompt: CloudSnapshotConflictPrompt?,
    onDismissRequest: () -> Unit,
    onSelectPlan: (CloudSnapshotResolutionPlan) -> Unit,
) {
    if (prompt == null) {
        return
    }
    val items = remember(prompt) {
        CloudSnapshotConflictUiMapper.optionItems(prompt)
    }
    AppModalBottomSheet(
        show = items.isNotEmpty(),
        title = stringResource(CloudSnapshotConflictUiMapper.titleRes(prompt)),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = stringResource(CloudSnapshotConflictUiMapper.messageRes(prompt)),
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items) { item ->
                    SelectionItemCard(
                        title = stringResource(item.titleRes),
                        subtitle = stringResource(item.descriptionRes),
                        containerColor = if (item.option.destructiveRestore) {
                            LegadoTheme.colorScheme.errorContainer
                        } else {
                            LegadoTheme.colorScheme.surface
                        },
                        leadingContent = {
                            Icon(
                                imageVector = item.icon.imageVector,
                                contentDescription = null,
                                tint = if (item.option.destructiveRestore) {
                                    LegadoTheme.colorScheme.onErrorContainer
                                } else {
                                    LegadoTheme.colorScheme.primary
                                },
                            )
                        },
                        onToggleSelection = {
                            onSelectPlan(
                                CloudSnapshotConflictUiMapper.resolutionPlan(
                                    prompt = prompt,
                                    item = item,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

private val CloudSnapshotConflictOptionIcon.imageVector: ImageVector
    get() = when (this) {
        CloudSnapshotConflictOptionIcon.DEVICE -> Icons.Default.PhoneAndroid
        CloudSnapshotConflictOptionIcon.CLOUD_RESTORE -> Icons.Default.CloudDownload
        CloudSnapshotConflictOptionIcon.CLOUD_COPY -> Icons.Default.CloudUpload
    }
