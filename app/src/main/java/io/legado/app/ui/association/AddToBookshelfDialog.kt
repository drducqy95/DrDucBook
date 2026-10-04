package io.legado.app.ui.association

import android.annotation.SuppressLint
import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import com.drducbook.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import com.drducbook.app.databinding.DialogAddToBookshelfBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 添加书籍链接到书架，需要对应网站书源
 * ${origin}/${path}, {origin: bookSourceUrl}
 * 按以下顺序尝试匹配书源并添加网址
 * - UrlOption中的指定的书源网址bookSourceUrl
 * - 在所有启用的书源中匹配orgin
 * - 在所有启用的书源中使用详情页正则匹配${origin}/${path}, {origin: bookSourceUrl}
 */
class AddToBookshelfDialog() : BaseDialogFragment(R.layout.dialog_add_to_bookshelf) {

    constructor(bookUrl: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("bookUrl", bookUrl)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    val binding by viewBinding(DialogAddToBookshelfBinding::bind)
    val viewModel by viewModels<ViewModel>()

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean("finishOnDismiss") == true) {
            activity?.finish()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val bookUrl = arguments?.getString("bookUrl")
        if (bookUrl.isNullOrBlank()) {
            toastOnUi(R.string.url_required)
            dismiss()
            return
        }
        viewModel.loadStateLiveData.observe(this) {
            if (it) {
                binding.rotateLoading.isVisible = true
            } else {
                binding.rotateLoading.isVisible = true
            }
        }
        viewModel.loadErrorLiveData.observe(this) {
            toastOnUi(it)
            dismiss()
        }
        viewModel.load(
            bookUrl = bookUrl,
            onExplore = { explore ->
                startActivity(
                    MainActivity.createExploreShowIntent(
                        context = requireContext(),
                        exploreName = explore.title,
                        sourceUrl = explore.bookSource.bookSourceUrl,
                        exploreUrl = explore.exploreUrl,
                    )
                )
                dismiss()
            },
            onSuccess = { book ->
                viewModel.saveSearchBook(book) {
                    startActivity(
                        MainActivity.createBookInfoIntent(
                            context = requireContext(),
                            name = book.name,
                            author = book.author,
                            bookUrl = book.bookUrl,
                            origin = book.origin,
                            coverPath = book.coverUrl
                        )
                    )
                    dismiss()
                }
            }
        )
        binding.tvCancel.setOnClickListener {
            dismiss()
        }
    }

    class ViewModel(application: Application) : BaseViewModel(application) {

        private val resolver: io.legado.app.domain.usecase.ExternalUrlResolverUseCase by lazy {
            org.koin.java.KoinJavaComponent.get(io.legado.app.domain.usecase.ExternalUrlResolverUseCase::class.java)
        }

        val loadStateLiveData = MutableLiveData<Boolean>()
        val loadErrorLiveData = MutableLiveData<String>()
        var book: Book? = null

        fun load(
            bookUrl: String,
            onExplore: (io.legado.app.domain.usecase.ResolvedExternalUrl.ExploreCategory) -> Unit,
            onSuccess: (book: Book) -> Unit
        ) {
            execute {
                when (val resolved = resolver.resolve(bookUrl)) {
                    is io.legado.app.domain.usecase.ResolvedExternalUrl.BookDetail -> {
                        if (resolved.inBookshelf) {
                            throw NoStackTraceException(
                                context.getString(R.string.book_already_in_shelf, resolved.book.name)
                            )
                        }
                        resolved
                    }
                    is io.legado.app.domain.usecase.ResolvedExternalUrl.ExploreCategory -> resolved
                    is io.legado.app.domain.usecase.ResolvedExternalUrl.Unmatched -> {
                        throw NoStackTraceException(context.getString(R.string.matching_source_not_found))
                    }
                }
            }.onError {
                AppLog.put("添加书籍 $bookUrl 出错", it)
                loadErrorLiveData.postValue(it.localizedMessage)
            }.onSuccess {
                when (it) {
                    is io.legado.app.domain.usecase.ResolvedExternalUrl.BookDetail -> {
                        book = it.book
                        onSuccess.invoke(it.book)
                    }
                    is io.legado.app.domain.usecase.ResolvedExternalUrl.ExploreCategory -> {
                        onExplore.invoke(it)
                    }
                    else -> {}
                }
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }

        fun saveSearchBook(book: Book, success: () -> Unit) {
            execute {
                val searchBook = book.toSearchBook()
                appDb.searchBookDao.insert(searchBook)
                searchBook
            }.onSuccess {
                success.invoke()
            }
        }

    }

}
