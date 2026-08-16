# Phase 01 — AI Router & Providers — Kế hoạch triển khai

Spec gốc: [../PHASE-01-AI-ROUTER-PROVIDERS.md](../PHASE-01-AI-ROUTER-PROVIDERS.md)
Wave: **1 (Blocker)**
Ước lượng: 3–4 ngày

---

## 1. Mục tiêu

Hoàn thiện AI Router thành module cấp ứng dụng hoạt động đầy đủ: dashboard hiển thị provider
grid có search/filter, provider configuration qua popup adaptive, model picker searchable,
OpenCode/MiMo family, OAuth credential binding an toàn, và migration dữ liệu cũ.

Phase này là dependency bắt buộc của Phase 02 (Agent), Phase 03 (dịch AI), Phase 07 (Sáng tác).

---

## 2. Trạng thái hiện tại

### Đã có (DONE/PARTIAL)

| Artifact | Trạng thái | File | Ghi chú |
|---|:---:|---|---|
| Route `MainRouteAiRouter` | DONE | `ui/main/MainNavKey.kt:40` | Đã đăng ký Navigation 3 |
| Route const `ai/router` | DONE | `ui/main/MainNavKey.kt:189` | |
| Router UI package | DONE | `ui/ai/router/` | 6 files, tách khỏi `ui/config/ai/` |
| `AiRouterContract.kt` | DONE | 10.3 KB | MVI contract |
| `AiRouterViewModel.kt` | PARTIAL | 51.7 KB | Logic có nhưng cần refactor InstallCatalogProvider → OpenProviderConfig |
| `AiRouterScreen.kt` | PARTIAL | 24.8 KB | Dashboard có nhưng chưa rõ health aggregate |
| `AiProviderGrid.kt` | PARTIAL | 12.2 KB | Grid có nhưng chưa có search/filter đầy đủ |
| `AiProviderConfigSheet.kt` | PARTIAL | 9.1 KB | Popup có nhưng thiếu SearchableModelPicker, Advanced section |
| `AiRouterDashboardMapper.kt` | PARTIAL | 15.4 KB | Mapper có nhưng chưa rõ family grouping |
| `TestAiProviderDraftUseCase.kt` | DONE | 6.8 KB | |
| `RepairAiRouteBindingsUseCase.kt` | PARTIAL | 12 KB | Cần verify OAuth repair one-time logic |
| `MigrateAiProviderApiKeysUseCase.kt` | PARTIAL | 2.3 KB | Cần verify encrypted migration |
| `AiCredentialEntity.kt` | DONE | 1.1 KB | |
| `AiProviderCatalog.kt` | DONE | 13.5 KB | Provider catalog đầy đủ |
| `AndroidAiSecretStore.kt` | DONE | Có | Encrypted store |
| Route redirect `settings/ai/router` | VERIFY | `MainNavigator.kt` | Cần kiểm tra redirect |

### Chưa có (TODO)

| Artifact | File dự kiến | Spec tham chiếu |
|---|---|---|
| `SearchableModelPicker` composable | `ui/ai/router/SearchableModelPicker.kt` | P1 §3.5 |
| Home shortcut AI Router | `ui/main/home/HomeScreen.kt` — thêm action icon | P1 §3.1 |
| Cá nhân menu AI Router | `ui/main/my/MyScreen.kt` — thêm item | P1 §3.1 |
| AI Settings cleanup | `ui/config/ai/AiConfigScreen.kt` — xóa router items | P1 §3.1 |
| Credential migration test | `test/.../AiCredentialMigrationTest.kt` | P1 §7 |

---

## 3. Phạm vi điều chỉnh

So với spec gốc:

- **Giữ nguyên**: Toàn bộ scope gốc P1.1–P1.6 vẫn áp dụng
- **Đã làm xong**: Navigation route, package UI tách riêng, contract/viewmodel/screen skeleton, domain use cases
- **Cần hoàn thiện**: SearchableModelPicker, Home/My shortcuts, OpenCode/MiMo family mapping, dashboard health aggregate, provider config refactor, credential security verify, test coverage
- **Không thay đổi**: Local provider entry (P1.6) vẫn trong scope

