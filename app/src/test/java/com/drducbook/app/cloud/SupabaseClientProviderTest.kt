package com.drducbook.app.cloud

import android.app.Application
import io.github.jan.supabase.auth.MemorySessionManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SupabaseClientProviderTest {

    @Test
    fun blankPublicConfigDoesNotCreateAClient() {
        assertNull(SupabaseClientProvider.create(config(url = "", key = "")))
    }

    @Test
    fun publishableConfigCreatesAllModuleClientWithoutNetworkAccess() {
        assertNotNull(
            SupabaseClientProvider.create(
                config(
                    url = "https://project-ref.supabase.co",
                    key = "sb_publishable_phase01_test",
                ),
                sessionManagerOverride = MemorySessionManager(),
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun serverSecretKeyIsRejected() {
        SupabaseClientProvider.create(
            config(
                url = "https://project-ref.supabase.co",
                key = "sb_secret_never_in_android",
            )
        )
    }

    private fun config(url: String, key: String) = SupabasePublicConfig(
        url = url,
        publishableKey = key,
        googleAuthClientId = "",
        googleDriveClientId = "",
    )
}
