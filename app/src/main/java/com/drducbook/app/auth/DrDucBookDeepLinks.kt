package com.drducbook.app.auth

import java.net.URI

object DrDucBookDeepLinks {
    const val AUTH_CALLBACK = "drducbook://auth/callback"
    const val IMPORT_PREFIX = "drducbook://import"

    fun isAuthCallback(rawUri: String?): Boolean {
        val uri = rawUri?.let { runCatching { URI(it) }.getOrNull() } ?: return false
        return uri.scheme == "drducbook" && uri.host == "auth" && uri.path == "/callback"
    }
}
