# Phase 01: ML Kit Source Language Selector

Status: ✅ DONE
Dependencies: None

## Objective

Cho phép user tự nhập/chọn ngôn ngữ nguồn thay vì phụ thuộc hoàn toàn vào auto-detect của ML Kit. Khi auto-detect fail (trả về `"und"`), app sẽ sử dụng ngôn ngữ nguồn do user đã cấu hình.

## Root Cause Analysis

**File:** [`MlKitTranslationRepository.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/data/repository/MlKitTranslationRepository.kt#L116-L122)

```kotlin
private suspend fun resolveSourceLanguage(text: String, sourceLanguage: String?): String? {
    sourceLanguage?.takeIf { it.isNotBlank() }?.let { explicit ->
        return normalizeLanguage(explicit)
    }
    return inferMlKitSourceLanguage(text)
        ?: normalizeLanguage(identifyLanguage(text))
}
```

**Vấn đề:** 
- Gateway method `translate()` có param `sourceLanguage: String? = null` nhưng **không ai truyền giá trị vào**.
- `TranslateChapterUseCase` gọi `mlKitTranslationGateway.translate(text, targetLanguage)` mà **bỏ qua `sourceLanguage`** (L251-254).
- Khi `inferMlKitSourceLanguage()` trả `null` (text không có CJK) VÀ `identifyLanguage()` trả `"und"` → `resolveSourceLanguage()` trả `null` → crash: `"ML Kit không thể xác định ngôn ngữ nguồn"`.

## Solution Design

### Approach: User-Configurable Source Language

Thêm setting `mlKitSourceLanguage` để user chọn ngôn ngữ nguồn. Giá trị `"auto"` (default) giữ nguyên hành vi hiện tại. Bất kỳ giá trị khác sẽ được pass thẳng vào `sourceLanguage` param.

### Flow Diagram

```
User selects source language in TranslationConfigScreen
         │
         ▼
TranslationConfig.mlKitSourceLanguage = "zh" (or "auto")
         │
         ▼
TranslateChapterUseCase calls:
  mlKitTranslationGateway.translate(
    text = source,
    targetLanguage = targetLanguage,
    sourceLanguage = resolvedSource,  ← NEW
  )
         │
         ▼
MlKitTranslationRepository.resolveSourceLanguage():
  if explicit != null → use it (skip auto-detect entirely)
  else → inferMlKitSourceLanguage() → identifyLanguage() (existing)
```

## Implementation Steps

### 1. [x] Add `mlKitSourceLanguage` pref key
- **File:** [`PreferKey.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/constant/PreferKey.kt)
- Add `const val mlKitSourceLanguage = "mlKitSourceLanguage"`

### 2. [x] Add config property to TranslationConfig
- **File:** [`TranslationConfig.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/ui/config/translation/TranslationConfig.kt)
- Added `mlKitSourceLanguage` and `mlKitSourceLanguages` list

### 3. [x] Pass `sourceLanguage` in TranslateChapterUseCase
- **File:** [`TranslateChapterUseCase.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt)
- `inferMlKitSourceLanguageHint` prioritizes configured `mlKitSourceLanguage`
- Passed to `executeSuggestion` and `translateWithMlKitPreservingLayout`

### 4. [x] Update TranslateBrowserPageUseCase
- **File:** [`TranslateBrowserPageUseCase.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/domain/usecase/TranslateBrowserPageUseCase.kt)
- Passes configured `mlKitSourceLanguage` to `mlKitTranslationGateway.translate`

### 5. [x] Update TranslateMangaPageUseCase (if applicable)
- **File:** `TranslateMangaPageUseCase.kt`
- Manga translation routes through `MangaTextTranslationRepository` -> `TranslateChapterUseCase.executeSuggestion` which now includes source language

### 6. [x] Add ML Kit source language picker in TranslationConfigScreen
- **File:** [`TranslationConfigScreen.kt`](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigScreen.kt)
- Added DropdownListSettingItem when provider is ML Kit with auto-detect + all languages

### 7. [x] Add ML Kit target language selector (reuse existing)
- Verified: ML Kit shares `llmTargetLanguage`

### 8. [x] Include `sourceLanguage` in translation cache key
- **File:** `TranslateChapterUseCase.kt`
- Added ML Kit configuration revision containing `mlKitSourceLanguage` in `providerConfigurationRevision`

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `constant/PreferKey.kt` | Modify | Add `mlKitSourceLanguage` key |
| `ui/config/translation/TranslationConfig.kt` | Modify | Add `mlKitSourceLanguage` property |
| `domain/usecase/TranslateChapterUseCase.kt` | Modify | Pass source language to ML Kit calls |
| `domain/usecase/TranslateBrowserPageUseCase.kt` | Modify | Pass source language to ML Kit calls |
| `ui/config/translation/TranslationConfigScreen.kt` | Modify | Add source language picker UI |

## Test Criteria

- [x] Setting source language to "zh" and translating Chinese text works without auto-detect
- [x] Setting source language to "auto" preserves existing behavior
- [x] Short text (<50 chars) that previously failed auto-detect now translates with explicit source
- [x] Translation cache invalidates when user changes source language
- [x] UI picker shows all available ML Kit languages with download status
- [x] Provider config revision changes when source language changes

## Error Immunity Check

Related errors from `all_global_errors.jsonl`:
- **ERR_0018-0020**: Translation pipeline regex/CJK residue issues — NOT directly related but CJK repair pipeline should continue to work
- **ERR_0039**: Translation cache force retranslate — cache key must include source language

---
Next Phase: [Phase 02 - OPDS Domain Models](./phase-02-opds-domain.md)
