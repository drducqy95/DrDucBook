package com.drducbook.app.cloud

import com.drducbook.app.BuildConfig
import java.net.URI

data class SupabasePublicConfig(
    val url: String,
    val publishableKey: String,
    val googleAuthClientId: String,
    val googleDriveClientId: String,
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() && publishableKey.isNotBlank()

    fun requireValid() {
        if (!isConfigured) return
        val uri = runCatching { URI(url) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) {
            "SUPABASE_URL must be an HTTPS URL"
        }
        require(!publishableKey.startsWith("sb_secret_")) {
            "SUPABASE_PUBLISHABLE_KEY must not be a server secret key"
        }
    }

    companion object {
        fun fromBuildConfig(): SupabasePublicConfig = SupabasePublicConfig(
            url = BuildConfig.SUPABASE_URL.trim(),
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim(),
            googleAuthClientId = BuildConfig.GOOGLE_AUTH_CLIENT_ID.trim(),
            googleDriveClientId = BuildConfig.GOOGLE_DRIVE_CLIENT_ID.trim(),
        )
    }
}
