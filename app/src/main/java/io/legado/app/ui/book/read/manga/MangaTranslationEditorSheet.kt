package io.legado.app.ui.book.read.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun MangaTranslationEditorSheet(
    state: MangaTranslationEditorUiState,
    onIntent: (MangaTranslationEditorIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = state.visible,
        onDismissRequest = { onIntent(MangaTranslationEditorIntent.Dismiss) },
        title = stringResource(R.string.manga_translation_editor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.sourceText,
                onValueChange = {},
                readOnly = true,
                label = { AppText(stringResource(R.string.translation_revision_raw)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.translatedText,
                onValueChange = { onIntent(MangaTranslationEditorIntent.SetTranslatedText(it)) },
                label = { AppText(stringResource(R.string.translation_revision_text)) },
                modifier = Modifier.fillMaxWidth(),
            )
            AppText(stringResource(R.string.manga_translation_font_size, state.fontSizeSp.toInt()))
            Slider(
                value = state.fontSizeSp,
                onValueChange = { onIntent(MangaTranslationEditorIntent.SetFontSize(it)) },
                valueRange = 8f..48f,
                steps = 39,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onIntent(MangaTranslationEditorIntent.CycleTextColor) }) {
                        Icon(Icons.Default.Palette, stringResource(R.string.manga_translation_text_color))
                    }
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(Color(state.textColor))
                    )
                    IconButton(onClick = { onIntent(MangaTranslationEditorIntent.CycleBackgroundColor) }) {
                        Icon(Icons.Default.Palette, stringResource(R.string.manga_translation_background))
                    }
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(Color(state.backgroundColor))
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onIntent(MangaTranslationEditorIntent.ChangeOrder(-1)) }) {
                        AppText("−")
                    }
                    AppText((state.order + 1).toString())
                    IconButton(onClick = { onIntent(MangaTranslationEditorIntent.ChangeOrder(1)) }) {
                        AppText("+")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = { onIntent(MangaTranslationEditorIntent.Dismiss) }) {
                    AppText(stringResource(android.R.string.cancel))
                }
                Button(onClick = { onIntent(MangaTranslationEditorIntent.Save) }) {
                    AppText(stringResource(R.string.save))
                }
            }
        }
    }
}
