package io.legado.app.ui.main

import android.app.Application
import android.content.Intent
import io.legado.app.ui.config.ConfigTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MainIntentTest {

    @Test
    fun readBookIntentIsDeliveredToTheExistingMainActivity() {
        val intent = MainIntent.createReadBookIntent(
            context = RuntimeEnvironment.getApplication(),
            bookUrl = "file:///sdcard/Download/test.epub",
        )

        assertEquals(MainRouteConst.ROUTE_READ_BOOK, intent.getStringExtra(MainIntent.EXTRA_START_ROUTE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun sourceHealthIntentCarriesOptionalSourceUrl() {
        val intent = MainIntent.createSourceHealthIntent(
            context = RuntimeEnvironment.getApplication(),
            sourceUrl = "https://source.example",
        )

        assertEquals(MainRouteConst.ROUTE_SOURCE_HEALTH, intent.getStringExtra(MainIntent.EXTRA_START_ROUTE))
        assertEquals("https://source.example", intent.getStringExtra(MainIntent.EXTRA_SOURCE_URL))
        assertEquals(
            MainRouteSourceHealth(sourceUrl = "https://source.example"),
            MainNavigator.resolveStartRoute(intent),
        )
    }

    @Test
    fun accountConfigIntentResolvesToAccountSettingsRoute() {
        val intent = MainIntent.createIntent(
            context = RuntimeEnvironment.getApplication(),
            configTag = ConfigTag.ACCOUNT_CONFIG,
        )

        assertEquals(MainRouteConst.ROUTE_SETTINGS_ACCOUNT, intent.getStringExtra(MainIntent.EXTRA_START_ROUTE))
        assertEquals(MainRouteSettingsAccount, MainNavigator.resolveStartRoute(intent))
    }
}
