package io.legado.app.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserBackPolicyTest {

    @Test
    fun webHistoryHasPriorityOverTabsAndAppRoute() {
        assertEquals(
            BrowserBackTarget.WEB_HISTORY,
            resolveBrowserBackTarget(canGoBack = true, tabCount = 3),
        )
    }

    @Test
    fun activeTabClosesBeforeLeavingBrowser() {
        assertEquals(
            BrowserBackTarget.CLOSE_TAB,
            resolveBrowserBackTarget(canGoBack = false, tabCount = 2),
        )
    }

    @Test
    fun lastTabReturnsToApplicationRoute() {
        assertEquals(
            BrowserBackTarget.APP_ROUTE,
            resolveBrowserBackTarget(canGoBack = false, tabCount = 1),
        )
    }
}
