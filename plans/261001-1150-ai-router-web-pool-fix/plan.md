# Plan: Khắc phục OpenCode Zen, Kích hoạt Gemini Web Free, Cách ly Pool Web Multi-Account & Bảo vệ Route Combo

Created: 2026-10-01T11:50:00+07:00
Updated: 2026-10-01T12:12:00+07:00
Status: 🟢 Planning Refined (Awaiting User Approval)

## Overview

Kế hoạch toàn diện giải quyết 4 vấn đề trọng yếu của hệ thống AI Route trong Legado Material 3:
1. **Khắc phục OpenCode Zen & OpenCode Go:** Inject bộ headers giả lập OpenCode CLI (`User-Agent`, `x-opencode-client`, `x-opencode-session`, `x-opencode-project`, `x-opencode-request`) vào cả `OpenAiChatHandler` lẫn `OpenAiResponsesHandler`, đồng thời cập nhật danh mục model và `AiProviderPresets`.
2. **Tự động kích hoạt Gemini Web Free:** Đưa `gemini_web` vào danh sách `autoInstallIds` và `AUTO_INSTALL_PROVIDER_PROFILE_IDS`, tự động bootstrap provider và các model web miễn phí không cần đăng nhập Google ngay khi khởi động.
3. **Cách ly phiên Multi-Account Web Pool (Gemini Web & ChatGPT Web):** Loại bỏ bug cache singleton (`cachedSession`) trong `GeminiWebSessionManager` và `ChatGptWebSessionManager`. Chuyển sang lưu trữ theo khóa định danh `ConcurrentHashMap<String, WebSession>`, cô lập Cookie cho từng provider, cập nhật tất cả 7 call sites invalidation, và pass credential vào `checkRpcError()`.
4. **Khắc phục lỗi tự động trả về combo free fallback khi model/combo bị lỗi:** Vá triệt để 6 bug ghi đè cấu hình: `ensureGeneratedPresetRouteBinding`, `ensureGeneratedFreeRoutes`, `createComboTemplate`, sticky default state, `GenerateChapterSummaryUseCase` router bypass trap, và bảo vệ cờ `isDefault` + route selection của người dùng.
5. **Kiểm thử & Xác minh toàn diện:** Unit tests cho session isolation, CLI headers (cả 2 handlers), route binding protection, router bypass, cache eviction, biên dịch Kotlin, full regression test suite, ProGuard verification.

## Tech Stack
- **Platform:** Android Jetpack Compose Material 3, Clean Architecture, MVI/UDF
- **Language / Runtime:** Kotlin 2.1, Coroutines, StateFlow / SharedFlow, OkHttp / Cronet SSE
- **DI & Storage:** Koin, AndroidX Room (DB v105)
- **AI Routing Engine:** `AiRouterRepository`, `AiRouterViewModel`, `GeminiWebSessionManager`, `ChatGptWebSessionManager`

## Phases

| Phase | Tên Phase | Mô tả trọng tâm | Files chính | Trạng thái |
|---|---|---|---|---|
| **01** | OpenCode Zen & Go Header Bypass | Inject 5 headers CLI vào `OpenAiChatHandler` + `OpenAiResponsesHandler` + sync `AiModels.kt` & catalog | 5 files | ⬜ Pending |
| **02** | Tự động kích hoạt Gemini Web Free | Cấu hình auto-install `gemini_web` và bootstrap model vào route tự sinh | 3 files | ⬜ Pending |
| **03** | Cách ly phiên Web Pool Multi-Account | Refactor SessionManager → ConcurrentHashMap + credential-aware invalidation (7 call sites) + cookie isolation | 5 files | ⬜ Pending |
| **04** | Bảo vệ Route Combo & Chống Override Fallback | Fix 6 bugs: preset hijacking, sticky default, combo template, router bypass, trigger chain | 6 files | ⬜ Pending |
| **05** | Testing & Verification | Unit test suites (6 categories) + compile check + full regression + ProGuard | N/A | ⬜ Pending |

## Impact Summary — Thiếu sót đã phát hiện và bổ sung

| # | Thiếu sót ban đầu | Phase bổ sung | Mức độ |
|---|---|---|---|
| 1 | `OpenAiResponsesHandler.kt` có header function RIÊNG, không reuse `openAiChatHeaders()` | Phase 01 | 🟡 Medium |
| 2 | `fetchOpenAiCompatibleModels()` trong `OpenAiResponsesHandler` build headers inline | Phase 01 | 🟡 Medium |
| 3 | `AiModels.kt` `AiProviderPresets` chứa OpenCode entry cần sync model | Phase 01 | 🟢 Low |
| 4 | `GeminiWebHandler.checkRpcError()` cần pass credential key | Phase 03 | 🔴 High |
| 5 | 5 invalidation call sites trong `GeminiWebHandler` cần truyền credential | Phase 03 | 🔴 High |
| 6 | `CookieManager` fallback trong `resolveCookieHeader` cần documentation | Phase 03 | 🟢 Low |
| 7 | `createComboTemplate()` không set `makeDefault = true` | Phase 04 | 🔴 High |
| 8 | `GenerateChapterSummaryUseCase` dùng `routeProfileId = ""` bypass router | Phase 04 | 🔴 High |
| 9 | `saveRoute()` sticky default — không thể unset `isDefault` | Phase 04 | 🟡 Medium |
| 10 | Thiếu ProGuard/R8 verification cho release build | Phase 05 | 🟡 Medium |

## Quick Commands
- Bắt đầu Phase 1: `/code phase-01`
- Kiểm tra tiến độ: `/next`
- Lưu context não bộ: `/save-brain`
