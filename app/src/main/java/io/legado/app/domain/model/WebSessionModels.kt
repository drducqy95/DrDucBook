package io.legado.app.domain.model

import androidx.annotation.Keep

object WebSessionType {
    const val GEMINI_WEB = "gemini_web"
    const val CHATGPT_WEB = "chatgpt_web"
}

@Keep
data class WebSessionCredential(
    val sessionType: String,
    val cookies: Map<String, String> = emptyMap(),
    val token: String? = null,
    val accountId: String? = null,
    val userAgent: String? = null,
    val expiresAt: Long? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    fun cookieHeader(): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    fun hasValidCookies(): Boolean =
        cookies.isNotEmpty()
}
