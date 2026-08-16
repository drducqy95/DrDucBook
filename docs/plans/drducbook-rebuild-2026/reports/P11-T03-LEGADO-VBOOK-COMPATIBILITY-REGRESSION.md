# P11.T03 - Legado/VBook compatibility regression hotfix

## Muc tieu

Khac phuc loi VBook Ext/registry: danh sach bi co con rat it muc va nhieu ext da import nhung khong chay duoc.

## Thay doi

- `app/src/main/java/io/legado/app/data/repository/vbook/VbookRegistryParser.kt`
  - Nho schema registry tuan thu hon: nhan nhieu alias cho `name/author/path/source/type/version/icon/locale`.
  - Tu suy ra `source` tu host URL tai xuong khi registry khong ghi ro.
  - Khong loai ext chi vi thieu `version` hoac `type` trong khi van co `name` va `path` hop le.
- `app/src/main/java/io/legado/app/domain/model/VbookRegistryModels.kt`
  - Mo rong suy dien `VbookPluginKind` cho nhieu alias thong dung cua VBook/Legado.
- `app/src/main/java/io/legado/app/help/vbook/VbookPluginImporter.kt`
  - Cho phep `src` co thu muc con.
  - Chi kiem tra script duoc khai bao khi plugin co `encrypt=true`.
  - Cho phep install duoc plugin bi lech metadata/ID, de luu alias ve plugin that sau khi import.
- `app/src/main/java/io/legado/app/help/vbook/VbookPluginAliasStore.kt`
  - Luu alias `registryId -> installedPluginId` trong `filesDir` de preview va da cai dong bo lai.
- `app/src/main/java/io/legado/app/help/vbook/VbookPluginInspector.kt`
  - Quet script trong cay `src` thay vi chi doc cap tren cung.
  - Doc script qua `ScriptLoader` de inspector khop runtime.
- `app/src/main/java/io/legado/app/help/vbook/VbookExecutor.kt`
  - Cho phep ten script nested an toan.
  - Gia nen `Engine.newBrowser()` thanh pseudo-browser de nhieu ext khong crash ngay khi can `launch/html/urls/callJs` co ban.
  - Thu lai doc script plain JS neu package khai `encrypt=true` nhung file phu khong ma hoa.
  - Bo sung `Base64`/`btoa`/`atob` compatibility va fallback `base64.js` cho ext thieu file helper.
  - Giai ma response theo charset khai bao/detect duoc, khong ep UTF-8 voi nguon GBK/legacy.
- `app/src/main/java/io/legado/app/help/vbook/VbookPluginAdapter.kt`
  - Nhap nhieu field tra ve hon cho book list/chapters.
  - Chup nhan envelope kieu `success=true`, `code=0`, `JSONArray` hoac object co `data/result/items`.
  - Bo qua row `section`, nhan them alias URL tap/chuong va content envelope cu kieu `images/items/list/body/chapter`.
- `app/src/test/java/io/legado/app/help/vbook/VbookExecutorTest.kt`
  - Khoa regression charset GBK va loader `base64.js` fallback.
- `app/src/test/java/io/legado/app/help/vbook/VbookPluginAdapterTest.kt`
  - Khoa regression danh sach chuong co `section` va content anh trong object envelope.

## Kiem tra

- `:app:compileAppDebugKotlin` PASS.
- `:app:testAppDebugUnitTest --tests "io.legado.app.data.repository.vbook.VbookRegistryParserTest" --tests "io.legado.app.help.vbook.VbookPluginImporterTest" --tests "io.legado.app.help.vbook.VbookPluginImporterSecurityTest" --tests "io.legado.app.help.vbook.VbookPluginInspectorTest" --tests "io.legado.app.data.repository.vbook.VbookImportRepositoryTest"` PASS.
- `:app:clean :app:testAppDebugUnitTest --tests "io.legado.app.compat.CompatibilityCorpusTest"` PASS.
- `.\gradlew.bat :app:compileAppDebugKotlin` PASS.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.vbook.VbookExecutorTest" --tests "io.legado.app.help.vbook.VbookPluginAdapterTest"` PASS.
- `.\gradlew.bat :app:assembleAppDebug` PASS.
- `adb install -r -t app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk` PASS.
- LDPlayer smoke sau khi clear logcat: `com.drducbook.app.debug` mo Kham pha nguon `nTruyen (VBook)`, log ghi `totalChapterNum=195` va `saveContent` cho chuong 1/2, khong con stack `toc.js thieu truong url hop le`.

## Ket qua

- Registry thuc te voi 135 ext khong con bi co do schema qua chat.
- Importer/runtime da chiu duoc nested script va legacy envelope kieu VBook cu hon.
- Alias store giup ext lech metadata van duoc nhan la da cai va canh preview hop ly hon.
- Runtime moi tai duoc danh sach chuong va noi dung chuong cua source sach VBook tren LDPlayer sau khi cai lai APK.

## Luu y

- Device smoke da chay voi mot source sach VBook that. P11.T03 van con can tiep tuc neu nguon rieng le bi 403/anti-bot, regex lam vo layout, hoac video ext can browser tuong tac that.

