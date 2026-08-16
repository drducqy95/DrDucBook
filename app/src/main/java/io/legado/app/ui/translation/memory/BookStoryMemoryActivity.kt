package io.legado.app.ui.translation.memory

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class BookStoryMemoryActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val bookUrl = intent.getStringExtra(EXTRA_BOOK_URL).orEmpty()
        BookStoryMemoryRouteScreen(
            bookUrl = bookUrl,
            onBack = { onBackPressedDispatcher.onBackPressed() },
        )
    }

    companion object {
        private const val EXTRA_BOOK_URL = "bookUrl"

        fun createIntent(context: Context, bookUrl: String) =
            Intent(context, BookStoryMemoryActivity::class.java)
                .putExtra(EXTRA_BOOK_URL, bookUrl)
    }
}
