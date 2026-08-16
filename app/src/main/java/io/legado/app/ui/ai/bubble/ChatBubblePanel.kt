package io.legado.app.ui.ai.bubble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.drducbook.app.R
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import kotlin.math.roundToInt

@Composable
fun ChatBubblePanel(
    show: Boolean,
    state: ChatBubbleSessionSnapshot,
    onDismiss: () -> Unit,
    onOpenFullScreen: () -> Unit,
) {
    if (!show) return
    var draft by rememberSaveable { mutableStateOf("") }
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.roundToPx() }
    val popupWidth = (configuration.screenWidthDp - 32).coerceIn(280, 420).dp
    val popupMaxHeight = (configuration.screenHeightDp - 64).coerceIn(320, 560).dp
    val positionProvider = remember(marginPx, dragOffset) {
        ChatBubblePipPositionProvider(marginPx, dragOffset)
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .width(popupWidth)
                .heightIn(min = 240.dp, max = popupMaxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { change, amount ->
                                change.consume()
                                dragOffset += IntOffset(
                                    amount.x.roundToInt(),
                                    amount.y.roundToInt(),
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.ai_chat),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .weight(1f),
                    )
                    IconButton(
                        onClick = {
                            ChatBubbleSessionStore.markSeen()
                            onDismiss()
                            onOpenFullScreen()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = stringResource(R.string.ai_chat_bubble_open_full_screen),
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                }
                Text(
                    text = state.status.statusLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.messages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_chat_bubble_no_active_messages),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.messages.forEach { message ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Text(
                                text = "${message.role}: ${message.content}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
                if (state.chatReady) {
                    AppTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.ai_chat),
                        minLines = 2,
                        maxLines = 4,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.menu_send),
                        enabled = draft.isNotBlank() && state.status != ChatBubbleAgentStatus.RUNNING,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (ChatBubbleSessionStore.requestSend(draft)) {
                                draft = ""
                            }
                        },
                    )
                }
                if (state.status == ChatBubbleAgentStatus.WAITING_APPROVAL) {
                    PrimaryButton(
                        text = stringResource(R.string.ai_chat_bubble_open_full_screen),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ChatBubbleSessionStore.markSeen()
                            onDismiss()
                            onOpenFullScreen()
                        },
                    )
                }
                if (state.status == ChatBubbleAgentStatus.RUNNING) {
                    SecondaryButton(
                        text = stringResource(R.string.stop),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ChatBubbleSessionStore.requestCancel()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

private class ChatBubblePipPositionProvider(
    private val marginPx: Int,
    private val dragOffset: IntOffset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val base = ChatBubblePositioner.positionPip(
            anchor = ChatBubbleRect(
                left = anchorBounds.left,
                top = anchorBounds.top,
                right = anchorBounds.right,
                bottom = anchorBounds.bottom,
            ),
            windowWidth = windowSize.width,
            windowHeight = windowSize.height,
            popupWidth = popupContentSize.width,
            popupHeight = popupContentSize.height,
            margin = marginPx,
        )
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        return IntOffset(
            x = (base.x.roundToInt() + dragOffset.x).coerceIn(marginPx, maxX),
            y = (base.y.roundToInt() + dragOffset.y).coerceIn(marginPx, maxY),
        )
    }
}

@Composable
private fun ChatBubbleAgentStatus.statusLabel(): String = when (this) {
    ChatBubbleAgentStatus.IDLE -> stringResource(R.string.ai_chat_bubble_status_idle)
    ChatBubbleAgentStatus.RUNNING -> stringResource(R.string.ai_chat_bubble_status_running)
    ChatBubbleAgentStatus.WAITING_APPROVAL -> stringResource(R.string.ai_chat_bubble_status_approval)
    ChatBubbleAgentStatus.ERROR -> stringResource(R.string.ai_chat_bubble_status_error)
}
