# Phase 04 — VBook Import, Source Health & Browser — Kế hoạch triển khai

Spec gốc: [../PHASE-04-VBOOK-SOURCE-HEALTH-BROWSER.md](../PHASE-04-VBOOK-SOURCE-HEALTH-BROWSER.md)
Wave: **3** | Phụ thuộc: P03 (dịch trang Browser)
Ước lượng: 5–7 ngày

---

## 1. Mục tiêu

Import plugin VBook qua link/file/registry JSON, quét sức khỏe nguồn hằng ngày bằng
WorkManager, Browser Compose đủ chức năng đăng nhập/cookie/captcha/tab/dịch trang.

---

## 2. Trạng thái hiện tại

### Đã có

| Artifact | Trạng thái | Ghi chú |
|---|:---:|---|
| VBook core | DONE | 6 files: Importer, Inspector, Adapter, Executor, MediaParser, SafeContext |
| VBook domain | DONE | `VbookRegistryModels.kt`, `VbookRegistryGateway.kt`, `VbookRegistryRepository.kt`, `VbookRegistryParser.kt` |
| Browser legacy | DONE | `WebViewActivity.kt` (12.5 KB), `WebViewModel.kt` (5.1 KB) — View-based |
| CheckSourceService | DONE | Existing service — nhưng plan nói dùng WorkManager |

### Chưa có

| Artifact | Ưu tiên |
|---|:---:|
| VBook Import UI (Contract/ViewModel/Screen) | HIGH |
| `ImportVbookRegistryUseCase.kt` | HIGH |
| `VbookImportModels.kt` | HIGH |
| `BookSourceHealth.kt` entity | **CRITICAL** |
| `BookSourceHealthDao.kt` | **CRITICAL** |
| `ProbeBookSourceUseCase.kt` | HIGH |
| `BookSourceHealthWorker.kt` (WorkManager) | HIGH |
| Source Health UI (Contract/ViewModel/Screen) | HIGH |
| Browser Compose (Contract/Route/Screen/TabStore) | HIGH |
| `BrowserPageTranslationBridge.kt` | MEDIUM |

---

## 3. Task chi tiết

### P04.T01 — VBook import models & use case `[TODO]`

**Files:**
- `domain/model/VbookImportModels.kt` `[NEW]`
- `domain/usecase/ImportVbookRegistryUseCase.kt` `[NEW]`

**Models:**
```
ImportClassification — SINGLE_PLUGIN, REGISTRY, COMPATIBLE_ARRAY, INVALID_SCHEMA
VbookImportPreview — name, author, version, type, icon, URL, capabilities, compatibility
VbookImportAction — INSTALL, UPDATE, SKIP_SAME, DOWNGRADE_WARNING, DUPLICATE_URL_WARNING
```

**Use case logic:**
1. Input: URL hoặc file URI
2. Đọc trong giới hạn an toàn (size limit, timeout)
3. Parse → classify schema
4. Preview danh sách plugin
5. Check duplicate (ID + checksum skip, version mới update, version thấp warn)
6. Install atomic qua staging; lỗi 1 plugin không hỏng plugin trước
7. Update giữ cookie/plugin storage khi stable plugin ID không đổi

**Tiêu chí pass:**
- Import registry JSON → preview đúng số plugin
- Duplicate (cùng ID+checksum) → skip
- Version mới → update preview
- Install partial failure → plugin đã cài trước OK

---

### P04.T02 — VBook import UI `[TODO]`

**Files:**
- `ui/vbook/importer/VbookImportContract.kt` `[NEW]`
- `ui/vbook/importer/VbookImportViewModel.kt` `[NEW]`
- `ui/vbook/importer/VbookImportScreen.kt` `[NEW]`

**Yêu cầu:**
1. Input: URL text field hoặc file picker
2. Preview list: name, author, version, type, icon, capabilities
3. Select all/individual, search, filter by type
4. Action buttons: Install selected, Cancel
5. Progress per plugin
6. Error handling: invalid schema, network error, parse error

**Navigation:** `MainRouteVbookImport`

---

### P04.T03 — Source health entity & DAO `[TODO → CRITICAL]`

**Files:**
- `data/entities/BookSourceHealth.kt` `[NEW]`
- `data/dao/BookSourceHealthDao.kt` `[NEW]`

