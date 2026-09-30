package io.legado.app.ui.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.AppText

/** Case operations shared by QT dictionary and translation-memory editors. */
enum class TranslationCaseTransform(val label: String) {
    LOWERCASE("aa"),
    CAPITALIZE_ONE("Aa¹"),
    CAPITALIZE_TWO("Aa²"),
    CAPITALIZE_THREE("Aa³"),
    CAPITALIZE_ALL("Aa"),
    UPPERCASE("AA"),
}

fun applyTranslationCaseTransform(
    value: String,
    transform: TranslationCaseTransform,
): String = when (transform) {
    TranslationCaseTransform.LOWERCASE -> value.lowercase()
    TranslationCaseTransform.UPPERCASE -> value.uppercase()
    TranslationCaseTransform.CAPITALIZE_ONE -> value.capitalizeWords(limit = 1)
    TranslationCaseTransform.CAPITALIZE_TWO -> value.capitalizeWords(limit = 2)
    TranslationCaseTransform.CAPITALIZE_THREE -> value.capitalizeWords(limit = 3)
    TranslationCaseTransform.CAPITALIZE_ALL -> value.capitalizeWords(limit = Int.MAX_VALUE)
}

private fun String.capitalizeWords(limit: Int): String {
    if (isEmpty() || limit <= 0) return this
    var transformed = 0
    return Regex("\\p{L}[\\p{L}\\p{M}]*").replace(this) { match ->
        if (transformed >= limit) match.value
        else {
            transformed += 1
            match.value.replaceFirstChar { it.titlecaseChar() }
        }
    }
}

@Composable
fun TranslationCaseControls(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TranslationCaseTransform.entries.forEach { transform ->
            val description = stringResource(transform.descriptionResource())
            TextButton(
                onClick = { onValueChange(applyTranslationCaseTransform(value, transform)) },
                enabled = value.any(Char::isLetter),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 0.dp, minHeight = 40.dp)
                    .semantics { contentDescription = description },
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            ) {
                AppText(
                    text = transform.label,
                    style = LegadoTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun TranslationCaseTransform.descriptionResource(): Int = when (this) {
    TranslationCaseTransform.LOWERCASE -> R.string.quick_dictionary_case_lowercase
    TranslationCaseTransform.CAPITALIZE_ONE -> R.string.quick_dictionary_case_capitalize_one
    TranslationCaseTransform.CAPITALIZE_TWO -> R.string.quick_dictionary_case_capitalize_two
    TranslationCaseTransform.CAPITALIZE_THREE -> R.string.quick_dictionary_case_capitalize_three
    TranslationCaseTransform.CAPITALIZE_ALL -> R.string.quick_dictionary_case_capitalize_all
    TranslationCaseTransform.UPPERCASE -> R.string.quick_dictionary_case_uppercase
}
