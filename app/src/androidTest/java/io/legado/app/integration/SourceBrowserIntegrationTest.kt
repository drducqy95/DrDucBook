package io.legado.app.integration

import android.content.Context
import android.webkit.CookieManager as WebkitCookieManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.domain.model.BrowserPageTextTranslation
import io.legado.app.data.cookie.CookieVaultCodec
import io.legado.app.help.http.CookieManager as AppCookieManager
import io.legado.app.help.http.CookieStore
import io.legado.app.ui.browser.BrowserPageTranslationBridge
import io.legado.app.ui.browser.isSafeBrowserUrl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class SourceBrowserIntegrationTest {

    private val testModule = module {
        single<CookieVaultCodec> { PassThroughCookieVaultCodec }
    }

    @Before
    fun setUp() {
        loadKoinModules(testModule)
    }

    @After
    fun tearDown() {
        unloadKoinModules(testModule)
    }

    @Test
    fun extractedNodeIdentitySurvivesApplyAndRestoreScripts() {
        val payload = """{"nodes":[{"id":"node-1","text":"第一章","contentHash":"hash-1"}]}"""
        val nodes = BrowserPageTranslationBridge.decodeSnapshot(Json.encodeToString(payload))
        val apply = BrowserPageTranslationBridge.applyTranslationsScript(
            listOf(BrowserPageTextTranslation("node-1", "第一章", "Chương 1", "hash-1"))
        )

        assertEquals("node-1", nodes.single().id)
        assertTrue(apply.contains("hash-1"))
        assertTrue(BrowserPageTranslationBridge.restoreOriginalScript().contains("entry.original"))
    }

    @Test
    fun browserCookieBridgeSyncsWebViewAndVaultScopes() {
        ApplicationProvider.getApplicationContext<Context>()
        val pageUrl = "https://phase04-device.example/login"
        val sourceUrl = "https://phase04-device.example/source"
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            WebkitCookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
                setCookie(pageUrl, "sid=device; Path=/")
                flush()
            }
            AppCookieManager.syncFromWebView(pageUrl, sourceUrl)
        }

        assertTrue(CookieStore.getCookie(sourceUrl).contains("sid=device"))

        CookieStore.replaceCookie(sourceUrl, "vault=applied")
        instrumentation.runOnMainSync {
            WebkitCookieManager.getInstance().removeAllCookies(null)
            WebkitCookieManager.getInstance().flush()
            AppCookieManager.applyToWebView(pageUrl)
        }

        val applied = WebkitCookieManager.getInstance().getCookie(pageUrl).orEmpty()
        assertTrue(applied.contains("vault=applied"))
        assertTrue(isSafeBrowserUrl(pageUrl))

        CookieStore.removeCookie(pageUrl)
        CookieStore.removeCookie(sourceUrl)
        assertEquals("", CookieStore.getCookie(sourceUrl))
        assertTrue(WebkitCookieManager.getInstance().getCookie(pageUrl).isNullOrBlank())
    }
}

private object PassThroughCookieVaultCodec : CookieVaultCodec {
    override fun encrypt(value: String): String = value
    override fun decrypt(value: String): String? = value
}
