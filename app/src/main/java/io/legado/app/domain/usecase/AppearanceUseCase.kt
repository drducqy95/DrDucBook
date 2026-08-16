package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AppearanceGateway
import io.legado.app.domain.model.AppearanceAssetKind
import io.legado.app.domain.model.AppearanceProfile
import io.legado.app.domain.model.AppearanceState
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class AppearanceUseCase(
    private val gateway: AppearanceGateway,
) {
    val state: StateFlow<AppearanceState> = gateway.state

    suspend fun save(profile: AppearanceProfile, activate: Boolean = false) =
        gateway.saveProfile(profile, activate)

    suspend fun activate(profileId: String) = gateway.activate(profileId)

    suspend fun duplicate(profileId: String, name: String) =
        gateway.duplicate(profileId, name)

    suspend fun rename(profileId: String, name: String) = gateway.rename(profileId, name)

    suspend fun delete(profileId: String) = gateway.delete(profileId)

    suspend fun importAsset(uri: String, kind: AppearanceAssetKind) =
        gateway.importAsset(uri, kind)

    suspend fun importAssetFile(source: File, kind: AppearanceAssetKind) =
        gateway.importAssetFile(source, kind)

    fun resolveAsset(assetId: String, legacyLocation: String? = null) =
        gateway.resolveAsset(assetId, legacyLocation)

    suspend fun exportSnapshot(destination: File) = gateway.exportSnapshot(destination)

    suspend fun restoreSnapshot(source: File) = gateway.restoreSnapshot(source)

    suspend fun cleanupUnusedAssets() = gateway.cleanupUnusedAssets()
}
