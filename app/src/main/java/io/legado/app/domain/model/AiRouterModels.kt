package io.legado.app.domain.model

import androidx.annotation.Keep

object AiRouteStrategy {
    const val PRIORITY = "priority"
    const val ROUND_ROBIN = "round_robin"
    const val WEIGHTED_ROUND_ROBIN = "weighted_round_robin"

    val all = listOf(PRIORITY, ROUND_ROBIN, WEIGHTED_ROUND_ROBIN)
}

object AiCredentialKind {
    const val API_KEY = "api_key"
    const val BEARER_TOKEN = "bearer_token"
    const val OAUTH_ACCESS_TOKEN = "oauth_access_token"

    val all = listOf(API_KEY, BEARER_TOKEN, OAUTH_ACCESS_TOKEN)
}

object AiOAuthProviderId {
    const val CODEX = "codex"
    const val CLAUDE = "claude"
    const val ANTIGRAVITY = "antigravity"
    const val XAI = "xai"
    const val KIMI = "kimi"
    const val QWEN = "qwen"
    const val GROK_CLI = "grok-cli"
    const val GITHUB = "github"
    const val CLINE = "cline"
    const val CLINEPASS = "clinepass"
    const val IFLOW = "iflow"
}

object AiCredentialStatus {
    const val VERIFYING = "verifying"
    const val ACTIVE = "active"
    const val REFRESHING = "refreshing"
    const val VERIFICATION_FAILED = "verification_failed"
    const val RELOGIN_REQUIRED = "relogin_required"

    fun isRouterEligible(status: String): Boolean =
        status == ACTIVE || status == REFRESHING
}

@Keep
data class AiCredentialConfig(
    val id: String,
    val providerId: String,
    val label: String,
    val kind: String = AiCredentialKind.API_KEY,
    val enabled: Boolean = true,
    val sortNumber: Int = 0,
    val cooldownUntil: Long = 0,
    val consecutiveFailures: Int = 0,
    val lastFailureKind: String? = null,
    val lastUsedAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val hasSecret: Boolean = false,
    val oauthProvider: String? = null,
    val accountId: String? = null,
    val accountLabel: String? = null,
    val expiresAt: Long? = null,
    val scopes: String? = null,
    val status: String = AiCredentialStatus.ACTIVE,
    val hasRefreshToken: Boolean = false,
)

@Keep
data class AiCredentialDraft(
    val id: String? = null,
    val providerId: String,
    val label: String,
    val kind: String = AiCredentialKind.API_KEY,
    /** Empty when editing means that the existing secret is retained. */
    val secret: String = "",
    val enabled: Boolean = true,
    val sortNumber: Int = 0,
)

@Keep
data class AiRouteProfileConfig(
    val id: String,
    val name: String,
    val taskType: String,
    val strategy: String = AiRouteStrategy.PRIORITY,
    val maxAttempts: Int = 3,
    val stickySession: Boolean = true,
    val enabled: Boolean = true,
    val isDefault: Boolean = true,
    val sortNumber: Int = 0,
)

@Keep
data class AiRouteProfileDraft(
    val id: String? = null,
    val name: String,
    val taskType: String,
    val strategy: String = AiRouteStrategy.PRIORITY,
    val maxAttempts: Int = 3,
    val stickySession: Boolean = true,
    val enabled: Boolean = true,
    val makeDefault: Boolean = true,
    val sortNumber: Int = 0,
)

@Keep
data class AiRouteTargetConfig(
    val id: String,
    val routeProfileId: String,
    val modelProfileId: String,
    val credentialId: String? = null,
    val priority: Int = 0,
    val weight: Int = 1,
    val maxConcurrency: Int = 0,
    val enabled: Boolean = true,
    val sortNumber: Int = 0,
    val cooldownUntil: Long = 0,
    val consecutiveFailures: Int = 0,
    val lastFailureKind: String? = null,
    val lastUsedAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
)

@Keep
data class AiRouteTargetDraft(
    val id: String? = null,
    val routeProfileId: String,
    val modelProfileId: String,
    val credentialId: String? = null,
    val priority: Int = 0,
    val weight: Int = 1,
    val maxConcurrency: Int = 0,
    val enabled: Boolean = true,
    val sortNumber: Int = 0,
)

@Keep
data class AiRouteAttemptConfig(
    val id: Long,
    val routeProfileId: String,
    val targetId: String,
    val providerName: String,
    val modelName: String,
    val credentialLabel: String?,
    val success: Boolean,
    val failureKind: String?,
    val latencyMs: Long,
    val firstEventMs: Long?,
    val createdAt: Long,
)

@Keep
data class AiRouterSnapshot(
    val credentials: List<AiCredentialConfig> = emptyList(),
    val routes: List<AiRouteProfileConfig> = emptyList(),
    val targets: List<AiRouteTargetConfig> = emptyList(),
    val attempts: List<AiRouteAttemptConfig> = emptyList(),
)

@Keep
data class AiOAuthProviderConfig(
    val id: String,
    val name: String,
    val warning: String,
    val flow: String = "",
    val available: Boolean = true,
)

@Keep
data class AiOAuthAuthorization(
    val providerId: String,
    val authorizationUrl: String,
    val userCode: String? = null,
)

sealed interface AiOAuthEvent {
    data class Connected(
        val providerId: String,
        val accountLabel: String,
        val providerProfileId: String = "",
        val modelProfileId: String = "",
        val credentialId: String = "",
        val routeProfileId: String = "",
        val targetId: String = "",
    ) : AiOAuthEvent
    data class Failed(val providerId: String, val message: String) : AiOAuthEvent
}
