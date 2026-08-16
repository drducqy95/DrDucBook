package io.legado.app.domain.gateway

import io.legado.app.domain.model.AppearanceAssetKind
import io.legado.app.domain.model.AppearanceProfile
import io.legado.app.domain.model.AppearanceState
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface AppearanceGateway {
    val state: StateFlow<AppearanceState>

    fun initialize()

    suspend fun saveProfile(profile: AppearanceProfile, activate: Boolean = false): AppearanceProfile

    suspend fun activate(profileId: String)

    suspend fun duplicate(profileId: String, name: String): AppearanceProfile

    suspend fun rename(profileId: String, name: String)

    suspend fun delete(profileId: String)

    suspend fun importAsset(uri: String, kind: AppearanceAssetKind): String

    suspend fun importAssetFile(source: File, kind: AppearanceAssetKind): String

    fun resolveAsset(assetId: String, legacyLocation: String? = null): String?

    suspend fun exportSnapshot(destination: File)

    suspend fun restoreSnapshot(source: File)

    suspend fun cleanupUnusedAssets(): Int
}
