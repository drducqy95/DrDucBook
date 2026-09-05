package io.legado.app.ui.download.center

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.media.download.MediaDownloadsRouteScreen
import io.legado.app.ui.widget.components.tabRow.AppTabRow

@Composable
fun DownloadCenterScreen(
    onOpenDownloadSettings: () -> Unit,
    onImportAudiobook: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.offline_cache),
        stringResource(R.string.media_downloads_title),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            AppTabRow(
                tabTitles = tabTitles,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                isScrollable = false,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "DownloadCenterTabTransition"
            ) { tab ->
                when (tab) {
                    0 -> BookCacheManageRouteScreen(
                        onBackClick = onBack,
                        onOpenDownloadSettings = onOpenDownloadSettings,
                    )
                    1 -> MediaDownloadsRouteScreen(
                        onBack = onBack,
                        onImportAudiobook = onImportAudiobook,
                        onOpenDownloadSettings = onOpenDownloadSettings,
                    )
                }
            }
        }
    }
}
