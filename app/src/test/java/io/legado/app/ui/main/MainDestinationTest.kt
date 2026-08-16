package io.legado.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainDestinationTest {

    @Test
    fun topLevelNavigationContainsExactlyFiveDestinations() {
        assertEquals(
            listOf(
                MainDestination.Home,
                MainDestination.Bookshelf,
                MainDestination.Explore,
                MainDestination.Workspace,
                MainDestination.My,
            ),
            MainDestination.mainDestinations,
        )
    }

    @Test
    fun legacyToolDestinationsCollapseIntoOneWorkspacePosition() {
        val migrated = MainDestination.ordered(
            "home,bookshelf,explore,browser,ai_agent,writing,ebook_editor,rss,my"
        )

        assertEquals(MainDestination.mainDestinations, migrated)
    }

    @Test
    fun legacyDefaultPagesFallbackToWorkspace() {
        listOf("browser", "ai_agent", "writing", "ebook_editor", "rss").forEach { route ->
            assertEquals(
                MainDestination.Workspace.route,
                MainDestination.normalizeSavedRoute(route),
            )
        }
    }
}
