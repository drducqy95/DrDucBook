package io.legado.app.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drducbook.app.BuildConfig
import com.drducbook.app.R
import io.legado.app.domain.model.BrowserSourceContext
import io.legado.app.domain.model.SourceKeyType
import io.legado.app.help.http.CookieManager as AppCookieManager
import io.legado.app.help.http.CookieStore
import io.legado.app.help.config.AppConfig
import io.legado.app.help.vbook.VbookMtcSessionCompat
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.utils.sendToClip
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.worker.BookSourceHealthWorker
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.lang.ref.WeakReference

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserRouteScreen(
    initialUrl: String?,
    sourceProbeUrl: String?,
    onBackClick: () -> Unit,
    onExitClick: () -> Unit,
    onOpenSourceHealth: (String?) -> Unit,
    viewModel: BrowserViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val invalidUrlMessage = stringResource(R.string.browser_invalid_url)
    val sslErrorMessage = stringResource(R.string.source_health_ssl_blocked)
    val downloadStartedMessage = stringResource(R.string.browser_download_started)
    val loginSyncedMessage = stringResource(R.string.browser_login_synced)
    val cookieClearedMessage = stringResource(R.string.browser_source_cookie_cleared)
    val currentOnExitClick by rememberUpdatedState(onExitClick)
    val currentOnOpenSourceHealth by rememberUpdatedState(onOpenSourceHealth)
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }
    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = true
                userAgentString = AppConfig.userAgent
            }
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        }
    }
    val defaultUserAgent = remember(webView) { webView.settings.userAgentString.orEmpty() }

    LaunchedEffect(viewModel, initialUrl, sourceProbeUrl) {
        viewModel.onIntent(BrowserIntent.Initialize(initialUrl, sourceProbeUrl))
    }

    LaunchedEffect(state.initialized, state.loadGeneration) {
        if (!state.initialized) return@LaunchedEffect
        if (state.isHomeMode) return@LaunchedEffect
        val tab = state.tabs.firstOrNull { it.id == state.activeTabId } ?: return@LaunchedEffect
        if (tab.isHome || tab.url.isBlank()) return@LaunchedEffect
        if (!isSafeBrowserUrl(tab.url)) {
            viewModel.onIntent(BrowserIntent.PageError(invalidUrlMessage))
            return@LaunchedEffect
        }
        AppCookieManager.applyToWebView(tab.url)
        webView.loadUrl(tab.url)
    }

    LaunchedEffect(viewModel, webView) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                BrowserEffect.GoBack -> if (webView.canGoBack()) webView.goBack()
                BrowserEffect.GoForward -> if (webView.canGoForward()) webView.goForward()
                BrowserEffect.Reload -> webView.reload()
                BrowserEffect.Stop -> webView.stopLoading()
                BrowserEffect.RequestPageSnapshot -> {
                    webView.evaluateJavascript(
                        BrowserPageTranslationBridge.extractionScript()
                    ) { result ->
                        viewModel.onIntent(
                            BrowserIntent.PageSnapshotReady(
                                BrowserPageTranslationBridge.decodeSnapshot(result)
                            )
                        )
                    }
                }
                BrowserEffect.RestoreOriginalPage -> webView.evaluateJavascript(
                    BrowserPageTranslationBridge.restoreOriginalScript(),
                    null,
                )
                is BrowserEffect.ApplyPageTranslations -> {
                    webView.evaluateJavascript(
                        BrowserPageTranslationBridge.applyTranslationsScript(effect.translations),
                        null,
                    )
                    webView.evaluateJavascript(
                        BrowserPageTranslationBridge.installMutationObserverScript(),
                        null,
                    )
                }
                is BrowserEffect.SetDesktopMode -> {
                    webView.settings.userAgentString = if (effect.enabled) {
                        DESKTOP_USER_AGENT
                    } else {
                        defaultUserAgent
                    }
                    webView.reload()
                }
                is BrowserEffect.OpenExternal -> openExternal(context, effect.url)
                is BrowserEffect.SharePage -> sharePage(context, effect.url, effect.title)
                is BrowserEffect.CopyLink -> context.sendToClip(effect.url)
                is BrowserEffect.SyncLoginAndProbe -> {
                    syncWebCookies(context, effect.url, effect.sourceUrl)
                    BookSourceHealthWorker.runNow(context, effect.sourceUrl)
                    context.toastOnUi(loginSyncedMessage)
                }
                is BrowserEffect.ShowMessage -> context.toastOnUi(effect.message)
                is BrowserEffect.OpenSourceHealth -> currentOnOpenSourceHealth(effect.sourceUrl)
                is BrowserEffect.OpenSourceLogin -> openSourceLogin(context, effect.sourceContext)
                is BrowserEffect.OpenSourceEdit -> openSourceEdit(context, effect.sourceContext)
                is BrowserEffect.ClearSourceCookie -> {
                    clearSourceCookie(effect.sourceContext)
                    context.toastOnUi(cookieClearedMessage)
                }
                BrowserEffect.ExitBrowser -> {
                    syncWebCookies(context, webView.url, sourceProbeUrl)
                    currentOnExitClick()
                }
            }
        }
    }

    DisposableEffect(webView, viewModel) {
        webView.addJavascriptInterface(
            BrowserMutationSignal { viewModel.onIntent(BrowserIntent.PageMutationDetected) },
            BrowserPageTranslationBridge.MUTATION_BRIDGE_NAME,
        )
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewModel.onIntent(BrowserIntent.PageProgress(newProgress))
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                val types = fileChooserParams?.acceptTypes
                    ?.filter(String::isNotBlank)
                    ?.toTypedArray()
                    ?.takeIf { it.isNotEmpty() }
                    ?: arrayOf("*/*")
                filePicker.launch(types)
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (isSafeBrowserUrl(url)) return false
                if (url.startsWith("mailto:") || url.startsWith("tel:")) {
                    openExternal(context, url)
                } else {
                    viewModel.onIntent(BrowserIntent.PageError(invalidUrlMessage))
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let { viewModel.onIntent(BrowserIntent.PageStarted(it)) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val finalUrl = url ?: return
                val page = view ?: return
                syncWebCookies(context, finalUrl, sourceProbeUrl)
                page.evaluateJavascript(
                    BrowserPageTranslationBridge.installMutationObserverScript(),
                    null,
                )
                viewModel.onIntent(
                    BrowserIntent.PageFinished(
                        url = finalUrl,
                        title = page.title.orEmpty(),
                        canGoBack = page.canGoBack(),
                        canGoForward = page.canGoForward(),
                    )
                )
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    viewModel.onIntent(
                        BrowserIntent.PageError(error?.description?.toString().orEmpty())
                    )
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: android.net.http.SslError?,
            ) {
                handler?.cancel()
                viewModel.onIntent(BrowserIntent.PageError(sslErrorMessage))
            }
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (!isSafeBrowserUrl(url)) {
                context.toastOnUi(invalidUrlMessage)
                return@setDownloadListener
            }
            runCatching {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(url.toUri())
                    .setTitle(fileName)
                    .setMimeType(mimeType)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                CookieManager.getInstance().getCookie(url)
                    ?.takeIf(String::isNotBlank)
                    ?.let { request.addRequestHeader("Cookie", it) }
                userAgent?.takeIf(String::isNotBlank)
                    ?.let { request.addRequestHeader("User-Agent", it) }
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                context.toastOnUi(downloadStartedMessage)
            }.onFailure { error ->
                context.toastOnUi(error.localizedMessage ?: invalidUrlMessage)
            }
        }
        onDispose {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            syncWebCookies(context, webView.url, sourceProbeUrl)
            webView.stopLoading()
            webView.removeJavascriptInterface(BrowserPageTranslationBridge.MUTATION_BRIDGE_NAME)
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    fun handleBrowserBack() {
        if (state.isHomeMode) {
            val tab = state.tabs.firstOrNull { it.id == state.activeTabId }
            if (tab?.isHome == true || tab?.url.isNullOrBlank()) {
                if (state.tabs.size > 1) {
                    viewModel.onIntent(BrowserIntent.CloseTab(state.activeTabId))
                } else {
                    onBackClick()
                }
            } else {
                viewModel.onIntent(BrowserIntent.ExitHome)
            }
            return
        }
        when (resolveBrowserBackTarget(webView.canGoBack(), state.tabs.size)) {
            BrowserBackTarget.WEB_HISTORY -> webView.goBack()
            BrowserBackTarget.CLOSE_TAB -> {
                viewModel.onIntent(BrowserIntent.CloseTab(state.activeTabId))
            }
            BrowserBackTarget.APP_ROUTE -> {
                syncWebCookies(context, webView.url, sourceProbeUrl)
                onBackClick()
            }
        }
    }

    BackHandler { handleBrowserBack() }

    BrowserScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = ::handleBrowserBack,
        webContent = {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

private fun syncWebCookies(context: Context, url: String?, scopeUrl: String? = null) {
    val safeUrl = url?.takeIf(::isSafeBrowserUrl)
    AppCookieManager.syncFromWebView(
        safeUrl,
        scopeUrl?.takeIf(String::isNotBlank),
    )
    val cookie = safeUrl?.let { CookieManager.getInstance().getCookie(it) }
    VbookMtcSessionCompat.syncBrowserCookie(context, scopeUrl, cookie)
}

private fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }.onFailure { context.toastOnUi(it.localizedMessage ?: url) }
}

private fun sharePage(context: Context, url: String, title: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                putExtra(Intent.EXTRA_TITLE, title)
            },
            title,
        )
    )
}

private fun openSourceLogin(context: Context, sourceContext: BrowserSourceContext) {
    context.startActivity<SourceLoginActivity> {
        putExtra(
            "type",
            when (sourceContext.key.type) {
                SourceKeyType.BOOK -> "bookSource"
                SourceKeyType.RSS -> "rssSource"
            }
        )
        putExtra("key", sourceContext.sourceUrl)
    }
}

private fun openSourceEdit(context: Context, sourceContext: BrowserSourceContext) {
    when (sourceContext.key.type) {
        SourceKeyType.BOOK -> context.startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", sourceContext.sourceUrl)
        }

        SourceKeyType.RSS -> context.startActivity<RssSourceEditActivity> {
            putExtra("sourceUrl", sourceContext.sourceUrl)
        }
    }
}

private fun clearSourceCookie(sourceContext: BrowserSourceContext) {
    CookieStore.removeCookie(sourceContext.sourceUrl)
}

private class BrowserMutationSignal(onMutation: () -> Unit) {
    private val callback = WeakReference(onMutation)

    @JavascriptInterface
    fun onMutation() {
        callback.get()?.invoke()
    }
}

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
