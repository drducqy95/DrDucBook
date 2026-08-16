package io.legado.app.ui.personalization

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.AppearanceAssetKind
import io.legado.app.domain.model.AppearanceIconSpec
import io.legado.app.domain.model.AppearanceProfile
import io.legado.app.domain.model.AppearanceWallpaperSpec
import io.legado.app.domain.model.wallpaperFor
import io.legado.app.domain.usecase.AppearanceUseCase
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PersonalizationViewModel(
    private val appearance: AppearanceUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonalizationUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PersonalizationEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var baseline: AppearanceProfile? = null
    private var pendingAsset: PersonalizationAssetRequest? = null

    init {
        observeProfiles()
    }

    fun onIntent(intent: PersonalizationIntent) {
        when (intent) {
            is PersonalizationIntent.SelectTab ->
                _uiState.update { it.copy(selectedTab = intent.tab) }
            is PersonalizationIntent.SelectProfile -> selectProfile(intent.profileId)
            is PersonalizationIntent.SetEngine -> updateDraft { it.copy(engine = intent.engine) }
            is PersonalizationIntent.SetThemeMode -> updateDraft { it.copy(themeMode = intent.mode) }
            is PersonalizationIntent.SetFontScale ->
                updateDraft { it.copy(fontScale = intent.value.coerceIn(8, 15)) }
            is PersonalizationIntent.SetContainerOpacity ->
                updateDraft { it.copy(containerOpacity = intent.value.coerceIn(30, 100)) }
            is PersonalizationIntent.SetBlurEnabled ->
                updateDraft { it.copy(blurEnabled = intent.enabled) }
            is PersonalizationIntent.SetProgressiveBlurEnabled ->
                updateDraft { it.copy(progressiveBlurEnabled = intent.enabled) }
            PersonalizationIntent.Apply -> applyDraft()
            PersonalizationIntent.Discard -> discardDraft()
            PersonalizationIntent.Reset -> resetDraft()
            PersonalizationIntent.BackPressed -> requestBack()
            PersonalizationIntent.DismissDialog ->
                _uiState.update { it.copy(dialog = null) }
            is PersonalizationIntent.RequestProfileName ->
                requestProfileName(intent.action, intent.profileId)
            is PersonalizationIntent.ConfirmProfileName -> confirmProfileName(intent.name)
            is PersonalizationIntent.RequestDeleteProfile -> requestDelete(intent.profileId)
            PersonalizationIntent.ConfirmDeleteProfile -> confirmDelete()
            is PersonalizationIntent.SelectIconSlot -> {
                _uiState.update { it.copy(selectedIconSlot = intent.slot) }
                refreshResolvedAssets()
            }
            is PersonalizationIntent.UpdateIcon -> updateIcon(intent.spec)
            PersonalizationIntent.RequestIconImport -> requestIconImport()
            PersonalizationIntent.RemoveIcon -> updateIcon(AppearanceIconSpec())
            is PersonalizationIntent.SelectWallpaperTarget -> {
                _uiState.update { it.copy(selectedWallpaperTarget = intent.targetKey) }
                refreshResolvedAssets()
            }
            is PersonalizationIntent.SetEditingDarkWallpaper -> {
                _uiState.update { it.copy(editingDarkWallpaper = intent.dark) }
                refreshResolvedAssets()
            }
            is PersonalizationIntent.SetPreviewDark -> {
                _uiState.update {
                    val draft = it.draft
                    it.copy(
                        previewDark = intent.dark,
                        contrastWarning = draft?.let { profile ->
                            hasContrastWarning(profile, intent.dark)
                        } ?: false,
                    )
                }
                refreshResolvedAssets()
            }
            is PersonalizationIntent.UpdateWallpaper -> updateWallpaper(intent.spec)
            is PersonalizationIntent.SetWallpaperFit ->
                updateWallpaper(currentWallpaper().copy(fit = intent.fit))
            is PersonalizationIntent.SetWallpaperHorizontalAlignment ->
                updateWallpaper(currentWallpaper().copy(horizontalAlignment = intent.alignment))
            is PersonalizationIntent.SetWallpaperVerticalAlignment ->
                updateWallpaper(currentWallpaper().copy(verticalAlignment = intent.alignment))
            PersonalizationIntent.RequestWallpaperImport -> requestWallpaperImport()
            PersonalizationIntent.RemoveWallpaper -> removeWallpaper()
            is PersonalizationIntent.AssetPicked -> importAsset(intent.uri)
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            appearance.state.collect { state ->
                val restoredDraft = _uiState.value.draft ?: restoreDraft()
                val draft = restoredDraft ?: state.activeProfile
                if (baseline == null) {
                    baseline = state.profiles.firstOrNull { it.id == draft.id } ?: state.activeProfile
                }
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        profiles = state.profiles.map { profile ->
                            AppearanceProfileUi(
                                profile = profile,
                                active = profile.id == state.activeProfileId,
                            )
                        }.toImmutableList(),
                        draft = draft,
                        selectedProfileId = current.selectedProfileId ?: draft.id,
                        dirty = savedStateHandle[DIRTY_KEY] ?: false,
                        saving = false,
                    )
                }
                refreshResolvedAssets()
            }
        }
    }

    private fun selectProfile(profileId: String) {
        val profile = _uiState.value.profiles.firstOrNull { it.profile.id == profileId }
            ?.profile ?: return
        baseline = profile
        setDraft(profile, profile.id != activeProfileId())
        _uiState.update { it.copy(selectedProfileId = profile.id) }
    }

    private fun updateDraft(transform: (AppearanceProfile) -> AppearanceProfile) {
        val draft = _uiState.value.draft ?: return
        setDraft(transform(draft), dirty = true)
    }

    private fun updateIcon(spec: AppearanceIconSpec) {
        val slot = _uiState.value.selectedIconSlot
        updateDraft { profile ->
            profile.copy(iconSlots = profile.iconSlots + (slot.key to spec))
        }
    }

    private fun currentIcon(): AppearanceIconSpec {
        val state = _uiState.value
        return state.draft?.iconSlots?.get(state.selectedIconSlot.key) ?: AppearanceIconSpec()
    }

    private fun currentWallpaper(): AppearanceWallpaperSpec {
        val state = _uiState.value
        val map = if (state.editingDarkWallpaper) {
            state.draft?.darkWallpapers
        } else {
            state.draft?.lightWallpapers
        }
        return map?.get(state.selectedWallpaperTarget)
            ?: map?.get(io.legado.app.domain.model.AppearanceTarget.GLOBAL.key)
            ?: AppearanceWallpaperSpec()
    }

    private fun updateWallpaper(spec: AppearanceWallpaperSpec) {
        val target = _uiState.value.selectedWallpaperTarget
        val editDark = _uiState.value.editingDarkWallpaper
        updateDraft { profile ->
            if (editDark) {
                profile.copy(darkWallpapers = profile.darkWallpapers + (target to spec))
            } else {
                profile.copy(lightWallpapers = profile.lightWallpapers + (target to spec))
            }
        }
    }

    private fun removeWallpaper() {
        val target = _uiState.value.selectedWallpaperTarget
        val editDark = _uiState.value.editingDarkWallpaper
        updateDraft { profile ->
            if (editDark) {
                profile.copy(darkWallpapers = profile.darkWallpapers - target)
            } else {
                profile.copy(lightWallpapers = profile.lightWallpapers - target)
            }
        }
    }

    private fun requestIconImport() {
        pendingAsset = PersonalizationAssetRequest(
            kind = AppearanceAssetKind.ICON,
            slot = _uiState.value.selectedIconSlot,
        )
        _effects.tryEmit(PersonalizationEffect.PickAsset(pendingAsset!!))
    }

    private fun requestWallpaperImport() {
        pendingAsset = PersonalizationAssetRequest(
            kind = AppearanceAssetKind.WALLPAPER,
            targetKey = _uiState.value.selectedWallpaperTarget,
            dark = _uiState.value.editingDarkWallpaper,
        )
        _effects.tryEmit(PersonalizationEffect.PickAsset(pendingAsset!!))
    }

    private fun importAsset(uri: String) {
        val request = pendingAsset ?: return
        viewModelScope.launch {
            runCatching {
                appearance.importAsset(uri, request.kind)
            }.onSuccess { assetId ->
                when (request.kind) {
                    AppearanceAssetKind.ICON -> {
                        val current = currentIcon()
                        updateIcon(current.copy(assetId = assetId, legacyLocation = null))
                    }
                    AppearanceAssetKind.WALLPAPER -> {
                        val current = currentWallpaper()
                        updateWallpaper(current.copy(assetId = assetId, legacyLocation = null))
                    }
                    AppearanceAssetKind.FONT -> Unit
                }
                _effects.tryEmit(PersonalizationEffect.ShowMessage("Đã thêm asset vào bản nháp"))
            }.onFailure {
                _effects.tryEmit(
                    PersonalizationEffect.ShowMessage(
                        it.message ?: "Không thể nhập asset giao diện"
                    )
                )
            }
            pendingAsset = null
        }
    }

    private fun applyDraft() {
        val draft = _uiState.value.draft ?: return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                appearance.save(draft, activate = true)
            }.onSuccess { saved ->
                baseline = saved
                savedStateHandle[DRAFT_KEY] = null
                savedStateHandle[DIRTY_KEY] = false
                _uiState.update {
                    it.copy(
                        draft = saved,
                        selectedProfileId = saved.id,
                        dirty = false,
                        saving = false,
                    )
                }
                _effects.tryEmit(PersonalizationEffect.ShowMessage("Đã áp dụng giao diện"))
            }.onFailure {
                _uiState.update { it.copy(saving = false) }
                _effects.tryEmit(
                    PersonalizationEffect.ShowMessage(it.message ?: "Không thể áp dụng giao diện")
                )
            }
        }
    }

    private fun discardDraft() {
        val active = _uiState.value.profiles.firstOrNull { it.active }?.profile ?: return
        baseline = active
        setDraft(active, dirty = false)
        _uiState.update { it.copy(selectedProfileId = active.id, dialog = null) }
    }

    private fun resetDraft() {
        baseline?.let { setDraft(it, dirty = it.id != activeProfileId()) }
    }

    private fun requestBack() {
        if (_uiState.value.dirty) {
            _uiState.update { it.copy(dialog = PersonalizationDialog.DiscardChanges) }
        } else {
            _effects.tryEmit(PersonalizationEffect.Close)
        }
    }

    private fun requestProfileName(action: ProfileNameAction, profileId: String) {
        val profile = _uiState.value.profiles.firstOrNull { it.profile.id == profileId }
            ?.profile ?: return
        _uiState.update {
            it.copy(
                dialog = PersonalizationDialog.ProfileName(
                    action = action,
                    profileId = profileId,
                    initialName = if (action == ProfileNameAction.DUPLICATE) {
                        profile.name + " - ban sao"
                    } else {
                        profile.name
                    },
                )
            )
        }
    }

    private fun confirmProfileName(name: String) {
        val dialog = _uiState.value.dialog as? PersonalizationDialog.ProfileName ?: return
        viewModelScope.launch {
            runCatching {
                when (dialog.action) {
                    ProfileNameAction.DUPLICATE ->
                        appearance.duplicate(dialog.profileId, name)
                    ProfileNameAction.RENAME -> {
                        appearance.rename(dialog.profileId, name)
                        null
                    }
                }
            }.onSuccess { duplicate ->
                _uiState.update { it.copy(dialog = null) }
                duplicate?.let {
                    baseline = it
                    setDraft(it, dirty = true)
                    _uiState.update { state -> state.copy(selectedProfileId = it.id) }
                }
            }.onFailure {
                _effects.tryEmit(
                    PersonalizationEffect.ShowMessage(it.message ?: "Không thể lưu hồ sơ")
                )
            }
        }
    }

    private fun requestDelete(profileId: String) {
        val profile = _uiState.value.profiles.firstOrNull { it.profile.id == profileId }
            ?.profile ?: return
        if (profile.builtIn) return
        _uiState.update {
            it.copy(dialog = PersonalizationDialog.DeleteProfile(profile.id, profile.name))
        }
    }

    private fun confirmDelete() {
        val dialog = _uiState.value.dialog as? PersonalizationDialog.DeleteProfile ?: return
        viewModelScope.launch {
            runCatching {
                appearance.delete(dialog.profileId)
            }.onSuccess {
                _uiState.update { it.copy(dialog = null) }
                discardDraft()
                appearance.cleanupUnusedAssets()
            }.onFailure {
                _effects.tryEmit(
                    PersonalizationEffect.ShowMessage(it.message ?: "Không thể xóa hồ sơ")
                )
            }
        }
    }

    private fun setDraft(profile: AppearanceProfile, dirty: Boolean) {
        savedStateHandle[DRAFT_KEY] = GSON.toJson(profile)
        savedStateHandle[DIRTY_KEY] = dirty
        _uiState.update {
            it.copy(
                draft = profile,
                dirty = dirty,
                contrastWarning = hasContrastWarning(profile, it.previewDark),
            )
        }
        refreshResolvedAssets()
    }

    private fun refreshResolvedAssets() {
        val state = _uiState.value
        val profile = state.draft ?: return
        val icon = profile.iconSlots[state.selectedIconSlot.key]
        val wallpaper = profile.wallpaperFor(
            target = io.legado.app.domain.model.AppearanceTarget.entries.firstOrNull {
                it.key == state.selectedWallpaperTarget
            } ?: io.legado.app.domain.model.AppearanceTarget.GLOBAL,
            dark = state.editingDarkWallpaper,
        )
        val previewWallpaper = profile.wallpaperFor(
            io.legado.app.domain.model.AppearanceTarget.GLOBAL,
            state.previewDark,
        )
        _uiState.update {
            it.copy(
                resolvedIconPath = icon?.let { spec ->
                    appearance.resolveAsset(spec.assetId, spec.legacyLocation)
                },
                resolvedWallpaperPath = wallpaper?.let { spec ->
                    appearance.resolveAsset(spec.assetId, spec.legacyLocation)
                },
                resolvedPreviewWallpaperPath = previewWallpaper?.let { spec ->
                    appearance.resolveAsset(spec.assetId, spec.legacyLocation)
                },
                contrastWarning = hasContrastWarning(profile, state.previewDark),
            )
        }
    }

    private fun restoreDraft(): AppearanceProfile? =
        savedStateHandle.get<String>(DRAFT_KEY)?.let { json ->
            runCatching { GSON.fromJson(json, AppearanceProfile::class.java) }.getOrNull()
        }

    private fun activeProfileId(): String? =
        _uiState.value.profiles.firstOrNull { it.active }?.profile?.id

    private fun hasContrastWarning(profile: AppearanceProfile, dark: Boolean): Boolean {
        val colors = if (dark) profile.darkColors else profile.lightColors
        val wallpaper = profile.wallpaperFor(
            io.legado.app.domain.model.AppearanceTarget.GLOBAL,
            dark,
        )
        return contrast(colors.primaryText, colors.background) < 4.5 ||
            wallpaper != null &&
            wallpaper.opacityPercent > 80 &&
            wallpaper.dimPercent < 20 &&
            wallpaper.overlayColor == null
    }

    private fun contrast(first: Int, second: Int): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private companion object {
        const val DRAFT_KEY = "personalization.draft"
        const val DIRTY_KEY = "personalization.dirty"
    }
}
