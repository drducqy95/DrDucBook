# P11.T04 - Performance, accessibility va security audit checkpoint

## Muc tieu

Loai regression phi chuc nang truoc rollout: performance/DB/media/download/source-health, accessibility/static lint, va security gates cho secret, WebView SSL, cookie/token/path traversal, Agent sandbox va cloud delivery.

## Trang thai

IN_PROGRESS. Da chay va sua duoc cac checkpoint security/static local. `:app:lintAppDebug` final da PASS voi 0 fatal/error; tiep tuc giam warning UI/i18n an toan, sua theme surface ramp va export ebook image alias. Chua dong DONE vi warning backlog localization/performance lon va chua co device metrics startup/memory/battery/ANR/OOM.

## Pham vi checkpoint

- Security secret scan va cloud security Node gates.
- Focused unit tests cho:
  - import archive path traversal;
  - diagnostic/audit redaction;
  - Agent permission/custom tool sandbox;
  - cookie security;
  - VBook importer security;
  - media download transfer/recovery state;
  - source health worker/processor;
  - responsive navigation state.
- Static WebView SSL audit.
- Lint AppDebug blocking gate va fix cac loi fatal/error ro rang trong media player/reader.
- Lint backlog source cleanup cho `EmptySuperCall` va Kotlin compile warnings trong chat.

## Thay doi da thuc hien

- `CustomAgentToolManifestRuntimeTest`:
  - Doi fixture token gia `hf_...` thanh `sk-testsecretplaceholder0000`.
  - Van giu test secret-literal rejection qua `SECRET_PATTERN`, nhung khong lam secret scanner nham voi HF token that.
- `MediaPlayerRouteScreen`, `MediaPlaybackService` va `ResolvedMediaPlayer`:
  - Doi Media3 unstable usage sang AndroidX `@OptIn(UnstableApi::class)` tai implementation boundary de khong day `UnsafeOptInUsageError` ra call site.
  - Fix nhom lint errors `UnsafeOptInUsageError` trong media player, playback service va subtitle styling.
- WebView SSL:
  - `RssReadWebController`: cancel SSL error thay vi proceed.
  - `WebViewLoginFragment`: cancel SSL error thay vi proceed.
  - `BackstageWebView`: cancel SSL error thay vi proceed trong ca 2 WebView client.
  - `BottomWebViewDialog`: cancel SSL error thay vi proceed.
  - `WebViewActivity` va `BrowserRouteScreen` da cancel tu truoc.
- `ReadBookRouteScreen`:
  - Doi cursor handle chon van ban tu `ImageView(context)` sang `TextSelectionCursorView` co override `performClick()`.
  - Doi `TextSelectionCursorView` ke thua `AppCompatImageView` de dat AppCompat custom view lint gate.
  - Giu `ReadBookController` goi `v.performClick()` o `ACTION_UP`, de TalkBack/accessibility action co duong click hop le ma khong pha thao tac keo chon.
- `FadePageDelegate`:
  - Doi `Paint()` tao trong `onDraw()` thanh `fadePaint` dung lai de giam allocation trong animation doc sach.
- `appModule` / `SourceCheckEngine`:
  - Sua crash debug khi mo reader tren Android 12/Huawei: `ReadBookViewModel` -> `GenerateChapterSummaryUseCase` -> `AiToolAwareGenerationUseCase` -> `AiToolGateway` -> `SourceCheckEngine` fail vi Koin tim `kotlin.jvm.functions.Function0`.
  - Doi `singleOf(::SourceCheckEngine)` thanh dang ky thu cong va truyen du 4 dependency chinh, khong de Koin tu resolve tham so default `now: () -> Long`.
- ViewModel cleanup:
  - Xoa 14 loi/canh bao `EmptySuperCall` stale lint report dang neu trong cac `onCleared()` UI ViewModel; cac cleanup rieng nhu cancel job, close pool, stop read aloud van giu nguyen.
  - Sua 2 Kotlin compile warnings trong `AiChatViewModel`: bo `!!` khong can thiet va bo dieu kien null luon dung.
