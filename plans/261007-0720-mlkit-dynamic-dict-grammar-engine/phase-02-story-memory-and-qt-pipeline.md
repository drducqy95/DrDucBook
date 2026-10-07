# Phase 02: Story Memory & Dynamic QT Pipeline in ML Kit

Status: ⬜ Pending
Dependencies: Phase 01

## Objective
Tích hợp toàn bộ dữ liệu từ **Story Memory (BookStoryMemory)**, **QuickDictionary (Room DB: Global, Series, Book)** và **QT Trie Matcher (Phase 01)** vào pipeline ML Kit trong `TranslateChapterUseCase` & `MlKitDictionaryEnforcer`. Loại bỏ hoàn toàn sự phụ thuộc vào các hardcode câu chữ cụ thể trong `KNOWN_PIVOT_REPLACEMENTS`.

## Requirements

### Functional
- [ ] Trong `TranslateChapterUseCase.kt`:
  - Trước khi dịch từng đoạn/sentence bằng ML Kit, gọi `quickTranslationGateway.findMatchingTerms(paragraph, dictionaries)` để lấy động toàn bộ từ vựng QT liên quan.
  - Hợp nhất: `Story Memory entities` (ưu tiên cao nhất) + `QuickDictionary Room entries` + `QT Trie matched terms`.
  - Chuyển toàn bộ danh sách hợp nhất này vào `MlKitDictionaryEnforcer.maskEntities(...)`.
- [ ] Trong `MlKitDictionaryEnforcer.kt`:
  - Loại bỏ các danh sách hardcode câu chuyện riêng lẻ (`KNOWN_PIVOT_REPLACEMENTS` nội dung từng đoạn văn).
  - Giữ lại cơ chế Masking / Unmasking thông minh và cơ chế giải quyết Pinyin / HanViet / Aliases động dựa trên dictionary & story memory.
  - Tự động thay thế các biến thể Pinyin (như "Su Xiao" -> "Tô Hiểu") bằng từ mục tiêu được định nghĩa trong từ điển/bộ nhớ dịch.
- [ ] Hỗ trợ đồng bộ `TranslateDynamicUiTextUseCase`: Nạp các từ vựng từ Story Memory và QT Pack vào bản dịch tiêu đề chương, tránh hiện tượng lệch danh xưng (như "Luân Hồi Nhạc Viên" vs "Luân Hồi Lạc Viên").

### Non-Functional
- [ ] Modularity: Cơ chế hoạt động độc lập với bất kỳ bộ truyện nào, hoạt động tức thì với mọi truyện tiên hiệp, huyền huyễn, đô thị, v.v.
- [ ] Safety: Bảo đảm không để lọt placeholder `__ENT_X__` ra văn bản cuối cùng.

## Implementation Steps
1. [ ] Cập nhật `TranslateChapterUseCase.translateMlKitParagraph` để lấy dynamic terms từ `quickTranslationGateway.findMatchingTerms(...)`.
2. [ ] Tái cấu trúc `MlKitDictionaryEnforcer.kt` để hoạt động 100% dựa trên danh sách terms và canonical memory được truyền vào.
3. [ ] Viết unit tests kiểm tra cơ chế dynamic extraction & masking với các trường hợp truyện khác nhau.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt` - [Dynamic terms harvesting & injection]
- `app/src/main/java/io/legado/app/domain/model/MlKitDictionaryEnforcer.kt` - [Refactor away from hardcode, purely dynamic]
- `app/src/main/java/io/legado/app/domain/usecase/TranslateDynamicUiTextUseCase.kt` - [Title & UI term alignment]
- `app/src/test/java/io/legado/app/domain/model/MlKitDictionaryEnforcerTest.kt` - [Unit tests]

## Test Criteria
- [ ] Một bộ truyện mới không hề có hardcode khi có mục từ trong QuickDictionary hoặc QT Pack thì ML Kit dịch đúng 100% thực thể đó.
- [ ] Placeholder `__ENT_X__` được giải mã hoàn chỉnh không để lại vết gạch dưới.

---
Next Phase: [Phase 03 - Pure Vietnamese Grammar & Syntax Healing Engine](file:///d:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main/plans/261007-0720-mlkit-dynamic-dict-grammar-engine/phase-03-grammar-only-healing-engine.md)
