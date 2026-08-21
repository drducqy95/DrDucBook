# Phase 02: Discovery Single-Source Dropdown

Status: ✅ Complete
Dependencies: Phase 01 (i18n keys shared)

## Objective

Chuyển Discovery từ chế độ multi-source checkbox sang single-source dropdown, giống cơ chế native app. Chọn 1 nguồn → xem categories (kinds) → chọn category → duyệt sách. Hoạt động độc lập, không phụ thuộc native Explore.

## Scope

| In Scope | Out of Scope |
|---|---|
| Discovery.vue rewrite — dropdown + kinds | Backend WebServiceDiscoveryController (đã đủ) |
| webService.ts — thêm kinds API functions | KtorServer.kt routes (đã có) |
| i18n.ts — thêm discovery keys | Native ExploreScreen changes |
| Kind type rendering (url/text/select/toggle) | Kind type `button` (JS eval — phức tạp) |
| Pagination ("Tải thêm") | Infinite scroll |

## Requirements

### Functional
- [ ] REQ-01: Hiện dropdown `el-select` liệt kê tất cả enabled explore sources
- [ ] REQ-02: Khi chọn source → gọi `GET /api/v2/discovery/kinds?sourceUrl=<url>` load categories
- [ ] REQ-03: Render kinds theo type:
  - `url` → chip/button clickable → load books cho category đó
  - `text` → input field → giá trị lưu vào kind values
  - `select` → dropdown với `chars` options
  - `toggle` → switch
- [ ] REQ-04: Khi click kind (url type) → gọi `GET /api/v2/discovery/home` với `sourceUrl`, `exploreUrl`, `args`
- [ ] REQ-05: PATCH kind values khi user thay đổi text/select/toggle → re-fetch books
- [ ] REQ-06: "Tải thêm" button cho pagination (page 2, 3, ...)
- [ ] REQ-07: Type filter tabs (text/image/audio/video) vẫn hoạt động như bộ lọc phụ
- [ ] REQ-08: Dynamic translation cho kind titles + book metadata

### Non-Functional
- [ ] NF-01: Dropdown load sources ≤ 2 giây
- [ ] NF-02: Book grid hiển thị ≤ 3 giây sau khi chọn category

## Implementation Steps

### Step 1: API functions — `webService.ts`
1. [ ] Thêm type `WebServiceDiscoveryKind` + `WebServiceDiscoveryKindsResponse`
2. [ ] Thêm `getWebServiceDiscoveryKinds(sourceUrl: string)`
3. [ ] Thêm `patchWebServiceDiscoveryKindValues(sourceUrl: string, values: Record<string, string>)`
4. [ ] Cập nhật `getDiscoveryHome()` nhận thêm `sourceUrl`, `exploreUrl`, `args`, `page`

### Step 2: i18n keys — `i18n.ts`
5. [ ] Thêm keys: `selectSource`, `selectCategory`, `loadMore`, `noCategories`, `allCategories`, `kindInput`, `kindToggle`

### Step 3: Rewrite Discovery.vue
6. [ ] Thay source-panel checkbox-group → `el-select` dropdown single source
7. [ ] Thêm kinds-panel section: render kinds theo type
8. [ ] Tạo component logic:
   - `selectedSource` ref → watch → load kinds
   - `selectedKind` ref → watch → load books
   - `kindValues` reactive object → PATCH on change
9. [ ] Grid hiển thị sách từ selected source + selected kind
10. [ ] "Tải thêm" button: increment page → append books
11. [ ] Dynamic translation cho kind titles, book names, authors

## Files to Create/Modify
- `modules/web/src/api/webService.ts` — Thêm kinds API types + functions
- `modules/web/src/i18n.ts` — Thêm discovery keys
- `modules/web/src/views/Discovery.vue` — Rewrite UI component

## Pass Criteria

- [ ] PASS-01: Mở tab Khám phá → thấy dropdown liệt kê sources
- [ ] PASS-02: Chọn 1 source → thấy categories/kinds hiển thị bên dưới
- [ ] PASS-03: Click category → thấy danh sách sách load trong grid
- [ ] PASS-04: Click "Tải thêm" → thêm sách từ page 2 xuất hiện
- [ ] PASS-05: Click sách → mở reader/media player
- [ ] PASS-06: Kind type `text` (nếu có) → nhập text → books refresh
- [ ] PASS-07: Kind type `select` (nếu có) → chọn option → books refresh
- [ ] PASS-08: Type filter tabs vẫn lọc đúng
- [ ] PASS-09: `pnpm build` thành công
- [ ] PASS-10: `.\gradlew.bat assembleAppDebug` thành công
- [ ] PASS-11: Install APK lên LDPlayer → mở WebService → verify Discovery trên browser

## Build Gate
```bash
cd modules/web && pnpm build
cd ../.. && .\gradlew.bat assembleAppDebug
adb connect 127.0.0.1:5555
adb install -r app/build/outputs/apk/app/debug/app-app-universal-debug.apk
```

---
Next Phase: → [phase-03-translation-search.md](./phase-03-translation-search.md)
