package io.legado.app.ui.personalization

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AppearanceAssetKind
import io.legado.app.domain.model.AppearanceEngine
import io.legado.app.domain.model.AppearanceIconSpec
import io.legado.app.domain.model.AppearanceProfile
import io.legado.app.domain.model.AppearanceThemeMode
import io.legado.app.domain.model.AppearanceWallpaperSpec
import io.legado.app.domain.model.IconSlot
import io.legado.app.domain.model.WallpaperAlignment
import io.legado.app.domain.model.WallpaperFit
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class PersonalizationTab {
    THEME,
    ICONS,
    WALLPAPER,
    PREVIEW,
}

enum class ProfileNameAction {
    DUPLICATE,
    RENAME,
}

@Stable
data class AppearanceProfileUi(
    val profile: AppearanceProfile,
    val active: Boolean,
)

@Stable
data class PersonalizationAssetRequest(
    val kind: AppearanceAssetKind,
    val slot: IconSlot? = null,
    val targetKey: String? = null,
    val dark: Boolean = false,
)

sealed interface PersonalizationDialog {
    data object DiscardChanges : PersonalizationDialog
    data class ProfileName(
        val action: ProfileNameAction,
        val profileId: String,
        val initialName: String,
    ) : PersonalizationDialog
    data class DeleteProfile(
        val profileId: String,
        val name: String,
    ) : PersonalizationDialog
}

@Stable
data class PersonalizationUiState(
    val loading: Boolean = true,
    val profiles: ImmutableList<AppearanceProfileUi> = persistentListOf(),
    val draft: AppearanceProfile? = null,
    val selectedProfileId: String? = null,
    val selectedTab: PersonalizationTab = PersonalizationTab.THEME,
    val selectedIconSlot: IconSlot = IconSlot.NAV_HOME,
    val selectedWallpaperTarget: String = "global",
    val editingDarkWallpaper: Boolean = false,
    val previewDark: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val dialog: PersonalizationDialog? = null,
    val resolvedIconPath: String? = null,
    val resolvedWallpaperPath: String? = null,
    val resolvedPreviewWallpaperPath: String? = null,
    val contrastWarning: Boolean = false,
)

sealed interface PersonalizationIntent {
    data class SelectTab(val tab: PersonalizationTab) : PersonalizationIntent
    data class SelectProfile(val profileId: String) : PersonalizationIntent
    data class SetEngine(val engine: AppearanceEngine) : PersonalizationIntent
    data class SetThemeMode(val mode: AppearanceThemeMode) : PersonalizationIntent
    data class SetFontScale(val value: Int) : PersonalizationIntent
    data class SetContainerOpacity(val value: Int) : PersonalizationIntent
    data class SetBlurEnabled(val enabled: Boolean) : PersonalizationIntent
    data class SetProgressiveBlurEnabled(val enabled: Boolean) : PersonalizationIntent
    data object Apply : PersonalizationIntent
    data object Discard : PersonalizationIntent
    data object Reset : PersonalizationIntent
    data object BackPressed : PersonalizationIntent
    data object DismissDialog : PersonalizationIntent
    data class RequestProfileName(
        val action: ProfileNameAction,
        val profileId: String,
    ) : PersonalizationIntent
    data class ConfirmProfileName(val name: String) : PersonalizationIntent
    data class RequestDeleteProfile(val profileId: String) : PersonalizationIntent
    data object ConfirmDeleteProfile : PersonalizationIntent
    data class SelectIconSlot(val slot: IconSlot) : PersonalizationIntent
    data class UpdateIcon(val spec: AppearanceIconSpec) : PersonalizationIntent
    data object RequestIconImport : PersonalizationIntent
    data object RemoveIcon : PersonalizationIntent
    data class SelectWallpaperTarget(val targetKey: String) : PersonalizationIntent
    data class SetEditingDarkWallpaper(val dark: Boolean) : PersonalizationIntent
    data class SetPreviewDark(val dark: Boolean) : PersonalizationIntent
    data class UpdateWallpaper(val spec: AppearanceWallpaperSpec) : PersonalizationIntent
    data class SetWallpaperFit(val fit: WallpaperFit) : PersonalizationIntent
    data class SetWallpaperHorizontalAlignment(
        val alignment: WallpaperAlignment,
    ) : PersonalizationIntent
    data class SetWallpaperVerticalAlignment(
        val alignment: WallpaperAlignment,
    ) : PersonalizationIntent
    data object RequestWallpaperImport : PersonalizationIntent
    data object RemoveWallpaper : PersonalizationIntent
    data class AssetPicked(val uri: String) : PersonalizationIntent
}

sealed interface PersonalizationEffect {
    data class PickAsset(val request: PersonalizationAssetRequest) : PersonalizationEffect
    data class ShowMessage(val message: String) : PersonalizationEffect
    data object Close : PersonalizationEffect
}
