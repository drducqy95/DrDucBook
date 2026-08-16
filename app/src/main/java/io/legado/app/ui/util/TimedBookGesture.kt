package io.legado.app.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics

/**
 * Three-stage book gesture used consistently by bookshelf and discovery cards.
 *
 * A tap opens the reader. Releasing after the platform long-press threshold opens book info.
 * Releasing after [veryLongPressMillis] opens phrase selection. Navigation is intentionally
 * deferred until release so replacing the current Navigation 3 entry cannot cancel the active
 * pointer gesture. Moving farther than touch slop cancels the gesture, so scrolling remains natural.
 */
fun Modifier.timedBookGesture(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onVeryLongClick: (() -> Unit)?,
    veryLongPressMillis: Long = 1_300L,
): Modifier = composed {
    val viewConfiguration = LocalViewConfiguration.current
    val haptic = LocalHapticFeedback.current
    val longPressMillis = viewConfiguration.longPressTimeoutMillis
    this
        .semantics {
            onClick(action = { onClick(); true })
            if (onLongClick != null) {
                onLongClick(action = { onLongClick(); true })
            }
        }
        .pointerInput(onClick, onLongClick, onVeryLongClick, longPressMillis, veryLongPressMillis) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var outcome: BookGestureOutcome = BookGestureOutcome.Cancelled
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial)
                        .changes
                        .firstOrNull { it.id == down.id }
                        ?: break
                    if (!change.pressed) {
                        outcome = BookGestureOutcome.Released(
                            change.uptimeMillis - down.uptimeMillis
                        )
                        break
                    }
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        break
                    }
                }
                when (outcome) {
                    BookGestureOutcome.Cancelled -> Unit
                    is BookGestureOutcome.Released -> when (
                        classifyBookGesture(
                            heldMillis = outcome.heldMillis,
                            longPressMillis = longPressMillis,
                            veryLongPressMillis = veryLongPressMillis,
                        )
                    ) {
                        BookGestureAction.OPEN_READER -> onClick()
                        BookGestureAction.OPEN_INFO -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick?.invoke()
                        }

                        BookGestureAction.SELECT_PHRASE -> {
                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            onVeryLongClick?.invoke()
                        }
                    }
                }
            }
        }
}

internal enum class BookGestureAction {
    OPEN_READER,
    OPEN_INFO,
    SELECT_PHRASE,
}

internal fun classifyBookGesture(
    heldMillis: Long,
    longPressMillis: Long,
    veryLongPressMillis: Long = 1_300L,
): BookGestureAction = when {
    heldMillis < longPressMillis -> BookGestureAction.OPEN_READER
    heldMillis < veryLongPressMillis -> BookGestureAction.OPEN_INFO
    else -> BookGestureAction.SELECT_PHRASE
}

private sealed interface BookGestureOutcome {
    data object Cancelled : BookGestureOutcome
    data class Released(val heldMillis: Long) : BookGestureOutcome
}
