package io.legado.app.ui.config.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.ai.router.normalizeAiRouterSearch
import kotlinx.collections.immutable.ImmutableList

@Stable
internal data class AiModelPickerOptionUi(
    val id: String,
    val providerName: String,
    val modelName: String,
    val modelId: String,
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
    val isMissing: Boolean = false,
)

@Composable
internal fun AiModelPickerSheet(
    show: Boolean,
    title: String,
    selectedModelId: String,
    models: ImmutableList<AiModelPickerOptionUi>,
    onDismissRequest: () -> Unit,
    onSelect: (AiModelPickerOptionUi) -> Unit,
    onManualEntry: (() -> Unit)? = null,
) {
    var query by remember(show) { mutableStateOf("") }
    val unknownProvider = stringResource(R.string.ai_model_provider_unknown)
    val filteredModels = remember(models, query) { filterAiModelPickerOptions(models, query) }
    val groupedModels = remember(filteredModels, unknownProvider) {
        filteredModels
            .groupBy { model -> model.providerName.ifBlank { unknownProvider } }
            .entries
            .sortedBy { it.key.lowercase() }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
    ) {
        Column {
            SearchBar(
                query = query,
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.ai_model_search_hint),
                autoFocus = false,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                if (filteredModels.isEmpty()) {
                    item(key = "empty", contentType = "message") {
                        SettingItem(
                            title = stringResource(R.string.ai_model_picker_empty),
                        )
                    }
                } else {
                    groupedModels.forEach { (providerName, providerModels) ->
                        item(key = "provider_$providerName", contentType = "header") {
                            PillHeaderDivider(title = providerName)
                        }
                        items(
                            items = providerModels,
                            key = { model -> "model_${model.id}" },
                            contentType = { "model" },
                        ) { model ->
                            val missingLabel = stringResource(R.string.ai_model_missing_catalog)
                            val displayTitle = if (model.isMissing) {
                                "${model.modelName} ($missingLabel)"
                            } else {
                                model.modelName
                            }
                            ClickableSettingItem(
                                title = displayTitle,
                                description = model.descriptionText(),
                                trailingContent = if (model.id == selectedModelId) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    onSelect(model)
                                    onDismissRequest()
                                },
                            )
                        }
                    }
                }
                onManualEntry?.let { manualEntry ->
                    item(key = "manual_entry", contentType = "command") {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_add_model_manually),
                            onClick = {
                                manualEntry()
                                onDismissRequest()
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun filterAiModelPickerOptions(
    models: List<AiModelPickerOptionUi>,
    query: String,
): List<AiModelPickerOptionUi> {
    val normalizedQuery = normalizeAiRouterSearch(query.trim())
    if (normalizedQuery.isBlank()) return models
    return models.filter { model ->
        normalizeAiRouterSearch(model.providerName).contains(normalizedQuery) ||
            normalizeAiRouterSearch(model.modelName).contains(normalizedQuery) ||
            normalizeAiRouterSearch(model.modelId).contains(normalizedQuery)
    }
}

@Composable
private fun AiModelPickerOptionUi.descriptionText(): String {
    val budget = when {
        contextWindow > 0 && maxOutputTokens > 0 -> stringResource(
            R.string.ai_model_picker_budget,
            formatTokenLimit(contextWindow),
            formatTokenLimit(maxOutputTokens),
        )

        contextWindow > 0 -> stringResource(
            R.string.ai_model_picker_context_budget,
            formatTokenLimit(contextWindow),
        )

        maxOutputTokens > 0 -> stringResource(
            R.string.ai_model_picker_output_budget,
            formatTokenLimit(maxOutputTokens),
        )

        else -> ""
    }
    return listOf(modelId, budget)
        .filter(String::isNotBlank)
        .joinToString("\n")
}
