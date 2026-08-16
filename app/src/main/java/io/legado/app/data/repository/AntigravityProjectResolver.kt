package io.legado.app.data.repository

import com.google.gson.JsonObject
import java.util.UUID

internal const val ANTIGRAVITY_PRODUCTION_BASE_URL = "https://cloudcode-pa.googleapis.com"
/** Managed IDE transport host; project discovery/onboarding deliberately stays on production. */
internal const val ANTIGRAVITY_IDE_BASE_URL = "https://daily-cloudcode-pa.googleapis.com"
internal const val ANTIGRAVITY_IDE_USER_AGENT = "antigravity/ide/2.1.1 darwin/arm64"
internal const val ANTIGRAVITY_OAUTH_USES_PKCE = false

internal fun antigravityCodeAssistHeaders(accessToken: String): Map<String, String> = mapOf(
    "Authorization" to "Bearer $accessToken",
    "Content-Type" to "application/json",
    "User-Agent" to ANTIGRAVITY_IDE_USER_AGENT,
    "x-request-source" to "local",
)

internal fun antigravityClientMetadata(isArm64: Boolean): Map<String, Int> = mapOf(
    "ideType" to 9,
    // Antigravity's enum uses Linux x64=3 and Linux arm64=4; Android follows Linux here.
    "platform" to if (isArm64) 4 else 3,
    "pluginType" to 2,
)

/** Same fallback shape used by 9Router when project discovery is temporarily unavailable. */
internal fun generateAntigravityProjectId(
    suffix: String = UUID.randomUUID().toString().replace("-", "").take(5),
): String = "useful-fuze-${suffix.lowercase().filter(Char::isLetterOrDigit).take(5)}"

/**
 * Resolves the managed Code Assist project returned by Antigravity.
 * Existing accounts already have a project and must not be onboarded again. New accounts receive
 * only the available tiers from loadCodeAssist, so onboarding is required before a project exists.
 */
internal suspend fun resolveAntigravityProject(
    loadPayload: JsonObject,
    maxOnboardingAttempts: Int = 10,
    onboard: suspend (tierId: String) -> JsonObject,
    waitBeforeRetry: suspend () -> Unit,
): String {
    loadPayload.antigravityProjectId()?.let { return it }

    val tierId = loadPayload.getAsJsonArray("allowedTiers")
        ?.mapNotNull { tier -> tier.takeIf { it.isJsonObject }?.asJsonObject }
        .orEmpty()
        .let { tiers ->
            tiers.firstOrNull { it.get("isDefault")?.asBoolean == true }
                ?: tiers.firstOrNull()
        }
        ?.stringValue("id")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "FREE"

    repeat(maxOnboardingAttempts.coerceAtLeast(1)) { attempt ->
        val result = onboard(tierId)
        if (result.get("done")?.asBoolean == true) {
            result.getAsJsonObject("response")
                ?.antigravityProjectId()
                ?.let { return it }
            error("Google Code Assist đã onboarding nhưng không trả project")
        }
        if (attempt < maxOnboardingAttempts - 1) waitBeforeRetry()
    }
    error("Google Code Assist onboarding quá thời gian")
}

private fun JsonObject.antigravityProjectId(): String? {
    val value = get("cloudaicompanionProject") ?: return null
    return when {
        value.isJsonPrimitive -> value.asString
        value.isJsonObject -> value.asJsonObject.stringValue("id")
        else -> null
    }?.trim()?.takeIf(String::isNotEmpty)
}

private fun JsonObject.stringValue(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString
