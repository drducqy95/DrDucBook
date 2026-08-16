# P04.T05 - Dong bo cookie WebView/OkHttp/Cronet/Rhino/VBook

## Ket qua

Trang thai: `DONE`

## Trien khai

- Gom apply/capture cookie WebView vao `CookieManager.applyToWebView()` va `CookieManager.syncFromWebView()`, bo luong `removeSessionCookies()` toan cuc.
- Noi Browser route, WebViewActivity va WebViewLoginFragment vao helper chung de load cookie truoc khi mo trang va capture cookie sau page finish/destroy.
- Noi `BackstageWebView` vao helper chung: apply cookie truoc khi load, capture cookie sau khi response/page finish de giu preload JS va HTML bridge dong bo voi vault.
- Them capture cookie cho RSS reader WebView va BottomWebViewDialog, bao gom persist `Set-Cookie` tu response vao vault.
- VBook plugin fetch doc cookie tu `CookieStore` theo URL request va tu dong them `CookieJar` header de OkHttp/Cronet path nhan biet can sync cookie.
- Cac runtime bridge van giu compatibility cho Legado/VBook ext/plugin va khong doi public JS contract cu.

## Verification

- `:app:compileAppDebugKotlin`: PASS.
- `:app:testAppDebugUnitTest --tests "io.legado.app.data.cookie.CookieVaultRepositoryTest" --tests "io.legado.app.help.http.CookieManagerSecurityTest"`: PASS.
- `CookieVaultRepositoryTest`: 3 tests, 0 failure.
- `CookieManagerSecurityTest`: 1 test, 0 failure.

## Bang chung

- `app/src/main/java/io/legado/app/help/http/CookieManager.kt`
- `app/src/main/java/io/legado/app/help/http/BackstageWebView.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserRouteScreen.kt`
- `app/src/main/java/io/legado/app/ui/browser/WebViewActivity.kt`
- `app/src/main/java/io/legado/app/ui/login/WebViewLoginFragment.kt`
- `app/src/main/java/io/legado/app/ui/rss/read/RssReadWebController.kt`
- `app/src/main/java/io/legado/app/ui/widget/dialog/BottomWebViewDialog.kt`
- `app/src/main/java/io/legado/app/help/vbook/VbookExecutor.kt`
- `app/src/test/java/io/legado/app/data/cookie/CookieVaultRepositoryTest.kt`
- `app/src/test/java/io/legado/app/help/http/CookieManagerSecurityTest.kt`

## Rui ro con lai

- Chua co device smoke test tren Browser/WebView/RSS/VBook thuc te cho tat ca account luong cookie, nen van can P04.T06/P04.T07 kiem tra target flow.
- `CronetCoroutineInterceptor` con ton tai nhung khong co call-site; path Cronet dang dung la `CronetInterceptor`.
- `BottomWebViewDialog` va WebView helper van phu thuoc cookie API cua WebView runtime; neu engine doi hanh vi cookie, can regression test device.
