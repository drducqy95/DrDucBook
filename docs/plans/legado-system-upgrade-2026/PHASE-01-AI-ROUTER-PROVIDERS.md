# Phase 01 — AI Router, provider, model, credential và OAuth

## 1. Kết quả phải đạt

AI Router trở thành module cấp ứng dụng, mở từ Home và Cá nhân, không còn là mục con của `Cài đặt > AI`. Người dùng cấu hình provider trong popup thích ứng, chọn model bằng dropdown có tìm kiếm, test được draft trước khi lưu và không gặp tình trạng OAuth đã kết nối nhưng Chatbot báo cấu hình không hợp lệ.

Phase này là dependency bắt buộc của Chatbot Agent, dịch AI, Sáng tác và Ebook Editor.

## 2. Phạm vi

### Trong phạm vi

- Navigation cấp ứng dụng và lối tắt Home/Cá nhân.
- Dashboard provider/route/health/history.
- Provider tile dạng icon, tìm kiếm và filter.
- Popup provider: Base URL, auth/API key, model, sync, test, save.
- Searchable model dropdown theo từng provider.
- OpenCode và MiMo dạng một provider family, nhiều connection mode.
- Provider tùy chỉnh và Local GGUF configuration entry.
- OAuth credential binding, refresh error và inference probe.
- Migrate API key plaintext sang encrypted credential.
- Repair dữ liệu route OAuth đã lưu trước đó.
- Tinh gọn `Cài đặt > AI` để chỉ giữ hành vi AI.

### Ngoài phạm vi

- Agent tool/skill/memory: Phase 02.
- Download model ML Kit và pipeline dịch: Phase 03.
- UI TTS và model voice: Phase 05.
- Viết lại protocol handler hoàn toàn; chỉ sửa handler khi cần chuẩn hóa test/error/credential.

## 3. Thiết kế UI/UX đã khóa

### 3.1 Lối vào

- Route mới: `MainRouteAiRouter`.
- Route string mới: `ai/router`.
- Route cũ `settings/ai/router` phải redirect tương thích.
- Thêm action icon trong `HomeScreen` và item trong `MyScreen`.
- Xóa callback/item `onNavigateToAiRouter` khỏi `AiConfigScreen` sau khi route mới hoạt động.

### 3.2 Dashboard

- Header health: route active, provider ready/degraded, credential cần login, success rate và latency.
- Provider grid:
  - 4 cột phone; 6–8 cột tablet theo adaptive grid.
  - Icon cục bộ, tên tối đa hai dòng, status dot và badge auth.
  - Có search không phân biệt dấu/hoa thường.
  - Filter: All, Ready, Error, Free, API, OAuth, Local và capability.
- Route card thu gọn theo task; target chỉ bung khi user nhấn.
- Attempt history mặc định 20 mục; màn chi tiết hỗ trợ tối đa 100 mục và filter.

### 3.3 Popup provider

- Phone: `AppModalBottomSheet` gần full-height.
- Tablet/foldable: `AppAlertDialog` hoặc adaptive dialog hai cột.
- Trường bắt buộc: connection mode, Base URL, API key/token, model dropdown, test connection.
- Nút: sync model, test, save, reset default, delete configuration.
- Advanced section: protocol, auth type, models URL, request path và custom headers.
- Secret đã lưu chỉ hiển thị `Đã lưu`; không nạp lại plaintext vào UiState.

### 3.4 OpenCode/MiMo family

- `OpenCode`: Free Zen và Go/API.
- `MiMo`: Free, API pay-as-you-go và Token Plan.
- Mỗi mode lưu thành provider profile/credential cụ thể riêng, nhưng dashboard gộp cùng một icon family.
- Route được phép dùng đồng thời Free và paid target.
- Free mode không yêu cầu key; paid mode chặn test nếu thiếu key.

### 3.5 Model picker

- Chọn provider/account trước, sau đó model dropdown chỉ lấy model của provider đó.
- Search theo display name và model ID.
- Hiển thị context/output limit ở dòng phụ.
- Có `Nhập model ID thủ công`.
- Model bị xóa khỏi catalog không bị xóa route; gắn badge `Không còn trong catalog`.

