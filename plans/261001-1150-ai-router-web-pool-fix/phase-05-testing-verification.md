# Phase 05: Testing & Verification

Status: ✅ Completed
Dependencies: [Phase 01](phase-01-opencode-zen.md), [Phase 02](phase-02-gemini-web-auto.md), [Phase 03](phase-03-web-pool-isolation.md), [Phase 04](phase-04-combo-fallback-protection.md)

## Objective
Kiểm thử toàn diện các thay đổi trên cả tầng Unit Test (JVM) và tầng biên dịch Kotlin (`compileAppDebugKotlin`), đảm bảo không có bất kỳ regression nào xảy ra.

## Test Matrix

### 1. OpenCode Header Injection Tests (`OpenAiChatHandlerTest.kt`)
- Test request gửi tới `https://opencode.ai/zen/v1` có đủ các headers:
  - `User-Agent: opencode/1.1.2/cli`
  - `x-opencode-client: cli`
  - `x-opencode-session: <uuid>`
  - `x-opencode-project: <uuid>`
  - `x-opencode-request: <uuid>`
- Test request gửi tới `https://opencode.ai/zen/go/v1` có đầy đủ headers (bao gồm `x-opencode-session`).
- Test request gửi tới provider khác (ví dụ `https://api.deepseek.com/v1`) không bị gắn các headers `x-opencode-*`.
- **⚠️ [BỔ SUNG]** Test `openAiResponsesHeaders()` trong `OpenAiResponsesHandler.kt` cũng inject đúng headers khi URL chứa `opencode.ai`.

### 2. Auto-install Gemini Web Tests (`AiRouterAutoInstallPolicyTest.kt`)
- Test `autoInstallIds` chứa `gemini_web`.
- Test `AUTO_INSTALL_PROVIDER_PROFILE_IDS` chứa `catalog_gemini_web`.
- Test các model web (`gemini-web-flash`, v.v.) được lựa chọn đúng vào generated route.

### 3. Web Multi-Account Session Isolation Tests (`WebSessionManagerTest.kt`)
- Test `GeminiWebSessionManager.getSession(credA)` và `GeminiWebSessionManager.getSession(credB)` trả về 2 session độc lập, không ghi đè nhau.
- Test `invalidateSession(credA)` chỉ xóa session của `credA`, giữ nguyên `credB`.
- Test `invalidateSession(null)` xóa toàn bộ cache (backward-compatible với `@Before setup()`).
- Test `ChatGptWebSessionManager.resolveAccessToken(credA)` và `credB` trả về đúng token riêng biệt.
- **⚠️ [BỔ SUNG]** Test `checkRpcError(line, credentialKey)` gọi `invalidateSession(credentialKey)` thay vì `invalidateSession()`.
- **⚠️ [BỔ SUNG]** Test cache max size (>20 entries → evict oldest).

### 4. Combo Fallback Protection Tests (`RepairAiRouteBindingsUseCaseTest.kt` & `AiRouterAutoInstallPolicyTest.kt`)
- Test khi preset đã có `routeProfileId` tùy chỉnh, việc gọi `ensureGeneratedPresetRouteBinding()` KHÔNG ghi đè `routeProfileId` về Free fallback.
- Test khi preset chưa có route (`routeProfileId.isNullOrBlank()`), route tự sinh được gán hợp lệ.
- **⚠️ [BỔ SUNG]** Test `ensureGeneratedFreeRoutes()` chỉ set `makeDefault = true` khi `taskRoutes.none { it.isDefault }`.
- **⚠️ [BỔ SUNG]** Test `createComboTemplate()` → route mới trở thành active default.
- **⚠️ [BỔ SUNG]** Test `GenerateChapterSummaryUseCase.resolvePreset()` dùng `routeProfileId = null` (sử dụng default route) thay vì `""` (bypass router).

### 5. Build Verification
- Chạy: `.\gradlew.bat :app:compileAppDebugKotlin`
- Chạy: `.\gradlew.bat test --tests "io.legado.app.data.repository.ai.*" --tests "io.legado.app.ui.ai.router.*" --tests "io.legado.app.domain.usecase.*"`
- **⚠️ [BỔ SUNG]** Chạy full test suite: `.\gradlew.bat test` để phát hiện regression.

### 6. ⚠️ [BỔ SUNG] ProGuard / R8 Verification
- Kiểm tra xem các class mới/sửa đổi có cần `-keep` rules trong `proguard-rules.pro`:
  - `ConcurrentHashMap<String, *Session>` không cần keep (Kotlin stdlib).
  - Verify existing keep rules cho `GeminiWebSessionManager`, `ChatGptWebSessionManager`, handlers vẫn đúng.
- Chạy: `.\gradlew.bat assembleAppRelease` (optional, verify R8 không strip critical code).

## Acceptance Criteria
- [ ] 100% unit tests liên quan pass.
- [ ] Lệnh biên dịch `:app:compileAppDebugKotlin` thành công 0 lỗi.
- [ ] Full test suite `gradlew test` pass (no regressions).
- [ ] Cập nhật `project_progress.json` đánh dấu hoàn thành Milestone P44.
- [ ] Ghi lỗi/bài học vào `all_global_errors.jsonl` (nếu có lỗi mới phát sinh).
- [ ] Cập nhật `.brain/session.json` trạng thái phiên.
