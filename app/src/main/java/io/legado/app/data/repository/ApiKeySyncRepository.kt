package io.legado.app.data.repository

import android.content.Context
import androidx.core.content.edit
import com.drducbook.app.cloud.CloudSyncClientContract
import com.drducbook.app.cloud.SupabaseClientProvider
import com.drducbook.app.cloud.SupabasePublicConfig
import io.legado.app.data.dao.AiProfileDao
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.domain.gateway.AccountAuthGateway
import io.legado.app.domain.gateway.AiSecretStore
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.ApiKeyBundle
import io.legado.app.domain.model.ApiKeyEntry
import io.legado.app.domain.model.ApiKeySyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant

class ApiKeySyncRepository(
    context: Context,
    private val aiProfileDao: AiProfileDao,
    private val secretStore: AiSecretStore,
    private val accountAuthGateway: AccountAuthGateway,
    config: SupabasePublicConfig = SupabaseClientProvider.config,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val rest = SupabaseAuthenticatedRestClient(config, accountAuthGateway)

    private val _syncStatus = MutableStateFlow<ApiKeySyncStatus>(loadInitialStatus())
    val syncStatus: StateFlow<ApiKeySyncStatus> = _syncStatus.asStateFlow()

    val configured: Boolean
        get() = rest.configured

    fun isAutoSyncEnabled(): Boolean =
        preferences.getBoolean(KEY_AUTO_SYNC, true)

    fun setAutoSyncEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTO_SYNC, enabled) }
    }

    fun getLastSyncedTimestamp(): Long =
        preferences.getLong(KEY_LAST_SYNCED_AT, 0L)

    private fun loadInitialStatus(): ApiKeySyncStatus {
        val lastSynced = preferences.getLong(KEY_LAST_SYNCED_AT, 0L)
        val count = preferences.getInt(KEY_LAST_SYNCED_COUNT, 0)
        return if (lastSynced > 0L) {
            ApiKeySyncStatus.Synced(lastSynced, count)
        } else {
            ApiKeySyncStatus.NotSynced
        }
    }

    suspend fun collectProviderKeys(): ApiKeyBundle = withContext(Dispatchers.IO) {
        val profiles = aiProfileDao.getProviders()
        val entries = profiles.mapNotNull { profile ->
            val resolvedKey = profile.secretRef?.let(secretStore::get)?.takeIf(String::isNotBlank)
                ?: profile.apiKey.takeIf(String::isNotBlank)
            if (resolvedKey.isNullOrBlank()) return@mapNotNull null

            ApiKeyEntry(
                providerId = profile.id,
                providerName = profile.name,
                protocol = profile.protocol,
                baseUrl = profile.baseUrl,
                apiKey = resolvedKey,
                authType = profile.authType,
                modelsUrl = profile.modelsUrl,
                customHeadersJson = profile.customHeadersJson,
                chatPath = profile.chatPath,
                enabled = profile.enabled,
            )
        }
        ApiKeyBundle(
            version = 1,
            updatedAt = Instant.now().toString(),
            providers = entries,
        )
    }

    suspend fun pushToCloud(session: AccountSession): Result<Int> = withContext(Dispatchers.IO) {
        if (!configured) {
            val error = "Dịch vụ đám mây chưa được cấu hình"
            _syncStatus.value = ApiKeySyncStatus.Error(error)
            return@withContext Result.failure(IllegalStateException(error))
        }

        _syncStatus.value = ApiKeySyncStatus.Syncing
        val bundle = collectProviderKeys()
        if (bundle.providers.isEmpty()) {
            val error = "Không có API key nào để đồng bộ"
            _syncStatus.value = ApiKeySyncStatus.Error(error)
            return@withContext Result.failure(IllegalStateException(error))
        }

        val tempFile = File.createTempFile("api_keys_upload_", ".enc", appContext.cacheDir)
        try {
            val masterSecret = deriveMasterSecret(session)
            val jsonText = json.encodeToString(bundle)
            val encrypted = ApiKeySyncEncryption.encrypt(jsonText.toByteArray(Charsets.UTF_8), masterSecret)
            tempFile.writeBytes(encrypted)

            val userId = CloudSyncClientContract.normalizeUuid(session.userId, "userId")
            val objectPath = CloudSyncClientContract.userAssetObjectPath(userId, API_KEYS_RELATIVE_PATH)

            rest.postFile(
                path = "storage/v1/object/${CloudSyncClientContract.USER_ASSET_BUCKET}/$objectPath",
                source = tempFile,
                upsert = true,
            )

            val now = System.currentTimeMillis()
            preferences.edit {
                putLong(KEY_LAST_SYNCED_AT, now)
                putInt(KEY_LAST_SYNCED_COUNT, bundle.providers.size)
            }
            _syncStatus.value = ApiKeySyncStatus.Synced(now, bundle.providers.size)
            Result.success(bundle.providers.size)
        } catch (t: Throwable) {
            val errorMsg = t.message ?: "Lỗi tải API key lên đám mây"
            _syncStatus.value = ApiKeySyncStatus.Error(errorMsg)
            Result.failure(t)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun pullFromCloud(session: AccountSession): Result<ApiKeyBundle> = withContext(Dispatchers.IO) {
        if (!configured) {
            val error = "Dịch vụ đám mây chưa được cấu hình"
            return@withContext Result.failure(IllegalStateException(error))
        }

        _syncStatus.value = ApiKeySyncStatus.Syncing
        val tempFile = File.createTempFile("api_keys_download_", ".enc", appContext.cacheDir)
        try {
            val userId = CloudSyncClientContract.normalizeUuid(session.userId, "userId")
            val objectPath = CloudSyncClientContract.userAssetObjectPath(userId, API_KEYS_RELATIVE_PATH)

            val found = rest.downloadTo(
                path = "storage/v1/object/authenticated/${CloudSyncClientContract.USER_ASSET_BUCKET}/$objectPath",
                destination = tempFile,
            )
            if (!found || tempFile.length() == 0L) {
                val error = "Không tìm thấy dữ liệu API key trên đám mây"
                _syncStatus.value = ApiKeySyncStatus.Error(error)
                return@withContext Result.failure(FileNotFoundException(error))
            }

            val masterSecret = deriveMasterSecret(session)
            val decrypted = ApiKeySyncEncryption.decrypt(tempFile.readBytes(), masterSecret)
            val bundle = json.decodeFromString<ApiKeyBundle>(decrypted.toString(Charsets.UTF_8))

            val now = System.currentTimeMillis()
            preferences.edit {
                putLong(KEY_LAST_SYNCED_AT, now)
                putInt(KEY_LAST_SYNCED_COUNT, bundle.providers.size)
            }
            _syncStatus.value = ApiKeySyncStatus.Synced(now, bundle.providers.size)
            Result.success(bundle)
        } catch (t: Throwable) {
            val errorMsg = t.message ?: "Lỗi tải API key từ đám mây"
            _syncStatus.value = ApiKeySyncStatus.Error(errorMsg)
            Result.failure(t)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun restoreAndAutoActivate(bundle: ApiKeyBundle): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        for (entry in bundle.providers) {
            if (entry.apiKey.isBlank()) continue

            val secretRef = secretStore.put(entry.apiKey, null)
            val existing = aiProfileDao.getProvider(entry.providerId)

            if (existing == null) {
                val newProfile = AiProviderProfile(
                    id = entry.providerId,
                    name = entry.providerName,
                    protocol = entry.protocol,
                    baseUrl = entry.baseUrl,
                    modelsUrl = entry.modelsUrl,
                    apiKey = "",
                    authType = entry.authType,
                    secretRef = secretRef,
                    customHeadersJson = entry.customHeadersJson,
                    chatPath = entry.chatPath,
                    enabled = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                aiProfileDao.insertProvider(newProfile)
            } else {
                val updatedProfile = existing.copy(
                    name = if (existing.name.isNotBlank()) existing.name else entry.providerName,
                    baseUrl = if (existing.baseUrl.isNotBlank()) existing.baseUrl else entry.baseUrl,
                    secretRef = secretRef,
                    enabled = true,
                    updatedAt = System.currentTimeMillis(),
                )
                aiProfileDao.updateProvider(updatedProfile)
            }
            restoredCount++
        }
        restoredCount
    }

    private suspend fun deriveMasterSecret(session: AccountSession): String {
        val userId = CloudSyncClientContract.normalizeUuid(session.userId, "userId")
        val token = accountAuthGateway.currentAccessToken().orEmpty()
        val tokenFingerprint = token.take(32)
        return "drducbook-user-$userId-auth-$tokenFingerprint"
    }

    companion object {
        private const val PREFERENCES_NAME = "drducbook_api_key_sync"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at"
        private const val KEY_LAST_SYNCED_COUNT = "last_synced_count"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val API_KEYS_RELATIVE_PATH = "secrets/api-keys.enc"
    }
}
