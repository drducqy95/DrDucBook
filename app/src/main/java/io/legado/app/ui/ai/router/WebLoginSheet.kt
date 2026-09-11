package io.legado.app.ui.ai.router

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.runtime.LaunchedEffect
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.data.repository.ai.ChatGptWebSessionManager
import io.legado.app.domain.model.AiProtocol
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.GSON
import io.legado.app.utils.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebLoginSheet(
    show: Boolean,
    loginUrl: String,
    protocol: String,
    isAddingAccount: Boolean = false,
    onLoginSuccess: (cookieString: String, isAddingAccount: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    val isGemini = protocol == AiProtocol.GEMINI_WEB
    val providerName = if (isGemini) "Gemini Web" else "ChatGPT Web"
    val configuration = LocalConfiguration.current
    val webViewHeight = (configuration.screenHeightDp.dp * 0.68f).coerceIn(480.dp, 700.dp)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var addingAsNewAccount by remember(isAddingAccount) { mutableStateOf(isAddingAccount) }
    var selectedTab by remember { mutableIntStateOf(if (isGemini) 1 else 0) }
    var manualCookieText by remember { mutableStateOf("") }
    var capturedCookie by remember { mutableStateOf<String?>(null) }
    var capturedToken by remember { mutableStateOf<String?>(null) }
    var capturedSnlm0e by remember { mutableStateOf<String?>(null) }
    var capturedBl by remember { mutableStateOf<String?>(null) }
    var capturedAccountEmail by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf(loginUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isExchangingCookie by remember { mutableStateOf(false) }

    fun triggerSessionScan(webView: WebView?, pageUrl: String) {
        val cm = CookieManager.getInstance()
        val cookies = cm.getCookie(pageUrl) ?: cm.getCookie(loginUrl).orEmpty()
        if (cookies.isNotBlank()) {
            capturedCookie = cookies
        }

        if (isGemini) {
            val script = """
            (function() {
                try {
                    var wiz = window.WIZ_global_data;
                    if (wiz && wiz.SNlM0e) {
                        return JSON.stringify({
                            snlm0e: wiz.SNlM0e,
                            bl: wiz.cfb2h || ""
                        });
                    }
                } catch (e) {}
                return "";
            })();
            """.trimIndent()
            webView?.evaluateJavascript(script) { rawResult ->
                val unescaped = rawResult?.trim('"')?.replace("\\\"", "\"")
                if (!unescaped.isNullOrBlank() && unescaped != "null" && unescaped != "\"\"") {
                    val json = runCatching { GSON.fromJson(unescaped, JsonObject::class.java) }.getOrNull()
                    val sn = json?.get("snlm0e")?.asString
                    val bl = json?.get("bl")?.asString
                    if (!sn.isNullOrBlank()) {
                        capturedSnlm0e = sn
                        capturedBl = bl
                        if (cookies.contains("__Secure-1PSID")) {
                            statusMessage = "✅ Đã nhận diện phiên đăng nhập Gemini (__Secure-1PSID & SNlM0e)"
                        }
                    }
                }
            }
        } else {
            val script = """
            (function() {
                try {
                    return window.__chatgpt_session_json || "";
                } catch (e) { return ""; }
            })();
            """.trimIndent()
            webView?.evaluateJavascript(script) { rawResult ->
                val unescaped = rawResult?.trim('"')?.replace("\\\"", "\"")?.replace("\\\\", "\\")
                if (!unescaped.isNullOrBlank() && unescaped != "null" && unescaped != "\"\"") {
                    val json = runCatching { GSON.fromJson(unescaped, JsonObject::class.java) }.getOrNull()
                    val token = json?.get("accessToken")?.asString
                    val email = json?.get("email")?.asString.orEmpty()
                    if (!token.isNullOrBlank()) {
                        capturedToken = token
                        capturedAccountEmail = email
                        statusMessage = if (email.isNotBlank()) {
                            "✅ Đã nhận diện ChatGPT Access Token ($email)"
                        } else {
                            "✅ Đã nhận diện ChatGPT Access Token (JWT)"
                        }
                    }
                }
            }

            // Directly probe /api/auth/session if on chatgpt.com
            if (pageUrl.contains("chatgpt.com")) {
                val directFetchScript = """
                (function() {
                    fetch('/api/auth/session')
                        .then(function(r) { return r.json(); })
                        .then(function(d) {
                            if (d && d.accessToken) {
                                window.__chatgpt_session_json = JSON.stringify({
                                    accessToken: d.accessToken,
                                    email: (d.user && d.user.email) || ""
                                });
                            }
                        })
                        .catch(function() {});
                })();
                """.trimIndent()
                webView?.evaluateJavascript(directFetchScript, null)
            }
        }
    }

    LaunchedEffect(selectedTab, capturedToken) {
        if (selectedTab == 0 && capturedToken == null && !isGemini) {
            while (isActive && capturedToken == null) {
                delay(2000L)
                webViewInstance?.let { wb ->
                    triggerSessionScan(wb, currentUrl)
                }
            }
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "Đăng nhập $providerName",
        endAction = {
            TextButton(onClick = onDismissRequest) {
                Text("Đóng")
            }
        },
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isGemini) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "✨ Khuyên dùng: Chế độ Miễn phí (Zero-Auth)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Gemini Web hỗ trợ trò chuyện hoàn toàn miễn phí mà không cần đăng nhập tài khoản Google, không cần Cookie và không cần mã F12. Tất cả model Flash, Pro, Thinking đều hoạt động ngay!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    onLoginSuccess("", false)
                                    onDismissRequest()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("⚡ Kích hoạt ngay")
                            }
                            OutlinedButton(
                                onClick = {
                                    addingAsNewAccount = true
                                    selectedTab = 1
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("+ Thêm tài khoản")
                            }
                        }
                    }
                }
            }

            if (addingAsNewAccount) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "➕ Chế độ: Thêm tài khoản mới vào pool ($providerName)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Trình duyệt") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Dán Cookie / Token") },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {

            if (selectedTab == 0) {
                Text(
                    text = if (isGemini) {
                        "Đăng nhập tài khoản Google của bạn. Phiên đăng nhập (Cookie & SNlM0e) sẽ được tự động nhận diện."
                    } else {
                        "Đăng nhập tài khoản ChatGPT của bạn. Access Token và Cookie sẽ được tự động nhận diện."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGemini) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, if (isGemini) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                                                else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (isGemini) "⚠️ Google chặn đăng nhập tài khoản trên WebView nhúng"
                                   else "💡 Lưu ý quan trọng khi đăng nhập ChatGPT:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isGemini) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = if (isGemini) {
                                "Google không cho phép đăng nhập trên trình duyệt nhúng ứng dụng (\"Trình duyệt không an toàn\"). Vui lòng chuyển sang tab \"Dán Cookie / Token\" bên cạnh để kết nối qua Chrome ngoài."
                            } else {
                                "• KHÔNG bấm \"Continue with Google\" trong WebView này (Google sẽ chặn và báo lỗi 'Trình duyệt không an toàn').\n" +
                                "• Hãy đăng nhập bằng Email + Mật khẩu (hoặc gửi mã OTP về Email).\n" +
                                "• Nếu tài khoản của bạn đăng ký qua Google: Chuyển sang tab \"Dán Cookie / Token\" bên cạnh để lấy Access Token từ Chrome/Edge chỉ trong 10 giây!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Dùng tab Dán Cookie / Token 👉")
                        }
                    }
                }

                val hasCapturedSession = if (isGemini) {
                    !capturedCookie.isNullOrBlank() && capturedCookie!!.contains("__Secure-1PSID")
                } else {
                    !capturedToken.isNullOrBlank()
                }

                if (hasCapturedSession) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = statusMessage
                                    ?: if (isGemini) "✅ Đã nhận diện phiên đăng nhập Google & Gemini!"
                                       else if (!capturedToken.isNullOrBlank()) "✅ Đã nhận diện ChatGPT Access Token!"
                                       else "✅ Đã tìm thấy Cookie ChatGPT!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { webViewInstance?.reload() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Tải lại")
                                }
                                Button(
                                    onClick = {
                                        if (isGemini) {
                                            val cm = CookieManager.getInstance()
                                            val fullCookies = cm.getCookie(currentUrl) ?: cm.getCookie(loginUrl) ?: capturedCookie.orEmpty()
                                            val finalPayload = if (!capturedSnlm0e.isNullOrBlank()) {
                                                val json = JsonObject().apply {
                                                    addProperty("cookie", fullCookies)
                                                    addProperty("snlm0e", capturedSnlm0e)
                                                    addProperty("bl", capturedBl.orEmpty())
                                                }
                                                json.toString()
                                            } else {
                                                fullCookies
                                            }
                                            onLoginSuccess(finalPayload, addingAsNewAccount)
                                        } else {
                                            val finalCredential = capturedToken?.takeIf { it.isNotBlank() }
                                            if (!finalCredential.isNullOrBlank()) {
                                                onLoginSuccess(finalCredential, addingAsNewAccount)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(2f),
                                ) {
                                    Text(if (!capturedToken.isNullOrBlank()) "Sử dụng Token này" else "Sử dụng Phiên này")
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Đang tải trang…", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Text(
                                    text = "Chưa nhận diện được phiên đăng nhập",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { webViewInstance?.reload() }) {
                                    Text("Tải lại")
                                }
                                OutlinedButton(
                                    onClick = {
                                        triggerSessionScan(webViewInstance, currentUrl)
                                    },
                                ) {
                                    Text("Kiểm tra")
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                setBackgroundColor(Color.WHITE)
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                                val cm = CookieManager.getInstance()
                                cm.setAcceptCookie(true)
                                cm.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(true)
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    // Use Desktop Chrome UA to bypass Google OAuth "disallowed_useragent" ("Trình duyệt không an toàn")
                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }

                                // Suppress X-Requested-With header
                                if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                    WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        isLoading = newProgress < 100
                                    }

                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: android.os.Message?,
                                    ): Boolean {
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                        transport.webView = view
                                        resultMsg.sendToTarget()
                                        return true
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        if (url.startsWith("http://") || url.startsWith("https://")) {
                                            return false
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        url?.let { currentUrl = it }
                                        // Polyfill window.chrome and mask webdriver to bypass Google & Cloudflare bot detection
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                try {
                                                    Object.defineProperty(navigator, 'webdriver', {get: () => false});
                                                    if (!window.chrome) {
                                                        window.chrome = {
                                                            app: { isInstalled: false },
                                                            runtime: {},
                                                            loadTimes: function() {},
                                                            csi: function() {}
                                                        };
                                                    }
                                                } catch (e) {}
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        url?.let { pageUrl ->
                                            currentUrl = pageUrl
                                            triggerSessionScan(view, pageUrl)

                                            // Direct extraction from page body if session endpoint or JSON loaded
                                            view?.evaluateJavascript(
                                                "(function() { return document.body ? (document.body.innerText || document.body.textContent) : ''; })();"
                                            ) { body ->
                                                val unescaped = body?.trim('"')
                                                    ?.replace("\\\"", "\"")
                                                    ?.replace("\\\\", "\\")
                                                    ?.replace("\\n", "\n")
                                                    ?.replace("\\r", "")
                                                    .orEmpty()
                                                val token = ChatGptWebSessionManager.extractJwt(unescaped)
                                                if (token != null) {
                                                    capturedToken = token
                                                    statusMessage = "✅ Đã nhận diện ChatGPT Access Token (JWT)!"
                                                }
                                            }

                                            if (!isGemini) {
                                                val pollScript = """
                                                (function() {
                                                    if (window.__legado_chatgpt_poll) return;
                                                    function poll() {
                                                        fetch('/api/auth/session')
                                                            .then(function(r) { return r.json(); })
                                                            .then(function(d) {
                                                                if (d && d.accessToken) {
                                                                    window.__chatgpt_session_json = JSON.stringify({
                                                                        accessToken: d.accessToken,
                                                                        email: (d.user && d.user.email) || ""
                                                                    });
                                                                }
                                                            })
                                                            .catch(function() {});
                                                    }
                                                    poll();
                                                    window.__legado_chatgpt_poll = setInterval(poll, 2500);
                                                })();
                                                """.trimIndent()
                                                view?.evaluateJavascript(pollScript, null)
                                            }
                                        }
                                    }
                                }
                                webViewInstance = this
                                loadUrl(loginUrl)
                            }
                        },
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.destroy()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (isGemini) "📌 Hướng dẫn kết nối Gemini Web (nhanh gọn):" else "📌 Hướng dẫn kết nối ChatGPT Web:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (isGemini) {
                            Text(
                                text = "📱 CÁCH 1 (Trên điện thoại - Không cần F12):\n" +
                                       "1. Bấm nút '📑 1. Chép Bookmarklet' bên dưới.\n" +
                                       "2. Bấm '🌐 2. Mở Gemini' trên Chrome đã đăng nhập Google.\n" +
                                       "3. Tạo 1 Dấu trang (Bookmark) trên Chrome, đặt tên là 'cookie' và dán mã vừa sao chép vào ô URL.\n" +
                                       "4. Tại trang Gemini, chạm vào thanh địa chỉ Chrome, gõ 'cookie' và chạm vào Dấu trang -> Cookie & Token sẽ được tự động copy!\n" +
                                       "5. Quay lại đây bấm '📋 3. Dán từ bộ nhớ tạm' rồi bấm 'Áp dụng & Lưu'.\n\n" +
                                       "📱 CÁCH 2 (Dùng tiện ích): Mở Kiwi Browser / Firefox Android -> Cài Cookie-Editor -> Xuất JSON -> Dán vào đây.\n\n" +
                                       "💻 CÁCH 3 (Cho máy tính PC): Bấm '💻 Chép lệnh PC' -> Mở F12 (Console) trên Chrome -> Dán và nhấn Enter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "1. Bấm '🌐 1. Mở ChatGPT' để đăng nhập trên Chrome/Edge (hỗ trợ cả đăng nhập bằng Google).\n" +
                                       "2. Bấm '🔑 2. Lấy Access Token' để mở trang session.\n" +
                                       "3. TRÊN CHROME: Chạm giữ màn hình -> Bấm menu (⋮) -> Chọn 'Chọn tất cả' (Select All) -> 'Sao chép' (Copy).\n" +
                                       "4. Quay lại đây bấm '📋 3. Dán từ bộ nhớ tạm' rồi bấm 'Áp dụng & Lưu'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                var copyConsoleSuccessNotice by remember { mutableStateOf<String?>(null) }

                if (!isGemini) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { context.openUrl("https://chatgpt.com") },
                        ) {
                            Text("🌐 1. Mở ChatGPT")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { context.openUrl("https://chatgpt.com/api/auth/session") },
                        ) {
                            Text("🔑 2. Lấy Token")
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            clipboardManager.getText()?.text?.let { text ->
                                if (text.isNotBlank()) {
                                    manualCookieText = text.trim()
                                }
                            }
                        },
                    ) {
                        Text("📋 3. Dán từ bộ nhớ tạm")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1.2f),
                            onClick = {
                                val bookmarklet = """javascript:(function(){var c=document.cookie;var w=window.WIZ_global_data||{};var d=JSON.stringify({cookie:c,snlm0e:w.SNlM0e||"",bl:w.cfb2h||""});if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(d).then(function(){alert("✅ Đã sao chép Cookie & Token Gemini vào bộ nhớ tạm! Hãy quay lại DrDucBook và bấm Dán.");}).catch(function(){prompt("Sao chép đoạn mã này:",d);});}else{prompt("Sao chép đoạn mã này:",d);}})();"""
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(bookmarklet))
                                copyConsoleSuccessNotice = "✅ Đã sao chép mã Bookmarklet! Mở Chrome điện thoại -> Tạo Dấu trang với URL là mã này."
                            },
                        ) {
                            Text("📑 1. Chép Bookmarklet")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { context.openUrl("https://gemini.google.com/app") },
                        ) {
                            Text("🌐 2. Mở Gemini")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val script = """copy(JSON.stringify({cookie: document.cookie, snlm0e: (window.WIZ_global_data && window.WIZ_global_data.SNlM0e) || "", bl: (window.WIZ_global_data && window.WIZ_global_data.cfb2h) || ""}))"""
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(script))
                                copyConsoleSuccessNotice = "✅ Đã sao chép lệnh Console cho PC! Hãy mở F12 (Console) trên Chrome, dán lệnh và nhấn Enter."
                            },
                        ) {
                            Text("💻 Chép lệnh PC (F12)")
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                clipboardManager.getText()?.text?.let { text ->
                                    if (text.isNotBlank()) {
                                        manualCookieText = text.trim()
                                    }
                                }
                            },
                        ) {
                            Text("📋 3. Dán từ tạm")
                        }
                    }
                }

                val isTruncated = remember(manualCookieText, isGemini) {
                    !isGemini && ChatGptWebSessionManager.isJwtTruncated(manualCookieText)
                }
                val validJwt = remember(manualCookieText, isGemini) {
                    if (!isGemini) ChatGptWebSessionManager.extractJwt(manualCookieText) else null
                }

                val detectedBadge = remember(manualCookieText, isGemini, isTruncated, validJwt) {
                    val trimmed = manualCookieText.trim()
                    when {
                        trimmed.isBlank() -> null
                        !isGemini && validJwt != null ->
                            Triple("✅ Đã nhận diện Access Token ChatGPT hợp lệ (JWT)!", true, false)
                        !isGemini && isTruncated ->
                            Triple("⚠️ Access Token bị cắt ngắn khi copy (thiếu phần thân/chữ ký)!\n👉 Vui lòng vào lại Chrome, chạm giữ màn hình -> bấm menu (⋮) -> 'Chọn tất cả' (Select All) -> 'Sao chép' rồi dán lại vào đây.", false, true)
                        !isGemini && (trimmed.contains("__Secure-next-auth.session-token") || trimmed.startsWith("[")) ->
                            Triple("ℹ️ Đã nhận diện Cookie phiên ChatGPT. Bấm Áp dụng để nạp vào trình duyệt và tự động lấy Token.", true, false)
                        isGemini && trimmed.startsWith("{") && (trimmed.contains("snlm0e", ignoreCase = true) || trimmed.contains("cookie", ignoreCase = true)) ->
                            Triple("✅ Đã nhận diện gói đăng nhập Gemini đầy đủ (Cookie + Token SNlM0e)!", true, false)
                        isGemini && trimmed.contains("__Secure-1PSID") && !trimmed.contains("__Secure-1PSIDTS") ->
                            Triple("ℹ️ Nhận diện Cookie có __Secure-1PSID (thiếu __Secure-1PSIDTS). Nếu kết nối thất bại, hãy sao chép toàn bộ Cookie (document.cookie) hoặc dùng lệnh Console.", true, false)
                        isGemini && trimmed.contains("__Secure-1PSID") ->
                            Triple("✅ Đã nhận diện Google Cookie hợp lệ (__Secure-1PSID)!", true, false)
                        isGemini && trimmed.startsWith("[") && trimmed.contains("__Secure-1PSID") ->
                            Triple("✅ Đã nhận diện Cookie-Editor JSON chứa __Secure-1PSID!", true, false)
                        isGemini && trimmed.startsWith("g.a000") ->
                            Triple("ℹ️ Nhận diện token __Secure-1PSID thô. Khuyên dùng lệnh Console để lấy đầy đủ cả Cookie và Token SNlM0e.", true, false)
                        else -> null
                    }
                }

                if (detectedBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (detectedBadge.third) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                else if (detectedBadge.second) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (detectedBadge.third) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                                    else if (detectedBadge.second) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                    else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = detectedBadge.first,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (detectedBadge.third) MaterialTheme.colorScheme.error
                                    else if (detectedBadge.second) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                AppTextField(
                    value = manualCookieText,
                    onValueChange = { manualCookieText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = if (isGemini) "Google Cookie (__Secure-1PSID hoặc JSON)" else "Access Token (eyJ...) hoặc JSON Session",
                    minLines = 3,
                    maxLines = 6,
                )

                Button(
                    onClick = {
                        val text = manualCookieText.trim()
                        if (text.isNotBlank()) {
                            val directJwt = if (!isGemini) ChatGptWebSessionManager.extractJwt(text) else null
                            if (directJwt != null) {
                                onLoginSuccess(directJwt, addingAsNewAccount)
                            } else if (!isGemini && (text.contains("__Secure-next-auth.session-token") || text.contains("=") || text.startsWith("["))) {
                                // Inject cookies into CookieManager and switch to Tab 0 so visible WebView can obtain the token
                                val cm = CookieManager.getInstance()
                                cm.setAcceptCookie(true)
                                val pairs = if (text.startsWith("[")) {
                                    runCatching {
                                        val arr = GSON.fromJson(text, JsonArray::class.java)
                                        arr.mapNotNull {
                                            val obj = it.asJsonObject
                                            val n = obj.get("name")?.asString
                                            val v = obj.get("value")?.asString
                                            if (!n.isNullOrBlank() && v != null) "$n=$v" else null
                                        }
                                    }.getOrDefault(emptyList())
                                } else {
                                    text.split(";")
                                }
                                for (p in pairs) {
                                    val tr = p.trim()
                                    if (tr.isNotBlank()) cm.setCookie("https://chatgpt.com", tr)
                                }
                                cm.flush()
                                selectedTab = 0
                                webViewInstance?.loadUrl("https://chatgpt.com/api/auth/session")
                            } else {
                                onLoginSuccess(text, addingAsNewAccount)
                            }
                        }
                    },
                    enabled = manualCookieText.isNotBlank() && !isTruncated,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (addingAsNewAccount) "💾 Lưu tài khoản mới vào pool" else "Áp dụng & Lưu")
                }

                if (isGemini) {
                    OutlinedButton(
                        onClick = {
                            onLoginSuccess("", false)
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("⚡ Dùng Chế độ Miễn phí (Không cần Cookie)")
                    }
                }
            }
        }
    }
}
}
