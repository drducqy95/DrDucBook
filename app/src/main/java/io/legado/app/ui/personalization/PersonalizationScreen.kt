package io.legado.app.ui.personalization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.domain.model.AppearanceEngine
import io.legado.app.domain.model.AppearanceProfile
import io.legado.app.domain.model.AppearanceThemeMode
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(
    state: PersonalizationUiState,
    onIntent: (PersonalizationIntent) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.personalization_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
            )
        },
        bottomBar = { PersonalizationActions(state, onIntent) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            PersonalizationTabs(
                selected = state.selectedTab,
                onSelected = { onIntent(PersonalizationIntent.SelectTab(it)) },
            )
            when {
                state.loading || state.draft == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.selectedTab == PersonalizationTab.THEME ->
                    PersonalizationThemeTab(state, onIntent)
                state.selectedTab == PersonalizationTab.ICONS ->
                    PersonalizationIconTab(state, onIntent)
                state.selectedTab == PersonalizationTab.WALLPAPER ->
                    PersonalizationWallpaperTab(state, onIntent)
                else -> PersonalizationPreviewTab(state, onIntent)
            }
        }
    }
    PersonalizationDialogs(state, onIntent)
}

@Composable
private fun PersonalizationTabs(
    selected: PersonalizationTab,
    onSelected: (PersonalizationTab) -> Unit,
) {
    val tabs = PersonalizationTab.entries
    PrimaryTabRow(selectedTabIndex = tabs.indexOf(selected)) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                text = {
                    Text(
                        tab.label(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun PersonalizationThemeTab(
    state: PersonalizationUiState,
    onIntent: (PersonalizationIntent) -> Unit,
) {
    val draft = state.draft ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = personalizationContentPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_profiles))
            LazyRow(
                contentPadding = PaddingValues(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.profiles, key = { it.profile.id }) { item ->
                    ProfileCard(
                        item = item,
                        selected = state.selectedProfileId == item.profile.id,
                        onClick = {
                            onIntent(PersonalizationIntent.SelectProfile(item.profile.id))
                        },
                    )
                }
            }
        }
        item { ProfileCommands(state, onIntent) }
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_engine))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
                AppearanceEngine.entries.forEachIndexed { index, engine ->
                    SegmentedButton(
                        selected = draft.engine == engine,
                        onClick = { onIntent(PersonalizationIntent.SetEngine(engine)) },
                        shape = SegmentedButtonDefaults.itemShape(index, AppearanceEngine.entries.size),
                        label = {
                            Text(if (engine == AppearanceEngine.MATERIAL) "Material" else "Miuix")
                        },
                    )
                }
            }
        }
        item {
            PersonalizationSectionTitle(stringResource(R.string.personalization_mode))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
                AppearanceThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = draft.themeMode == mode,
                        onClick = { onIntent(PersonalizationIntent.SetThemeMode(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            AppearanceThemeMode.entries.size,
                        ),
                        label = { Text(mode.label()) },
                    )
                }
            }
        }
        item {
            PersonalizationSlider(
                title = stringResource(R.string.personalization_font_scale),
                value = draft.fontScale.toFloat(),
                valueLabel = (draft.fontScale * 10).toString() + "%",
                range = 8f..15f,
                steps = 6,
                onValueChange = {
                    onIntent(PersonalizationIntent.SetFontScale(it.roundToInt()))
                },
            )
        }
        item {
            PersonalizationSlider(
                title = stringResource(R.string.personalization_container_opacity),
                value = draft.containerOpacity.toFloat(),
                valueLabel = draft.containerOpacity.toString() + "%",
                range = 30f..100f,
                steps = 13,
                onValueChange = {
                    onIntent(PersonalizationIntent.SetContainerOpacity(it.roundToInt()))
                },
            )
        }
        item {
            PersonalizationToggle(
                title = stringResource(R.string.personalization_blur),
                checked = draft.blurEnabled,
                onChecked = { onIntent(PersonalizationIntent.SetBlurEnabled(it)) },
            )
        }
        item {
            PersonalizationToggle(
                title = stringResource(R.string.personalization_progressive_blur),
                checked = draft.progressiveBlurEnabled,
                onChecked = {
                    onIntent(PersonalizationIntent.SetProgressiveBlurEnabled(it))
                },
            )
        }
    }
}

