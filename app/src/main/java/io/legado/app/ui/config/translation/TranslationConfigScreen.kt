package io.legado.app.ui.config.translation

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.data.appDb
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.AiRouterSnapshot
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryUniverse
import io.legado.app.domain.model.ExternalAssetCatalog
import io.legado.app.domain.model.QuickTranslationPronounMode
import io.legado.app.domain.model.quickDictionaryUniverseKey
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.translation.HachimiOnnxImportPhase
import io.legado.app.model.translation.HachimiOnnxImportProgress
import io.legado.app.model.translation.HachimiOnnxModelImporter
import io.legado.app.model.translation.HachimiOnnxModelRegistry
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.config.ai.prompt.AI_PROMPT_SELECTION_MODEL_PREFIX
import io.legado.app.ui.config.ai.prompt.AI_PROMPT_SELECTION_ROUTE_PREFIX
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.openUrl
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.GSON
import io.legado.app.utils.FilteredOpenDocumentContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationConfigScreen(
    onBackClick: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToAiPromptEditor: () -> Unit,
    onNavigateToPrompts: () -> Unit,
    onNavigateToQuickDictionary: (requestImportFile: Boolean) -> Unit,
    onNavigateToMlKitModels: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val translateChapterUseCase: TranslateChapterUseCase = koinInject()
    val quickDictionaryGateway: QuickDictionaryGateway = koinInject()
    val quickTranslationGateway: QuickTranslationGateway = koinInject()
    val aiProfileGateway: AiProfileGateway = koinInject()
    val aiRouterGateway: AiRouterGateway = koinInject()
    val aiPresets by aiProfileGateway.observePresets().collectAsState(initial = emptyList())
    val aiProviders by aiProfileGateway.observeProviders().collectAsState(initial = emptyList())
    val aiModels by aiProfileGateway.observeModels().collectAsState(initial = emptyList())
    val aiRouterSnapshot by aiRouterGateway.observeSnapshot().collectAsState(
        initial = AiRouterSnapshot()
    )
    val translationAiPresets = aiPresets.filter {
        it.taskType == AiTaskType.TRANSLATE_CHAPTER && it.enabled
    }
    val translationAiTargetsByRoute = aiRouterSnapshot.targets
        .filter { it.enabled }
        .groupBy { it.routeProfileId }
    val translationAiRoutes = aiRouterSnapshot.routes.filter {
        it.taskType == AiTaskType.TRANSLATE_CHAPTER &&
            it.enabled &&
            translationAiTargetsByRoute[it.id].orEmpty().isNotEmpty()
    }
    val selectedTranslationPreset = translationAiPresets
        .firstOrNull { it.isDefault }
        ?: translationAiPresets.firstOrNull()
    val selectedPresetRouteId = selectedTranslationPreset?.chunkPolicyJson
        ?.let { json ->
            runCatching { GSON.fromJson(json, AiTaskRuntimeOptions::class.java) }
                .getOrNull()
                ?.routeProfileId
        }
        .orEmpty()
    val enabledAiProviderNames = aiProviders
        .filter { it.enabled }
        .associate { it.id to it.name }
    val translationAiModels = aiModels.filter {
        it.enabled && it.providerId in enabledAiProviderNames
    }
    val selectedPresetModelId = selectedTranslationPreset?.modelProfileId.orEmpty()
    val nmtModelRegistry = remember(context) { HachimiOnnxModelRegistry(context) }
    var isNmtModelInstalled by remember { mutableStateOf(nmtModelRegistry.isInstalled()) }
    var isImportingNmtModel by remember { mutableStateOf(false) }
    var nmtImportProgress by remember { mutableStateOf<HachimiOnnxImportProgress?>(null) }
    var nmtImportJob by remember { mutableStateOf<Job?>(null) }
    var showQuickDictionaryImport by remember { mutableStateOf(false) }
    var showNmtPromptEditor by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (TranslationConfig.llmProvider.shouldWarmQuickTranslation()) {
            withContext(Dispatchers.IO) {
                runCatching(quickTranslationGateway::warmUp)
            }
        }
    }
    val nmtModelImportPicker = rememberLauncherForActivityResult(
        contract = FilteredOpenDocumentContract(
            primaryMimeType = "application/zip",
            persistableAccess = true,
        ),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        uri.takePersistablePermissionSafely(
            context,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        nmtImportJob?.cancel()
        nmtImportJob = coroutineScope.launch {
            isImportingNmtModel = true
            nmtImportProgress = HachimiOnnxImportProgress(HachimiOnnxImportPhase.PREPARING)
            try {
                withContext(Dispatchers.IO) {
                    HachimiOnnxModelImporter.import(context, uri) { progress ->
                        withContext(Dispatchers.Main.immediate) {
                            nmtImportProgress = progress
                        }
                    }
                }
                isNmtModelInstalled = nmtModelRegistry.isInstalled()
                context.toastOnUi(R.string.nmt_model_imported)
            } catch (_: CancellationException) {
                context.toastOnUi(R.string.nmt_model_import_cancelled)
            } catch (error: Throwable) {
                context.toastOnUi(
                    resources.getString(
                        R.string.nmt_model_import_failed,
                        error.localizedMessage ?: error.javaClass.simpleName,
                    )
                )
            } finally {
                isImportingNmtModel = false
                nmtImportProgress = null
                nmtImportJob = null
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.translation_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.translation_provider)) {
                    DropdownListSettingItem(
                        title = stringResource(R.string.llm_provider),
                        selectedValue = TranslationConfig.llmProvider,
                        displayEntries = TranslationConfig.providerDisplayNames.toTypedArray(),
                        entryValues = TranslationConfig.providerValues.toTypedArray(),
                        onValueChange = {
                            TranslationConfig.llmProvider = it
                            if (it == TranslationConstants.PROVIDER_QUICK_TRANSLATOR ||
                                it == TranslationConstants.PROVIDER_NMT
                            ) {
                                TranslationConfig.llmTargetLanguage = TranslationConstants.TARGET_VIETNAMESE
                            }
                            if (it.shouldWarmQuickTranslation()) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    runCatching(quickTranslationGateway::warmUp)
                                }
                            }
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.translation_options)) {
                    val availableLanguages = TranslationConstants.targetLanguagesForProvider(
                        TranslationConfig.llmProvider
                    )
                    val languageEntries = availableLanguages.map { it.second }.toTypedArray()
                    val languageValues = availableLanguages.map { it.first }.toTypedArray()
                    DropdownListSettingItem(
                        title = stringResource(R.string.llm_target_language),
                        selectedValue = TranslationConfig.llmTargetLanguage,
                        displayEntries = languageEntries,
                        entryValues = languageValues,
                        onValueChange = { TranslationConfig.llmTargetLanguage = it }
                    )

                    DropdownListSettingItem(
                        title = stringResource(R.string.quick_translation_pronoun_mode),
                        description = stringResource(R.string.quick_translation_pronoun_mode_summary),
                        selectedValue = TranslationConfig.quickTranslationPronounMode,
                        displayEntries = arrayOf(
                            stringResource(R.string.quick_translation_pronoun_mode_auto),
                            stringResource(R.string.quick_translation_pronoun_mode_ancient),
                            stringResource(R.string.quick_translation_pronoun_mode_modern),
                            stringResource(R.string.quick_translation_pronoun_mode_western),
                            stringResource(R.string.quick_translation_pronoun_mode_off),
                        ),
                        entryValues = arrayOf(
                            QuickTranslationPronounMode.AUTO.value,
                            QuickTranslationPronounMode.ANCIENT.value,
                            QuickTranslationPronounMode.MODERN.value,
                            QuickTranslationPronounMode.WESTERN.value,
                            QuickTranslationPronounMode.OFF.value,
                        ),
                        onValueChange = { TranslationConfig.quickTranslationPronounMode = it },
                    )

                    SwitchSettingItem(
                        title = stringResource(R.string.dynamic_ui_translation),
                        description = stringResource(R.string.dynamic_ui_translation_summary),
                        checked = TranslationConfig.dynamicUiTranslationEnabled,
                        onCheckedChange = { TranslationConfig.dynamicUiTranslationEnabled = it },
                    )

                    ClickableSettingItem(
                        title = stringResource(R.string.retranslate_dynamic_ui),
                        description = stringResource(R.string.retranslate_dynamic_ui_summary),
                        onClick = {
                            coroutineScope.launch {
                                translateChapterUseCase.clearDynamicUiTranslationCache()
                                context.toastOnUi(R.string.dynamic_ui_translation_cache_cleared)
                            }
                        },
                    )

                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.quick_dictionary_settings)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.quick_dictionary_manager),
                        description = stringResource(R.string.quick_dictionary_manager_summary),
                        onClick = { onNavigateToQuickDictionary(false) },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.quick_dictionary_import_file),
                        description = stringResource(R.string.quick_dictionary_import_summary),
                        onClick = { onNavigateToQuickDictionary(true) },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.quick_dictionary_download_catalog),
                        description = stringResource(R.string.quick_dictionary_download_catalog_summary),
                        onClick = { context.openUrl(ExternalAssetCatalog.quickTranslationCleanZipUrl) },
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.offline_translation_models)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.mlkit_language_models),
                        description = stringResource(R.string.mlkit_language_models_summary),
                        onClick = onNavigateToMlKitModels,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.nmt_download_model),
                        description = stringResource(R.string.nmt_download_model_summary),
                        onClick = { context.openUrl(ExternalAssetCatalog.hachimiOnnxZipUrl) },
                    )
                    ClickableSettingItem(
                        title = stringResource(
                            if (isImportingNmtModel) {
                                R.string.nmt_cancel_import_model
                            } else {
                                R.string.nmt_import_model
                            }
                        ),
                        description = if (isImportingNmtModel) {
                            when (nmtImportProgress?.phase) {
                                HachimiOnnxImportPhase.WAITING_FOR_RUNTIME ->
                                    stringResource(R.string.nmt_model_waiting_for_runtime)
                                HachimiOnnxImportPhase.INSTALLING ->
                                    stringResource(R.string.nmt_model_installing)
                                else -> nmtImportProgress?.percent?.let { percent ->
                                    stringResource(
                                        R.string.nmt_model_import_progress,
                                        percent,
                                        nmtImportProgress?.currentFile.orEmpty(),
                                    )
                                } ?: stringResource(R.string.nmt_model_importing)
                            }
                        } else if (isNmtModelInstalled) {
                            stringResource(R.string.nmt_model_installed_summary)
                        } else {
                            stringResource(R.string.nmt_model_missing_summary)
                        },
                        onClick = {
                            if (isImportingNmtModel) {
                                nmtImportJob?.cancel()
                            } else {
                                nmtModelImportPicker.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                    )
                                )
                            }
                        },
                    )
                }
            }

            if (TranslationConfig.llmProvider == TranslationConfig.PROVIDER_NMT) {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.nmt_decode_config)) {
                        ClickableSettingItem(
                            title = stringResource(R.string.nmt_source_prompt),
                            description = stringResource(R.string.nmt_source_prompt_summary),
                            onClick = { showNmtPromptEditor = true },
                        )
                        InputSettingItem(
                            title = stringResource(R.string.nmt_max_chars_per_chunk),
                            value = TranslationConfig.nmtMaxCharsPerChunk.toString(),
                            defaultValue = "1000",
                            description = stringResource(R.string.translation_chunk_input_summary),
                            onConfirm = { input ->
                                parseTranslationChunkSize(input)?.let {
                                    TranslationConfig.nmtMaxCharsPerChunk = it
                                } ?: context.toastOnUi(
                                    resources.getString(
                                        R.string.input_value_range,
                                        TranslationConfig.MIN_CHUNK_CHARS,
                                        TranslationConfig.MAX_CHUNK_CHARS,
                                    )
                                )
                            },
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.nmt_source_token_budget),
                            value = TranslationConfig.nmtSourceTokenBudget.toFloat(),
                            defaultValue = 96f,
                            valueRange = 32f..480f,
                            steps = 27,
                            description = stringResource(
                                R.string.nmt_source_token_budget_summary,
                                TranslationConfig.nmtSourceTokenBudget,
                            ),
                            onValueChange = { TranslationConfig.nmtSourceTokenBudget = it.toInt() },
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.nmt_max_new_tokens),
                            value = TranslationConfig.nmtMaxNewTokens.toFloat(),
                            defaultValue = 240f,
                            valueRange = 32f..384f,
                            steps = 21,
                            description = TranslationConfig.nmtMaxNewTokens.toString(),
                            onValueChange = { TranslationConfig.nmtMaxNewTokens = it.toInt() },
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.nmt_repetition_penalty),
                            value = TranslationConfig.nmtRepetitionPenalty,
                            defaultValue = 1.2f,
                            valueRange = 1f..2f,
                            steps = 9,
                            description = "%.1f".format(TranslationConfig.nmtRepetitionPenalty),
                            onValueChange = { TranslationConfig.nmtRepetitionPenalty = it },
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.nmt_no_repeat_bigram),
                            checked = TranslationConfig.nmtNoRepeatBigram,
                            onCheckedChange = { TranslationConfig.nmtNoRepeatBigram = it },
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.nmt_retry_missing_terms),
                            description = stringResource(R.string.nmt_retry_missing_terms_summary),
                            checked = TranslationConfig.nmtRetryMissingTerms,
                            onCheckedChange = { TranslationConfig.nmtRetryMissingTerms = it },
                        )
                    }
                }
            }

            if (TranslationConfig.llmProvider == TranslationConfig.PROVIDER_APP_AI) {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.ai_config)) {
                        ClickableSettingItem(
                            title = stringResource(R.string.translation_app_ai_provider),
                            description = stringResource(R.string.translation_app_ai_provider_summary),
                            onClick = onNavigateToAi
                        )
                        val routeValues = translationAiRoutes.map {
                            AI_PROMPT_SELECTION_ROUTE_PREFIX + it.id
                        }
                        val modelValues = translationAiModels.map {
                            AI_PROMPT_SELECTION_MODEL_PREFIX + it.id
                        }
                        val selectedRoute = translationAiRoutes
                            .firstOrNull { it.id == selectedPresetRouteId }
                        val selectedModel = translationAiModels
                            .firstOrNull { it.id == selectedPresetModelId }
                        val selectedTargetValue = when {
                            selectedRoute != null ->
                                AI_PROMPT_SELECTION_ROUTE_PREFIX + selectedRoute.id
                            selectedModel != null ->
                                AI_PROMPT_SELECTION_MODEL_PREFIX + selectedModel.id
                            else -> (routeValues + modelValues).firstOrNull().orEmpty()
                        }
                        if (routeValues.isNotEmpty() || modelValues.isNotEmpty()) {
                            DropdownListSettingItem(
                                title = stringResource(R.string.ai_prompt_editor_model_or_combo),
                                selectedValue = selectedTargetValue,
                                displayEntries = (translationAiRoutes.map { route ->
                                    val targetCount =
                                        translationAiTargetsByRoute[route.id].orEmpty().size
                                    "Combo · ${route.name} ($targetCount)"
                                } + translationAiModels.map { model ->
                                    val providerName = enabledAiProviderNames[model.providerId]
                                        .orEmpty()
                                    resources.getString(
                                        R.string.ai_prompt_editor_model_entry,
                                        "$providerName · ${model.displayName} (${model.modelId})",
                                    )
                                }).toTypedArray(),
                                entryValues = (routeValues + modelValues).toTypedArray(),
                                description = selectedRoute?.let { route ->
                                    stringResource(
                                        R.string.translation_ai_fallback_combo_summary,
                                        translationAiTargetsByRoute[route.id].orEmpty().size,
                                        route.maxAttempts,
                                    )
                                } ?: selectedModel?.let { model ->
                                    "${enabledAiProviderNames[model.providerId].orEmpty()} · ${model.displayName}"
                                } ?: stringResource(R.string.ai_prompt_editor_model_required),
                                onValueChange = { selection ->
                                    coroutineScope.launch {
                                        runCatching {
                                            val preset = aiProfileGateway.getTaskPreset(
                                                AiTaskType.TRANSLATE_CHAPTER
                                            ) ?: error(
                                                resources.getString(
                                                    R.string.ai_prompt_editor_model_required
                                                )
                                            )
                                            val routeId = selection
                                                .takeIf {
                                                    it.startsWith(
                                                        AI_PROMPT_SELECTION_ROUTE_PREFIX
                                                    )
                                                }
                                                ?.removePrefix(AI_PROMPT_SELECTION_ROUTE_PREFIX)
                                                .orEmpty()
                                            val selectedModelProfileId = if (routeId.isNotBlank()) {
                                                translationAiTargetsByRoute[routeId]
                                                    .orEmpty()
                                                    .sortedWith(
                                                        compareBy<io.legado.app.domain.model.AiRouteTargetConfig> {
                                                            it.priority
                                                        }.thenBy { it.sortNumber }
                                                            .thenBy { it.id }
                                                    )
                                                    .firstOrNull()
                                                    ?.modelProfileId
                                                    ?: error(
                                                        resources.getString(
                                                            R.string.translation_ai_fallback_combo_empty
                                                        )
                                                    )
                                            } else {
                                                selection.removePrefix(
                                                    AI_PROMPT_SELECTION_MODEL_PREFIX
                                                ).takeIf { modelId ->
                                                    translationAiModels.any { it.id == modelId }
                                                } ?: error(
                                                    resources.getString(
                                                        R.string.ai_prompt_editor_model_required
                                                    )
                                                )
                                            }
                                            aiProfileGateway.saveTaskPreset(
                                                AiTaskPresetDraft(
                                                    presetId = preset.id,
                                                    taskType = preset.taskType,
                                                    name = preset.name,
                                                    description = preset.description,
                                                    modelProfileId = selectedModelProfileId,
                                                    promptTemplate = preset.promptTemplate,
                                                    params = preset.params,
                                                    runtimeOptions = preset.runtimeOptions.copy(
                                                        routeProfileId = routeId
                                                    ),
                                                    enabled = true,
                                                    makeDefault = true,
                                                    sortNumber = selectedTranslationPreset
                                                        ?.sortNumber
                                                        ?: 0,
                                                )
                                            )
                                        }.onFailure { error ->
                                            context.toastOnUi(
                                                error.localizedMessage
                                                    ?: resources.getString(R.string.error)
                                            )
                                        }
                                    }
                                },
                            )
                        } else {
                            ClickableSettingItem(
                                title = stringResource(R.string.ai_prompt_editor_model_or_combo),
                                description = stringResource(R.string.ai_prompt_editor_model_required),
                                onClick = onNavigateToAi,
                            )
                        }
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_prompt_editor_title),
                            description = stringResource(R.string.ai_prompt_editor_summary),
                            onClick = onNavigateToAiPromptEditor,
                        )
                        if (selectedTranslationPreset != null) {
                            DropdownListSettingItem(
                                title = stringResource(R.string.translation_ai_prompt),
                                selectedValue = selectedTranslationPreset.id,
                                displayEntries = translationAiPresets
                                    .map { it.name }
                                    .toTypedArray(),
                                entryValues = translationAiPresets
                                    .map { it.id }
                                    .toTypedArray(),
                                onValueChange = { presetId ->
                                    coroutineScope.launch {
                                        runCatching {
                                            aiProfileGateway.setDefaultTaskPreset(presetId)
                                        }.onFailure { error ->
                                            context.toastOnUi(
                                                error.localizedMessage
                                                    ?: resources.getString(R.string.error)
                                            )
                                        }
                                    }
                                },
                            )
                        } else {
                            ClickableSettingItem(
                                title = stringResource(R.string.translation_ai_prompt),
                                description = stringResource(
                                    R.string.translation_ai_prompt_empty
                                ),
                                onClick = onNavigateToAiPromptEditor,
                            )
                        }
                        ClickableSettingItem(
                            title = stringResource(R.string.translation_prompt_pipeline),
                            description = stringResource(R.string.translation_prompt_pipeline_summary),
                            onClick = onNavigateToPrompts,
                        )
                        InputSettingItem(
                            title = stringResource(R.string.ai_max_chars_per_chunk),
                            value = TranslationConfig.aiMaxCharsPerChunk.toString(),
                            defaultValue = "1000",
                            description = stringResource(R.string.ai_translation_chunk_summary),
                            onConfirm = { input ->
                                parseTranslationChunkSize(input)?.let {
                                    TranslationConfig.aiMaxCharsPerChunk = it
                                } ?: context.toastOnUi(
                                    resources.getString(
                                        R.string.input_value_range,
                                        TranslationConfig.MIN_CHUNK_CHARS,
                                        TranslationConfig.MAX_CHUNK_CHARS,
                                    )
                                )
                            },
                        )
                    }
                }
            }

            if (TranslationConfig.llmProvider != TranslationConfig.PROVIDER_NMT &&
                TranslationConfig.llmProvider != TranslationConfig.PROVIDER_APP_AI
            ) {
                item {
                    SplicedColumnGroup(
                        title = stringResource(R.string.translation_standard_chunk_config)
                    ) {
                        InputSettingItem(
                            title = stringResource(R.string.llm_max_chars_per_chunk),
                            value = TranslationConfig.llmMaxCharsPerChunk.toString(),
                            defaultValue = "10000",
                            description = stringResource(R.string.translation_chunk_input_summary),
                            onConfirm = { input ->
                                parseTranslationChunkSize(input)?.let {
                                    TranslationConfig.llmMaxCharsPerChunk = it
                                } ?: context.toastOnUi(
                                    resources.getString(
                                        R.string.input_value_range,
                                        TranslationConfig.MIN_CHUNK_CHARS,
                                        TranslationConfig.MAX_CHUNK_CHARS,
                                    )
                                )
                            },
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.llm_concurrent_chunks),
                            value = TranslationConfig.llmConcurrentChunks.toFloat(),
                            defaultValue = 1f,
                            valueRange = 1f..4f,
                            steps = 2,
                            description = TranslationConfig.llmConcurrentChunks.toString(),
                            onValueChange = {
                                TranslationConfig.llmConcurrentChunks = it.toInt()
                            },
                        )
                    }
                }
            }
        }
    }

    NmtSourcePromptDialog(
        show = showNmtPromptEditor,
        initialPrompt = TranslationConfig.nmtSourcePrompt,
        onDismissRequest = { showNmtPromptEditor = false },
        onSave = { prompt ->
            TranslationConfig.nmtSourcePrompt = prompt.trim()
            showNmtPromptEditor = false
        },
    )

    QuickDictionaryImportDialog(
        show = showQuickDictionaryImport,
        onDismissRequest = { showQuickDictionaryImport = false },
        onImport = { text, type, scope, universeName, contextMarkers, projectKey ->
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        importQuickDictionaryEntries(
                            gateway = quickDictionaryGateway,
                            text = text,
                            type = type,
                            scope = scope,
                            universeName = universeName,
                            contextMarkers = contextMarkers,
                            projectKey = projectKey,
                        )
                    }
                }
                result.onSuccess { importResult ->
                    showQuickDictionaryImport = false
                    context.toastOnUi(
                        resources.getString(
                            R.string.quick_dictionary_imported,
                            importResult.importedEntries,
                            importResult.duplicateLines,
                        )
                    )
                }.onFailure { error ->
                    context.toastOnUi(error.localizedMessage ?: resources.getString(R.string.error))
                }
            }
        },
    )
}

