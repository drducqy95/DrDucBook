# Phase 05: Per-Provider Cache & Pipeline Behavior

Status: ✅ Complete
Dependencies: None (core domain logic, independent)

## Objective

Thay đổi cache behavior: mỗi provider có cache riêng, lưu vĩnh viễn, khi chuyển provider nếu đã có cache thì gọi cache, KHÔNG tự dịch lại. Chỉ dịch lại khi user chọn "Dịch lại chương". Áp dụng cho CẢ native reader và web service.

## Scope

| In Scope | Out of Scope |
|---|---|
| TranslateChapterUseCase.kt — relaxed cache lookup | Thay đổi cache file format |
| TranslationManager.kt — no-auto-translate policy | Thêm provider mới |
| TranslationCacheRepositoryImpl.kt — new query methods | Room DB migration |
| Web reader provider selector + stale badge | Native reader UI changes (verify only) |
| WebServiceTranslationJobController.kt — provider cache listing | |

## Current Cache Architecture (file-based, per-provider)

```
<cachePath>/<bookFolderName>/
├── ch001.vi.google.nb              # Google cache
├── ch001.vi.google.nb.meta.json    # Google metadata (hash, status, revision)
├── ch001.vi.app_ai.nb              # AI cache (separate file!)
├── ch001.vi.app_ai.nb.meta.json    # AI metadata
├── ch001.vi.quick_translator.nb    # QT cache
└── ch001.vi.quick_translator.nb.meta.json
```

**Key**: Cache IS already per-provider. The problem is **validation strictness** — cache is rejected if hash doesn't match current dictionary/config.

## Requirements

### Functional — Cache Behavior (Native + Web)
- [ ] REQ-01: Khi mở chương + provider A có cache → **LUÔN hiển thị cache**, bất kể content hash match hay không
- [ ] REQ-02: Nếu hash không match → đánh dấu `STALE` nhưng VẪN hiển thị nội dung
- [ ] REQ-03: Khi switch provider A → B:
  - Nếu B có cache → hiện cache B ngay lập tức
  - Nếu B chưa có cache → hiện placeholder "Chưa có bản dịch cho provider này" + nút "Dịch ngay"
- [ ] REQ-04: KHÔNG auto-translate khi cache miss (trừ khi user click "Dịch ngay" hoặc bật auto-translate)
- [ ] REQ-05: "Dịch lại chương" button → `forceRetranslate = true` → dịch mới + tạo revision
- [ ] REQ-06: `USER_EDITED` / `FINAL` revisions vẫn protected — không bao giờ bị machine overwrite

### Functional — Web UI
- [ ] REQ-07: Web reader hiện provider selector dropdown khi bật chế độ dịch
- [ ] REQ-08: Badge `STALE` (⚠️) hiện khi cache cũ hơn dictionary/config
- [ ] REQ-09: API endpoint `GET /api/v2/translation/memory/providers?bookUrl=&chapterIndex=` — list tất cả provider caches cho 1 chapter

### Non-Functional
- [ ] NF-01: Cache lookup ≤ 50ms (đọc file metadata, không validate hash)
- [ ] NF-02: Provider switch ≤ 100ms (direct file read)
- [ ] NF-03: Backward compatible — cache files cũ vẫn đọc được

## Implementation Steps

### Step 1: Domain — TranslateChapterUseCase.kt (CRITICAL)
1. [ ] **Relaxed cache lookup**: Sửa `readCurrentTranslation` call:
   ```kotlin
   // BEFORE: reject if hash mismatch
   val cached = gateway.readCurrentTranslation(...)?.takeIf { it.cacheContentHash == contentHash }
   
   // AFTER: accept any cache, mark stale
   val cached = gateway.readCurrentTranslation(...)?.let { revision ->
       if (revision.cacheContentHash == contentHash) revision
       else revision.copy(status = RevisionStatus.STALE)
   }
   ```
2. [ ] **No auto-translate on cache miss**: Thêm parameter `autoTranslateOnMiss: Boolean = false`
   - Khi `false` + cache miss + `!forceRetranslate` → return `NoTranslationCacheException`
   - Web reader sẽ set `true` chỉ khi user click "Dịch ngay"
3. [ ] **Chunk cache cũng relax**: Trong chunk loop, accept chunk cache nếu `originalContentHash` match (content chưa đổi), bất kể dictionary hash

### Step 2: Domain — TranslationManager.kt
4. [ ] Sửa `getPreferredCachedTranslation()`:
   - Accept ANY cache for requested provider (bỏ hash validation khi display)
   - Thêm `stale: Boolean` field vào returned state
5. [ ] Thêm `listProviderCachesForChapter(bookUrl, chapterIndex, targetLanguage)`:
   - Scan disk cho tất cả `<chapter>.<lang>.*.nb.meta.json` files
   - Return list `ProviderCacheInfo(provider, status, stale, updatedAt, hasUserEdits)`

