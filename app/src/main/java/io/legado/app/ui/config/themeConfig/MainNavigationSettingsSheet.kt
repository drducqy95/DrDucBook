package io.legado.app.ui.config.themeConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.utils.move
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun MainNavigationSettingsSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    var navigationItems by remember(show) {
        mutableStateOf(MainDestination.ordered(ThemeConfig.mainNavigationOrder))
    }
    val navigationListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(navigationListState) { from, to ->
            navigationItems = navigationItems.toMutableList().apply {
                move(from.index, to.index)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            ThemeConfig.mainNavigationOrder =
                navigationItems.joinToString(",") { it.route }
        }
    }

    val selectedDefault = MainDestination.normalizeSavedRoute(ThemeConfig.defaultHomePage)
        .takeIf { route -> navigationItems.any { it.route == route } }
        ?: MainDestination.Bookshelf.route

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.main_navigation_settings),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            CompactDropdownSettingItem(
                title = stringResource(R.string.default_home_page),
                selectedValue = selectedDefault,
                displayEntries = navigationItems.map { stringResource(it.labelId) }.toTypedArray(),
                entryValues = navigationItems.map { it.route }.toTypedArray(),
                onValueChange = { ThemeConfig.defaultHomePage = it },
            )
            Spacer(modifier = Modifier.padding(bottom = 4.dp))
            LazyColumn(
                state = navigationListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = navigationItems,
                    key = { it.route },
                ) { destination ->
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = destination.route,
                        reorderIndex = navigationItems.indexOf(destination),
                        reorderItemCount = navigationItems.size,
                        onMoveItem = { from, to ->
                            navigationItems = navigationItems.toMutableList().apply { move(from, to) }
                            ThemeConfig.mainNavigationOrder =
                                navigationItems.joinToString(",") { it.route }
                        },
                        title = stringResource(destination.labelId),
                        isEnabled = true,
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        onEnabledChange = null,
                    )
                }
            }
        }
    }
}
