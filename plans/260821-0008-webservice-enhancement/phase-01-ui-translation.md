# Phase 01: UI Translation Completion

Status: ✅ Complete
Dependencies: None (independent, highest user impact)

## Objective

Hoàn thiện hệ thống dịch giao diện WebService. Tiêu đề chương, danh sách mục lục, và tất cả hardcoded Vietnamese strings phải hỗ trợ 3 locales (vi/en/zh-CN) qua hệ thống `i18n.ts`.

## Scope

| In Scope | Out of Scope |
|---|---|
| Chapter title translation trong reader | ReadSettings.vue (sẽ làm sau nếu cần) |
| Catalog/TOC chapter title translation | BookItems.vue metadata |
| BookChapter.vue ~25 hardcoded strings | MediaReader.vue catalog translation |
| PopCatalog.vue hardcoded "Mục lục" | ToolBar.vue source editor labels |
| i18n.ts thêm ~25 keys × 3 locales | |

## Requirements

### Functional
- [ ] REQ-01: Chapter title trong reader body phải hiển thị qua `dynamicText('catalog', title)` — tiêu đề "第1章 呼吸就能变强" phải được dịch sang tiếng Việt
- [ ] REQ-02: Document title (browser tab) phải dùng translated title
- [ ] REQ-03: Mục lục (PopCatalog) phải batch-translate tất cả chapter titles khi mở
- [ ] REQ-04: CatalogItem.vue hiển thị chapter title qua `dynamicText`
- [ ] REQ-05: Tất cả hardcoded Vietnamese strings trong BookChapter.vue thay bằng `t(key)`
- [ ] REQ-06: PopCatalog.vue "Mục lục" hardcoded → `t('catalog')`
- [ ] REQ-07: TTS spoken title sử dụng translated title khi đang đọc bản dịch

### Non-Functional
- [ ] NF-01: Dynamic translation batch call không vượt quá 100 items/request
- [ ] NF-02: Không block UI khi đang translate — hiện text gốc trước, replace khi có kết quả

## Implementation Steps

### Step 1: Thêm i18n keys — `i18n.ts`
1. [ ] Thêm ~25 keys mới vào `messages.vi`, `messages.en`, `messages['zh-CN']`:
   - Reader toolbar: `catalog`, `readerSettings`, `translate`, `bookshelf`, `toTop`, `toBottom`
   - Navigation: `prevPage`, `nextPage`, `prevChapter`, `nextChapter`
   - Loading/Status: `loadingChapter`, `retry`, `translating`, `readingTranslation`, `translationCancelled`, `translationError`
   - Actions: `cancelTranslation`, `originalText`, `translatedText`
   - Alerts: `firstChapter`, `lastChapter`

### Step 2: Fix ChapterContent.vue — title translation
2. [ ] Import `dynamicText` từ `@/i18n`
3. [ ] Thay `{{ title }}` → `{{ dynamicText('catalog', title) }}`

### Step 3: Fix CatalogItem.vue — chapter list translation
4. [ ] Import `dynamicText` từ `@/i18n`
5. [ ] Thay `{{ cata.title }}` → `{{ dynamicText('catalog', cata.title) }}`

### Step 4: Fix PopCatalog.vue — batch translate + label
6. [ ] Import `{ dynamicText, t, translateDynamicTexts, webLocale }` từ `@/i18n`
7. [ ] Thay `"Mục lục"` hardcoded → `{{ t('catalog') }}`
8. [ ] Thêm `watch([catalog, webLocale], () => translateDynamicTexts('catalog', catalog.value.map(c => c.title)))`

### Step 5: Fix BookChapter.vue — hardcoded strings + title binding
9. [ ] `:title="data.title"` → `:title="dynamicText('catalog', data.title)"`
10. [ ] Replace tất cả hardcoded strings (xem bảng dưới)
11. [ ] `document.title` assignments → wrap `dynamicText`
12. [ ] Thêm `watch` cho `translateDynamicTexts('catalog', ...)` khi catalog load
13. [ ] TTS title sử dụng `dynamicText('catalog', ...)`

### Bảng hardcoded strings cần thay trong BookChapter.vue

