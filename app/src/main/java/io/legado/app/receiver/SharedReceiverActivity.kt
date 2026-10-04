package io.legado.app.receiver

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.legado.app.domain.usecase.ExternalUrlResolverUseCase
import io.legado.app.domain.usecase.ResolvedExternalUrl
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class SharedReceiverActivity : AppCompatActivity() {

    private val receivingType = "text/plain"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initIntent()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun initIntent() {
        when {
            intent.action == Intent.ACTION_SEND && intent.type == receivingType -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    dispose(it)
                } ?: finish()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && intent.action == Intent.ACTION_PROCESS_TEXT
                    && intent.type == receivingType -> {
                intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)?.let {
                    dispose(it)
                } ?: finish()
            }
            intent.getStringExtra("action") == "readAloud" -> {
                MediaButtonReceiver.readAloud(appCtx, false)
                finish()
            }
            else -> finish()
        }
    }

    private fun dispose(text: String) {
        if (text.isBlank()) {
            finish()
            return
        }
        val firstUrl = text.split("\\s+".toRegex()).firstOrNull {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
        if (firstUrl != null) {
            lifecycleScope.launch(Dispatchers.Main) {
                val resolver = org.koin.java.KoinJavaComponent.get<ExternalUrlResolverUseCase>(
                    ExternalUrlResolverUseCase::class.java
                )
                val resolved = withContext(Dispatchers.IO) { resolver.resolve(firstUrl) }
                when (resolved) {
                    is ResolvedExternalUrl.BookDetail -> {
                        startActivity(
                            MainActivity.createBookInfoIntent(
                                context = this@SharedReceiverActivity,
                                name = resolved.book.name,
                                author = resolved.book.author,
                                bookUrl = resolved.book.bookUrl,
                                origin = resolved.book.origin,
                                coverPath = resolved.book.coverUrl,
                            )
                        )
                    }
                    is ResolvedExternalUrl.ExploreCategory -> {
                        startActivity(
                            MainActivity.createExploreShowIntent(
                                context = this@SharedReceiverActivity,
                                exploreName = resolved.title,
                                sourceUrl = resolved.bookSource.bookSourceUrl,
                                exploreUrl = resolved.exploreUrl,
                            )
                        )
                    }
                    is ResolvedExternalUrl.Unmatched -> {
                        startActivity(MainActivity.createHomeIntent(this@SharedReceiverActivity))
                    }
                }
                finish()
            }
        } else {
            SearchActivity.start(this, text)
            finish()
        }
    }
}
