package io.legado.app.ui.browser

import android.app.Application
import io.legado.app.domain.model.SourceKey
import io.legado.app.domain.model.SourceKeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BrowserTabStoreTest {

    @Test
    fun restoresNormalTabsAndActiveSelection() {
        val application: Application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("browser_tabs", 0).edit().clear().commit()
        val store = BrowserTabStore(application)
        val first = store.newTab("https://example.com/one")
        val second = store.newTab("https://example.com/two")

        store.save(listOf(first, second), second.id)
        val restored = BrowserTabStore(application).restore()

        assertEquals(2, restored.tabs.size)
        assertEquals(second.id, restored.activeTabId)
        assertEquals("https://example.com/two", restored.tabs.last().url)
    }

    @Test
    fun emptySessionRestoresHomeTab() {
        val application: Application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("browser_tabs", 0).edit().clear().commit()

        val restored = BrowserTabStore(application).restore()

        assertEquals(1, restored.tabs.size)
        assertEquals(BrowserTabStore.HOME_URL, restored.tabs.single().url)
        assertEquals(true, restored.tabs.single().isHome)
        assertEquals(restored.tabs.single().id, restored.activeTabId)
    }

    @Test
    fun unsafeSchemesAreNotPersistedOrNavigatedDirectly() {
        val application: Application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("browser_tabs", 0).edit().clear().commit()
        val store = BrowserTabStore(application)
        store.save(
            listOf(store.newTab("file:///data/user/0/private.txt")),
            "missing",
        )

        val restored = BrowserTabStore(application).restore()

        assertFalse(restored.tabs.any { it.url.startsWith("file:") })
        assertEquals(true, restored.tabs.single().isHome)
        assertEquals("https://example.com", normalizeBrowserInput("example.com"))
        assertFalse(isSafeBrowserUrl("javascript:alert(1)"))
    }

    @Test
    fun browserUrlPolicyRejectsNonHttpSchemesAndMalformedHosts() {
        assertFalse(isSafeBrowserUrl("content://settings/system"))
        assertFalse(isSafeBrowserUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse(isSafeBrowserUrl("about:blank"))
        assertFalse(isSafeBrowserUrl("https:///missing-host"))

        assertEquals("https://example.org", normalizeBrowserInput("example.org"))
        assertEquals(
            "https://www.google.com/search?q=javascript%3Aalert%281%29",
            normalizeBrowserInput("javascript:alert(1)"),
        )
    }

    @Test
    fun multipleTabsSurviveBrowserExitAndRecreation() {
        val application: Application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("browser_tabs", 0).edit().clear().commit()
        val store = BrowserTabStore(application)
        val first = store.newTab("https://example.com/first")
        val second = store.newTab("https://example.com/second")

        store.save(listOf(first, second), first.id)
        val restoredAfterExit = BrowserTabStore(application).restore()

        assertEquals(listOf(first.id, second.id), restoredAfterExit.tabs.map { it.id })
        assertEquals(first.id, restoredAfterExit.activeTabId)
    }

    @Test
    fun sourceKeySurvivesSaveAndRestore() {
        val application: Application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("browser_tabs", 0).edit().clear().commit()
        val store = BrowserTabStore(application)
        val tab = store.newTab("https://example.com/home").copy(
            sourceKey = SourceKey(SourceKeyType.BOOK, "https://example.com/source"),
        )

        store.save(listOf(tab), tab.id)
        val restored = BrowserTabStore(application).restore()

        assertEquals(tab.sourceKey, restored.tabs.single().sourceKey)
    }
}
