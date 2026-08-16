package io.legado.app.ui.ai.bubble

import android.app.Activity
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.drducbook.app.R
import io.legado.app.help.config.AiChatBubbleConfig
import io.legado.app.ui.theme.AppTheme

class ChatBubbleHost private constructor(
    val activity: Activity,
    private val parent: ViewGroup,
    private val view: ComposeView,
    private val bubbleSizePx: Int,
    private val bubbleMarginPx: Int,
) {

    fun detach() {
        parent.removeView(view)
    }

    fun restorePosition() {
        val bounds = currentBounds() ?: return
        val position = ChatBubblePositioner.restore(
            normalized = AiChatBubbleConfig.getPosition(activity.chatBubbleOrientation),
            bounds = bounds,
        )
        setPosition(position)
    }

    private fun moveBy(deltaX: Float, deltaY: Float) {
        val bounds = currentBounds() ?: return
        setPosition(
            ChatBubblePositioner.clamp(
                ChatBubblePixelPosition(
                    x = view.x + deltaX,
                    y = view.y + deltaY,
                ),
                bounds,
            )
        )
    }

    private fun snapAndPersist() {
        val bounds = currentBounds() ?: return
        val snapped = ChatBubblePositioner.snapToNearestEdge(
            position = ChatBubblePixelPosition(view.x, view.y),
            bounds = bounds,
        )
        setPosition(snapped)
        AiChatBubbleConfig.savePosition(
            orientation = activity.chatBubbleOrientation,
            position = ChatBubblePositioner.normalize(snapped, bounds),
        )
    }

    private fun setPosition(position: ChatBubblePixelPosition) {
        view.x = position.x
        view.y = position.y
    }

    private fun currentBounds(): ChatBubblePixelBounds? {
        if (parent.width <= 0 || parent.height <= 0) return null
        val insets = ViewCompat.getRootWindowInsets(parent)
            ?.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
        val margin = bubbleMarginPx.toFloat()
        val minX = (insets?.left ?: 0).toFloat() + margin
        val minY = (insets?.top ?: 0).toFloat() + margin
        val maxX = parent.width - (insets?.right ?: 0) - margin - bubbleSizePx
        val maxY = parent.height - (insets?.bottom ?: 0) - margin - bubbleSizePx
        return ChatBubblePixelBounds(
            minX = minX,
            minY = minY,
            maxX = maxX.coerceAtLeast(minX),
            maxY = maxY.coerceAtLeast(minY),
        )
    }

    companion object {
        private const val BUBBLE_SIZE_DP = 56
        private const val BUBBLE_MARGIN_DP = 16

        fun attach(
            activity: Activity,
            onOpenChat: () -> Unit,
            onHideSession: () -> Unit,
            onDisableBubble: () -> Unit,
        ): ChatBubbleHost {
            val parent = activity.window.decorView as ViewGroup
            val density = activity.resources.displayMetrics.density
            val size = (BUBBLE_SIZE_DP * density).toInt()
            val margin = (BUBBLE_MARGIN_DP * density).toInt()
            lateinit var host: ChatBubbleHost
            val view = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    AppTheme {
                        ChatBubbleButton(
                            onOpenChat = onOpenChat,
                            onHideSession = onHideSession,
                            onDisableBubble = onDisableBubble,
                            onDrag = { deltaX, deltaY -> host.moveBy(deltaX, deltaY) },
                            onDragEnd = { host.snapAndPersist() },
                        )
                    }
                }
            }
            val params = FrameLayout.LayoutParams(size, size)
            parent.addView(view, params)
            host = ChatBubbleHost(activity, parent, view, size, margin)
            view.post { host.restorePosition() }
            return host
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubbleButton(
    onOpenChat: () -> Unit,
    onHideSession: () -> Unit,
    onDisableBubble: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var panelExpanded by remember { mutableStateOf(false) }
    val sessionState by ChatBubbleSessionStore.state.collectAsState()
    Box {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .combinedClickable(
                    onClick = {
                        ChatBubbleSessionStore.markSeen()
                        panelExpanded = true
                    },
                    onLongClick = { menuExpanded = true },
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                BadgedBox(
                    badge = {
                        if (
                            sessionState.unreadCount > 0 ||
                            sessionState.status != ChatBubbleAgentStatus.IDLE
                        ) {
                            Badge {
                                Text(
                                    when (sessionState.status) {
                                        ChatBubbleAgentStatus.RUNNING -> "…"
                                        ChatBubbleAgentStatus.WAITING_APPROVAL -> "!"
                                        ChatBubbleAgentStatus.ERROR -> "!"
                                        ChatBubbleAgentStatus.IDLE -> sessionState.unreadCount.toString()
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = stringResource(R.string.ai_chat),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ai_chat_bubble_open_full_screen)) },
                onClick = {
                    menuExpanded = false
                    onOpenChat()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ai_chat_bubble_hide_session)) },
                onClick = {
                    menuExpanded = false
                    onHideSession()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ai_chat_bubble_turn_off)) },
                onClick = {
                    menuExpanded = false
                    onDisableBubble()
                },
            )
        }
        ChatBubblePanel(
            show = panelExpanded,
            state = sessionState,
            onDismiss = { panelExpanded = false },
            onOpenFullScreen = onOpenChat,
        )
    }
}

private val Activity.chatBubbleOrientation: ChatBubbleOrientation
    get() {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ChatBubbleOrientation.LANDSCAPE
        } else {
            ChatBubbleOrientation.PORTRAIT
        }
    }