private fun String.shouldWarmQuickTranslation(): Boolean =
    this == TranslationConstants.PROVIDER_QUICK_TRANSLATOR

@Composable
private fun NmtSourcePromptDialog(
    show: Boolean,
    initialPrompt: String,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
) {
    var prompt by remember(show, initialPrompt) { mutableStateOf(initialPrompt) }
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.nmt_source_prompt),
        confirmText = stringResource(R.string.save),
        onConfirm = { onSave(prompt) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(stringResource(R.string.nmt_source_prompt_hint))
                AppTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = stringResource(R.string.nmt_source_prompt),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
    )
}

@Composable
private fun QuickDictionaryImportDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onImport: (
        text: String,
        type: QuickDictionaryType,
        scope: QuickDictionaryScope,
        universeName: String,
        contextMarkers: String,
        projectKey: String,
    ) -> Unit,
) {
    var text by remember(show) { mutableStateOf("") }
    var type by remember(show) { mutableStateOf(QuickDictionaryType.VIETPHRASE) }
    var scope by remember(show) { mutableStateOf(QuickDictionaryScope.GLOBAL) }
    var universeName by remember(show) { mutableStateOf("") }
    var contextMarkers by remember(show) { mutableStateOf("") }
    var projectKey by remember(show) { mutableStateOf("") }
    var projectOptions by remember(show) { mutableStateOf(emptyList<QuickDictionaryProjectOption>()) }
    var isLoadingProjects by remember(show) { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        isLoadingProjects = true
        projectOptions = runCatching {
            withContext(Dispatchers.IO) {
                appDb.bookDao.getAll()
                    .asSequence()
                    .filterNot { it.isNotShelf }
                    .filter { it.bookUrl.isNotBlank() }
                    .distinctBy { it.bookUrl }
                    .sortedWith(compareBy({ it.name }, { it.author }))
                    .map { book ->
                        QuickDictionaryProjectOption(
                            key = book.bookUrl,
                            label = buildString {
                                append(book.name.ifBlank { book.bookUrl })
                                if (book.author.isNotBlank()) {
                                    append(" — ").append(book.author)
                                }
                            },
                        )
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())
        isLoadingProjects = false
        if (projectOptions.none { it.key == projectKey }) {
            projectKey = projectOptions.firstOrNull()?.key.orEmpty()
        }
    }

    LaunchedEffect(scope, projectOptions) {
        if (scope == QuickDictionaryScope.PROJECT &&
            projectOptions.none { it.key == projectKey }
        ) {
            projectKey = projectOptions.firstOrNull()?.key.orEmpty()
        }
    }

    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.quick_dictionary_import),
        confirmText = stringResource(R.string.confirm),
        onConfirm = {
            onImport(text, type, scope, universeName, contextMarkers, projectKey)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(stringResource(R.string.quick_dictionary_type))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickDictionaryImportTypes.forEach { candidate ->
                        FilterChip(
                            selected = candidate == type,
                            onClick = { type = candidate },
                            label = { AppText(stringResource(candidate.labelResource())) },
                        )
                    }
                }

                AppText(stringResource(R.string.quick_dictionary_scope))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDictionaryScope.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == scope,
                            onClick = { scope = candidate },
                            label = { AppText(stringResource(candidate.labelResource())) },
                        )
                    }
                }

                if (scope == QuickDictionaryScope.UNIVERSE) {
                    AppTextField(
                        value = universeName,
                        onValueChange = { universeName = it },
                        label = stringResource(R.string.quick_dictionary_universe_name),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    AppTextField(
                        value = contextMarkers,
                        onValueChange = { contextMarkers = it },
                        label = stringResource(R.string.quick_dictionary_context_markers),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                }

                if (scope == QuickDictionaryScope.PROJECT) {
                    if (isLoadingProjects) {
                        AppText(stringResource(R.string.quick_dictionary_project_loading))
                    } else if (projectOptions.isEmpty()) {
                        AppText(stringResource(R.string.quick_dictionary_project_empty))
                    } else {
                        DropdownListSettingItem(
                            title = stringResource(R.string.quick_dictionary_project),
                            selectedValue = projectKey,
                            displayEntries = projectOptions.map { it.label }.toTypedArray(),
                            entryValues = projectOptions.map { it.key }.toTypedArray(),
                            onValueChange = { projectKey = it },
                        )
                    }
                }

                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.quick_dictionary_import_data),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp),
                    minLines = 8,
                    maxLines = 18,
                )
                AppText(stringResource(R.string.quick_dictionary_import_format_hint))
            }
        },
    )
}

