package io.legado.app.ui.book.source.health

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.domain.model.SourceKeyType
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.utils.startActivity

class SourceHealthActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL)
        SourceHealthRouteScreen(
            sourceUrl = sourceUrl,
            onBackClick = { finish() },
            onOpenBrowser = { sourceUrlParam, initialUrl ->
                startActivity<WebViewActivity> {
                    putExtra("title", sourceUrlParam)
                    putExtra("url", initialUrl ?: sourceUrlParam)
                }
            },
            onOpenEdit = { sourceUrlParam, sourceType ->
                when (sourceType) {
                    SourceKeyType.BOOK -> startActivity<BookSourceEditActivity> {
                        putExtra("sourceUrl", sourceUrlParam)
                    }
                    SourceKeyType.RSS -> startActivity<RssSourceEditActivity> {
                        putExtra("sourceUrl", sourceUrlParam)
                    }
                }
            },
        )
    }

    companion object {
        const val EXTRA_SOURCE_URL = "sourceUrl"

        fun startIntent(context: Context, sourceUrl: String? = null): Intent =
            Intent(context, SourceHealthActivity::class.java).apply {
                if (!sourceUrl.isNullOrBlank()) {
                    putExtra(EXTRA_SOURCE_URL, sourceUrl)
                }
            }
    }
}
