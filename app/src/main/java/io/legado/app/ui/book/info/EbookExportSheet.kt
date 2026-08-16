package io.legado.app.ui.book.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.service.export.EbookExportContentSource
import io.legado.app.service.export.EbookExportFormat
import io.legado.app.service.export.EbookExportImageOptimization
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun EbookExportSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onExport: (format: String, scope: String, contentSource: String) -> Unit,
    onExportWithOptions: ((String, String, String, EbookExportImageOptimization, Boolean) -> Unit)? = null,
) {
    var format by remember(show) { mutableStateOf(EbookExportFormat.EPUB3) }
    var contentSource by remember(show) { mutableStateOf(EbookExportContentSource.BOTH) }
    var exportAll by remember(show) { mutableStateOf(true) }
    var scope by remember(show) { mutableStateOf("") }
    var imageOptimization by remember(show) {
        mutableStateOf(EbookExportImageOptimization.ORIGINAL)
    }
    var sendToKindle by remember(show) { mutableStateOf(false) }
    var scopeError by remember(show) { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.export_ebook),
        endAction = {
            SmallTonalButton(
                text = stringResource(R.string.export),
                onClick = {
                    val selectedScope = if (exportAll) "all" else scope.trim()
                    if (!exportAll && !isValidExportScope(selectedScope)) {
                        scopeError = true
                    } else {
                        onExportWithOptions?.invoke(
                            format.value,
                            selectedScope,
                            contentSource.value,
                            imageOptimization,
                            sendToKindle,
                        ) ?: onExport(format.value, selectedScope, contentSource.value)
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(stringResource(R.string.export_type))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EbookExportFormat.entries.forEach { item ->
                    SmallTonalButton(
                        text = item.value.uppercase(),
                        selected = format == item,
                        onClick = {
                            format = item
                            if (item !in setOf(
                                    EbookExportFormat.EPUB3,
                                    EbookExportFormat.PDF,
                                    EbookExportFormat.TXT,
                                    EbookExportFormat.HTML,
                                )
                            ) {
                                sendToKindle = false
                            }
                        },
                    )
                }
            }
            AppText("Image optimization")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EbookExportImageOptimization.entries.forEach { item ->
                    SmallTonalButton(
                        text = item.value,
                        selected = imageOptimization == item,
                        onClick = { imageOptimization = item },
                    )
                }
            }
            if (format == EbookExportFormat.CBZ) {
                AppText("Comic exports can be very large. Balanced or Small is recommended for Send to Kindle.")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText("Send to Kindle after export")
                    AppText("Uses the saved Kindle email; your mail app still asks you to confirm sending.")
                }
                Switch(
                    checked = sendToKindle,
                    onCheckedChange = { sendToKindle = it },
                    enabled = format in setOf(
                        EbookExportFormat.EPUB3,
                        EbookExportFormat.PDF,
                        EbookExportFormat.TXT,
                        EbookExportFormat.HTML,
                    ),
                )
            }
            AppText(stringResource(R.string.export_content_source))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EbookExportContentSource.entries.forEach { item ->
                    SmallTonalButton(
                        text = exportContentSourceLabel(item),
                        selected = contentSource == item,
                        onClick = { contentSource = item },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = exportAll, onClick = { exportAll = true })
                AppText(stringResource(R.string.export_all))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !exportAll, onClick = { exportAll = false })
                AppText(stringResource(R.string.export_chapter_index))
            }
            if (!exportAll) {
                AppTextField(
                    value = scope,
                    onValueChange = {
                        scope = it
                        scopeError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.export_chapter_index),
                    placeholder = { AppText("1-10,15,20-25") },
                    isError = scopeError,
                    supportingText = if (scopeError) {
                        { AppText(stringResource(R.string.error_scope_input)) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun exportContentSourceLabel(source: EbookExportContentSource): String {
    return when (source) {
        EbookExportContentSource.ORIGINAL -> stringResource(R.string.export_content_source_original)
        EbookExportContentSource.TRANSLATION -> stringResource(R.string.export_content_source_translation)
        EbookExportContentSource.BOTH -> stringResource(R.string.export_content_source_both)
    }
}

internal fun isValidExportScope(scope: String): Boolean {
    if (scope.isBlank()) return false
    return scope.split(',').all { token ->
        val parts = token.trim().split('-')
        when (parts.size) {
            1 -> parts[0].toIntOrNull()?.let { it > 0 } == true
            2 -> {
                val start = parts[0].toIntOrNull()
                val end = parts[1].toIntOrNull()
                start != null && end != null && start > 0 && end >= start
            }
            else -> false
        }
    }
}