private val quickDictionaryImportTypes = listOf(
    QuickDictionaryType.NAME,
    QuickDictionaryType.VIETPHRASE,
    QuickDictionaryType.PHONETIC,
    QuickDictionaryType.PRONOUN,
    QuickDictionaryType.LUAT_NHAN,
    QuickDictionaryType.IGNORE,
)

private data class QuickDictionaryProjectOption(
    val key: String,
    val label: String,
)

private data class QuickDictionaryInlineImportResult(
    val importedEntries: Int,
    val duplicateLines: Int,
)

private suspend fun importQuickDictionaryEntries(
    gateway: QuickDictionaryGateway,
    text: String,
    type: QuickDictionaryType,
    scope: QuickDictionaryScope,
    universeName: String,
    contextMarkers: String,
    projectKey: String,
): QuickDictionaryInlineImportResult {
    val normalizedUniverseName = universeName.trim()
    val markers = contextMarkers.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
    val universeKey = if (scope == QuickDictionaryScope.UNIVERSE) {
        quickDictionaryUniverseKey(normalizedUniverseName)
    } else {
        ""
    }
    val scopeKey = when (scope) {
        QuickDictionaryScope.GLOBAL -> ""
        QuickDictionaryScope.UNIVERSE -> {
            require(universeKey.isNotBlank() && markers.isNotEmpty()) {
                "Universe cần tên và ít nhất một marker ngữ cảnh"
            }
            gateway.saveUniverse(
                QuickDictionaryUniverse(
                    key = universeKey,
                    name = normalizedUniverseName,
                    contextMarkers = markers,
                )
            )
            universeKey
        }
        QuickDictionaryScope.PROJECT -> projectKey.trim().also {
            require(it.isNotBlank()) { "Vui lòng chọn dự án/truyện cho từ điển cấp Project" }
        }
    }

    val parsedEntries = parseQuickDictionaryImportLines(text, type)
    val uniqueEntries = parsedEntries.distinctBy { it.first }
    var count = 0
    uniqueEntries.forEach { (raw, hanViet, target) ->
        gateway.save(
            QuickDictionaryEntry(
                raw = raw,
                hanViet = hanViet,
                target = target,
                type = type,
                scope = scope,
                scopeKey = scopeKey,
            )
        )
        count += 1
    }
    require(count > 0) { "Không có dòng từ điển hợp lệ để nhập" }
    return QuickDictionaryInlineImportResult(
        importedEntries = count,
        duplicateLines = parsedEntries.size - uniqueEntries.size,
    )
}