### Step 3: Data — TranslationCacheRepositoryImpl.kt
6. [ ] Thêm `readCacheIgnoringHash(book, chapter, targetLanguage, provider)`:
   - Đọc `.nb` file + metadata, KHÔNG validate contentHash
   - Return `TranslationRevision` với `status = STALE` nếu hash mismatch
7. [ ] Thêm `listProviderCaches(book, chapter, targetLanguage)`:
   - Glob `<chapterName>.<targetLang>.*.nb.meta.json`
   - Parse metadata → return `ProviderCacheInfo` list

### Step 4: Backend — WebServiceTranslationJobController.kt
8. [ ] Thêm endpoint `getProviderCaches(bookUrl, chapterIndex, targetLanguage)`:
   - Call `TranslationManager.listProviderCachesForChapter(...)`
   - Return `List<WebServiceProviderCacheInfo>`

### Step 5: Backend — KtorServer.kt
9. [ ] Thêm route `GET /api/v2/translation/memory/providers`

### Step 6: Frontend — BookChapter.vue
10. [ ] Thêm provider selector dropdown trong translation control panel
11. [ ] Load provider caches khi mở chương: `GET /api/v2/translation/memory/providers`
12. [ ] Switch provider → load cache cho provider đó (hoặc hiện placeholder)
13. [ ] Stale badge (⚠️) khi cache đã cũ
14. [ ] "Dịch lại chương" button → `forceRetranslate: true`

### Step 7: Frontend — webService.ts
15. [ ] Thêm `getWebServiceProviderCaches(bookUrl, chapterIndex)`
16. [ ] Thêm type `WebServiceProviderCacheInfo`

### Step 8: Domain Models — WebServiceModels.kt
17. [ ] Thêm `WebServiceProviderCacheInfo` data class

## Cache Behavior Matrix (Pass/Fail reference)

| Scenario | Expected Behavior |
|---|---|
| Open chapter, Google cache exists, hash matches | ✅ Show Google cache, no stale badge |
| Open chapter, Google cache exists, dict changed | ✅ Show Google cache, ⚠️ STALE badge |
| Open chapter, Google cache exists, switch to AI | ✅ If AI cache exists → show AI. If not → placeholder "No translation" |
| Open chapter, no cache for any provider | ✅ Show placeholder "Chưa có bản dịch" + "Dịch ngay" button |
| User clicks "Dịch ngay" | ✅ Translate + save cache + show result |
| User clicks "Dịch lại chương" | ✅ forceRetranslate → new revision, stale badge clears |
| Dict changes on chapter with USER_EDITED | ✅ Show USER_EDITED (protected), stale badge but content preserved |
| Switch provider back to one with USER_EDITED | ✅ Show USER_EDITED content (highest priority) |

## Files to Create/Modify
- `app/.../domain/usecase/TranslateChapterUseCase.kt` — Relaxed cache + no auto-translate
- `app/.../model/translation/TranslationManager.kt` — Relaxed preferred cache + list provider caches
- `app/.../data/repository/TranslationCacheRepositoryImpl.kt` — New query methods
- `app/.../web/WebServiceTranslationJobController.kt` — Provider caches endpoint
- `app/.../web/KtorServer.kt` — New route
- `app/.../domain/webservice/WebServiceModels.kt` — New DTOs
- `modules/web/src/views/BookChapter.vue` — Provider selector + stale badge
- `modules/web/src/api/webService.ts` — New API function

## Pass Criteria

- [ ] PASS-01: Dịch chương 1 bằng Google → file `ch001.vi.google.nb` tạo trên disk
- [ ] PASS-02: Dịch chương 1 bằng AI → file `ch001.vi.app_ai.nb` tạo (Google cache KHÔNG bị xóa)
- [ ] PASS-03: Switch về Google → hiện cache Google NGAY, không trigger dịch mới
- [ ] PASS-04: Thay đổi dictionary term → cache hiện ⚠️ STALE badge nhưng vẫn hiển thị nội dung cũ
- [ ] PASS-05: Click "Dịch lại" → dịch mới chạy + badge hết stale
- [ ] PASS-06: Edit bản dịch → USER_EDITED → switch provider → switch lại → edit vẫn còn
- [ ] PASS-07: Mở chương chưa có cache → hiện placeholder + nút "Dịch ngay"
- [ ] PASS-08: Native reader cũng áp dụng đúng behavior (verify trên LDPlayer)
- [ ] PASS-09: `.\gradlew.bat test` — unit tests pass
- [ ] PASS-10: `.\gradlew.bat assembleAppDebug` thành công
- [ ] PASS-11: Verify trên LDPlayer + browser

## Build Gate
```bash
.\gradlew.bat test
.\gradlew.bat assembleAppDebug
cd modules/web && pnpm build
cd ../..
adb connect 127.0.0.1:5555
adb install -r app/build/outputs/apk/app/debug/app-app-universal-debug.apk
```

---
Next Phase: → [phase-06-translation-dashboard.md](./phase-06-translation-dashboard.md)
