package io.legado.app.data.repository

import android.content.Context
import org.robolectric.RuntimeEnvironment
import io.legado.app.data.dao.AiProfileDao
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.domain.gateway.AccountAuthGateway
import io.legado.app.domain.gateway.AiSecretStore
import io.legado.app.domain.model.AccountAuthResult
import io.legado.app.domain.model.AccountEmailCredentials
import io.legado.app.domain.model.AccountGoogleIdCredential
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.AccountSignOutMode
import io.legado.app.domain.model.ApiKeyBundle
import io.legado.app.domain.model.ApiKeyEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import android.app.Application
import org.robolectric.annotation.Config
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ApiKeySyncRepositoryTest {

    private lateinit var context: Context
    private lateinit var fakeDao: FakeAiProfileDao
    private lateinit var fakeSecretStore: FakeAiSecretStore
    private lateinit var fakeAuthGateway: FakeAccountAuthGateway
    private lateinit var repository: ApiKeySyncRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.injectAsAppCtx()
        fakeDao = FakeAiProfileDao()
        fakeSecretStore = FakeAiSecretStore()
        fakeAuthGateway = FakeAccountAuthGateway()
        repository = ApiKeySyncRepository(
            context = context,
            aiProfileDao = fakeDao,
            secretStore = fakeSecretStore,
            accountAuthGateway = fakeAuthGateway,
        )
    }

    @Test
    fun collectProviderKeysOnlyIncludesNonEmptyKeys() = runBlocking {
        fakeSecretStore.putSecret("sec_1", "sk-valid-key")
        fakeDao.insertProvider(
            AiProviderProfile(
                id = "openai",
                name = "OpenAI",
                protocol = "openai_chat",
                baseUrl = "https://api.openai.com",
                apiKey = "",
                secretRef = "sec_1",
                enabled = true,
            )
        )
        fakeDao.insertProvider(
            AiProviderProfile(
                id = "empty_provider",
                name = "Empty",
                protocol = "gemini",
                baseUrl = "https://gemini.api",
                apiKey = "",
                secretRef = null,
                enabled = false,
            )
        )

        val bundle = repository.collectProviderKeys()
        assertEquals(1, bundle.providers.size)
        assertEquals("openai", bundle.providers.first().providerId)
        assertEquals("sk-valid-key", bundle.providers.first().apiKey)
    }

    @Test
    fun restoreAndAutoActivateCreatesNewProfilesAndEnablesThem() = runBlocking {
        val bundle = ApiKeyBundle(
            version = 1,
            updatedAt = "2026-10-03T12:00:00Z",
            providers = listOf(
                ApiKeyEntry(
                    providerId = "gemini",
                    providerName = "Google Gemini",
                    protocol = "gemini",
                    baseUrl = "https://generativelanguage.googleapis.com",
                    apiKey = "AIzaSySecret",
                    enabled = true,
                )
            )
        )

        val count = repository.restoreAndAutoActivate(bundle)
        assertEquals(1, count)

        val provider = fakeDao.getProvider("gemini")
        assertNotNull(provider)
        assertTrue(provider!!.enabled)
        assertNotNull(provider.secretRef)
        assertEquals("AIzaSySecret", fakeSecretStore.get(provider.secretRef!!))
    }

    @Test
    fun autoSyncPreferenceToggleWorks() {
        repository.setAutoSyncEnabled(false)
        assertEquals(false, repository.isAutoSyncEnabled())
        repository.setAutoSyncEnabled(true)
        assertEquals(true, repository.isAutoSyncEnabled())
    }

    private class FakeAiSecretStore : AiSecretStore {
        private val secrets = mutableMapOf<String, String>()

        fun putSecret(ref: String, secret: String) {
            secrets[ref] = secret
        }

        override fun put(secret: String, secretRef: String?): String {
            val ref = secretRef ?: "sec_${secrets.size + 1}"
            secrets[ref] = secret
            return ref
        }

        override fun get(secretRef: String): String? = secrets[secretRef]

        override fun delete(secretRef: String) {
            secrets.remove(secretRef)
        }
    }

    private class FakeAiProfileDao : AiProfileDao {
        private val providers = mutableMapOf<String, AiProviderProfile>()

        override fun observeProviders(): Flow<List<AiProviderProfile>> = flowOf(providers.values.toList())
        override fun observeModels(): Flow<List<AiModelProfile>> = flowOf(emptyList())
        override fun observePresets(): Flow<List<AiTaskPreset>> = flowOf(emptyList())
        override suspend fun getProviders(): List<AiProviderProfile> = providers.values.toList()
        override suspend fun getProvider(id: String): AiProviderProfile? = providers[id]
        override suspend fun setProviderEnabled(id: String, enabled: Boolean, updatedAt: Long) {
            providers[id]?.let { providers[id] = it.copy(enabled = enabled, updatedAt = updatedAt) }
        }
        override suspend fun getModel(id: String): AiModelProfile? = null
        override suspend fun getModelsByProvider(providerId: String): List<AiModelProfile> = emptyList()
        override suspend fun getPreset(id: String): AiTaskPreset? = null
        override suspend fun getDefaultPreset(taskType: String): AiTaskPreset? = null
        override suspend fun getFirstEnabledPreset(taskType: String): AiTaskPreset? = null
        override suspend fun getFirstEnabledPresetExcluding(taskType: String, excludedPresetId: String): AiTaskPreset? = null
        override suspend fun countProviders(): Int = providers.size
        override suspend fun insertProvider(provider: AiProviderProfile) { providers[provider.id] = provider }
        override suspend fun insertModel(model: AiModelProfile) {}
        override suspend fun insertPreset(preset: AiTaskPreset) {}
        override suspend fun updateProvider(provider: AiProviderProfile) { providers[provider.id] = provider }
        override suspend fun updateModel(model: AiModelProfile) {}
        override suspend fun updatePreset(preset: AiTaskPreset) {}
        override suspend fun deleteProvider(providerId: String) { providers.remove(providerId) }
        override suspend fun deleteModel(modelId: String) {}
        override suspend fun deleteModelsByProvider(providerId: String) {}
        override suspend fun deletePreset(presetId: String) {}
        override suspend fun deletePresetsByModel(modelProfileId: String) {}
        override suspend fun deletePresetsByProvider(providerId: String) {}
        override suspend fun clearDefaultPresets(taskType: String) {}
        override suspend fun markPresetDefault(presetId: String, updatedAt: Long) {}
        override suspend fun markModelsStale(providerId: String, activeModelIds: List<String>, now: Long) {}
        override suspend fun deprecateModels(providerId: String, modelIds: List<String>, now: Long) {}
        override suspend fun updateModelLastSeen(providerId: String, modelIds: List<String>, now: Long) {}
        override suspend fun deprecateStaleModels(cutoff: Long, now: Long) {}
        override fun observeActiveModels(): Flow<List<AiModelProfile>> = flowOf(emptyList())
    }

    private class FakeAccountAuthGateway : AccountAuthGateway {
        var session: AccountSession? = AccountSession(
            userId = "11111111-2222-3333-4444-555555555555",
            email = "user@example.com",
            emailVerified = true,
            providerIds = setOf("email"),
            expiresAtEpochMillis = System.currentTimeMillis() + 3600000,
        )

        override fun observeSession(): Flow<AccountSession?> = flowOf(session)
        override suspend fun currentSession(): AccountSession? = session
        override suspend fun currentAccessToken(): String = "fake_access_token_1234567890_abcdefg"
        override suspend fun signUpWithEmail(credentials: AccountEmailCredentials): AccountAuthResult =
            AccountAuthResult.SignedIn(session!!)
        override suspend fun signInWithEmail(credentials: AccountEmailCredentials): AccountSession = session!!
        override suspend fun signInOrLinkGoogle(credential: AccountGoogleIdCredential): AccountSession = session!!
        override suspend fun sendPasswordReset(email: String) {}
        override suspend fun reauthenticate() {}
        override suspend fun changePassword(currentPassword: String, newPassword: String) {}
        override suspend fun refreshSession() {}
        override suspend fun signOut(mode: AccountSignOutMode): AccountAuthResult.SignedOut {
            session = null
            return AccountAuthResult.SignedOut
        }
    }
}