## 4. Work package

### P1.1 — Tách navigation và package UI

- Tạo package `ui/ai/router` và chuyển contract/viewmodel/screen hiện có ra khỏi `ui/config/ai/router`.
- Sửa Navigation 3 graph, navigator normalization và back-stack.
- Thêm Home/Cá nhân shortcut; giữ deep-link alias cũ.
- Tinh gọn AI Settings: bỏ provider database, model database và AI Router; giữ AI behavior, prompt, translation, summary, Agent policy và bubble toggle.

### P1.2 — Dashboard state và adaptive layout

- Tạo UI model provider family, connection variant, route health và aggregate attempt.
- Dùng immutable list/map ở UiState boundary.
- Tách provider grid, route cards, health summary và diagnostic list thành stateless composable.
- Không tính health hoặc filter trong composable; ViewModel/use case cung cấp state đã chuẩn hóa.

### P1.3 — Provider configuration workflow

- Thay `InstallCatalogProvider` bằng `OpenProviderConfig`.
- Catalog click không được lưu provider/model hoặc đặt default ngay.
- Draft được test trực tiếp bằng `AiTextGateway`/handler phù hợp trước khi save.
- Test gồm validate, model discovery nếu có và một inference tối thiểu.
- Save draft chưa test được phép, nhưng connection giữ `UNVERIFIED` và không được auto-bind route.

### P1.4 — Credential security

- Mọi API key mới ghi vào `AndroidAiSecretStore` và `AiCredentialEntity`.
- Migration đọc `AiProviderProfile.apiKey`, tạo credential tương ứng, ghi `secretRef`, sau đó xóa plaintext.
- Backup/export/diagnostic chỉ chứa masked metadata.
- Thêm redaction cho request header, OAuth token và provider data.

### P1.5 — OAuth route binding và repair

- `AiOAuthEvent.Connected` phải trả về định danh credential/account vừa lưu.
- Sau OAuth:
  1. Lưu/refresh model profile.
  2. Tạo hoặc cập nhật target Chat gắn `credentialId`.
  3. Chạy inference probe.
  4. Chỉ báo ready sau probe pass.
- `AiRouterRepository.resolveRoute()` không được nuốt refresh error.
- Cấm fallback trực tiếp sang OAuth provider có `apiKey` rỗng.
- Tạo repair use case chạy một lần cho Codex/Claude/Antigravity/xAI/Kimi/Qwen/Grok CLI/GitHub/Cline/ClinePass.

### P1.6 — Local provider entry

- Local GGUF xuất hiện như provider tile nhưng popup thay key/URL bằng file picker, model metadata, RAM estimate và native-runtime status.
- Chỉ đánh dấu ready nếu inspect/load/generate probe pass.
- Không tự đặt LocalAI làm default khi ABI/native library không hỗ trợ.

## 5. Interface và schema

### Domain model dự kiến

- `AiProviderFamily`
- `AiProviderConnectionMode`
- `AiProviderConnectionDraft`
- `AiConnectionTestResult`
- `AiConnectionStatus`
- `AiProviderDashboardItem`
- `AiRouteHealth`

### Gateway/use case dự kiến

- `TestAiProviderDraftUseCase`
- `SaveAiProviderConnectionUseCase`
- `RepairAiRouteBindingsUseCase`
- `ObserveAiRouterDashboardUseCase`
- Mở rộng `AiRouterGateway` cho transaction save connection + credential + target.

Không tạo schema provider-family trong Room nếu có thể suy ra từ catalog ID; connection mode được ánh xạ tới provider profile cụ thể để không phá route hiện tại.

## 6. File tác động

### File hiện có cần sửa/di chuyển

