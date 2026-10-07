package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.domain.model.ChapterContextCopyUiState
import io.legado.app.domain.model.ChapterContextVersionUi
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun ChapterContextCopySheet(
    show: Boolean,
    state: ChapterContextCopyUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.chapter_context_copy_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.chapterTitle.isNotBlank()) {
                AppText(
                    text = state.chapterTitle,
                    style = LegadoTheme.typography.titleMedium,
                    color = LegadoTheme.colorScheme.onSurface,
                )
            }
            AppText(
                text = stringResource(R.string.chapter_context_copy_subtitle),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppCircularProgressIndicator()
                }
            } else if (state.versions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = stringResource(R.string.chapter_context_copy_no_versions),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                state.versions.forEach { version ->
                    ChapterContextVersionCard(
                        version = version,
                        onCopy = {
                            onIntent(ReadBookIntent.CopyChapterContext(version.id))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterContextVersionCard(
    version: ChapterContextVersionUi,
    onCopy: () -> Unit,
) {
    val icon = resolveVersionIcon(version)

    NormalCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = version.isAvailable, onClick = onCopy),
        containerColor = if (version.isAvailable) {
            LegadoTheme.colorScheme.surfaceContainerHigh
        } else {
            LegadoTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (version.isAvailable) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(24.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        text = version.title,
                        style = LegadoTheme.typography.titleSmall,
                        color = if (version.isAvailable) {
                            LegadoTheme.colorScheme.onSurface
                        } else {
                            LegadoTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (version.statusDescription != null) {
                        AppText(
                            text = "• ${version.statusDescription}",
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.primary,
                        )
                    }
                }

                if (version.charCount > 0) {
                    AppText(
                        text = stringResource(R.string.chapter_context_copy_chars, version.charCount),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (version.previewText.isNotBlank()) {
                    AppText(
                        text = version.previewText,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(
                onClick = onCopy,
                enabled = version.isAvailable,
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.chapter_context_copy_action),
                    tint = if (version.isAvailable) {
                        LegadoTheme.colorScheme.primary
                    } else {
                        LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                )
            }
        }
    }
}

private fun resolveVersionIcon(version: ChapterContextVersionUi): ImageVector {
    return when {
        version.id == "raw" -> Icons.Default.MenuBook
        version.id == "rewrite" -> Icons.Default.EditNote
        version.provider == TranslationConstants.PROVIDER_APP_AI -> Icons.Default.AutoAwesome
        version.provider == TranslationConstants.PROVIDER_LOCAL_AI -> Icons.Default.Psychology
        version.provider == TranslationConstants.PROVIDER_ML_KIT -> Icons.Default.SmartToy
        version.provider == TranslationConstants.PROVIDER_QUICK_TRANSLATOR -> Icons.Default.Translate
        else -> Icons.Default.Translate
    }
}
