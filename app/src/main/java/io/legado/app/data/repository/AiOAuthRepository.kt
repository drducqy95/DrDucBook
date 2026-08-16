package io.legado.app.data.repository

import android.net.Uri
import android.os.Build
import com.google.gson.JsonObject
import io.legado.app.data.dao.AiRouterDao
import io.legado.app.data.entities.AiCredentialEntity
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiRouteProfileEntity
import io.legado.app.data.entities.AiRouteTargetEntity
import io.legado.app.domain.gateway.AiOAuthGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiSecretStore
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiCredentialKind
import io.legado.app.domain.model.AiCredentialStatus
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiOAuthAuthorization
import io.legado.app.domain.model.AiOAuthEvent
import io.legado.app.domain.model.AiOAuthProviderConfig
import io.legado.app.domain.model.AiOAuthProviderId
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiProviderRegistry
import io.legado.app.domain.model.AiRegistryAuthType
import io.legado.app.domain.model.AiRouteStrategy
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postForm
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OAuth account pool inspired by 9router's provider adapters. These CLI/IDE endpoints are isolated
 * from standard API-key providers because they may change without API compatibility guarantees.
 */
class AiOAuthRepository(
    private val dao: AiRouterDao,
    private val secretStore: AiSecretStore,
    private val profileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val clock: Clock,
) : AiOAuthGateway {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<AiOAuthEvent>(extraBufferCapacity = 16)
    override val events: Flow<AiOAuthEvent> = _events.asSharedFlow()
    private val refreshLocks = ConcurrentHashMap<String, Mutex>()

    @Volatile
    private var activeServer: OAuthLoopbackServer? = null

    override fun providers(): List<AiOAuthProviderConfig> {
        val implemented = OAUTH_PROVIDERS.associateBy(OAuthProvider::id)
        return AiProviderRegistry.entries.asSequence()
            .filter { AiRegistryAuthType.OAUTH in it.authModes }
            .map { registry ->
                val adapter = implemented[registry.id]
                AiOAuthProviderConfig(
                    id = registry.id,
                    name = adapter?.name ?: registry.name,
                    warning = when {
                        adapter != null -> buildString {
                            if (registry.hidden || registry.deprecated) {
                                append("Upstream đang ẩn/deprecated provider này. ")
                            }
                            append("Endpoint OAuth CLI/IDE có thể thay đổi hoặc bị provider giới hạn.")
                        }
                        else ->
                            "Registry có OAuth (${registry.oauth?.flow ?: "custom"}) nhưng ứng dụng chưa có adapter an toàn cho provider này; chưa cho phép đăng nhập để tránh tạo credential không chạy được."
                    },
                    flow = registry.oauth?.flow.orEmpty(),
                    available = adapter != null,
                )
            }
            .sortedWith(compareByDescending<AiOAuthProviderConfig> { it.available }.thenBy { it.name })
            .toList()
    }

    override suspend fun begin(providerId: String): AiOAuthAuthorization = withContext(Dispatchers.IO) {
        val config = providerConfig(providerId)
        if (config.flow == OAuthFlow.DEVICE_CODE) {
            return@withContext beginDeviceAuthorization(config)
        }
        activeServer?.close()
        val server = OAuthLoopbackServer.open(
            port = config.fixedPort,
            callbackPath = config.callbackPath,
            redirectHost = config.redirectHost,
        )
        activeServer = server
        val verifier = randomBase64Url(64)
        val challenge = sha256Base64Url(verifier)
        val state = randomBase64Url(32)
        val authorizationUrl = buildAuthorizationUrl(
            config = config,
            redirectUri = server.redirectUri,
            state = state,
            challenge = challenge,
        )
        scope.launch {
            runCatching {
                val callback = server.awaitCallback()
                if (config.id != AiOAuthProviderId.CLINE && config.id != AiOAuthProviderId.CLINEPASS) {
                    require(callback["state"] == state) { "OAuth state không hợp lệ" }
                }
                callback["error"]?.let { error ->
                    error("OAuth bị từ chối: ${callback["error_description"] ?: error}")
                }
                val rawCode = callback["code"] ?: error("Không nhận được authorization code")
                val code = rawCode.substringBefore('#')
                val tokens = when (config.id) {
                    AiOAuthProviderId.CLINE,
                    AiOAuthProviderId.CLINEPASS -> exchangeClineCode(config, code, server.redirectUri)
                    else -> exchangeAuthorizationCode(
                        config = config,
                        code = code,
                        redirectUri = server.redirectUri,
                        verifier = verifier,
                        state = state,
                    )
                }
                val account = resolveAccount(config, tokens)
                val saved = saveOAuthAccount(config, tokens, account)
                _events.emit(saved.toConnectedEvent(config.id, account.label))
            }.onFailure { error ->
                _events.emit(
                    AiOAuthEvent.Failed(
                        providerId = config.id,
                        message = error.message ?: "Đăng nhập OAuth thất bại",
                    )
                )
            }
            server.close()
            if (activeServer === server) activeServer = null
        }
        AiOAuthAuthorization(config.id, authorizationUrl)
    }

    private suspend fun beginDeviceAuthorization(
        config: OAuthProvider,
    ): AiOAuthAuthorization {
        val verifier = randomBase64Url(64)
        val challenge = sha256Base64Url(verifier)
        val deviceId = UUID.randomUUID().toString()
        val values = linkedMapOf("client_id" to config.clientId)
        config.scope.takeIf(String::isNotBlank)?.let { values["scope"] = it }
        if (config.id == AiOAuthProviderId.GROK_CLI) values["referrer"] = "grok-build"
        if (config.deviceUsePkce) {
            values["code_challenge"] = challenge
            values["code_challenge_method"] = "S256"
        }
        val response = okHttpClient.newCallStrResponse {
            url(config.deviceCodeUrl ?: error("Provider thiếu device-code endpoint"))
            postForm(values)
            addHeaders(oauthRequestHeaders(config, deviceId))
        }
        val body = response.body.orEmpty()
        if (!response.isSuccessful()) {
            error("OAuth device-code HTTP ${response.code()}: ${body.oauthErrorMessage()}")
        }
        val device = runCatching { GSON.fromJson(body, JsonObject::class.java) }
            .getOrNull()
            ?: error("Provider trả device-code không hợp lệ")
        val deviceCode = device.string("device_code")
            ?: error("Provider không trả device_code")
        val userCode = device.string("user_code")
        val authorizationUrl = device.string("verification_uri_complete")
            ?: device.string("verification_uri")
            ?: config.deviceVerificationUrl
            ?: error("Provider không trả trang xác nhận")
        val expiresInSeconds = device.long("expires_in")?.coerceIn(60L, 1_800L) ?: 300L
        val initialIntervalSeconds = device.long("interval")?.coerceIn(2L, 30L) ?: 5L
        scope.launch {
            runCatching {
                val tokens = awaitDeviceTokens(
                    config = config,
                    deviceCode = deviceCode,
                    verifier = verifier,
                    deviceId = deviceId,
                    expiresInSeconds = expiresInSeconds,
                    initialIntervalSeconds = initialIntervalSeconds,
                )
                val account = resolveAccount(config, tokens)
                val saved = saveOAuthAccount(config, tokens, account)
                _events.emit(saved.toConnectedEvent(config.id, account.label))
            }.onFailure { error ->
                _events.emit(
                    AiOAuthEvent.Failed(
                        providerId = config.id,
                        message = error.message ?: "Đăng nhập device-code thất bại",
                    )
                )
            }
        }
        return AiOAuthAuthorization(config.id, authorizationUrl, userCode)
    }

    private suspend fun awaitDeviceTokens(
        config: OAuthProvider,
        deviceCode: String,
        verifier: String,
        deviceId: String,
        expiresInSeconds: Long,
        initialIntervalSeconds: Long,
    ): JsonObject {
        val deadline = clock.millis() + expiresInSeconds * 1_000L
        var intervalSeconds = initialIntervalSeconds
        while (clock.millis() < deadline) {
            delay(intervalSeconds * 1_000L)
            val values = linkedMapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                "client_id" to config.clientId,
                "device_code" to deviceCode,
            )
            if (config.deviceUsePkce) values["code_verifier"] = verifier
            val response = okHttpClient.newCallStrResponse {
                url(config.tokenUrl)
                postForm(values)
                addHeaders(oauthRequestHeaders(config, deviceId))
            }
            val tokens = runCatching {
                GSON.fromJson(response.body.orEmpty(), JsonObject::class.java)
            }.getOrNull() ?: error("Provider trả token không hợp lệ")
            when (val oauthError = tokens.string("error")) {
                null -> {
                    require(tokens.string("access_token").isNullOrBlank().not()) {
                        "Provider không trả access token"
                    }
                    if (config.id == AiOAuthProviderId.KIMI) {
                        tokens.addProperty("_device_id", deviceId)
                    }
                    return finalizeDeviceTokens(config, tokens)
                }

                "authorization_pending" -> Unit
                "slow_down" -> intervalSeconds = (intervalSeconds + 5L).coerceAtMost(30L)
                "access_denied" -> error("Người dùng từ chối đăng nhập")
                "expired_token" -> error("Mã đăng nhập đã hết hạn")
                else -> error(tokens.string("error_description") ?: oauthError)
            }
        }
        error("Đăng nhập device-code quá thời gian")
    }

    private fun oauthRequestHeaders(
        config: OAuthProvider,
        deviceId: String?,
    ): Map<String, String> = buildMap {
        put("Accept", "application/json")
        if (config.id == AiOAuthProviderId.KIMI) {
            put("X-Msh-Platform", "9router")
            put("X-Msh-Version", AiProviderRegistry.UPSTREAM_VERSION)
            put("X-Msh-Device-Name", Build.DEVICE.ifBlank { "Android" })
            put("X-Msh-Device-Model", Build.MODEL.ifBlank { "Android" })
            deviceId?.takeIf(String::isNotBlank)?.let { put("X-Msh-Device-Id", it) }
        }
        if (config.id == AiOAuthProviderId.GROK_CLI) {
            put("User-Agent", "grok-pager/0.2.99 grok-shell/0.2.99 (android; arm64)")
        }
    }

    private suspend fun finalizeDeviceTokens(
        config: OAuthProvider,
        tokens: JsonObject,
    ): JsonObject = when (config.id) {
        AiOAuthProviderId.GITHUB -> exchangeGitHubCopilotToken(tokens)
        else -> tokens
    }

    private suspend fun exchangeGitHubCopilotToken(tokens: JsonObject): JsonObject {
        val githubToken = tokens.string("access_token")
            ?: error("GitHub không trả access token")
        val commonHeaders = mapOf(
            // GitHub's internal Copilot endpoints use the GitHub OAuth token, not the
            // short-lived Copilot token, and their clients send the legacy `token` scheme.
            "Authorization" to "token $githubToken",
            "Accept" to "application/json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "GitHubCopilotChat/0.26.7",
            "Editor-Version" to "vscode/1.85.0",
            "Editor-Plugin-Version" to "copilot-chat/0.26.7",
        )
        val copilotResponse = okHttpClient.newCallStrResponse {
            url("https://api.github.com/copilot_internal/v2/token")
            addHeaders(commonHeaders)
        }
        if (!copilotResponse.isSuccessful()) {
            error("Không lấy được GitHub Copilot token: HTTP ${copilotResponse.code()}")
        }
        val copilot = runCatching {
            GSON.fromJson(copilotResponse.body, JsonObject::class.java)
        }.getOrNull() ?: error("GitHub Copilot trả token không hợp lệ")
        val copilotToken = copilot.string("token") ?: error("GitHub Copilot không trả token")
        val userResponse = okHttpClient.newCallStrResponse {
            url("https://api.github.com/user")
            addHeaders(commonHeaders)
        }
        val user = if (userResponse.isSuccessful()) {
            runCatching { GSON.fromJson(userResponse.body, JsonObject::class.java) }.getOrNull()
        } else {
            null
        }
        return JsonObject().apply {
            addProperty("access_token", copilotToken)
            addProperty("refresh_token", githubToken)
            copilot.long("expires_at")?.let { expiresAtSeconds ->
                addProperty(
                    "expires_in",
                    (expiresAtSeconds - clock.millis() / 1_000L).coerceAtLeast(60L),
                )
            }
            tokens.string("scope")?.let { addProperty("scope", it) }
            user?.string("id")?.let { addProperty("_github_id", it) }
            user?.string("login")?.let { addProperty("_github_login", it) }
            user?.string("email")?.let { addProperty("_github_email", it) }
        }
    }

    override suspend fun resolveAccessToken(credentialId: String): String {
        var credential = dao.getCredential(credentialId)
            ?: error("OAuth credential không còn tồn tại")
        require(credential.status != AiCredentialStatus.RELOGIN_REQUIRED) {
            "OAuth credential cần đăng nhập lại"
        }
        require(AiCredentialStatus.isRouterEligible(credential.status)) {
            "OAuth credential chưa vượt qua inference probe"
        }
        val provider = credential.oauthProvider?.let(::providerConfig)
        val refreshLeadMs = provider?.refreshLeadMs ?: DEFAULT_REFRESH_LEAD_MS
        val now = clock.millis()
        val shouldRefreshForExpiry =
            credential.expiresAt != null && credential.expiresAt <= now + refreshLeadMs
        val shouldRefreshForAge = provider?.let { config ->
            credential.shouldRefreshForAge(config, now)
        } == true
        if (shouldRefreshForExpiry || shouldRefreshForAge) {
            refresh(credentialId).getOrThrow()
            credential = dao.getCredential(credentialId)
                ?: error("OAuth credential không còn tồn tại")
        }
        return secretStore.get(credential.secretRef)
            ?.takeIf(String::isNotBlank)
            ?: error("Không đọc được OAuth access token")
    }

    override suspend fun refresh(credentialId: String): Result<Unit> {
        return refreshLocks.getOrPut(credentialId) { Mutex() }.withLock {
            runCatching {
                val credential = dao.getCredential(credentialId)
                    ?: error("OAuth credential không còn tồn tại")
                val config = providerConfig(
                    credential.oauthProvider ?: error("Credential không phải OAuth")
                )
                val refreshRef = credential.refreshTokenRef
                    ?: markReloginAndFail(credentialId, "OAuth credential không có refresh token")
                val refreshToken = secretStore.get(refreshRef)
                    ?: markReloginAndFail(credentialId, "Không đọc được OAuth refresh token")
                dao.updateCredentialStatus(credentialId, AiCredentialStatus.REFRESHING, clock.millis())
                val deviceId = credential.providerDataJson
                    ?.let { raw -> runCatching { GSON.fromJson(raw, JsonObject::class.java) }.getOrNull() }
                    ?.string("deviceId")
                val tokens = if (config.id == AiOAuthProviderId.GITHUB) {
                    exchangeGitHubCopilotToken(
                        JsonObject().apply { addProperty("access_token", refreshToken) }
                    )
                } else {
                    exchangeRefreshToken(config, refreshToken, deviceId)
                }
                val accessToken = tokens.string("access_token")
                    ?: error("Provider không trả access token")
                val newRefreshToken = tokens.string("refresh_token") ?: refreshToken
                val idToken = tokens.string("id_token")
                val now = clock.millis()
                val accessRef = secretStore.put(accessToken, credential.secretRef)
                val updatedRefreshRef = secretStore.put(newRefreshToken, refreshRef)
                val updatedIdRef = when {
                    idToken != null -> secretStore.put(idToken, credential.idTokenRef)
                    else -> credential.idTokenRef
                }
                val updatedProviderData = credential.providerDataJson.toJsonObjectOrEmpty()
                tokens.string("resource_url")?.let {
                    updatedProviderData.addProperty("resourceUrl", it)
                }
                if (config.id == AiOAuthProviderId.CODEX) {
                    CodexOAuthMetadata.extractAccountId(accessToken, idToken)?.let {
                        updatedProviderData.addProperty(CodexOAuthMetadata.CHATGPT_ACCOUNT_ID, it)
                    }
                }
                updatedProviderData.stampRefresh(config, now)
                dao.updateOAuthTokens(
                    credentialId = credentialId,
                    accessTokenRef = accessRef,
                    refreshTokenRef = updatedRefreshRef,
                    idTokenRef = updatedIdRef,
                    expiresAt = tokens.long("expires_in")?.let { now + it * 1_000L },
                    scopes = tokens.string("scope") ?: credential.scopes,
                    status = AiCredentialStatus.ACTIVE,
                    providerDataJson = GSON.toJson(updatedProviderData),
                    now = now,
                )
            }.onFailure { error ->
                val permanent = error.message.orEmpty().contains("invalid_grant", ignoreCase = true) ||
                    error.message.orEmpty().contains("refresh token", ignoreCase = true)
                dao.updateCredentialStatus(
                    credentialId,
                    if (permanent) AiCredentialStatus.RELOGIN_REQUIRED else AiCredentialStatus.ACTIVE,
                    clock.millis(),
                )
            }
        }
    }

    override suspend fun syncModels(credentialId: String): Result<List<AiAvailableModel>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val credential = dao.getCredential(credentialId)
                    ?: error("Tài khoản OAuth không còn tồn tại")
                val config = providerConfig(
                    credential.oauthProvider ?: error("Credential không phải OAuth")
                )
                val accessToken = resolveAccessToken(credentialId)
                val provider = profileGateway.getProvider(config.profileId)
                    ?: error("Provider OAuth chưa được cấu hình")
                val accountData = credential.providerDataJson.toJsonObjectOrEmpty()
                    .entrySet()
                    .mapNotNull { (key, value) ->
                        value.takeIf { it.isJsonPrimitive }
                            ?.asString
                            ?.let { key to it }
                    }
                    .toMap()
                val configuredModels = config.models.mapIndexed { index, model ->
                    profileGateway.saveModel(
                        AiModelDraft(
                            providerId = provider.id,
                            modelName = model.name,
                            modelId = model.id,
                            contextWindow = model.contextWindow,
                            maxOutputTokens = model.maxOutputTokens,
                            sortNumber = index,
                        )
                    )
                }
                val availableModels = configuredModels.mapNotNull { model ->
                    runCatching {
                        runInferenceProbe(
                            config = config,
                            providerProfileId = provider.id,
                            model = model,
                            accessToken = accessToken,
                            accountData = accountData,
                        )
                        AiAvailableModel(
                            id = model.modelId,
                            name = model.displayName,
                            contextWindow = model.contextWindow,
                            maxOutputTokens = model.maxOutputTokens,
                        )
                    }.getOrNull()
                }
                require(availableModels.isNotEmpty()) {
                    "Không có model nào vượt qua kiểm tra với tài khoản này"
                }
                dao.updateCredentialStatus(
                    credentialId,
                    AiCredentialStatus.ACTIVE,
                    clock.millis(),
                )
                availableModels
            }
        }

    private suspend fun exchangeAuthorizationCode(
        config: OAuthProvider,
        code: String,
        redirectUri: String,
        verifier: String,
        state: String,
    ): JsonObject {
        val values = linkedMapOf(
            "grant_type" to "authorization_code",
            "client_id" to config.clientId,
            "code" to code,
            "redirect_uri" to redirectUri,
        )
        if (config.usePkce) values["code_verifier"] = verifier
        config.clientSecret?.let { values["client_secret"] = it }
        if (config.id == AiOAuthProviderId.CLAUDE) values["state"] = state
        return tokenRequest(config, values)
    }

    /** Cline's callback normally carries a base64 JSON envelope; the HTTP exchange is a fallback
     * for older extensions that return an opaque authorization code instead. */
    private suspend fun exchangeClineCode(
        config: OAuthProvider,
        code: String,
        redirectUri: String,
    ): JsonObject {
        val decoded = runCatching {
            val normalized = code
                .replace('-', '+')
                .replace('_', '/')
                .let { value -> value + "=".repeat((4 - value.length % 4) % 4) }
            String(Base64.getDecoder().decode(normalized), Charsets.UTF_8)
        }.getOrNull()
        val envelope = decoded?.let { value ->
            val start = value.indexOf('{')
            val end = value.lastIndexOf('}')
            if (start >= 0 && end > start) {
                runCatching {
                    GSON.fromJson(value.substring(start, end + 1), JsonObject::class.java)
                }.getOrNull()
            } else {
                null
            }
        }
        if (envelope != null && envelope.string("accessToken").isNullOrBlank().not()) {
            return normalizeClineTokens(envelope)
        }
        val response = okHttpClient.newCallStrResponse {
            url(config.tokenUrl)
            postJson(
                GSON.toJson(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to code,
                        "client_type" to "extension",
                        "redirect_uri" to redirectUri,
                    )
                )
            )
            addHeaders(mapOf("Accept" to "application/json"))
        }
        val body = response.body.orEmpty()
        if (!response.isSuccessful()) {
            error("Cline OAuth token HTTP ${response.code()}: ${body.oauthErrorMessage()}")
        }
        val root = GSON.fromJson(body, JsonObject::class.java)
            ?: error("Cline OAuth token response không hợp lệ")
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
        return normalizeClineTokens(data)
    }

    private fun normalizeClineTokens(raw: JsonObject): JsonObject = JsonObject().apply {
        val accessToken = raw.string("access_token")
            ?: raw.string("accessToken")
            ?: error("Cline không trả access token")
        addProperty("access_token", accessToken)
        val refreshToken = raw.string("refresh_token")
            ?: raw.string("refreshToken")
            ?: error("Cline không trả refresh token")
        addProperty("refresh_token", refreshToken)
        raw.string("email")?.let { addProperty("_email", it) }
        raw.string("firstName")?.let { addProperty("_first_name", it) }
        raw.string("lastName")?.let { addProperty("_last_name", it) }
        raw.string("scope")?.let { addProperty("scope", it) }
        val expiresIn = raw.long("expires_in") ?: raw.long("expiresIn")
        val expiresAt = raw.string("expires_at") ?: raw.string("expiresAt")
        when {
            expiresIn != null -> addProperty("expires_in", expiresIn)
            expiresAt != null -> {
                val expiryMillis = expiresAt.toLongOrNull()?.let { value ->
                    if (value < 100_000_000_000L) value * 1_000L else value
                }
                expiryMillis?.let {
                    addProperty("expires_in", ((it - clock.millis()) / 1_000L).coerceAtLeast(60L))
                }
            }
        }
    }

    private suspend fun exchangeRefreshToken(
        config: OAuthProvider,
        refreshToken: String,
        deviceId: String? = null,
    ): JsonObject {
        val values = linkedMapOf(
            "grant_type" to "refresh_token",
            "client_id" to config.clientId,
            "refresh_token" to refreshToken,
        )
        config.clientSecret?.let { values["client_secret"] = it }
        config.refreshScope?.let { values["scope"] = it }
        return tokenRequest(
            config = config,
            values = values,
            deviceId = deviceId,
            useJson = config.jsonRefreshRequest || config.jsonTokenRequest,
        )
    }

    private suspend fun tokenRequest(
        config: OAuthProvider,
        values: Map<String, String>,
        deviceId: String? = null,
        useJson: Boolean = config.jsonTokenRequest,
    ): JsonObject {
        val response = okHttpClient.newCallStrResponse {
            url(config.tokenUrl)
            if (useJson) {
                postJson(GSON.toJson(values))
            } else {
                postForm(values)
            }
            addHeaders(oauthRequestHeaders(config, deviceId))
        }
        val body = response.body.orEmpty()
        if (!response.isSuccessful()) {
            val detail = body.oauthErrorMessage()
            throw IllegalStateException("OAuth token HTTP ${response.code()}: $detail")
        }
        return runCatching { GSON.fromJson(body, JsonObject::class.java) }
            .getOrNull()
            ?: error("Phản hồi OAuth token không hợp lệ")
    }

    private suspend fun resolveAccount(config: OAuthProvider, tokens: JsonObject): OAuthAccount {
        val accessToken = tokens.string("access_token") ?: error("Provider không trả access token")
        return when (config.id) {
            AiOAuthProviderId.CODEX -> {
                val claims = tokens.string("id_token")?.decodeJwtPayload()
                val idToken = tokens.string("id_token")
                val chatgptAccountId = CodexOAuthMetadata.extractAccountId(
                    accessToken = accessToken,
                    idToken = idToken,
                )
                val subject = CodexOAuthMetadata.extractSubject(
                    accessToken = accessToken,
                    idToken = idToken,
                ) ?: claims?.findString("sub")
                val email = CodexOAuthMetadata.extractEmail(
                    accessToken = accessToken,
                    idToken = idToken,
                ) ?: claims?.findString("email")
                val accountId = chatgptAccountId
                    ?: subject
                    ?: UUID.randomUUID().toString()
                OAuthAccount(
                    accountId,
                    email ?: "ChatGPT / Codex",
                    buildMap {
                        chatgptAccountId?.let {
                            put(CodexOAuthMetadata.CHATGPT_ACCOUNT_ID, it)
                        }
                        subject?.let {
                            put("subject", it)
                        }
                    },
                )
            }

            AiOAuthProviderId.ANTIGRAVITY -> resolveGoogleAccount(accessToken)
            AiOAuthProviderId.KIMI -> {
                val deviceId = tokens.string("_device_id")
                val claims = accessToken.decodeJwtPayload()
                val email = claims?.findString("email")
                OAuthAccount(
                    id = claims?.findString("sub")
                        ?: email
                        ?: tokens.string("refresh_token")?.take(24)
                        ?: UUID.randomUUID().toString(),
                    label = email ?: config.name,
                    data = buildMap {
                        deviceId?.let { put("deviceId", it) }
                        put("authMethod", "device_code")
                    },
                )
            }
            AiOAuthProviderId.QWEN -> {
                val claims = accessToken.decodeJwtPayload()
                val email = claims?.findString("email")
                OAuthAccount(
                    id = claims?.findString("sub")
                        ?: email
                        ?: UUID.randomUUID().toString(),
                    label = email ?: config.name,
                    data = buildMap {
                        tokens.string("resource_url")?.let { put("resourceUrl", it) }
                        put("authMethod", "device_code_pkce")
                    },
                )
            }
            AiOAuthProviderId.GITHUB -> {
                val login = tokens.string("_github_login")
                val email = tokens.string("_github_email")
                OAuthAccount(
                    id = tokens.string("_github_id")
                        ?: login
                        ?: UUID.randomUUID().toString(),
                    label = email ?: login ?: config.name,
                    data = buildMap {
                        login?.let { put("login", it) }
                        email?.let { put("email", it) }
                        put("authMethod", "device_code")
                    },
                )
            }
            AiOAuthProviderId.CLINE,
            AiOAuthProviderId.CLINEPASS -> {
                val email = tokens.string("_email")
                OAuthAccount(
                    id = email ?: tokens.string("refresh_token")?.take(24) ?: UUID.randomUUID().toString(),
                    label = email ?: config.name,
                    data = buildMap {
                        tokens.string("_first_name")?.let { put("firstName", it) }
                        tokens.string("_last_name")?.let { put("lastName", it) }
                        put("authMethod", "extension_callback")
                    },
                )
            }
            else -> {
                val claims = tokens.string("id_token")?.decodeJwtPayload()
                    ?: accessToken.decodeJwtPayload()
                val email = claims?.findString("email")
                OAuthAccount(
                    id = claims?.findString("sub")
                        ?: email
                        ?: tokens.string("refresh_token")?.take(24)
                        ?: UUID.randomUUID().toString(),
                    label = email ?: config.name,
                    data = emptyMap(),
                )
            }
        }
    }

    private suspend fun resolveGoogleAccount(accessToken: String): OAuthAccount {
        val userResponse = okHttpClient.newCallStrResponse {
            url("https://www.googleapis.com/oauth2/v1/userinfo?alt=json")
            addHeaders(
                mapOf(
                    "Authorization" to "Bearer $accessToken",
                    "x-request-source" to "local",
                )
            )
        }
        val user = if (userResponse.isSuccessful()) {
            runCatching { GSON.fromJson(userResponse.body, JsonObject::class.java) }.getOrNull()
        } else {
            null
        }
        val codeAssistHeaders = antigravityCodeAssistHeaders(accessToken)
        val projectResponse = okHttpClient.newCallStrResponse {
            url("$ANTIGRAVITY_PRODUCTION_BASE_URL/v1internal:loadCodeAssist")
            postJson(GSON.toJson(mapOf("metadata" to GOOGLE_CLIENT_METADATA)))
            addHeaders(codeAssistHeaders)
        }
        // OAuth has already succeeded. Discovery/onboarding is auxiliary and must not prevent
        // storing the account when Code Assist is temporarily unavailable.
        val finalProject = runCatching {
        if (!projectResponse.isSuccessful()) {
            val detail = projectResponse.body.orEmpty().trim().take(300)
            error(
                "Google Code Assist load project HTTP ${projectResponse.code()}" +
                    detail.takeIf(String::isNotEmpty)?.let { ": $it" }.orEmpty()
            )
        }
        val projectRoot = runCatching {
            GSON.fromJson(projectResponse.body, JsonObject::class.java)
        }.getOrNull() ?: error("Google Code Assist trả dữ liệu project không hợp lệ")
        resolveAntigravityProject(
            loadPayload = projectRoot,
            maxOnboardingAttempts = 2,
            onboard = { tierId ->
                val onboardResponse = okHttpClient.newCallStrResponse {
                    url("$ANTIGRAVITY_PRODUCTION_BASE_URL/v1internal:onboardUser")
                    postJson(
                        GSON.toJson(
                            mapOf(
                                "tierId" to tierId,
                                "metadata" to GOOGLE_CLIENT_METADATA,
                            )
                        )
                    )
                    addHeaders(codeAssistHeaders)
                }
                if (!onboardResponse.isSuccessful()) {
                    val detail = onboardResponse.body.orEmpty().trim().take(300)
                    error(
                        "Google Code Assist onboarding HTTP ${onboardResponse.code()}" +
                            detail.takeIf(String::isNotEmpty)?.let { ": $it" }.orEmpty()
                    )
                }
                runCatching {
                    GSON.fromJson(onboardResponse.body, JsonObject::class.java)
                }.getOrNull()
                    ?: error("Google Code Assist onboarding trả dữ liệu không hợp lệ")
            },
            waitBeforeRetry = { delay(1_000L) },
        )
        }.getOrNull()
        val email = user?.string("email")
        return OAuthAccount(
            id = user?.string("id") ?: email ?: UUID.randomUUID().toString(),
            label = email ?: "Google Antigravity",
            data = buildMap {
                finalProject?.takeIf(String::isNotBlank)?.let { put("projectId", it) }
            },
        )
    }

    private suspend fun saveOAuthAccount(
        config: OAuthProvider,
        tokens: JsonObject,
        account: OAuthAccount,
    ): OAuthSaveResult {
        val accessToken = tokens.string("access_token") ?: error("Provider không trả access token")
        val refreshToken = tokens.string("refresh_token")
            ?: error("Provider không trả refresh token; hãy thu hồi quyền và đăng nhập lại")
        val idToken = tokens.string("id_token")
        val provider = profileGateway.saveProvider(
            AiProviderDraft(
                providerId = config.profileId,
                providerName = config.name,
                protocol = config.protocol,
                baseUrl = config.baseUrl,
                modelsUrl = null,
                apiKey = "",
                authType = config.authType,
                headers = config.headers,
                chatPath = config.chatPath,
                responsesPath = config.responsesPath,
                messagesPath = config.messagesPath,
                customHeaders = config.customHeaders,
            )
        )
        val savedModels = config.models.mapIndexed { index, model ->
            profileGateway.saveModel(
                AiModelDraft(
                    providerId = provider.id,
                    modelName = model.name,
                    modelId = model.id,
                    contextWindow = model.contextWindow,
                    maxOutputTokens = model.maxOutputTokens,
                    sortNumber = index,
                )
            )
        }
        if (savedModels.isEmpty()) {
            error("OAuth provider không có model để tạo route")
        }
        val credentialId = stableCredentialId(config.id, account.id)
        val existing = dao.getCredential(credentialId)
        val accessRef = secretStore.put(accessToken, existing?.secretRef)
        val refreshRef = secretStore.put(refreshToken, existing?.refreshTokenRef)
        val idRef = idToken?.let { secretStore.put(it, existing?.idTokenRef) }
            ?: existing?.idTokenRef
        val now = clock.millis()
        dao.upsertCredential(
            AiCredentialEntity(
                id = credentialId,
                providerId = provider.id,
                label = "${config.name} · ${account.label}",
                kind = AiCredentialKind.OAUTH_ACCESS_TOKEN,
                secretRef = accessRef,
                enabled = true,
                sortNumber = existing?.sortNumber ?: 0,
                cooldownUntil = 0,
                consecutiveFailures = 0,
                lastFailureKind = null,
                oauthProvider = config.id,
                refreshTokenRef = refreshRef,
                idTokenRef = idRef,
                accountId = account.id,
                accountLabel = account.label,
                expiresAt = tokens.long("expires_in")?.let { now + it * 1_000L },
                scopes = tokens.string("scope"),
                status = AiCredentialStatus.VERIFYING,
                providerDataJson = GSON.toJson(
                    JsonObject().apply {
                        account.data.forEach(::addProperty)
                        stampRefresh(config, now)
                    }
                ),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
        val usableModel = verifyOAuthCredentialModels(
            models = savedModels,
            updateStatus = { status ->
                dao.updateCredentialStatus(credentialId, status, clock.millis())
            },
            probe = { model ->
                runInferenceProbe(
                    config = config,
                    providerProfileId = provider.id,
                    model = model,
                    accessToken = accessToken,
                    accountData = account.data,
                )
            },
        )
        val routeModels = savedModels.preferModel(usableModel.id)
        val chatBinding = bindOAuthRouteTargets(
            taskType = AiTaskType.CHAT,
            defaultRouteId = DEFAULT_OAUTH_CHAT_ROUTE_ID,
            defaultRouteName = "Default Chat",
            strategy = AiRouteStrategy.PRIORITY,
            maxAttempts = 3,
            stickySession = true,
            models = routeModels,
            defaultTargetMaxConcurrency = 0,
            preferProvidedModelOrder = true,
            now = now,
        ).firstOrNull() ?: error("OAuth route target was not created")
        val translationBinding = bindOAuthRouteTargets(
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            defaultRouteId = DEFAULT_OAUTH_TRANSLATION_ROUTE_ID,
            defaultRouteName = "Default Translation",
            strategy = AiRouteStrategy.ROUND_ROBIN,
            maxAttempts = 3,
            stickySession = true,
            models = routeModels,
            defaultTargetMaxConcurrency = 2,
            preferProvidedModelOrder = true,
            now = now,
        ).firstOrNull() ?: error("OAuth translation route target was not created")
        ensureDefaultOAuthTaskPresets(
            modelProfileId = usableModel.id,
            chatRouteId = chatBinding.routeId,
            translationRouteId = translationBinding.routeId,
        )
        return OAuthSaveResult(
            providerProfileId = provider.id,
            modelProfileId = usableModel.id,
            credentialId = credentialId,
            routeProfileId = chatBinding.routeId,
            targetId = chatBinding.targetId,
        )
    }

    private fun List<AiModelProfile>.preferModel(modelProfileId: String): List<AiModelProfile> {
        val selected = firstOrNull { it.id == modelProfileId } ?: return this
        return listOf(selected) + filterNot { it.id == modelProfileId }
    }

    private suspend fun bindOAuthRouteTargets(
        taskType: String,
        defaultRouteId: String,
        defaultRouteName: String,
        strategy: String,
        maxAttempts: Int,
        stickySession: Boolean,
        models: List<AiModelProfile>,
        defaultTargetMaxConcurrency: Int,
        preferProvidedModelOrder: Boolean = false,
        now: Long,
    ): List<OAuthRouteBinding> {
        var route = dao.getActiveRoute(taskType)
            ?: AiRouteProfileEntity(
                id = defaultRouteId,
                name = defaultRouteName,
                taskType = taskType,
                strategy = strategy,
                maxAttempts = maxAttempts,
                stickySession = stickySession,
                enabled = true,
                isDefault = true,
                sortNumber = 0,
                createdAt = now,
                updatedAt = now,
            ).also { dao.upsertRoute(it) }
        if (route.maxAttempts < maxAttempts) {
            route = route.copy(maxAttempts = maxAttempts, updatedAt = now)
            dao.upsertRoute(route)
        }
        val routeTargets = dao.getEnabledTargets(route.id).toMutableList()
        val bindings = models.mapIndexed { index, model ->
            val existingPoolTarget = routeTargets.firstOrNull { target ->
                target.modelProfileId == model.id && target.credentialId.isNullOrBlank()
            }
            val legacyBoundTargets = routeTargets.filter { target ->
                target.modelProfileId == model.id && !target.credentialId.isNullOrBlank()
            }
            val targetId = existingPoolTarget?.id
                ?: legacyBoundTargets.firstOrNull()?.id
                ?: stableOAuthTargetId(route.id, model.id)
            val existingTarget = existingPoolTarget
                ?: legacyBoundTargets.firstOrNull()
                ?: dao.getTarget(targetId)
            val savedTarget = AiRouteTargetEntity(
                id = targetId,
                routeProfileId = route.id,
                modelProfileId = model.id,
                credentialId = null,
                priority = if (preferProvidedModelOrder) index else (existingTarget?.priority ?: index),
                weight = existingTarget?.weight ?: 1,
                maxConcurrency = existingTarget?.maxConcurrency ?: defaultTargetMaxConcurrency,
                enabled = existingTarget?.enabled ?: true,
                sortNumber = if (preferProvidedModelOrder) index else (existingTarget?.sortNumber ?: index),
                cooldownUntil = 0,
                consecutiveFailures = 0,
                lastFailureKind = null,
                lastUsedAt = existingTarget?.lastUsedAt,
                lastSuccessAt = existingTarget?.lastSuccessAt,
                lastFailureAt = null,
                createdAt = existingTarget?.createdAt ?: now,
                updatedAt = now,
            )
            dao.upsertTarget(savedTarget)
            legacyBoundTargets
                .filterNot { it.id == savedTarget.id }
                .forEach { staleTarget ->
                    dao.deleteTarget(staleTarget.id)
                    routeTargets.removeAll { it.id == staleTarget.id }
                }
            routeTargets.removeAll { it.id == savedTarget.id }
            routeTargets += savedTarget
            OAuthRouteBinding(route.id, targetId)
        }
        val enabledTargetCount = routeTargets.size
        val desiredMaxAttempts = maxOf(route.maxAttempts, maxAttempts, enabledTargetCount)
            .coerceAtMost(MAX_OAUTH_ROUTE_ATTEMPTS)
        if (route.maxAttempts < desiredMaxAttempts) {
            dao.upsertRoute(route.copy(maxAttempts = desiredMaxAttempts, updatedAt = now))
        }
        return bindings
    }

    private suspend fun ensureDefaultOAuthTaskPresets(
        modelProfileId: String,
        chatRouteId: String,
        translationRouteId: String,
    ) {
        ensureDefaultOAuthTaskPreset(
            taskType = AiTaskType.CHAT,
            name = "Default Chat",
            modelProfileId = modelProfileId,
            promptTemplate = "You are a helpful AI assistant.",
            routeProfileId = chatRouteId,
        )
        ensureDefaultOAuthTaskPreset(
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            name = "Default Translation",
            modelProfileId = modelProfileId,
            promptTemplate = TranslationConstants.DEFAULT_PROMPT,
            routeProfileId = translationRouteId,
        )
    }

    private suspend fun ensureDefaultOAuthTaskPreset(
        taskType: String,
        name: String,
        modelProfileId: String,
        promptTemplate: String,
        routeProfileId: String,
    ) {
        val existing = profileGateway.getTaskPreset(taskType)
        if (!shouldBindOAuthPresetToDefaultRoute(
                existing?.runtimeOptions?.routeProfileId,
                routeProfileId,
            )
        ) {
            // A non-default route is an explicit user choice. OAuth login must not replace it.
            return
        }
        profileGateway.saveTaskPreset(
            AiTaskPresetDraft(
                presetId = existing?.id,
                taskType = existing?.taskType ?: taskType,
                name = existing?.name?.ifBlank { name } ?: name,
                description = existing?.description.orEmpty(),
                modelProfileId = modelProfileId,
                promptTemplate = existing?.promptTemplate?.ifBlank { promptTemplate } ?: promptTemplate,
                params = existing?.params ?: AiGenerationParams(),
                runtimeOptions = (existing?.runtimeOptions ?: AiTaskRuntimeOptions())
                    .copy(routeProfileId = routeProfileId),
                makeDefault = true,
            )
        )
    }

    private suspend fun runInferenceProbe(
        config: OAuthProvider,
        providerProfileId: String,
        model: AiModelProfile,
        accessToken: String,
        accountData: Map<String, String>,
    ) {
        val runtimeMetadata = accountData.filterKeys { !it.isSensitiveProviderDataKey() }
        val runtimeBaseUrl = runtimeMetadata["resourceUrl"]
            ?.trimEnd('/')
            ?.takeIf(String::isNotBlank)
            ?.let { "$it/v1" }
            ?: config.baseUrl
        val request = AiGenerateRequest(
            model = AiModelConfig(
                id = model.id,
                provider = AiProviderConfig(
                    id = providerProfileId,
                    name = config.name,
                    protocol = config.protocol,
                    baseUrl = runtimeBaseUrl,
                    apiKey = accessToken,
                    authType = config.authType ?: AiProviderAuthType.BEARER,
                    modelsUrl = null,
                    headers = config.headers.orEmpty(),
                    chatPath = config.chatPath ?: "/chat/completions",
                    responsesPath = config.responsesPath ?: "/responses",
                    messagesPath = config.messagesPath ?: "/v1/messages",
                    customHeaders = config.customHeaders.orEmpty(),
                    runtimeMetadata = runtimeMetadata,
                ),
                displayName = model.displayName,
                modelId = model.modelId,
                contextWindow = model.contextWindow,
                maxOutputTokens = model.maxOutputTokens,
            ),
            messages = listOf(
                AiMessage(
                    role = AiMessageRole.USER,
                    content = "Reply with OK.",
                )
            ),
            params = AiGenerationParams(
                temperature = 0f,
                // Subscription models can spend their first tokens on reasoning. A 16-token
                // probe incorrectly rejected valid OAuth accounts before routes were created.
                maxOutputTokens = 512,
            ),
        )
        val response = aiTextGateway.generate(request).getOrElse { error ->
            throw IllegalStateException(
                "OAuth inference probe failed: ${error.message ?: "unknown error"}",
                error,
            )
        }
        require(response.text.isNotBlank()) {
            "OAuth inference probe returned an empty response"
        }
    }

    private fun String.isSensitiveProviderDataKey(): Boolean {
        val normalized = lowercase()
        return listOf(
            "token",
            "secret",
            "apikey",
            "api_key",
            "authorization",
            "password",
            "credential",
        ).any(normalized::contains)
    }

    private fun AiCredentialEntity.shouldRefreshForAge(
        config: OAuthProvider,
        now: Long,
    ): Boolean {
        val maxAgeMs = config.maxRefreshAgeMs?.takeIf { it > 0 } ?: return false
        if (refreshTokenRef == null) return false
        val lastRefreshAt = providerDataJson
            .toJsonObjectOrEmpty()
            .long(CodexOAuthMetadata.LAST_REFRESH_AT)
            ?: return true
        return now - lastRefreshAt >= maxAgeMs
    }

    private fun String?.toJsonObjectOrEmpty(): JsonObject {
        if (isNullOrBlank()) return JsonObject()
        return runCatching { GSON.fromJson(this, JsonObject::class.java) }
            .getOrNull()
            ?: JsonObject()
    }

    private fun JsonObject.stampRefresh(config: OAuthProvider, now: Long) {
        if (config.maxRefreshAgeMs != null) {
            addProperty(CodexOAuthMetadata.LAST_REFRESH_AT, now)
        }
    }

    private suspend fun markReloginAndFail(credentialId: String, message: String): Nothing {
        dao.updateCredentialStatus(credentialId, AiCredentialStatus.RELOGIN_REQUIRED, clock.millis())
        error(message)
    }

    private fun providerConfig(id: String): OAuthProvider = OAUTH_PROVIDERS
        .firstOrNull { it.id == id }
        ?: error("OAuth provider không được hỗ trợ: $id")

    private companion object {
        const val DEFAULT_REFRESH_LEAD_MS = 5 * 60 * 1_000L
        const val DEFAULT_OAUTH_CHAT_ROUTE_ID = "route_default_chat"
        const val DEFAULT_OAUTH_TRANSLATION_ROUTE_ID = "route_default_translation"
        const val MAX_OAUTH_ROUTE_ATTEMPTS = 20

        val GOOGLE_CLIENT_METADATA: Map<String, Int>
            get() = antigravityClientMetadata(
                isArm64 = Build.SUPPORTED_ABIS.any { abi -> abi.equals("arm64-v8a", true) }
            )

        val OAUTH_PROVIDERS = listOf(
            OAuthProvider(
                id = AiOAuthProviderId.CODEX,
                name = "ChatGPT / Codex",
                profileId = "oauth_codex",
                protocol = AiProtocol.CODEX_SUBSCRIPTION,
                baseUrl = "https://chatgpt.com/backend-api/codex",
                authorizeUrl = "https://auth.openai.com/oauth/authorize",
                tokenUrl = "https://auth.openai.com/oauth/token",
                clientId = "app_EMoamEEZ73f0CkXaXp7hrann",
                scope = CodexOAuthMetadata.OAUTH_SCOPE,
                refreshScope = CodexOAuthMetadata.OAUTH_SCOPE,
                callbackPath = "/auth/callback",
                fixedPort = 1455,
                jsonRefreshRequest = CodexOAuthMetadata.REFRESH_USES_JSON,
                refreshLeadMs = 5 * 24 * 60 * 60 * 1_000L,
                maxRefreshAgeMs = 8 * 24 * 60 * 60 * 1_000L,
                extraAuthorizationParams = mapOf(
                    "id_token_add_organizations" to "true",
                    "codex_cli_simplified_flow" to "true",
                    "originator" to "codex_cli_rs",
                ),
                models = listOf(
                    OAuthModel("gpt-5.6-sol", "GPT 5.6 Sol", 400_000, 128_000),
                    OAuthModel("gpt-5.6-sol-review", "GPT 5.6 Sol Review", 400_000, 128_000),
                    OAuthModel("gpt-5.6-terra", "GPT 5.6 Terra", 400_000, 128_000),
                    OAuthModel("gpt-5.6-terra-review", "GPT 5.6 Terra Review", 400_000, 128_000),
                    OAuthModel("gpt-5.6-luna", "GPT 5.6 Luna", 400_000, 128_000),
                    OAuthModel("gpt-5.6-luna-review", "GPT 5.6 Luna Review", 400_000, 128_000),
                    OAuthModel("gpt-5.5", "GPT 5.5", 400_000, 128_000),
                    OAuthModel("gpt-5.5-review", "GPT 5.5 Review", 400_000, 128_000),
                    OAuthModel("gpt-5.4", "GPT 5.4", 400_000, 128_000),
                    OAuthModel("gpt-5.4-review", "GPT 5.4 Review", 400_000, 128_000),
                    OAuthModel("gpt-5.4-mini", "GPT 5.4 Mini", 400_000, 128_000),
                    OAuthModel("gpt-5.4-mini-review", "GPT 5.4 Mini Review", 400_000, 128_000),
                    OAuthModel("gpt-5.3-codex-spark", "GPT 5.3 Codex Spark", 400_000, 128_000),
                    OAuthModel(
                        "gpt-5.3-codex-spark-review",
                        "GPT 5.3 Codex Spark Review",
                        400_000,
                        128_000,
                    ),
                ),
                responsesPath = "/responses",
            ),
            OAuthProvider(
                id = AiOAuthProviderId.CLAUDE,
                name = "Claude Code",
                profileId = "oauth_claude",
                protocol = AiProtocol.CLAUDE_SUBSCRIPTION,
                baseUrl = "https://api.anthropic.com",
                authorizeUrl = "https://claude.ai/oauth/authorize",
                tokenUrl = "https://api.anthropic.com/v1/oauth/token",
                clientId = "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
                scope = "org:create_api_key user:profile user:inference",
                callbackPath = "/callback",
                jsonTokenRequest = true,
                refreshLeadMs = 4 * 60 * 60 * 1_000L,
                extraAuthorizationParams = mapOf("code" to "true"),
                models = listOf(
                    OAuthModel("claude-sonnet-5", "Claude Sonnet 5", 200_000, 64_000),
                    OAuthModel("claude-opus-4-8", "Claude Opus 4.8", 200_000, 64_000),
                ),
            ),
            OAuthProvider(
                id = AiOAuthProviderId.ANTIGRAVITY,
                name = "Google Antigravity",
                profileId = "oauth_antigravity",
                protocol = AiProtocol.ANTIGRAVITY,
                // Chat requests use the managed Antigravity IDE host. Project onboarding remains
                // on the production Cloud Code Assist host in resolveGoogleAccount().
                baseUrl = ANTIGRAVITY_IDE_BASE_URL,
                authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth",
                tokenUrl = "https://oauth2.googleapis.com/token",
                clientId = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com",
                clientSecret = "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",
                scope = listOf(
                    "https://www.googleapis.com/auth/cloud-platform",
                    "https://www.googleapis.com/auth/userinfo.email",
                    "https://www.googleapis.com/auth/userinfo.profile",
                    "https://www.googleapis.com/auth/cclog",
                    "https://www.googleapis.com/auth/experimentsandconfigs",
                ).joinToString(" "),
                callbackPath = "/callback",
                extraAuthorizationParams = mapOf(
                    "access_type" to "offline",
                    "prompt" to "consent",
                ),
                usePkce = ANTIGRAVITY_OAUTH_USES_PKCE,
                models = listOf(
                    OAuthModel("gemini-3-flash-agent", "Gemini 3.5 Flash (High)", 1_000_000, 64_000),
                    OAuthModel("gemini-pro-agent", "Gemini 3.1 Pro (High)", 1_000_000, 64_000),
                ),
            ),
            OAuthProvider(
                id = AiOAuthProviderId.XAI,
                name = "xAI / Grok",
                profileId = "oauth_xai",
                protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://api.x.ai/v1",
                authorizeUrl = "https://auth.x.ai/oauth2/authorize",
                tokenUrl = "https://auth.x.ai/oauth2/token",
                clientId = "b1a00492-073a-47ea-816f-4c329264a828",
                scope = "openid profile email offline_access grok-cli:access api:access",
                refreshScope = "openid profile email offline_access grok-cli:access api:access",
                callbackPath = "/callback",
                fixedPort = 56121,
                redirectHost = "127.0.0.1",
                extraAuthorizationParams = mapOf(
                    "plan" to "generic",
                    "referrer" to "cli-proxy-api",
                ),
                models = listOf(
                    OAuthModel("grok-4", "Grok 4", 256_000, 64_000),
                    OAuthModel("grok-4-fast-reasoning", "Grok 4 Fast Reasoning", 2_000_000, 64_000),
                    OAuthModel("grok-code-fast-1", "Grok Code Fast", 256_000, 64_000),
                ),
            ),
            OAuthProvider(
                id = AiOAuthProviderId.KIMI,
                name = "Kimi Code",
                profileId = "oauth_kimi",
                protocol = AiProtocol.ANTHROPIC_MESSAGES,
                baseUrl = "https://api.kimi.com/coding",
                authorizeUrl = "",
                tokenUrl = "https://auth.kimi.com/api/oauth/token",
                clientId = "17e5f671-d194-4dfb-9706-5516cb48c098",
                scope = "",
                callbackPath = "/callback",
                refreshLeadMs = 5 * 60 * 1_000L,
                models = listOf(
                    OAuthModel("k3", "Kimi K3 (Code)", 1_000_000, 64_000),
                    OAuthModel("kimi-for-coding", "Kimi for Coding", 256_000, 64_000),
                    OAuthModel(
                        "kimi-for-coding-highspeed",
                        "Kimi for Coding Highspeed",
                        256_000,
                        64_000,
                    ),
                    OAuthModel("kimi-k2.7-code", "Kimi K2.7 Code", 256_000, 64_000),
                ),
                flow = OAuthFlow.DEVICE_CODE,
                deviceCodeUrl = "https://auth.kimi.com/api/oauth/device_authorization",
                deviceVerificationUrl = "https://www.kimi.com/code/authorize_device",
                authType = AiProviderAuthType.HEADER,
                customHeaders = mapOf("x-api-key" to "{apiKey}"),
                messagesPath = "/v1/messages",
            ),
            OAuthProvider(
                id = AiOAuthProviderId.QWEN,
                name = "Qwen Code",
                profileId = "oauth_qwen",
                protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://portal.qwen.ai/v1",
                authorizeUrl = "",
                tokenUrl = "https://chat.qwen.ai/api/v1/oauth2/token",
                clientId = "f0304373b74a44d2b584a3fb70ca9e56",
                scope = "openid profile email model.completion",
                callbackPath = "/callback",
                refreshLeadMs = 20 * 60 * 1_000L,
                models = listOf(
                    OAuthModel("qwen3-coder-plus", "Qwen3 Coder Plus", 1_000_000, 64_000),
                    OAuthModel("qwen3-coder-flash", "Qwen3 Coder Flash", 1_000_000, 64_000),
                    OAuthModel("coder-model", "Qwen3.6 Coder Model", 1_000_000, 64_000),
                ),
                flow = OAuthFlow.DEVICE_CODE,
                deviceCodeUrl = "https://chat.qwen.ai/api/v1/oauth2/device/code",
                deviceUsePkce = true,
                chatPath = "/chat/completions",
            ),
            OAuthProvider(
                id = AiOAuthProviderId.GROK_CLI,
                name = "Grok CLI (Grok Build)",
                profileId = "oauth_grok_cli",
                protocol = AiProtocol.GROK_CLI_SUBSCRIPTION,
                baseUrl = "https://cli-chat-proxy.grok.com/v1",
                authorizeUrl = "",
                tokenUrl = "https://auth.x.ai/oauth2/token",
                clientId = "b1a00492-073a-47ea-816f-4c329264a828",
                scope = "openid profile email offline_access grok-cli:access api:access " +
                    "conversations:read conversations:write",
                refreshScope = "openid profile email offline_access grok-cli:access api:access " +
                    "conversations:read conversations:write",
                callbackPath = "/callback",
                models = listOf(
                    OAuthModel("grok-build", "Grok Build", 500_000, 64_000),
                    OAuthModel("grok-4.5", "Grok 4.5", 500_000, 64_000),
                    OAuthModel("grok-4.5-high", "Grok 4.5 (High)", 500_000, 64_000),
                    OAuthModel("grok-4.5-medium", "Grok 4.5 (Medium)", 500_000, 64_000),
                    OAuthModel("grok-4.5-low", "Grok 4.5 (Low)", 500_000, 64_000),
                ),
                flow = OAuthFlow.DEVICE_CODE,
                deviceCodeUrl = "https://auth.x.ai/oauth2/device/code",
                authType = AiProviderAuthType.BEARER,
                customHeaders = mapOf(
                    "User-Agent" to "grok-shell/0.2.99 (android; arm64)",
                    "x-xai-token-auth" to "xai-grok-cli",
                    "x-grok-client-identifier" to "grok-shell",
                    "x-grok-client-version" to "0.2.99",
                    "x-grok-client-mode" to "headless",
                ),
                responsesPath = "/responses",
            ),
            OAuthProvider(
                id = AiOAuthProviderId.GITHUB,
                name = "GitHub Copilot",
                profileId = "oauth_github",
                protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://api.githubcopilot.com",
                authorizeUrl = "https://github.com/login/oauth/authorize",
                tokenUrl = "https://github.com/login/oauth/access_token",
                clientId = "Iv1.b507a08c87ecfe98",
                scope = "read:user",
                callbackPath = "/callback",
                models = listOf(
                    OAuthModel("gpt-5.2", "GPT-5.2", 128_000, 64_000),
                    OAuthModel("gpt-5.2-codex", "GPT-5.2 Codex", 128_000, 64_000),
                    OAuthModel("gpt-5.3-codex", "GPT-5.3 Codex", 128_000, 64_000),
                    OAuthModel("gpt-5.4", "GPT-5.4", 128_000, 64_000),
                    OAuthModel("gpt-5.4-mini", "GPT-5.4 Mini", 128_000, 64_000),
                    OAuthModel("claude-sonnet-4.6", "Claude Sonnet 4.6", 200_000, 64_000),
                    OAuthModel("claude-opus-4.7", "Claude Opus 4.7", 200_000, 64_000),
                    OAuthModel("gemini-3-flash-preview", "Gemini 3 Flash", 1_000_000, 64_000),
                    OAuthModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro", 1_000_000, 64_000),
                    OAuthModel("grok-code-fast-1", "Grok Code Fast 1", 256_000, 64_000),
                ),
                flow = OAuthFlow.DEVICE_CODE,
                deviceCodeUrl = "https://github.com/login/device/code",
                deviceVerificationUrl = "https://github.com/login/device",
                authType = AiProviderAuthType.BEARER,
                customHeaders = mapOf(
                    "copilot-integration-id" to "vscode-chat",
                    "editor-version" to "vscode/1.110.0",
                    "editor-plugin-version" to "copilot-chat/0.38.0",
                    "user-agent" to "GitHubCopilotChat/0.38.0",
                    "openai-intent" to "conversation-panel",
                    "x-github-api-version" to "2025-04-01",
                    "x-vscode-user-agent-library-version" to "electron-fetch",
                    "X-Initiator" to "user",
                    "Accept" to "application/json",
                ),
                chatPath = "/chat/completions",
            ),
            OAuthProvider(
                id = AiOAuthProviderId.CLINE,
                name = "Cline",
                profileId = "oauth_cline",
                protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://api.cline.bot/api/v1",
                authorizeUrl = "https://api.cline.bot/api/v1/auth/authorize",
                tokenUrl = "https://api.cline.bot/api/v1/auth/token",
                clientId = "",
                scope = "",
                callbackPath = "/callback",
                usePkce = false,
                models = listOf(
                    OAuthModel("anthropic/claude-opus-4.7", "Claude Opus 4.7", 200_000, 64_000),
                    OAuthModel("anthropic/claude-sonnet-4.6", "Claude Sonnet 4.6", 200_000, 64_000),
                    OAuthModel("openai/gpt-5.4", "GPT-5.4", 128_000, 64_000),
                    OAuthModel("google/gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", 1_000_000, 64_000),
                    OAuthModel("google/gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite Preview", 1_000_000, 64_000),
                    OAuthModel("kwaipilot/kat-coder-pro", "KAT Coder Pro", 128_000, 64_000),
                ),
                authType = AiProviderAuthType.BEARER,
                customHeaders = mapOf(
                    "HTTP-Referer" to "https://cline.bot",
                    "X-Title" to "Cline",
                ),
                chatPath = "/chat/completions",
            ),
            OAuthProvider(
                id = AiOAuthProviderId.CLINEPASS,
                name = "ClinePass",
                profileId = "oauth_clinepass",
                protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://api.cline.bot/api/v1",
                authorizeUrl = "https://api.cline.bot/api/v1/auth/authorize",
                tokenUrl = "https://api.cline.bot/api/v1/auth/token",
                clientId = "",
                scope = "",
                callbackPath = "/callback",
                usePkce = false,
                models = listOf(
                    OAuthModel("cline-pass/glm-5.2", "GLM-5.2 (ClinePass)", 128_000, 64_000),
                    OAuthModel("cline-pass/kimi-k2.7-code", "Kimi K2.7 Code (ClinePass)", 256_000, 64_000),
                    OAuthModel("cline-pass/deepseek-v4-pro", "DeepSeek V4 Pro (ClinePass)", 128_000, 64_000),
                    OAuthModel("cline-pass/mimo-v2.5", "MiMo V2.5 (ClinePass)", 200_000, 32_000),
                ),
                authType = AiProviderAuthType.BEARER,
                customHeaders = mapOf(
                    "HTTP-Referer" to "https://cline.bot",
                    "X-Title" to "ClinePass",
                ),
                chatPath = "/chat/completions",
            ),
        )
    }
}

private data class OAuthProvider(
    val id: String,
    val name: String,
    val profileId: String,
    val protocol: String,
    val baseUrl: String,
    val authorizeUrl: String,
    val tokenUrl: String,
    val clientId: String,
    val clientSecret: String? = null,
    val scope: String,
    val refreshScope: String? = null,
    val callbackPath: String,
    val fixedPort: Int = 0,
    val redirectHost: String = "localhost",
    val usePkce: Boolean = true,
    val jsonTokenRequest: Boolean = false,
    val jsonRefreshRequest: Boolean = false,
    val refreshLeadMs: Long = 5 * 60 * 1_000L,
    val extraAuthorizationParams: Map<String, String> = emptyMap(),
    val models: List<OAuthModel>,
    val flow: OAuthFlow = OAuthFlow.LOOPBACK,
    val deviceCodeUrl: String? = null,
    val deviceVerificationUrl: String? = null,
    val deviceUsePkce: Boolean = false,
    val authType: String? = null,
    val headers: Map<String, String>? = null,
    val customHeaders: Map<String, String>? = null,
    val chatPath: String? = null,
    val responsesPath: String? = null,
    val messagesPath: String? = null,
    val maxRefreshAgeMs: Long? = null,
)

private enum class OAuthFlow {
    LOOPBACK,
    DEVICE_CODE,
}

private data class OAuthModel(
    val id: String,
    val name: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
)

private data class OAuthAccount(
    val id: String,
    val label: String,
    val data: Map<String, String>,
)

    private data class OAuthSaveResult(
    val providerProfileId: String,
    val modelProfileId: String,
    val credentialId: String,
    val routeProfileId: String,
    val targetId: String,
) {
    fun toConnectedEvent(providerId: String, accountLabel: String) = AiOAuthEvent.Connected(
        providerId = providerId,
        accountLabel = accountLabel,
        providerProfileId = providerProfileId,
        modelProfileId = modelProfileId,
        credentialId = credentialId,
        routeProfileId = routeProfileId,
        targetId = targetId,
    )
}

internal fun shouldBindOAuthPresetToDefaultRoute(
    existingRouteProfileId: String?,
    defaultRouteProfileId: String,
): Boolean = existingRouteProfileId.isNullOrBlank() ||
    existingRouteProfileId == defaultRouteProfileId

private data class OAuthRouteBinding(
    val routeId: String,
    val targetId: String,
)

private fun buildAuthorizationUrl(
    config: OAuthProvider,
    redirectUri: String,
    state: String,
    challenge: String,
): String {
    if (config.id == AiOAuthProviderId.CLINE || config.id == AiOAuthProviderId.CLINEPASS) {
        return Uri.parse(config.authorizeUrl).buildUpon()
            .appendQueryParameter("client_type", "extension")
            .appendQueryParameter("callback_url", redirectUri)
            .appendQueryParameter("redirect_uri", redirectUri)
            .build()
            .toString()
    }
    return Uri.parse(config.authorizeUrl).buildUpon().apply {
    appendQueryParameter("client_id", config.clientId)
    appendQueryParameter("response_type", "code")
    appendQueryParameter("redirect_uri", redirectUri)
    appendQueryParameter("scope", config.scope)
    appendQueryParameter("state", state)
    if (config.usePkce) {
        appendQueryParameter("code_challenge", challenge)
        appendQueryParameter("code_challenge_method", "S256")
    }
    config.extraAuthorizationParams.forEach(::appendQueryParameter)
    if (config.id == AiOAuthProviderId.XAI) {
        appendQueryParameter("nonce", randomBase64Url(16))
    }
    }.build().toString()
}

private class OAuthLoopbackServer private constructor(
    private val serverSocket: ServerSocket,
    private val callbackPath: String,
    private val redirectHost: String,
) {
    val redirectUri: String = "http://$redirectHost:${serverSocket.localPort}$callbackPath"

    suspend fun awaitCallback(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            serverSocket.soTimeout = 5 * 60 * 1_000
            serverSocket.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine().orEmpty()
                while (true) {
                    if (reader.readLine().isNullOrEmpty()) break
                }
                val target = requestLine.split(' ').getOrNull(1)
                    ?: error("OAuth callback không hợp lệ")
                val uri = Uri.parse("http://localhost$target")
                require(uri.path == callbackPath) { "OAuth callback path không hợp lệ" }
                val params = uri.queryParameterNames.associateWith { key ->
                    uri.getQueryParameter(key).orEmpty()
                }
                val responseBody =
                    "Đã nhận xác nhận từ nhà cung cấp. Hãy quay lại Legado để chờ hoàn tất lưu tài khoản."
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/plain; charset=utf-8\r\n")
                    append("Content-Length: ${responseBody.toByteArray(Charsets.UTF_8).size}\r\n")
                    append("Connection: close\r\n\r\n")
                    append(responseBody)
                }
                socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                params
            }
        } catch (_: SocketTimeoutException) {
            error("Đăng nhập OAuth quá thời gian 5 phút")
        }
    }

    fun close() {
        runCatching { serverSocket.close() }
    }

    companion object {
        fun open(port: Int, callbackPath: String, redirectHost: String): OAuthLoopbackServer {
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(redirectHost), port))
            }
            return OAuthLoopbackServer(socket, callbackPath, redirectHost)
        }
    }
}