**Entity schema:**
```kotlin
@Entity(tableName = "book_source_health")
data class BookSourceHealth(
    @PrimaryKey val sourceUrl: String,
    val status: String, // HEALTHY, DEGRADED, AUTH_REQUIRED, CAPTCHA_REQUIRED, BROKEN_RULE, HTTP_ERROR, UNKNOWN_OFFLINE
    val lastChecked: Long,
    val latencyMs: Long?,
    val httpStatus: Int?,
    val failureStep: String?,
    val messageRedacted: String?,
    val consecutiveFailures: Int = 0,
)
```

**DAO:**
- `upsert`, `getBySourceUrl`, `getAll`, `getAllByStatus`, `deleteBySourceUrl`
- `getErrorCount`, `getAuthRequiredCount`, `getCaptchaRequiredCount`

**Room migration:** version +1 (additive table)

---

### P04.T04 — Probe use case `[TODO]`

**File:** `domain/usecase/ProbeBookSourceUseCase.kt` `[NEW]`

**Probe logic:**
1. Check base URL connectivity
2. Search/explore mẫu (1 page, ít item)
3. Parse ≥1 item có title + URL
4. Optional: probe detail/chapter/content nhẹ
5. Status mapping: network error → HTTP_ERROR, parse fail → BROKEN_RULE, auth needed → AUTH_REQUIRED, captcha → CAPTCHA_REQUIRED

**Offline-all detection:**
- Nếu toàn thiết bị offline (DNS chung lỗi) → KHÔNG đánh hàng loạt nguồn là hỏng
- Check connectivity trước khi bắt đầu batch

---

### P04.T05 — Source health worker (WorkManager) `[TODO]`

**File:** `worker/BookSourceHealthWorker.kt` `[NEW]`

**Yêu cầu:**
1. `PeriodicWorkRequestBuilder<BookSourceHealthWorker>(24, HOURS)` với flex window
2. Constraint: `NetworkType.CONNECTED`; không yêu cầu charging
3. Unique periodic work name: `"source_health_check"`
4. Chỉ probe nguồn enabled
5. Manual `Kiểm tra ngay`: unique one-time work
6. KHÔNG dùng `CheckSourceService` cho schedule nền — tái sử dụng probe logic nhưng scheduler = WorkManager

**Registration:** `App.kt` hoặc `AppStartupMaintenanceUseCase`

---

### P04.T06 — Source health UI `[TODO]`

**Files:**
- `ui/book/source/health/SourceHealthContract.kt` `[NEW]`
- `ui/book/source/health/SourceHealthViewModel.kt` `[NEW]`
- `ui/book/source/health/SourceHealthScreen.kt` `[NEW]`

**Yêu cầu:**
1. Source row: name, cached icon/favicon, full source URL/domain
2. Badge status (color dot) + last check time
3. Filter: All, Healthy, Error, Auth required, Captcha required
4. Notification: 1 summary/ngày → số lỗi, cần login, captcha
5. Nhấn notification → filter `Có lỗi`
6. Nhấn source lỗi → diagnostic: `Mở Browser`, `Sửa nguồn`, `Chạy debug`
7. Manual `Kiểm tra ngay` button

---

### P04.T07 — Browser Compose architecture (P4.7) `[TODO]`

**Files:**
- `ui/browser/BrowserContract.kt` `[NEW]`
- `ui/browser/BrowserRouteScreen.kt` `[NEW]`
- `ui/browser/BrowserScreen.kt` `[NEW]`
- `ui/browser/BrowserTabStore.kt` `[NEW]`

**Contract:**
```kotlin
@Stable data class BrowserUiState(
    val tabs: ImmutableList<BrowserTab>,
    val activeTabIndex: Int,
    val addressBarText: String,
    val isLoading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val sheet: BrowserSheet = BrowserSheet.None,
)
@Stable data class BrowserTab(
    val id: String, val url: String, val title: String,
    val favicon: Bitmap?, val progress: Float,
    val isIncognito: Boolean, val profileId: String,
)
```

**RouteScreen:** lifecycle, file picker, permission, download, WebView host
**Screen:** stateless toolbar/tab strip/history/bookmark sheets
**TabStore:** Restore normal tabs after recreation; incognito no restore

---

### P04.T08 — Browser features (P4.8) `[TODO]`

