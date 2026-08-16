package com.drducbook.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import com.drducbook.app.auth.AuthCallbackActivity
import io.legado.app.api.ReaderProvider
import io.legado.app.ui.association.OnLineImportActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ManifestIdentityTest {

    @Test
    fun providersAndDeepLinksUseTheNewApplicationIdentity() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val packageInfo = context.packageManager.getPackageInfo(
            packageName,
            android.content.pm.PackageManager.GET_PROVIDERS,
        )
        val readerProvider = packageInfo.providers.orEmpty().single {
            it.name == ReaderProvider::class.java.name
        }

        assertEquals("$packageName.readerProvider", readerProvider.authority)
        assertNotEquals("io.legato.kazusa.readerProvider", readerProvider.authority)
        assertResolvesTo(
            Intent(Intent.ACTION_VIEW, Uri.parse("drducbook://auth/callback?code=test")),
            AuthCallbackActivity::class.java,
        )
        assertResolvesTo(
            Intent(Intent.ACTION_VIEW, Uri.parse("drducbook://import?src=test")),
            OnLineImportActivity::class.java,
        )
    }

    private fun assertResolvesTo(intent: Intent, expected: Class<*>) {
        val context: Application = RuntimeEnvironment.getApplication()
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        val matches = context.packageManager.queryIntentActivities(intent, 0)
        assertTrue(
            "${intent.data} did not resolve to ${expected.name}",
            matches.any {
                ComponentName(it.activityInfo.packageName, it.activityInfo.name).className == expected.name
            },
        )
    }
}
