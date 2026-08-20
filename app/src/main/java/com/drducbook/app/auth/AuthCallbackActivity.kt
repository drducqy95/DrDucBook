package com.drducbook.app.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.drducbook.app.cloud.SupabaseClientProvider
import io.github.jan.supabase.auth.handleDeeplinks
import io.legado.app.ui.main.MainActivity

class AuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DrDucBookDeepLinks.isAuthCallback(intent?.dataString)) {
            finish()
            return
        }
        val client = SupabaseClientProvider.client
        if (client == null) {
            AuthCallbackStateStore.saveError(this, IllegalStateException("Supabase chưa được cấu hình"))
            returnToApp()
            return
        }
        client.handleDeeplinks(
            intent = intent,
            onSessionSuccess = { returnToApp() },
            onError = { error ->
                AuthCallbackStateStore.saveError(this, error)
                returnToApp()
            },
        )
    }

    private fun returnToApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}
