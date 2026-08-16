package io.legado.app.ui.authoring.ebook

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.resolveEbookDocument
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Composable
fun EbookChapterManagerSheet(state: EbookEditorUiState, onIntent: (EbookEditorIntent) -> Unit) {
    AppModalBottomSheet(
        show = state.sheet is EbookEditorSheet.ChapterManager,
        onDismissRequest = { onIntent(EbookEditorIntent.DismissSheet) },
        title = "Chapter manager",
    ) {
        val selected = state.project?.resolveEbookDocument()?.chapters
            ?.firstOrNull { it.id == state.selectedChapterId }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected != null) {
                OutlinedTextField(
                    value = selected.subtitle,
                    onValueChange = { onIntent(EbookEditorIntent.UpdateChapterSubtitle(it)) },
                    label = { Text("Subtitle") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Page break before")
                    Switch(
                        checked = selected.pageBreakBefore,
                        onCheckedChange = { onIntent(EbookEditorIntent.UpdateChapterPageBreakBefore(it)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Auto renumber chapters")
                Switch(
                    checked = state.autoRenumberChapters,
                    onCheckedChange = { onIntent(EbookEditorIntent.SetAutoRenumberChapters(it)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onIntent(EbookEditorIntent.AddChapter) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Insert")
                }
                Button(onClick = { onIntent(EbookEditorIntent.SplitChapter) }) {
                    Icon(Icons.Default.CallSplit, contentDescription = null)
                    Text("Split")
                }
                Button(onClick = { onIntent(EbookEditorIntent.MergeWithNextChapter) }) {
                    Icon(Icons.Default.CallMerge, contentDescription = null)
                    Text("Merge next")
                }
                if (state.selectedChapterIds.size > 1) {
                    Button(onClick = { onIntent(EbookEditorIntent.RequestDeleteSelectedChapters) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("Delete ${state.selectedChapterIds.size}")
                    }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.project?.chapters.orEmpty(), key = { it.id }) { chapter ->
                    var dragDistance by remember(chapter.id) { mutableFloatStateOf(0f) }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(
                                if (chapter.id in state.selectedChapterIds) {
                                    LegadoTheme.colorScheme.secondaryContainer
                                } else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = { onIntent(EbookEditorIntent.SelectChapter(chapter.id)) },
                                onLongClick = { onIntent(EbookEditorIntent.ToggleChapterSelection(chapter.id)) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(chapter.title, style = LegadoTheme.typography.titleSmall)
                            Text("${chapter.content.length} characters", style = LegadoTheme.typography.bodySmall)
                        }
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            modifier = Modifier.pointerInput(chapter.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragDistance = 0f
                                        onIntent(EbookEditorIntent.SelectChapter(chapter.id))
                                    },
                                    onDragEnd = { dragDistance = 0f },
                                    onDragCancel = { dragDistance = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragDistance += amount.y
                                    if (kotlin.math.abs(dragDistance) >= CHAPTER_DRAG_STEP_PX) {
                                        onIntent(EbookEditorIntent.MoveChapter(if (dragDistance < 0f) -1 else 1))
                                        dragDistance = 0f
                                    }
                                }
                            },
                        )
                        IconButton(onClick = {
                            onIntent(EbookEditorIntent.SelectChapter(chapter.id))
                            onIntent(EbookEditorIntent.MoveChapter(-1))
                        }) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                        IconButton(onClick = {
                            onIntent(EbookEditorIntent.SelectChapter(chapter.id))
                            onIntent(EbookEditorIntent.MoveChapter(1))
                        }) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down") }
                        IconButton(onClick = {
                            onIntent(EbookEditorIntent.SelectChapter(chapter.id))
                            onIntent(EbookEditorIntent.RequestDeleteChapter)
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
    }
}

private const val CHAPTER_DRAG_STEP_PX = 48f
