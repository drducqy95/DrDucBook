package io.legado.app.ui.book.info.edit

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import io.legado.app.ui.theme.AppTheme
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookInfoEditActivity : BaseComposeActivity(), ChangeCoverDialog.CallBack {

    private val viewModel by viewModel<BookInfoEditViewModel>()

    @Composable
    override fun Content() {
        setContent {
            AppTheme {
                BookInfoEditScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onSave = {
                        viewModel.save {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("bookUrl")?.let {
            viewModel.loadBook(it)
        }
    }

    override fun coverChangeTo(coverUrl: String) {
        // 更新封面 URL
        viewModel.onCoverUrlChange(coverUrl)
    }

}
