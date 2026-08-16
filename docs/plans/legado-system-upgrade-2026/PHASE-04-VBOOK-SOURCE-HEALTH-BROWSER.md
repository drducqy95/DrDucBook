# Phase 04 — VBook import, kiểm tra sức khỏe nguồn và Browser nội bộ

## 1. Kết quả phải đạt

Người dùng import được plugin VBook bằng link hoặc file, kể cả JSON registry tổng hợp; xem được tên/icon/URL/trạng thái nguồn; ứng dụng tự probe nguồn tối đa một lần mỗi 24 giờ; Browser đủ chức năng để đăng nhập và tự giải captcha, giữ cookie cho nguồn và hỗ trợ dịch UI/trang web.

## 2. Phạm vi

### Trong phạm vi

- Import plugin VBook đơn từ URL/file.
- Import registry JSON từ URL/file theo cấu trúc `vbook-fd1246b6.json`.
- Preview/select/update/duplicate/checksum/capability probe.
- Daily health worker cho nguồn enabled.
- Source health entity, history ngắn, notification tổng hợp và repair shortcut.
- Source list hiển thị name, icon và URL.
- Browser: address/search, tab, navigation, history, bookmark, login/cookie, upload/download, desktop mode và find in page.
- User giải captcha/challenge thủ công trong WebView.
- Cookie/profile bridge giữa Browser và HTTP source requests.
- Việt hóa Browser chrome và tùy chọn dịch visible page text.

### Ngoài phạm vi

- Bypass captcha tự động, anti-bot hoặc DRM.
- Bỏ qua SSL error mặc định.
- Chạy JavaScript plugin với filesystem/reflection/private-network không giới hạn.
- Crawler toàn bộ nguồn mỗi ngày.
- Điều khiển Browser từ Agent không cần approval; Agent integration chỉ mở sau Phase 02.

## 3. VBook importer

### P4.1 — Input classification

Importer nhận URL/file của plugin JSON, registry JSON, ZIP plugin legacy hoặc JSON array tương thích. Sau khi đọc trong giới hạn an toàn, parser phải xác định single plugin, registry, compatible array hoặc invalid schema. Không cài trước khi parse/validate và hiển thị preview.

### P4.2 — Preview và cài đặt

- Hiển thị name, author, version, type, icon, URL, capabilities và compatibility warning.
- Cho select all/individual và search/filter type.
- Cùng ID + checksum: skip; version mới: update preview; version thấp: cảnh báo downgrade.
- ID khác nhưng source URL trùng: cảnh báo, không tự hợp nhất.
- Install atomic qua staging; lỗi một plugin không làm hỏng plugin đã cài trước đó.
- Update giữ cookie/plugin storage khi stable plugin ID không đổi.

### P4.3 — Capability probe

- Probe theo declared type và script có thật: search, explore, detail, chapter, content, media, TTS.
- Không suy type từ name/description.
- Lưu evidence và compatibility report theo plugin version/checksum.
- Network probe có timeout, domain policy và giới hạn response size.

## 4. Kiểm tra sức khỏe nguồn hằng ngày

### P4.4 — Scheduling

- Dùng WorkManager unique periodic work, interval 24 giờ và flex window phù hợp.
- Constraint: network connected; không yêu cầu charging.
- Chỉ probe nguồn enabled.
- Manual `Kiểm tra ngay` chạy unique one-time work và hiển thị progress.
- Không dùng `CheckSourceService` dài hạn cho lịch nền; có thể tái sử dụng logic probe nhưng scheduler phải là WorkManager.

### P4.5 — Probe policy

- Kiểm tra base URL/connectivity.
- Chạy search/explore mẫu với giới hạn một page/ít item.
- Xác nhận parse được ít nhất một item có title và URL.
- Tùy khả năng, probe detail/chapter/content ở mức nhẹ.
- Trạng thái: `HEALTHY`, `DEGRADED`, `AUTH_REQUIRED`, `CAPTCHA_REQUIRED`, `BROKEN_RULE`, `HTTP_ERROR`, `UNKNOWN_OFFLINE`.
- Nếu toàn thiết bị offline/DNS chung lỗi, không đánh dấu hàng loạt nguồn là hỏng.
- Lưu lastChecked, latency, HTTP status, failure step, message redacted và consecutive failures.

### P4.6 — UI/notification

- Source row/card luôn có name, cached icon/favicon và full source URL/domain.
- Badge status và thời điểm check.
- Notification một summary/ngày: số lỗi, cần login và captcha; không notification từng nguồn.
- Nhấn notification mở filter `Có lỗi`.
- Nhấn source lỗi mở diagnostic và lối tắt `Mở Browser`, `Sửa nguồn`, `Chạy debug`.

## 5. Browser nội bộ

### P4.7 — Browser architecture

- Giữ WebView runtime hiện có nhưng tách chrome/state sang Compose MVI.
- `BrowserRouteScreen` xử lý lifecycle, file picker, permission, download và WebView host.
- `BrowserScreen` stateless hiển thị toolbar/tab/history/sheet.
- Tab state có URL/title/favicon/progress/canBack/canForward/incognito/profile.
- Restore normal tabs sau process recreation; incognito không restore.

### P4.8 — Tính năng cơ bản

- Address/search bar; search mặc định Google.
- Back/forward/reload/stop/home.
- Multi-tab, close/reopen recent tab.
- History, bookmark, share/copy/open external.
- Find in page, desktop mode và per-site user agent.
- File upload, download interception, popup/window.open.
- Persistent cookie/local storage cho normal profile.
- Incognito xóa cookie/storage khi đóng tab cuối.
- SSL error có cảnh báo; không silent proceed.
- `file://` arbitrary và unsafe bridge bị chặn.

