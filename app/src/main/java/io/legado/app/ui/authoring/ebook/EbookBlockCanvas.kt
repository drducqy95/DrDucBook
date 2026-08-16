package io.legado.app.ui.authoring.ebook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.domain.model.EbookBlock
import io.legado.app.domain.model.EbookDocument
import io.legado.app.domain.model.EbookImageBlock
import io.legado.app.domain.model.EbookPageSize
import io.legado.app.domain.model.blockPlainText
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider

@Composable
fun EbookBlockCanvas(
    document: EbookDocument,
    chapterId: String?,
    selectedBlockId: String?,
    selectedBlockIds: Set<String> = selectedBlockId?.let(::setOf).orEmpty(),
    onIntent: (EbookEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapter = document.chapters.firstOrNull { it.id == chapterId } ?: return
    val page = document.pageSize ?: EbookPageSize()
    var zoom by remember { mutableFloatStateOf(1f) }
    val focusRequester = remember { FocusRequester() }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppSlider(
            value = zoom,
            onValueChange = { zoom = it },
            valueRange = 0.5f..2f,
            steps = 5,
            accessibilityLabel = "Canvas zoom",
            accessibilityValue = "${(zoom * 100).toInt()}%",
            modifier = Modifier.fillMaxWidth(),
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().clipToBounds()
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
        ) {
            val baseScale = (maxWidth.value / page.width).coerceAtMost(1f)
            val scale = baseScale * zoom
             val density = LocalDensity.current
             Box(
                 modifier = Modifier
                    .width((page.width * scale).dp)
                     .height((page.height * scale).dp)
                     .background(Color.White)
                     .border(1.dp, LegadoTheme.colorScheme.outlineVariant)
                     .focusRequester(focusRequester)
                     .focusable()
                     .onPreviewKeyEvent { event ->
                         if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                         when (event.key) {
                             Key.DirectionLeft -> EbookEditorIntent.MoveSelectedBlock(-GRID_KEY_STEP, 0f)
                             Key.DirectionRight -> EbookEditorIntent.MoveSelectedBlock(GRID_KEY_STEP, 0f)
                             Key.DirectionUp -> EbookEditorIntent.MoveSelectedBlock(0f, -GRID_KEY_STEP)
                             Key.DirectionDown -> EbookEditorIntent.MoveSelectedBlock(0f, GRID_KEY_STEP)
                             Key.Delete, Key.Backspace -> EbookEditorIntent.DeleteSelectedBlock
                             Key.Tab -> EbookEditorIntent.SelectNextBlock(1)
                             else -> return@onPreviewKeyEvent false
                         }.let(onIntent)
                         true
                     },
             ) {
                chapter.blocks.sortedBy { it.geometry?.zIndex ?: it.readingOrder }.forEach { block ->
                    val geometry = block.geometry ?: return@forEach
                    if (geometry.isHidden || geometry.page != 0) return@forEach
                     val selected = block.id in selectedBlockIds || block.id == selectedBlockId
                     Box(
                         modifier = Modifier
                            .offset((geometry.x * scale).dp, (geometry.y * scale).dp)
                            .width((geometry.width * scale).dp)
                            .height((geometry.height * scale).dp)
                            .graphicsLayer(rotationZ = geometry.rotation)
                            .background(Color.White)
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) LegadoTheme.colorScheme.primary else Color.LightGray,
                            )
                             .semantics {
                                 contentDescription = "${block.name}, reading order ${block.readingOrder + 1}"
                                 onClick {
                                     onIntent(EbookEditorIntent.SelectBlock(block.id))
                                     true
                                 }
                             }
                             .pointerInput(block.id) {
                                 detectTapGestures(
                                     onTap = {
                                         focusRequester.requestFocus()
                                         onIntent(EbookEditorIntent.SelectBlock(block.id))
                                     },
                                     onLongPress = {
                                         focusRequester.requestFocus()
                                         onIntent(EbookEditorIntent.ToggleBlockSelection(block.id))
                                     },
                                 )
                             }
                             .pointerInput(block.id, geometry.isLocked, scale) {
                                 detectDragGestures(
                                     onDragStart = {
                                         focusRequester.requestFocus()
                                         if (block.id !in selectedBlockIds) {
                                             onIntent(EbookEditorIntent.SelectBlock(block.id))
                                         }
                                     },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (!geometry.isLocked) {
                                            onIntent(
                                                EbookEditorIntent.MoveSelectedBlock(
                                                    dx = amount.x / density.density / scale,
                                                    dy = amount.y / density.density / scale,
                                                )
                                            )
                                        }
                                    },
                                )
                            }
                            .padding(6.dp),
                    ) {
                         BlockPreview(block)
                         if (block.id == selectedBlockId && !geometry.isLocked) {
                             RESIZE_HANDLES.forEach { (handle, alignment) ->
                                 ResizeHandle(
                                     handle = handle,
                                     alignment = alignment,
                                     blockId = block.id,
                                     scale = scale,
                                     density = density.density,
                                     onIntent = onIntent,
                                 )
                             }
                             Box(
                                 modifier = Modifier.align(Alignment.TopCenter).offset(y = (-22).dp).size(18.dp)
                                     .background(LegadoTheme.colorScheme.tertiary)
                                     .pointerInput(block.id) {
                                        detectDragGestures { change, amount ->
                                            change.consume()
                                            onIntent(EbookEditorIntent.RotateSelectedBlock(amount.x / 4f))
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ResizeHandle(
    handle: EbookResizeHandle,
    alignment: Alignment,
    blockId: String,
    scale: Float,
    density: Float,
    onIntent: (EbookEditorIntent) -> Unit,
) {
    Box(
        modifier = Modifier.align(alignment).size(14.dp)
            .background(LegadoTheme.colorScheme.primary)
            .pointerInput(blockId, handle, scale) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onIntent(
                        EbookEditorIntent.ResizeSelectedBlockFromHandle(
                            handle = handle,
                            dx = amount.x / density / scale,
                            dy = amount.y / density / scale,
                        )
                    )
                }
            },
    )
}

@Composable
private fun BlockPreview(block: EbookBlock) {
    if (block is EbookImageBlock) {
        AsyncImage(
            model = block.uri,
            contentDescription = block.alt,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Text(
            text = blockPlainText(block).ifBlank { block.name },
            color = Color.Black,
            style = LegadoTheme.typography.bodyMedium,
        )
    }
}

private const val GRID_KEY_STEP = 8f

private val RESIZE_HANDLES = listOf(
    EbookResizeHandle.TOP_LEFT to Alignment.TopStart,
    EbookResizeHandle.TOP to Alignment.TopCenter,
    EbookResizeHandle.TOP_RIGHT to Alignment.TopEnd,
    EbookResizeHandle.LEFT to Alignment.CenterStart,
    EbookResizeHandle.RIGHT to Alignment.CenterEnd,
    EbookResizeHandle.BOTTOM_LEFT to Alignment.BottomStart,
    EbookResizeHandle.BOTTOM to Alignment.BottomCenter,
    EbookResizeHandle.BOTTOM_RIGHT to Alignment.BottomEnd,
)
