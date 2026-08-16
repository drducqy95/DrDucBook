package io.legado.app.ui.main

import androidx.annotation.StringRes
import com.drducbook.app.R
import io.legado.app.ui.config.themeConfig.ThemeConfig
import kotlinx.collections.immutable.persistentListOf

sealed class MainDestination(
    val route: String,
    @StringRes val labelId: Int
) {
    object Home : MainDestination(
        route = "home",
        labelId = R.string.home
    )

    object Bookshelf : MainDestination(
        route = "bookshelf",
        labelId = R.string.bookshelf
    )

    object Explore : MainDestination(
        route = "explore",
        labelId = R.string.discovery
    )

    object Workspace : MainDestination(
        route = "workspace",
        labelId = R.string.workspace_title
    )

    object My : MainDestination(
        route = "my",
        labelId = R.string.my
    )

    companion object {
        private val legacyWorkspaceRoutes = setOf(
            "browser",
            "ai_agent",
            "writing",
            "ebook_editor",
            "rss",
        )

        val mainDestinations = persistentListOf<MainDestination>(
            Home,
            Bookshelf,
            Explore,
            Workspace,
            My
        )

        fun ordered(order: String): List<MainDestination> {
            val byRoute = mainDestinations.associateBy { it.route }
            val ordered = order
                .split(',')
                .map(String::trim)
                .map(::normalizeSavedRoute)
                .distinct()
                .mapNotNull(byRoute::get)
            return ordered + mainDestinations.filterNot { it in ordered }
        }

        fun normalizeSavedRoute(route: String): String =
            if (route in legacyWorkspaceRoutes) Workspace.route else route
    }
}

val MainDestination.customIconPath: String
    get() = when (this) {
        MainDestination.Home -> ThemeConfig.navIconHome
        MainDestination.Bookshelf -> ThemeConfig.navIconBookshelf
        MainDestination.Explore -> ThemeConfig.navIconExplore
        MainDestination.Workspace -> ThemeConfig.navIconWorkspace
        MainDestination.My -> ThemeConfig.navIconMy
    }
