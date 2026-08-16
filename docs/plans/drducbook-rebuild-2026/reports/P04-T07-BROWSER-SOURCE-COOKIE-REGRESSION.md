# P04.T07 - Browser/source/cookie regression tests

## Ket qua

Trang thai: `DONE`

## Trien khai

- Khoa regression cho source index va Browser tab/session: process death, multiple tabs, malicious URL policy, same-host source disambiguation va back target policy.
- Khoa regression cho CookieVault: path/expiry, secure cookie, host-only scope, fail-closed khi value khong giai ma duoc, va removeCookie clear scope.
- Khoa regression cho targeted health: check all-enabled, check 1 source target va no-op khi source target khong ton tai.
- Them instrumented smoke tren emulator cho browser cookie bridge: sync tu WebView vao scope source, apply tu vault ra WebView, va logout/clear cookie xoa duoc scope.
- Fix androidTest namespace regression `HttpTest.kt` de instrumented suite build duoc sau rebrand.

## Verification

- `:app:compileAppDebugKotlin`: PASS.
- `:app:testAppDebugUnitTest --tests "io.legado.app.domain.model.SourceDomainIndexTest" --tests "io.legado.app.ui.browser.BrowserTabStoreTest" --tests "io.legado.app.ui.browser.BrowserBackPolicyTest" --tests "io.legado.app.ui.browser.BrowserHomeDataTest" --tests "io.legado.app.data.repository.BrowserBookmarkRepositoryTest" --tests "io.legado.app.data.cookie.CookieVaultRepositoryTest" --tests "io.legado.app.help.http.CookieManagerSecurityTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest"`: PASS.
- `:app:compileAppDebugAndroidTestKotlin`: PASS.
- `:app:connectedAppDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.integration.SourceBrowserIntegrationTest`: PASS.
- `SourceBrowserIntegrationTest`: 2 tests, 0 failure, 0 error, 0 skipped.

## Bang chung

- `app/src/test/java/io/legado/app/domain/model/SourceDomainIndexTest.kt`
- `app/src/test/java/io/legado/app/ui/browser/BrowserTabStoreTest.kt`
- `app/src/test/java/io/legado/app/data/cookie/CookieVaultRepositoryTest.kt`
- `app/src/test/java/io/legado/app/worker/BookSourceHealthCheckProcessorTest.kt`
- `app/src/androidTest/java/io/legado/app/integration/SourceBrowserIntegrationTest.kt`
- `app/src/androidTest/java/io/legado/app/HttpTest.kt`
- `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`
- `app/build/outputs/androidTest-results/connected/debug/flavors/app/emulator-5554 - 14/testlog/test-results.log`

## Rui ro con lai

- SSL block va download listener con duoc bao phu boi browser runtime logic, nhung chua co mot instrumented test rieng cho tung nhan; can giu watch o phase sau.
- `HttpTest.kt` van la smoke device co tinh chat thu cong; neu no phat sinh doan deprecation hoac timeout, co the tach thanh helper test rieng hon.