private fun parseQuickDictionaryImportLines(
    text: String,
    type: QuickDictionaryType,
): List<Triple<String, String, String>> {
    return text.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line -> parseQuickDictionaryImportLine(line, type) }
        .toList()
}

private fun parseQuickDictionaryImportLine(
    line: String,
    type: QuickDictionaryType,
): Triple<String, String, String>? {
    val parts = splitQuickDictionaryImportLine(line)
    val raw = parts.getOrNull(0)?.trim().orEmpty()
    if (raw.isBlank()) return null
    if (type == QuickDictionaryType.IGNORE) {
        return Triple(raw, "", "")
    }
    val value = parts.getOrNull(1)?.trim().orEmpty()
    if (value.isBlank()) return null
    val extra = parts.getOrNull(2)?.trim().orEmpty()
    return if (type == QuickDictionaryType.PHONETIC) {
        Triple(raw, value, extra)
    } else {
        Triple(raw, extra, value)
    }
}

private fun splitQuickDictionaryImportLine(line: String): List<String> {
    val separators = listOf("\t", "=>", "→", "=", "|", ":")
    val separator = separators.firstOrNull { line.contains(it) }
        ?: return listOf(line)
    return line.split(separator, limit = 3)
}

private fun QuickDictionaryType.labelResource(): Int = when (this) {
    QuickDictionaryType.TERM -> R.string.quick_dictionary_type_term
    QuickDictionaryType.NAME -> R.string.quick_dictionary_type_name
    QuickDictionaryType.VIETPHRASE -> R.string.quick_dictionary_type_vietphrase
    QuickDictionaryType.PRONOUN -> R.string.quick_dictionary_type_pronoun
    QuickDictionaryType.PHONETIC -> R.string.quick_dictionary_type_phonetic
    QuickDictionaryType.LUAT_NHAN -> R.string.quick_dictionary_type_luat_nhan
    QuickDictionaryType.IGNORE -> R.string.quick_dictionary_type_ignore
}

private fun QuickDictionaryScope.labelResource(): Int = when (this) {
    QuickDictionaryScope.GLOBAL -> R.string.quick_dictionary_scope_global
    QuickDictionaryScope.UNIVERSE -> R.string.quick_dictionary_scope_universe
    QuickDictionaryScope.PROJECT -> R.string.quick_dictionary_scope_project
}

internal fun parseTranslationChunkSize(input: String): Int? =
    input.trim().toIntOrNull()?.takeIf {
        it in TranslationConfig.MIN_CHUNK_CHARS..TranslationConfig.MAX_CHUNK_CHARS
    }