**Yêu cầu:**
1. Address/search bar (default: Google)
2. Back/forward/reload/stop/home
3. Multi-tab, close, reopen recent
4. History, bookmark, share/copy/open external
5. Find in page, desktop mode, per-site user agent
6. File upload, download interception, popup/`window.open`
7. Persistent cookie/localStorage (normal profile)
8. Incognito: xóa khi đóng tab cuối
9. SSL error → cảnh báo, KHÔNG silent proceed
10. `file://` arbitrary + unsafe bridge → chặn

---

### P04.T09 — Login/captcha/cookie bridge (P4.9) `[TODO]`

**Yêu cầu:**
1. Source diagnostic → Browser bằng source URL + headers/user-agent + cookie profile
2. User đăng nhập + giải captcha thủ công
3. Sau login: sync cookie theo domain → source cookie store
4. Action `Đã đăng nhập — kiểm tra lại nguồn`
5. KHÔNG trích password/form value vào log/history/Agent context

---

### P04.T10 — Browser page translation (P4.10) `[TODO]`

**File:** `ui/browser/BrowserPageTranslationBridge.kt` `[NEW]`

**Yêu cầu:**
1. `Dịch trang`: extract visible text nodes (skip script/style/input/password/contenteditable)
2. Chunk qua ML Kit hoặc AI Router
3. Mapping node → content hash
4. Toggle `Gốc/Dịch` → restore DOM original WITHOUT reload
5. MutationObserver dịch nội dung mới (debounce + limit)
6. KHÔNG can thiệp form, event handler, URL

---

## 5. Test bắt buộc

### Unit tests hiện có (phải pass)

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.vbook.VbookPluginImporterTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.vbook.VbookPluginInspectorTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.vbook.VbookRegistryParserTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.vbook.VbookRegistryRepositoryTest"
```

### Unit tests mới

| Test case | Priority |
|---|:---:|
| Classify plugin/registry/array/file/URL | HIGH |
| Duplicate/upgrade/downgrade/partial failure + atomic | HIGH |
| Zip traversal, zip bomb, oversized JSON, unsafe URL, private IP | CRITICAL |
| Worker unique schedule, offline-all, per-source failure | HIGH |
| Source health aggregation no notification spam | MEDIUM |
| Cookie bridge round-trip Browser ↔ source | HIGH |
| Incognito cleanup + normal restore | MEDIUM |
| SSL error, file upload/download, popup handling | MEDIUM |
| Page translation skip input/password/script + restore DOM | HIGH |

### Nox smoke test

| # | Kịch bản |
|:---:|---|
| 1 | Import registry URL mẫu, preview, cài 1 plugin |
| 2 | Import cùng registry bằng file JSON |
| 3 | Manual source scan (online + offline) |
| 4 | Source row: tên/icon/URL/status |
| 5 | Browser → source lỗi → login/captcha → đóng mở app → cookie OK |
| 6 | Multi-tab, history, bookmark, download, file upload |
| 7 | Dịch trang → toggle gốc/dịch → form không hỏng |

---

## 6. Điều kiện đóng phase

- [ ] Link/file VBook đơn và registry đều import được
- [ ] Daily health không báo sai hàng loạt khi offline
- [ ] Source list luôn có name/icon/URL và diagnostic actionable
- [ ] Browser giữ đăng nhập, cho user xử lý captcha và dịch trang không phá DOM

## 7. Execution status 2026-07-26

| Task | Status | Evidence |
|---|---|---|
| P04.T01-P04.T02 | DONE | URL/file JSON registry preview and per-plugin install UI; sample registry previewed on LDPlayer with 133 plugins |
| P04.T03-P04.T06 | DONE / DEVICE_PARTIAL | Room 102->103 health table, bounded probe, unique WorkManager and Compose health list; filter overflow fixed with horizontal scrolling |
| P04.T07-P04.T08 | DONE / DEVICE_SMOKE_PASS | Browser Compose route and direct main navigation shortcut; guarded WebView, tabs, address/search, navigation, desktop mode, download handoff |
| P04.T09 | PARTIAL | Scoped cookie round-trip, manual login/captcha flow and SSL cancellation; incognito/popup/result-screen gates remain |
| P04.T10 | PARTIAL / UNIT_PASS | DOM-safe extraction, identity/hash mapping, chunking, residual policy, MutationObserver debounce and restore; real ML Kit page fixture remains |

Verification: 26 focused JVM tests passed; `:app:compileAppDebugKotlin` passed; `:app:assembleAppDebug` passed; LDPlayer `127.0.0.1:5555` opened the main navigation item `Trình duyệt` and Browser Compose without CrashReport.
