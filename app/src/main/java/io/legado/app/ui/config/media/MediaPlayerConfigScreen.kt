package io.legado.app.ui.config.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.R
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.text.AppText
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerConfigScreen(
    onBackClick: () -> Unit,
    viewModel: MediaPlayerConfigViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.media_player_settings),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.media_player_playback_settings)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.media_player_auto_pip_on_exit),
                        description = if (state.supportsPictureInPicture) {
                            stringResource(R.string.media_player_auto_pip_on_exit_summary)
                        } else {
                            stringResource(R.string.media_player_pip_not_supported)
                        },
                        checked = state.autoEnterPipOnExit,
                        enabled = state.supportsPictureInPicture,
                        onCheckedChange = {
                            viewModel.onIntent(
                                MediaPlayerConfigIntent.SetAutoEnterPipOnExit(it)
                            )
                        },
                    )
                    if (!state.supportsPictureInPicture) {
                        AppText(stringResource(R.string.media_player_pip_not_supported))
                    }
                }
            }
        }
    }
}
