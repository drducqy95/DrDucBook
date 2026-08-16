package io.legado.app.ui.authoring.ebook

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.domain.model.EbookBlock
import io.legado.app.domain.model.EbookCodeBlock
import io.legado.app.domain.model.EbookDividerBlock
import io.legado.app.domain.model.EbookHeadingBlock
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.domain.model.EbookListBlock
import io.legado.app.domain.model.EbookPageBreakBlock
import io.legado.app.domain.model.EbookParagraphBlock
import io.legado.app.domain.model.EbookQuoteBlock
import io.legado.app.domain.model.blockPlainText
import io.legado.app.domain.model.resolveEbookDocument
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Composable
fun EbookLayerPanel(state: EbookEditorUiState, onIntent: (EbookEditorIntent) -> Unit) {
    AppModalBottomSheet(
        show = state.sheet is EbookEditorSheet.Layers,
        onDismissRequest = { onIntent(EbookEditorIntent.DismissSheet) },
        title = "Layers and reading order",
    ) {
        val blocks = state.project?.resolveEbookDocument()?.chapters
            ?.firstOrNull { it.id == state.selectedChapterId }
            ?.blocks.orEmpty()
            .sortedByDescending { it.geometry?.zIndex ?: 0 }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(blocks, key = EbookBlock::id) { block ->
                var dragDistance by remember(block.id) { mutableFloatStateOf(0f) }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = { onIntent(EbookEditorIntent.SelectBlock(block.id)) },
                            onDoubleClick = { onIntent(EbookEditorIntent.ShowRenameBlock(block.id)) },
                        )
                        .pointerInput(block.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragDistance = 0f
                                    onIntent(EbookEditorIntent.SelectBlock(block.id))
                                },
                                onDragEnd = { dragDistance = 0f },
                                onDragCancel = { dragDistance = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragDistance += amount.y
                                if (kotlin.math.abs(dragDistance) >= LAYER_DRAG_STEP_PX) {
                                    onIntent(EbookEditorIntent.MoveSelectedBlockLayer(if (dragDistance < 0f) 1 else -1))
                                    dragDistance = 0f
                                }
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (block is EbookImageBlock) {
                        AsyncImage(
                            model = block.uri,
                            contentDescription = block.alt,
                            modifier = Modifier.size(48.dp).padding(end = 8.dp),
                        )
                    } else {
                        Text(
                            text = blockTypeName(block),
                            style = LegadoTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(block.name, style = LegadoTheme.typography.titleSmall)
                        Text(
                            blockPlainText(block).take(80),
                            style = LegadoTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                    IconButton(onClick = {
                        onIntent(EbookEditorIntent.SelectBlock(block.id))
                        onIntent(EbookEditorIntent.ToggleSelectedBlockVisibility)
                    }) {
                        Icon(
                            if (block.geometry?.isHidden == true) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Show or hide",
                        )
                    }
                    IconButton(onClick = {
                        onIntent(EbookEditorIntent.SelectBlock(block.id))
                        onIntent(EbookEditorIntent.ToggleSelectedBlockLock)
                    }) {
                        Icon(
                            if (block.geometry?.isLocked == true) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock or unlock",
                        )
                    }
                    Column(verticalArrangement = Arrangement.Center) {
                        IconButton(onClick = {
                            onIntent(EbookEditorIntent.SelectBlock(block.id))
                            onIntent(EbookEditorIntent.MoveSelectedBlockLayer(1))
                        }) { Icon(Icons.Default.ArrowUpward, contentDescription = "Bring forward") }
                        IconButton(onClick = {
                            onIntent(EbookEditorIntent.SelectBlock(block.id))
                            onIntent(EbookEditorIntent.MoveSelectedBlockLayer(-1))
                        }) { Icon(Icons.Default.ArrowDownward, contentDescription = "Send backward") }
                    }
                    Column(verticalArrangement = Arrangement.Center) {
                        IconButton(onClick = {
                            onIntent(EbookEditorIntent.SelectBlock(block.id))
                            onIntent(EbookEditorIntent.MoveSelectedReadingOrder(-1))
                        }) { Icon(Icons.Default.ArrowUpward, contentDescription = "Earlier reading order") }
                        IconButton(onClick = {
                            onIntent(EbookEditorIntent.SelectBlock(block.id))
                            onIntent(EbookEditorIntent.MoveSelectedReadingOrder(1))
                        }) { Icon(Icons.Default.ArrowDownward, contentDescription = "Later reading order") }
                    }
                }
            }
        }
    }
}

private const val LAYER_DRAG_STEP_PX = 48f

private fun blockTypeName(block: EbookBlock): String = when (block) {
    is EbookParagraphBlock -> "Paragraph"
    is EbookHeadingBlock -> "Heading"
    is EbookQuoteBlock -> "Quote"
    is EbookImageBlock -> "Image"
    is EbookDividerBlock -> "Divider"
    is EbookPageBreakBlock -> "Page break"
    is EbookCodeBlock -> "Code"
    is EbookListBlock -> "List"
}