- Lint UI/i18n cleanup nho:
  - Dua cac chuoi mau/phan tach UI sang resource non-translatable thay vi hardcode trong layout.
  - Doi mot so margin/padding/Gravity `Left/Right` sang `Start/End` khi khong phu thuoc toa do vat ly.
  - Doi `String.format`/case conversion sang `Locale.ROOT` cho cac doan format tien do, scale va parser module book.
  - Sua `EmptyMessageView` dung `setText(R.string.empty)` thay vi gan literal id resource.
- Theme surface ramp:
  - Them `ThemeSurfaceAdjustments` de AMOLED va Transparent co day du cac muc `surfaceContainerLowest/Low/Container/High/Highest`, `surfaceDim`, `surfaceBright` dong nhat.
  - Ap dung chung cho `ThemeEngine` va `ThemeOverride`, tranh hien tuong card/panel/bottom sheet lech mau dot ngot giua cac khoi.
- Export ebook image alias:
  - `collectExportImages` luu URL tuyet doi lam source chinh va giu duong dan tuong doi lam alias.
  - `EbookExportWriter` thay the ca source chinh va alias khi ghi EPUB/HTML, giup anh truyen tranh khong bi lot ve reader/HTML cu hoac mat anh trong file xuat.
- Export local file path:
  - `FileDoc.fromDir/fromFile/asFile` dung `Uri.fromFile` va bo giai ma local file path rieng cho filesystem paths/`file://` Uris.
  - Regression nay sua loi JVM/Windows lam `uri.path` rong khi export EPUB voi anh local, dan den chon xuat nhung khong tao duoc file hop le.