---

## 4. Task chi tiết

### P01.T01 — SearchableModelPicker composable `[TODO]`

**File:** `app/src/main/java/io/legado/app/ui/ai/router/SearchableModelPicker.kt` `[NEW]`

**Yêu cầu:**
1. Stateless composable nhận `ImmutableList<ModelPickerItem>`, `selectedModelId: String?`, `onSelect: (String) -> Unit`, `onManualEntry: (String) -> Unit`
2. Search TextField: filter theo display name + model ID, case-insensitive, diacritics-insensitive (dấu tiếng Việt)
3. Mỗi item hiển thị: display name (bold), model ID (secondary), context window / output limit (caption)
4. Badge `Không còn trong catalog` cho model đã chọn nhưng bị xóa khỏi catalog
5. Mục cuối: `Nhập model ID thủ công` → TextField + confirm
6. Dùng `LazyColumn` + `key(modelId)` + `contentType`
7. `@Stable data class ModelPickerItem(...)` với `ImmutableList`

**Tiêu chí pass:**
- Gõ "gpt" lọc đúng model chứa "gpt" trong name hoặc ID
- Gõ "gpt-5" chọn được, xóa text, danh sách restore đầy đủ
- Nhập thủ công model ID tùy ý → callback `onManualEntry` nhận đúng giá trị
- Compile pass, không có `@Composable invocations` warning

---

### P01.T02 — Tích hợp SearchableModelPicker vào provider config `[TODO]`

**File:** `app/src/main/java/io/legado/app/ui/ai/router/AiProviderConfigSheet.kt` `[MODIFY]`

**Yêu cầu:**
1. Thay model dropdown hiện tại bằng `SearchableModelPicker`
2. Thêm Advanced section (expandable): protocol selector, auth type, models URL override, request path, custom headers
3. Secret đã lưu → hiển thị `"Đã lưu (••••)"`, không nạp lại plaintext vào UiState
4. Nút: Sync model, Test, Save, Reset default, Delete configuration
5. Phone: `AppModalBottomSheet` gần full-height; Tablet: adaptive dialog

**Tiêu chí pass:**
- Mở popup → model picker search hoạt động
- Test connection → draft gọi `AiTextGateway` trước khi save
- Save draft chưa test → connection giữ `UNVERIFIED`
- Secret field không hiển thị plaintext API key đã lưu

---

### P01.T03 — Home/Cá nhân shortcuts `[TODO]`

**Files:**
- `app/src/main/java/io/legado/app/ui/main/home/HomeScreen.kt` `[MODIFY]` — thêm action icon AI Router trong toolbar
- `app/src/main/java/io/legado/app/ui/main/my/MyScreen.kt` `[MODIFY]` — thêm menu item "AI Router"

**Yêu cầu:**
1. Home: icon button trong top bar hoặc action row → navigate `MainRouteAiRouter`
2. Cá nhân: item trong settings list → navigate `MainRouteAiRouter`
3. Icon dùng `AppIcons` phù hợp (AI/router icon)

**Tiêu chí pass:**
- Nhấn icon từ Home → mở AI Router dashboard
- Nhấn item từ Cá nhân → mở AI Router dashboard
- Back navigation đúng stack

---

### P01.T04 — Dashboard health & family grouping `[PARTIAL → DONE]`

**File:** `app/src/main/java/io/legado/app/ui/ai/router/AiRouterDashboardMapper.kt` `[MODIFY]`

**Yêu cầu:**
1. Provider family mapping: OpenCode (Free Zen, Go/API) + MiMo (Free, API, Token Plan) → gộp cùng icon trên dashboard
2. Health aggregate: route active count, provider ready/degraded count, credential cần login, success rate (%), average latency (ms)
3. Filter: All, Ready, Error, Free, API, OAuth, Local, capability
4. Search provider: không phân biệt dấu/hoa thường