@Composable
private fun ProfileCard(
    item: AppearanceProfileUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val profile = item.profile
    Card(
        modifier = Modifier
            .width(154.dp)
            .height(124.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(2.dp, LegadoTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = Color(profile.lightColors.background)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileColorDot(Color(profile.lightColors.primary))
                ProfileColorDot(Color(profile.lightColors.secondary))
                ProfileColorDot(Color(profile.darkColors.primary))
            }
            Column {
                Text(
                    profile.displayName(),
                    color = Color(profile.lightColors.primaryText),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        item.active -> stringResource(R.string.personalization_active)
                        profile.builtIn -> stringResource(R.string.personalization_built_in)
                        else -> stringResource(R.string.personalization_custom)
                    },
                    color = Color(profile.lightColors.secondaryText),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ProfileColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun ProfileCommands(
    state: PersonalizationUiState,
    onIntent: (PersonalizationIntent) -> Unit,
) {
    val profile = state.profiles.firstOrNull {
        it.profile.id == state.selectedProfileId
    }?.profile ?: return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                onIntent(
                    PersonalizationIntent.RequestProfileName(
                        ProfileNameAction.DUPLICATE,
                        profile.id,
                    )
                )
            },
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.personalization_duplicate))
        }
        if (!profile.builtIn) {
            OutlinedButton(
                onClick = {
                    onIntent(
                        PersonalizationIntent.RequestProfileName(
                            ProfileNameAction.RENAME,
                            profile.id,
                        )
                    )
                },
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.rename))
            }
            IconButton(
                onClick = { onIntent(PersonalizationIntent.RequestDeleteProfile(profile.id)) },
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = LegadoTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PersonalizationActions(
    state: PersonalizationUiState,
    onIntent: (PersonalizationIntent) -> Unit,
) {
    Surface(
        color = LegadoTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { onIntent(PersonalizationIntent.Discard) },
                enabled = state.dirty && !state.saving,
            ) {
                Text(stringResource(R.string.personalization_discard))
            }
            IconButton(
                onClick = { onIntent(PersonalizationIntent.Reset) },
                enabled = !state.saving,
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = stringResource(R.string.personalization_reset),
                )
            }
            Button(
                onClick = { onIntent(PersonalizationIntent.Apply) },
                enabled = state.dirty && !state.saving,
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.personalization_apply))
            }
        }
    }
}

@Composable
private fun PersonalizationDialogs(
    state: PersonalizationUiState,
    onIntent: (PersonalizationIntent) -> Unit,
) {
    when (val dialog = state.dialog) {
        PersonalizationDialog.DiscardChanges -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(PersonalizationIntent.DismissDialog) },
            title = stringResource(R.string.personalization_unsaved_title),
            text = stringResource(R.string.personalization_unsaved_message),
            confirmText = stringResource(R.string.personalization_discard),
            onConfirm = {
                onIntent(PersonalizationIntent.Discard)
                onIntent(PersonalizationIntent.BackPressed)
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(PersonalizationIntent.DismissDialog) },
        )
        is PersonalizationDialog.ProfileName -> {
            var name by remember(dialog) { mutableStateOf(dialog.initialName) }
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(PersonalizationIntent.DismissDialog) },
                title = stringResource(
                    if (dialog.action == ProfileNameAction.DUPLICATE) {
                        R.string.personalization_duplicate
                    } else {
                        R.string.rename
                    }
                ),
                content = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.personalization_profile_name)) },
                    )
                },
                confirmText = stringResource(R.string.save),
                onConfirm = {
                    if (name.isNotBlank()) {
                        onIntent(PersonalizationIntent.ConfirmProfileName(name))
                    }
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onIntent(PersonalizationIntent.DismissDialog) },
            )
        }
        is PersonalizationDialog.DeleteProfile -> AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(PersonalizationIntent.DismissDialog) },
            title = stringResource(R.string.personalization_delete_profile),
            text = stringResource(R.string.personalization_delete_profile_message, dialog.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = { onIntent(PersonalizationIntent.ConfirmDeleteProfile) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(PersonalizationIntent.DismissDialog) },
        )
        null -> Unit
    }
}
