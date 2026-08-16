package io.legado.app.ui.config.readConfig

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.domain.model.ExternalAssetCatalog
import io.legado.app.model.tts.LocalTtsModelImporter
import io.legado.app.ui.book.read.sheet.ClickActionConfigSheet
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.FilteredOpenDocumentContract
import io.legado.app.utils.openUrl
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadConfigScreen(
    onBackClick: () -> Unit,
    onNavigateToTtsModelManager: () -> Unit = {},
    viewModel: ReadConfigViewModel = koinViewModel()
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    var showPageKeySheet by remember { mutableStateOf(false) }
    var showClickActionSheet by remember { mutableStateOf(false) }
    var isImportingLocalTtsModel by remember { mutableStateOf(false) }
    var ttsImportJob by remember { mutableStateOf<Job?>(null) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val localTtsModelImportPicker = rememberLauncherForActivityResult(
        contract = FilteredOpenDocumentContract(
            primaryMimeType = "application/zip",
            persistableAccess = false,
        ),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ttsImportJob?.cancel()
        ttsImportJob = coroutineScope.launch {
            isImportingLocalTtsModel = true
            runCatching {
                withContext(Dispatchers.IO) {
                    LocalTtsModelImporter.import(context, uri)
                }
            }.onSuccess { model ->
                context.toastOnUi(resources.getString(R.string.local_tts_model_imported, model.name))
            }.onFailure { error ->
                context.toastOnUi(
                    resources.getString(
                        R.string.local_tts_model_import_failed,
                        error.localizedMessage ?: error.javaClass.simpleName,
                    )
                )
            }
            isImportingLocalTtsModel = false
            ttsImportJob = null
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.read_config),
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
                SplicedColumnGroup(title = stringResource(R.string.screen_settings)) {
                DropdownListSettingItem(
                    title = stringResource(R.string.screen_direction),
                    selectedValue = state.screenOrientation,
                    displayEntries = stringArrayResource(R.array.screen_direction_title),
                    entryValues = stringArrayResource(R.array.screen_direction_value),
                    onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.ScreenOrientationChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.keep_light),
                    selectedValue = state.keepLight,
                    displayEntries = stringArrayResource(R.array.screen_time_out),
                    entryValues = stringArrayResource(R.array.screen_time_out_value),
                    onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.KeepLightChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.pt_hide_status_bar),
                    checked = state.hideStatusBar,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.HideStatusBarChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.pt_hide_navigation_bar),
                    checked = state.hideNavigationBar,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.HideNavigationBarChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.padding_display_cutouts),
                    checked = state.paddingDisplayCutouts,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.PaddingDisplayCutoutsChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.title_bar_mode),
                    selectedValue = state.titleBarMode,
                    displayEntries = stringArrayResource(R.array.title_bar_mode),
                    entryValues = stringArrayResource(R.array.title_bar_mode_value),
                    onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.TitleBarModeChanged(it))
                    }
                )

                SliderSettingItem(
                    title = stringResource(R.string.menu_alpha),
                    description = stringResource(R.string.menu_alpha_sum, state.readMenuBlurAlpha),
                    value = state.readMenuBlurAlpha.toFloat(),
                    defaultValue = 60f,
                    valueRange = 0f..100f,
                    onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.ReadMenuBlurAlphaChanged(it.toInt()))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.read_body_to_lh),
                    checked = state.readBodyToLh,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.ReadBodyToLhChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.read_change_all),
                    description = stringResource(R.string.read_change_all_s),
                    checked = state.defaultSourceChangeAll,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.DefaultSourceChangeAllChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.text_full_justify),
                    checked = state.textFullJustify,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.TextFullJustifyChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.text_bottom_justify),
                    checked = state.textBottomJustify,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.TextBottomJustifyChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.adapt_special_style),
                    checked = state.adaptSpecialStyle,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.AdaptSpecialStyleChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.use_zh_layout),
                    checked = state.useZhLayout,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.UseZhLayoutChanged(it))
                    }
                )

                    DropdownListSettingItem(
                    title = stringResource(R.string.show_brightness_view),
                        selectedValue = state.showBrightnessView,
                        displayEntries = stringArrayResource(R.array.brightness_bar_mode_title),
                        entryValues = stringArrayResource(R.array.brightness_bar_mode_value),
                        onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.ShowBrightnessViewChanged(it))
                    }
                )

                    if (state.showBrightnessView == "2") {
                        DropdownListSettingItem(
                            title = stringResource(R.string.brightness_bar_position),
                            selectedValue = state.brightnessVwPos,
                            displayEntries = stringArrayResource(R.array.brightness_bar_position_title),
                            entryValues = stringArrayResource(R.array.brightness_bar_position_value),
                            onValueChange = {
                                viewModel.onIntent(ReadConfigIntent.BrightnessVwPosChanged(it))
                            }
                        )
                    }

                SwitchSettingItem(
                    title = stringResource(R.string.use_underline),
                    checked = state.useUnderline,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.UseUnderlineChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.italicize_dialogue),
                    description = stringResource(R.string.italicize_dialogue_summary),
                    checked = state.italicizeDialogue,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.ItalicizeDialogueChanged(it))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.page_control)) {
                DropdownListSettingItem(
                    title = stringResource(R.string.read_slider_mode),
                    selectedValue = state.readSliderMode,
                    displayEntries = stringArrayResource(R.array.read_slider_mode),
                    entryValues = stringArrayResource(R.array.read_slider_mode_value),
                    onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.ReadSliderModeChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.double_page_horizontal),
                    selectedValue = state.doubleHorizontalPage,
                    displayEntries = stringArrayResource(R.array.double_page_title),
                    entryValues = stringArrayResource(R.array.double_page_value),
                    onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.DoubleHorizontalPageChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.progress_bar_behavior),
                    selectedValue = state.progressBarBehavior,
                    displayEntries = stringArrayResource(R.array.progress_bar_behavior_title),
                    entryValues = stringArrayResource(R.array.progress_bar_behavior_value),
                    onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.ProgressBarBehaviorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.mouse_wheel_page),
                    checked = state.mouseWheelPage,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.MouseWheelPageChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.volume_key_page),
                    checked = state.volumeKeyPage,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.VolumeKeyPageChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.volume_key_page_on_play),
                    checked = state.volumeKeyPageOnPlay,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.VolumeKeyPageOnPlayChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.key_page_on_long_press),
                    checked = state.keyPageOnLongPress,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.KeyPageOnLongPressChanged(it))
                    }
                )

                SliderSettingItem(
                    title = stringResource(R.string.page_touch_slop_title),
                    description = stringResource(
                        R.string.page_touch_slop_summary,
                        state.pageTouchSlop
                    ),
                    value = state.pageTouchSlop.toFloat(),
                    defaultValue = 0f,
                    valueRange = 0f..1000f,
                    onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.PageTouchSlopChanged(it.toInt()))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.local_tts)) {
                ClickableSettingItem(
                    title = stringResource(R.string.tts_model_manager),
                    description = stringResource(R.string.tts_model_manager_summary),
                    onClick = onNavigateToTtsModelManager,
                )

                ClickableSettingItem(
                    title = stringResource(R.string.download_local_tts_valtec_model),
                    description = stringResource(R.string.download_local_tts_valtec_model_summary),
                    onClick = { context.openUrl(ExternalAssetCatalog.ttsValtecModelZipUrl) },
                )

                ClickableSettingItem(
                    title = stringResource(R.string.local_tts_voice_catalog_folder),
                    description = stringResource(R.string.local_tts_voice_catalog_folder_summary),
                    onClick = { context.openUrl(ExternalAssetCatalog.ttsPiperVoiceFolderUrl) },
                )

                ExternalAssetCatalog.releaseEligibleTtsVoiceCatalog.forEach { voice ->
                    ClickableSettingItem(
                        title = voice.displayName,
                        description = stringResource(
                            R.string.local_tts_voice_download_summary,
                            voice.engine,
                            formatAssetSize(voice.sizeBytes),
                            if (voice.importSupported) {
                                stringResource(R.string.local_tts_import_supported)
                            } else {
                                stringResource(R.string.local_tts_piper_download_only)
                            },
                        ),
                        onClick = { context.openUrl(voice.downloadUrl) },
                    )
                }

                ClickableSettingItem(
                    title = stringResource(R.string.import_local_tts_model),
                    description = if (isImportingLocalTtsModel) {
                        stringResource(R.string.importing_local_tts_model_summary)
                    } else {
                        stringResource(R.string.local_tts_import_model_summary)
                    },
                    onClick = {
                        if (!isImportingLocalTtsModel) {
                            localTtsModelImportPicker.launch(
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

                SplicedColumnGroup(title = stringResource(R.string.other)) {
                SwitchSettingItem(
                    title = stringResource(R.string.enable_slider_vibrator),
                    checked = state.sliderVibrator,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.SliderVibratorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.enable_select_vibrator),
                    checked = state.selectVibrator,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.SelectVibratorChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.auto_change_source),
                    checked = state.autoChangeSource,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.AutoChangeSourceChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.auto_switch_theme_reminder_title),
                    description = stringResource(R.string.auto_switch_theme_reminder_desc),
                    checked = state.autoSuggestDayNight,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.AutoSuggestDayNightChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.selectText),
                    checked = state.selectText,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.SelectTextChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.no_anim_scroll_page),
                    checked = state.noAnimScrollPage,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.NoAnimScrollPageChanged(it))
                    }
                )

                DropdownListSettingItem(
                    title = stringResource(R.string.click_image_way),
                    selectedValue = state.clickImgWay,
                    displayEntries = stringArrayResource(R.array.click_image_way_title),
                    entryValues = stringArrayResource(R.array.click_image_way_value),
                    onValueChange = {
                        viewModel.onIntent(ReadConfigIntent.ClickImgWayChanged(it))
                    }
                )

                if (CanvasRecorderFactory.isSupport) {
                    SwitchSettingItem(
                        title = stringResource(R.string.enable_optimize_render),
                        checked = state.optimizeRender,
                        onCheckedChange = {
                            viewModel.onIntent(ReadConfigIntent.OptimizeRenderChanged(it))
                        }
                    )
                }

                ClickableSettingItem(
                    title = stringResource(R.string.click_regional_config),
                    onClick = { showClickActionSheet = true }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.disable_return_key),
                    checked = state.disableReturnKey,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.DisableReturnKeyChanged(it))
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.custom_page_key),
                    onClick = { showPageKeySheet = true }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_read_title_addition),
                    checked = state.showReadTitleAddition,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.ShowReadTitleAdditionChanged(it))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.show_menu_icon),
                    checked = state.showMenuIcon,
                    onCheckedChange = {
                        viewModel.onIntent(ReadConfigIntent.ShowMenuIconChanged(it))
                    }
                )
                }
            }
        }
    }

    PageKeySheet(
        show = showPageKeySheet,
        prevKeys = state.prevKeys,
        nextKeys = state.nextKeys,
        onDismissRequest = { showPageKeySheet = false },
        onConfirm = { prevKeys, nextKeys ->
            viewModel.onIntent(ReadConfigIntent.PageKeysChanged(prevKeys, nextKeys))
            showPageKeySheet = false
        }
    )

    if (showClickActionSheet) {
        ClickActionConfigSheet(
            onDismissRequest = { showClickActionSheet = false },
        )
    }
}

private fun formatAssetSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
