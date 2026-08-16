package io.legado.app.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.utils.GSON
import java.util.Base64

internal object CodexOAuthMetadata {
    const val CHATGPT_ACCOUNT_ID = "chatgptAccountId"
    const val LAST_REFRESH_AT = "lastRefreshAt"
    const val OAUTH_SCOPE = "openid profile email offline_access"
    const val REFRESH_USES_JSON = false

    fun extractAccountId(
        accessToken: String?,
        idToken: String? = null,
    ): String? = sequenceOf(idToken, accessToken)
        .mapNotNull { token -> token?.decodeJwtPayload() }
        .mapNotNull { payload ->
            payload.findStringClaim(
                "chatgpt_account_id",
                "account_id",
                "https://api.openai.com/auth.chatgpt_account_id",
            )
        }
        .firstOrNull()

    fun extractEmail(
        accessToken: String?,
        idToken: String? = null,
    ): String? = sequenceOf(idToken, accessToken)
        .mapNotNull { token -> token?.decodeJwtPayload() }
        .mapNotNull { payload -> payload.findStringClaim("email") }
        .firstOrNull()

    fun extractSubject(
        accessToken: String?,
        idToken: String? = null,
    ): String? = sequenceOf(idToken, accessToken)
        .mapNotNull { token -> token?.decodeJwtPayload() }
        .mapNotNull { payload -> payload.findStringClaim("sub") }
        .firstOrNull()

    private fun String.decodeJwtPayload(): JsonObject? = runCatching {
        val payload = split('.').getOrNull(1) ?: return@runCatching null
        val decoded = Base64.getUrlDecoder().decode(payload.padBase64())
        GSON.fromJson(decoded.toString(Charsets.UTF_8), JsonObject::class.java)
    }.getOrNull()

    private fun String.padBase64(): String = this + "=".repeat((4 - length % 4) % 4)

    private fun JsonElement.findStringClaim(vararg names: String, depth: Int = 0): String? {
        if (depth > 6) return null
        if (isJsonObject) {
            val obj = asJsonObject
            names.forEach { name ->
                obj.string(name)?.let { return it }
            }
            obj.entrySet().forEach { (key, value) ->
                if (names.any { name -> key.matchesClaimName(name) }) {
                    value.asStringOrNull()?.let { return it }
                }
            }
            obj.entrySet().forEach { (_, value) ->
                value.findStringClaim(*names, depth = depth + 1)?.let { return it }
            }
        } else if (isJsonArray) {
            asJsonArray.forEach { child ->
                child.findStringClaim(*names, depth = depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun String.matchesClaimName(name: String): Boolean =
        this == name || endsWith(".$name") || endsWith("/$name") || endsWith(":$name")

    private fun JsonObject.string(name: String): String? = get(name)
        ?.asStringOrNull()

    private fun JsonElement.asStringOrNull(): String? = takeUnless { it.isJsonNull }
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?.takeIf(String::isNotBlank)
}
