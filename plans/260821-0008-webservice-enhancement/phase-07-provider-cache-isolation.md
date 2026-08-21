# Phase 07: Provider Cache Isolation Fix

Status: ✅ Complete
Dependencies: Phase 05, Phase 06

## Objective

Sửa lỗi cross-provider cache leak: khi user chọn provider cụ thể (ví dụ Google Translate), hệ thống luôn trả về cache QT thay vì cache đúng provider đã chọn. Áp dụng thứ tự ưu tiên AI → NMT → QT → Google → ML Kit cho cả native và webservice, nhưng CHỈ khi không có provider cụ thể được yêu cầu.

## Root Cause Analysis

### Bug chính: `findPreferredMachineCache()` không respect `requestedProvider`

**File:** `TranslateChapterUseCase.kt` L1334-1384

```
execute(provider = "google")
  → findPreferredMachineCache(requestedProvider = "google")
  → Scan: app_ai (miss) → nmt (miss) → quick_translator (HIT!)
  → Return QT content ← BUG! Google Translate không bao giờ được gọi
```

### Bug phụ: `hasCachedTranslation()` cross-provider check

**File:** `TranslationManager.kt` L104-112

```kotlin
if (provider == TranslationConfig.llmProvider) {
    return getPreferredCachedTranslation(...) != null  // Checks ALL providers
}
```

## Requirements

### Functional
- [x] REQ-01: Khi `execute()` được gọi với provider cụ thể, chỉ check cache của provider đó
- [x] REQ-02: Cross-provider fallback CHỈ áp dụng khi KHÔNG chỉ định provider (auto-select mode)
- [x] REQ-03: Thứ tự ưu tiên fallback: AI → NMT → QT → Google → ML Kit
- [x] REQ-04: Behavior nhất quán giữa native reader và webservice
- [x] REQ-05: `hasCachedTranslation()` phải check đúng provider được yêu cầu
- [x] REQ-06: WebService `/api/v2/translation/content` trả đúng cache per-provider

### Non-Functional
- [x] Không ảnh hưởng performance cache lookup hiện tại
- [x] Backward-compatible: legacy cache files vẫn đọc được

## Implementation Steps

### Step 1: Fix `findPreferredMachineCache` in `TranslateChapterUseCase.kt`
- [x] Sửa `findPreferredMachineCache` để khi `requestedProvider.isNotBlank()`, chỉ scan candidate tương ứng với `requestedProvider`.
- [x] Khi `requestedProvider.isBlank()`, scan theo `preferredContentProviders(targetLanguage)` (AI -> NMT -> QT -> Google -> ML Kit).

### Step 2: Fix `hasCachedTranslation` in `TranslationManager.kt`
- [x] Bỏ cross-provider branch trong `hasTranslatedCache()`, luôn gọi `getCachedTranslation(book, chapter, provider, targetLanguage)`.

### Step 3: Verification & Integration Testing
- [x] Build APK Debug thành công (`assembleAppDebug`).
- [x] Cài đặt lên máy ảo và test API WebService.
- [x] Xác thực: `provider=google` trả `content: null` khi chưa dịch, không trả cache QT.
- [x] Xác thực: Dịch Google Translate thành công và tạo cache Google riêng biệt (11,442 chars).
- [x] Xác thực: `GET /api/v2/translation/memory/providers` hiển thị đủ 3 cache độc lập (`google`, `nmt`, `quick_translator`).
- [x] Xác thực: Auto-select (provider null) trả về cache ưu tiên cao nhất (`nmt`).

## Files Modified
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt` — Sửa `findPreferredMachineCache()` để respect `requestedProvider`.
- `app/src/main/java/io/legado/app/model/translation/TranslationManager.kt` — Sửa `hasTranslatedCache()` để check đúng provider.

---
Next Phase: Phase 08 - Story Memory Series Toggle / Phase 09 - AI Rewrite Prompt