private fun randomBase64Url(bytes: Int): String = ByteArray(bytes).also {
    SecureRandom().nextBytes(it)
}.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

private fun sha256Base64Url(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.US_ASCII))
    .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

private fun stableCredentialId(providerId: String, accountId: String): String {
    val uuid = UUID.nameUUIDFromBytes("oauth:$providerId:$accountId".toByteArray())
    return "credential_${uuid.toString().replace("-", "")}" 
}

private fun stableOAuthTargetId(
    routeId: String,
    modelProfileId: String,
): String {
    val uuid = UUID.nameUUIDFromBytes("oauth-target:$routeId:$modelProfileId".toByteArray())
    return "target_${uuid.toString().replace("-", "")}"
}

private fun String.decodeJwtPayload(): JsonObject? = runCatching {
    val payload = split('.').getOrNull(1) ?: return@runCatching null
    val decoded = Base64.getUrlDecoder().decode(payload.padBase64())
    GSON.fromJson(decoded.toString(Charsets.UTF_8), JsonObject::class.java)
}.getOrNull()

private fun String.padBase64(): String = this + "=".repeat((4 - length % 4) % 4)

private fun JsonObject.findString(name: String): String? {
    string(name)?.let { return it }
    entrySet().forEach { (_, value) ->
        if (value.isJsonObject) value.asJsonObject.findString(name)?.let { return it }
    }
    return null
}

private fun JsonObject.string(name: String): String? = get(name)
    ?.takeUnless { it.isJsonNull }
    ?.takeIf { it.isJsonPrimitive }
    ?.asString

private fun JsonObject.long(name: String): Long? = get(name)
    ?.takeUnless { it.isJsonNull }
    ?.let { runCatching { it.asLong }.getOrNull() }

private fun String.oauthErrorMessage(): String {
    val root = runCatching { GSON.fromJson(this, JsonObject::class.java) }.getOrNull()
    return root?.string("error_description")
        ?: root?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
        ?: root?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.string("message")
        ?: "Provider từ chối yêu cầu OAuth"
}