## Lenh da chay

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\security\scan-secrets.ps1
node --test scripts\test-cloud-security-gates.mjs
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.security.*" --tests "io.legado.app.help.http.CookieManagerSecurityTest" --tests "io.legado.app.help.config.ThemePackageSecurityPolicyTest" --tests "io.legado.app.help.vbook.VbookPluginImporterSecurityTest" --tests "io.legado.app.domain.agent.AgentAuditSanitizerTest" --tests "io.legado.app.security.AgentPermissionSecurityTest" --tests "io.legado.app.data.repository.AiAgentRepositoryAuditTest" --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --tests "io.legado.app.data.repository.MediaDownloadRepositoryTest" --tests "io.legado.app.ui.media.download.MediaDownloadsStateTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.ui.main.MainResponsiveNavigationTest" --tests "io.legado.app.ui.main.MainDestinationTest" --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --console=plain
rg -n "handler\?\.proceed\(\)|SslErrorHandler.*proceed" app\src\main\java
.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon
.\gradlew.bat :app:lintAppDebug --console=plain
.\gradlew.bat :app:lintAppDebug --console=plain --no-daemon
rg -n "ImageView\(context\)\.apply|val paint = Paint\(\)|Paint\(\)\.apply \{ this\.alpha = alpha \}" app\src\main\java\io\legado\app\ui\book\read\ReadBookRouteScreen.kt app\src\main\java\io\legado\app\ui\book\read\page\delegate\FadePageDelegate.kt
rg -n "TextSelectionCursorView|v\.performClick\(\)|fadePaint" app\src\main\java\io\legado\app\ui\book\read\ReadBookRouteScreen.kt app\src\main\java\io\legado\app\ui\book\read\ReadBookController.kt app\src\main\java\io\legado\app\ui\book\read\page\delegate\FadePageDelegate.kt
Get-Process java -ErrorAction SilentlyContinue | Select-Object Id,ProcessName,StartTime,CPU
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest" --console=plain --no-daemon
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --console=plain --no-daemon
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --console=plain --no-daemon
.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon
adb -s emulator-5554 install -r -t app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk
adb -s emulator-5554 shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity --es startRoute book/read
adb -s emulator-5554 logcat -d -t 800
.\gradlew.bat :app:lintAppDebug --console=plain --no-daemon
rg -n "super\.onCleared\(\)" app/src/main/java/io/legado/app/ui
.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon
.\gradlew.bat :app:lintAppDebug --console=plain --no-daemon
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiRouterRepositoryTest" --console=plain --no-daemon
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.export.EbookExportWriterTest" --tests "io.legado.app.ui.theme.ThemeEngineTest" --console=plain --no-daemon
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.export.EbookExportScopeTest" --console=plain --no-daemon
.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon
.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon
adb -s emulator-5554 install -r -t app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk
adb -s emulator-5554 shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity
adb -s emulator-5554 logcat -d -t 500
```

## Ket qua

- Secret scan lan dau FAIL vi fixture test co chuoi `hf_abcdefghijklmnopqrstuvwxyz`.
- Sau fix fixture:
  - Secret scan PASS: 3 allow-listed Bearer-token fixtures, 0 unapproved.
  - `CustomAgentToolManifestRuntimeTest` PASS.
- Node cloud security gates PASS: 6/6.
- Focused audit unit suite PASS in 31s.
- `rg` SSL proceed scan PASS: khong con `handler?.proceed()` trong `app/src/main/java`.
- Kotlin compile PASS in 1m36s sau WebView SSL va Media3 opt-in fixes cuoi cung.
- Lint AppDebug:
  - lan 1 timeout sau 5 phut nhung sinh report cu co 8 errors/2997 warnings/26 hints;
  - 8 errors deu la `UnsafeOptInUsageError` trong `MediaPlayerRouteScreen`, da fix bang annotation truc tiep `@UnstableApi`;
  - lan 2 timeout sau 10 phut, report khong cap nhat, nen full lint clean gate van pending.
- Accessibility/performance source-level checkpoint:
  - `:app:compileAppDebugKotlin --console=plain --no-daemon` PASS in 2m11s sau fix cursor/fade.
  - Static scan khong con `ImageView(context).apply`, `val paint = Paint()` hoac `Paint().apply { this.alpha = alpha }` tai hai file vua sua.
  - Static scan xac nhan `TextSelectionCursorView`, `v.performClick()` va `fadePaint` dang duoc noi dung.
  - Khong con tien trinh Java sau compile.
- Reader Koin crash checkpoint:
  - Crash log tren Huawei/Android 12 cho thay root cause la `NoDefinitionFoundException: kotlin.jvm.functions.Function0` khi tao `SourceCheckEngine` qua Koin.
  - `:app:compileAppDebugKotlin` PASS sau fix DI.
  - `SourceCheckEngineTest` PASS in 49s.
  - `BookSourceHealthCheckProcessorTest` PASS in 49s.
  - `AiToolRepositoryToolCatalogTest` PASS in 39s.
  - `:app:assembleAppDebug` PASS in 5m16s.
  - LDPlayer install x86_64 PASS va direct `book/read` route smoke khong con `NoDefinitionFoundException`, `Could not create instance`, `ReadBookViewModel` crash hoac `Function0` trong logcat.
  - Luu y: mot lan chay Gradle test song song da FAIL do hai test task ghi chung `test-results` binary; da dung chay song song Gradle va rerun tung test noi tiep PASS.
- Lint backlog cleanup checkpoint 2026-08-01 16:36:
  - `:app:lintAppDebug --console=plain --no-daemon` van timeout sau 20 phut va khong cap nhat `lint-results-appDebug.*` (report van la timestamp 2026-08-01 06:23).
  - `.\gradlew.bat --stop` dung 1 daemon; Java process tu thoat sach sau do.
  - Static scan `super.onCleared()` trong `app/src/main/java/io/legado/app/ui` khong con match.
  - `:app:compileAppDebugKotlin --console=plain --no-daemon` PASS in 1m14s sau cleanup va khong con 2 Kotlin warnings `AiChatViewModel` vua sua.
  - `:app:assembleAppDebug --console=plain --no-daemon` PASS; x86_64 APK install len LDPlayer PASS; app launch smoke PASS va logcat 500 dong gan nhat khong co crash/Koin/translation parse fatal.
- Lint blocking gate final 2026-08-02 00:29 local:
  - `:app:compileAppDebugKotlin --console=plain --no-daemon` PASS sau khi doi Media3 sang AndroidX `@OptIn` va cursor sang `AppCompatImageView`.
  - `:app:lintAppDebug --console=plain --no-daemon` PASS in 39m10s.
  - Report `app/build/reports/lint-results-appDebug.xml` moi co 3024 issues: 0 fatal/error, 2998 warnings, 26 hints.
- Lint/theme/export checkpoint 2026-08-02 07:35 local:
  - Sau cleanup UI/i18n scoped, `:app:compileAppDebugKotlin --console=plain --no-daemon` PASS.
  - `:app:lintAppDebug --console=plain --no-daemon` PASS in 41m05s.
  - Lint report moi co 2995 issues: 0 fatal/error, 2969 warnings, 26 hints. Top backlog con lai: `MissingTranslation` 1293, `UnusedResources` 1217, `UseKtx` 119, `PluralsCandidate` 114, `ModifierParameter` 43, `Overdraw` 25.
  - `AiRouterRepositoryTest` PASS cho provider API-key pool follow-up.
  - `EbookExportWriterTest` + `ThemeEngineTest` PASS; regression moi xac nhan EPUB/HTML rewrite ca URL anh tuyet doi va duong dan tuong doi, dong thoi khoa lai ramp AMOLED/Transparent.
  - `EbookExportScopeTest` PASS; `:app:compileAppDebugKotlin` PASS sau theme/export fixes.
  - `:app:assembleAppDebug --console=plain --no-daemon` PASS in 5m10s.
  - APK debug moi: x86_64 SHA-256 `ABAD1D53E41B734D060F0F2B83F8C2B71349ED82B38095CBC82005777AF6C159`; universal SHA-256 `FE9D3ECC10B9621C1E78436D0A676D7CD9585C6F5E297B81F9AE0F9DE7B92191`.
  - LDPlayer `emulator-5554` install x86_64 PASS; launch `MainActivity` PASS; app process con song sau 6 giay; logcat 500 dong gan nhat khong co `FATAL EXCEPTION`, `AndroidRuntime`, Koin `NoDefinitionFoundException`, `Could not create instance`, `EbookExport` hoac `ThemeEngine` crash.
- Export/local path checkpoint 2026-08-02 11:51 local:
  - `ExportAuthoringProjectUseCaseTest.epubExportPackagesEscapedLocalImageReferences` PASS sau khi `FileDoc` xu ly dung filesystem paths/`file://` Uris tren Windows.
  - Full `:app:testAppDebugUnitTest --no-daemon --console=plain` PASS: 944 tests, 0 failures, 0 errors, 1 skipped.
  - `:app:assembleAppDebug --console=plain --no-daemon` PASS in 2m03s; cai x86_64 debug APK len `emulator-5554` PASS; launch smoke khong co crash match trong logcat 700 dong gan nhat.
  - APK debug latest: x86_64 SHA-256 `566E5F51ADC95AFD3E18D85282611CD9F15378922F4EB778288038D602DFBD90`; universal SHA-256 `A45E63380E69F3B010AFE75880F08370475316DC3D9C526A9134766C95D39F06`.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportWriterTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportScopeTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.theme.ThemeEngineTest.xml`
- `app/build/reports/lint-results-appDebug.txt`
- `app/build/reports/lint-results-appDebug.xml`
- Static source scan:
  - khong con `handler?.proceed()`;
  - khong con direct `@UnstableApi` propagation trong media player/service/player helper;
  - khong con fake `hf_...` token trong custom Agent tool fixture.
  - khong con cursor handle tao bang `ImageView(context).apply` trong `ReadBookRouteScreen`.
  - `TextSelectionCursorView` ke thua `AppCompatImageView`.
  - khong con `Paint()` allocation trong `FadePageDelegate.onDraw()`.
  - khong con `singleOf(::SourceCheckEngine)` trong `appModule`; `SourceCheckEngine` duoc tao thu cong.
  - khong con `super.onCleared()` trong `app/src/main/java/io/legado/app/ui`.
  - `ThemeEngine` va `ThemeOverride` dung chung AMOLED surface ramp.
  - `EbookExportWriter` thay image references bang source + alias truoc khi ghi EPUB/HTML.
  - `FileDoc.fromDir/fromFile/asFile` khong con phu thuoc `uri.path!!` cho filesystem path co drive letter.
- Secret scan output: PASS voi 3 allow-listed, 0 unapproved sau fix.
- Node output: 6 cloud security tests PASS.
- Device smoke output: LDPlayer `emulator-5554` mo route `book/read` khong thay Koin/FATAL match trong logcat 800 dong cuoi.
- Device smoke output 2026-08-02 07:42: LDPlayer `emulator-5554` cai x86_64 debug moi va mo `MainActivity` khong thay crash match trong logcat 500 dong cuoi.
- APK moi:
  - `app-app-x86_64-debug.apk` SHA-256 moi nhat `5E32888308BBF392B7D41D356FB4A9025EBFD0BB714B200AC423106B0BED64A2`.
  - `app-app-universal-debug.apk` SHA-256 `FFD9C2DFADC0CC38A2BE99DC7A8AABF2BFA4B9280312286B4AB5FD647BBF3AFD`.
  - `app-app-arm64-v8a-debug.apk` SHA-256 `97025BA1EEEB236E66254883086731DF48A576CCD21B66A3A3832BEC3A0795BE`.

## Rui ro/cong viec con lai

- P11.T04 van IN_PROGRESS:
  - can triage warning backlog accessibility/performance/localization con lai trong lint report moi, dac biet `HardcodedText`, `Rtl*`, `MissingTranslation` va cac warning performance/compose khac;
  - can device/instrumented metrics cho startup, memory, battery, DB/source-health/media/download va ANR/OOM smoke.
- Reader Koin crash da het tren LDPlayer smoke, nhung can nguoi dung cai APK moi len may Huawei HBN-LX9 de xac nhan cung data that tren thiet bi bao loi.
- SSL trust-all trong HTTP stack (`SSLHelper`/`HttpHelper`) can mot quyet dinh rieng: legacy source compatibility co the can tuy chon rieng, nhung rollout security khong nen silently trust all neu khong co user/source policy.

## Checkpoint 2026-08-02 09:25 - Export file visibility and install

- `DocumentUtils.createFileIfNotExist()` tao file SAF bang MIME type theo duoi file thay vi mime rong, de file EPUB/PDF/HTML/TXT/CBZ co the hien dung trong document provider sau khi xuat.
- `:app:assembleAppDebug` PASS; cai `app-app-x86_64-debug.apk` len `emulator-5554` PASS.
- Android restore app vao `WebViewActivity` dang mo truoc do, khong bao install/runtime error trong buoc mo activity.
- APK 2026-08-02 09:24: x86_64 SHA-256 `1091ADAB7EE51C72D5C86DBA2B84A367B83FAA13D6A3A4B2154BAA290A34F529`; universal SHA-256 `B5497F40692091A66861FACA7F50735C7BCF0FAC6CF1FA575D1DAE2FD140B514`.

## Checkpoint 2026-08-01 17:03 - Reader Debug Crash Hotfix 2

- Root cause moi sau khi fix Koin: `EffectiveReplacesSheet` doc truc tiep `ReadConfig.chineseConverterType` trong composition, gay `IllegalStateException: Reading a state that was created after the snapshot was taken`.
- Thay doi:
  - `SourceCheckEngine`: dua `now: () -> Long` ra khoi primary constructor va them constructor test-only de tranh moi dang ky Koin auto tiep tuc co the resolve `Function0`.
  - `SourceCheckEngineTest`: them regression test tao engine qua Koin voi constructor 4 tham so ro rang.
  - `EffectiveReplacesSheet`: nhan `chineseConverterType` tu state man doc thay vi doc truc tiep `ReadConfig` trong composition.
  - `ReadBookScreen`: truyen `preferences.chineseConverterType` vao `EffectiveReplacesSheet`.
- Lenh/xac minh:
  - `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --console=plain --no-daemon` PASS.
  - `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon` PASS.
  - `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon` PASS.
  - Cai `app-app-x86_64-debug.apk` len LDPlayer PASS; mo truc tiep `startRoute=book/read` sau 8 giay van o `MainActivity`, khong mo `CrashReportActivity`, khong sinh file crash moi.
- APK moi:
  - `app/build/outputs/apk/app/debug/app-app-arm64-v8a-debug.apk` SHA-256 `E72CCAF17534E5FC5A256488439FEA60946E10F4A666BF670254581EDA8E2419`.
  - `app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk` SHA-256 `406F18372A844F3B7030D574604BF1C95D9BA970C824C08091C1395CEF29FE3F`.
  - `app/build/outputs/apk/app/debug/app-app-universal-debug.apk` SHA-256 `6969DB420D7E7E7C2CEFD6C9C2ACC6A2908B9E06715FDB83A454FD1159B20180`.