- `app/src/main/java/io/legado/app/ui/config/ai/router/AiRouterContract.kt`
- `app/src/main/java/io/legado/app/ui/config/ai/router/AiRouterViewModel.kt`
- `app/src/main/java/io/legado/app/ui/config/ai/router/AiRouterScreen.kt`
- `app/src/main/java/io/legado/app/ui/config/ai/AiConfigContract.kt`
- `app/src/main/java/io/legado/app/ui/config/ai/AiConfigScreen.kt`
- `app/src/main/java/io/legado/app/ui/config/ai/AiConfigViewModel.kt`
- `app/src/main/java/io/legado/app/ui/config/ai/AiProviderEdit*`
- `app/src/main/java/io/legado/app/ui/config/ai/AiModelEdit*`
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavigator.kt`
- `app/src/main/java/io/legado/app/ui/main/home/HomeScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/my/MyScreen.kt`
- `app/src/main/java/io/legado/app/domain/model/AiProviderCatalog.kt`
- `app/src/main/java/io/legado/app/domain/model/AiRouterModels.kt`
- `app/src/main/java/io/legado/app/domain/gateway/AiRouterGateway.kt`
- `app/src/main/java/io/legado/app/data/repository/AiRouterRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/AiOAuthRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/AiProfileRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/AndroidAiSecretStore.kt`
- `app/src/main/java/io/legado/app/data/repository/ai/OpenAiResponsesHandler.kt`
- `app/src/main/java/io/legado/app/data/entities/AiProviderProfile.kt`
- `app/src/main/java/io/legado/app/data/entities/AiCredentialEntity.kt`
- `app/src/main/java/io/legado/app/data/AppDatabase.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`

### File tạo mới dự kiến

- `app/src/main/java/io/legado/app/ui/ai/router/AiProviderConfigSheet.kt`
- `app/src/main/java/io/legado/app/ui/ai/router/AiProviderGrid.kt`
- `app/src/main/java/io/legado/app/ui/ai/router/SearchableModelPicker.kt`
- `app/src/main/java/io/legado/app/domain/usecase/TestAiProviderDraftUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/RepairAiRouteBindingsUseCase.kt`
- `app/src/test/java/io/legado/app/domain/usecase/RepairAiRouteBindingsUseCaseTest.kt`
- `app/src/test/java/io/legado/app/data/repository/AiCredentialMigrationTest.kt`

Chỉ xóa package/file editor cũ sau khi `rg` xác nhận không còn reference.

## 7. Test bắt buộc phải pass

### Unit test hiện có

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.AiRouteSelectorTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.AiRouterPolicyTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiTextRepositoryImplTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiProviderCatalogTest"
```

### Test mới bắt buộc

- Provider family mapping OpenCode/MiMo.
- Search model/provider không phân biệt dấu và stable selection.
- Free mode không cần key; paid mode thiếu key bị chặn.
- Draft test dùng giá trị chưa save.
- OAuth Connected tạo target có credential.
- OAuth refresh failure được giữ đúng taxonomy.
- Router không fallback sang OAuth provider key rỗng.
- Migration plaintext key tạo encrypted credential và xóa plaintext.
- Repair không ghi đè custom route hợp lệ.
- Old route alias mở đúng dashboard mới.

### Build và instrumentation

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
```

Nox smoke test bắt buộc:

1. Mở AI Router từ Home và Cá nhân.
2. Mở provider popup trên phone layout.
3. OpenCode Free test và chat thành công không key.
4. MiMo API yêu cầu key.
5. OAuth Codex login, chọn `gpt-5.6-terra`, gửi `Xin chào` và nhận content hoặc lỗi endpoint xác thực; tuyệt đối không còn lỗi local `Cấu hình AI chưa hợp lệ` do credential rỗng.
6. Attempt history ghi đúng provider/model/account/latency.
7. Logcat không chứa API key/token và không có Koin/navigation crash.

## 8. Điều kiện đóng phase

- AI Router không còn phụ thuộc đường dẫn Cài đặt.
- Provider catalog click luôn mở cấu hình, không cài âm thầm.
- Model picker gọn và searchable.
- OpenCode/MiMo Free/API hoạt động đúng mode.
- OAuth credential binding và migration dữ liệu cũ đã được chứng minh bằng test.
- Chat, dịch và task route khác có thể resolve target hợp lệ qua cùng Router.
