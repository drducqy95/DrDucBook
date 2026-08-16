package io.legado.app.ui.personalization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.domain.model.AppearanceTarget
import io.legado.app.domain.model.AppearanceThemeMode
import io.legado.app.domain.model.AppearancePresets
import io.legado.app.domain.model.AppearanceProfile
import io.legado.app.domain.model.IconSlot
import io.legado.app.domain.model.WallpaperAlignment
import io.legado.app.domain.model.WallpaperFit
import io.legado.app.ui.theme.LegadoTheme

internal fun personalizationContentPadding() = PaddingValues(
    start = 16.dp,
    top = 16.dp,
    end = 16.dp,
    bottom = 120.dp,
)

@Composable
internal fun PersonalizationSectionTitle(text: String) {
    Text(
        text = text,
        color = LegadoTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun PersonalizationSlider(
    title: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title)
            Text(valueLabel, color = LegadoTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
internal fun PersonalizationToggle(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
internal fun PersonalizationTab.label(): String = stringResource(
    when (this) {
        PersonalizationTab.THEME -> R.string.personalization_tab_theme
        PersonalizationTab.ICONS -> R.string.personalization_tab_icons
        PersonalizationTab.WALLPAPER -> R.string.personalization_tab_wallpaper
        PersonalizationTab.PREVIEW -> R.string.personalization_tab_preview
    }
)

@Composable
internal fun AppearanceThemeMode.label(): String = stringResource(
    when (this) {
        AppearanceThemeMode.SYSTEM -> R.string.follow_system
        AppearanceThemeMode.LIGHT -> R.string.day
        AppearanceThemeMode.DARK -> R.string.night
    }
)

@Composable
internal fun AppearanceProfile.displayName(): String = when (id) {
    AppearancePresets.COPPER_CYAN_ID -> stringResource(R.string.theme_copper_cyan)
    AppearancePresets.FOREST_CORAL_ID -> stringResource(R.string.theme_forest_coral)
    AppearancePresets.INK_AMBER_ID -> stringResource(R.string.theme_ink_amber)
    else -> name
}

@Composable
internal fun AppearanceTarget.label(): String = stringResource(
    when (this) {
        AppearanceTarget.GLOBAL -> R.string.personalization_target_global
        AppearanceTarget.HOME -> R.string.home
        AppearanceTarget.BOOKSHELF -> R.string.bookshelf
        AppearanceTarget.WORKSPACE -> R.string.workspace_title
        AppearanceTarget.AGENT -> R.string.personalization_target_agent
        AppearanceTarget.AUTHORING -> R.string.personalization_target_authoring
        AppearanceTarget.EBOOK -> R.string.personalization_target_ebook
        AppearanceTarget.READER -> R.string.personalization_target_reader
    }
)

@Composable
internal fun IconSlot.label(): String = stringResource(
    when (this) {
        IconSlot.NAV_HOME -> R.string.home
        IconSlot.NAV_BOOKSHELF -> R.string.bookshelf
        IconSlot.NAV_EXPLORE -> R.string.discovery
        IconSlot.NAV_WORKSPACE -> R.string.workspace_title
        IconSlot.NAV_MY -> R.string.my
        IconSlot.WORKSPACE_WRITING, IconSlot.SHORTCUT_WRITING ->
            R.string.personalization_target_authoring
        IconSlot.WORKSPACE_EBOOK, IconSlot.SHORTCUT_EBOOK ->
            R.string.personalization_target_ebook
        IconSlot.WORKSPACE_AGENT, IconSlot.SHORTCUT_AGENT ->
            R.string.personalization_target_agent
        IconSlot.WORKSPACE_RSS, IconSlot.SHORTCUT_RSS -> R.string.workspace_rss_sources
        IconSlot.TOOLBAR_SEARCH -> R.string.search
        IconSlot.TOOLBAR_REFRESH -> R.string.refresh
        IconSlot.TOOLBAR_MORE -> R.string.more
        IconSlot.TOOLBAR_BROWSER_EXIT -> R.string.browser_exit
        IconSlot.READER_TOC -> R.string.catalogue
        IconSlot.READER_THEME -> R.string.theme_setting
        IconSlot.READER_TTS -> R.string.read_aloud
        IconSlot.READER_MORE -> R.string.more
    }
)

@Composable
internal fun WallpaperFit.label(): String = stringResource(
    if (this == WallpaperFit.COVER) {
        R.string.personalization_fit_cover
    } else {
        R.string.personalization_fit_contain
    }
)

@Composable
internal fun WallpaperAlignment.label(): String = stringResource(
    when (this) {
        WallpaperAlignment.START -> R.string.personalization_align_start
        WallpaperAlignment.CENTER -> R.string.personalization_align_center
        WallpaperAlignment.END -> R.string.personalization_align_end
    }
)
