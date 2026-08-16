package io.legado.app.ui.quickdict

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.domain.model.MappedDisplayText
import io.legado.app.domain.model.alignedParagraphMapping
import io.legado.app.ui.widget.components.AppTextField
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
fun QuickDictionarySelectableText(
    displayText: String,
    sourceText: String,
    bookUrl: String,
    sourceLocation: String,
    onQuickDictionaryRequest: (QuickDictionaryRequest) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    mappedDisplayText: MappedDisplayText = alignedParagraphMapping(
        sourceText,
        displayText,
        "display",
    ),
) {
    var fieldValue by remember(displayText) { mutableStateOf(TextFieldValue(displayText)) }
    val actionLabel = stringResource(com.drducbook.app.R.string.quick_dictionary_add)
    val textStyle = if (color == Color.Unspecified) style else style.copy(color = color)

    BasicTextField(
        value = fieldValue,
        onValueChange = { next -> fieldValue = next.copy(text = displayText) },
        readOnly = true,
        textStyle = textStyle,
        maxLines = maxLines,
        modifier = modifier.appendTextContextMenuComponents {
            item(
                key = QuickDictionaryContextMenuKey,
                label = actionLabel,
            ) {
                val selection = fieldValue.selection
                val start = selection.min.coerceIn(0, displayText.length)
                val end = selection.max.coerceIn(start, displayText.length)
                if (end > start) {
                    onQuickDictionaryRequest(
                        QuickDictionaryRequest(
                            bookUrl = bookUrl,
                            selectedText = displayText.substring(start, end),
                            sourceText = sourceText,
                            displayText = displayText,
                            selectionStart = start,
                            selectionEnd = end,
                            sourceLocation = sourceLocation,
                            mappedDisplayText = mappedDisplayText,
                        )
                    )
                }
                close()
            }
        },
        decorationBox = { innerTextField ->
            androidx.compose.foundation.layout.Box {
                innerTextField()
            }
        },
    )
}

@Composable
fun QuickDictionaryEditableTextField(
    value: String,
    sourceText: String = value,
    onValueChange: (String) -> Unit,
    label: String,
    bookUrl: String,
    sourceLocation: String,
    onQuickDictionaryRequest: (QuickDictionaryRequest) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    mappedDisplayText: MappedDisplayText = alignedParagraphMapping(
        sourceText,
        value,
        "editor",
    ),
) {
    val textState = rememberTextFieldState(initialText = value)
    val actionLabel = stringResource(com.drducbook.app.R.string.quick_dictionary_add)

    LaunchedEffect(value) {
        if (textState.text.toString() != value) {
            textState.setTextAndPlaceCursorAtEnd(value)
        }
    }
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .distinctUntilChanged()
            .drop(1)
            .collect(onValueChange)
    }

    AppTextField(
        state = textState,
        label = label,
        backgroundColor = LegadoTheme.colorScheme.surfaceInput,
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
        modifier = modifier.appendTextContextMenuComponents {
            item(
                key = QuickDictionaryContextMenuKey,
                label = actionLabel,
            ) {
                val displayText = textState.text.toString()
                val selection = textState.selection
                val start = selection.min.coerceIn(0, displayText.length)
                val end = selection.max.coerceIn(start, displayText.length)
                if (end > start) {
                    onQuickDictionaryRequest(
                        QuickDictionaryRequest(
                            bookUrl = bookUrl,
                            selectedText = displayText.substring(start, end),
                            sourceText = sourceText,
                            displayText = displayText,
                            selectionStart = start,
                            selectionEnd = end,
                            sourceLocation = sourceLocation,
                            mappedDisplayText = mappedDisplayText,
                        )
                    )
                }
                close()
            }
        },
    )
}

private data object QuickDictionaryContextMenuKey
