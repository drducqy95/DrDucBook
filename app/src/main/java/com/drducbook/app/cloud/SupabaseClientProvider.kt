package com.drducbook.app.cloud

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import com.drducbook.app.auth.DrDucBookDeepLinks
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import splitties.init.appCtx
import java.security.MessageDigest

object SupabaseClientProvider {

    val config: SupabasePublicConfig by lazy(SupabasePublicConfig::fromBuildConfig)

    val client: SupabaseClient? by lazy { create(config) }

    internal fun create(
        config: SupabasePublicConfig,
        sessionManagerOverride: SessionManager? = null,
    ): SupabaseClient? {
        if (!config.isConfigured) return null
        config.requireValid()
        return createSupabaseClient(
            supabaseUrl = config.url,
            supabaseKey = config.publishableKey,
        ) {
            install(Auth) {
                scheme = "drducbook"
                host = "auth"
                defaultRedirectUrl = DrDucBookDeepLinks.AUTH_CALLBACK
                flowType = FlowType.PKCE
                sessionManager = sessionManagerOverride ?: EncryptedSessionManager(
                    context = appCtx,
                    configFingerprint = configFingerprint(config),
                )
                codeVerifierCache = MemoryCodeVerifierCache()
            }
            install(Postgrest)
            install(Storage)
            install(Functions)
        }
    }

    private fun configFingerprint(config: SupabasePublicConfig): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${config.url}|${config.publishableKey}".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
}
