# Phase 04: Bảo vệ Route Combo & Chống Override Fallback

Status: ✅ Completed
Dependencies: [Phase 03](phase-03-web-pool-isolation.md)

## Objective
Khắc phục lỗi tự động trả về combo free fallback khi model/combo người dùng chọn bị lỗi hoặc khi app reload; đảm bảo lựa chọn combo/route của người dùng luôn được bảo tồn bền vững và không bị hệ thống tự động ghi đè.

## Context & Phân tích chi tiết nguyên nhân gốc rễ (Root Cause)

Tại sao khi model/combo người dùng chọn bị lỗi (hoặc khi mở lại app), hệ thống lại tự động chuyển về "Free fallback · Dịch văn bản"?

### Bug 1: Preset Hijacking bởi `ensureGeneratedPresetRouteBinding()` (CRITICAL)
**File:** `AiRouterViewModel.kt` dòng 1012-1040

```kotlin
private suspend fun ensureGeneratedPresetRouteBinding(
    taskType: String,
    routeId: String,
    modelIds: List<String>,
) {
    val existing = profileGateway.getTaskPreset(taskType)
    if (existing?.runtimeOptions?.routeProfileId == routeId) return // ❌ BUG TAI HẠI!
    ...
    profileGateway.saveTaskPreset(
        AiTaskPresetDraft(
            ...
            runtimeOptions = existing?.runtimeOptions?.copy(routeProfileId = routeId),
            makeDefault = true,
        )
    )
}
```
- Hàm này kiểm tra: Nếu preset hiện tại CÓ `routeProfileId` KHÁC VỚI `routeId` (tức là người dùng đã chọn một combo khác), hàm cho rằng preset "chưa được bind" → **ghi đè preset của người dùng về Free fallback**!
- So sánh: `AiOAuthRepository` (line 1352) kiểm tra `shouldBindOAuthPresetToDefaultRoute()` trước khi bind. `RepairAiRouteBindingsUseCase` (line 242) kiểm tra `routeProfileId.isNotBlank()`. Nhưng `ensureGeneratedPresetRouteBinding` KHÔNG CÓ guard tương tự!

### Bug 2: Cướp cờ `isDefault` bởi `ensureGeneratedFreeRoutes()` (CRITICAL)
**File:** `AiRouterViewModel.kt` dòng 960

- Khi tạo/cập nhật route tự sinh, code đặt `makeDefault = true` vô điều kiện.
- `saveRoute()` trong `AiRouterRepository.kt` (line 205) gọi `dao.clearDefaultRoutes(entity.taskType)` khi `isDefault = true`.
- `clearDefaultRoutes` chạy: `UPDATE ai_route_profiles SET isDefault = 0 WHERE taskType = :taskType`.
- Hậu quả: Cờ mặc định của combo mà người dùng đã thiết lập trước đó bị hủy!

### Bug 3: Trigger Chain — Cài catalog → Ghi đè preset
**File:** `AiRouterViewModel.kt` `installCatalogEntry()` (line 793-875)

- `installCatalogEntry()` import models → gọi `ensureGeneratedFreeRoutes()` (line 874).
- `ensureGeneratedFreeRoutes()` luôn gọi `ensureGeneratedPresetRouteBinding()` (line 1004) VÔ ĐIỀU KIỆN, bất kể `shouldRebuildGeneratedRoute()` trả về gì.
- Hậu quả: Mỗi khi ViewModel init hoặc catalog entry mới được install, preset bị reset.

### Bug 4: Sticky Default State trong `saveRoute()` (SUBTLE)
**File:** `AiRouterRepository.kt` line 200

```kotlin
isDefault = draft.makeDefault || existing?.isDefault == true
```
- Nếu route existing đã là default, `makeDefault = false` KHÔNG THỂ unset `isDefault`!
- Không có cách nào revoke default status qua `AiRouteProfileDraft`.

### ⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] Bug 5: `createComboTemplate()` KHÔNG set `makeDefault = true`
**File:** `AiRouterViewModel.kt` line 760-770

- Khi user tạo combo template, route mới KHÔNG được gán `isDefault = true`.
- Combo template KHÔNG BAO GIỜ thắng `getActiveRoute()` trừ khi user manual edit preset.
- Cần: sau khi tạo combo route, tự động set `isDefault = true` cho route mới.

### ⚠️ [THIẾU SÓT ĐÃ BỔ SUNG] Bug 6: `routeProfileId = ""` Bypass Router Trap
**File:** `GenerateChapterSummaryUseCase.kt` line 193

```kotlin
runtimeOptions = fallbackPreset.runtimeOptions.copy(routeProfileId = ""),
```
- `routeProfileId = ""` (non-null blank string) ≠ `routeProfileId = null`.
- Trong `AiRouterRepository.resolveRoute()` (line 433-435):
  ```kotlin
  if (request.routeProfileId != null && request.routeProfileId.isBlank()) return null
  ```
- `""` → `resolveRoute()` trả null → router bị BYPASS HOÀN TOÀN → request chạy trực tiếp không có failover, không cooldown, không retry!
- Fix: dùng `routeProfileId = null` (sử dụng default route) thay vì `""`.

