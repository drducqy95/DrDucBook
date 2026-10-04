package io.legado.app.ui.association

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.drducbook.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import com.drducbook.app.databinding.DialogOpenUrlConfirmBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class OpenUrlConfirmDialog() : BaseDialogFragment(R.layout.dialog_open_url_confirm),
    Toolbar.OnMenuItemClickListener {

    constructor(
        uri: String,
        mimeType: String?,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int
    ) : this() {
        arguments = Bundle().apply {
            putString("uri", uri)
            putString("mimeType", mimeType)
            putString("sourceOrigin", sourceOrigin)
            putString("sourceName", sourceName)
            putInt("sourceType", sourceType)
        }
    }

    val binding by viewBinding(DialogOpenUrlConfirmBinding::bind)
    val viewModel by viewModels<OpenUrlConfirmViewModel>()

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initMenu()
        val arguments = arguments ?: return
        viewModel.initData(arguments)
        if (viewModel.uri.isBlank()) {
            dismiss()
            return
        }
        //binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.subtitle = viewModel.sourceName
        initView()
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.open_url_confirm)
        //binding.toolBar.menu.applyTint(requireContext())
    }

    private fun initView() {
        binding.message.text = getString(R.string.open_url_request, viewModel.sourceName)
        binding.btnNegative.setOnClickListener { dismiss() }
        binding.btnPositive.setOnClickListener {
            openUrl()
            dismiss()
        }

        lifecycleScope.launch(Dispatchers.Main) {
            val resolver = org.koin.java.KoinJavaComponent.get<io.legado.app.domain.usecase.ExternalUrlResolverUseCase>(
                io.legado.app.domain.usecase.ExternalUrlResolverUseCase::class.java
            )
            val resolved = withContext(Dispatchers.IO) {
                resolver.resolve(viewModel.uri)
            }
            when (resolved) {
                is io.legado.app.domain.usecase.ResolvedExternalUrl.BookDetail -> {
                    val srcName = resolved.bookSource.bookSourceName
                    binding.message.text = "Phát hiện liên kết truyện từ nguồn [$srcName]. Bạn có muốn mở sách trong ứng dụng?"
                    binding.btnPositive.text = "Mở sách"
                    binding.btnPositive.setOnClickListener {
                        startActivity(
                            io.legado.app.ui.main.MainActivity.createBookInfoIntent(
                                context = requireContext(),
                                name = resolved.book.name,
                                author = resolved.book.author,
                                bookUrl = resolved.book.bookUrl,
                                origin = resolved.book.origin,
                                coverPath = resolved.book.coverUrl,
                            )
                        )
                        dismiss()
                    }
                }
                is io.legado.app.domain.usecase.ResolvedExternalUrl.ExploreCategory -> {
                    val srcName = resolved.bookSource.bookSourceName
                    binding.message.text = "Phát hiện liên kết khám phá từ nguồn [$srcName]. Bạn có muốn mở trong ứng dụng?"
                    binding.btnPositive.text = "Khám phá"
                    binding.btnPositive.setOnClickListener {
                        startActivity(
                            io.legado.app.ui.main.MainActivity.createExploreShowIntent(
                                context = requireContext(),
                                exploreName = resolved.title,
                                sourceUrl = resolved.bookSource.bookSourceUrl,
                                exploreUrl = resolved.exploreUrl,
                            )
                        )
                        dismiss()
                    }
                }
                is io.legado.app.domain.usecase.ResolvedExternalUrl.Unmatched -> {}
            }
        }
    }

    private fun openUrl() {
        try {
            val uri = viewModel.uri.toUri()
            val mimeType = viewModel.mimeType
            // 创建目标 Intent 并设置类型
            val targetIntent = Intent(Intent.ACTION_VIEW).apply {
                // 同时设置 Data 和 Type
                if (!mimeType.isNullOrBlank()) {
                    setDataAndType(uri, mimeType)
                } else {
                    data = uri
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 验证是否有应用可以处理
            if (targetIntent.resolveActivity(appCtx.packageManager) != null) {
                startActivity(targetIntent)
            } else {
                toastOnUi(R.string.can_not_open)
            }
        } catch (e: Exception) {
            AppLog.put("打开链接失败", e, true)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_disable_source -> {
                viewModel.disableSource {
                    dismiss()
                }
            }

            R.id.menu_delete_source -> {
                alert(R.string.draw) {
                    setMessage(getString(R.string.sure_del) + "\n" + viewModel.sourceName)
                    noButton()
                    yesButton {
                        viewModel.deleteSource {
                            dismiss()
                        }
                    }
                }
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.finish()
    }

}