## 2026-08-01 - Hotfix regex/comic content

### Thay doi bo sung

- `app/src/main/java/io/legado/app/help/vbook/VbookPluginAdapter.kt`
  - Chuan hoa noi dung truyen chu VBook co HTML nhe (`<p>`, `<br>`, block tags, HTML entity) thanh text sach truoc khi luu vao reader.
  - Voi nguon `BookSourceType.image`, chuyen cac dang chap data `images/items/list`, URL anh tran, object `url/src/image/link`, hoac HTML `<img>` thanh chuoi `<img src="...">` de reader truyen tranh nhan dung anh.
  - Thu tu uu tien anh cho comic khong anh huong nguon truyen chu; neu khong tim thay anh thi fallback ve text normalization.
  - Tu reconcile type cho VBook source da cai cu khi source van la default nhung profile plugin khai bao comic/audio/video, va cap nhat lai database neu DB san sang.
- `app/src/main/java/io/legado/app/domain/model/VbookRegistryModels.kt`
  - Bo sung alias comic: `comics`, `truyen-tranh`, `truyen_tranh`, `tranh`, `comic_source`, `manga_source`.
- `app/src/test/java/io/legado/app/help/vbook/VbookPluginAdapterTest.kt`
  - Them regression test cho loi hien literal `<p>/<br>` trong reader.
  - Doi regression comic image array sang ky vong `<img src="...">` thay vi URL tran.
- `app/src/test/java/io/legado/app/help/vbook/VbookPluginImporterTest.kt`
  - Khoa mapping `truyen-tranh` -> `BookSourceType.image`.

### Kiem tra

- `.\gradlew.bat :app:testAppDebugUnitTest --tests io.legado.app.help.vbook.VbookPluginAdapterTest --tests io.legado.app.help.vbook.VbookPluginImporterTest` PASS.
- Lenh tren da chay qua `:app:compileAppDebugKotlin` trong cung Gradle invocation; Kotlin compile PASS.
- `.\gradlew.bat :app:assembleAppDebug` PASS.
- `adb install -r -t app\build\outputs\apk\app\debug\app-app-x86_64-debug.apk` PASS tren LDPlayer `emulator-5554`.
- `adb shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity` PASS; PID `4235`, `versionName=3.26.13_debug`.
- Logcat launch smoke: khong co `FATAL`, `AndroidRuntime`, `VbookPluginException`.

### Luu y van hanh

- Neu chuong da tung bi luu cache voi `<p>/<br>` hoac URL anh tran, can tai lai/xoa cache chuong do de app ghi lai noi dung moi.

## 2026-08-01 - Follow-up regression suite

### Kiem tra bo sung

- Chay lai focused VBook + Markdown + Agent + AI translation regression suite:
  - `VbookPluginAdapterTest`
  - `VbookExecutorTest`
  - `VbookPluginImporterTest`
  - `MarkdownBlockNormalizerTest`
  - `AiToolRepositoryToolCatalogTest`
  - `AgentPermissionBrokerTest`
  - `AiChatGenerationUseCaseTest`
  - `AiTranslationRefinePipelineTest`
  - `AiPromptCatalogTest`
  - `TranslateChapterAiRetryTest`
- `.\gradlew.bat :app:compileAppDebugKotlin --console=plain` PASS.
- `.\gradlew.bat :app:assembleAppDebug --console=plain` PASS.
- `adb -s emulator-5554 install -r -t app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk` PASS.
- `adb -s emulator-5554 shell am start -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity` PASS.

### Ket qua

- Cac hotfix VBook adapter/executor/importer van PASS sau khi them chatbot tool va pipeline dich AI moi.
- APK debug moi da cai va mo lai tren LDPlayer de nguoi dung tiep tuc smoke test nguon VBook that.

## 2026-08-02 - Full local compatibility refresh

### Kiem tra bo sung

- Chay lai focused full compatibility/VBook local suite:
  - `CompatibilityCorpusTest`
  - `VbookRegistryParserTest`
  - `VbookRegistryRepositoryTest`
  - `VbookImportRepositoryTest`
  - `VbookPluginImporterTest`
  - `VbookPluginImporterSecurityTest`
  - `VbookPluginInspectorTest`
  - `VbookExecutorTest`
  - `VbookPluginAdapterTest`
  - `VbookMediaParserTest`
- Tong XML test: 36 focused compatibility/VBook tests, 0 failures, 0 errors.
- Sau do chay lai gop voi Piper/catalog tests de giu XML evidence moi nhat: 13 test classes / 49 tests PASS.
- `.\gradlew.bat :app:compileAppReleaseKotlin --console=plain --no-daemon` PASS in 4m32s, xac nhan source/compat layer compile duoc o release variant.

### Gioi han con lai

- Full local corpus da duoc lam moi va release Kotlin compile PASS; P11.T06 verifier moi xac nhan release ZIP 4/4 hop le, invalid release = 0.
- P11.T03 van IN_PROGRESS vi can them minified/package/device-corpus evidence theo P11.T06 signing/release rollout gate; production artifact van unsigned.