| ~Line | Current | Key | vi | en | zh-CN |
|-------|---------|-----|----|----|-------|
| 26 | `"Mục lục"` | `catalog` | Mục lục | Table of Contents | 目录 |
| 42 | `"Cài đặt"` | `readerSettings` | Cài đặt | Settings | 设置 |
| 133 | `"Dịch"` | `translate` | Dịch | Translate | 翻译 |
| 139 | `"Kệ sách"` | `bookshelf` | Kệ sách | Library | 书架 |
| 143 | `"Đầu trang"` | `toTop` | Đầu trang | Top | 顶部 |
| 151 | `"Cuối trang"` | `toBottom` | Cuối trang | Bottom | 底部 |
| 163 | `"Trang trước"` | `prevPage` | Trang trước | Previous page | 上一页 |
| 163 | `"Chương trước"` | `prevChapter` | Chương trước | Previous chapter | 上一章 |
| 170 | `"Trang sau"` | `nextPage` | Trang sau | Next page | 下一页 |
| 170 | `"Chương sau"` | `nextChapter` | Chương sau | Next chapter | 下一章 |
| 217 | `"Đang tải nội dung chương…"` | `loadingChapter` | Đang tải nội dung chương… | Loading chapter… | 正在加载章节… |
| 219 | `"Thử lại"` | `retry` | Thử lại | Retry | 重试 |
| 581 | `"Hủy dịch"` | `cancelTranslation` | Hủy dịch | Cancel translation | 取消翻译 |
| 581 | `"Bản gốc"` | `originalText` | Bản gốc | Original | 原文 |
| 581 | `"Bản dịch"` | `translatedText` | Bản dịch | Translation | 译文 |
| 981 | `"Đang dịch"` | `translating` | Đang dịch | Translating | 翻译中 |
| 982 | `"Đang đọc bản dịch"` | `readingTranslation` | Đang đọc bản dịch | Reading translation | 正在阅读译文 |
| 983 | `"Đã hủy dịch"` | `translationCancelled` | Đã hủy dịch | Translation cancelled | 已取消翻译 |
| 984 | `"Dịch lỗi"` | `translationError` | Dịch lỗi | Translation error | 翻译错误 |
| 1316 | `"Đây là chương đầu"` | `firstChapter` | Đây là chương đầu | First chapter | 这是第一章 |
| 1343 | `"Đây là chương cuối"` | `lastChapter` | Đây là chương cuối | Last chapter | 这是最后一章 |

## Files to Create/Modify
- `modules/web/src/i18n.ts` — Thêm ~25 keys × 3 locales
- `modules/web/src/components/ChapterContent.vue` — Import + dynamicText title
- `modules/web/src/components/CatalogItem.vue` — Import + dynamicText title
- `modules/web/src/components/PopCatalog.vue` — Import + dynamicText + watch + t()
- `modules/web/src/views/BookChapter.vue` — ~25 hardcoded replacements + dynamicText bindings

## Pass Criteria

- [ ] PASS-01: Mở web reader → đọc chương truyện Trung → tiêu đề chương hiện tiếng Việt (không còn 第X章)
- [ ] PASS-02: Mở Mục lục → tất cả chapter titles hiện tiếng Việt
- [ ] PASS-03: Browser tab title hiện tên chương tiếng Việt
- [ ] PASS-04: Chuyển ngôn ngữ web sang English → tất cả toolbar labels hiện English
- [ ] PASS-05: Chuyển sang 简体中文 → toolbar labels hiện tiếng Trung
- [ ] PASS-06: Không còn hardcoded Vietnamese string nào trong BookChapter.vue
- [ ] PASS-07: `pnpm build` thành công, không error
- [ ] PASS-08: `.\gradlew.bat assembleAppDebug` thành công
- [ ] PASS-09: Install APK lên LDPlayer → mở WebService → verify trên trình duyệt

## Build Gate
```bash
cd modules/web && pnpm build
cd ../.. && .\gradlew.bat assembleAppDebug
adb connect 127.0.0.1:5555
adb install -r app/build/outputs/apk/app/debug/app-app-universal-debug.apk
# Mở browser → http://<LDPlayer-IP>:<port> → đọc chương → verify
```

---
Next Phase: → [phase-02-discovery.md](./phase-02-discovery.md)