### P4.9 — Login/captcha/source session

- Source diagnostic mở Browser bằng source URL, headers/user-agent cần thiết và cookie profile liên kết.
- User nhập tài khoản và tự hoàn thành captcha/challenge.
- Sau navigation/login, đồng bộ cookie theo domain về source cookie store.
- Có action `Đã đăng nhập — kiểm tra lại nguồn`.
- Không trích password/form value vào log, history hoặc Agent context.

### P4.10 — Dịch Browser

- Tất cả Browser chrome dùng string resources Việt/Anh như app.
- Optional `Dịch trang` lấy visible text nodes, bỏ qua script/style/input/password/contenteditable.
- Chunk qua ML Kit/AI Router; giữ mapping node và content hash.
- Toggle `Gốc/Dịch`, restore DOM original không reload.
- MutationObserver dịch nội dung mới có debounce và giới hạn.
- Dịch trang không can thiệp form, event handler hoặc URL.

## 6. File tác động

### File hiện có cần sửa

- `app/src/main/java/io/legado/app/help/vbook/VbookPluginImporter.kt`
- `app/src/main/java/io/legado/app/help/vbook/VbookPluginInspector.kt`
- `app/src/main/java/io/legado/app/help/vbook/VbookPluginAdapter.kt`
- `app/src/main/java/io/legado/app/help/vbook/VbookExecutor.kt`
- `app/src/main/java/io/legado/app/help/vbook/VbookSafeContext.kt`
- `app/src/main/java/io/legado/app/data/repository/vbook/VbookRegistryRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/vbook/VbookRegistryParser.kt`
- `app/src/main/java/io/legado/app/domain/gateway/VbookRegistryGateway.kt`
- `app/src/main/java/io/legado/app/domain/model/VbookRegistryModels.kt`
- `app/src/main/java/io/legado/app/service/CheckSourceService.kt`
- `app/src/main/java/io/legado/app/data/entities/BookSource.kt`
- `app/src/main/java/io/legado/app/data/dao/BookSourceDao.kt`
- `app/src/main/java/io/legado/app/data/repository/BookSourceRepository.kt`
- `app/src/main/java/io/legado/app/help/source/SourceVerificationHelp.kt`
- `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt`
- `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceViewModel.kt`
- `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt`
- `app/src/main/java/io/legado/app/ui/browser/WebViewActivity.kt`
- `app/src/main/java/io/legado/app/ui/browser/WebViewModel.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/ui/main/home/HomeScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/my/MyScreen.kt`
- `app/src/main/java/io/legado/app/data/AppDatabase.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/AndroidManifest.xml`

### File tạo mới dự kiến

- `app/src/main/java/io/legado/app/domain/model/VbookImportModels.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ImportVbookRegistryUseCase.kt`
- `app/src/main/java/io/legado/app/ui/vbook/importer/VbookImportContract.kt`
- `app/src/main/java/io/legado/app/ui/vbook/importer/VbookImportViewModel.kt`
- `app/src/main/java/io/legado/app/ui/vbook/importer/VbookImportScreen.kt`
- `app/src/main/java/io/legado/app/data/entities/BookSourceHealth.kt`
- `app/src/main/java/io/legado/app/data/dao/BookSourceHealthDao.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ProbeBookSourceUseCase.kt`
- `app/src/main/java/io/legado/app/worker/BookSourceHealthWorker.kt`
- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthContract.kt`
- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthScreen.kt`
- `app/src/main/java/io/legado/app/ui/book/source/health/SourceHealthViewModel.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserContract.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserRouteScreen.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserScreen.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserTabStore.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserPageTranslationBridge.kt`

## 7. Test bắt buộc phải pass

### Test hiện có

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.vbook.VbookPluginImporterTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.vbook.VbookPluginInspectorTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.vbook.VbookRegistryParserTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.vbook.VbookRegistryRepositoryTest"
```

### Test mới bắt buộc

- Classify plugin/registry object/array/file/URL.
- Duplicate, upgrade, downgrade, partial failure và atomic install.
- Zip traversal, zip bomb, oversized JSON, unsafe URL và private IP policy.
- Worker unique schedule, offline-all classification và per-source failure.
- Source health aggregation/notification không spam.
- Cookie bridge round-trip Browser ↔ source request.
- Incognito cleanup và normal profile restore.
- SSL error, file upload/download và popup handling.
- Page translation bỏ qua input/password/script và restore original DOM.

### Instrumentation/Nox

1. Import registry URL mẫu, preview và chọn cài một plugin.
2. Import cùng registry bằng file JSON.
3. Chạy manual source scan khi online và offline.
4. Xem source row có tên/icon/URL/status.
5. Mở Browser từ source lỗi, login/giải captcha thủ công, đóng mở app và kiểm tra cookie còn hiệu lực.
6. Mở nhiều tab, history/bookmark/download/file upload.
7. Bật dịch trang, toggle gốc/dịch và thao tác form không bị hỏng.

### Gate build

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
.\gradlew.bat :app:connectedAppDebugAndroidTest --no-daemon --console=plain
```

## 8. Điều kiện đóng phase

- Link/file VBook đơn và registry đều import được.
- Daily health không báo sai hàng loạt khi offline.
- Source list luôn có name/icon/URL và diagnostic actionable.
- Browser giữ đăng nhập, cho user xử lý captcha và dịch trang mà không phá DOM chức năng.
