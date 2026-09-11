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
import io.legado.app.ui.ai.router.normalizeAiRouterSearch
import io.legado.app.ui.config.ai.prompt.AI_PROMPT_SELECTION_MODEL_PREFIX
import io.legado.app.ui.config.ai.prompt.AI_PROMPT_SELECTION_ROUTE_PREFIX
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SettingItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Stable
sealed interface AiComboModelPickerItemUi {
    val id: String
    val displayName: String

    @Stable
    data class ComboItem(
        override val id: String,
        override val displayName: String,
        val targetCount: Int = 0,
        val maxAttempts: Int = 0,
        val taskType: String = "",
        val isDefault: Boolean = false,
    ) : AiComboModelPickerItemUi

    @Stable
    data class ModelItem(
        override val id: String,
        override val displayName: String,
        val providerName: String,
        val modelName: String,
        val modelId: String,
        val contextWindow: Int = 0,
        val maxOutputTokens: Int = 0,
        val isMissing: Boolean = false,
        val isStale: Boolean = false,
    ) : AiComboModelPickerItemUi
}

@Composable
internal fun AiComboModelPickerSheet(
    show: Boolean,
    title: String,
    selectedId: String,
    combos: ImmutableList<AiComboModelPickerItemUi.ComboItem>,
    models: ImmutableList<AiComboModelPickerItemUi.ModelItem>,
    onDismissRequest: () -> Unit,
    onSelectCombo: (AiComboModelPickerItemUi.ComboItem) -> Unit,
    onSelectModel: (AiComboModelPickerItemUi.ModelItem) -> Unit,
) {
    var query by remember(show) { mutableStateOf("") }
    val unknownProvider = stringResource(R.string.ai_model_provider_unknown)
    val (filteredCombos, filteredModels) = remember(combos, models, query) {
        filterComboModelPickerItems(combos, models, query)
    }
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
                if (filteredCombos.isEmpty() && filteredModels.isEmpty()) {
                    item(key = "empty", contentType = "message") {
                        SettingItem(
                            title = stringResource(R.string.ai_model_picker_empty),
                        )
                    }
                } else {
                    // --- SECTION 1: COMBOS ---
                    if (filteredCombos.isNotEmpty()) {
                        item(key = "header_combos", contentType = "header") {
                            PillHeaderDivider(title = stringResource(R.string.ai_prompt_editor_fallback_combo))
                        }
                        items(
                            items = filteredCombos,
                            key = { combo -> "combo_${combo.id}" },
                            contentType = { "combo" },
                        ) { combo ->
                            val isSelected = selectedId == combo.id ||
                                selectedId == AI_PROMPT_SELECTION_ROUTE_PREFIX + combo.id ||
                                selectedId == "route:${combo.id}"
                            ClickableSettingItem(
                                title = combo.displayName,
                                description = stringResource(
                                    R.string.ai_prompt_editor_fallback_combo_summary,
                                    combo.targetCount,
                                    combo.maxAttempts,
                                ),
                                trailingContent = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else null,
                                onClick = {
                                    onSelectCombo(combo)
                                    onDismissRequest()
                                },
                            )
                        }
                    }

                    // --- SECTION 2: STANDALONE MODELS GROUPED BY PROVIDER ---
                    if (filteredModels.isNotEmpty()) {
                        item(key = "header_models_section", contentType = "header") {
                            PillHeaderDivider(title = stringResource(R.string.ai_picker_section_models))
                        }
                        groupedModels.forEach { (providerName, providerModels) ->
                            item(key = "provider_$providerName", contentType = "header") {
                                PillHeaderDivider(title = providerName)
                            }
                            items(
                                items = providerModels,
                                key = { model -> "model_${model.id}" },
                                contentType = { "model" },
                            ) { model ->
                                val isSelected = selectedId == model.id ||
                                    selectedId == AI_PROMPT_SELECTION_MODEL_PREFIX + model.id ||
                                    selectedId == "model:${model.id}"
                                val missingLabel = stringResource(R.string.ai_model_missing_catalog)
                                val staleLabel = stringResource(R.string.ai_model_stale_warning)
                                val displayTitle = when {
                                    model.isStale -> "${model.modelName} ($staleLabel)"
                                    model.isMissing -> "${model.modelName} ($missingLabel)"
                                    else -> model.modelName
                                }
                                ClickableSettingItem(
                                    title = displayTitle,
                                    description = model.descriptionText(),
                                    trailingContent = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        onSelectModel(model)
                                        onDismissRequest()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun filterComboModelPickerItems(
    combos: List<AiComboModelPickerItemUi.ComboItem>,
    models: List<AiComboModelPickerItemUi.ModelItem>,
    query: String,
): Pair<List<AiComboModelPickerItemUi.ComboItem>, List<AiComboModelPickerItemUi.ModelItem>> {
    val normalizedQuery = normalizeAiRouterSearch(query.trim())
    if (normalizedQuery.isBlank()) return combos to models

    val filteredCombos = combos.filter { combo ->
        normalizeAiRouterSearch(combo.displayName).contains(normalizedQuery) ||
            normalizeAiRouterSearch(combo.taskType).contains(normalizedQuery)
    }

    val filteredModels = models.filter { model ->
        normalizeAiRouterSearch(model.providerName).contains(normalizedQuery) ||
            normalizeAiRouterSearch(model.modelName).contains(normalizedQuery) ||
            normalizeAiRouterSearch(model.modelId).contains(normalizedQuery)
    }

    return filteredCombos to filteredModels
}

@Composable
private fun AiComboModelPickerItemUi.ModelItem.descriptionText(): String {
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