## Requirements

### Functional
- [ ] Trong `AiRouterViewModel.kt`:
  - Sửa `ensureGeneratedPresetRouteBinding()`:
    - **CHỈ** liên kết preset với route tự sinh nếu: `existing == null` HOẶC `existing.runtimeOptions.routeProfileId.isNullOrBlank()`.
    - Nếu người dùng đã có `routeProfileId` hợp lệ khác rỗng (đang chọn combo khác), **TUYỆT ĐỐI KHÔNG GHI ĐÈ**.
  - Sửa `ensureGeneratedFreeRoutes()`:
    - Không gán cứng `makeDefault = true`. Chỉ gán `makeDefault = true` nếu chưa có bất kỳ route nào đang là default cho taskType đó (`taskRoutes.none { it.isDefault }`).
  - **⚠️ [BỔ SUNG]** Sửa `createComboTemplate()`:
    - Sau khi tạo combo route, set `makeDefault = true` cho route mới và cập nhật preset `routeProfileId` tương ứng.
- [ ] **⚠️ [BỔ SUNG]** Trong `AiRouterRepository.kt`:
  - Sửa `saveRoute()` line 200: Cho phép unset `isDefault` khi `draft.makeDefault == false` bằng cách thêm flag `forceDefaultOverride` hoặc sửa logic thành `isDefault = draft.makeDefault`.
- [ ] **⚠️ [BỔ SUNG]** Trong `GenerateChapterSummaryUseCase.kt`:
  - Sửa line 193: `routeProfileId = ""` → `routeProfileId = null` để sử dụng default route thay vì bypass router.
- [ ] Trong `TranslationConfigScreen.kt`:
  - Khi người dùng chọn một combo từ `AiComboModelPickerSheet`, đồng bộ đặt `isDefault = true` cho route profile đó trong database (thông qua `saveRoute`), đảm bảo `dao.getActiveRoute(TRANSLATE_CHAPTER)` luôn phản ánh chính xác lựa chọn của người dùng.
- [ ] Trong `AiRouterRepository.kt`:
  - Khi một candidate trong route gặp lỗi, giữ nguyên lỗi và context, không tự ý xóa routeProfileId hay fallback về default route một cách âm thầm; trả về thông báo lỗi rõ ràng.

### Non-Functional
- Giữ nguyên tính tương thích ngược với các bản sao lưu cấu hình trước đó.
- Tránh "route-to-route cascade" (router chỉ failover TRONG một route, không tự động nhảy sang route khác).

## Implementation Steps
1. [ ] Sửa guard condition trong `ensureGeneratedPresetRouteBinding()` — only bind khi preset chưa có route.
2. [ ] Sửa `makeDefault` trong `ensureGeneratedFreeRoutes()` — conditional on `taskRoutes.none { it.isDefault }`.
3. [ ] Sửa `createComboTemplate()` — set `makeDefault = true` cho route mới khi user tạo combo.
4. [ ] Sửa `saveRoute()` sticky default logic — cho phép revoke default status.
5. [ ] Sửa `GenerateChapterSummaryUseCase.kt` — `routeProfileId = null` thay vì `""`.
6. [ ] Cập nhật luồng chọn combo trong `TranslationConfigScreen.kt` để đồng bộ `isDefault`.
7. [ ] Viết unit test: preset đã có custom route → `ensureGeneratedPresetRouteBinding()` KHÔNG ghi đè.
8. [ ] Viết unit test: preset trống → route tự sinh được gán hợp lệ.
9. [ ] Viết unit test: `createComboTemplate()` → route mới trở thành default.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/ui/ai/router/AiRouterViewModel.kt` — Fix 3 hàm: `ensureGeneratedPresetRouteBinding`, `ensureGeneratedFreeRoutes`, `createComboTemplate`
- `app/src/main/java/io/legado/app/data/repository/AiRouterRepository.kt` — Fix sticky default logic trong `saveRoute()`
- `app/src/main/java/io/legado/app/domain/usecase/GenerateChapterSummaryUseCase.kt` — Fix `routeProfileId = ""` → `null`
- `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt` — Sync `isDefault` on combo selection
- `app/src/test/java/io/legado/app/domain/usecase/RepairAiRouteBindingsUseCaseTest.kt` — Route protection tests
- `app/src/test/java/io/legado/app/ui/ai/router/AiRouterAutoInstallPolicyTest.kt` — Auto-install protection tests

## Test Criteria
- [ ] Chọn combo tùy chỉnh, khởi tạo lại ViewModel, kiểm tra preset vẫn giữ nguyên combo tùy chỉnh, không bị reset về Free fallback.
- [ ] Giả lập lỗi trên combo tùy chỉnh, xác nhận hệ thống không âm thầm chuyển preset về Free fallback.
- [ ] `createComboTemplate()` tạo route mới → route mới trở thành active default.
- [ ] `GenerateChapterSummaryUseCase` dùng default route thay vì bypass router.
- [ ] Biên dịch Kotlin thành công.

---
Next Phase: [Phase 05: Testing & Verification](phase-05-testing-verification.md)