**Tiêu chí pass:**
- Dashboard hiển thị OpenCode dưới 1 family icon dù có 2+ connection modes
- Filter "Free" chỉ hiển thị provider Free
- Search "opencode" tìm được OpenCode
- Health header hiển thị số liệu aggregate

---

### P01.T05 — Provider config workflow refactor `[PARTIAL → DONE]`

**File:** `app/src/main/java/io/legado/app/ui/ai/router/AiRouterViewModel.kt` `[MODIFY]`

**Yêu cầu:**
1. Thay `InstallCatalogProvider` bằng `OpenProviderConfig` — catalog click mở popup config, KHÔNG cài/lưu ngay
2. Draft test: gọi `AiTextGateway`/handler trước khi save (validate → model discovery → inference tối thiểu)
3. Save chưa test → connection `UNVERIFIED`, không auto-bind route
4. Free mode: không yêu cầu key → test trực tiếp
5. Paid mode: chặn test nếu thiếu key

**Tiêu chí pass:**
- Click provider tile → mở config popup (không cài)
- Test thành công → trạng thái `VERIFIED`
- Save không test → trạng thái `UNVERIFIED`
- OpenCode Free: test không cần key
- MiMo API: thiếu key → chặn test, hiển thị lỗi

---

### P01.T06 — Credential security & migration `[PARTIAL → DONE]`

**Files:**
- `data/repository/AndroidAiSecretStore.kt` `[VERIFY]`
- `domain/usecase/MigrateAiProviderApiKeysUseCase.kt` `[VERIFY/MODIFY]`
- `data/entities/AiProviderProfile.kt` `[VERIFY]`

**Yêu cầu:**
1. API key mới ghi vào `AndroidAiSecretStore` → `AiCredentialEntity` có `secretRef`
2. Migration: đọc `AiProviderProfile.apiKey` plaintext → tạo credential encrypted → ghi `secretRef` → xóa plaintext
3. Backup/export/diagnostic chỉ chứa masked metadata (e.g. `sk-****1234`)
4. Redaction: request header, OAuth token, provider data trong log/diagnostic

**Tiêu chí pass:**
- Sau migration: `AiProviderProfile.apiKey` column rỗng
- `AiCredentialEntity` có record tương ứng với `secretRef`
- Log/diagnostic không chứa API key plaintext
- Export backup không chứa secret

---

### P01.T07 — OAuth repair & route binding `[PARTIAL → DONE]`

**File:** `domain/usecase/RepairAiRouteBindingsUseCase.kt` `[VERIFY/MODIFY]`

**Yêu cầu:**
1. `AiOAuthEvent.Connected` trả credential/account ID
2. Sau OAuth: lưu/refresh model profile → tạo/update target có `credentialId` → chạy inference probe → báo ready sau probe pass
3. `resolveRoute()` không nuốt refresh error
4. Cấm fallback sang OAuth provider có apiKey rỗng
5. Repair one-time cho Codex/Claude/Antigravity/xAI/Kimi/Qwen/Grok CLI/GitHub/Cline

**Tiêu chí pass:**
- OAuth login → target tự động có credentialId
- Probe fail → trạng thái không chuyển ready
- Provider OAuth có apiKey rỗng → không được fallback
- Repair chạy không lỗi, không ghi đè custom route hợp lệ

---

### P01.T08 — Local provider entry `[TODO]`

**File:** `ui/ai/router/AiProviderConfigSheet.kt` `[MODIFY]` (phần Local GGUF)

**Yêu cầu:**
1. Local GGUF tile: popup thay key/URL bằng file picker, model metadata, RAM estimate, native-runtime status
2. Chỉ ready nếu inspect/load/generate probe pass
3. Không tự đặt LocalAI làm default khi ABI/native library không hỗ trợ

