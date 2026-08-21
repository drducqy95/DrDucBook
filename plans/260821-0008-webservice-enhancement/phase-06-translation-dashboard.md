# Phase 06: Translation Dashboard & Memory API

Status: ⬜ Pending
Dependencies: Phase 05 (provider cache APIs)

## Objective

Tạo Translation Dashboard trên web để quản lý translation jobs, xem provider cache browser, pretranslate batches, và xem thống kê translation memory. Thêm backend API cho glossary và story memory.

## Scope

| In Scope | Out of Scope |
|---|---|
| TranslationDashboard.vue — new page | Glossary editor (edit/add terms) — future |
| Backend memory stats endpoint | Story memory editor — future |
| Provider cache browser per chapter | Worldbuilding graph visualization |
| Pretranslate batch UI | Pronoun matrix editor |
| Navigation integration | Translation quality scoring |

## Requirements

### Functional
- [ ] REQ-01: Trang `/translation` hiện 4 panels:
  - **Active Jobs**: List active/completed translation jobs + progress
  - **Provider Cache Browser**: Chọn book → xem cache status per chapter per provider
  - **Pretranslate**: Chọn book → provider → range → batch translate
  - **Memory Stats**: Thống kê glossary terms, characters, worldbuilding
- [ ] REQ-02: Backend `GET /api/v2/translation/memory/stats` — trả số lượng terms, characters, entities
- [ ] REQ-03: Backend `GET /api/v2/translation/memory/glossary?bookUrl=...` — list glossary terms cho 1 book
- [ ] REQ-04: Backend `GET /api/v2/translation/memory/story?bookUrl=...` — trả story memory summary
- [ ] REQ-05: Navigation sidebar có link "Dịch thuật" đến dashboard
- [ ] REQ-06: Active jobs panel auto-refresh mỗi 3s khi có running jobs

### Non-Functional
- [ ] NF-01: Dashboard load ≤ 3 giây
- [ ] NF-02: Stats API ≤ 1 giây

## Implementation Steps

### Step 1: Backend — Memory Stats API
1. [ ] Thêm method `getMemoryStats()` vào `WebServiceTranslationJobController`:
   - Count global dict terms via `QuickTranslationRepository`
   - Count story memory entities via `TranslationStoryMemoryUseCase`
2. [ ] Thêm method `getGlossary(bookUrl)` — list dictionary terms scoped to book
3. [ ] Thêm method `getStoryMemory(bookUrl)` — summary of story memory (characters, factions, timeline count)

### Step 2: Backend — KtorServer routes
4. [ ] `GET /api/v2/translation/memory/stats`
5. [ ] `GET /api/v2/translation/memory/glossary?bookUrl=...`
6. [ ] `GET /api/v2/translation/memory/story?bookUrl=...`

### Step 3: Backend — WebServiceModels.kt
7. [ ] Thêm DTOs:
   - `WebServiceTranslationMemoryStatsResponse`
   - `WebServiceGlossaryTermResponse`
   - `WebServiceStoryMemorySummaryResponse`

### Step 4: Frontend — webService.ts
8. [ ] Thêm types + functions cho 3 memory endpoints

### Step 5: Frontend — TranslationDashboard.vue
9. [ ] Panel 1: Active Jobs — poll `/api/v2/translation/jobs`, show progress bars, cancel buttons
10. [ ] Panel 2: Provider Cache Browser:
    - Book selector (từ shelf)
    - Chapter list với badges per provider: `Google ✓`, `AI ✓ (stale)`, `QT ✓`, `❌`
    - Dùng `GET /api/v2/translation/memory/providers` per chapter (hoặc batch)
11. [ ] Panel 3: Pretranslate Control:
    - Book selector, provider selector, from chapter, count
    - Submit → `POST /api/v2/translation/pretranslate`
    - Show progress
12. [ ] Panel 4: Memory Stats:
    - Call `GET /api/v2/translation/memory/stats`
    - Display counts in cards

### Step 6: Navigation — WebAppShell.vue + router
13. [ ] Thêm route `/translation` → `TranslationDashboard`
14. [ ] Thêm nav item "Dịch thuật" trong sidebar

### Step 7: i18n keys
15. [ ] Thêm keys: `translationDashboard`, `activeJobs`, `providerCaches`, `pretranslateControl`, `memoryStats`, `globalTerms`, `projectTerms`, `characters`, `factions`, `worldbuilding`, `storyTimeline`, `translateNow`, `noTranslation`, `stale`

## Files to Create/Modify
- `app/.../web/WebServiceTranslationJobController.kt` — 3 new methods
- `app/.../web/KtorServer.kt` — 3 new routes
- `app/.../domain/webservice/WebServiceModels.kt` — 3 new DTOs
- `modules/web/src/api/webService.ts` — Types + 3 functions
- `modules/web/src/views/TranslationDashboard.vue` — NEW file
- `modules/web/src/router/index.ts` — New route
- `modules/web/src/components/WebAppShell.vue` — Nav link
- `modules/web/src/i18n.ts` — Dashboard keys

## Pass Criteria

- [ ] PASS-01: Mở `/translation` → thấy 4 panels render đúng
- [ ] PASS-02: Active Jobs panel hiện job list (nếu có) với progress
- [ ] PASS-03: Provider Cache Browser: chọn book → thấy chapter list với provider badges
- [ ] PASS-04: Pretranslate: submit batch → jobs created + progress hiện
- [ ] PASS-05: Memory Stats: hiện counts (có thể 0 nếu chưa có data)
- [ ] PASS-06: Sidebar có link "Dịch thuật" navigate đúng
- [ ] PASS-07: `pnpm build` + `.\gradlew.bat assembleAppDebug` thành công
- [ ] PASS-08: Verify trên LDPlayer + browser

## Build Gate
```bash
cd modules/web && pnpm build
cd ../.. && .\gradlew.bat assembleAppDebug
adb connect 127.0.0.1:5555
adb install -r app/build/outputs/apk/app/debug/app-app-universal-debug.apk
```

---
✅ Plan Complete — 6 Phases defined.
