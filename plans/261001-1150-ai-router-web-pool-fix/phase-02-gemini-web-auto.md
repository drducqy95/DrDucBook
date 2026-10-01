# Phase 02: Tự động kích hoạt Gemini Web Free

Status: ✅ Completed
Dependencies: [Phase 01](phase-01-opencode-zen.md)

## Objective
Tự động kích hoạt provider `gemini_web` ngay khi khởi tạo app/router để người dùng có sẵn nguồn AI dịch/chat miễn phí chất lượng cao (Gemini 2.5 Flash / Gemini Pro) mà không cần nhập API key hay đăng nhập tài khoản Google.

## Context
- `AiProviderCatalog.kt` đã có cấu hình `gemini_web` với protocol `GEMINI_WEB` và `authType = NONE`.
- Model catalog bao gồm:
  - `gemini-web-default`
  - `gemini-web-flash`
  - `gemini-web-pro`
  - `gemini-web-thinking`
- `GeminiWebHandler.kt` đã hỗ trợ RPC streaming `batchexecute`, trích xuất `snlm0e` tự động và stream delta accumulator hoàn chỉnh.
- Hiện tại, `autoInstallIds` trong `AiProviderCatalog.kt` và `AUTO_INSTALL_PROVIDER_PROFILE_IDS` trong `AiRouterViewModel.kt` đang để trống, khiến app mới cài đặt hoặc reset không tự động cài đặt `gemini_web`.

## Requirements

### Functional
- [ ] Trong `AiProviderCatalog.kt`:
  - Thêm `"gemini_web"` vào `autoInstallIds`.
- [ ] Trong `AiRouterViewModel.kt`:
  - Thêm `"catalog_gemini_web"` vào `AUTO_INSTALL_PROVIDER_PROFILE_IDS`.
  - Cập nhật danh sách model ưu tiên cho route tự sinh để bao gồm các model `gemini-web-*` (ví dụ `gemini-web-flash`).
  - Hỗ trợ cả `catalog_gemini_web` và `catalog_opencode_free` (sau khi OpenCode được fix) trong danh sách auto-install hoặc template combo.

### Non-Functional
- Quá trình bootstrap diễn ra trên `Dispatchers.IO`, hoàn toàn idempotent, không block main thread và không làm chậm UI khởi động.

## Implementation Steps
1. [ ] Cập nhật `autoInstallIds` trong `AiProviderCatalog.kt` bổ sung `"gemini_web"`.
2. [ ] Cập nhật `AUTO_INSTALL_PROVIDER_PROFILE_IDS` trong `AiRouterViewModel.kt` bổ sung `"catalog_gemini_web"`.
3. [ ] Cấu hình model filter & priority cho `gemini_web` trong `selectGeneratedFreeRouteModelIds()`.
4. [ ] Cập nhật unit test `AiRouterAutoInstallPolicyTest.kt` để kiểm tra auto-install `catalog_gemini_web`.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/model/AiProviderCatalog.kt`
- `app/src/main/java/io/legado/app/ui/ai/router/AiRouterViewModel.kt`
- `app/src/test/java/io/legado/app/ui/ai/router/AiRouterAutoInstallPolicyTest.kt`

## Test Criteria
- [ ] Khi khởi động ViewModel, provider `catalog_gemini_web` được cài đặt tự động.
- [ ] Các model `gemini-web-*` được lưu vào Room database.
- [ ] Unit tests cho auto-install pass 100%.

---
Next Phase: [Phase 03: Cách ly phiên Web Pool Multi-Account](phase-03-web-pool-isolation.md)