**Tiêu chí pass:**
- Chọn Local tile → popup hiển thị file picker + metadata
- ABI không hỗ trợ → thông báo rõ ràng, không cho ready

---

### P01.T09 — AI Settings cleanup `[TODO]`

**File:** `ui/config/ai/AiConfigScreen.kt` `[MODIFY]`

**Yêu cầu:**
1. Xóa callback/item `onNavigateToAiRouter` (đã có shortcut ở Home/Cá nhân)
2. Xóa provider database và model database items
3. Giữ lại: AI behavior, prompt, translation, summary, Agent policy, bubble toggle

**Tiêu chí pass:**
- Mở Cài đặt > AI: không thấy "AI Router", "Provider", "Model Database"
- Các mục AI behavior/prompt/translation vẫn hoạt động

---

### P01.T10 — Route redirect compatibility `[VERIFY]`

**File:** `ui/main/MainNavigator.kt` `[MODIFY nếu cần]`

**Yêu cầu:**
1. Deep-link hoặc navigation tới `settings/ai/router` phải redirect sang `ai/router`

**Tiêu chí pass:**
- Navigate bằng route string cũ → mở đúng dashboard mới

---

## 5. Test bắt buộc

### Unit tests hiện có (phải giữ pass)

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.AiRouteSelectorTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.AiRouterPolicyTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiTextRepositoryImplTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiProviderCatalogTest"
```

### Unit tests mới `[TODO]`

**File:** `app/src/test/java/io/legado/app/data/repository/AiCredentialMigrationTest.kt` `[NEW]`

| Test case | Mô tả |
|---|---|
| `migration_plaintext_creates_encrypted_credential` | Migration tạo credential encrypted từ apiKey plaintext |
| `migration_clears_plaintext_after_verify` | apiKey column rỗng sau migration |
| `oauth_connected_creates_target_with_credentialId` | OAuth Connected → target có credentialId |
| `oauth_refresh_failure_preserves_taxonomy` | Refresh error giữ đúng error type |
| `router_no_fallback_to_empty_oauth_key` | Router không fallback sang provider OAuth có key rỗng |
| `free_mode_no_key_required` | Free mode test thành công không cần key |
| `paid_mode_blocked_without_key` | Paid mode thiếu key → chặn test |
| `draft_test_uses_unsaved_values` | Test connection dùng draft values, chưa save |
| `repair_preserves_valid_custom_route` | Repair không ghi đè route hợp lệ |
| `old_route_alias_opens_new_dashboard` | Route cũ redirect đúng |

### Build gate

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
```

### Nox smoke test

| # | Kịch bản | Pass criteria |
|:---:|---|---|
| 1 | Mở AI Router từ Home icon | Dashboard hiển thị provider grid |
| 2 | Mở AI Router từ Cá nhân | Dashboard hiển thị provider grid |
| 3 | Mở provider popup trên phone | BottomSheet gần full-height |
| 4 | Search model "gpt" | Danh sách lọc đúng |
| 5 | OpenCode Free test không key | Test pass, chat gửi "Xin chào" thành công |
| 6 | MiMo API yêu cầu key | Thiếu key → chặn test |
| 7 | OAuth Codex login + chat | Nhận response, không lỗi "Cấu hình AI chưa hợp lệ" |
| 8 | Attempt history | Ghi đúng provider/model/account/latency |
| 9 | Logcat | Không chứa API key/token, không Koin/navigation crash |

---

## 6. Điều kiện đóng phase

- [ ] AI Router không còn phụ thuộc đường dẫn Cài đặt
- [ ] Provider catalog click luôn mở cấu hình, không cài âm thầm
- [ ] Model picker gọn và searchable
- [ ] OpenCode/MiMo Free/API hoạt động đúng mode
- [ ] OAuth credential binding và migration dữ liệu cũ đã được chứng minh bằng test
- [ ] Chat, dịch và task route khác có thể resolve target hợp lệ qua cùng Router
