package io.legado.app.data.repository

import com.google.gson.JsonObject
import java.util.UUID

internal const val ANTIGRAVITY_PRODUCTION_BASE_URL = "https://cloudcode-pa.googleapis.com"
/** Managed IDE transport host; falls back to production for general availability. */
internal const val ANTIGRAVITY_IDE_BASE_URL = "https://daily-cloudcode-pa.googleapis.com"
internal const val ANTIGRAVITY_IDE_USER_AGENT = "antigravity/ide/2.9.1 darwin/arm64"
internal const val ANTIGRAVITY_OAUTH_USES_PKCE = false

internal const val ANTIGRAVITY_DEFAULT_SYSTEM =
    "You are Antigravity, a powerful agentic AI coding assistant designed by the Google Deepmind team working on Advanced Agentic Coding.\n" +
    "You are pair programming with a USER to solve their coding task. The task may require creating a new codebase, modifying or debugging an existing codebase, or simply answering a question.\n" +
    "**Absolute paths only**\n" +
    "**Proactiveness**"

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

val ANTIGRAVITY_SUPPORTED_MODELS: List<io.legado.app.domain.model.AiAvailableModel> = listOf(
    // Gemini 3.8 Flash (released 2026-09-02)
    io.legado.app.domain.model.AiAvailableModel("gemini-3.8-flash-high", "Gemini 3.8 (High)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("gemini-3.8-flash-medium", "Gemini 3.8 (Medium)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("gemini-3.8-flash-low", "Gemini 3.8 (Low)", 1_000_000, 64_000),

    // Gemini 3.7 Flash
    io.legado.app.domain.model.AiAvailableModel("gemini-3.7-flash-high", "Gemini 3.7 Flash (High)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("gemini-3.7-flash-medium", "Gemini 3.7 Flash (Medium)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("gemini-3.7-flash-low", "Gemini 3.7 Flash (Low)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("gemini-3.7-flash-tiered", "Gemini 3.7 Flash (Tiered)", 1_000_000, 64_000),

    // Gemini 3.6 Flash
    io.legado.app.domain.model.AiAvailableModel("gemini-3.6-flash-high", "Gemini 3.6 Flash (High)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("gemini-3.6-flash-medium", "Gemini 3.6 Flash (Medium)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("gemini-3.6-flash-low", "Gemini 3.6 Flash (Low)", 1_000_000, 64_000),

    // Gemini 3.1 Pro
    io.legado.app.domain.model.AiAvailableModel("gemini-pro-agent", "Gemini 3.1 Pro (High)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("gemini-3.1-pro-low", "Gemini 3.1 Pro (Low)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", 1_000_000, 64_000),

    // Claude (via Antigravity backend)
    io.legado.app.domain.model.AiAvailableModel("claude-opus-4-6-thinking", "Claude Opus 4.6 (Thinking)", 1_000_000, 64_000),
    io.legado.app.domain.model.AiAvailableModel("claude-sonnet-4-6", "Claude Sonnet 4.6 (Thinking)", 1_000_000, 64_000),

    // GPT-OSS
    io.legado.app.domain.model.AiAvailableModel("gpt-oss-120b-medium", "GPT-OSS 120B Medium", 128_000, 64_000),
)
