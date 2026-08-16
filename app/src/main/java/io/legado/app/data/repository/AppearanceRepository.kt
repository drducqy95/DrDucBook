package io.legado.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.legado.app.domain.gateway.AppearanceGateway
import io.legado.app.domain.model.APPEARANCE_SCHEMA_VERSION
import io.legado.app.domain.model.AppearanceAssetKind
import io.legado.app.domain.model.AppearancePresets
import io.legado.app.domain.model.AppearanceProfile
import io.legado.app.domain.model.AppearanceSnapshot
import io.legado.app.domain.model.AppearanceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class AppearanceRepository(
    private val context: Context,
) : AppearanceGateway {
    private val root = File(context.filesDir, APPEARANCE_FOLDER)
    private val assets = File(root, ASSET_FOLDER)
    private val store = AppearanceFileStore(root)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(loadInitialState())
    override val state = _state.asStateFlow()

    init {
        AppearanceThemeAdapter.apply(_state.value.activeProfile, ::resolveAsset)
    }

    override fun initialize() = Unit

    override suspend fun saveProfile(
        profile: AppearanceProfile,
        activate: Boolean,
    ): AppearanceProfile = mutex.withLock {
        val now = System.currentTimeMillis()
        val canonical = AppearancePresets.all.firstOrNull { it.id == profile.id }
        val saved = when {
            canonical == profile -> canonical
            canonical != null -> profile.copy(
                id = "custom-" + UUID.randomUUID(),
                name = profile.name + " tuy chinh",
                builtIn = false,
                updatedAt = now,
            )
            else -> profile.copy(updatedAt = now)
        }
        val current = _state.value
        val profiles = current.profiles
            .filterNot { it.id == saved.id }
            .plus(saved)
        val activeId = if (activate) saved.id else current.activeProfileId
        updateState(profiles, activeId)
        saved
    }

    override suspend fun activate(profileId: String) = mutex.withLock {
        val profile = _state.value.profiles.firstOrNull { it.id == profileId }
            ?: error("Không tìm thấy hồ sơ giao diện")
        updateState(_state.value.profiles, profile.id)
    }

    override suspend fun duplicate(
        profileId: String,
        name: String,
    ): AppearanceProfile = mutex.withLock {
        require(name.isNotBlank()) { "Tên hồ sơ không được để trống" }
        val source = _state.value.profiles.firstOrNull { it.id == profileId }
            ?: error("Không tìm thấy hồ sơ giao diện")
        val duplicate = source.copy(
            id = "custom-" + UUID.randomUUID(),
            name = name.trim(),
            builtIn = false,
            updatedAt = System.currentTimeMillis(),
        )
        updateState(_state.value.profiles + duplicate, _state.value.activeProfileId)
        duplicate
    }

    override suspend fun rename(profileId: String, name: String) = mutex.withLock {
        require(name.isNotBlank()) { "Tên hồ sơ không được để trống" }
        val source = _state.value.profiles.firstOrNull { it.id == profileId }
            ?: error("Không tìm thấy hồ sơ giao diện")
        require(!source.builtIn) { "Hay nhan ban theme tich hop truoc khi doi ten" }
        val profiles = _state.value.profiles.map {
            if (it.id == profileId) {
                it.copy(name = name.trim(), updatedAt = System.currentTimeMillis())
            } else {
                it
            }
        }
        updateState(profiles, _state.value.activeProfileId)
    }

    override suspend fun delete(profileId: String) = mutex.withLock {
        val source = _state.value.profiles.firstOrNull { it.id == profileId }
            ?: return@withLock
        require(!source.builtIn) { "Không thể xóa theme tích hợp" }
        val profiles = _state.value.profiles.filterNot { it.id == profileId }
        val activeId = if (_state.value.activeProfileId == profileId) {
            AppearancePresets.fallback().id
        } else {
            _state.value.activeProfileId
        }
        updateState(profiles, activeId)
    }

    override suspend fun importAsset(
        uri: String,
        kind: AppearanceAssetKind,
    ): String = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val resolver = context.contentResolver
        val mimeType = resolver.getType(parsed)
        val displayName = resolver.query(
            parsed,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val maxBytes = AppearanceAssetPolicy.maxBytes(kind)
        val bytes = resolver.openInputStream(parsed)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) { "Tệp giao diện vượt quá giới hạn" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("Không thể đọc tệp giao diện")
        storeValidatedAsset(bytes, displayName, mimeType, kind)
    }

    override suspend fun importAssetFile(
        source: File,
        kind: AppearanceAssetKind,
    ): String = withContext(Dispatchers.IO) {
        require(source.isFile) { "Không tìm thấy tệp giao diện" }
        require(source.length() <= AppearanceAssetPolicy.maxBytes(kind)) {
            "Tệp giao diện vượt quá giới hạn"
        }
        storeValidatedAsset(source.readBytes(), source.name, null, kind)
    }

    private fun storeValidatedAsset(
        bytes: ByteArray,
        displayName: String?,
        mimeType: String?,
        kind: AppearanceAssetKind,
    ): String {
        val validated = AppearanceAssetPolicy.validate(bytes, displayName, mimeType, kind)
        assets.mkdirs()
        val fileName = validated.sha256 + "." + validated.extension
        val target = File(assets, fileName)
        if (!target.isFile) {
            val temp = File(assets, "$fileName.tmp")
            temp.writeBytes(validated.bytes)
            require(temp.renameTo(target) || run {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }) { "Không thể lưu asset giao diện" }
        }
        return fileName
    }

    override fun resolveAsset(assetId: String, legacyLocation: String?): String? {
        if (assetId.isNotBlank() && assetId.matches(SAFE_ASSET_NAME)) {
            val file = File(assets, assetId)
            if (file.isFile) {
                return file.absolutePath
            }
        }
        return legacyLocation?.takeIf { it.isNotBlank() }
    }

    override suspend fun exportSnapshot(destination: File) = mutex.withLock {
        val current = _state.value
        val snapshot = AppearanceSnapshot(
            schemaVersion = APPEARANCE_SCHEMA_VERSION,
            activeProfileId = current.activeProfileId,
            profiles = current.profiles,
        )
        withContext(Dispatchers.IO) {
            // Export the already validated in-memory state directly. A corrupt or legacy local file
            // must not prevent every backup target from being created.
            AppearanceBackupFiles.exportValidatedSnapshot(
                snapshot = snapshot,
                sourceAssets = assets,
                destination = destination,
            )
            // Best-effort migration/self-heal to the stable on-disk schema.
            runCatching { store.write(snapshot) }
            Unit
        }
    }

    override suspend fun restoreSnapshot(source: File) = mutex.withLock {
        withContext(Dispatchers.IO) {
            AppearanceBackupFiles.copyValidatedSnapshot(source, root)
        }
        val restoredState = loadInitialState()
        _state.value = restoredState.copy(revision = _state.value.revision + 1)
        AppearanceThemeAdapter.apply(_state.value.activeProfile, ::resolveAsset)
        cleanupUnusedAssets()
        Unit
    }

    override suspend fun cleanupUnusedAssets(): Int = withContext(Dispatchers.IO) {
        val referenced = _state.value.profiles.flatMap { profile ->
            buildList {
                addAll(profile.iconSlots.values.map { it.assetId })
                addAll(profile.lightWallpapers.values.map { it.assetId })
                addAll(profile.darkWallpapers.values.map { it.assetId })
            }
        }.filter(String::isNotBlank).toSet()
        assets.listFiles()
            .orEmpty()
            .filter(File::isFile)
            .filterNot { it.name in referenced }
            .count { it.delete() }
    }

    private fun loadInitialState(): AppearanceState {
        val snapshot = store.readOrNull() ?: AppearanceSnapshot(
            activeProfileId = AppearanceThemeAdapter.LEGACY_PROFILE_ID,
            profiles = AppearancePresets.all + AppearanceThemeAdapter.fromLegacy(),
        ).also(store::write)
        val customProfiles = snapshot.profiles.filterNot { profile ->
            AppearancePresets.all.any { it.id == profile.id }
        }
        val profiles = AppearancePresets.all + customProfiles
        val activeId = snapshot.activeProfileId.takeIf { id ->
            profiles.any { it.id == id }
        } ?: AppearancePresets.fallback().id
        return AppearanceState(
            activeProfileId = activeId,
            profiles = profiles,
        )
    }

    private suspend fun updateState(
        profiles: List<AppearanceProfile>,
        activeId: String,
    ) {
        val normalized = profiles.distinctBy { it.id }
        val snapshot = AppearanceSnapshot(
            schemaVersion = APPEARANCE_SCHEMA_VERSION,
            activeProfileId = activeId,
            profiles = normalized,
        )
        withContext(Dispatchers.IO) {
            store.write(snapshot)
        }
        _state.value = AppearanceState(
            activeProfileId = activeId,
            profiles = normalized,
            revision = _state.value.revision + 1,
        )
        AppearanceThemeAdapter.apply(_state.value.activeProfile, ::resolveAsset)
    }

    companion object {
        const val APPEARANCE_FOLDER = "appearance"
        const val ASSET_FOLDER = "assets"
        private val SAFE_ASSET_NAME = Regex("^[a-f0-9]{64}\\.(png|jpg|webp|svg|ttf|otf)$")
    }
}
