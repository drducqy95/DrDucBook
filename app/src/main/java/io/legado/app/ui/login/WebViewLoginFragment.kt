package io.legado.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONTokener
import androidx.fragment.app.activityViewModels
import com.drducbook.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.BaseSource
import com.drducbook.app.databinding.FragmentWebViewLoginBinding
import io.legado.app.help.http.CookieManager as AppCookieManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.vbook.VbookMtcSessionCompat
//import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.gone
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.snackbar
import io.legado.app.utils.viewbindingdelegate.viewBinding

class WebViewLoginFragment : BaseFragment(R.layout.fragment_web_view_login) {

    private val binding by viewBinding(FragmentWebViewLoginBinding::bind)
    private val viewModel by activityViewModels<SourceLoginViewModel>()

    private var checking = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        viewModel.source?.let {
            binding.titleBar.title = getString(R.string.login_source, it.getTag())
            initWebView(it)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.source_webview_login, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_ok -> {
                if (!checking) {
                    checking = true
                    binding.titleBar.snackbar(R.string.check_host_cookie)
                    viewModel.source?.let {
                        loadUrl(it)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(source: BaseSource) {
        //binding.progressBar.fontColor = accentColor
        binding.webView.settings.apply {
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            javaScriptEnabled = true
            displayZoomControls = false
            userAgentString = viewModel.headerMap[AppConst.UA_NAME] ?: AppConfig.userAgent
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                syncBrowserSession(source, url)
                if (checking) {
                    activity?.finish()
                }
                super.onPageFinished(view, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return shouldOverrideUrlLoading(request.url)
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "KotlinRedundantDiagnosticSuppress")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return shouldOverrideUrlLoading(Uri.parse(url))
            }

            private fun shouldOverrideUrlLoading(url: Uri): Boolean {
                when (url.scheme) {
                    "http", "https" -> {
                        return false
                    }

                    else -> {
                        binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                            context?.openUrl(url)
                        }
                        return true
                    }
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel()
            }
        }
        binding.webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.setProgress(newProgress)
                binding.progressBar.gone(newProgress == 100)
            }

        }
        loadUrl(source)
    }

    private fun loadUrl(source: BaseSource) {
        val loginUrl = source.loginUrl ?: return
        val absoluteUrl = NetworkUtils.getAbsoluteURL(source.getKey(), loginUrl)
        AppCookieManager.applyToWebView(absoluteUrl)
        binding.webView.loadUrl(absoluteUrl, viewModel.headerMap)
    }

    private fun syncBrowserSession(source: BaseSource, url: String?) {
        AppCookieManager.syncFromWebView(url, source.getKey())
        val cookie = url?.let { CookieManager.getInstance().getCookie(it) }
        val appContext = context?.applicationContext ?: return
        // MTC has shipped versions that keep the token in Web Storage rather than
        // a cookie. Read both stores before the login activity is closed.
        val storageScript = """
            (function() {
              try {
                return JSON.stringify({
                  authorization: localStorage.getItem('authorization'),
                  accessToken: localStorage.getItem('accessToken'),
                  access_token: localStorage.getItem('access_token'),
                  token: localStorage.getItem('token')
                });
              } catch (error) {
                return '{}';
              }
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(storageScript) { encoded ->
            val storageJson = runCatching {
                JSONTokener(encoded).nextValue() as? String
            }.getOrNull()
            VbookMtcSessionCompat.syncBrowserSession(
                context = appContext,
                sourceUrl = source.getKey(),
                cookie = cookie,
                localStorageJson = storageJson,
            )
        }
    }

    override fun onPause() {
        viewModel.source?.let { syncBrowserSession(it, binding.webView.url) }
        super.onPause()
    }

    override fun onDestroy() {
        viewModel.source?.let { syncBrowserSession(it, binding.webView.url) }
        super.onDestroy()
        binding.webView.destroy()
    }

}
